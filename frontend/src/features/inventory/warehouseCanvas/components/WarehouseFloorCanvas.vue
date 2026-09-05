<script setup lang="ts">
import { Check, Lock, TriangleAlert } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import type { CSSProperties } from 'vue'
import { formatCaseBreakdown } from '../warehouseCanvasModel'
import type {
  CanvasRect,
  CanvasTool,
  LayoutIssue,
  WarehouseArea,
  WarehouseCanvasState,
  WarehouseSku,
  WarehouseSkuBlock,
} from '../types'

const FLOOR_WIDTH = 728
const FLOOR_HEIGHT = 672
const MIN_AREA_WIDTH = 80
const MIN_AREA_HEIGHT = 60
const MIN_BLOCK_WIDTH = 96
const MIN_BLOCK_HEIGHT = 72

type LogicalPoint = { x: number; y: number }
type PointerInteraction =
  | { kind: 'draw'; pointerId: number; start: LogicalPoint; current: LogicalPoint }
  | { kind: 'move-area'; pointerId: number; start: LogicalPoint; area: WarehouseArea; current: LogicalPoint }
  | { kind: 'resize-area'; pointerId: number; start: LogicalPoint; area: WarehouseArea; current: LogicalPoint }
  | { kind: 'move-block'; pointerId: number; start: LogicalPoint; block: WarehouseSkuBlock; current: LogicalPoint }
  | { kind: 'resize-block'; pointerId: number; start: LogicalPoint; block: WarehouseSkuBlock; current: LogicalPoint }

const props = defineProps<{
  state: WarehouseCanvasState
  tool: CanvasTool
  selectedAreaId: string | null
  selectedBlockId: string | null
  issues: LayoutIssue[]
  searchQuery: string
}>()

const emit = defineEmits<{
  'select-area': [id: string]
  'select-block': [id: string]
  'create-area': [rect: CanvasRect]
  'update-area-rect': [rect: CanvasRect & { id: string }]
  'update-block-rect': [rect: CanvasRect & { id: string }]
}>()

const floorElement = ref<HTMLElement | null>(null)
const interaction = ref<PointerInteraction | null>(null)

const catalogBySkuId = computed(() => new Map(props.state.catalog.map((sku) => [sku.skuId, sku])))
const visibleAreaIds = computed(() => new Set(props.state.areas.filter((area) => area.visible).map((area) => area.id)))
const issuesByBlockId = computed(() => {
  const byBlockId = new Map<string, LayoutIssue[]>()
  for (const issue of props.issues) {
    for (const blockId of issue.blockIds) {
      byBlockId.set(blockId, [...(byBlockId.get(blockId) ?? []), issue])
    }
  }
  return byBlockId
})

const visibleAreas = computed(() => props.state.areas.filter((area) => area.visible))
const visibleBlocks = computed(() => props.state.blocks.filter((block) => visibleAreaIds.value.has(block.areaId)))
const drawPreview = computed(() => {
  if (interaction.value?.kind !== 'draw') return null
  return createDrawRect(interaction.value.start, interaction.value.current)
})

function rectangleStyle(rectangle: CanvasRect): CSSProperties {
  return {
    left: `${rectangle.x / FLOOR_WIDTH * 100}%`,
    top: `${rectangle.y / FLOOR_HEIGHT * 100}%`,
    width: `${rectangle.width / FLOOR_WIDTH * 100}%`,
    height: `${rectangle.height / FLOOR_HEIGHT * 100}%`,
  }
}

function skuFor(block: WarehouseSkuBlock): WarehouseSku | undefined {
  return catalogBySkuId.value.get(block.skuId)
}

function issuesFor(blockId: string): LayoutIssue[] {
  return issuesByBlockId.value.get(blockId) ?? []
}

function matchesSearch(block: WarehouseSkuBlock): boolean {
  const query = props.searchQuery.trim().toLocaleLowerCase()
  if (!query) return true
  const sku = skuFor(block)
  return Boolean(sku && `${sku.productName} ${sku.skuCode}`.toLocaleLowerCase().includes(query))
}

