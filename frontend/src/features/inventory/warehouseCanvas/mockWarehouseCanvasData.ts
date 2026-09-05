import type { WarehouseCanvasState } from './types'

export const seedWarehouseCanvas: WarehouseCanvasState = {
  warehouseId: 1,
  warehouseName: 'BeBeFish 主仓',
  areas: [
    { id: 'area-a', name: 'A-01', x: 40, y: 60, width: 360, height: 300, visible: true, locked: false },
    { id: 'area-b', name: 'B-01', x: 40, y: 440, width: 360, height: 300, visible: true, locked: false },
    { id: 'area-c', name: 'C-01', x: 480, y: 60, width: 280, height: 300, visible: true, locked: false },
  ],
  catalog: [
    { id: 'sku-blue', skuId: 101, skuCode: 'BBF-BLUE-101', productName: '蓝色储物箱', unitsPerCase: 24, accent: 'blue' },
    { id: 'sku-pink', skuId: 102, skuCode: 'BBF-PINK-102', productName: '粉色收纳盒', unitsPerCase: 24, accent: 'pink' },
    { id: 'sku-tea', skuId: 103, skuCode: 'BBF-TEA-103', productName: '茶具套装', unitsPerCase: 12, accent: 'green' },
    { id: 'sku-cyan', skuId: 104, skuCode: 'BBF-CYAN-104', productName: '青色水杯', unitsPerCase: 6, accent: 'cyan' },
  ],
  blocks: [
    { id: 'block-blue-a', skuId: 101, areaId: 'area-a', units: 150, x: 76, y: 96, width: 120, height: 80 },
    { id: 'block-cyan-a', skuId: 104, areaId: 'area-a', units: 120, x: 220, y: 96, width: 120, height: 80 },
    { id: 'block-pink-b', skuId: 102, areaId: 'area-b', units: 420, x: 220, y: 476, width: 120, height: 80 },
    { id: 'block-tea-b', skuId: 103, areaId: 'area-b', units: 236, x: 220, y: 580, width: 120, height: 80 },
    { id: 'block-blue-c', skuId: 101, areaId: 'area-c', units: 100, x: 516, y: 96, width: 120, height: 80 },
  ],
}
