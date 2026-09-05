import { computed, ref } from 'vue'
import { createMemoryWarehouseCanvasRepository, seedWarehouseCanvas, type WarehouseCanvasRepository } from './mockWarehouseCanvasData'
import { moveWholeBlock as moveWholeBlockInModel, splitBlock as splitBlockInModel, validateCanvasLayout } from './warehouseCanvasModel'
import type { CanvasRect, CanvasTool, WarehouseArea, WarehouseCanvasState, WarehouseSkuBlock } from './types'

type CreateAreaInput = Omit<WarehouseArea, 'visible' | 'locked'> & Partial<Pick<WarehouseArea, 'visible' | 'locked'>>

const emptyState: WarehouseCanvasState = {
  warehouseId: 0,
  warehouseName: '',
  areas: [],
  catalog: [],
  blocks: [],
}

export function useWarehouseCanvas(options: { repository?: WarehouseCanvasRepository } = {}) {
  const repository = options.repository ?? createMemoryWarehouseCanvasRepository(seedWarehouseCanvas)
  const state = ref<WarehouseCanvasState>(cloneState(emptyState))
  const loading = ref(false)
  const dirty = ref(false)
  const saving = ref(false)
  const savedAt = ref<Date | null>(null)
  const selectedAreaId = ref<string | null>(null)
  const selectedBlockId = ref<string | null>(null)
  const activeTool = ref<CanvasTool>('select')
  const searchQuery = ref('')
  const undoHistory = ref<WarehouseCanvasState[]>([])
  const redoHistory = ref<WarehouseCanvasState[]>([])
  const issues = computed(() => validateCanvasLayout(state.value))
  const canUndo = computed(() => undoHistory.value.length > 0)
  const canRedo = computed(() => redoHistory.value.length > 0)
  const matchedBlockIds = computed(() => {
    const query = searchQuery.value.trim().toLocaleLowerCase()
    if (!query) return []

    const matchingSkuIds = new Set(state.value.catalog
      .filter((sku) => `${sku.skuCode} ${sku.productName}`.toLocaleLowerCase().includes(query))
      .map((sku) => sku.skuId))

    return state.value.blocks.filter((block) => matchingSkuIds.has(block.skuId)).map((block) => block.id)
  })

  async function load(warehouseId: number): Promise<void> {
    loading.value = true
    try {
      state.value = cloneState(await repository.load(warehouseId))
      dirty.value = false
      savedAt.value = null
      undoHistory.value = []
      redoHistory.value = []
      selectedAreaId.value = null
      selectedBlockId.value = null
    } finally {
      loading.value = false
    }
  }

  function createArea(area: CreateAreaInput): void {
    mutate((current) => ({
      ...current,
      areas: [...current.areas, {
        ...area,
        ...normalizeRect(area),
        visible: area.visible ?? true,
        locked: area.locked ?? false,
      }],
    }))
  }

  function renameArea(areaId: string, name: string): void {
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, name } : area
    )) }))
  }

  function updateAreaRect(areaId: string, rect: CanvasRect): void {
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, ...normalizeRect(rect) } : area
    )) }))
  }

  function toggleAreaVisibility(areaId: string): void {
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, visible: !area.visible } : area
    )) }))
  }

  function toggleAreaLock(areaId: string): void {
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, locked: !area.locked } : area
    )) }))
  }

  function deleteArea(areaId: string, confirmed = false): void {
    const containsBlocks = state.value.blocks.some((block) => block.areaId === areaId)
    if (containsBlocks && !confirmed) throw new Error('区域含有产品块，删除前需要确认')

    mutate((current) => ({
      ...current,
      areas: current.areas.filter((area) => area.id !== areaId),
      blocks: current.blocks.filter((block) => block.areaId !== areaId),
    }))
  }

  function addBlock(block: WarehouseSkuBlock): void {
    requireArea(block.areaId)
    if (!state.value.catalog.some((sku) => sku.skuId === block.skuId)) throw new Error(`未找到 SKU：${block.skuId}`)
    assertUnits(block.units)
    mutate((current) => ({ ...current, blocks: [...current.blocks, { ...block, ...normalizeRect(block) }] }))
  }

  function updateBlockRect(blockId: string, rect: CanvasRect): void {
    mutate((current) => ({ ...current, blocks: current.blocks.map((block) => (
      block.id === blockId ? { ...block, ...normalizeRect(rect) } : block
    )) }))
  }

  function updateBlockUnits(blockId: string, units: number): void {
    assertUnits(units)
    mutate((current) => ({ ...current, blocks: current.blocks.map((block) => (
      block.id === blockId ? { ...block, units } : block
    )) }))
  }

  function moveWholeBlock(blockId: string, targetAreaId: string, target: Pick<CanvasRect, 'x' | 'y'>): void {
    mutate((current) => moveWholeBlockInModel(current, blockId, targetAreaId, target))
  }

  function splitBlock(command: {
    blockId: string
    targetAreaId: string
    movedUnits: number
    target: Pick<CanvasRect, 'x' | 'y'>
  }): void {
    mutate((current) => splitBlockInModel(current, command))
  }

  function deleteBlock(blockId: string): void {
    mutate((current) => ({ ...current, blocks: current.blocks.filter((block) => block.id !== blockId) }))
  }

  function selectArea(areaId: string | null): void {
    selectedAreaId.value = areaId
    if (areaId !== null) selectedBlockId.value = null
  }

  function selectBlock(blockId: string | null): void {
    selectedBlockId.value = blockId
    if (blockId !== null) selectedAreaId.value = null
  }

  function undo(): void {
    const previous = undoHistory.value.pop()
    if (!previous) return
    redoHistory.value.push(cloneState(state.value))
    state.value = previous
    dirty.value = true
  }

  function redo(): void {
    const next = redoHistory.value.pop()
    if (!next) return
    undoHistory.value.push(cloneState(state.value))
    state.value = next
    dirty.value = true
  }

  async function save(): Promise<boolean> {
    if (issues.value.length > 0) return false
    saving.value = true
    try {
      state.value = cloneState(await repository.save(state.value))
      dirty.value = false
      savedAt.value = new Date()
      return true
    } finally {
      saving.value = false
    }
  }

  function mutate(operation: (current: WarehouseCanvasState) => WarehouseCanvasState): void {
    undoHistory.value.push(cloneState(state.value))
    state.value = operation(state.value)
    redoHistory.value = []
    dirty.value = true
  }

  function requireArea(areaId: string): void {
    if (!state.value.areas.some((area) => area.id === areaId)) throw new Error(`未找到目标区域：${areaId}`)
  }

  return {
    state,
    loading,
    dirty,
    saving,
    savedAt,
    selectedAreaId,
    selectedBlockId,
    activeTool,
    searchQuery,
    issues,
    canUndo,
    canRedo,
    matchedBlockIds,
    load,
    createArea,
    renameArea,
    updateAreaRect,
    toggleAreaVisibility,
    toggleAreaLock,
    deleteArea,
    addBlock,
    updateBlockRect,
    updateBlockUnits,
    moveWholeBlock,
    splitBlock,
    deleteBlock,
    selectArea,
    selectBlock,
    undo,
    redo,
    save,
  }
}

function normalizeRect(rect: CanvasRect): CanvasRect {
  for (const value of [rect.x, rect.y, rect.width, rect.height]) {
    if (!Number.isFinite(value)) throw new Error('矩形坐标必须是有限数字')
  }
  return {
    x: Math.round(rect.x),
    y: Math.round(rect.y),
    width: Math.round(rect.width),
    height: Math.round(rect.height),
  }
}

function assertUnits(units: number): void {
  if (!Number.isInteger(units) || units < 0) throw new Error('库存个数必须是大于或等于零的整数')
}

function cloneState(state: WarehouseCanvasState): WarehouseCanvasState {
  return {
    ...state,
    areas: state.areas.map((area) => ({ ...area })),
    catalog: state.catalog.map((sku) => ({ ...sku })),
    blocks: state.blocks.map((block) => ({ ...block })),
  }
}