function accentClasses(sku: WarehouseSku | undefined): string {
  switch (sku?.accent) {
    case 'pink':
      return 'border-pink-300 bg-pink-50 text-pink-950'
    case 'green':
      return 'border-emerald-300 bg-emerald-50 text-emerald-950'
    case 'cyan':
      return 'border-cyan-300 bg-cyan-50 text-cyan-950'
    default:
      return 'border-blue-300 bg-blue-50 text-blue-950'
  }
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(Math.max(value, minimum), maximum)
}

function logicalPoint(event: PointerEvent): LogicalPoint {
  const bounds = floorElement.value?.getBoundingClientRect()
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) return { x: 0, y: 0 }
  return {
    x: clamp(Math.round((event.clientX - bounds.left) * FLOOR_WIDTH / bounds.width), 0, FLOOR_WIDTH),
    y: clamp(Math.round((event.clientY - bounds.top) * FLOOR_HEIGHT / bounds.height), 0, FLOOR_HEIGHT),
  }
}

function capturePointer(pointerId: number): void {
  floorElement.value?.setPointerCapture?.(pointerId)
}

function releasePointer(pointerId: number): void {
  floorElement.value?.releasePointerCapture?.(pointerId)
}

function onFloorPointerDown(event: PointerEvent): void {
  if (props.tool !== 'draw' || !isPrimaryButton(event)) return
  const start = logicalPoint(event)
  interaction.value = { kind: 'draw', pointerId: event.pointerId, start, current: start }
  capturePointer(event.pointerId)
}

function onAreaPointerDown(event: PointerEvent, area: WarehouseArea): void {
  if (props.tool !== 'select' || !isPrimaryButton(event)) return
  emit('select-area', area.id)
  if (area.locked) return
  const start = logicalPoint(event)
  interaction.value = { kind: 'move-area', pointerId: event.pointerId, start, current: start, area: { ...area } }
  capturePointer(event.pointerId)
}

function onBlockPointerDown(event: PointerEvent, block: WarehouseSkuBlock): void {
  if (props.tool !== 'select' || !isPrimaryButton(event)) return
  emit('select-block', block.id)
  const start = logicalPoint(event)
  interaction.value = { kind: 'move-block', pointerId: event.pointerId, start, current: start, block: { ...block } }
  capturePointer(event.pointerId)
}

function onAreaResizePointerDown(event: PointerEvent, area: WarehouseArea): void {
  if (props.tool !== 'select' || area.locked || !isPrimaryButton(event)) return
  const start = logicalPoint(event)
  interaction.value = { kind: 'resize-area', pointerId: event.pointerId, start, current: start, area: { ...area } }
  capturePointer(event.pointerId)
}

function onBlockResizePointerDown(event: PointerEvent, block: WarehouseSkuBlock): void {
  if (props.tool !== 'select' || !isPrimaryButton(event)) return
  const start = logicalPoint(event)
  interaction.value = { kind: 'resize-block', pointerId: event.pointerId, start, current: start, block: { ...block } }
  capturePointer(event.pointerId)
}

function onPointerMove(event: PointerEvent): void {
  if (!interaction.value || interaction.value.pointerId !== event.pointerId) return
  interaction.value.current = logicalPoint(event)
}

function onPointerUp(event: PointerEvent): void {
  const activeInteraction = interaction.value
  if (!activeInteraction || activeInteraction.pointerId !== event.pointerId) return
  activeInteraction.current = logicalPoint(event)

  if (activeInteraction.kind === 'draw') {
    emit('create-area', createDrawRect(activeInteraction.start, activeInteraction.current))
  } else if (activeInteraction.kind === 'move-area') {
    emit('update-area-rect', { id: activeInteraction.area.id, ...moveAreaRect(activeInteraction) })
  } else if (activeInteraction.kind === 'resize-area') {
    emit('update-area-rect', { id: activeInteraction.area.id, ...resizeAreaRect(activeInteraction) })
  } else if (activeInteraction.kind === 'move-block') {
    emit('update-block-rect', { id: activeInteraction.block.id, ...moveBlockRect(activeInteraction) })
  } else {
    emit('update-block-rect', { id: activeInteraction.block.id, ...resizeBlockRect(activeInteraction) })
  }

  interaction.value = null
  releasePointer(event.pointerId)
}

