<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, Box, MapPin } from 'lucide-vue-next'
import type { LayoutIssue, LayoutObject, LayoutObjectType, LayoutTool, WarehouseLayoutState } from '../types'

const props = defineProps<{
  state: WarehouseLayoutState
  selectedObjectId: string | null
  activeTool: LayoutTool
  issues: LayoutIssue[]
}>()

const emit = defineEmits<{
  select: [objectId: string | null]
  createAt: [position: { x: number; y: number }]
}>()

const boundary = computed(() => props.state.objects.find((object) => object.type === 'boundary'))
const visibleObjects = computed(() => props.state.objects
  .filter((object) => object.type !== 'boundary' && object.visible)
  .sort((left, right) => order(left.type) - order(right.type)))

function objectStyle(object: LayoutObject) {
  const frame = boundary.value
  if (!frame) return {}
  return {
    left: `${((object.x - frame.x) / frame.width) * 100}%`,
    top: `${((object.y - frame.y) / frame.height) * 100}%`,
    width: `${(object.width / frame.width) * 100}%`,
    height: `${(object.height / frame.height) * 100}%`,
    transform: `rotate(${object.rotation}deg)`,
  }
}

function issuesFor(objectId: string) {
  return props.issues.filter((issue) => issue.objectId === objectId)
}

function handleCanvasClick(event: MouseEvent) {
  if (event.target !== event.currentTarget && (event.target as HTMLElement).closest('[data-layout-object]')) return
  if (props.activeTool === 'select' || props.activeTool === 'pan') {
    emit('select', null)
    return
  }
  const element = event.currentTarget as HTMLElement
  const rect = element.getBoundingClientRect()
  const frame = boundary.value
  if (!frame || rect.width === 0 || rect.height === 0) return
  emit('createAt', {
    x: Math.round((frame.x + ((event.clientX - rect.left) / rect.width) * frame.width) * 10) / 10,
    y: Math.round((frame.y + ((event.clientY - rect.top) / rect.height) * frame.height) * 10) / 10,
  })
}

function order(type: LayoutObjectType) {
  return ({ 'fixed-zone': 1, 'free-zone': 1, aisle: 2, obstacle: 3, 'fixed-location': 4, boundary: 0 })[type]
}
</script>

