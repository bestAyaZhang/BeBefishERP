import { cloneWarehouseLayout } from './warehouseLayoutModel'
import type { LayoutObject, WarehouseLayoutRepository, WarehouseLayoutState } from './types'

const sharedObjects: LayoutObject[] = [
  { id: 'warehouse-boundary', type: 'boundary', code: 'WH-BOUNDARY', name: '仓库边界', x: 0, y: 0, width: 120, height: 84, rotation: 0, visible: true, locked: true },
  { id: 'zone-a', type: 'fixed-zone', code: 'A区', name: '成品区', x: 4, y: 4, width: 61, height: 31, rotation: 0, visible: true, locked: false },
  { id: 'zone-b', type: 'free-zone', code: 'B区', name: '自由堆放区', x: 69, y: 4, width: 47, height: 31, rotation: 0, visible: true, locked: false },
  { id: 'aisle-main', type: 'aisle', code: '通道-01', name: '主通道', x: 4, y: 39, width: 112, height: 11, rotation: 0, visible: true, locked: false },
  { id: 'zone-c', type: 'fixed-zone', code: 'C区', name: '待检区', x: 4, y: 55, width: 72, height: 25, rotation: 0, visible: true, locked: false },
  { id: 'obstacle-1', type: 'obstacle', code: '立柱-01', name: '承重立柱', x: 80, y: 58, width: 5, height: 5, rotation: 0, visible: true, locked: false },
  ...Array.from({ length: 12 }, (_, index): LayoutObject => {
    const column = index % 4
    const row = Math.floor(index / 4)
    const number = String(index + 1).padStart(2, '0')
    return {
      id: `location-a${number}`,
      type: 'fixed-location',
      code: `A-${number}`,
      name: `成品定位 A-${number}`,
      x: 7 + column * 14,
      y: 7 + row * 8.5,
      width: 11,
      height: 6.5,
      rotation: 0,
      visible: true,
      locked: false,
      parentId: 'zone-a',
      maxHeight: 4,
      maxWeight: 1200,
      maxVolume: 38,
      maxSkuCount: 1,
      singleSku: true,
    }
  }),
  {
    id: 'location-a13', type: 'fixed-location', code: 'A-13', name: '成品定位 A-13',
    x: 110, y: 43, width: 14, height: 9, rotation: 0, visible: true, locked: false,
    parentId: 'zone-b', maxHeight: 4, maxWeight: 1200, maxVolume: 38, maxSkuCount: 1, singleSku: true,
  },
]

function state(
  warehouseId: number,
  warehouseCode: string,
  warehouseName: string,
  warehouseAddress: string,
  locationOffset: number,
): WarehouseLayoutState {
  return {
    warehouseId,
    warehouseCode,
    warehouseName,
    warehouseAddress,
    version: 3,
    publishedVersion: 2,
    status: 'draft',
    savedAt: '2026-09-27T08:18:00+08:00',
    objects: sharedObjects.map((object) => object.id === 'location-a13'
      ? { ...object, y: object.y + locationOffset }
      : { ...object }),
  }
}

export const seedWarehouseLayouts: Record<number, WarehouseLayoutState> = {
  8: state(8, 'WH-HZ-MAIN', '杭州主仓', '浙江省杭州市余杭区', 0),
  9: state(9, 'WH-YW-SPARE', '义乌备货仓', '浙江省义乌市', 1),
}

export function createWarehouseLayoutRepository(
  seeds: Record<number, WarehouseLayoutState> = seedWarehouseLayouts,
): WarehouseLayoutRepository {
  const stored = new Map<number, WarehouseLayoutState>(
    Object.entries(seeds).map(([warehouseId, value]) => [Number(warehouseId), cloneWarehouseLayout(value)]),
  )

  return {
    async load(warehouseId) {
      const value = stored.get(warehouseId)
      if (!value) throw new Error(`未找到仓库布局（ID ${warehouseId}）`)
      return cloneWarehouseLayout(value)
    },
    async saveDraft(value) {
      const saved = { ...cloneWarehouseLayout(value), status: 'draft' as const, savedAt: new Date().toISOString() }
      stored.set(saved.warehouseId, saved)
      return cloneWarehouseLayout(saved)
    },
    async publish(value) {
      const published = {
        ...cloneWarehouseLayout(value),
        version: value.version + 1,
        publishedVersion: value.version + 1,
        status: 'published' as const,
        savedAt: new Date().toISOString(),
      }
      stored.set(published.warehouseId, published)
      return cloneWarehouseLayout(published)
    },
  }
}