function onPointerCancel(event: PointerEvent): void {
  if (!interaction.value || interaction.value.pointerId !== event.pointerId) return
  interaction.value = null
  releasePointer(event.pointerId)
}

function createDrawRect(start: LogicalPoint, current: LogicalPoint): CanvasRect {
  const x = clamp(Math.min(start.x, current.x), 0, FLOOR_WIDTH - MIN_AREA_WIDTH)
  const y = clamp(Math.min(start.y, current.y), 0, FLOOR_HEIGHT - MIN_AREA_HEIGHT)
  return {
    x,
    y,
    width: clamp(Math.abs(current.x - start.x), MIN_AREA_WIDTH, FLOOR_WIDTH - x),
    height: clamp(Math.abs(current.y - start.y), MIN_AREA_HEIGHT, FLOOR_HEIGHT - y),
  }
}

function pointerDelta(activeInteraction: PointerInteraction): LogicalPoint {
  return {
    x: activeInteraction.current.x - activeInteraction.start.x,
    y: activeInteraction.current.y - activeInteraction.start.y,
  }
}

function moveAreaRect(activeInteraction: Extract<PointerInteraction, { kind: 'move-area' }>): CanvasRect {
  const delta = pointerDelta(activeInteraction)
  return {
    x: clamp(activeInteraction.area.x + delta.x, 0, FLOOR_WIDTH - activeInteraction.area.width),
    y: clamp(activeInteraction.area.y + delta.y, 0, FLOOR_HEIGHT - activeInteraction.area.height),
    width: activeInteraction.area.width,
    height: activeInteraction.area.height,
  }
}

function resizeAreaRect(activeInteraction: Extract<PointerInteraction, { kind: 'resize-area' }>): CanvasRect {
  const delta = pointerDelta(activeInteraction)
  return {
    x: activeInteraction.area.x,
    y: activeInteraction.area.y,
    width: clamp(activeInteraction.area.width + delta.x, MIN_AREA_WIDTH, FLOOR_WIDTH - activeInteraction.area.x),
    height: clamp(activeInteraction.area.height + delta.y, MIN_AREA_HEIGHT, FLOOR_HEIGHT - activeInteraction.area.y),
  }
}

function moveBlockRect(activeInteraction: Extract<PointerInteraction, { kind: 'move-block' }>): CanvasRect {
  const delta = pointerDelta(activeInteraction)
  return {
    x: clamp(activeInteraction.block.x + delta.x, 0, FLOOR_WIDTH - activeInteraction.block.width),
    y: clamp(activeInteraction.block.y + delta.y, 0, FLOOR_HEIGHT - activeInteraction.block.height),
    width: activeInteraction.block.width,
    height: activeInteraction.block.height,
  }
}

function resizeBlockRect(activeInteraction: Extract<PointerInteraction, { kind: 'resize-block' }>): CanvasRect {
  const delta = pointerDelta(activeInteraction)
  return {
    x: activeInteraction.block.x,
    y: activeInteraction.block.y,
    width: clamp(activeInteraction.block.width + delta.x, MIN_BLOCK_WIDTH, FLOOR_WIDTH - activeInteraction.block.x),
    height: clamp(activeInteraction.block.height + delta.y, MIN_BLOCK_HEIGHT, FLOOR_HEIGHT - activeInteraction.block.y),
  }
}

