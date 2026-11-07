<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { warehousePlannerScene } from '../warehousePlannerScene'
import type { PlannerPalletGroup, PlannerRect } from '../warehousePlannerScene'
import WarehousePlannerInspector from './WarehousePlannerInspector.vue'
import WarehouseStructureLayer from './WarehouseStructureLayer.vue'
import WarehouseZoneLayer from './WarehouseZoneLayer.vue'
import { createWarehouseStructure, palletStructureConflicts, validatePlannerLayout } from '../warehouseStructure'
import type { WarehouseStructure, StructureIssue } from '../warehouseStructure'
import { resizePalletRect } from '../palletResize'
import type { ResizeHandle } from '../palletResize'

const props = defineProps<{
  gridSnapping: boolean
  inventoryDetailsVisible?: boolean
  detailMode?: boolean
  measurementEnabled: boolean
  selectedPalletId: string | null
  selectedZoneId?: string | null
  palletGroups?: readonly PlannerPalletGroup[]
  structure?: WarehouseStructure
  toolbarTarget?: HTMLElement | null
  minimapTarget?: HTMLElement | null
  statusTarget?: HTMLElement | null
  minimapViewport?: PlannerRect
  zoomPercent?: number
  viewportRulers?: boolean
  structureEditing?: boolean
  zoneEditing?: boolean
  goodsEditing?: boolean
  showDemoRooms?: boolean
  focusedStructureId?: string | null
}>()

const emit = defineEmits<{
  'select-pallet': [id: string | null]
  'select-zone': [id: string | null]
  'create-pallet': [rect: PlannerRect]
  'move-pallet': [move: { id: string; left: number; top: number }]
  'resize-pallet': [rect: { id: string; left: number; top: number; width: number; height: number }]
  'request-delete-pallet': [id: string]
  'change-structure': [structure: WarehouseStructure]
  'structure-issues': [issues: StructureIssue[]]
  'structure-busy': [busy: boolean]
  'pan-minimap': [position: { left: number; top: number }]
}>()

type RulerTickKind = 'minor' | 'meter' | 'major'
type RulerTick = {
  edge: 'start' | 'middle' | 'end'
  kind: RulerTickKind
  position: number
  value: number
}

function createRulerTicks(maxMeters: number): RulerTick[] {
  return Array.from({ length: maxMeters * 2 + 1 }, (_, index) => ({
    edge: index === 0 ? 'start' : index === maxMeters * 2 ? 'end' : 'middle',
    kind: index % 10 === 0 ? 'major' : index % 2 === 0 ? 'meter' : 'minor',
    position: index / (maxMeters * 2) * 100,
    value: index / 2,
  }))
}

