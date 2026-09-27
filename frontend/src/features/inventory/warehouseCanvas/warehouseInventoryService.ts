import { request } from '../../../services/http'
import { createService } from '../../../services/serviceFactory'

export type WarehouseInventoryAllocation = {
  zoneId: string | null
  palletId: string
  skuId: number
  skuCode: string
  productName: string
  skuName: string | null
  specification: string | null
  unitsPerCase: number | null
  units: number
}

export type WarehouseInventoryLayout = {
  warehouseId: number
  totalUnits: number
  skuCount: number
  placedUnits: number
  unallocatedUnits: number
  updatedAt: string | null
  allocations: WarehouseInventoryAllocation[]
}

export type WarehousePileAllocationInput = {
  palletId: string
  skuId: number
  units: number
}

export type WarehouseInventoryService = {
  load: (warehouseId: number) => Promise<WarehouseInventoryLayout>
  allocateToPile: (warehouseId: number, input: WarehousePileAllocationInput) => Promise<WarehouseInventoryLayout>
}

export const httpWarehouseInventoryService: WarehouseInventoryService = {
  load: warehouseId => request<WarehouseInventoryLayout>(`/api/warehouses/${warehouseId}/inventory-layout`),
  allocateToPile: (warehouseId, input) => request<WarehouseInventoryLayout>(
    `/api/warehouses/${warehouseId}/inventory-layout/pile-allocations`,
    { method: 'POST', body: JSON.stringify(input) },
  ),
}

export function createMockWarehouseInventoryService(): WarehouseInventoryService {
  const layouts = new Map<number, WarehouseInventoryLayout>()

  const cloneLayout = (layout: WarehouseInventoryLayout): WarehouseInventoryLayout => ({
    ...layout,
    allocations: layout.allocations.map(allocation => ({ ...allocation })),
  })

  const createLayout = (warehouseId: number): WarehouseInventoryLayout => {
    const allocations: WarehouseInventoryAllocation[] = [
      { zoneId: 'zone-receiving', palletId: 'pallet-a01', skuId: 101, skuCode: 'SKU-FISH-500ML-蓝', productName: '深海矿物水 500ml 蓝', skuName: '蓝色瓶装', specification: '500ml × 24', unitsPerCase: 24, units: 288 },
      { zoneId: 'zone-receiving', palletId: 'pallet-a02', skuId: 102, skuCode: 'SKU-CUP-12OZ', productName: '12oz 冷饮杯', skuName: '透明', specification: '50 个/箱', unitsPerCase: 50, units: 200 },
      { zoneId: 'zone-buffer', palletId: 'pallet-c018', skuId: 101, skuCode: 'SKU-FISH-500ML-蓝', productName: '深海矿物水 500ml 蓝', skuName: '蓝色瓶装', specification: '500ml × 24', unitsPerCase: 24, units: 120 },
      { zoneId: 'zone-buffer', palletId: 'pallet-c018', skuId: 102, skuCode: 'SKU-CUP-12OZ', productName: '12oz 冷饮杯', skuName: '透明', specification: '50 个/箱', unitsPerCase: 50, units: 80 },
      { zoneId: 'zone-buffer', palletId: 'pallet-c018', skuId: 103, skuCode: 'SKU-TEA-1L-绿', productName: '茉莉绿茶 1L', skuName: '绿标', specification: '1L × 20', unitsPerCase: 20, units: 50 },
      { zoneId: 'zone-shipping', palletId: 'pallet-c01', skuId: 103, skuCode: 'SKU-TEA-1L-绿', productName: '茉莉绿茶 1L', skuName: '绿标', specification: '1L × 20', unitsPerCase: 20, units: 180 },
      { zoneId: null, palletId: 'UNALLOCATED', skuId: 104, skuCode: 'SKU-PENDING-001', productName: '待分配商品', skuName: null, specification: null, unitsPerCase: 12, units: 72 },
    ]
    const totalUnits = allocations.reduce((sum, item) => sum + item.units, 0)
    const placedUnits = allocations.filter(item => item.palletId !== 'UNALLOCATED').reduce((sum, item) => sum + item.units, 0)
    return {
      warehouseId,
      totalUnits,
      skuCount: new Set(allocations.map(item => item.skuId)).size,
      placedUnits,
      unallocatedUnits: totalUnits - placedUnits,
      updatedAt: new Date().toISOString(),
      allocations,
    }
  }

  const storedLayout = (warehouseId: number): WarehouseInventoryLayout => {
    const existing = layouts.get(warehouseId)
    if (existing) return existing

    const layout = createLayout(warehouseId)
    layouts.set(warehouseId, cloneLayout(layout))
    return layout
  }

  return {
    async load(warehouseId) {
      return cloneLayout(storedLayout(warehouseId))
    },
    async allocateToPile(warehouseId, input) {
      if (!Number.isInteger(input.units) || input.units <= 0) {
        throw new Error('Allocated units must be a positive integer')
      }

      const current = storedLayout(warehouseId)
      const next = cloneLayout(current)
      const sourceIndex = next.allocations.findIndex(allocation => (
        allocation.palletId === 'UNALLOCATED' && allocation.skuId === input.skuId
      ))
      const source = next.allocations[sourceIndex]
      if (!source || source.units < input.units) {
        throw new Error('Insufficient unallocated units')
      }

      if (source.units === input.units) next.allocations.splice(sourceIndex, 1)
      else source.units -= input.units

      const target = next.allocations.find(allocation => (
        allocation.palletId === input.palletId && allocation.skuId === input.skuId
      ))
      if (target) target.units += input.units
      else next.allocations.push({ ...source, zoneId: null, palletId: input.palletId, units: input.units })

      const placedUnits = next.allocations
        .filter(allocation => allocation.palletId !== 'UNALLOCATED')
        .reduce((sum, allocation) => sum + allocation.units, 0)
      const unallocatedUnits = next.allocations
        .filter(allocation => allocation.palletId === 'UNALLOCATED')
        .reduce((sum, allocation) => sum + allocation.units, 0)
      const updatedLayout: WarehouseInventoryLayout = {
        ...next,
        skuCount: new Set(next.allocations.map(allocation => allocation.skuId)).size,
        placedUnits,
        unallocatedUnits,
        updatedAt: new Date().toISOString(),
      }
      layouts.set(warehouseId, cloneLayout(updatedLayout))
      return cloneLayout(updatedLayout)
    },
  }
}

export const mockWarehouseInventoryService = createMockWarehouseInventoryService()
export const warehouseInventoryService: WarehouseInventoryService = createService(
  () => mockWarehouseInventoryService,
  () => httpWarehouseInventoryService,
)
