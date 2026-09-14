import { warehousePlannerScene } from './warehousePlannerScene'
import type { PlannerRect, PlannerPalletGroup, PlannerZone } from './warehousePlannerScene'
import { isPassage } from './warehousePassages'

export type StructurePoint = { x: number; y: number }
export type WallAttachment = { wallId: string; segmentId: string; t: number }
export type StructureNode = StructurePoint & { id: string; attachment?: WallAttachment }
export type StructureWall = { id: string; closed: boolean; nodes: StructureNode[] }
export type StructureDoor = { id: string; kind: 'loading' | 'ordinary'; width: number; attachment: WallAttachment | null; position: StructurePoint }
export type StructureElevator = PlannerRect & { id: string }
export type StructureColumn = PlannerRect & { id: string }
export type WarehouseStructure = { outline: StructureWall; partitions: StructureWall[]; doors: StructureDoor[]; elevators?: StructureElevator[]; columns?: StructureColumn[]; zones?: PlannerZone[] }
export function rectanglesOverlap(a: PlannerRect, b: PlannerRect): boolean {
  return a.left < b.left+b.width-EPS && a.left+a.width > b.left+EPS && a.top < b.top+b.height-EPS && a.top+a.height > b.top+EPS
}
export type StructureSegment = { id: string; wallId: string; a: StructureNode; b: StructureNode }
export type StructureIssue = { id: string; objectIds: string[]; message: string }
const EPS = 1e-7
export const WALL_HALF_WIDTH = .12
export const metric = (p: StructurePoint) => ({ x: p.x * .6, y: p.y * .4 })
export const distance = (a: StructurePoint, b: StructurePoint) => Math.hypot((b.x - a.x) * .6, (b.y - a.y) * .4)
export const interpolate = (a: StructurePoint, b: StructurePoint, t: number): StructurePoint => ({ x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t })
export const cloneStructure = (s: WarehouseStructure): WarehouseStructure => JSON.parse(JSON.stringify(s))
export function wallSegments(wall: StructureWall): StructureSegment[] {
  return wall.nodes.slice(0, wall.closed ? undefined : -1).map((a, i) => {
    const b = wall.nodes[(i + 1) % wall.nodes.length]!
    return { id: `${a.id}:${b.id}`, wallId: wall.id, a, b }
  })
}
export function structureSegments(s: WarehouseStructure): StructureSegment[] {
  return [s.outline, ...s.partitions].flatMap(wallSegments)
}
export function resolveAttachment(s: WarehouseStructure, attachment: WallAttachment | null) {
  return attachment && structureSegments(s).find((seg) => seg.wallId === attachment.wallId && seg.id === attachment.segmentId)
}
export function project(p: StructurePoint, a: StructurePoint, b: StructurePoint) {
  const m = metric(p), ma = metric(a), mb = metric(b)
  const dx = mb.x - ma.x, dy = mb.y - ma.y
  const t = Math.max(0, Math.min(1, ((m.x - ma.x) * dx + (m.y - ma.y) * dy) / (dx * dx + dy * dy || 1)))
  const point = interpolate(a, b, t)
  return { point, t, distance: distance(p, point) }
}
function cross(a: StructurePoint, b: StructurePoint, c: StructurePoint) {
  return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
}
function onSegment(p: StructurePoint, a: StructurePoint, b: StructurePoint) {
  return Math.abs(cross(a, b, p)) < EPS && p.x >= Math.min(a.x, b.x) - EPS && p.x <= Math.max(a.x, b.x) + EPS && p.y >= Math.min(a.y, b.y) - EPS && p.y <= Math.max(a.y, b.y) + EPS
}
export function segmentsIntersect(a: StructurePoint, b: StructurePoint, c: StructurePoint, d: StructurePoint): boolean {
  return (cross(a, b, c) * cross(a, b, d) < -EPS && cross(c, d, a) * cross(c, d, b) < -EPS)
    || onSegment(a, c, d) || onSegment(b, c, d) || onSegment(c, a, b) || onSegment(d, a, b)
}
export function pointInOutline(p: StructurePoint, nodes: StructurePoint[]): boolean {
  let inside = false
  for (let i = 0, j = nodes.length - 1; i < nodes.length; j = i++) {
    const a = nodes[j]!, b = nodes[i]!
    if (onSegment(p, a, b)) return true
    if ((a.y > p.y) !== (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
  }
  return inside
}
export function validWall(wall: StructureWall): boolean {
  if (wall.nodes.length < (wall.closed ? 3 : 2)) return false
  if (wall.nodes.some((p) => !Number.isFinite(p.x) || !Number.isFinite(p.y) || p.x < 0 || p.x > 100 || p.y < 0 || p.y > 100)) return false
  const segs = wallSegments(wall)
  if (segs.some((s) => distance(s.a, s.b) < .1)) return false
  if (wall.closed && Math.abs(segs.reduce((sum, s) => sum + s.a.x * s.b.y - s.b.x * s.a.y, 0)) < EPS) return false
  for (let i = 0; i < segs.length; i++) for (let j = i + 1; j < segs.length; j++) {
    const a = segs[i]!, b = segs[j]!
    const adjacent = j === i + 1 || (wall.closed && i === 0 && j === segs.length - 1)
    if (adjacent) {
      const shared = a.b.id === b.a.id ? a.b : a.a
      const aa = a.a.id === shared.id ? a.b : a.a, bb = b.a.id === shared.id ? b.b : b.a
      if (onSegment(aa, shared, bb) || onSegment(bb, shared, aa)) return false
    } else if (segmentsIntersect(a.a, a.b, b.a, b.b)) return false
  }
  return true
}
export function doorGeometry(s: WarehouseStructure, door: StructureDoor) {
  const seg = resolveAttachment(s, door.attachment)
  if (!seg || !door.attachment) return { center: door.position, a: door.position, b: door.position, valid: false }
  const length = distance(seg.a, seg.b)
  const half = door.width / (2 * length)
  return {
    center: interpolate(seg.a, seg.b, door.attachment.t),
    a: interpolate(seg.a, seg.b, door.attachment.t - half),
    b: interpolate(seg.a, seg.b, door.attachment.t + half),
    valid: door.attachment.t >= half - EPS && door.attachment.t <= 1 - half + EPS && (door.kind !== 'loading' || seg.wallId === s.outline.id),
  }
}
export function solidWallSegments(s: WarehouseStructure): StructureSegment[] {
  return structureSegments(s).flatMap((seg) => {
    const length = distance(seg.a, seg.b)
    const doors = s.doors.filter((d) => d.attachment?.wallId === seg.wallId && d.attachment?.segmentId === seg.id && doorGeometry(s, d).valid)
      .map((d) => ({ start: d.attachment!.t - d.width / (2 * length), end: d.attachment!.t + d.width / (2 * length) })).sort((a, b) => a.start - b.start)
    let t = 0
    const pieces: StructureSegment[] = []
    for (const d of doors) {
      if (d.start > t) pieces.push({ ...seg, a: { ...seg.a, ...interpolate(seg.a, seg.b, t) }, b: { ...seg.b, ...interpolate(seg.a, seg.b, d.start) } })
      t = Math.max(t, d.end)
    }
    if (t < 1) pieces.push({ ...seg, a: { ...seg.a, ...interpolate(seg.a, seg.b, t) } })
    return pieces
  })
}
function segmentDistance(a: StructurePoint, b: StructurePoint, c: StructurePoint, d: StructurePoint) {
  return segmentsIntersect(a, b, c, d) ? 0 : Math.min(project(a, c, d).distance, project(b, c, d).distance, project(c, a, b).distance, project(d, a, b).distance)
}
function rectangleWallConflicts(s: WarehouseStructure, r: PlannerRect): string[] {
  const points = [{ x: r.left, y: r.top }, { x: r.left + r.width, y: r.top }, { x: r.left + r.width, y: r.top + r.height }, { x: r.left, y: r.top + r.height }]
  const edges = points.map((a, i) => [a, points[(i + 1) % 4]!] as const)
  const ids = new Set<string>()
  if (points.some((p) => !pointInOutline(p, s.outline.nodes))) ids.add(s.outline.id)
  // Checking every boundary segment also detects concave cutouts between interior corners.
  for (const seg of wallSegments(s.outline)) {
    const inRect = (p: StructurePoint) => p.x > r.left + EPS && p.x < r.left + r.width - EPS && p.y > r.top + EPS && p.y < r.top + r.height - EPS
    if (inRect(seg.a) || inRect(seg.b) || edges.some(([a, b]) => cross(a, b, seg.a) * cross(a, b, seg.b) < -EPS && cross(seg.a, seg.b, a) * cross(seg.a, seg.b, b) < -EPS)) ids.add(s.outline.id)
  }
  for (const seg of solidWallSegments(s)) {
    const inRect = (p: StructurePoint) => p.x > r.left && p.x < r.left + r.width && p.y > r.top && p.y < r.top + r.height
    if (inRect(seg.a) || inRect(seg.b) || edges.some(([a, b]) => segmentDistance(a, b, seg.a, seg.b) < WALL_HALF_WIDTH - EPS)) ids.add(seg.wallId)
  }
  return [...ids]
}
function rectangleStructureConflicts(s: WarehouseStructure, r: PlannerRect): string[] {
  return [...rectangleWallConflicts(s,r), ...(s.elevators ?? []).filter((lift) => rectanglesOverlap(lift,r)).map((lift) => lift.id)]
}
export function palletStructureConflicts(s: WarehouseStructure, r: PlannerRect): string[] {
  return [...rectangleStructureConflicts(s, r), ...(s.columns ?? []).filter(column => rectanglesOverlap(column, r)).map(column => column.id), ...(s.zones ?? []).filter(zone => isPassage(zone) && rectanglesOverlap(zone, r)).map(zone => zone.id)]
}
export function validateStructure(s: WarehouseStructure): StructureIssue[] {
  const issues: StructureIssue[] = []
  const add = (id: string, objectIds: string[], message: string) => issues.push({ id, objectIds, message })
  if (!s.outline.closed || !validWall(s.outline)) add('outline-invalid', [s.outline.id], '外围墙需闭合，且不能交叉或重叠')
  for (const wall of s.partitions) {
    if (!validWall(wall)) add(`wall:${wall.id}`, [wall.id], '隔墙不能交叉、重叠或退化')
    if (wall.nodes.some((p) => !pointInOutline(p, s.outline.nodes)) || wallSegments(wall).some((seg) => {
      const cuts = [0, 1]
      for (const edge of wallSegments(s.outline)) {
        const den = cross({ x: 0, y: 0 }, { x: seg.b.x - seg.a.x, y: seg.b.y - seg.a.y }, { x: edge.b.x - edge.a.x, y: edge.b.y - edge.a.y })
        if (Math.abs(den) > EPS && segmentsIntersect(seg.a, seg.b, edge.a, edge.b)) cuts.push(cross({ x: 0, y: 0 }, { x: edge.a.x - seg.a.x, y: edge.a.y - seg.a.y }, { x: edge.b.x - edge.a.x, y: edge.b.y - edge.a.y }) / den)
      }
      cuts.sort((a, b) => a - b)
      return cuts.slice(1).some((t, i) => !pointInOutline(interpolate(seg.a, seg.b, (t + cuts[i]!) / 2), s.outline.nodes))
    })) add(`outside:${wall.id}`, [wall.id], '隔墙超出仓库边界')
    for (const node of wall.nodes) if (node.attachment && !resolveAttachment(s, node.attachment)) add(`connection:${node.id}`, [wall.id], '隔墙连接已失效，请重新连接端点')
  }
  const segs = structureSegments(s)
  for (let i = 0; i < segs.length; i++) for (let j = i + 1; j < segs.length; j++) {
    const a = segs[i]!, b = segs[j]!
    if (a.wallId === b.wallId || !segmentsIntersect(a.a, a.b, b.a, b.b)) continue
    const collinear = Math.abs(cross(a.a, a.b, b.a)) < EPS && Math.abs(cross(a.a, a.b, b.b)) < EPS
    const axis = Math.abs(a.b.x-a.a.x)>Math.abs(a.b.y-a.a.y) ? 'x' : 'y'
    const overlaps = collinear && Math.min(Math.max(a.a[axis],a.b[axis]),Math.max(b.a[axis],b.b[axis])) - Math.max(Math.min(a.a[axis],a.b[axis]),Math.min(b.a[axis],b.b[axis])) > EPS
    const endpoint = onSegment(a.a, b.a, b.b) || onSegment(a.b, b.a, b.b) || onSegment(b.a, a.a, a.b) || onSegment(b.b, a.a, a.b)
    if (overlaps || !endpoint) add(`cross:${a.id}:${b.id}`, [a.wallId, b.wallId], '墙段交叉或重叠，请使用端点连接')
  }
  for (const door of s.doors) {
    if (!doorGeometry(s, door).valid) add(`door:${door.id}`, [door.id], '门未贴合有效墙段，或墙段长度不足')
    for (const other of s.doors) {
      if (other.id <= door.id || !door.attachment || !other.attachment || door.attachment.wallId !== other.attachment.wallId || door.attachment.segmentId !== other.attachment.segmentId) continue
      if (distance(doorGeometry(s, door).center, doorGeometry(s, other).center) < (door.width + other.width) / 2 - EPS) add(`doors:${door.id}:${other.id}`, [door.id, other.id], '两个门的开口重叠')
    }
  }
  for (const lift of s.elevators ?? []) {
    if (![lift.left,lift.top,lift.width,lift.height].every(Number.isFinite) || lift.width <= 0 || lift.height <= 0) {
      add(`elevator-invalid:${lift.id}`, [lift.id], '电梯占地尺寸无效')
      continue
    }
    const conflicts=[...rectangleWallConflicts(s,lift), ...(s.columns ?? []).filter(column => rectanglesOverlap(column, lift)).map(column => column.id)]
    if(conflicts.length) add(`elevator-position:${lift.id}`,[lift.id,...conflicts],conflicts.includes(s.outline.id)?'电梯超出仓库边界':(s.columns ?? []).some(column => conflicts.includes(column.id))?'电梯与柱子重叠':'电梯与隔墙重叠')
    for(const other of s.elevators ?? []) if(other.id>lift.id && rectanglesOverlap(lift,other)) add(`elevators:${lift.id}:${other.id}`,[lift.id,other.id],'两部电梯重叠')
  }
  for (const column of s.columns ?? []) {
    if (![column.left,column.top,column.width,column.height].every(Number.isFinite) || column.width <= 0 || column.height <= 0) {
      add(`column-invalid:${column.id}`, [column.id], '柱子占地尺寸无效')
      continue
    }
    const conflicts=rectangleWallConflicts(s,column)
    if(conflicts.length) add(`column-position:${column.id}`,[column.id,...conflicts],conflicts.includes(s.outline.id)?'柱子超出仓库边界':'柱子与隔墙重叠')
    for(const other of s.columns ?? []) if(other.id>column.id && rectanglesOverlap(column,other)) add(`columns:${column.id}:${other.id}`,[column.id,other.id],'两根柱子重叠')
  }
  for (const zone of s.zones ?? []) {
    if (![zone.left, zone.top, zone.width, zone.height].every(Number.isFinite) || zone.width <= 0 || zone.height <= 0) {
      add(`zone-invalid:${zone.id}`, [zone.id], '区域尺寸无效')
      continue
    }
    const conflicts = [...rectangleStructureConflicts(s, zone), ...(isPassage(zone) ? (s.columns ?? []).filter(column => rectanglesOverlap(column, zone)).map(column => column.id) : [])]
    if (conflicts.length) add(`zone-position:${zone.id}`, [zone.id, ...conflicts], `${zone.label}${conflicts.includes(s.outline.id) ? '超出仓库边界' : (s.elevators ?? []).some(e => conflicts.includes(e.id)) ? '与电梯重叠' : (s.columns ?? []).some(column => conflicts.includes(column.id)) ? '与柱子重叠' : '与隔墙重叠'}`)
    for (const other of s.zones ?? []) {
      if (other.id > zone.id && !(isPassage(zone) && isPassage(other)) && rectanglesOverlap(zone, other)) add(`zones:${zone.id}:${other.id}`, [zone.id, other.id], `${zone.label}与${other.label}重叠`)
    }
  }
  return issues
}
export function updateAttachments(s: WarehouseStructure): WarehouseStructure {
  // Partitions are stored in creation order, and may only attach to earlier walls.
  for (const wall of s.partitions) for (const node of wall.nodes) {
    const seg = resolveAttachment(s, node.attachment ?? null)
    if (seg && node.attachment) Object.assign(node, interpolate(seg.a, seg.b, node.attachment.t))
  }
  for (const door of s.doors) if (resolveAttachment(s, door.attachment)) door.position = doorGeometry(s, door).center
  return s
}
export function validatePlannerLayout(s: WarehouseStructure, pallets: readonly PlannerPalletGroup[]): StructureIssue[] {
  const result = validateStructure(s)
  for (const pallet of pallets) {
    const conflicts = palletStructureConflicts(s, pallet)
    const passage = (s.zones ?? []).find(zone => isPassage(zone) && conflicts.includes(zone.id))
    if (conflicts.length) result.push({id:`pallet-structure:${pallet.id}`,objectIds:[pallet.id,...conflicts],message:`${pallet.name}${conflicts.includes(s.outline.id)?'超出仓库边界':(s.elevators??[]).some(lift=>conflicts.includes(lift.id))?'与电梯重叠':(s.columns??[]).some(column=>conflicts.includes(column.id))?'与柱子重叠':passage ? passage.kind === 'fire' ? '占用消防留空区' : '占用通道' : '与隔墙重叠'}`})
  }
  for(let i=0;i<pallets.length;i++) for(let j=i+1;j<pallets.length;j++) {
    const a=pallets[i]!,b=pallets[j]!
    if(a.left<b.left+b.width&&a.left+a.width>b.left&&a.top<b.top+b.height&&a.top+a.height>b.top) result.push({id:`pile-overlap:${a.id}:${b.id}`,objectIds:[a.id,b.id],message:`${a.name}与${b.name}重叠`})
  }
  return result
}
export function moveStructureNode(s: WarehouseStructure, wallId: string, nodeId: string, p: StructurePoint, attachment?: WallAttachment) {
  const next = cloneStructure(s)
  const node = [next.outline, ...next.partitions].find((w) => w.id === wallId)?.nodes.find((n) => n.id === nodeId)
  if (node) { Object.assign(node, p); node.attachment = attachment }
  return updateAttachments(next)
}
function remapSegments(before: WarehouseStructure, next: WarehouseStructure, wallId: string) {
  const wall = [next.outline, ...next.partitions].find((w) => w.id === wallId)!
  const newSegments = wallSegments(wall)
  const remap = (attachment: WallAttachment | null, p: StructurePoint): WallAttachment | null => {
    if (!attachment || attachment.wallId !== wallId || resolveAttachment(next, attachment)) return attachment
    const closest = newSegments.map((seg) => ({ seg, hit: project(p, seg.a, seg.b) })).sort((a, b) => a.hit.distance - b.hit.distance)[0]
    return closest ? { wallId, segmentId: closest.seg.id, t: closest.hit.t } : null
  }
  for (const d of next.doors) d.attachment = remap(d.attachment, doorGeometry(before, before.doors.find((v) => v.id === d.id)!).center)
  for (const w of next.partitions) for (const n of w.nodes) if (n.attachment) n.attachment = remap(n.attachment, n) ?? undefined
  return updateAttachments(next)
}
export function insertStructureNode(s: WarehouseStructure, wallId: string, segmentId: string, p: StructurePoint, id: string) {
  const next = cloneStructure(s), wall = [next.outline, ...next.partitions].find((w) => w.id === wallId)!
  const index = wallSegments(wall).findIndex((seg) => seg.id === segmentId)
  if (index < 0) return next
  wall.nodes.splice(index + 1, 0, { id, ...p })
  return remapSegments(s, next, wallId)
}
export function removeStructureNode(s: WarehouseStructure, wallId: string, nodeId: string) {
  const next = cloneStructure(s), wall = [next.outline, ...next.partitions].find((w) => w.id === wallId)!
  if (wall.nodes.length <= (wall.closed ? 3 : 2)) return next
  wall.nodes = wall.nodes.filter((n) => n.id !== nodeId)
  return remapSegments(s, next, wallId)
}
export function createWarehouseStructure(): WarehouseStructure {
  const outline: StructureWall = { id: 'outline', closed: true, nodes: [[6.3,9.5],[88,9.5],[96.7,41.5],[96.7,60.5],[90.5,90.5],[6.3,90.5]].map(([x,y], i) => ({ id: `outer-${i}`, x: x!, y: y! })) }
  const partition = (id: string, points: number[][]): StructureWall => ({ id, closed: false, nodes: points.map(([x,y], i) => ({ id: `${id}-${i}`, x: x!, y: y! })) })
  const s: WarehouseStructure = { outline, partitions: [partition('room-top', [[6.3,79.9],[36.8,79.9],[36.8,90.5]]), partition('room-divider', [[19.8,79.9],[19.8,90.5]])], doors: [], elevators: [], columns: warehousePlannerScene.columns.map(column => ({ id: column.id, left: column.left - .5, top: column.top - .75, width: 1, height: 1.5 })), zones: [...warehousePlannerScene.zones, ...warehousePlannerScene.passages].map(zone => ({ ...zone })) }
  for (const wall of s.partitions) for (const node of wall.nodes) {
    const seg = structureSegments(s).find((seg) => seg.wallId !== wall.id && [outline, ...s.partitions].findIndex((w) => w.id === seg.wallId) < [outline, ...s.partitions].findIndex((w) => w.id === wall.id) && project(node, seg.a, seg.b).distance < EPS)
    if (seg) node.attachment = { wallId: seg.wallId, segmentId: seg.id, t: project(node, seg.a, seg.b).t }
  }
  s.doors = [23,51,79].map((x, i) => ({ id: `door-${i+1}`, kind: 'loading', width: 4, position: { x, y: 9.5 }, attachment: { wallId: outline.id, segmentId: wallSegments(outline)[0]!.id, t: (x-6.3)/(88-6.3) } }))
  return s
}