const horizontalRulerTicks = createRulerTicks(60)
const verticalRulerTicks = createRulerTicks(40)
const horizontalRulerLabels = horizontalRulerTicks.filter((tick) => tick.kind === 'major')
const verticalRulerLabels = verticalRulerTicks.filter((tick) => tick.kind === 'major')
const palletGroups = computed(() => props.palletGroups ?? warehousePlannerScene.palletGroups)
const drawingBoard = ref<HTMLElement | null>(null)
const drawingPile = ref<{ pointerId: number; x: number; y: number; bounds: DOMRect; rect: PlannerRect } | null>(null)
const creationMessage = ref('')
const creationConflicts = computed(() => {
  const rect = drawingPile.value?.rect
  if (!rect) return []
  return [...palletStructureConflicts(currentStructure.value, rect), ...palletGroups.value.filter(p => rect.left < p.left+p.width && rect.left+rect.width > p.left && rect.top < p.top+p.height && rect.top+rect.height > p.top).map(p => p.id)]
})
function creationPoint(event: PointerEvent, bounds: DOMRect) {
  const x = (event.clientX-bounds.left)/bounds.width*100, y = (event.clientY-bounds.top)/bounds.height*100
  return props.gridSnapping ? { x: Math.round(x*1.2)/1.2, y: Math.round(y*.8)/.8 } : { x, y }
}
function startPileCreation(event: PointerEvent) {
  if (!props.goodsEditing || props.inventoryDetailsVisible || event.button !== 0 || palletDrag.value || palletResize.value || (event.target as Element).closest('button, input, [role="button"]')) return
  const bounds = drawingBoard.value!.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  const { x, y } = creationPoint(event, bounds)
  event.preventDefault()
  creationMessage.value = ''
  drawingPile.value = { pointerId: event.pointerId, x, y, bounds, rect: { left:x, top:y, width:0, height:0 } }
  emit('select-pallet', null)
  emit('structure-busy', true)
  drawingBoard.value?.setPointerCapture?.(event.pointerId)
}
function movePileCreation(event: PointerEvent) {
  const draft = drawingPile.value
  if (!draft || draft.pointerId !== event.pointerId) return
  const { x, y } = creationPoint(event, draft.bounds)
  draft.rect = { left:Math.min(x,draft.x), top:Math.min(y,draft.y), width:Math.abs(x-draft.x), height:Math.abs(y-draft.y) }
}
function cancelPileCreation() {
  if (!drawingPile.value) return
  const id = drawingPile.value.pointerId
  drawingPile.value = null
  drawingBoard.value?.releasePointerCapture?.(id)
  emit('structure-busy', false)
}
function finishPileCreation(event: PointerEvent) {
  if (drawingPile.value?.pointerId !== event.pointerId) return
  movePileCreation(event)
  const rect = drawingPile.value!.rect
  if (rect.width >= .833 && rect.height >= 1.249) {
    if (creationConflicts.value.length) {
      showCollisionFeedback(creationConflicts.value)
      creationMessage.value = '位置重叠或超出仓库边界，请重新框选'
    } else emit('create-pallet', { ...rect })
  }
  cancelPileCreation()
}
const initialStructure = createWarehouseStructure()
const structurePreview = ref<WarehouseStructure | null>(null)
const committedStructure = computed(() => props.structure ?? initialStructure)
const currentStructure = computed(() => structurePreview.value ?? committedStructure.value)
const planningIssues = computed(() => validatePlannerLayout(currentStructure.value, palletGroups.value))
watch(planningIssues, (issues) => emit('structure-issues', issues), { immediate: true })
const persistentConflictIds = computed(() => new Set(planningIssues.value.flatMap((v) => v.objectIds)))
const minimapOutline = computed(() => currentStructure.value.outline.nodes.map((p) => `${p.x},${p.y}`).join(' '))
const minimapSurface = ref<HTMLElement | null>(null)
const visibleMinimapViewport = computed(() => props.minimapViewport ?? { left: 0, top: 0, width: 100, height: 100 })
const scaleDenominator = computed(() => Math.round(10000 / (props.zoomPercent ?? 100)))
const clippedMinimapViewport = computed(() => {
  const view = visibleMinimapViewport.value
  const left = Math.max(0, Math.min(100, view.left))
  const top = Math.max(0, Math.min(100, view.top))
  const round = (value: number) => Math.round(value * 100) / 100
  return {
    left: round(left),
    top: round(top),
    width: round(Math.max(0, Math.min(100, view.left + view.width) - left)),
    height: round(Math.max(0, Math.min(100, view.top + view.height) - top)),
  }
})
const minimapWindowStyle = computed(() => ({
  left: `${clippedMinimapViewport.value.left}%`,
  top: `${clippedMinimapViewport.value.top}%`,
  width: `${clippedMinimapViewport.value.width}%`,
  height: `${clippedMinimapViewport.value.height}%`,
}))
let minimapDrag: { pointerId: number; offsetX: number; offsetY: number } | null = null
function minimapPoint(event: PointerEvent) {
  const bounds = minimapSurface.value?.getBoundingClientRect()
  if (!bounds?.width || !bounds.height) return null
  return {
    x: Math.max(0, Math.min(100, (event.clientX - bounds.left) / bounds.width * 100)),
    y: Math.max(0, Math.min(100, (event.clientY - bounds.top) / bounds.height * 100)),
  }
}
function panMinimap(point: { x: number; y: number }, offsetX: number, offsetY: number) {
  const view = visibleMinimapViewport.value
  emit('pan-minimap', {
    left: Math.max(-view.width, Math.min(100, point.x - offsetX)),
    top: Math.max(-view.height, Math.min(100, point.y - offsetY)),
  })
}
function startMinimapDrag(event: PointerEvent) {
  if (event.button !== 0) return
  const point = minimapPoint(event)
  if (!point) return
  event.preventDefault()
  event.stopPropagation()
  const view = visibleMinimapViewport.value
  const clipped = clippedMinimapViewport.value
  const inside = point.x >= clipped.left && point.x <= clipped.left + clipped.width && point.y >= clipped.top && point.y <= clipped.top + clipped.height
  minimapDrag = {
    pointerId: event.pointerId,
    offsetX: inside ? point.x - view.left : view.width / 2,
    offsetY: inside ? point.y - view.top : view.height / 2,
  }
  minimapSurface.value?.setPointerCapture?.(event.pointerId)
  panMinimap(point, minimapDrag.offsetX, minimapDrag.offsetY)
}
function moveMinimapDrag(event: PointerEvent) {
  if (!minimapDrag || minimapDrag.pointerId !== event.pointerId) return
  const point = minimapPoint(event)
  if (point) panMinimap(point, minimapDrag.offsetX, minimapDrag.offsetY)
}
function stopMinimapDrag(event: PointerEvent) {
  if (!minimapDrag || minimapDrag.pointerId !== event.pointerId) return
  moveMinimapDrag(event)
  minimapSurface.value?.releasePointerCapture?.(event.pointerId)
  minimapDrag = null
}
function nudgeMinimap(event: KeyboardEvent) {
  const offsets: Record<string, { x: number; y: number }> = {
    ArrowLeft: { x: -5, y: 0 }, ArrowRight: { x: 5, y: 0 }, ArrowUp: { x: 0, y: -5 }, ArrowDown: { x: 0, y: 5 },
  }
  const offset = offsets[event.key]
  if (!offset) return
  event.preventDefault()
  const view = visibleMinimapViewport.value
  panMinimap({ x: view.left + offset.x, y: view.top + offset.y }, 0, 0)
}
type PalletDrag = {
  pointerId: number
  startClientX: number
  startClientY: number
  boardWidth: number
  boardHeight: number
  pallet: PlannerPalletGroup
  preview: { left: number; top: number }
  guides: { x?: number; y?: number }
  overlappingIds: string[]
}
type PalletResize = {
  pointerId: number
  startClientX: number
  startClientY: number
  boardWidth: number
  boardHeight: number
  pallet: PlannerPalletGroup
  handle: ResizeHandle
  preview: PlannerRect
  overlappingIds: string[]
}
type PointerSample = { pointerId: number; clientX: number; clientY: number }
const palletDrag = ref<PalletDrag | null>(null)
const palletResize = ref<PalletResize | null>(null)
const resizeHandles: ResizeHandle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
const collisionFeedbackIds = ref<string[]>([])
let pendingDragSample: PointerSample | null = null
let dragFrameId: number | null = null
let collisionFeedbackTimer: number | null = null
const selectedPallet = computed(() => {
  const pallet = palletGroups.value.find((item) => item.id === props.selectedPalletId)
  return pallet ? displayedPallet(pallet) : undefined
})

function rectStyle(rect: PlannerRect) {
  return {
    left: `${rect.left}%`,
    top: `${rect.top}%`,
    width: `${rect.width}%`,
    height: `${rect.height}%`,
  }
}

