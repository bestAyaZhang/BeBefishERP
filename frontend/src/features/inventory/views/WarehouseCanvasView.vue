<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Boxes, Warehouse, Plus, SquareDashedMousePointer, MousePointer2, Hand, Undo2, Redo2, Search, Eye, EyeOff, Lock, Unlock, TriangleAlert } from 'lucide-vue-next'
import AccessibleDialog from '../../../components/AccessibleDialog.vue'
import WarehouseFloorCanvas from '../warehouseCanvas/components/WarehouseFloorCanvas.vue'
import WarehouseProductDrawer from '../warehouseCanvas/components/WarehouseProductDrawer.vue'
import WarehouseObjectPanel from '../warehouseCanvas/components/WarehouseObjectPanel.vue'
import { useWarehouseCanvas } from '../warehouseCanvas/useWarehouseCanvas'
import { findOpenPosition, summarizeCanvas } from '../warehouseCanvas/warehouseCanvasModel'
import type { CanvasRect } from '../warehouseCanvas/types'

const canvas = useWarehouseCanvas()
const { state, activeTool, selectedAreaId, selectedBlockId, issues, searchQuery } = canvas
const { dirty, saving, loading, canUndo, canRedo } = canvas
const summary = computed(() => summarizeCanvas(state.value))
const selectedArea = computed(() => state.value.areas.find((area) => area.id === selectedAreaId.value))
const selectedBlock = computed(() => state.value.blocks.find((block) => block.id === selectedBlockId.value))
const selectedSku = computed(() => state.value.catalog.find((sku) => sku.skuId === selectedBlock.value?.skuId))
const filteredCatalog = computed(() => state.value.catalog.filter((sku) => `${sku.productName} ${sku.skuCode}`.toLocaleLowerCase().includes(searchQuery.value.trim().toLocaleLowerCase())))
const skuTotal = (skuId: number) => state.value.blocks.filter((block) => block.skuId === skuId).reduce((total, block) => total + block.units, 0)
const pendingArea = ref<CanvasRect | null>(null)
const areaName = ref('D-01')
const error = ref('')
const notice = ref('')
const drawerOpen = ref(false)
const drawerSkuId = ref<number>()
const drawerAreaId = ref<string>()
const partialOpen = ref(false)
const partialUnits = ref<number | string>(60)
const partialTarget = ref('')
const moveTargets = computed(() => state.value.areas.filter((area) => area.id !== selectedBlock.value?.areaId && area.visible && !area.locked))
const deleteAreaId = ref<string | null>(null)
const deleteFinal = ref(false)
const deletingArea = computed(() => state.value.areas.find((area) => area.id === deleteAreaId.value))
const deleteCount = computed(() => state.value.blocks.filter((block) => block.areaId === deleteAreaId.value).length)
const viewport = ref<HTMLElement>()
const zoom = ref(100)
let pan: { pointerId: number; x: number; y: number; left: number; top: number } | null = null
const rectFields = ['x', 'y', 'width', 'height'] as const
let nextId = 1
function run(action: () => void) {
  error.value = ''
  try { action() } catch (cause) { error.value = cause instanceof Error ? cause.message : '操作失败，请重试' }
}
watch(state, () => {
  if (selectedAreaId.value && !selectedArea.value) canvas.selectArea(null)
  if (selectedBlockId.value && !selectedBlock.value) canvas.selectBlock(null)
  if (!selectedBlock.value) partialOpen.value = false
  if (deleteAreaId.value && !deletingArea.value) deleteAreaId.value = null
})
function clearSelection() { canvas.selectArea(null); canvas.selectBlock(null); error.value = '' }
function chooseArea(id: string) { error.value = ''; canvas.selectArea(id) }
function chooseBlock(id: string) { error.value = ''; canvas.selectBlock(id) }
async function locateBlock(id: string) {
  chooseBlock(id)
  await nextTick()
  const element = [...(viewport.value?.querySelectorAll<HTMLElement>('[data-testid^="warehouse-sku-block-"]') ?? [])]
    .find((candidate) => candidate.dataset.testid === `warehouse-sku-block-${id}`)
  element?.focus({ preventScroll: true })
  element?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
}
function updateAreaRect(rect: CanvasRect & { id: string }) { run(() => canvas.updateAreaRect(rect.id, rect)) }
function updateBlockRect(rect: CanvasRect & { id: string }) {
  run(() => {
    const block = state.value.blocks.find((item) => item.id === rect.id)
    if (!block || state.value.areas.find((area) => area.id === block.areaId)?.locked) return
    // An unchanged-size drag fully landing in another area means whole-block transfer.
    const target = rect.width === block.width && rect.height === block.height
      ? state.value.areas.find((area) => area.id !== block.areaId && area.visible && !area.locked && rect.x >= area.x && rect.y >= area.y && rect.x + rect.width <= area.x + area.width && rect.y + rect.height <= area.y + area.height)
      : undefined
    if (target) canvas.moveWholeBlock(block.id, target.id, rect)
    else canvas.updateBlockRect(block.id, rect)
  })
}
function moveBlock(targetAreaId: string, movedUnits?: number) {
  run(() => {
    const block = selectedBlock.value
    if (!block) return
    if (!moveTargets.value.some((area) => area.id === targetAreaId)) throw new Error('请选择其他可见且未锁定的区域')
    const target = findOpenPosition(state.value, targetAreaId, { width: block.width, height: block.height })
    if (!target) throw new Error('目标区域没有足够的空白位置')
    if (movedUnits === undefined) canvas.moveWholeBlock(block.id, targetAreaId, target)
    else canvas.splitBlock({ blockId: block.id, targetAreaId, target, movedUnits })
    partialOpen.value = false
    notice.value = '库存移动完成，总个数保持不变'
  })
}
function openPartial() { error.value = ''; partialUnits.value = 60; partialTarget.value = moveTargets.value[0]?.id ?? ''; partialOpen.value = true }
function requestDeleteArea() {
  if (!selectedArea.value) return
  if (!state.value.blocks.some((block) => block.areaId === selectedArea.value!.id)) { run(() => canvas.deleteArea(selectedArea.value!.id)); return }
  error.value = ''; deleteFinal.value = false; deleteAreaId.value = selectedArea.value.id
}
function moveFirst() {
  const block = state.value.blocks.find((item) => item.areaId === deleteAreaId.value)
  deleteAreaId.value = null
  if (block) chooseBlock(block.id)
}
function confirmDeleteArea() { run(() => { if (deleteAreaId.value) canvas.deleteArea(deleteAreaId.value, true); deleteAreaId.value = null }) }
async function saveLayout() {
  error.value = ''
  try { if (await canvas.save()) notice.value = '布局已保存（当前演示会话）' } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存失败，请重试' }
}
function startDrag(event: DragEvent, skuId: number) { event.dataTransfer?.setData('application/x-warehouse-sku', String(skuId)); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy' }
function dropProduct(event: DragEvent) {
  const skuId = Number(event.dataTransfer?.getData('application/x-warehouse-sku'))
  if (!state.value.catalog.some((sku) => sku.skuId === skuId)) return
  const hit = (event.target as HTMLElement).closest('[data-testid^="warehouse-area-"], [data-testid^="warehouse-sku-block-"]')
  const hitId = hit?.getAttribute('data-testid') ?? ''
  const areaId = hitId.startsWith('warehouse-area-') ? hitId.slice('warehouse-area-'.length) : state.value.blocks.find((block) => `warehouse-sku-block-${block.id}` === hitId)?.areaId
  const area = state.value.areas.find((item) => item.id === areaId && item.visible && !item.locked)
  if (!area) { error.value = '请将产品放入可见且未锁定的区域'; return }
  openProduct(skuId, area.id)
}
function startPan(event: PointerEvent) {
  if (activeTool.value !== 'pan' || event.button !== 0 || !viewport.value) return
  event.preventDefault()
  pan = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, left: viewport.value.scrollLeft, top: viewport.value.scrollTop }
  viewport.value.setPointerCapture?.(event.pointerId)
}
function movePan(event: PointerEvent) {
  if (!pan || pan.pointerId !== event.pointerId || !viewport.value) return
  viewport.value.scrollLeft = pan.left + pan.x - event.clientX
  viewport.value.scrollTop = pan.top + pan.y - event.clientY
}
function stopPan(event: PointerEvent) { viewport.value?.releasePointerCapture?.(event.pointerId); pan = null }
function startArea(rect: CanvasRect) { error.value = ''; areaName.value = 'D-01'; pendingArea.value = { ...rect } }
function confirmArea() {
  run(() => {
    if (!pendingArea.value || !areaName.value.trim()) throw new Error('请输入区域名称')
    const id = `area-created-${nextId++}`
    canvas.createArea({ id, name: areaName.value.trim(), ...pendingArea.value })
    pendingArea.value = null
    canvas.selectArea(id)
    activeTool.value = 'select'
  })
}
function openProduct(skuId?: number, areaId?: string) {
  error.value = ''; drawerSkuId.value = skuId; drawerAreaId.value = areaId; drawerOpen.value = true
}
function addProduct(input: { skuId: number; areaId: string; units: number }) {
  run(() => {
    const area = state.value.areas.find((item) => item.id === input.areaId)
    if (!area || area.locked || !area.visible) throw new Error('请选择可见且未锁定的目标区域')
    const size = { width: 120, height: 80 }
    const position = findOpenPosition(state.value, area.id, size)
    if (!position) throw new Error('目标区域没有足够的空白位置')
    canvas.addBlock({ id: `block-created-${nextId++}`, ...input, ...size, ...position })
    drawerOpen.value = false
    notice.value = `已将产品放入 ${area.name}`
  })
}
onMounted(async () => { try { await canvas.load(1) } catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' } })
</script>

<template>
  <main data-testid="warehouse-canvas-view" class="warehouse-page">
    <header class="page-header">
      <div><h1><Warehouse :size="20" /> 仓库画布</h1><p>绘制区域 · 放入产品 · 管理库存</p></div>
      <label>当前仓库 <select aria-label="当前仓库"><option>{{ state.warehouseName }}</option></select></label>
      <button data-testid="add-warehouse-product" class="primary" @click="openProduct()"><Plus :size="16" />添加产品</button>
    </header>
    <div class="toolbar">
      <label class="search"><Search :size="16" /><input v-model="searchQuery" data-testid="warehouse-sku-search" aria-label="搜索 SKU 或产品名称" placeholder="搜索 SKU / 产品名称" /></label>
      <button data-testid="canvas-tool-select" :aria-pressed="activeTool === 'select'" @click="activeTool = 'select'"><MousePointer2 :size="16" />选择</button>
      <button data-testid="canvas-tool-draw" :aria-pressed="activeTool === 'draw'" @click="activeTool = 'draw'"><SquareDashedMousePointer :size="16" />绘制区域</button>
      <button data-testid="canvas-tool-pan" :aria-pressed="activeTool === 'pan'" @click="activeTool = 'pan'"><Hand :size="16" />平移</button>
      <select v-model.number="zoom" data-testid="canvas-zoom" aria-label="画布缩放"><option :value="75">75%</option><option :value="100">100%</option><option :value="125">125%</option><option :value="150">150%</option></select>
      <button data-testid="canvas-undo" aria-label="撤销" :disabled="!canUndo" @click="canvas.undo"><Undo2 :size="16" /></button>
      <button data-testid="canvas-redo" aria-label="重做" :disabled="!canRedo" @click="canvas.redo"><Redo2 :size="16" /></button>
      <span data-testid="warehouse-save-state" class="save-state">{{ saving ? '保存中…' : dirty ? '未保存' : '已保存' }}</span>
      <button data-testid="save-warehouse-layout" :disabled="issues.length > 0 || saving || loading" @click="saveLayout">保存布局</button>
    </div>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error && !drawerOpen && !pendingArea && !partialOpen && !selectedArea && !selectedBlock" role="alert" class="error">{{ error }}</p>
    <div data-testid="warehouse-canvas-summary" class="summary">
      <strong>{{ summary.totalUnits.toLocaleString('en-US') }} 个库存总量</strong>
      <span>{{ state.areas.length }} 个区域</span><span>{{ summary.skuCount }} 种 SKU</span>
      <span>布局尺寸不代表容量</span>
    </div>
    <div class="workspace-scroll"><div class="workspace">
      <aside class="panel layers"><h2><Warehouse :size="16" /> 仓库与图层</h2>
        <div v-for="area in state.areas" :key="area.id" class="area-row">
          <button class="area-select" :aria-pressed="selectedAreaId === area.id" @click="chooseArea(area.id)"><strong>{{ area.name }}</strong><small>{{ summary.areaTotals[area.id] }} 个</small></button>
          <button :data-testid="`layer-visibility-${area.id}`" :aria-label="`${area.visible ? '隐藏' : '显示'} ${area.name}`" :aria-pressed="!area.visible" @click="canvas.toggleAreaVisibility(area.id)"><Eye v-if="area.visible" :size="14" /><EyeOff v-else :size="14" /></button>
          <button :data-testid="`layer-lock-${area.id}`" :aria-label="`${area.locked ? '解锁' : '锁定'} ${area.name}`" :aria-pressed="area.locked" @click="canvas.toggleAreaLock(area.id)"><Lock v-if="area.locked" :size="14" /><Unlock v-else :size="14" /></button>
        </div>
        <p>区域尺寸仅用于画布布局</p>
      </aside>
      <section class="panel floor-panel">
        <h2>仓库平面图 <span>库存单位：个</span></h2>
        <div ref="viewport" data-testid="canvas-viewport" class="canvas-viewport" :class="{ 'pan-tool': activeTool === 'pan' }" @pointerdown.capture="startPan" @pointermove="movePan" @pointerup="stopPan" @pointercancel="stopPan" @dragover.prevent @drop.prevent="dropProduct"><div data-testid="canvas-scaled-floor" class="floor-stage" :style="{ width: `${zoom}%` }">
          <WarehouseFloorCanvas :state="state" :tool="activeTool" :selected-area-id="selectedAreaId" :selected-block-id="selectedBlockId" :issues="issues" :search-query="searchQuery" @create-area="startArea" @select-area="chooseArea" @select-block="chooseBlock" @update-area-rect="updateAreaRect" @update-block-rect="updateBlockRect" />
          <div v-if="pendingArea" data-testid="pending-area-preview" class="pending-area" :style="{ left: `${pendingArea.x / 728 * 100}%`, top: `${pendingArea.y / 672 * 100}%`, width: `${pendingArea.width / 728 * 100}%`, height: `${pendingArea.height / 672 * 100}%` }">{{ areaName }}</div>
        </div></div>
        <div v-if="issues.length" data-testid="warehouse-layout-issues" class="issue-list" role="alert"><h3><TriangleAlert :size="16" />请调整布局后保存</h3>
          <article v-for="issue in issues" :key="issue.id"><span>{{ issue.type === 'outside-area' ? '产品块越出所属区域边界' : '产品块发生重叠' }}</span><button v-for="id in issue.blockIds" :key="id" :aria-label="`定位 ${id}`" @click="locateBlock(id)">定位产品</button></article>
        </div>
        <p>产品块大小仅表达大致占地，不随库存个数变化。</p>
      </section>
      <WarehouseObjectPanel
        v-if="selectedArea || selectedBlock"
        :area="selectedArea" :block="selectedBlock" :sku="selectedSku" :areas="state.areas" :error="error"
        @close="clearSelection"
        @rename="(name) => run(() => { if (!name.trim()) throw new Error('请输入区域名称'); canvas.renameArea(selectedArea!.id, name.trim()) })"
        @area-rect="(rect) => updateAreaRect({ id: selectedArea!.id, ...rect })"
        @block-rect="(rect) => run(() => canvas.updateBlockRect(selectedBlock!.id, rect))"
        @units="(units) => run(() => canvas.updateBlockUnits(selectedBlock!.id, units))"
        @lock="canvas.toggleAreaLock(selectedArea!.id)"
        @move-whole="moveBlock" @partial-move="openPartial" @delete-area="requestDeleteArea"
        @delete-block="run(() => canvas.deleteBlock(selectedBlock!.id))"
      />
      <aside v-else data-testid="warehouse-product-library" class="panel library">
        <h2><Boxes :size="16" /> 产品库</h2><p>库存以“个”为权威值</p>
        <article v-for="sku in filteredCatalog" :key="sku.skuId" :data-testid="`library-sku-${sku.skuId}`" class="product-card" :class="`accent-${sku.accent}`" draggable="true" @dragstart="startDrag($event, sku.skuId)">
          <strong>{{ sku.productName }}</strong><small>{{ sku.skuCode }}</small>
          <span>{{ skuTotal(sku.skuId) }} 个</span><small>{{ sku.unitsPerCase }} 个/件</small>
          <button @click="openProduct(sku.skuId)">放入区域</button>
        </article>
        <p v-if="!filteredCatalog.length">没有匹配的 SKU</p><p>拖入画布区域，或点击“放入区域”。</p>
      </aside>
    </div></div>
    <AccessibleDialog :open="pendingArea !== null" title="命名区域" test-id="area-name-dialog" body-test-id="area-name-body" footer-test-id="area-name-footer" close-test-id="area-name-close" panel-class="w-[400px] max-w-full" @cancel="pendingArea = null">
      <div v-if="pendingArea" class="area-form"><label>区域名称<input v-model="areaName" data-testid="area-name-input" /></label>
        <div class="rect-grid"><label v-for="field in rectFields" :key="field">{{ field }}<input v-model.number="pendingArea[field]" type="number" :aria-label="`区域 ${field}`" /></label></div>
        <p>布局尺寸不代表容量。</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      </div>
      <template #footer><button data-testid="create-area-cancel" @click="pendingArea = null">取消</button><button data-testid="create-area-confirm" class="primary" @click="confirmArea">创建区域</button></template>
    </AccessibleDialog>
    <WarehouseProductDrawer :open="drawerOpen" :catalog="state.catalog" :areas="state.areas" :initial-sku-id="drawerSkuId" :initial-area-id="drawerAreaId" :error="error" @cancel="drawerOpen = false" @confirm="addProduct" />
    <AccessibleDialog :open="partialOpen && !!selectedBlock" title="移动部分库存" test-id="partial-move-dialog" body-test-id="partial-move-body" footer-test-id="partial-move-footer" close-test-id="partial-move-close" panel-class="w-[440px] max-w-full" @cancel="partialOpen = false">
      <div v-if="selectedBlock" class="area-form"><strong>{{ selectedSku?.productName }}</strong><p>来源库存 {{ selectedBlock.units }} 个</p>
        <label>移动个数<input v-model="partialUnits" data-testid="partial-move-units" type="number" min="1" :max="selectedBlock.units - 1" step="1" /></label>
        <label>目标区域<select v-model="partialTarget" data-testid="partial-move-area"><option v-for="area in moveTargets" :key="area.id" :value="area.id">{{ area.name }}</option></select></label>
        <output data-testid="partial-move-conservation">{{ selectedBlock.units - Number(partialUnits) }} + {{ partialUnits }} = {{ selectedBlock.units }} 个</output>
        <p>原产品块扣减移动个数，目标区域生成新产品块。</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      </div>
      <template #footer><button @click="partialOpen = false">取消</button><button data-testid="partial-move-confirm" class="primary" @click="moveBlock(partialTarget, partialUnits === '' ? NaN : Number(partialUnits))">确认移动</button></template>
    </AccessibleDialog>
    <AccessibleDialog :open="deleteAreaId !== null" :title="deleteFinal ? '再次确认删除' : '区域内含有产品'" test-id="delete-area-dialog" body-test-id="delete-area-body" footer-test-id="delete-area-footer" close-test-id="delete-area-close" panel-class="w-[460px] max-w-full" @cancel="deleteAreaId = null">
      <p>{{ deletingArea?.name }} 中有 {{ deleteCount }} 个产品块。{{ deleteFinal ? '确认一并删除区域及这些产品块？可使用撤销恢复。' : '请先移动产品，或选择一并删除。' }}</p>
      <template #footer><button @click="deleteAreaId = null">取消</button><button v-if="!deleteFinal" data-testid="delete-area-move-first" @click="moveFirst">先移动产品</button><button v-if="!deleteFinal" data-testid="delete-area-together" class="danger-button" @click="deleteFinal = true">一并删除</button><button v-else data-testid="delete-area-confirm" class="danger-button" @click="confirmDeleteArea">确认一并删除</button></template>
    </AccessibleDialog>
  </main>
</template>

<style scoped>
.warehouse-page { background:#F6F7FB; color:#25314D; padding:20px; font-family:'Noto Sans SC Variable',Inter,sans-serif; font-size:12px; }
.page-header,.summary,h1,h2 { display:flex; align-items:center; gap:12px; }
.page-header { justify-content:space-between; flex-wrap:wrap; margin-bottom:16px; }
h1 { font-size:20px; font-weight:700; } h2 { font-size:13px; font-weight:700; margin-bottom:16px; }
p,small { color:#64748B; } p { margin-top:8px; line-height:1.7; }
select,input,button { min-height:40px; border:1px solid #E2E8F0; border-radius:8px; padding:8px 12px; background:white; }
button { display:inline-flex; align-items:center; justify-content:center; gap:6px; } button:disabled { opacity:.45; cursor:not-allowed; } .primary { background:#536DFF; color:white; border-color:#536DFF; }.primary:hover:enabled { background:#465EEA; }
.toolbar { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; } .floor-stage { position:relative; } .pending-area { position:absolute; border:2px dashed #536DFF; background:#536DFF15; pointer-events:none; padding:8px; }
.search { display:flex; align-items:center; gap:8px; } .search input { width:190px; }.save-state { align-self:center; color:#16A36A; } button[aria-pressed="true"] { background:#536DFF12; color:#536DFF; border-color:#536DFF; }
.canvas-viewport { overflow:auto; max-height:720px; touch-action:none; } .floor-stage { min-width:560px; }.pan-tool { cursor:grab; } .pan-tool:active { cursor:grabbing; }
.issue-list { margin-top:12px; padding:12px; border:1px solid #F59E0B; border-radius:8px; background:#FFFBEB; color:#92400E; }.issue-list h3,.issue-list article { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }.issue-list article { margin-top:8px; }.danger-button { background:#EF476F; color:white; border-color:#EF476F; }
.area-form,.area-form label { display:grid; gap:8px; } .rect-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; } .rect-grid input { width:100%; } .error { color:#EF476F; }
.summary { background:white; border:1px solid #E2E8F0; border-radius:8px; padding:14px 16px; margin-bottom:16px; flex-wrap:wrap; } .summary strong { font-size:16px; } .summary span { color:#64748B; }
.workspace-scroll { overflow-x:auto; } .workspace { display:grid; grid-template-columns:196px minmax(560px,1fr) 240px; gap:12px; align-items:start; }
.panel { background:#FFF; border:1px solid #E2E8F0; border-radius:8px; padding:16px; min-width:0; }
.floor-panel h2 { justify-content:space-between; } .floor-panel h2 span { font-size:11px; color:#94A3B8; font-weight:400; }
.area-row { display:flex; align-items:center; gap:4px; padding:8px 0; border-bottom:1px solid #E2E8F0; } .area-row button { padding:4px; border-color:transparent; min-width:28px; }.area-row .area-select { display:grid; justify-content:start; text-align:left; flex:1; }.area-row small { font-size:10px; }
.product-card { display:grid; gap:6px; margin-top:12px; padding:12px; background:#F8FAFC; border:1px solid #E2E8F0; border-radius:8px; }
.accent-blue { border-left:3px solid #536DFF; }.accent-pink { border-left:3px solid #EF476F; }.accent-green { border-left:3px solid #16A36A; }.accent-cyan { border-left:3px solid #06B6D4; }
@media (min-width:1024px) and (max-width:1179px) { .workspace { min-width:1020px; } }
@media (max-width:1023px) { .workspace { display:flex; flex-direction:column; } .panel { width:100%; } .floor-panel { min-width:560px; } .warehouse-page { padding:12px; } }
</style>
