<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { MousePointer2, SquareDashedMousePointer, Pencil, Trash2, X } from 'lucide-vue-next'
import { cloneStructure, validatePlannerLayout } from '../warehouseStructure'
import type { StructureIssue, WarehouseStructure } from '../warehouseStructure'
import type { PlannerZone, PlannerZoneKind, PlannerPalletGroup } from '../warehousePlannerScene'
import { isPassage, passageAxis, passageWidthMeters, zoneKinds } from '../warehousePassages'
import WarehousePassageMark from './WarehousePassageMark.vue'

const props = defineProps<{ structure: WarehouseStructure; editing: boolean; viewing?: boolean; selectedZoneId?: string | null; gridSnapping: boolean; issues: StructureIssue[]; focusedId?: string | null; pallets?: readonly PlannerPalletGroup[] }>()
const emit = defineEmits<{ commit: [value: WarehouseStructure]; preview: [value: WarehouseStructure | null]; busy: [value: boolean]; conflict: [ids: string[]]; select: [id: string | null] }>()
const svg = ref<SVGSVGElement>()
const nameInput = ref<HTMLInputElement>()
const tool = ref<'select' | 'draw'>('draw')
const selectedId = ref<string | null>(null)
const preview = ref<WarehouseStructure | null>(null)
const pending = ref<PlannerZone | null>(null)
const name = ref('')
const detail = ref('')
const tone = ref<PlannerZone['tone']>('blue')
const creationKind = ref<PlannerZoneKind>('area')
const kind = ref<PlannerZoneKind>('area')
const widthInput = ref<number | string>(1)
const widthChanged = ref(false)
const traffic = ref<NonNullable<PlannerZone['traffic']>>('both')
const message = ref('')
const current = computed(() => preview.value ?? props.structure)
const zones = computed(() => current.value.zones ?? [])
const selected = computed(() => zones.value.find(zone => zone.id === selectedId.value))
const invalidIds = computed(() => new Set([...props.issues, ...validatePlannerLayout(current.value, props.pallets ?? [])].flatMap(issue => issue.objectIds)))
const tones: { id: PlannerZone['tone']; label: string; color: string }[] = [
  { id: 'blue', label: '蓝色', color: '#6489d7' }, { id: 'green', label: '绿色', color: '#5d9b87' },
  { id: 'amber', label: '黄色', color: '#b49250' }, { id: 'purple', label: '紫色', color: '#9272b4' },
]
const handles = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const
type Handle = typeof handles[number]
type Point = { x: number; y: number }
type Drag = { pointerId: number; startX: number; startY: number; origin: Point; zone: PlannerZone; kind: 'draw' | 'move' | Handle; bounds: DOMRect }
const drag = shallowRef<Drag | null>(null)
let frame: number | null = null
let sample: { clientX: number; clientY: number } | null = null
let sequence = 0
const hint = computed(() => message.value || (pending.value ? '确认属性后加入规划 · 通道宽度由实际占地计算' : tool.value === 'draw' ? creationKind.value === 'area' ? '拖拽绘制区域 · 区域可包含货堆 · Esc 取消' : '拖拽绘制矩形通道 · 可从已有通道内起笔连接下一段 · Esc 取消' : '拖动调整位置 · 拖动边角调整范围 · 通道内禁止放货堆'))