function palletStyle(pallet: PlannerPalletGroup) {
  const resizing = palletResize.value?.pallet.id === pallet.id ? palletResize.value : null
  const drag = palletDrag.value?.pallet.id === pallet.id ? palletDrag.value : null
  const scale = Math.max((props.zoomPercent ?? 100) / 100, 0.01)
  const transform = drag
    ? `translate3d(${roundPosition((drag.preview.left - pallet.left) / 100 * drag.boardWidth / scale)}px, ${roundPosition((drag.preview.top - pallet.top) / 100 * drag.boardHeight / scale)}px, 0)`
    : undefined
  return {
    ...rectStyle(resizing?.preview ?? pallet),
    '--pallet-columns': String(pallet.columns),
    '--pallet-rows': String(pallet.rows),
    transform,
    zIndex: drag || resizing ? 21 : undefined,
  }
}

function displayedPallet(pallet: PlannerPalletGroup): PlannerPalletGroup {
  if (palletResize.value?.pallet.id === pallet.id) return { ...pallet, ...palletResize.value.preview }
  return palletDrag.value?.pallet.id === pallet.id
    ? { ...pallet, ...palletDrag.value.preview }
    : pallet
}

function palletTotalUnits(pallet: PlannerPalletGroup) {
  return pallet.contents.reduce((total, item) => total + item.units, 0)
}

function inspectorStyle(pallet: PlannerPalletGroup) {
  const horizontal = pallet.left > 65
    ? { right: `${roundPosition(100 - pallet.left + 2)}%` }
    : { left: `${Math.min(pallet.left + pallet.width + 2, 72)}%` }
  const vertical = pallet.top >= 58
    ? { bottom: '4%' }
    : { top: `${Math.max(14, pallet.top - 3)}%` }
  return { ...horizontal, ...vertical }
}

function roundPosition(value: number) {
  return Number(value.toFixed(2))
}

function constrainedPosition(pallet: PlannerPalletGroup, left: number, top: number, snapToGrid: boolean, roundResult = true) {
  if (snapToGrid && props.gridSnapping) {
    left = Math.round(left / (100 / 120)) * (100 / 120)
    top = Math.round(top / (100 / 80)) * (100 / 80)
  }
  top = Math.min(Math.max(top, 0), 100 - pallet.height)
  left = Math.min(Math.max(left, 0), 100 - pallet.width)
  return roundResult ? { left: roundPosition(left), top: roundPosition(top) } : { left, top }
}

function nearestAlignment(moving: number[], others: number[][], boardSize: number) {
  let best: { delta: number; position: number } | undefined
  for (const anchors of others) {
    for (let index = 0; index < moving.length; index += 1) {
      const delta = anchors[index] - moving[index]
      if (Math.abs(delta) * boardSize / 100 > 6) continue
      if (!best || Math.abs(delta) < Math.abs(best.delta)) best = { delta, position: anchors[index] }
    }
  }
  return best
}

function palletConflictIds(pallet: PlannerPalletGroup, rect: PlannerRect) {
  const right = rect.left + rect.width
  const bottom = rect.top + rect.height
  const ids = palletGroups.value
    .filter((other) => other.id !== pallet.id)
    .filter((other) => (
      rect.left < other.left + other.width
      && right > other.left
      && rect.top < other.top + other.height
      && bottom > other.top
    ))
    .map((other) => other.id)
  return [...ids, ...palletStructureConflicts(currentStructure.value, { ...pallet, ...rect })]
}

function overlappingPalletIds(pallet: PlannerPalletGroup, position: { left: number; top: number }) {
  return palletConflictIds(pallet, { ...pallet, ...position })
}

function palletIsOverlapping(id: string) {
  if (persistentConflictIds.value.has(id) && palletDrag.value?.pallet.id !== id && palletResize.value?.pallet.id !== id) return true
  if (collisionFeedbackIds.value.includes(id)) return true
  const drag = palletDrag.value
  const resize = palletResize.value
  return Boolean((drag?.overlappingIds.length && (drag.pallet.id === id || drag.overlappingIds.includes(id)))
    || (resize?.overlappingIds.length && (resize.pallet.id === id || resize.overlappingIds.includes(id))))
}

function clearCollisionFeedback() {
  if (collisionFeedbackTimer !== null) window.clearTimeout(collisionFeedbackTimer)
  collisionFeedbackTimer = null
  collisionFeedbackIds.value = []
}

function showCollisionFeedback(ids: string[]) {
  clearCollisionFeedback()
  if (!ids.length) return
  collisionFeedbackIds.value = ids
  collisionFeedbackTimer = window.setTimeout(clearCollisionFeedback, 1200)
}

function dragPreview(sample: PointerSample, drag: PalletDrag) {
  const raw = constrainedPosition(
    drag.pallet,
    drag.pallet.left + (sample.clientX - drag.startClientX) / drag.boardWidth * 100,
    drag.pallet.top + (sample.clientY - drag.startClientY) / drag.boardHeight * 100,
    false,
    false,
  )
  const xAnchors = [raw.left, raw.left + drag.pallet.width / 2, raw.left + drag.pallet.width]
  const yAnchors = [raw.top, raw.top + drag.pallet.height / 2, raw.top + drag.pallet.height]
  const otherPallets = palletGroups.value.filter((pallet) => pallet.id !== drag.pallet.id)
  const xAlignment = nearestAlignment(
    xAnchors,
    otherPallets.map((pallet) => [pallet.left, pallet.left + pallet.width / 2, pallet.left + pallet.width]),
    drag.boardWidth,
  )
  const yAlignment = nearestAlignment(
    yAnchors,
    otherPallets.map((pallet) => [pallet.top, pallet.top + pallet.height / 2, pallet.top + pallet.height]),
    drag.boardHeight,
  )
  const preview = constrainedPosition(
    drag.pallet,
    raw.left + (xAlignment?.delta ?? 0),
    raw.top + (yAlignment?.delta ?? 0),
    false,
    false,
  )
  return {
    preview,
    guides: {
      x: xAlignment?.position,
      y: yAlignment?.position,
    },
    overlappingIds: overlappingPalletIds(drag.pallet, preview),
  }
}

