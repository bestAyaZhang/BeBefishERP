import { describe, expect, it } from 'vitest'
import {
  findOpenPosition,
  formatCaseBreakdown,
  moveWholeBlock,
  splitBlock,
  summarizeCanvas,
  validateCanvasLayout,
} from './warehouseCanvasModel'
import { seedWarehouseCanvas } from './mockWarehouseCanvasData'

describe('warehouse canvas model', () => {
  it('keeps every seed area inside the 728 by 672 logical floor', () => {
    expect(seedWarehouseCanvas.areas.every((area) => (
      area.x >= 0
      && area.y >= 0
      && area.x + area.width <= 728
      && area.y + area.height <= 672
    ))).toBe(true)
  })

  it('formats complete cases and remaining units from the authoritative unit count', () => {
    expect(formatCaseBreakdown(250, 24)).toBe('10 件 + 10 个')
    expect(formatCaseBreakdown(576, 24)).toBe('24 件')
    expect(formatCaseBreakdown(0, 24)).toBe('0 件')
  })

  it('summarizes all blocks by warehouse, SKU, and area', () => {
    const summary = summarizeCanvas(seedWarehouseCanvas)

    expect(summary.totalUnits).toBe(1026)
    expect(summary.skuCount).toBe(4)
    expect(summary.areaTotals).toEqual({ 'area-a': 270, 'area-b': 656, 'area-c': 100 })
  })

  it('reports blocks that extend outside their assigned area', () => {
    const outsideState = {
      ...seedWarehouseCanvas,
      blocks: seedWarehouseCanvas.blocks.map((block) =>
        block.id === 'block-blue-a' ? { ...block, x: 20 } : block,
      ),
    }

    expect(validateCanvasLayout(outsideState)[0]).toMatchObject({
      type: 'outside-area',
      blockIds: ['block-blue-a'],
    })
  })

  it('reports same-area blocks that overlap', () => {
    const overlapState = {
      ...seedWarehouseCanvas,
      blocks: seedWarehouseCanvas.blocks.map((block) =>
        block.id === 'block-tea-b' ? { ...block, x: 260, y: 390 } : block,
      ),
    }

    expect(validateCanvasLayout(overlapState)[0]).toMatchObject({
      type: 'overlap',
      blockIds: ['block-pink-b', 'block-tea-b'],
    })
  })

  it('ignores invalid blocks in hidden areas during layout validation', () => {
    const hiddenOverlapState = {
      ...seedWarehouseCanvas,
      areas: seedWarehouseCanvas.areas.map((area) => (
        area.id === 'area-b' ? { ...area, visible: false } : area
      )),
      blocks: seedWarehouseCanvas.blocks.map((block) => (
        block.id === 'block-tea-b' ? { ...block, x: 260, y: 390 } : block
      )),
    }

    expect(validateCanvasLayout(hiddenOverlapState)).toEqual([])
  })

  it('moves a complete block to a target area without changing its units', () => {
    const whole = moveWholeBlock(seedWarehouseCanvas, 'block-blue-a', 'area-b', { x: 76, y: 500 })

    expect(whole.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({
      areaId: 'area-b',
      units: 150,
    })
    expect(seedWarehouseCanvas.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({
      areaId: 'area-a',
      units: 150,
    })
  })

  it('splits only a valid partial quantity and conserves SKU inventory', () => {
    const partial = splitBlock(seedWarehouseCanvas, {
      blockId: 'block-blue-a',
      targetAreaId: 'area-b',
      movedUnits: 60,
      target: { x: 76, y: 500 },
    })

    expect(partial.blocks.find((block) => block.id === 'block-blue-a')?.units).toBe(90)
    expect(partial.blocks.reduce((total, block) => block.skuId === 101 ? total + block.units : total, 0)).toBe(250)
    expect(() => splitBlock(seedWarehouseCanvas, {
      blockId: 'block-blue-a',
      targetAreaId: 'area-b',
      movedUnits: 150,
      target: { x: 76, y: 500 },
    })).toThrow('移动个数必须大于 0 且小于来源库存')
  })

  it('normalizes whole and partial move targets to integer logical coordinates', () => {
    const whole = moveWholeBlock(seedWarehouseCanvas, 'block-blue-a', 'area-b', { x: 76.4, y: 500.6 })
    const partial = splitBlock(seedWarehouseCanvas, {
      blockId: 'block-blue-a',
      targetAreaId: 'area-b',
      movedUnits: 60,
      target: { x: 76.6, y: 500.2 },
    })

    expect(whole.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({ x: 76, y: 501 })
    expect(partial.blocks.find((block) => block.id === 'block-blue-a-split-1')).toMatchObject({ x: 77, y: 500 })
  })

  it('finds the first in-area position that does not overlap another block', () => {
    const position = findOpenPosition(seedWarehouseCanvas, 'area-a', { width: 120, height: 80 })

    expect(position).not.toBeNull()
    if (position === null) throw new Error('Expected an open position in area A-01')

    expect(validateCanvasLayout({
      ...seedWarehouseCanvas,
      blocks: [
        ...seedWarehouseCanvas.blocks,
        { id: 'new-block', skuId: 104, areaId: 'area-a', units: 1, width: 120, height: 80, ...position },
      ],
    })).toEqual([])
  })
})
