export type LayoutObjectType = 'boundary' | 'fixed-zone' | 'free-zone' | 'aisle' | 'obstacle' | 'fixed-location'

export type LayoutTool = 'select' | 'pan' | 'draw-fixed-zone' | 'draw-free-zone' | 'draw-aisle' | 'draw-obstacle' | 'draw-location'

export type LayoutStatus = 'draft' | 'published'

export type LayoutObject = {
  id: string
  type: LayoutObjectType
  code: string
  name: string
  x: number
  y: number
  width: number
  height: number
  rotation: number
  visible: boolean
  locked: boolean
  parentId?: string
  maxHeight?: number
  maxWeight?: number
  maxVolume?: number
  maxSkuCount?: number
  singleSku?: boolean
}

export type WarehouseLayoutState = {
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  warehouseAddress: string
  version: number
  publishedVersion: number
  status: LayoutStatus
  savedAt: string
  objects: LayoutObject[]
}

export type LayoutIssueRule = 'OUT_OF_BOUNDARY' | 'AISLE_OVERLAP' | 'CLEARANCE_WARNING' | 'INVALID_GEOMETRY'

export type LayoutIssue = {
  id: string
  objectId: string
  rule: LayoutIssueRule
  severity: 'error' | 'warning'
  title: string
  description: string
}

export type LayoutObjectPatch = Partial<Omit<LayoutObject, 'id' | 'type'>>

export interface WarehouseLayoutRepository {
  load(warehouseId: number): Promise<WarehouseLayoutState>
  saveDraft(state: WarehouseLayoutState): Promise<WarehouseLayoutState>
  publish(state: WarehouseLayoutState): Promise<WarehouseLayoutState>
}

export const layoutObjectTypeLabels: Record<LayoutObjectType, string> = {
  boundary: '仓库边界',
  'fixed-zone': '固定区',
  'free-zone': '自由区',
  aisle: '主通道',
  obstacle: '障碍物',
  'fixed-location': '固定库位',
}