function committedDrop(sample: PointerSample, drag: PalletDrag) {
  const aligned = dragPreview(sample, drag)
  let position = constrainedPosition(drag.pallet, aligned.preview.left, aligned.preview.top, false)
  if (props.gridSnapping) {
    const gridPosition = constrainedPosition(drag.pallet, aligned.preview.left, aligned.preview.top, true)
    position = constrainedPosition(
      drag.pallet,
      aligned.guides.x === undefined ? gridPosition.left : aligned.preview.left,
      aligned.guides.y === undefined ? gridPosition.top : aligned.preview.top,
      false,
    )
  }
  return { position, overlappingIds: overlappingPalletIds(drag.pallet, position) }
}

function clearScheduledPreview() {
  if (dragFrameId !== null && dragFrameId >= 0) cancelAnimationFrame(dragFrameId)
  dragFrameId = null
  pendingDragSample = null
}

function schedulePalletPreview(sample: PointerSample) {
  pendingDragSample = sample
  if (dragFrameId !== null) return
  dragFrameId = -1
  const scheduledId = requestAnimationFrame(() => {
    dragFrameId = null
    const next = pendingDragSample
    pendingDragSample = null
    const drag = palletDrag.value
    if (!next) return
    if (drag && drag.pointerId === next.pointerId) {
      const { preview, guides, overlappingIds } = dragPreview(next, drag)
      palletDrag.value = { ...drag, preview, guides, overlappingIds }
    }
    const resize = palletResize.value
    if (resize && resize.pointerId === next.pointerId) {
      const preview = resizePreview(next, resize, false)
      palletResize.value = { ...resize, preview, overlappingIds: palletConflictIds(resize.pallet, preview) }
    }
  })
  if (dragFrameId !== null) dragFrameId = scheduledId
}

function startPalletDrag(event: PointerEvent, pallet: PlannerPalletGroup) {
  if (props.inventoryDetailsVisible || props.structureEditing || props.zoneEditing || palletResize.value) return
  if (event.button !== 0) return
  const bounds = drawingBoard.value?.getBoundingClientRect()
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) return
  event.preventDefault()
  ;(event.currentTarget as HTMLElement | null)?.focus()
  clearCollisionFeedback()
  emit('select-pallet', pallet.id)
  palletDrag.value = {
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    boardWidth: bounds.width,
    boardHeight: bounds.height,
    pallet: { ...pallet, contents: pallet.contents.map((item) => ({ ...item })) },
    preview: { left: pallet.left, top: pallet.top },
    guides: {},
    overlappingIds: [],
  }
  emit('structure-busy', true)
  drawingBoard.value?.setPointerCapture?.(event.pointerId)
}

function resizePreview(sample: PointerSample, resize: PalletResize, snap: boolean) {
  return resizePalletRect(resize.pallet, resize.handle, {
    x: (sample.clientX - resize.startClientX) / resize.boardWidth * 100,
    y: (sample.clientY - resize.startClientY) / resize.boardHeight * 100,
  }, snap)
}

function startPalletResize(event: PointerEvent, pallet: PlannerPalletGroup, handle: ResizeHandle) {
  if (!props.goodsEditing || props.inventoryDetailsVisible || event.button !== 0 || palletDrag.value || palletResize.value) return
  const bounds = drawingBoard.value?.getBoundingClientRect()
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) return
  event.preventDefault()
  event.stopPropagation()
  ;(event.currentTarget as HTMLElement | null)?.parentElement?.focus()
  clearCollisionFeedback()
  palletResize.value = {
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    boardWidth: bounds.width,
    boardHeight: bounds.height,
    pallet: { ...pallet, contents: pallet.contents.map(item => ({ ...item })) },
    handle,
    preview: { left: pallet.left, top: pallet.top, width: pallet.width, height: pallet.height },
    overlappingIds: [],
  }
  emit('structure-busy', true)
  drawingBoard.value?.setPointerCapture?.(event.pointerId)
}

function movePalletResize(event: PointerEvent) {
  if (palletResize.value?.pointerId !== event.pointerId) return
  schedulePalletPreview({ pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY })
}

function stopPalletResize(event: PointerEvent) {
  const resize = palletResize.value
  if (!resize || resize.pointerId !== event.pointerId) return
  if (Math.abs(event.clientX - resize.startClientX) < 3 && Math.abs(event.clientY - resize.startClientY) < 3) {
    cancelPalletResize(event)
    return
  }
  clearScheduledPreview()
  const rect = resizePreview({ pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY }, resize, props.gridSnapping)
  const conflictingIds = palletConflictIds(resize.pallet, rect)
  if (conflictingIds.length) showCollisionFeedback([resize.pallet.id, ...conflictingIds])
  else if (rect.left !== resize.pallet.left || rect.top !== resize.pallet.top
    || rect.width !== resize.pallet.width || rect.height !== resize.pallet.height) {
    emit('resize-pallet', { id: resize.pallet.id, ...rect })
  }
  palletResize.value = null
  emit('structure-busy', false)
  drawingBoard.value?.releasePointerCapture?.(event.pointerId)
}

function cancelPalletResize(event: Pick<PointerEvent, 'pointerId'>) {
  if (palletResize.value?.pointerId !== event.pointerId) return
  clearScheduledPreview()
  palletResize.value = null
  emit('structure-busy', false)
  drawingBoard.value?.releasePointerCapture?.(event.pointerId)
}

function movePalletDrag(event: PointerEvent) {
  const drag = palletDrag.value
  if (!drag || drag.pointerId !== event.pointerId) return
  schedulePalletPreview({ pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY })
}

