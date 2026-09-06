<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { routeLocationKey } from 'vue-router'
import { Warehouse, Plus, SquareDashedMousePointer, MousePointer2, Hand, Undo2, Redo2, Search, Eye, EyeOff, Lock, Unlock, TriangleAlert, X } from 'lucide-vue-next'
import AccessibleDialog from '../../../components/AccessibleDialog.vue'
import WarehouseFloorCanvas from '../warehouseCanvas/components/WarehouseFloorCanvas.vue'
import WarehouseBlueprintScene from '../warehouseCanvas/components/WarehouseBlueprintScene.vue'
import WarehousePlannerChrome from '../warehouseCanvas/components/WarehousePlannerChrome.vue'
import type { PlannerUiTool } from '../warehouseCanvas/components/WarehousePlannerChrome.vue'
import WarehouseProductDrawer from '../warehouseCanvas/components/WarehouseProductDrawer.vue'
import WarehouseObjectPanel from '../warehouseCanvas/components/WarehouseObjectPanel.vue'
import { useWarehouseCanvas } from '../warehouseCanvas/useWarehouseCanvas'
import { findOpenPosition, formatCaseBreakdown, summarizeCanvas } from '../warehouseCanvas/warehouseCanvasModel'
import type { CanvasRect } from '../warehouseCanvas/types'
import { warehousePlannerScene } from '../warehouseCanvas/warehousePlannerScene'
import type { PlannerPalletGroup } from '../warehouseCanvas/warehousePlannerScene'
import { cloneStructure, createWarehouseStructure, validatePlannerLayout } from '../warehouseCanvas/warehouseStructure'
import type { WarehouseStructure, StructureIssue } from '../warehouseCanvas/warehouseStructure'

const canvas = useWarehouseCanvas()
const route = inject(routeLocationKey, null)
const warehouseLimitation = computed(() => route?.query.warehouseId && route.query.warehouseId !== '1'
  ? `当前仅支持演示仓库（ID 1）；所选仓库 ID ${route.query.warehouseId} 尚未加载。`
  : '单仓库演示：保存仅在当前页面有效，刷新后重置。')
const visibleWarehouseNotice = computed(() => route?.query.warehouseId && route.query.warehouseId !== '1'
  ? warehouseLimitation.value
  : '')
