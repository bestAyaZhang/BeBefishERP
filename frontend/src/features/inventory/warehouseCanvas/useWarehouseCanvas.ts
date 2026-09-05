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

const logicalFloor = { width: 728, height: 672 }

export function useWarehouseCanvas(options: { repository?: WarehouseCanvasRepository } = {}) {
  const repository = options.repository ?? createMemoryWarehouseCanvasRepository(seedWarehouseCanvas)
  const state = ref<WarehouseCanvasState>(cloneState(emptyState))
  const savedState = ref<WarehouseCanvasState>(cloneState(emptyState))
  const loading = ref(false)
  const dirty = computed(() => !statesEqual(state.value, savedState.value))
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
      const loadedState = cloneState(await repository.load(warehouseId))
      assertCanvasGeometry(loadedState)
      state.value = loadedState
      savedState.value = cloneState(loadedState)
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
    if (state.value.areas.some((existingArea) => existingArea.id === area.id)) {
      throw new Error(`区域 ID 已存在：${area.id}`)
    }
    const rect = normalizeAreaRect(area)
    mutate((current) => ({
      ...current,
      areas: [...current.areas, {
        ...area,
        ...rect,
        visible: area.visible ?? true,
        locked: area.locked ?? false,
      }],
    }))
  }

  function renameArea(areaId: string, name: string): void {
    requireArea(areaId)
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, name } : area
    )) }))
  }

  function updateAreaRect(areaId: string, rect: CanvasRect): void {
    requireArea(areaId)
    const normalizedRect = normalizeAreaRect(rect)
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, ...normalizedRect } : area
    )) }))
  }

  function toggleAreaVisibility(areaId: string): void {
    requireArea(areaId)
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, visible: !area.visible } : area
    )) }))
  }

  function toggleAreaLock(areaId: string): void {
    requireArea(areaId)
    mutate((current) => ({ ...current, areas: current.areas.map((area) => (
      area.id === areaId ? { ...area, locked: !area.locked } : area
    )) }))
  }

  function deleteArea(areaId: string, confirmed = false): void {
    requireArea(areaId)
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
    if (state.value.blocks.some((existingBlock) => existingBlock.id === block.id)) throw new Error(`产品块 ID 已存在：${block.id}`)
    assertUnits(block.units)
    const normalizedRect = normalizeRect(block)
    mutate((current) => ({ ...current, blocks: [...current.blocks, { ...block, ...normalizedRect }] }))
  }

  function updateBlockRect(blockId: string, rect: CanvasRect): void {
    requireBlock(blockId)
    const normalizedRect = normalizeRect(rect)
    mutate((current) => ({ ...current, blocks: current.blocks.map((block) => (
      block.id === blockId ? { ...block, ...normalizedRect } : block
    )) }))
  }

  function updateBlockUnits(blockId: string, units: number): void {
    requireBlock(blockId)
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
    requireBlock(blockId)
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
  }

  function redo(): void {
    const next = redoHistory.value.pop()
    if (!next) return
    undoHistory.value.push(cloneState(state.value))
    state.value = next
  }

  async function save(): Promise<boolean> {
    if (issues.value.length > 0) return false
    const stateAtSaveStart = cloneState(state.value)
    saving.value = true
    try {
      const saved = cloneState(await repository.save(stateAtSaveStart))
      savedState.value = cloneState(saved)
      if (statesEqual(state.value, stateAtSaveStart)) state.value = saved
      savedAt.value = new Date()
      return true
    } finally {
      saving.value = false
    }
  }

  function mutate(operation: (current: WarehouseCanvasState) => WarehouseCanvasState): void {
    const nextState = operation(state.value)
    if (statesEqual(nextState, state.value)) return
    undoHistory.value.push(cloneState(state.value))
    state.value = nextState
    redoHistory.value = []
  }

  function requireArea(areaId: string): void {
    if (!state.value.areas.some((area) => area.id === areaId)) throw new Error(`未找到目标区域：${areaId}`)
  }

  function requireBlock(blockId: string): void {
    if (!state.value.blocks.some((block) => block.id === blockId)) {
      throw new Error(`未找到产品块：${blockId}`)
    }
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
  const normalized = {
    x: Math.round(rect.x),
    y: Math.round(rect.y),
    width: Math.round(rect.width),
    height: Math.round(rect.height),
  }
  if (normalized.width <= 0 || normalized.height <= 0) {
    throw new Error('矩形宽高必须大于 0')
  }
  return normalized
}

function normalizeAreaRect(rect: CanvasRect): CanvasRect {
  const normalized = normalizeRect(rect)
  if (
    normalized.x < 0
    || normalized.y < 0
    || normalized.x + normalized.width > logicalFloor.width
    || normalized.y + normalized.height > logicalFloor.height
  ) {
    throw new Error('区域必须位于逻辑画布范围内')
  }
  return normalized
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

function assertCanvasGeometry(state: WarehouseCanvasState): void {
  state.areas.forEach(normalizeAreaRect)
  state.blocks.forEach(normalizeRect)
}

function statesEqual(left: WarehouseCanvasState, right: WarehouseCanvasState): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}