function stopPalletDrag(event: PointerEvent) {
  const drag = palletDrag.value
  if (!drag || drag.pointerId !== event.pointerId) return
  clearScheduledPreview()
  if (Math.abs(event.clientX - drag.startClientX) < 3 && Math.abs(event.clientY - drag.startClientY) < 3) {
    palletDrag.value = null
    emit('structure-busy', false)
    drawingBoard.value?.releasePointerCapture?.(event.pointerId)
    return
  }
  const drop = committedDrop({ pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY }, drag)
  if (drop.overlappingIds.length > 0) {
    showCollisionFeedback([drag.pallet.id, ...drop.overlappingIds])
  } else if (drop.position.left !== drag.pallet.left || drop.position.top !== drag.pallet.top) {
    emit('move-pallet', { id: drag.pallet.id, ...drop.position })
  }
  palletDrag.value = null
  emit('structure-busy', false)
  drawingBoard.value?.releasePointerCapture?.(event.pointerId)
}

function cancelPalletDrag(event: Pick<PointerEvent, 'pointerId'>) {
  if (!palletDrag.value || palletDrag.value.pointerId !== event.pointerId) return
  clearScheduledPreview()
  palletDrag.value = null
  emit('structure-busy', false)
  drawingBoard.value?.releasePointerCapture?.(event.pointerId)
}
function cancelPalletWithEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && drawingPile.value) { event.preventDefault(); cancelPileCreation(); return }
  if (event.key === 'Escape' && palletResize.value) { event.preventDefault(); cancelPalletResize({ pointerId: palletResize.value.pointerId }); return }
  if(event.key !== 'Escape' || !palletDrag.value) return
  event.preventDefault()
  cancelPalletDrag({pointerId:palletDrag.value.pointerId})
}
function deleteFocusedPallet(event: KeyboardEvent) {
  if (event.key !== 'Delete' || !props.goodsEditing || props.inventoryDetailsVisible || palletDrag.value || palletResize.value) return
  const focused = document.activeElement
  if (!focused || focused.tagName !== 'BUTTON' || focused.getAttribute('data-testid') !== `planner-pallet-${props.selectedPalletId}`) return
  event.preventDefault()
  emit('request-delete-pallet', props.selectedPalletId!)
}
function handleKeydown(event: KeyboardEvent) { cancelPalletWithEscape(event); deleteFocusedPallet(event) }
onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  clearScheduledPreview()
  clearCollisionFeedback()
})
</script>

