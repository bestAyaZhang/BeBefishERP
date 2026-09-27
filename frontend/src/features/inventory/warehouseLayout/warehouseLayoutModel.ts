import type { LayoutIssue, LayoutObject, LayoutObjectPatch, WarehouseLayoutState } from './types'

export function updateLayoutObject(
  state: WarehouseLayoutState,
  objectId: string,
  patch: LayoutObjectPatch,
): WarehouseLayoutState {
  if (!state.objects.some((object) => object.id === objectId)) throw new Error(`未找到布局对象：${objectId}`)
  const next = state.objects.map((object) => object.id === objectId ? normalizeObject({ ...object, ...patch }) : { ...object })
  return { ...state, status: 'draft', objects: next }
}

export function addLayoutObject(state: WarehouseLayoutState, object: LayoutObject): WarehouseLayoutState {
  if (state.objects.some((candidate) => candidate.id === object.id)) throw new Error(`布局对象 ID 已存在：${object.id}`)
  return { ...state, status: 'draft', objects: [...state.objects.map((item) => ({ ...item })), normalizeObject(object)] }
}

export function validateWarehouseLayout(state: WarehouseLayoutState): LayoutIssue[] {
  const issues: LayoutIssue[] = []
  const boundary = state.objects.find((object) => object.type === 'boundary')
  if (!boundary) {
    return [{
      id: 'missing-boundary',
      objectId: '',
      rule: 'INVALID_GEOMETRY',
      severity: 'error',
      title: '缺少仓库边界',
      description: '请先绘制仓库边界，再发布布局。',
    }]
  }

  const aisles = state.objects.filter((object) => object.type === 'aisle' && object.visible)
  state.objects.filter((object) => object.type !== 'boundary' && object.visible).forEach((object) => {
    if (!hasValidGeometry(object)) {
      issues.push(issue(object, 'INVALID_GEOMETRY', 'error', '几何参数无效', '位置与尺寸必须是有效数字，宽度和深度必须大于 0。'))
      return
    }
    if (!contains(boundary, object)) {
      issues.push(issue(object, 'OUT_OF_BOUNDARY', 'error', '对象超出仓库边界', `${object.code} 有部分位于仓库边界之外。`))
    }
    if (object.type === 'fixed-location' && aisles.some((aisle) => intersects(aisle, object))) {
      issues.push(issue(object, 'AISLE_OVERLAP', 'error', '库位侵占主通道', `${object.code} 与主通道重叠，请调整位置或尺寸。`))
    }
    if (object.type === 'fixed-location' && distanceToBoundary(boundary, object) < 2) {
      issues.push(issue(object, 'CLEARANCE_WARNING', 'warning', '边界净距不足', `${object.code} 与仓库边界的净距小于 2m。`))
    }
  })
  return issues
}

export function cloneWarehouseLayout(state: WarehouseLayoutState): WarehouseLayoutState {
  return { ...state, objects: state.objects.map((object) => ({ ...object })) }
}

function issue(
  object: LayoutObject,
  rule: LayoutIssue['rule'],
  severity: LayoutIssue['severity'],
  title: string,
  description: string,
): LayoutIssue {
  return { id: `${rule}-${object.id}`, objectId: object.id, rule, severity, title, description }
}

function normalizeObject(object: LayoutObject): LayoutObject {
  const numericKeys = ['x', 'y', 'width', 'height', 'rotation'] as const
  numericKeys.forEach((key) => {
    if (!Number.isFinite(object[key])) throw new Error('布局对象几何参数必须是有效数字')
  })
  if (object.width <= 0 || object.height <= 0) throw new Error('布局对象宽度和深度必须大于 0')
  return {
    ...object,
    code: object.code.trim(),
    name: object.name.trim(),
    x: round(object.x),
    y: round(object.y),
    width: round(object.width),
    height: round(object.height),
    rotation: round(object.rotation),
  }
}

function hasValidGeometry(object: LayoutObject): boolean {
  return [object.x, object.y, object.width, object.height, object.rotation].every(Number.isFinite)
    && object.width > 0
    && object.height > 0
}

function contains(container: LayoutObject, item: LayoutObject): boolean {
  return item.x >= container.x
    && item.y >= container.y
    && item.x + item.width <= container.x + container.width
    && item.y + item.height <= container.y + container.height
}

function intersects(left: LayoutObject, right: LayoutObject): boolean {
  return left.x < right.x + right.width
    && left.x + left.width > right.x
    && left.y < right.y + right.height
    && left.y + left.height > right.y
}

function distanceToBoundary(boundary: LayoutObject, object: LayoutObject): number {
  return Math.min(
    object.x - boundary.x,
    object.y - boundary.y,
    boundary.x + boundary.width - (object.x + object.width),
    boundary.y + boundary.height - (object.y + object.height),
  )
}

function round(value: number): number {
  return Math.round(value * 10) / 10
}
