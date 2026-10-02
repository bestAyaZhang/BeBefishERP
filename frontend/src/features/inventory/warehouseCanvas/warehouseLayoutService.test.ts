import { beforeEach, describe, expect, it, vi } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../../services/http', () => ({ request }))

import { createMockWarehouseInventoryService } from './warehouseInventoryService'
import { blankWarehouseLayout, createMemoryWarehouseLayoutService, httpWarehouseLayoutService } from './warehouseLayoutService'

describe('warehouse layout save contract', () => {
  beforeEach(() => request.mockReset())

  it('sends confirmed pile allocation snapshots separately from the persisted document', async () => {
    const document = blankWarehouseLayout()
    const releasedPileAllocations = [{ palletId: 'pallet-a01', allocations: [{ skuId: 101, units: 288 }, { skuId: 999, units: 0 }] }]
    request.mockResolvedValue({ revision: 4, document })

    await httpWarehouseLayoutService.save(8, { revision: 3, document, releasedPileAllocations })

    expect(request).toHaveBeenCalledWith('/api/warehouses/8/layout', {
      method: 'PUT',
      body: JSON.stringify({ revision: 3, document, releasedPileAllocations }),
    })
    expect(document).not.toHaveProperty('releasedPileAllocations')
  })

  it('omits release metadata from ordinary layout saves', async () => {
    const document = blankWarehouseLayout()
    request.mockResolvedValue({ revision: 1, document })

    await httpWarehouseLayoutService.save(8, { revision: 0, document })

    expect(request).toHaveBeenCalledWith('/api/warehouses/8/layout', {
      method: 'PUT', body: JSON.stringify({ revision: 0, document }),
    })
  })

  it('releases confirmed mock allocations to unallocated without changing warehouse stock', async () => {
    const inventory = createMockWarehouseInventoryService()
    const layouts = createMemoryWarehouseLayoutService(inventory)
    const document = blankWarehouseLayout()
    document.palletGroups = [{ id: 'pallet-a01' }] as typeof document.palletGroups
    await layouts.save(8, { revision: 0, document })
    await inventory.allocateToPile(8, { palletId: 'pallet-a01', skuId: 999, units: 0 })
    const before = await inventory.load(8)

    const nextDocument = blankWarehouseLayout()
    const saved = await layouts.save(8, {
      revision: 1,
      document: nextDocument,
      releasedPileAllocations: [{
        palletId: 'pallet-a01',
        allocations: [{ skuId: 101, units: 288 }, { skuId: 999, units: 0 }],
      }],
    })

    const after = await inventory.load(8)
    expect(saved).toEqual({ revision: 2, document: nextDocument })
    expect(after.totalUnits).toBe(before.totalUnits)
    expect(after.placedUnits).toBe(before.placedUnits - 288)
    expect(after.unallocatedUnits).toBe(before.unallocatedUnits + 288)
    expect(after.allocations.filter(item => item.palletId === 'pallet-a01')).toEqual([])
    expect(after.allocations).toContainEqual(expect.objectContaining({ palletId: 'UNALLOCATED', skuId: 101, units: 288 }))
  })
})