<template>
  <section
    data-testid="warehouse-blueprint-scene"
    class="blueprint-scene"
    :class="{ 'grid-muted': !gridSnapping, 'details-visible': inventoryDetailsVisible, 'detail-mode': detailMode, 'viewport-canvas': viewportRulers }"
    :data-measuring="measurementEnabled ? 'true' : 'false'"
    :data-grid-snapping="gridSnapping ? 'true' : 'false'"
    :data-view-mode="inventoryDetailsVisible ? 'details' : 'planning'"
    aria-label="仓库平面规划画布"
  >
    <div v-if="!detailMode && !viewportRulers" data-testid="planner-ruler-x" class="ruler ruler-x" role="img" aria-label="横向标尺，0 至 60 米，最小刻度 0.5 米">
      <i
        v-for="tick in horizontalRulerTicks"
        :key="tick.value"
        data-testid="planner-ruler-x-tick"
        class="ruler-tick"
        :class="`tick-${tick.kind}`"
        :data-tick-kind="tick.kind"
        :style="{ left: `${tick.position}%` }"
        aria-hidden="true"
      />
      <span
        v-for="tick in horizontalRulerLabels"
        :key="`x-label-${tick.value}`"
        class="ruler-label"
        :data-edge="tick.edge"
        :style="{ left: `${tick.position}%` }"
      >{{ tick.value }}</span>
      <span class="ruler-unit">(m)</span>
    </div>
    <div v-if="!detailMode && !viewportRulers" data-testid="planner-ruler-y" class="ruler ruler-y" role="img" aria-label="纵向标尺，0 至 40 米，最小刻度 0.5 米">
      <i
        v-for="tick in verticalRulerTicks"
        :key="tick.value"
        data-testid="planner-ruler-y-tick"
        class="ruler-tick"
        :class="`tick-${tick.kind}`"
        :data-tick-kind="tick.kind"
        :style="{ top: `${tick.position}%` }"
        aria-hidden="true"
      />
      <span
        v-for="tick in verticalRulerLabels"
        :key="`y-label-${tick.value}`"
        class="ruler-label"
        :data-edge="tick.edge"
        :style="{ top: `${tick.position}%` }"
      >{{ tick.value }}</span>
      <span class="ruler-unit">(m)</span>
    </div>

    <div
      ref="drawingBoard"
      class="drawing-board"
      @pointerdown="startPileCreation"
      @pointermove="movePalletDrag($event); movePalletResize($event); movePileCreation($event)"
      @pointerup="stopPalletDrag($event); stopPalletResize($event); finishPileCreation($event)"
      @pointercancel="cancelPalletDrag($event); cancelPalletResize($event); cancelPileCreation()"
      @lostpointercapture="cancelPileCreation(); cancelPalletResize($event)"
    >
      <div v-if="drawingPile" data-testid="pile-creation-preview" class="pile-creation-preview" :class="{ invalid: creationConflicts.length }" :style="rectStyle(drawingPile.rect)"></div>
      <p v-if="goodsEditing && !inventoryDetailsVisible" class="pile-creation-hint" role="status">{{ creationMessage || '在空白处按住鼠标拖动创建货物堆 · Esc 取消' }}</p>
      <WarehouseStructureLayer
        :toolbar-target="toolbarTarget"
        :pallets="palletGroups"
        :structure="committedStructure" :editing="Boolean(structureEditing) && !inventoryDetailsVisible"
        :grid-snapping="gridSnapping" :issues="planningIssues.concat(collisionFeedbackIds.length ? [{id:'drop-conflict',objectIds:collisionFeedbackIds,message:'位置冲突'}] : [], palletDrag?.overlappingIds.length ? [{id:'drag-conflict',objectIds:palletDrag.overlappingIds,message:'位置冲突'}] : [], palletResize?.overlappingIds.length ? [{id:'resize-conflict',objectIds:palletResize.overlappingIds,message:'位置冲突'}] : [])"
        :focused-id="focusedStructureId"
        @preview="structurePreview = $event" @commit="emit('change-structure', $event)" @busy="emit('structure-busy', $event)"
        @conflict="showCollisionFeedback"
      />

      <WarehouseZoneLayer
        :toolbar-target="toolbarTarget"
        :pallets="palletGroups"
        :structure="committedStructure" :editing="Boolean(zoneEditing) && !inventoryDetailsVisible"
        :viewing="Boolean(detailMode)" :selected-zone-id="selectedZoneId"
        :grid-snapping="gridSnapping" :focused-id="focusedStructureId"
        :issues="planningIssues.concat(collisionFeedbackIds.length ? [{ id: 'drop-conflict', objectIds: collisionFeedbackIds, message: '位置冲突' }] : [], palletDrag?.overlappingIds.length ? [{ id: 'drag-conflict', objectIds: palletDrag.overlappingIds, message: '位置冲突' }] : [], palletResize?.overlappingIds.length ? [{ id: 'resize-conflict', objectIds: palletResize.overlappingIds, message: '位置冲突' }] : [])"
        @preview="structurePreview = $event" @commit="emit('change-structure', $event)" @busy="emit('structure-busy', $event)" @conflict="showCollisionFeedback" @select="emit('select-zone', $event)"
      />

      <article v-for="room in (showDemoRooms ? warehousePlannerScene.rooms : [])" :key="room.id" class="utility-room" :style="rectStyle(room)">
        <strong>{{ room.label }}</strong><span>{{ room.detail }}</span>
      </article>

      <button
        v-for="pallet in palletGroups"
        :key="pallet.id"
        :data-testid="`planner-pallet-${pallet.id}`"
        class="pallet-group"
        :class="{ selected: selectedPalletId === pallet.id, dragging: palletDrag?.pallet.id === pallet.id, resizing: palletResize?.pallet.id === pallet.id, overlapping: palletIsOverlapping(pallet.id) }"
        :data-selected="selectedPalletId === pallet.id ? 'true' : 'false'"
        :data-overlapping="palletIsOverlapping(pallet.id) ? 'true' : 'false'"
        :aria-pressed="selectedPalletId === pallet.id"
        :style="palletStyle(pallet)"
        type="button"
        :aria-label="`${pallet.name}，地面箱子堆砌，${pallet.contents.length} 种商品，共 ${palletTotalUnits(pallet)} 个`"
        @pointerdown="startPalletDrag($event, pallet)"
        @click="emit('select-pallet', pallet.id)"
      >
        <span class="pallet-cells" aria-hidden="true">
          <i v-for="cell in pallet.columns * pallet.rows" :key="cell" data-testid="pallet-box-cell" class="pallet-cell" />
        </span>
        <span class="pallet-code" aria-hidden="true">{{ pallet.code }}</span>
        <span class="sr-only">{{ pallet.name }} · {{ pallet.contents.length }} 种商品 · 共 {{ palletTotalUnits(pallet) }} 个</span>
        <template v-if="selectedPalletId === pallet.id && goodsEditing && !structureEditing && !zoneEditing && !inventoryDetailsVisible">
          <i v-for="handle in resizeHandles" :key="handle" class="selection-handle" :class="`handle-${handle}`" :data-testid="`pallet-resize-handle-${handle}`" aria-hidden="true" @pointerdown.stop.prevent="startPalletResize($event, pallet, handle)" @click.stop />

        </template>
        <span v-if="persistentConflictIds.has(pallet.id) && !palletDrag && !palletResize" class="pile-issue-label">{{ palletStructureConflicts(currentStructure,pallet).includes('outline') ? '超出仓库边界' : '位置冲突' }}</span>
      </button>

      <i
        v-if="palletDrag?.guides.x !== undefined"
        data-testid="planner-alignment-guide-x"
        class="alignment-guide vertical-guide"
        :style="{ left: `${palletDrag.guides.x}%` }"
        aria-hidden="true"
      />
      <i
        v-if="palletDrag?.guides.y !== undefined"
        data-testid="planner-alignment-guide-y"
        class="alignment-guide horizontal-guide"
        :style="{ top: `${palletDrag.guides.y}%` }"
        aria-hidden="true"
      />

      <template v-if="measurementEnabled && selectedPallet && !palletDrag && !palletResize">
        <div data-testid="planner-measurement-width" class="measurement width-measure" :style="{ left: `${selectedPallet.left}%`, top: `${selectedPallet.top - 2}%`, width: `${selectedPallet.width}%` }">
          <span>{{ selectedPallet.lengthMeters }}m</span>
        </div>
        <div data-testid="planner-measurement-height" class="measurement height-measure" :style="{ left: `${selectedPallet.left + selectedPallet.width + 1}%`, top: `${selectedPallet.top}%`, height: `${selectedPallet.height}%` }">
          <span>{{ selectedPallet.widthMeters }}m</span>
        </div>
      </template>

      <WarehousePlannerInspector
        v-if="inventoryDetailsVisible && !detailMode && selectedPallet && !palletDrag && !palletResize"
        class="scene-inspector"
        :style="inspectorStyle(selectedPallet)"
        :pallet="selectedPallet"
        @close="emit('select-pallet', null)"
      />

      <Teleport :to="minimapTarget ?? 'body'" :disabled="!minimapTarget">
      <aside v-if="!detailMode" data-testid="planner-minimap" class="planner-minimap" :class="{ 'external-minimap': minimapTarget }" aria-label="仓库小地图">
        <button ref="minimapSurface" type="button" data-testid="planner-minimap-surface" class="minimap-shell" aria-label="拖动查看仓库画布" @pointerdown="startMinimapDrag" @pointermove="moveMinimapDrag" @pointerup="stopMinimapDrag" @pointercancel="stopMinimapDrag" @keydown="nudgeMinimap">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><polygon :points="minimapOutline" fill="#f0f3f6" stroke="#8994a1" stroke-width="1.5" /><rect v-for="zone in currentStructure.zones" :key="zone.id" :x="zone.left" :y="zone.top" :width="zone.width" :height="zone.height" :fill="{ green: 'var(--wh-zone-soft)', blue: 'var(--wh-zone-primary)', amber: 'var(--wh-zone-deep)', purple: 'var(--wh-zone-muted)' }[zone.tone]" /><rect v-for="lift in currentStructure.elevators" :key="lift.id" :x="lift.left" :y="lift.top" :width="lift.width" :height="lift.height" fill="#819ab5" /><rect v-for="column in currentStructure.columns" :key="column.id" :x="column.left" :y="column.top" :width="column.width" :height="column.height" fill="#566271" /></svg>
          <i v-for="pallet in palletGroups" :key="pallet.id" :style="rectStyle(pallet)" />
          <span data-testid="planner-minimap-window" :style="minimapWindowStyle" aria-hidden="true" />
        </button>
      </aside>
      </Teleport>
    </div>
    <Teleport :to="statusTarget ?? 'body'" :disabled="!statusTarget">
    <footer v-if="!detailMode" data-testid="planner-coordinate-status" class="coordinate-status" :class="{ 'external-status': statusTarget }">
      <span>X&nbsp; {{ selectedPallet ? `${selectedPallet.xMeters}m` : '—' }}</span>
      <span>Y&nbsp; {{ selectedPallet ? `${selectedPallet.yMeters}m` : '—' }}</span>
      <span class="status-divider" aria-hidden="true" />
      <span>比例&nbsp; 1:{{ scaleDenominator }}</span>
      <span>缩放&nbsp; {{ zoomPercent ?? 100 }}%</span>
      <strong class="north-indicator" aria-label="北向">N<span>▲</span></strong>
    </footer>
    </Teleport>
  </section>