function setPreview(value: WarehouseStructure | null) { preview.value = value; emit('preview', value) }
function withZone(zone: PlannerZone) {
  const next = cloneStructure(props.structure)
  const existing = (next.zones ?? []).some(item => item.id === zone.id)
  next.zones = existing ? next.zones!.map(item => item.id === zone.id ? { ...zone } : item) : [...(next.zones ?? []), { ...zone }]
  return next
}
function clearFrame() { if (frame !== null) cancelAnimationFrame(frame); frame = null; sample = null }
function release() {
  clearFrame()
  if (drag.value && svg.value?.hasPointerCapture?.(drag.value.pointerId)) svg.value.releasePointerCapture(drag.value.pointerId)
  drag.value = null
}
function cancel() { release(); pending.value = null; setPreview(null); emit('busy', false) }
function choose(next: 'draw' | 'select') { cancel(); tool.value = next; message.value = ''; emit('conflict', []) }
function point(event: { clientX: number; clientY: number }, bounds: DOMRect): Point {
  return { x: (event.clientX - bounds.left) / bounds.width * 100, y: (event.clientY - bounds.top) / bounds.height * 100 }
}
function snapped(p: Point) {
  return props.gridSnapping ? { x: Math.round(p.x * 1.2) / 1.2, y: Math.round(p.y * .8) / .8 } : p
}
function start(event: PointerEvent, zone?: PlannerZone, handle?: Handle) {
  if (!props.editing || pending.value || event.button !== 0 || drag.value) return
  if (!handle && tool.value === 'draw' && creationKind.value !== 'area' && zone && isPassage(zone)) zone = undefined
  if (!zone && tool.value !== 'draw') { selectedId.value = null; return }
  const bounds = svg.value!.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  event.preventDefault(); event.stopPropagation()
  emit('conflict', []); message.value = ''
  svg.value!.focus({ preventScroll: true })
  const origin = point(event, bounds)
  const value: PlannerZone = zone ?? { id: `zone-${Date.now().toString(36)}-${++sequence}`, label: creationKind.value === 'area' ? '新区域' : zoneKinds.find(item => item.id === creationKind.value)!.label, tone: 'blue', ...(creationKind.value === 'area' ? {} : { kind: creationKind.value }), left: origin.x, top: origin.y, width: 0, height: 0 }
  selectedId.value = value.id
  if (zone) tool.value = 'select'
  drag.value = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, origin, zone: { ...value }, kind: handle ?? (zone ? 'move' : 'draw'), bounds }
  svg.value!.setPointerCapture?.(event.pointerId)
  emit('busy', true)
}
function update(event: { clientX: number; clientY: number }, snap: boolean) {
  const gesture = drag.value
  if (!gesture) return
  const end = point(event, gesture.bounds)
  const original = gesture.zone
  let left = original.left, top = original.top, right = left + original.width, bottom = top + original.height
  if (gesture.kind === 'move') {
    const position = { x: left + end.x - gesture.origin.x, y: top + end.y - gesture.origin.y }
    const next = snap ? snapped(position) : position
    left = next.x; top = next.y; right = left + original.width; bottom = top + original.height
  } else if (gesture.kind === 'draw') {
    const a = snap ? snapped(gesture.origin) : gesture.origin, b = snap ? snapped(end) : end
    left = Math.min(a.x, b.x); top = Math.min(a.y, b.y); right = Math.max(a.x, b.x); bottom = Math.max(a.y, b.y)
  } else {
    const p = snap ? snapped(end) : end
    if (gesture.kind.includes('w')) left = Math.min(p.x, right - 100 / 120)
    if (gesture.kind.includes('e')) right = Math.max(p.x, left + 100 / 120)
    if (gesture.kind.includes('n')) top = Math.min(p.y, bottom - 100 / 80)
    if (gesture.kind.includes('s')) bottom = Math.max(p.y, top + 100 / 80)
  }
  setPreview(withZone({ ...original, left, top, width: right - left, height: bottom - top }))
}
function move(event: PointerEvent) {
  if (!drag.value || drag.value.pointerId !== event.pointerId) return
  sample = { clientX: event.clientX, clientY: event.clientY }
  if (frame !== null) return
  frame = requestAnimationFrame(() => { frame = null; const next = sample; sample = null; if (next) update(next, false) })
}
function openProperties(zone: PlannerZone) {
  pending.value = { ...zone }; name.value = zone.label; detail.value = zone.detail ?? ''; tone.value = zone.tone
  kind.value = zone.kind ?? 'area'; traffic.value = zone.traffic ?? 'both'
  widthInput.value = Number(passageWidthMeters(zone).toFixed(2)); widthChanged.value = false
  message.value = ''
  emit('busy', true)
  nextTick(() => { nameInput.value?.focus(); nameInput.value?.select() })
}
function stop(event: PointerEvent) {
  const gesture = drag.value
  if (!gesture || gesture.pointerId !== event.pointerId) return
  if (Math.hypot(event.clientX - gesture.startX, event.clientY - gesture.startY) < 3) { cancel(); return }
  clearFrame(); update(event, true)
  const next = preview.value!, zone = next.zones!.find(item => item.id === gesture.zone.id)!
  if (isPassage(zone) && !zone.axis) zone.axis = passageAxis(zone)
  const issues = validatePlannerLayout(next, props.pallets ?? []).filter(issue => issue.objectIds.includes(zone.id))
  release()
  if (zone.width < 100 / 120 - 1e-7 || zone.height < 100 / 80 - 1e-7 || issues.length) {
    cancel()
    message.value = issues.length ? `${issues[0]!.message}，${gesture.kind === 'draw' ? '未创建区域' : '已回到原位'}` : '区域至少为 0.5 × 0.5 米'
    emit('conflict', [...new Set(issues.flatMap(issue => issue.objectIds))])
    return
  }
  if (gesture.kind === 'draw') { openProperties(zone); return }
  cancel()
  if (JSON.stringify(zone) !== JSON.stringify(gesture.zone)) emit('commit', next)
}
function save() {
  if (!props.editing || !pending.value) return
  const label = name.value.trim()
  if (!label || label.length > 40) { message.value = '请输入 1–40 字的区域名称'; return }
  const zone: PlannerZone = { ...pending.value, label, tone: tone.value }
  if (kind.value === 'area') {
    const nextDetail = detail.value.trim()
    if (nextDetail.length > 60) { message.value = '区域说明不能超过 60 个字'; return }
    if (nextDetail) zone.detail = nextDetail
    else delete zone.detail
    delete zone.kind; delete zone.axis; delete zone.traffic
  } else {
    delete zone.detail
    const width = Number(widthInput.value)
    if (widthInput.value === '' || !Number.isFinite(width) || width < .5) { message.value = '通道宽度请输入不小于 0.5 米的数值（仅为绘制下限）'; return }
    zone.kind = kind.value; zone.axis = passageAxis(zone)
    if (widthChanged.value) {
      if (zone.axis === 'horizontal') zone.height = width / .4
      else zone.width = width / .6
    }
    if (kind.value === 'forklift') zone.traffic = traffic.value
    else delete zone.traffic
  }
  const next = withZone(zone)
  const issues = validatePlannerLayout(next, props.pallets ?? []).filter(issue => issue.objectIds.includes(zone.id))
  if (issues.length) { setPreview(next); message.value = issues[0]!.message; return }
  cancel(); message.value = ''; tool.value = 'select'
  if (JSON.stringify(next) !== JSON.stringify(props.structure)) emit('commit', next)
  nextTick(() => svg.value?.focus({ preventScroll: true }))
}
function select(zone: PlannerZone) {
  if (!props.editing || pending.value || drag.value) return
  selectedId.value = zone.id; tool.value = 'select'; message.value = ''
}
function selectView(zone: PlannerZone) {
  if (!props.viewing || props.editing || isPassage(zone)) return
  emit('select', zone.id)
}
function remove() {
  if (!props.editing || !selected.value || pending.value || drag.value) return
  const next = cloneStructure(props.structure)
  next.zones = (next.zones ?? []).filter(zone => zone.id !== selectedId.value)
  selectedId.value = null; message.value = '区域已删除，货堆保持原位，可撤销恢复'
  emit('commit', next)
}
function keydown(event: KeyboardEvent) {
  if (!props.editing) return
  if (event.key === 'Escape') { event.preventDefault(); cancel(); tool.value = 'select'; message.value = '已取消当前操作'; svg.value?.focus(); return }
  if (event.target instanceof Element && event.target.closest('input, textarea, select, [contenteditable="true"]')) return
  if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); remove() }
}
function handlePosition(zone: PlannerZone, handle: Handle) {
  return { x: handle.includes('w') ? 0 : handle.includes('e') ? zone.width * 6 : zone.width * 3, y: handle.includes('n') ? 0 : handle.includes('s') ? zone.height * 4 : zone.height * 2 }
}
watch(() => props.editing, editing => {
  cancel(); message.value = ''; tool.value = 'draw'
  if (editing) window.addEventListener('keydown', keydown)
  else window.removeEventListener('keydown', keydown)
}, { immediate: true })
watch(() => props.structure, () => { cancel(); if (!(props.structure.zones ?? []).some(zone => zone.id === selectedId.value)) selectedId.value = null })
watch(() => props.focusedId, id => { if (id && zones.value.some(zone => zone.id === id)) { selectedId.value = id; tool.value = 'select' } })
onBeforeUnmount(() => { cancel(); window.removeEventListener('keydown', keydown) })
</script>