function onAreaKeydown(event: KeyboardEvent, area: WarehouseArea): void {
  const delta = arrowDelta(event)
  if (!delta || props.tool !== 'select' || props.selectedAreaId !== area.id || area.locked) return
  event.preventDefault()
  emit('update-area-rect', {
    id: area.id,
    x: clamp(area.x + delta.x, 0, FLOOR_WIDTH - area.width),
    y: clamp(area.y + delta.y, 0, FLOOR_HEIGHT - area.height),
    width: area.width,
    height: area.height,
  })
}

function onBlockKeydown(event: KeyboardEvent, block: WarehouseSkuBlock): void {
  const delta = arrowDelta(event)
  if (!delta || props.tool !== 'select' || props.selectedBlockId !== block.id) return
  event.preventDefault()
  emit('update-block-rect', {
    id: block.id,
    x: clamp(block.x + delta.x, 0, FLOOR_WIDTH - block.width),
    y: clamp(block.y + delta.y, 0, FLOOR_HEIGHT - block.height),
    width: block.width,
    height: block.height,
  })
}

function arrowDelta(event: KeyboardEvent): LogicalPoint | null {
  const distance = event.shiftKey ? 10 : 1
  if (event.key === 'ArrowLeft') return { x: -distance, y: 0 }
  if (event.key === 'ArrowRight') return { x: distance, y: 0 }
  if (event.key === 'ArrowUp') return { x: 0, y: -distance }
  if (event.key === 'ArrowDown') return { x: 0, y: distance }
  return null
}

function isPrimaryButton(event: PointerEvent): boolean {
  return event.button === undefined || event.button === 0
}
</script>