</template>

<style scoped>
.pile-creation-preview {position:absolute;z-index:30;border:1px solid var(--wh-accent);background:var(--wh-accent-preview);pointer-events:none;}
.pile-creation-preview.invalid {border-color:#dc3545;background:#dc354520;}
.pile-creation-hint {position:absolute;top:8px;left:50%;transform:translateX(-50%);z-index:30;margin:0;padding:6px 12px;border:1px solid #e2e8f0;border-radius:6px;background:#ffffffed;color:var(--wh-muted);font-size:12px;pointer-events:none;white-space:nowrap;}
.blueprint-scene.detail-mode { min-width:760px; min-height:560px; padding:64px 32px 32px; background:var(--wh-raised); }

.blueprint-scene { position: relative; width: 100%; min-width: 980px; height: 100%; min-height: 720px; padding: 36px 28px 28px 48px; background:var(--wh-ground); color:var(--wh-text); }
.drawing-board { position:relative; width:100%; height:100%; overflow:hidden; border:1px solid var(--wh-border); background-color:var(--wh-raised); background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='40'%3E%3Cpath d='M40 0H0V40' fill='none' stroke='%23e4e9f5' stroke-width='1'/%3E%3C/svg%3E"); background-size:40px 40px; }
.grid-muted .drawing-board { background-image:none; }
.blueprint-scene.viewport-canvas {background:transparent;}
.viewport-canvas .drawing-board, .viewport-canvas.grid-muted .drawing-board {border:0;background-color:transparent;background-image:none;}
.ruler { position: absolute; z-index: 2; color: var(--wh-muted); font:11px/1 var(--wh-font); }
.ruler-x { top: 7px; left: 48px; right: 28px; height: 28px; border-bottom: 1px solid #8f9baa; }
.ruler-y { top: 36px; bottom: 28px; left: 8px; width: 39px; border-right: 1px solid #8f9baa; }
.ruler-tick { position: absolute; display: block; background: #98a4b2; pointer-events: none; }
.ruler-x .ruler-tick { bottom: 0; width: 1px; height: 4px; }
.ruler-x .tick-meter { height: 8px; background: #7f8b9a; }
.ruler-x .tick-major { height: 14px; background: #667384; }
.ruler-y .ruler-tick { right: 0; width: 4px; height: 1px; }
.ruler-y .tick-meter { width: 8px; background: #7f8b9a; }
.ruler-y .tick-major { width: 14px; background: #667384; }
.ruler-label { position: absolute; color: #4b586b; font-variant-numeric: tabular-nums; white-space: nowrap; }
.ruler-x .ruler-label { top: 0; transform: translateX(-50%); }
.ruler-x .ruler-label[data-edge='start'] { transform: none; }
.ruler-x .ruler-label[data-edge='end'] { transform: translateX(-100%); }
.ruler-y .ruler-label { right: 20px; transform: translateY(-50%); }
.ruler-y .ruler-label[data-edge='start'] { transform: none; }
.ruler-y .ruler-label[data-edge='end'] { transform: translateY(-100%); }
.ruler-unit { position: absolute; color: var(--wh-muted); }
.ruler-x .ruler-unit { right: -25px; top: 0; }.ruler-y .ruler-unit { bottom: -18px; right: 5px; }
.utility-room { position: absolute; z-index: 4; display: grid; place-content: center; gap: 4px; border: 0; background: transparent; text-align: center; pointer-events:none; }.utility-room strong { font-size: 12px; }.utility-room span { color: var(--wh-muted); font-size: 10px; }
.pallet-group { position:absolute; z-index:8; min-height:0; border:1px solid var(--wh-pile-border); border-radius:2px; padding:0; background:var(--wh-pile-bg); cursor:grab; touch-action:none; user-select:none; }
.pallet-cells { position:absolute; inset:2px; display:grid; grid-template-columns:repeat(var(--pallet-columns),minmax(0,1fr)); grid-template-rows:repeat(var(--pallet-rows),minmax(0,1fr)); gap:2px; pointer-events:none; overflow:hidden; }
.pallet-cell { display:block; min-width:0; min-height:0; border:1px solid var(--wh-pile-rule); border-radius:1px; background:var(--wh-pile-cell); }
.pallet-code { position:absolute; top:50%; left:50%; width:max-content; padding:3px 5px; border-radius:2px; background:var(--wh-pile-label); color:var(--wh-pile-ink); font:600 13px/1 var(--wh-font); transform:translate(-50%,-50%); pointer-events:none; }
.details-visible .pallet-group { cursor: pointer; }
.pallet-group.dragging, .pallet-group.resizing { z-index: 19; cursor: grabbing; will-change: transform,width,height,left,top; }
.pallet-group:hover { border-color:var(--wh-accent); }.pallet-group:focus-visible { outline: 2px solid var(--wh-accent); outline-offset: 2px; }
.pallet-group.selected { z-index: 15; border:2px solid var(--wh-accent); }
.pallet-group.overlapping { z-index: 20; border: 2px solid #e5484d; box-shadow: 0 0 0 3px rgba(229,72,77,.18),0 3px 10px rgba(139,35,40,.16); }
.selection-handle { position: absolute; z-index: 2; width: 10px; height: 10px; border: 1px solid white; background:var(--wh-accent); outline:1px solid var(--wh-accent); touch-action:none; }
.handle-nw{left:-5px;top:-5px;cursor:nwse-resize}.handle-n{left:50%;top:-5px;transform:translateX(-50%);cursor:ns-resize}.handle-ne{right:-5px;top:-5px;cursor:nesw-resize}.handle-e{right:-5px;top:50%;transform:translateY(-50%);cursor:ew-resize}.handle-se{right:-5px;bottom:-5px;cursor:nwse-resize}.handle-s{left:50%;bottom:-5px;transform:translateX(-50%);cursor:ns-resize}.handle-sw{left:-5px;bottom:-5px;cursor:nesw-resize}.handle-w{left:-5px;top:50%;transform:translateY(-50%);cursor:ew-resize}
.alignment-guide { position: absolute; z-index: 18; display: block; pointer-events: none; background: var(--wh-accent);  }
.vertical-guide { top: 10.1%; bottom: 10.1%; width: 1px; }
.horizontal-guide { right: 4%; left: 6.8%; height: 1px; }
.measurement { position: absolute; z-index: 18; color: var(--wh-accent); font: 11px/1 var(--wh-font); pointer-events: none; }
.width-measure { border-top: 1px solid var(--wh-accent); }.height-measure { border-left: 1px solid var(--wh-accent); }
.measurement span { position: absolute; padding: 2px 4px; border-radius: 3px; background: var(--wh-accent-soft); white-space: nowrap; }
.width-measure span { left: 50%; top: -17px; transform: translateX(-50%); }.height-measure span { left: 4px; top: 50%; transform: translateY(-50%); }
.scene-inspector { position: absolute; z-index: 24; }
.planner-minimap { position:absolute; z-index:20; right:16px; bottom:48px; width:144px; height:104px; border:1px solid var(--wh-border); border-radius:8px; padding:8px; background:var(--wh-surface); }
.planner-minimap.external-minimap { position:static; width:100%; height:100%; pointer-events:auto; }
.minimap-shell { position: relative; display:block; width: 100%; height: 100%; min-height:0; border:0; border-radius:0; padding:0; background: #f5f7f9; cursor:grab; touch-action:none; }
.minimap-shell:active {cursor:grabbing;}
.minimap-shell:focus-visible {outline:2px solid var(--wh-accent);outline-offset:2px;}
.minimap-shell svg {position:absolute;inset:0;width:100%;height:100%;}
.pile-issue-label {position:absolute;bottom:calc(100% + 5px);left:50%;transform:translateX(-50%);white-space:nowrap;background:#fff1f2;color:#c8263c;border:1px solid #fecdd3;border-radius:4px;padding:2px 5px;font-size:11px;pointer-events:none;}
.minimap-shell i { position: absolute; display: block; background:var(--wh-pile-border); opacity: .8; transform: scale(.9); }
.minimap-shell > span { position: absolute; display:block; border: 2px solid var(--wh-accent); background:transparent; pointer-events:none; }
.coordinate-status { position: absolute; z-index: 25; right: 0; bottom: 0; left: 0; display: flex; align-items: center; gap: 22px; height: 28px; padding: 0 22px; border-top: 1px solid #e6ebf1; background: rgba(255,255,255,.96); color: var(--wh-muted); font: 11px/1 var(--wh-font); }
.coordinate-status.external-status { position:static; width:100%; max-width:100%; height:34px; gap:20px; padding:0 16px; border:0; border-radius:0; background:var(--wh-surface); }
.status-divider { width: 1px; height: 14px; margin-inline: -8px; background: #d6dde5; }.north-indicator { display: flex; align-items: center; gap: 4px; margin-left: auto; color: #354159; font-size:11px; }.north-indicator span { font-size: 15px; }
@media (max-width:480px) { .coordinate-status.external-status { gap:12px; font-size:11px; padding:0 12px; } .north-indicator { display:none; } }
@media (max-width: 1280px) { .blueprint-scene { min-width: 1060px; } }
</style>