<template>
  <div class="zone-layer" :class="{ editing, viewing, drawing: tool === 'draw', dragging: !!drag }">
    <svg ref="svg" data-testid="zone-canvas" class="zone-svg" viewBox="0 0 600 400" preserveAspectRatio="none" tabindex="-1" aria-label="仓库区域规划画布"
      @pointerdown="start($event)" @pointermove="move" @pointerup="stop" @pointercancel="cancel">
      <g v-for="zone in [...zones].sort((a, b) => Number(a.id === selectedId) - Number(b.id === selectedId))" :key="zone.id"
        :data-testid="`planner-zone-${zone.id}`" class="operation-zone" :class="[`tone-${zone.tone}`, { passage: isPassage(zone), 'fire-passage': zone.kind === 'fire', chosen: (editing && selectedId === zone.id) || (viewing && selectedZoneId === zone.id), invalid: invalidIds.has(zone.id) }]"
        :transform="`translate(${zone.left * 6},${zone.top * 4})`" role="button" :tabindex="editing || (viewing && !isPassage(zone)) ? 0 : -1" :aria-label="`${zone.label}，区域`" :aria-pressed="(editing && selectedId === zone.id) || (viewing && selectedZoneId === zone.id)"
        @pointerdown.stop="start($event, zone)" @click.stop="selectView(zone)" @keydown.enter.prevent.stop="viewing ? selectView(zone) : select(zone)" @keydown.space.prevent.stop="viewing ? selectView(zone) : select(zone)" @dblclick.stop="select(zone); editing && !pending && openProperties(zone)">
        <rect class="zone-body" :width="zone.width * 6" :height="zone.height * 4" rx="1" />
        <WarehousePassageMark v-if="isPassage(zone)" :zone="zone" />
        <foreignObject v-else x="2" y="2" :width="Math.max(0, zone.width * 6 - 4)" :height="Math.max(0, zone.height * 4 - 4)" class="zone-caption-box">
          <div xmlns="http://www.w3.org/1999/xhtml" class="zone-caption"><strong>{{ zone.label }}</strong><small v-if="zone.height > 8 && zone.detail">{{ zone.detail }}</small></div>
        </foreignObject>
        <template v-if="editing && selectedId === zone.id && !pending && !drag">
          <rect v-for="handle in handles" :key="handle" :data-testid="`zone-handle-${handle}`" class="zone-handle" :style="{ cursor: `${handle}-resize` }"
            :x="handlePosition(zone, handle).x - 2" :y="handlePosition(zone, handle).y - 2" width="4" height="4" @pointerdown.stop="start($event, zone, handle)" />
        </template>
      </g>
    </svg>
    <div v-if="editing" class="zone-toolbar" aria-label="区域规划工具" @pointerdown.stop>
      <button data-testid="zone-tool-select" :aria-pressed="tool === 'select'" :disabled="!!pending" @click="choose('select')"><MousePointer2 :size="16" />选择</button>
      <select v-model="creationKind" data-testid="zone-kind-picker" aria-label="绘制类型" :disabled="!!pending || !!drag" @change="choose('draw')"><option v-for="item in zoneKinds" :key="item.id" :value="item.id">{{ item.label }}</option></select>
      <button data-testid="zone-tool-draw" :aria-pressed="tool === 'draw'" :disabled="!!pending" @click="choose('draw')"><SquareDashedMousePointer :size="16" />{{ creationKind === 'area' ? '绘制区域' : '绘制通道' }}</button>
      <template v-if="selected && !pending && !drag"><span class="separator" /><button data-testid="zone-edit" @click="openProperties(selected)"><Pencil :size="15" />编辑属性</button><button data-testid="zone-delete" @click="remove"><Trash2 :size="15" />删除</button></template>
    </div>
    <form v-if="editing && pending" data-testid="zone-save" class="zone-properties" role="dialog" aria-label="区域与通道属性" novalidate @submit.prevent="save" @pointerdown.stop>
      <header><strong>{{ (structure.zones ?? []).some(zone => zone.id === pending!.id) ? '编辑区域' : '新建区域' }}</strong><button type="button" aria-label="关闭区域编辑" @click="cancel"><X :size="16" /></button></header>
      <label>区域名称<input ref="nameInput" v-model="name" data-testid="zone-name" maxlength="40" autocomplete="off" placeholder="例如：收货区、混放区 A+B" /></label>
      <label v-if="kind === 'area'" class="detail-field">区域说明（选填）<input v-model="detail" data-testid="zone-detail" maxlength="60" autocomplete="off" placeholder="例如：入库交接、冷藏待检" /></label>
      <label class="passage-field">类型<select v-model="kind" data-testid="zone-kind"><option v-for="item in zoneKinds" :key="item.id" :value="item.id">{{ item.label }}</option></select></label>
      <template v-if="kind !== 'area'">
        <label class="passage-field">通道宽度（米） · {{ passageAxis(pending) === 'horizontal' ? '横向段' : '纵向段' }}<input v-model="widthInput" data-testid="passage-width" type="number" min="0.5" step="0.1" @input="widthChanged = true" /></label>
        <label v-if="kind === 'forklift'" class="passage-field">行驶方向<select v-model="traffic" data-testid="passage-traffic"><option value="both">双向</option><option value="forward">{{ passageAxis(pending) === 'horizontal' ? '向右 →' : '向下 ↓' }}</option><option value="backward">{{ passageAxis(pending) === 'horizontal' ? '向左 ←' : '向上 ↑' }}</option></select></label>
        <p class="passage-note">仅规划占地，不代表安全合规；请按现场要求确认宽度。</p>
      </template>
      <fieldset v-if="kind === 'area'"><legend>区域颜色</legend><button v-for="color in tones" :key="color.id" type="button" :data-testid="`zone-tone-${color.id}`" :aria-label="color.label" :aria-pressed="tone === color.id" :style="{ '--swatch': color.color }" @click="tone = color.id"><span />{{ color.label }}</button></fieldset>
      <p v-if="message" role="alert" class="form-error">{{ message }}</p>
      <footer><button type="button" data-testid="zone-cancel" @click="cancel">取消</button><button type="submit" class="primary">确认</button></footer>
    </form>
    <p v-if="editing" class="zone-hint" role="status">{{ hint }}</p>
  </div>
