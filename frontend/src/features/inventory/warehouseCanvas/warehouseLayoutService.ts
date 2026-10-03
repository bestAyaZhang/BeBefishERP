import { request } from '../../../services/http'
import { createService } from '../../../services/serviceFactory'
import type { WarehouseStructure } from './warehouseStructure'
import type { PlannerPalletGroup } from './warehousePlannerScene'
import { mockWarehouseInventoryService } from './warehouseInventoryService'
import type { MockWarehouseInventoryService } from './warehouseInventoryService'

export type WarehouseLayoutDocument = {
  schemaVersion: 1
  structure: WarehouseStructure
  palletGroups: PlannerPalletGroup[]
  completed: boolean
}
export type WarehouseLayoutResponse = { revision: number; document: WarehouseLayoutDocument | null }
export type ReleasedPileAllocation = { palletId: string; allocations: { skuId: number; units: number }[] }
export type WarehouseLayoutSaveInput = {
  revision: number
  document: WarehouseLayoutDocument
  releasedPileAllocations?: ReleasedPileAllocation[]
}
export type WarehouseLayoutService = {
  load: (id: number) => Promise<WarehouseLayoutResponse>
  save: (id: number, value: WarehouseLayoutSaveInput) => Promise<WarehouseLayoutResponse>
}
export function blankWarehouseLayout(): WarehouseLayoutDocument {
  return { schemaVersion:1, structure:{outline:{id:'outline',closed:true,nodes:[]},partitions:[],doors:[],elevators:[],columns:[],zones:[]}, palletGroups:[], completed:false }
}
export const httpWarehouseLayoutService: WarehouseLayoutService = {
  load: (id: number) => request<WarehouseLayoutResponse>(`/api/warehouses/${id}/layout`),
  save: (id: number, value: WarehouseLayoutSaveInput) => request<WarehouseLayoutResponse>(`/api/warehouses/${id}/layout`, { method:'PUT', body:JSON.stringify(value) }),
}

export function createMemoryWarehouseLayoutService(inventory: Pick<MockWarehouseInventoryService, 'releaseConfirmedPiles'> = mockWarehouseInventoryService): WarehouseLayoutService {
  const layouts = new Map<number, WarehouseLayoutResponse>()
  const clone = (value: WarehouseLayoutResponse): WarehouseLayoutResponse => JSON.parse(JSON.stringify(value)) as WarehouseLayoutResponse
  return {
    async load(id) {
      return clone(layouts.get(id) ?? { revision: 0, document: null })
    },
    async save(id, value) {
      const existing = layouts.get(id) ?? { revision: 0, document: null }
      if (existing.revision !== value.revision) throw new Error('规划已被其他人修改，请刷新后重试')
      const oldIds = new Set(existing.document?.palletGroups.map(pile => pile.id) ?? [])
      const newIds = new Set(value.document.palletGroups.map(pile => pile.id))
      const removedIds = [...oldIds].filter(pileId => !newIds.has(pileId))
      inventory.releaseConfirmedPiles(id, removedIds, value.releasedPileAllocations ?? [])
      const saved = clone({ revision: value.revision + 1, document: value.document })
      layouts.set(id, saved)
      return clone(saved)
    },
  }
}

export const mockWarehouseLayoutService = createMemoryWarehouseLayoutService()
export const warehouseLayoutService: WarehouseLayoutService = createService(
  () => mockWarehouseLayoutService,
  () => httpWarehouseLayoutService,
)
