import { describe, expect, it } from 'vitest'
import { createWarehouseLayoutRepository, seedWarehouseLayouts } from './mockWarehouseLayoutRepository'
import { updateLayoutObject, validateWarehouseLayout } from './warehouseLayoutModel'
import type { WarehouseLayoutState } from './types'

describe('warehouse layout model', () => {
  it('provides independent approved drafts for each warehouse', async () => {
    const repository = createWarehouseLayoutRepository(seedWarehouseLayouts)

    const hangzhou = await repository.load(8)
    const yiwu = await repository.load(9)

    expect(hangzhou).toMatchObject({ warehouseId: 8, warehouseCode: 'WH-HZ-MAIN', warehouseName: '杭州主仓' })
    expect(yiwu).toMatchObject({ warehouseId: 9, warehouseCode: 'WH-YW-SPARE', warehouseName: '义乌备货仓' })
    expect(hangzhou.objects).not.toBe(yiwu.objects)

    const changed = updateLayoutObject(hangzhou, 'location-a13', { name: '成品定位 A-13（调整）' })
    await repository.saveDraft(changed)

    expect((await repository.load(8)).objects.find((object) => object.id === 'location-a13')?.name).toContain('调整')
    expect((await repository.load(9)).objects.find((object) => object.id === 'location-a13')?.name).not.toContain('调整')
  })

  it('updates geometry immutably and keeps the original draft intact', () => {
    const state = seedWarehouseLayouts[8]
    const updated = updateLayoutObject(state, 'location-a13', { x: 75, y: 61, width: 18 })

    expect(updated.objects.find((object) => object.id === 'location-a13')).toMatchObject({ x: 75, y: 61, width: 18 })
    expect(state.objects.find((object) => object.id === 'location-a13')).not.toMatchObject({ x: 75, y: 61, width: 18 })
  })

  it('reports objects outside the warehouse boundary as blocking issues', () => {
    const state = updateLayoutObject(seedWarehouseLayouts[8], 'location-a13', { x: 118, width: 12 })

    expect(validateWarehouseLayout(state)).toContainEqual(expect.objectContaining({
      objectId: 'location-a13',
      rule: 'OUT_OF_BOUNDARY',
      severity: 'error',
    }))
  })

  it('reports a fixed location intersecting an aisle as a blocking issue', () => {
    const state = updateLayoutObject(seedWarehouseLayouts[8], 'location-a13', { x: 28, y: 43, width: 16, height: 12 })

    expect(validateWarehouseLayout(state)).toContainEqual(expect.objectContaining({
      objectId: 'location-a13',
      rule: 'AISLE_OVERLAP',
      severity: 'error',
    }))
  })

  it('rejects missing warehouse IDs instead of loading another warehouse', async () => {
    const repository = createWarehouseLayoutRepository(seedWarehouseLayouts)

    await expect(repository.load(404)).rejects.toThrow('未找到仓库布局')
  })

  it('publishes only the requested warehouse and increments its version', async () => {
    const repository = createWarehouseLayoutRepository(seedWarehouseLayouts)
    const draft = await repository.load(9)

    const published = await repository.publish({ ...draft, issues: [] } as WarehouseLayoutState)

    expect(published).toMatchObject({ warehouseId: 9, status: 'published', version: 4 })
    expect((await repository.load(8)).version).toBe(3)
  })
})
