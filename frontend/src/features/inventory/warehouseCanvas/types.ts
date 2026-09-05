export type CanvasTool = 'select' | 'draw' | 'pan'

export type CanvasRect = {
  x: number
  y: number
  width: number
  height: number
}

export type WarehouseArea = CanvasRect & {
  id: string
  name: string
  visible: boolean
  locked: boolean
}

export type WarehouseSku = {
  id: string
  skuId: number
  skuCode: string
  productName: string
  unitsPerCase: number
  accent: 'blue' | 'pink' | 'green' | 'cyan'
}

export type WarehouseSkuBlock = CanvasRect & {
  id: string
  skuId: number
  areaId: string
  units: number
}

export type WarehouseCanvasState = {
  warehouseId: number
  warehouseName: string
  areas: WarehouseArea[]
  catalog: WarehouseSku[]
  blocks: WarehouseSkuBlock[]
}

export type LayoutIssue = {
  id: string
  type: 'outside-area' | 'overlap'
  blockIds: string[]
  message: string
}
