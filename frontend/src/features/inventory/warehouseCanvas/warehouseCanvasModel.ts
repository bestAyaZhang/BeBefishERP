import type { CanvasRect, LayoutIssue, WarehouseCanvasState, WarehouseSkuBlock } from './types'

export function formatCaseBreakdown(units: number, unitsPerCase: number): string {
  const cases = Math.floor(units / unitsPerCase)
  const remainingUnits = units % unitsPerCase

  if (remainingUnits === 0) return `${cases} 件`
  return `${cases} 件 + ${remainingUnits} 个`
}

export function summarizeCanvas(state: WarehouseCanvasState): {
  totalUnits: number
  skuCount: number
  areaTotals: Record<string, number>
} {
  const areaTotals = Object.fromEntries(state.areas.map((area) => [area.id, 0])) as Record<string, number>
  const skuIds = new Set<number>()
  let totalUnits = 0

  for (const block of state.blocks) {
    totalUnits += block.units
    skuIds.add(block.skuId)
    areaTotals[block.areaId] = (areaTotals[block.areaId] ?? 0) + block.units
  }

  return { totalUnits, skuCount: skuIds.size, areaTotals }
}

export function validateCanvasLayout(state: WarehouseCanvasState): LayoutIssue[] {
  const issues: LayoutIssue[] = []
  const areasById = new Map(state.areas.map((area) => [area.id, area]))
  const visibleBlocks = state.blocks.filter((block) => areasById.get(block.areaId)?.visible)
  const blockName = (block: WarehouseSkuBlock) => state.catalog.find((sku) => sku.skuId === block.skuId)?.productName ?? '未知产品'

  for (const block of visibleBlocks) {
    const area = areasById.get(block.areaId)
    if (!area || !isInside(block, area)) {
      issues.push({
        id: `outside-area:${block.id}`,
        type: 'outside-area',
        blockIds: [block.id],
        message: `${blockName(block)} 超出所属区域边界（${area?.name ?? '未知区域'}）；${block.units} 个保持不变。`,
      })
    }
  }

  for (let leftIndex = 0; leftIndex < visibleBlocks.length; leftIndex += 1) {
    const left = visibleBlocks[leftIndex]
    for (let rightIndex = leftIndex + 1; rightIndex < visibleBlocks.length; rightIndex += 1) {
      const right = visibleBlocks[rightIndex]
      if (left.areaId !== right.areaId || !rectanglesOverlap(left, right)) continue

      issues.push({
        id: `overlap:${left.id}:${right.id}`,
        type: 'overlap',
        blockIds: [left.id, right.id],
        message: `${areasById.get(left.areaId)?.name} 内 ${blockName(left)} 与 ${blockName(right)} 重叠；库存未改变。`,
      })
    }
  }

  return issues
}

export function moveWholeBlock(
  state: WarehouseCanvasState,
  blockId: string,
  targetAreaId: string,
  target: Pick<CanvasRect, 'x' | 'y'>,
): WarehouseCanvasState {
  getBlock(state, blockId)
  getArea(state, targetAreaId)
  const normalizedTarget = normalizeTarget(target)

  return {
    ...state,
    blocks: state.blocks.map((block) => (
      block.id === blockId ? { ...block, areaId: targetAreaId, ...normalizedTarget } : block
    )),
  }
}

export function splitBlock(
  state: WarehouseCanvasState,
  command: {
    blockId: string
    targetAreaId: string
    movedUnits: number
    target: Pick<CanvasRect, 'x' | 'y'>
  },
): WarehouseCanvasState {
  const sourceBlock = getBlock(state, command.blockId)
  getArea(state, command.targetAreaId)
  const normalizedTarget = normalizeTarget(command.target)

  if (
    !Number.isInteger(command.movedUnits)
    || command.movedUnits <= 0
    || command.movedUnits >= sourceBlock.units
  ) {
    throw new Error('移动个数必须大于 0 且小于来源库存')
  }

  const splitBlock: WarehouseSkuBlock = {
    ...sourceBlock,
    id: getSplitBlockId(state, sourceBlock.id),
    areaId: command.targetAreaId,
    units: command.movedUnits,
    ...normalizedTarget,
  }

  return {
    ...state,
    blocks: [
      ...state.blocks.map((block) => (
        block.id === sourceBlock.id ? { ...block, units: block.units - command.movedUnits } : block
      )),
      splitBlock,
    ],
  }
}

export function findOpenPosition(
  state: WarehouseCanvasState,
  areaId: string,
  size: Pick<CanvasRect, 'width' | 'height'>,
  excludedBlockId?: string,
): Pick<CanvasRect, 'x' | 'y'> | null {
  const area = state.areas.find((candidate) => candidate.id === areaId)
  if (!area || size.width <= 0 || size.height <= 0) return null

  const blocks = state.blocks.filter((block) => block.areaId === areaId && block.id !== excludedBlockId)
  const candidateXs = [area.x, ...blocks.map((block) => block.x + block.width)].sort((a, b) => a - b)
  const candidateYs = [area.y, ...blocks.map((block) => block.y + block.height)].sort((a, b) => a - b)

  for (const y of candidateYs) {
    for (const x of candidateXs) {
      const candidate = { x, y, ...size }
      if (isInside(candidate, area) && !blocks.some((block) => rectanglesOverlap(candidate, block))) {
        return { x, y }
      }
    }
  }

  return null
}

function isInside(rectangle: CanvasRect, area: CanvasRect): boolean {
  return (
    rectangle.x >= area.x
    && rectangle.y >= area.y
    && rectangle.x + rectangle.width <= area.x + area.width
    && rectangle.y + rectangle.height <= area.y + area.height
  )
}

function rectanglesOverlap(left: CanvasRect, right: CanvasRect): boolean {
  return (
    left.x < right.x + right.width
    && left.x + left.width > right.x
    && left.y < right.y + right.height
    && left.y + left.height > right.y
  )
}

function getBlock(state: WarehouseCanvasState, blockId: string): WarehouseSkuBlock {
  const block = state.blocks.find((candidate) => candidate.id === blockId)
  if (!block) throw new Error(`未找到产品块：${blockId}`)
  return block
}

function getArea(state: WarehouseCanvasState, areaId: string): void {
  if (!state.areas.some((area) => area.id === areaId)) {
    throw new Error(`未找到目标区域：${areaId}`)
  }
}

function getSplitBlockId(state: WarehouseCanvasState, sourceBlockId: string): string {
  let sequence = 1
  let id = `${sourceBlockId}-split-${sequence}`
  const ids = new Set(state.blocks.map((block) => block.id))

  while (ids.has(id)) {
    sequence += 1
    id = `${sourceBlockId}-split-${sequence}`
  }

  return id
}

function normalizeTarget(target: Pick<CanvasRect, 'x' | 'y'>): Pick<CanvasRect, 'x' | 'y'> {
  if (!Number.isFinite(target.x) || !Number.isFinite(target.y)) {
    throw new Error('坐标必须是有限数字')
  }

  return { x: Math.round(target.x), y: Math.round(target.y) }
}