const { state, activeTool, selectedAreaId, selectedBlockId, issues, searchQuery } = canvas
const { dirty, saving, loading, canUndo, canRedo } = canvas
const summary = computed(() => summarizeCanvas(state.value))
const selectedArea = computed(() => state.value.areas.find((area) => area.id === selectedAreaId.value))
const selectedBlock = computed(() => state.value.blocks.find((block) => block.id === selectedBlockId.value))
const selectedSku = computed(() => state.value.catalog.find((sku) => sku.skuId === selectedBlock.value?.skuId))
const filteredCatalog = computed(() => state.value.catalog.filter((sku) => `${sku.productName} ${sku.skuCode}`.toLocaleLowerCase().includes(searchQuery.value.trim().toLocaleLowerCase())))
const skuTotal = (skuId: number) => state.value.blocks.filter((block) => block.skuId === skuId).reduce((total, block) => total + block.units, 0)
const areaSkuCount = (areaId: string) => new Set(state.value.blocks.filter((block) => block.areaId === areaId).map((block) => block.skuId)).size
const pendingArea = ref<CanvasRect | null>(null)
const areaName = ref('D-01')
const error = ref('')
const notice = ref('')
const plannerTool = ref<PlannerUiTool>('goods')
const measurementEnabled = ref(false)
const gridSnapping = ref(true)
const plannerCompleted = ref(false)
const plannerStructure = ref(createWarehouseStructure())
const plannerStructureIssues = ref<StructureIssue[]>([])
const plannerStructureBusy = ref(false)
const plannerIssuesOpen = ref(false)
const focusedStructureId = ref<string | null>(null)
const selectedPalletId = ref<string | null>('pallet-c018')
function clonePlannerPalletGroups(groups: readonly PlannerPalletGroup[]): PlannerPalletGroup[] {
  return groups.map((pallet) => ({
    ...pallet,
    contents: pallet.contents.map((item) => ({ ...item })),
  }))
}
const plannerPalletGroups = ref<PlannerPalletGroup[]>(clonePlannerPalletGroups(warehousePlannerScene.palletGroups))
type PlannerSnapshot = {
  tool: PlannerUiTool
  measurementEnabled: boolean
  gridSnapping: boolean
  selectedPalletId: string | null
  palletGroups: PlannerPalletGroup[]
  structure: WarehouseStructure
}
const plannerUndoStack = ref<PlannerSnapshot[]>([])
const plannerRedoStack = ref<PlannerSnapshot[]>([])
const plannerCanUndo = computed(() => plannerUndoStack.value.length > 0)
const plannerCanRedo = computed(() => plannerRedoStack.value.length > 0)
const issuesCollapsed = ref(false)
watch(() => issues.value.length, (count, previous) => { if (count === 0 || count > previous) issuesCollapsed.value = false })
const drawerOpen = ref(false)
const drawerSkuId = ref<number>()
const drawerAreaId = ref<string>()
const partialOpen = ref(false)
const partialUnits = ref<number | string>(60)
const partialTarget = ref('')
const partialUnitsValid = computed(() => {
  const units = Number(partialUnits.value)
  return partialUnits.value !== '' && Number.isInteger(units) && units > 0 && units < (selectedBlock.value?.units ?? 0)
})
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
function capturePlannerSnapshot(): PlannerSnapshot {
  return {
    tool: plannerTool.value,
    measurementEnabled: measurementEnabled.value,
    gridSnapping: gridSnapping.value,
    selectedPalletId: selectedPalletId.value,
    palletGroups: clonePlannerPalletGroups(plannerPalletGroups.value),
    structure: cloneStructure(plannerStructure.value),
  }
}
function applyPlannerSnapshot(snapshot: PlannerSnapshot) {
  plannerTool.value = snapshot.tool
  measurementEnabled.value = snapshot.measurementEnabled
  gridSnapping.value = snapshot.gridSnapping
  selectedPalletId.value = snapshot.selectedPalletId
  plannerPalletGroups.value = clonePlannerPalletGroups(snapshot.palletGroups)
  plannerStructure.value = cloneStructure(snapshot.structure)
  activeTool.value = snapshot.tool === 'zone' ? 'draw' : snapshot.tool === 'measure' ? 'pan' : 'select'
}
function changePlannerState(change: () => void) {
  const before = capturePlannerSnapshot()
  change()
  const after = capturePlannerSnapshot()
  if (JSON.stringify(before) === JSON.stringify(after)) return
  plannerUndoStack.value = [...plannerUndoStack.value.slice(-49), before]
  plannerRedoStack.value = []
}
function changePlannerTool(tool: PlannerUiTool) {
  if (plannerCompleted.value) return
    plannerTool.value = tool
    activeTool.value = tool === 'zone' ? 'draw' : tool === 'measure' ? 'pan' : 'select'
    if (tool === 'measure') measurementEnabled.value = true
}
function togglePlannerMeasurement() {
  changePlannerState(() => { measurementEnabled.value = !measurementEnabled.value })
}
function togglePlannerGrid() {
  changePlannerState(() => { gridSnapping.value = !gridSnapping.value })
}
function selectPlannerPallet(id: string | null) {
  selectedPalletId.value = id
}
function changePlannerStructure(value: WarehouseStructure) {
  changePlannerState(() => { plannerStructure.value = cloneStructure(value) })
}
async function locatePlannerIssue(issue: StructureIssue) {
  const pile = plannerPalletGroups.value.find((p) => issue.objectIds.includes(p.id))
  if (pile) { plannerTool.value='goods'; selectedPalletId.value=pile.id }
  else { plannerTool.value='structure'; focusedStructureId.value=null; await nextTick(); focusedStructureId.value=issue.objectIds[0] ?? null }
  await nextTick()
  const root = document.querySelector('[data-testid="warehouse-blueprint-scene"]')
  const target = pile ? [...(root?.querySelectorAll<HTMLElement>('[data-testid^="planner-pallet-"]') ?? [])].find((e)=>e.dataset.testid===`planner-pallet-${pile.id}`) : root?.querySelector('.structure-toolbar')
  target?.scrollIntoView?.({block:'nearest',inline:'nearest'})
  if (pile) (target as HTMLElement | undefined)?.focus?.({preventScroll:true})
}
function movePlannerPallet(move: { id: string; left: number; top: number }) {
  changePlannerState(() => {
    plannerPalletGroups.value = plannerPalletGroups.value.map((pallet) => {
      if (pallet.id !== move.id) return pallet
      return {
        ...pallet,
        left: move.left,
        top: move.top,
        xMeters: Number((pallet.xMeters + (move.left - pallet.left) * .6).toFixed(1)),
        yMeters: Number((pallet.yMeters + (move.top - pallet.top) * .4).toFixed(1)),
      }
    })
    selectedPalletId.value = move.id
  })
}
function undoPlanner() {
  if (plannerCompleted.value || plannerStructureBusy.value) return
  const previous = plannerUndoStack.value.at(-1)
  if (!previous) return
  plannerRedoStack.value = [...plannerRedoStack.value, capturePlannerSnapshot()]
  plannerUndoStack.value = plannerUndoStack.value.slice(0, -1)
  applyPlannerSnapshot(previous)
}
function redoPlanner() {
  if (plannerCompleted.value || plannerStructureBusy.value) return
  const next = plannerRedoStack.value.at(-1)
  if (!next) return
  plannerUndoStack.value = [...plannerUndoStack.value, capturePlannerSnapshot()]
  plannerRedoStack.value = plannerRedoStack.value.slice(0, -1)
  applyPlannerSnapshot(next)
}
function completeUiPreview() {
  if (!plannerCompleted.value) {
    plannerStructureIssues.value=validatePlannerLayout(plannerStructure.value,plannerPalletGroups.value)
    if (plannerStructureBusy.value || plannerStructureIssues.value.length) { plannerIssuesOpen.value=true;return }
  }
  plannerCompleted.value = !plannerCompleted.value
  notice.value = plannerCompleted.value
    ? '规划已完成，点击货堆查看详情'
    : '已返回规划编辑'
}
function chooseArea(id: string) { error.value = ''; canvas.selectArea(id) }
function chooseBlock(id: string) { error.value = ''; canvas.selectBlock(id) }
async function locateBlock(id: string) {
  chooseBlock(id)
  issuesCollapsed.value = true
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
    const positionChanged = rect.x !== block.x || rect.y !== block.y
    const target = positionChanged && rect.width === block.width && rect.height === block.height
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
function startArea(rect: CanvasRect) { clearSelection(); areaName.value = 'D-01'; pendingArea.value = { ...rect } }
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
    <section class="planner-prototype" data-fullscreen="true" aria-label="仓库平面规划 UI 预览">
      <WarehousePlannerChrome
        :warehouse-name="state.warehouseName"
        :context-notice="visibleWarehouseNotice"
        :active-tool="plannerTool"
        :measurement-enabled="measurementEnabled"
        :grid-snapping="gridSnapping"
        :can-undo="plannerCanUndo && !plannerCompleted && !plannerStructureBusy"
        :can-redo="plannerCanRedo && !plannerCompleted && !plannerStructureBusy"
        :completed="plannerCompleted"
        :completion-blocked="!plannerCompleted && (plannerStructureBusy || plannerStructureIssues.length > 0)"
        :completion-reason="plannerStructureBusy ? '请先结束当前操作' : `请先处理 ${plannerStructureIssues.length} 个规划问题`"
        @change-tool="changePlannerTool"
        @toggle-measurement="togglePlannerMeasurement"
        @toggle-grid="togglePlannerGrid"
        @undo="undoPlanner"
        @redo="redoPlanner"
        @complete="completeUiPreview"
      />
      <div class="planner-canvas-scroll">
        <WarehouseBlueprintScene
          :grid-snapping="gridSnapping"
          :inventory-details-visible="plannerCompleted"
          :measurement-enabled="measurementEnabled"
          :selected-pallet-id="selectedPalletId"
          :pallet-groups="plannerPalletGroups"
          :structure="plannerStructure"
          :structure-editing="plannerTool === 'structure' && !plannerCompleted"
          :focused-structure-id="focusedStructureId"
          @change-structure="changePlannerStructure"
          @structure-issues="plannerStructureIssues = $event"
          @structure-busy="plannerStructureBusy = $event"
          @select-pallet="selectPlannerPallet"
          @move-pallet="movePlannerPallet"
        />
      </div>
      <aside v-if="plannerStructureIssues.length && !plannerCompleted" class="planner-structure-issues" aria-label="规划问题">
        <button class="structure-issues-toggle" data-testid="planner-structure-issues-toggle" :aria-expanded="plannerIssuesOpen" @click="plannerIssuesOpen = !plannerIssuesOpen"><TriangleAlert :size="16" />{{ plannerStructureIssues.length }} 个规划问题 · {{ plannerIssuesOpen ? '收起' : '查看' }}</button>
        <div v-if="plannerIssuesOpen" class="structure-issue-items">
          <div v-for="issue in plannerStructureIssues" :key="issue.id"><span>{{ issue.message }}</span><button @click="locatePlannerIssue(issue)">定位</button></div>
        </div>
      </aside>
    </section>
    <p v-if="notice" role="status" class="planner-toast">{{ notice }}</p>
    <section class="legacy-editor" aria-hidden="true">
    <header class="page-header">
      <div><h1>仓库画布 <span class="area-badge">{{ state.areas.length }} 个区域</span></h1><p>绘制区域并放入 SKU；库存仅以“个”为权威值。</p></div>
      <button data-testid="add-warehouse-product" class="primary" @click="openProduct()"><Plus :size="16" />添加产品</button>
    </header>
    <div class="toolbar">
      <label class="warehouse-selector">仓库<select aria-label="当前仓库"><option>{{ state.warehouseName }}</option></select><small data-testid="warehouse-demo-limitation">{{ warehouseLimitation }}</small></label>
      <label class="search"><span>SKU 搜索</span><span class="search-control"><Search :size="20" /><input v-model="searchQuery" data-testid="warehouse-sku-search" aria-label="搜索 SKU 或产品名称" placeholder="搜索 SKU / 产品名称" /></span><small>{{ searchQuery ? `${canvas.matchedBlockIds.value.length} 个匹配块；非匹配项已淡化` : '按 SKU 编码或产品名称搜索' }}</small></label>
      <div class="tool-group">
      <button data-testid="canvas-tool-select" title="选择" aria-label="选择" :aria-pressed="activeTool === 'select'" @click="activeTool = 'select'"><MousePointer2 :size="20" /></button>
      <button data-testid="canvas-tool-draw" title="绘制区域" aria-label="绘制区域" :aria-pressed="activeTool === 'draw'" @click="activeTool = 'draw'"><SquareDashedMousePointer :size="20" /></button>
      <button data-testid="canvas-tool-pan" title="平移" aria-label="平移" :aria-pressed="activeTool === 'pan'" @click="activeTool = 'pan'"><Hand :size="20" /></button>
      <select v-model.number="zoom" data-testid="canvas-zoom" aria-label="画布缩放"><option :value="75">75%</option><option :value="100">100%</option><option :value="125">125%</option><option :value="150">150%</option></select>
      <button data-testid="canvas-undo" aria-label="撤销" :disabled="!canUndo" @click="canvas.undo"><Undo2 :size="16" /></button>
      <button data-testid="canvas-redo" aria-label="重做" :disabled="!canRedo" @click="canvas.redo"><Redo2 :size="16" /></button>
      </div>
      <button data-testid="save-warehouse-layout" class="save-state" :disabled="issues.length > 0 || saving || loading" @click="saveLayout"><span data-testid="warehouse-save-state">{{ saving ? '保存中…' : dirty ? '未保存 · 保存' : '已保存' }}</span></button>
    </div>
    <p v-if="error && !drawerOpen && !pendingArea && !partialOpen && !selectedArea && !selectedBlock" role="alert" class="error">{{ error }}</p>
    <div class="workspace-scroll"><div class="workspace">
      <aside class="panel layers"><h2>区域与图层</h2>
        <div data-testid="warehouse-canvas-summary" class="summary">
          <span>{{ state.areas.length }} 个区域</span><span>{{ summary.skuCount }} 个 SKU</span>
          <strong>{{ summary.totalUnits.toLocaleString('en-US') }} 个库存总量</strong>
        </div>
        <div class="list-label">区域清单</div>
        <div v-for="area in state.areas" :key="area.id" class="area-row">
          <button class="area-select" :aria-pressed="selectedAreaId === area.id" @click="chooseArea(area.id)"><span>{{ area.name }} · {{ summary.areaTotals[area.id] }} 个</span><small>{{ areaSkuCount(area.id) }} 个 SKU · {{ area.visible ? '可见' : '隐藏' }}</small></button>
          <button :data-testid="`layer-visibility-${area.id}`" :aria-label="`${area.visible ? '隐藏' : '显示'} ${area.name}`" :aria-pressed="!area.visible" @click="canvas.toggleAreaVisibility(area.id)"><Eye v-if="area.visible" :size="14" /><EyeOff v-else :size="14" /></button>
          <button :data-testid="`layer-lock-${area.id}`" :aria-label="`${area.locked ? '解锁' : '锁定'} ${area.name}`" :aria-pressed="area.locked" @click="canvas.toggleAreaLock(area.id)"><Lock v-if="area.locked" :size="14" /><Unlock v-else :size="14" /></button>
        </div>
      </aside>
      <section class="panel floor-panel">
        <h2>仓库平面 · 画布布局</h2><p class="floor-subtitle">区域与 SKU 尺寸均为手动视觉数据；不计算容量或库存</p>
        <div ref="viewport" data-testid="canvas-viewport" class="canvas-viewport" :class="{ 'pan-tool': activeTool === 'pan' }" @pointerdown.capture="startPan" @pointermove="movePan" @pointerup="stopPan" @pointercancel="stopPan" @dragover.prevent @drop.prevent="dropProduct"><div data-testid="canvas-scaled-floor" class="floor-stage" :style="{ width: `${zoom}%` }">
          <WarehouseFloorCanvas :state="state" :tool="activeTool" :selected-area-id="selectedAreaId" :selected-block-id="selectedBlockId" :issues="issues" :search-query="searchQuery" @create-area="startArea" @select-area="chooseArea" @select-block="chooseBlock" @update-area-rect="updateAreaRect" @update-block-rect="updateBlockRect" />
          <div v-if="pendingArea" data-testid="pending-area-preview" class="pending-area" :style="{ left: `${pendingArea.x / 728 * 100}%`, top: `${pendingArea.y / 672 * 100}%`, width: `${pendingArea.width / 728 * 100}%`, height: `${pendingArea.height / 672 * 100}%` }">{{ areaName }}</div>
        </div></div>
      </section>
      <aside v-if="pendingArea" class="panel pending-properties"><h2>区域属性 · {{ areaName }}</h2><label>区域名称<input :value="areaName" readonly /></label><label v-for="field in rectFields" :key="field">{{ {x:'X',y:'Y',width:'宽度',height:'高度'}[field] }}<input :value="pendingArea[field]" readonly /></label><p>区域大小仅用于布局；不计算容量，也不改变库存个数。</p></aside>
      <WarehouseObjectPanel
        v-else-if="selectedArea || selectedBlock"
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
        <h2>产品库 · SKU 汇总</h2><p class="inventory-rule">库存以“个”为权威值<br />件数为只读换算</p>
        <article v-for="sku in state.catalog" :key="sku.skuId" :data-testid="`library-sku-${sku.skuId}`" class="product-card" :class="{ 'is-dimmed': !filteredCatalog.includes(sku), 'is-match': searchQuery && filteredCatalog.includes(sku) }" draggable="true" @dragstart="startDrag($event, sku.skuId)">
          <span>{{ sku.skuCode }} · {{ skuTotal(sku.skuId) }} 个 · {{ sku.unitsPerCase }} 个/件 · {{ formatCaseBreakdown(skuTotal(sku.skuId), sku.unitsPerCase) }}</span>
          <button :aria-label="`放入区域：${sku.productName}`" @click="openProduct(sku.skuId)">放入区域</button>
        </article>
        <p v-if="!filteredCatalog.length">没有匹配的 SKU</p><p>拖入画布区域，或点击“放入区域”。</p>
      </aside>
    </div></div>
    <button v-if="issues.length && issuesCollapsed" data-testid="expand-layout-issues" class="issue-toggle" @click="issuesCollapsed = false"><TriangleAlert :size="18" />布局问题（{{ issues.length }}）</button>
    <aside v-if="issues.length && !issuesCollapsed" data-testid="warehouse-layout-issues" class="issue-list" role="alert"><h3><TriangleAlert :size="20" />布局问题（{{ issues.length }}）<button data-testid="collapse-layout-issues" aria-label="收起布局问题，编辑对象属性" class="issue-close" @click="issuesCollapsed = true"><X :size="16" /></button></h3><p>请调整布局后保存；库存个数与件数换算保持不变。</p>
      <article v-for="issue in issues" :key="issue.id"><strong>{{ issue.type === 'outside-area' ? '产品块越出所属区域边界' : '产品块发生重叠' }}</strong><p>{{ issue.message }}</p><button v-for="(id, index) in issue.blockIds" :key="id" data-testid="locate-layout-issue" :aria-label="`定位问题产品 ${index + 1}`" @click="locateBlock(id)">定位产品{{ issue.blockIds.length > 1 ? ` ${index + 1}` : '' }}</button></article>
      <button disabled class="issue-save">保存布局</button>
    </aside>
    </section>
    <AccessibleDialog :open="pendingArea !== null" :title="`区域名称: ${areaName}`" test-id="area-name-dialog" body-test-id="area-name-body" footer-test-id="area-name-footer" close-test-id="area-name-close" panel-class="warehouse-area-name-dialog" @cancel="pendingArea = null">
      <div v-if="pendingArea" class="area-form"><label>区域名称<input v-model="areaName" data-testid="area-name-input" /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
      </div>
      <template #footer><button data-testid="create-area-cancel" @click="pendingArea = null">取消</button><button data-testid="create-area-confirm" class="primary" @click="confirmArea">创建区域</button></template>
    </AccessibleDialog>
    <WarehouseProductDrawer :open="drawerOpen" :catalog="state.catalog" :areas="state.areas" :initial-sku-id="drawerSkuId" :initial-area-id="drawerAreaId" :error="error" @cancel="drawerOpen = false" @confirm="addProduct" />
    <AccessibleDialog :open="partialOpen && !!selectedBlock" title="移动部分库存" :description="`${selectedSku?.skuCode ?? ''} · ${selectedSku?.productName ?? ''} · 来源 ${state.areas.find((area) => area.id === selectedBlock?.areaId)?.name ?? ''} · 可移动 1–${(selectedBlock?.units ?? 1) - 1} 个`" test-id="partial-move-dialog" body-test-id="partial-move-body" footer-test-id="partial-move-footer" close-test-id="partial-move-close" overlay-class="items-end justify-end" panel-class="warehouse-canvas-flow-drawer" body-class="warehouse-drawer-body" @cancel="partialOpen = false">
      <div v-if="selectedBlock" class="area-form partial-form">
        <div class="partial-inputs"><label>目标区域<select v-model="partialTarget" data-testid="partial-move-area"><option v-for="area in moveTargets" :key="area.id" :value="area.id">{{ area.name }}</option></select><small>与来源区域不同</small></label>
        <label>移动个数<input v-model="partialUnits" data-testid="partial-move-units" type="number" min="1" :max="selectedBlock.units - 1" step="1" :aria-invalid="!partialUnitsValid" /><small>正整数且小于 {{ selectedBlock.units }}</small></label></div>
        <div v-if="partialUnitsValid" class="conservation-card">
          <p>来源产品块：{{ selectedSku?.productName }} · {{ state.areas.find((area) => area.id === selectedBlock?.areaId)?.name }} / {{ selectedBlock.units }} 个</p>
          <p>移动：{{ partialUnits }} 个</p>
          <p>移动后来源：{{ selectedSku?.productName }} · {{ selectedBlock.units - Number(partialUnits) }} 个</p>
          <p>新目标产品块：{{ state.areas.find((area) => area.id === partialTarget)?.name }} · {{ partialUnits }} 个</p>
          <output data-testid="partial-move-conservation">{{ selectedBlock.units - Number(partialUnits) }} + {{ partialUnits }} = {{ selectedBlock.units }} 个</output>
        </div>
        <p v-else role="alert" class="error">移动个数必须是大于 0 且小于来源库存的整数</p>
        <div class="move-rules"><strong>移动规则</strong><p>移动数量必须是大于 0 的整数，且小于来源库存。</p><p>确认后，原产品块扣减移动个数，目标区域生成新产品块。</p><p>库存总个数保持不变，产品块大小不随数量自动改变。</p><p v-if="error && partialUnitsValid" role="alert" class="error">{{ error }}</p></div>
      </div>
      <template #footer><button @click="partialOpen = false">取消</button><button data-testid="partial-move-confirm" class="primary" :disabled="!partialUnitsValid || !moveTargets.some((area) => area.id === partialTarget)" @click="moveBlock(partialTarget, Number(partialUnits))">确认移动 {{ partialUnitsValid ? `${partialUnits} 个` : '' }}</button></template>
    </AccessibleDialog>
    <AccessibleDialog :open="deleteAreaId !== null" :title="deleteFinal ? '再次确认删除' : `删除非空区域 ${deletingArea?.name}？`" test-id="delete-area-dialog" body-test-id="delete-area-body" footer-test-id="delete-area-footer" close-test-id="delete-area-close" panel-class="warehouse-delete-dialog" @cancel="deleteAreaId = null">
      <p>{{ deletingArea?.name }} 中有 {{ deleteCount }} 个产品块。{{ deleteFinal ? '确认一并删除区域及这些产品块？可使用撤销恢复。' : '请先移动产品，或选择一并删除。' }}</p>
      <template #footer><button @click="deleteAreaId = null">取消</button><button v-if="!deleteFinal" data-testid="delete-area-move-first" @click="moveFirst">先移动产品</button><button v-if="!deleteFinal" data-testid="delete-area-together" class="danger-button" @click="deleteFinal = true">一并删除</button><button v-else data-testid="delete-area-confirm" class="danger-button" @click="confirmDeleteArea">确认一并删除</button></template>
    </AccessibleDialog>
  </main>
</template>

<style scoped>
.planner-structure-issues {position:absolute;z-index:36;top:116px;right:24px;max-width:320px;border:1px solid #fecdd3;border-radius:8px;background:#fff;box-shadow:0 4px 16px #25314d15;}
.structure-issues-toggle {display:flex;align-items:center;gap:7px;width:100%;padding:9px 12px;border:0;border-radius:8px;background:#fff1f2;color:#bc263c;font:inherit;font-size:12px;cursor:pointer;}
.structure-issue-items {max-height:280px;overflow:auto;padding:4px 10px;}
.structure-issue-items>div {display:flex;align-items:center;gap:8px;padding:9px 0;border-bottom:1px solid #f1f5f9;font-size:12px;}
.structure-issue-items>div>span {flex:1;}
.structure-issue-items button {border:0;border-radius:4px;padding:5px 8px;background:#eef2ff;color:#4663ee;cursor:pointer;white-space:nowrap;font:inherit;}
.warehouse-page { position:relative; height:100%; min-height:760px; background:#F6F7FB; color:#25314D; padding:0; font-family:'Noto Sans SC Variable',Inter,sans-serif; font-size:12px; }
.planner-prototype { position:relative; height:100%; min-height:760px; overflow:hidden; background:#f9fbfd; }
.planner-canvas-scroll { height:calc(100% - 56px); overflow:auto; }
.legacy-editor { display:none; }
.warehouse-page > p[role="status"] { position:fixed; z-index:55; bottom:18px; left:50%; margin:0; padding:9px 14px; border:1px solid #d8e0e8; border-radius:8px; background:#fff; color:#354159; box-shadow:0 8px 24px #25314d20; transform:translateX(-50%); }
.page-header,.summary,h1,h2 { display:flex; align-items:center; gap:12px; }
.page-header { justify-content:space-between; height:112px; margin:0; padding:24px 24px 12px; }
h1 { font-size:24px; line-height:32px; font-weight:700; } h2 { font-size:18px; line-height:26px; font-weight:500; margin-bottom:12px; }
.area-badge { border:1px solid #e2e8f0; border-radius:4px; padding:2px 8px; font-size:12px; line-height:18px; font-weight:400; color:#64748b; }
.page-header p { font-size:14px; line-height:22px; }
.page-header > button { width:128px; font-size:14px; }
p,small { color:#64748B; } p { margin-top:8px; line-height:1.7; }
select,input,button { min-height:40px; border:1px solid #E2E8F0; border-radius:8px; padding:8px 12px; background:white; }
button { display:inline-flex; align-items:center; justify-content:center; gap:6px; } button:disabled { opacity:.45; cursor:not-allowed; } .primary { background:#536DFF; color:white; border-color:#536DFF; }.primary:hover:enabled { background:#465EEA; }
.toolbar { display:grid; grid-template-columns:minmax(200px,1fr) minmax(230px,1fr) 360px 104px; gap:12px; height:104px; padding:8px 28px 12px; align-items:center; }
.warehouse-selector,.search { display:grid; gap:4px; align-self:start; }
.warehouse-selector select,.search-control { height:40px; width:100%; font-size:14px; }
.warehouse-selector small,.search small { font-size:11px; line-height:16px; color:#64748b; }
.search-control { display:flex; align-items:center; gap:8px; padding:0 12px; border:1px solid #e2e8f0; border-radius:8px; background:white; color:#64748b; }
.search-control input { border:0; padding:0; min-width:0; width:100%; outline:none; }
.search-control:focus-within { outline:2px solid #536dff; outline-offset:2px; }
.tool-group { display:flex; align-items:center; justify-content:center; gap:8px; height:56px; border:1px solid #e2e8f0; border-radius:8px; background:white; }
.tool-group button { width:40px; height:40px; padding:8px; color:#64748b; }
.tool-group select { width:60px; padding:0; border:0; font-size:14px; }
.floor-stage { position:relative; } .pending-area { position:absolute; border:2px dashed #25314d; background:transparent; pointer-events:none; padding:8px; }
.save-state { align-self:center; color:#25314d; background:#16a36a22; border:0; min-height:28px; padding:4px 8px; font-size:12px; } button[aria-pressed="true"] { background:#536DFF; color:white; border-color:#536DFF; }
.canvas-viewport { overflow:auto; max-height:720px; touch-action:none; } .floor-stage { min-width:560px; }.pan-tool { cursor:grab; } .pan-tool:active { cursor:grabbing; }
.issue-list { position:fixed; z-index:35; top:488px; right:0; bottom:0; width:360px; padding:20px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; color:#25314d; overflow:auto; }.issue-list h3 { display:flex; align-items:center; gap:8px; font-size:18px; font-weight:500; }.issue-list article { margin-top:12px; padding:12px; border:1px solid #f59e0b; background:#f8fafc; }.issue-list article strong { font-size:14px; font-weight:500; }.issue-list article p { margin:6px 0 10px; }.issue-list article button { width:100%; height:32px; min-height:32px; margin-top:4px; }.issue-save { width:100%; margin-top:12px; background:#f8fafc; color:#94a3b8; border:0; }.danger-button { background:#EF476F; color:white; border-color:#EF476F; }
.area-form,.area-form label { display:grid; gap:8px; } .rect-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; } .rect-grid input { width:100%; } .error { color:#EF476F; }
.pending-properties label { display:grid; gap:8px; margin-bottom:20px; }.pending-properties input { min-width:0; width:100%; }
.partial-form { gap:0; }.partial-inputs { display:grid; grid-template-columns:1fr 1fr; gap:16px; min-height:84px; }.partial-inputs input,.partial-inputs select { width:100%; font-size:14px; }.partial-inputs label { gap:4px; }.partial-inputs small { line-height:18px; }
.conservation-card { border:1px solid #e2e8f0; border-radius:8px; padding:12px 16px; min-height:150px; }.conservation-card p { margin:0 0 4px; color:#25314d; line-height:18px; }.conservation-card output { color:#16a36a; font-size:14px; line-height:22px; font-weight:500; }
.move-rules { border:1px solid #e2e8f0; border-radius:8px; padding:16px; min-height:200px; background:white; }.move-rules strong { font-weight:500; font-size:14px; }.move-rules p { font-size:12px; line-height:18px; }
.summary { display:grid; gap:4px; background:#F8FAFC; border:1px solid #E2E8F0; border-radius:6px; padding:8px 12px; margin-bottom:12px; font-size:14px; line-height:24px; } .summary strong { font-size:14px; font-weight:600; } .summary span { color:#25314d; }
.workspace-scroll { overflow-x:auto; } .workspace { display:grid; grid-template-columns:196px minmax(560px,1fr) 240px; gap:0; align-items:start; }
.panel { background:#FFF; border:0 solid #E2E8F0; padding:16px; min-width:0; height:743px; overflow-y:auto; }
.workspace { border-top:1px solid #e2e8f0; }
.layers { box-shadow:inset -1px 0 #e2e8f0; }
.floor-panel { background:#f8fafc; padding-top:11px; overflow:hidden; }
.floor-panel h2 { margin-bottom:0; } .floor-subtitle { margin:0 0 16px; font-size:12px; line-height:18px; }
.list-label { font-size:12px; margin-bottom:12px; }
.area-row { display:flex; flex-wrap:wrap; gap:0; position:relative; padding:8px 10px; margin-bottom:12px; border:1px solid #E2E8F0; border-radius:6px; background:#f8fafc; }
.area-row button { padding:0; border:0; min-height:22px; background:transparent; color:#25314d; }.area-row .area-select { display:grid; justify-content:start; text-align:left; width:100%; font-size:14px; line-height:22px; gap:0; }.area-row small { font-size:14px; color:inherit; }
.area-row > button:not(.area-select) { position:absolute; right:4px; top:4px; width:24px; min-height:24px; opacity:0; background:#f8fafc; }.area-row > button:last-child { top:auto; bottom:4px; }
.area-row:hover > button:not(.area-select),.area-row:focus-within > button:not(.area-select) { opacity:1; }
.area-row:has(.area-select[aria-pressed="true"]) { border-color:#536dff; }
.product-card { display:block; position:relative; margin-top:12px; padding:12px; height:104px; background:#F8FAFC; border:1px solid #E2E8F0; border-radius:6px; font-size:14px; line-height:22px; font-weight:500; }
.product-card button { position:absolute; bottom:4px; right:12px; padding:0; min-height:24px; border:0; background:#f8fafc; font-size:11px; justify-content:start; color:#536dff; opacity:0; }.product-card:hover button,.product-card:focus-within button { opacity:1; }
.product-card.is-dimmed { opacity:.55; }.product-card.is-match { border:2px solid #536dff; padding:11px; }
.inventory-rule { margin:0; min-height:62px; padding:8px 12px; border:1px solid #e2e8f0; border-radius:6px; background:#f8fafc; font-size:12px; line-height:18px; }
.accent-blue { border-left:3px solid #536DFF; }.accent-pink { border-left:3px solid #EF476F; }.accent-green { border-left:3px solid #16A36A; }.accent-cyan { border-left:3px solid #06B6D4; }
@media (min-width:1024px) and (max-width:1179px) { .workspace { min-width:996px; } }
@media (max-width:1250px) { .toolbar { grid-template-columns:1fr 1fr; height:auto; } .tool-group { justify-content:start; padding:8px; } }
@media (max-width:1023px) { .workspace { display:flex; flex-direction:column; } .panel { width:100%; height:auto; } .floor-panel { min-width:560px; } .warehouse-page { padding:12px; } .page-header { height:auto; } .toolbar { padding:16px 0; } }
</style>

<style>
/* Both warehouse workflows share the approved viewport-aligned drawer surface. */
.warehouse-canvas-flow-drawer {
  position: absolute;
  top: 264px;
  right: 0;
  bottom: 0;
  width: 720px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}
.warehouse-page .fixed:has(> .warehouse-canvas-flow-drawer) { background:transparent; }
.warehouse-canvas-flow-drawer > header { height:112px; min-height:112px; padding:24px; border:0; align-items:start; }
.warehouse-canvas-flow-drawer > header h2 { font-size:15px; line-height:22px; font-weight:500; margin:0; }
.warehouse-canvas-flow-drawer > header p { font-size:14px; line-height:22px; font-weight:400; margin-top:8px; }
.warehouse-canvas-flow-drawer > header button { border:0; min-height:24px; width:24px; height:24px; padding:0; }
.warehouse-drawer-body { flex:1; min-height:0; overflow:auto; margin:0 24px 16px; border-radius:8px; background:#f8fafc; }
.warehouse-canvas-flow-drawer > footer { height:88px; min-height:88px; margin:0 24px; padding:12px 0 36px; gap:8px; }
.warehouse-canvas-flow-drawer > footer button { width:144px; height:40px; font-size:14px; }
.warehouse-page .fixed:has(> .warehouse-area-name-dialog) { background:transparent; }
.warehouse-area-name-dialog { position:fixed; top:556px; right:272px; width:276px; }
.warehouse-area-name-dialog > header { padding:12px 12px 0; border:0; }.warehouse-area-name-dialog > header h2 { font-size:14px; font-weight:500; line-height:22px; margin:0; }.warehouse-area-name-dialog > header button { border:0; width:24px; height:24px; min-height:24px; padding:0; }
.warehouse-area-name-dialog > div { padding:8px 12px 12px; }.warehouse-area-name-dialog input { width:100%; min-height:32px; font-size:12px; padding:6px 8px; }
.warehouse-area-name-dialog > footer { border:0; padding:12px; gap:12px; }.warehouse-area-name-dialog > footer button { width:120px; min-height:32px; padding:4px 8px; }
.warehouse-page .fixed:has(> .warehouse-delete-dialog) { background:transparent; }
.warehouse-delete-dialog { position:fixed; top:448px; left:calc(50% - 132px); width:440px; }
.warehouse-delete-dialog > header { border:0; padding:20px 24px 8px; }.warehouse-delete-dialog > header h2 { font-size:15px; font-weight:500; line-height:22px; color:#ef476f; margin:0; }.warehouse-delete-dialog > header button { border:0; min-height:24px; padding:0; }
.warehouse-delete-dialog > div { padding:8px 24px 24px; color:#64748b; font-size:14px; line-height:22px; }.warehouse-delete-dialog > footer { border:0; padding:16px 24px 32px; }.warehouse-delete-dialog > footer button { font-size:14px; }
@media (max-width:1023px), (max-height:760px) { .warehouse-area-name-dialog { top:50%; right:50%; transform:translate(50%,-50%); } }
.issue-list h3 .issue-close { width:24px; min-height:24px; height:24px; margin-left:auto; padding:2px; border:0; }
.issue-toggle { position:fixed; z-index:35; right:16px; bottom:16px; display:flex; align-items:center; gap:8px; background:#fff7ed; border-color:#f59e0b; color:#9a3412; }
@media (max-width:1023px), (max-height:760px) { .warehouse-delete-dialog { top:50%; left:50%; width:min(440px,calc(100vw - 32px)); transform:translate(-50%,-50%); } .issue-list { top:320px; width:320px; } }
@media (max-width: 1023px), (max-height: 600px) {
  .warehouse-canvas-flow-drawer {
    top: 16px;
    right: 16px;
    bottom: 16px;
    width: min(720px, calc(100vw - 32px));
  }
}
</style>