<template>
  <section class="canvas-panel">
    <header>
      <div><h2>仓库平面 · 草稿布局</h2><p>网格 1m · 吸附开启 · 坐标原点位于仓库左上角</p></div>
      <div class="canvas-legend"><span><i class="zone"></i>区域</span><span><i class="aisle"></i>通道</span><span><i class="location"></i>库位</span></div>
    </header>

    <div class="canvas-stage">
      <div class="ruler ruler-x"><span v-for="mark in 7" :key="mark">{{ (mark - 1) * 20 }}m</span></div>
      <div class="ruler ruler-y"><span v-for="mark in 5" :key="mark">{{ (mark - 1) * 20 }}m</span></div>
      <div
        v-if="boundary"
        data-testid="warehouse-layout-canvas"
        class="layout-canvas"
        :class="{ drawing: !['select', 'pan'].includes(activeTool), panning: activeTool === 'pan' }"
        :aria-label="`${state.warehouseName} 仓库布局画布`"
        @click="handleCanvasClick"
      >
        <div class="boundary-label"><MapPin :size="13" />{{ state.warehouseCode }} · {{ boundary.width }}m × {{ boundary.height }}m</div>
        <button
          v-for="object in visibleObjects"
          :key="object.id"
          :data-testid="`layout-object-${object.id}`"
          data-layout-object
          type="button"
          class="layout-object"
          :class="[`type-${object.type}`, { selected: selectedObjectId === object.id, locked: object.locked, problematic: issuesFor(object.id).length }]"
          :style="objectStyle(object)"
          :aria-label="`选择 ${object.code} ${object.name}`"
          :aria-pressed="selectedObjectId === object.id"
          @click.stop="emit('select', object.id)"
        >
          <template v-if="object.type === 'fixed-location'">
            <span class="location-code">{{ object.code }}</span>
            <span v-if="object.id !== 'location-a13'" class="location-meta">{{ Number(object.code.slice(2)) % 3 + 1 }} SKU</span>
            <AlertTriangle v-if="issuesFor(object.id).length" :size="13" class="object-warning" />
          </template>
          <template v-else-if="object.type === 'obstacle'"><Box :size="14" /><span>{{ object.code }}</span></template>
          <template v-else><strong>{{ object.code }} · {{ object.name }}</strong><small>{{ object.width }} × {{ object.height }}m</small></template>
          <i v-if="selectedObjectId === object.id" v-for="corner in 4" :key="corner" :class="`handle handle-${corner}`"></i>
        </button>
        <div v-if="!['select', 'pan'].includes(activeTool)" class="draw-tip">点击画布放置对象 · Esc 退出绘制</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.canvas-panel { min-width:0; height:100%; display:flex; flex-direction:column; background:#f8fafc; }
.canvas-panel>header { min-height:58px; padding:12px 14px 9px; display:flex; align-items:flex-start; justify-content:space-between; border-bottom:1px solid #e2e8f0; background:#fff; }
h2 { margin:0; color:#25314d; font-size:14px; font-weight:700; line-height:20px; }
header p { margin:2px 0 0; color:#94a3b8; font-size:10px; }
.canvas-legend { display:flex; gap:10px; color:#64748b; font-size:9px; }
.canvas-legend span { display:flex; align-items:center; gap:4px; }
.canvas-legend i { width:9px; height:9px; border:1px solid #cbd5e1; }
.canvas-legend .zone { background:#d7f4f7; border-color:#22b8cf; }.canvas-legend .aisle { background:#fef3c7; border-color:#f59e0b; }.canvas-legend .location { background:#fff; border-color:#536dff; }
.canvas-stage { position:relative; min-height:0; flex:1; overflow:auto; padding:30px 22px 24px 34px; background:#f8fafc; }
.ruler { position:absolute; display:flex; color:#94a3b8; font:9px/1 Inter,sans-serif; pointer-events:none; }
.ruler-x { left:34px; right:22px; top:13px; justify-content:space-between; }
.ruler-y { left:8px; top:30px; bottom:24px; flex-direction:column; justify-content:space-between; }
.layout-canvas { position:relative; min-width:580px; min-height:406px; aspect-ratio:120/84; overflow:hidden; border:2px solid #64748b; border-radius:2px; background-color:#fff; background-image:linear-gradient(#e8edf5 1px,transparent 1px),linear-gradient(90deg,#e8edf5 1px,transparent 1px); background-size:20px 20px; box-shadow:0 8px 28px rgba(37,49,77,.08); cursor:default; }
.layout-canvas.drawing { cursor:crosshair; }.layout-canvas.panning { cursor:grab; }
.boundary-label { position:absolute; z-index:8; left:8px; top:7px; display:flex; align-items:center; gap:4px; padding:3px 6px; border-radius:4px; background:#25314d; color:#fff; font-size:9px; }
.layout-object { position:absolute; min-width:0; overflow:visible; border:1px solid; border-radius:2px; display:flex; flex-direction:column; align-items:flex-start; justify-content:flex-start; padding:5px 6px; background:#fff; color:#25314d; text-align:left; cursor:pointer; transition:border-color 100ms,box-shadow 100ms,filter 100ms; }
.layout-object:hover { filter:brightness(.98); }.layout-object.locked { cursor:not-allowed; }
.layout-object strong { font-size:9px; font-weight:700; line-height:13px; }.layout-object small { color:#64748b; font-size:8px; line-height:12px; }
.type-fixed-zone { border-color:#22b8cf; background:rgba(34,184,207,.16); }.type-free-zone { border-color:#14b8a6; background:rgba(20,184,166,.15); }.type-aisle { border-color:#f59e0b; background:#fef3c7; flex-direction:row; align-items:center; justify-content:center; padding:2px; color:#92400e; }
.type-obstacle { border-color:#64748b; background:#e2e8f0; align-items:center; justify-content:center; padding:2px; color:#475569; }.type-obstacle span { display:none; }
.type-fixed-location { border-color:#7c8fff; background:rgba(255,255,255,.94); padding:3px 4px; justify-content:center; box-shadow:0 1px 2px rgba(37,49,77,.08); }
.type-fixed-location.problematic { border-color:#ef476f; background:#fff1f4; border-style:dashed; }
.layout-object.selected { z-index:7; border-color:#536dff; box-shadow:0 0 0 2px rgba(83,109,255,.22),0 5px 13px rgba(37,49,77,.16); }
.location-code { color:#25314d; font:700 8px/10px Inter,sans-serif; }.location-meta { color:#94a3b8; font-size:7px; line-height:9px; }.object-warning { position:absolute; top:2px; right:2px; color:#ef476f; }
.handle { position:absolute; z-index:9; width:6px; height:6px; border:1px solid #fff; background:#536dff; }.handle-1{left:-4px;top:-4px}.handle-2{right:-4px;top:-4px}.handle-3{right:-4px;bottom:-4px}.handle-4{left:-4px;bottom:-4px}
.draw-tip { position:absolute; z-index:20; left:50%; bottom:12px; translate:-50% 0; padding:6px 10px; border-radius:6px; background:#25314d; color:#fff; font-size:10px; box-shadow:0 8px 20px rgba(37,49,77,.18); }
@media (max-width:1100px){.layout-canvas{min-width:520px}.canvas-legend{display:none}}
</style>
