import { beforeEach, describe, expect, it, vi } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../../services/http', () => ({ request }))

import {
  createMockWarehouseInventoryService,
  httpWarehouseInventoryService,
} from './warehouseInventoryService'

describe('warehouse inventory layout service', () => {
  beforeEach(() => request.mockReset())

  it('loads actual warehouse inventory from the warehouse-scoped endpoint', async () => {
    request.mockResolvedValue({ warehouseId: 8, totalUnits: 0, skuCount: 0, placedUnits: 0, unallocatedUnits: 0, updatedAt: null, allocations: [] })

    await httpWarehouseInventoryService.load(8)

    expect(request).toHaveBeenCalledWith('/api/warehouses/8/inventory-layout')
  })

  it('allocates unassigned units to a pile through the warehouse endpoint', async () => {
    const layout = { warehouseId: 8, totalUnits: 100, skuCount: 1, placedUnits: 24, unallocatedUnits: 76, updatedAt: null, allocations: [] }
    request.mockResolvedValue(layout)

    await httpWarehouseInventoryService.allocateToPile(8, { palletId: 'pallet-c018', skuId: 101, units: 24 })

    expect(request).toHaveBeenCalledWith(
      '/api/warehouses/8/inventory-layout/pile-allocations',
      { method: 'POST', body: JSON.stringify({ palletId: 'pallet-c018', skuId: 101, units: 24 }) },
    )
  })

  it('searches catalog SKUs through the inventory-scoped endpoint', async () => {
    request.mockResolvedValue([])

    await httpWarehouseInventoryService.listSkuCandidates(8, '玻璃 杯')

    expect(request).toHaveBeenCalledWith('/api/warehouses/8/inventory-layout/sku-candidates?keyword=%E7%8E%BB%E7%92%83+%E6%9D%AF')
  })

  it('provides clearly local mock allocations for UI development', async () => {
    const result = await createMockWarehouseInventoryService().load(8)

    expect(result.warehouseId).toBe(8)
    expect(result.totalUnits).toBeGreaterThan(0)
    expect(result.allocations.some(item => item.zoneId && item.palletId !== 'UNALLOCATED')).toBe(true)
    expect(result.allocations.every(item => item.units >= 0)).toBe(true)
  })

  it('moves available unallocated units into the requested pile without changing the total', async () => {
    const service = createMockWarehouseInventoryService()
    await service.load(8)

    await service.allocateToPile(8, { palletId: 'pallet-a03', skuId: 104, units: 24 })

    const allocated = await service.load(8)
    expect(allocated.totalUnits).toBe(990)
    expect(allocated.placedUnits).toBe(942)
    expect(allocated.unallocatedUnits).toBe(48)
    expect(allocated.allocations).toContainEqual(expect.objectContaining({ palletId: 'pallet-a03', skuId: 104, units: 24, zoneId: null }))

    await expect(service.allocateToPile(8, { palletId: 'pallet-a03', skuId: 104, units: -1 })).rejects.toThrow()
    await expect(service.allocateToPile(8, { palletId: 'pallet-a03', skuId: 104, units: 1.5 })).rejects.toThrow()
    await expect(service.allocateToPile(8, { palletId: 'pallet-a03', skuId: 104, units: 49 })).rejects.toThrow()

    expect(await service.load(8)).toEqual(allocated)
  })

  it('links a catalog SKU with no stock to a pile while keeping all inventory totals at zero', async () => {
    const service = createMockWarehouseInventoryService()
    const before = await service.load(8)
    const candidates = await service.listSkuCandidates(8, 'ZERO')
    const sku = candidates.find(item => item.skuCode === 'SKU-ZERO-001')!

    const result = await service.allocateToPile(8, { palletId: 'pallet-a03', skuId: sku.skuId, units: 0 })

    expect(result.totalUnits).toBe(before.totalUnits)
    expect(result.placedUnits).toBe(before.placedUnits)
    expect(result.unallocatedUnits).toBe(before.unallocatedUnits)
    expect(result.allocations).toContainEqual(expect.objectContaining({ palletId: 'pallet-a03', skuId: sku.skuId, units: 0 }))
  })
})
