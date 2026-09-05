import { describe, expect, it } from 'vitest'
import { createMemoryWarehouseCanvasRepository, seedWarehouseCanvas } from './mockWarehouseCanvasData'
import { useWarehouseCanvas } from './useWarehouseCanvas'

function createCanvas() {
  return useWarehouseCanvas({
    repository: createMemoryWarehouseCanvasRepository(seedWarehouseCanvas),
  })
}

describe('warehouse canvas controller', () => {
  it('renames an area and restores the change through undo and redo', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.renameArea('area-a', 'A-成品')
    expect(canvas.state.value.areas[0].name).toBe('A-成品')
    expect(canvas.dirty.value).toBe(true)

    canvas.undo()
    expect(canvas.state.value.areas[0].name).toBe('A-01')

    canvas.redo()
    expect(canvas.state.value.areas[0].name).toBe('A-成品')
  })

  it('creates an area with an integer rectangle', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.createArea({ id: 'area-d', name: 'D-01', x: 500.6, y: 400.4, width: 100.8, height: 80.2 })

    expect(canvas.state.value.areas.find((area) => area.id === 'area-d')).toMatchObject({
      name: 'D-01', x: 501, y: 400, width: 101, height: 80, visible: true, locked: false,
    })
  })

  it('toggles an area visibility and lock state', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.toggleAreaVisibility('area-a')
    canvas.toggleAreaLock('area-a')

    expect(canvas.state.value.areas.find((area) => area.id === 'area-a')).toMatchObject({ visible: false, locked: true })
  })

  it('updates a block unit count with an integer value', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.updateBlockUnits('block-blue-a', 175)

    expect(canvas.state.value.blocks.find((block) => block.id === 'block-blue-a')?.units).toBe(175)
  })

  it('resizes a block without changing its units', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.updateBlockRect('block-blue-a', { x: 80.2, y: 100.8, width: 140.6, height: 90.4 })

    expect(canvas.state.value.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({
      x: 80, y: 101, width: 141, height: 90, units: 150,
    })
  })

  it('moves a whole block to another area without changing its units', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.moveWholeBlock('block-blue-a', 'area-b', { x: 76.4, y: 450.6 })

    expect(canvas.state.value.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({
      areaId: 'area-b', x: 76, y: 451, units: 150,
    })
  })

  it('splits a partial move while conserving the original SKU inventory', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.splitBlock({
      blockId: 'block-blue-a', targetAreaId: 'area-b', movedUnits: 60, target: { x: 76, y: 500 },
    })

    expect(canvas.state.value.blocks.find((block) => block.id === 'block-blue-a')?.units).toBe(90)
    expect(canvas.state.value.blocks.filter((block) => block.skuId === 101).reduce((total, block) => total + block.units, 0)).toBe(250)
  })

  it('does not save a layout with validation issues', async () => {
    const repository = createMemoryWarehouseCanvasRepository(seedWarehouseCanvas)
    const canvas = useWarehouseCanvas({ repository })
    await canvas.load(8)
    canvas.updateBlockRect('block-blue-a', { x: 0, y: 0, width: 120, height: 80 })

    await expect(canvas.save()).resolves.toBe(false)
    expect(canvas.issues.value).toHaveLength(1)
    expect(canvas.dirty.value).toBe(true)
    expect((await repository.load(8)).blocks.find((block) => block.id === 'block-blue-a')?.x).toBe(76)
  })

  it('saves a valid layout and clears its dirty state', async () => {
    const repository = createMemoryWarehouseCanvasRepository(seedWarehouseCanvas)
    const canvas = useWarehouseCanvas({ repository })
    await canvas.load(8)
    canvas.renameArea('area-a', 'A-成品')

    await expect(canvas.save()).resolves.toBe(true)
    expect(canvas.dirty.value).toBe(false)
    expect(canvas.savedAt.value).toBeInstanceOf(Date)
    expect((await repository.load(8)).areas[0].name).toBe('A-成品')
  })

  it('finds both blue SKU blocks when searching by product name', async () => {
    const canvas = createCanvas()
    await canvas.load(8)

    canvas.searchQuery.value = '蓝色'

    expect(canvas.matchedBlockIds.value).toEqual(['block-blue-a', 'block-blue-c'])
  })
})