<template>
  <div
    ref="floorElement"
    data-testid="warehouse-canvas-floor"
    class="warehouse-floor relative aspect-[728/672] w-full min-w-[560px] overflow-hidden rounded-lg border border-slate-200 bg-slate-50 outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
    role="application"
    tabindex="0"
    aria-label="仓库平面画布，逻辑尺寸 728 × 672"
    @pointerdown="onFloorPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerCancel"
  >
    <div
      v-if="drawPreview"
      data-testid="warehouse-area-draw-preview"
      :style="rectangleStyle(drawPreview)"
      class="pointer-events-none absolute z-20 rounded-lg border-2 border-dashed border-blue-500 bg-blue-100/50"
      aria-hidden="true"
    />

    <div
      v-for="area in visibleAreas"
      :key="area.id"
      :data-testid="`warehouse-area-${area.id}`"
      :data-selected="selectedAreaId === area.id ? 'true' : 'false'"
      :style="rectangleStyle(area)"
      class="absolute rounded-lg border-2 border-dashed border-slate-300 bg-white/55 text-left outline-none transition-shadow focus-visible:ring-2 focus-visible:ring-blue-500"
      :class="selectedAreaId === area.id ? 'border-solid border-blue-600 ring-2 ring-blue-200' : ''"
      role="button"
      tabindex="0"
      :aria-label="`选择仓库区域 ${area.name}${area.locked ? '，已锁定' : ''}`"
      @click="emit('select-area', area.id)"
      @pointerdown.stop="onAreaPointerDown($event, area)"
      @keydown.enter.prevent="emit('select-area', area.id)"
      @keydown.space.prevent="emit('select-area', area.id)"
      @keydown="onAreaKeydown($event, area)"
    >
      <div class="pointer-events-none flex items-center gap-1.5 px-2 py-1 text-xs font-semibold text-slate-700">
        <Check v-if="selectedAreaId === area.id" :size="14" aria-hidden="true" />
        <span>{{ area.name }}</span>
        <span
          v-if="area.locked"
          :data-testid="`area-lock-${area.id}`"
          class="inline-flex items-center gap-1 text-slate-500"
          :aria-label="`${area.name} 已锁定`"
        >
          <Lock :size="13" aria-hidden="true" />
          <span class="sr-only">已锁定</span>
        </span>
      </div>

      <button
        v-if="selectedAreaId === area.id"
        :data-testid="`warehouse-resize-handle-area-${area.id}`"
        type="button"
        class="absolute -bottom-1.5 -right-1.5 z-30 h-3.5 w-3.5 cursor-se-resize rounded-sm border-2 border-white bg-blue-600 shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        :aria-label="`调整区域 ${area.name} 大小`"
        @click.stop
        @pointerdown.stop="onAreaResizePointerDown($event, area)"
      />
    </div>

    <div
      v-for="block in visibleBlocks"
      :key="block.id"
      :data-testid="`warehouse-sku-block-${block.id}`"
      :data-selected="selectedBlockId === block.id ? 'true' : 'false'"
      :data-warning="issuesFor(block.id).length > 0 ? 'true' : 'false'"
      :data-search-match="matchesSearch(block) ? 'true' : 'false'"
      :style="rectangleStyle(block)"
      class="absolute z-10 flex min-h-0 flex-col overflow-visible rounded-md border p-2 text-left outline-none transition-[opacity,box-shadow] focus-visible:ring-2 focus-visible:ring-blue-500"
      :class="[
        accentClasses(skuFor(block)),
        selectedBlockId === block.id ? 'ring-2 ring-blue-600 ring-offset-1' : '',
        issuesFor(block.id).length > 0 ? 'border-amber-500 ring-2 ring-amber-300' : '',
        searchQuery.trim() && !matchesSearch(block) ? 'opacity-25' : 'opacity-100',
      ]"
      role="button"
      tabindex="0"
      :aria-label="`选择产品块 ${skuFor(block)?.productName ?? block.id}，${block.units} 个`"
      @click.stop="emit('select-block', block.id)"
      @pointerdown.stop="onBlockPointerDown($event, block)"
      @keydown.enter.prevent="emit('select-block', block.id)"
      @keydown.space.prevent="emit('select-block', block.id)"
      @keydown="onBlockKeydown($event, block)"
    >
      <div class="pointer-events-none min-w-0">
        <div class="truncate text-xs font-semibold">{{ skuFor(block)?.productName ?? '未知 SKU' }}</div>
        <div class="truncate text-[10px] opacity-70">{{ skuFor(block)?.skuCode ?? `SKU ${block.skuId}` }}</div>
        <div class="mt-1 text-sm font-bold leading-none">{{ block.units }} 个</div>
        <div class="mt-1 truncate text-[10px] font-medium opacity-75">
          {{ formatCaseBreakdown(block.units, skuFor(block)?.unitsPerCase ?? 1) }}
        </div>
      </div>

      <div
        v-if="issuesFor(block.id).length"
        class="pointer-events-none absolute left-1 top-[calc(100%+4px)] z-40 flex max-w-52 items-start gap-1 rounded-md border border-amber-300 bg-amber-50 px-1.5 py-1 text-[10px] font-medium leading-tight text-amber-900 shadow-sm"
        role="status"
      >
        <TriangleAlert :size="12" class="mt-px shrink-0" aria-hidden="true" />
        <span>{{ issuesFor(block.id).map((issue) => issue.message).join('；') }}</span>
      </div>

      <button
        v-if="selectedBlockId === block.id"
        :data-testid="`warehouse-resize-handle-block-${block.id}`"
        type="button"
        class="absolute -bottom-1.5 -right-1.5 z-30 h-3.5 w-3.5 cursor-se-resize rounded-sm border-2 border-white bg-blue-600 shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        :aria-label="`调整产品块 ${skuFor(block)?.productName ?? block.id} 大小`"
        @click.stop
        @pointerdown.stop="onBlockResizePointerDown($event, block)"
      />
    </div>
  </div>
</template>

<style scoped>
.warehouse-floor {
  background-image:
    linear-gradient(to right, rgb(226 232 240 / 0.55) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(226 232 240 / 0.55) 1px, transparent 1px);
  background-size: 24px 24px;
}
</style>