</template>

<style scoped>
.zone-layer {position:absolute;inset:0;pointer-events:none;}
.zone-svg {position:absolute;inset:0;z-index:3;width:100%;height:100%;outline:none;overflow:visible;pointer-events:none;}
.editing .zone-svg {pointer-events:auto;touch-action:none;}
.viewing .zone-svg {pointer-events:auto;}
.drawing .zone-svg {cursor:crosshair;}
.operation-zone {color:#5079b8;pointer-events:none;}
.operation-zone.tone-green {color:#448773;}.operation-zone.tone-amber {color:#a8863d;}.operation-zone.tone-purple {color:#8863aa;}
.operation-zone.passage {color:#64748b;}.operation-zone.fire-passage {color:#bd5454;}
.zone-body {fill:currentColor;fill-opacity:.09;stroke:currentColor;stroke-opacity:.6;stroke-width:.65;stroke-dasharray:2 1.5;}
.passage .zone-body {fill-opacity:.025;stroke-width:.3;stroke-opacity:.45;stroke-dasharray:4 3;}
.fire-passage .zone-body {fill-opacity:0;stroke:#f08c80;stroke-width:.2;stroke-opacity:.65;stroke-dasharray:none;}
.editing .operation-zone {pointer-events:all;cursor:grab;}
.viewing .operation-zone:not(.passage) {pointer-events:all;cursor:pointer;}
.dragging .operation-zone {cursor:grabbing;}
.chosen .zone-body {stroke:#536dff;stroke-opacity:1;stroke-width:1.2;stroke-dasharray:none;fill-opacity:.13;}
.invalid .zone-body {fill:#dc3545;stroke:#dc3545;stroke-opacity:1;stroke-width:1.4;stroke-dasharray:3 2;}
.zone-handle {fill:#fff;stroke:#536dff;stroke-width:.9;}
.zone-caption-box {pointer-events:none;overflow:hidden;}
.zone-caption {height:100%;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:4px;overflow:hidden;}
.zone-caption strong {max-width:100%;font-size:7px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.zone-caption small {max-width:100%;font-size:5px;opacity:.7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.zone-toolbar {position:absolute;z-index:24;top:10px;left:24px;display:flex;align-items:center;gap:4px;padding:5px;background:#ffffffed;border:1px solid #e1e7ef;border-radius:8px;box-shadow:0 3px 12px #25314d0d;pointer-events:auto;}
.zone-toolbar button,.zone-properties button {display:flex;align-items:center;justify-content:center;gap:5px;border:0;background:transparent;color:#596779;min-height:30px;padding:5px 9px;border-radius:5px;font-size:12px;}
.zone-toolbar button[aria-pressed=true] {color:#536dff;background:#edf1ff;}.zone-toolbar button:disabled {opacity:.45;cursor:default;}
.zone-toolbar button:hover:not(:disabled),.zone-properties button:hover {background:#f1f4fa;}
.separator {height:20px;width:1px;background:#e1e7ef;margin:0 4px;}
.zone-properties {position:absolute;z-index:24;top:60px;left:24px;width:300px;max-width:calc(100% - 48px);padding:14px;background:#fff;border:1px solid #e1e7ef;border-radius:10px;box-shadow:0 8px 24px #25314d1a;pointer-events:auto;color:#36465c;}
.zone-properties header {display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;font-size:14px;}
.zone-properties label {display:grid;gap:6px;font-size:12px;}.zone-properties input {width:100%;border:1px solid #d9e1ed;border-radius:5px;padding:8px;font-size:13px;}
.zone-properties input:focus {outline:2px solid #dfe6ff;border-color:#536dff;}
.zone-toolbar select,.zone-properties select {border:1px solid #d9e1ed;background:white;border-radius:5px;padding:6px;color:#596779;font-size:12px;}
.detail-field,.passage-field {margin-top:10px;}.passage-note {font-size:11px;line-height:1.6;color:#7b8797;margin:10px 0;}
.zone-properties fieldset {display:flex;gap:4px;border:0;padding:0;margin:14px 0;}.zone-properties legend {font-size:12px;margin-bottom:8px;}
.zone-properties fieldset button {padding:5px;flex:1;font-size:11px;}.zone-properties fieldset span {width:10px;height:10px;border-radius:50%;background:var(--swatch);}
.zone-properties fieldset button[aria-pressed=true] {box-shadow:inset 0 0 0 1px #536dff;background:#f4f6ff;}
.zone-properties footer {display:flex;justify-content:flex-end;gap:8px;}.zone-properties footer button {padding:6px 18px;}.zone-properties .primary {background:#536dff;color:white;}.zone-properties .primary:hover {background:#405bea;}
.form-error {font-size:12px;color:#c83248;margin:8px 0;}
.zone-hint {position:absolute;z-index:24;bottom:10px;left:24px;max-width:calc(100% - 48px);padding:7px 10px;border:1px solid #e1e7ef;border-radius:6px;background:#fffffff0;color:#657388;font-size:12px;pointer-events:none;}
</style>
