import type { WarehouseCanvasState } from './types'

export interface WarehouseCanvasRepository {
  load(warehouseId: number): Promise<WarehouseCanvasState>
  save(state: WarehouseCanvasState): Promise<WarehouseCanvasState>
}

export const seedWarehouseCanvas: WarehouseCanvasState = {
  warehouseId: 1,
  warehouseName: '华东一号仓',
  areas: [
    { id: 'area-a', name: 'A-01', x: 16, y: 16, width: 696, height: 220, visible: true, locked: false },
    { id: 'area-b', name: 'B-01', x: 16, y: 252, width: 340, height: 400, visible: true, locked: false },
    { id: 'area-c', name: 'C-01', x: 372, y: 252, width: 340, height: 400, visible: true, locked: false },
  ],
  catalog: [
    { id: 'sku-blue', skuId: 101, skuCode: 'SKU-FISH-500ML-蓝', productName: '深海矿物水 500ml 蓝', unitsPerCase: 24, accent: 'blue' },
    { id: 'sku-cyan', skuId: 104, skuCode: 'SKU-CUP-12OZ', productName: '12oz 冷饮杯', unitsPerCase: 50, accent: 'cyan' },
    { id: 'sku-pink', skuId: 102, skuCode: 'SKU-FISH-350ML-粉', productName: '深海矿物水 350ml 粉', unitsPerCase: 24, accent: 'pink' },
    { id: 'sku-tea', skuId: 103, skuCode: 'SKU-TEA-1L-绿', productName: '茉莉绿茶 1L', unitsPerCase: 20, accent: 'green' },
  ],
  blocks: [
    { id: 'block-blue-a', skuId: 101, areaId: 'area-a', units: 150, x: 48, y: 64, width: 210, height: 135 },
    { id: 'block-cyan-a', skuId: 104, areaId: 'area-a', units: 120, x: 276, y: 64, width: 210, height: 135 },
    { id: 'block-pink-b', skuId: 102, areaId: 'area-b', units: 576, x: 48, y: 304, width: 210, height: 135 },
    { id: 'block-tea-b', skuId: 103, areaId: 'area-b', units: 80, x: 48, y: 456, width: 210, height: 135 },
    { id: 'block-blue-c', skuId: 101, areaId: 'area-c', units: 100, x: 404, y: 304, width: 210, height: 135 },
  ],
}

export function createMemoryWarehouseCanvasRepository(
  initialState: WarehouseCanvasState,
): WarehouseCanvasRepository {
  let storedState = cloneState(initialState)

  return {
    async load(warehouseId) {
      return { ...cloneState(storedState), warehouseId }
    },
    async save(state) {
      storedState = cloneState(state)
      return cloneState(storedState)
    },
  }
}

function cloneState(state: WarehouseCanvasState): WarehouseCanvasState {
  return {
    ...state,
    areas: state.areas.map((area) => ({ ...area })),
    catalog: state.catalog.map((sku) => ({ ...sku })),
    blocks: state.blocks.map((block) => ({ ...block })),
  }
}
