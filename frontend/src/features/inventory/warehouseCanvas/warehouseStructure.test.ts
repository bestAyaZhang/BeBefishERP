import { describe, expect, it } from 'vitest'
import { createWarehouseStructure, doorGeometry, insertStructureNode, moveStructureNode, palletStructureConflicts, pointInOutline, removeStructureNode, validatePlannerLayout, validateStructure, validWall, wallSegments } from './warehouseStructure'
import type { StructureWall, WarehouseStructure } from './warehouseStructure'
import { warehousePlannerScene } from './warehousePlannerScene'

const wall = (coords: number[][]): StructureWall => ({ id: 'outline', closed: true, nodes: coords.map(([x,y], i) => ({ id: `${i}`, x: x!, y: y! })) })
describe('warehouse structure geometry', () => {
  it.each(['aisle', 'forklift', 'fire'] as const)('keeps %s clear of goods while allowing connected passage segments', kind => {
    const a = { id: 'route-a', label: '通道 A', tone: 'blue' as const, kind, left: 40, top: 40, width: 20, height: 5 }
    const b = { ...a, id: 'route-b', left: 55, width: 5, height: 20 }
    const s = { ...createWarehouseStructure(), columns: [], zones: [a, b] }
    expect(validateStructure(s)).toEqual([])
    const pile = { ...warehousePlannerScene.palletGroups[0]!, left: 42, top: 41, width: 2, height: 2 }
    expect(palletStructureConflicts(s, pile)).toContain('route-a')
    expect(validatePlannerLayout(s, [pile]).some(issue => issue.objectIds.includes('route-a') && issue.objectIds.includes(pile.id))).toBe(true)
    const storage = { ...a, id: 'storage', kind: 'area' as const }
    expect(validateStructure({ ...s, zones: [a, storage] }).some(issue => issue.objectIds.includes('storage'))).toBe(true)
  })
  it('initializes editable passage examples without blocking existing goods or completion', () => {
    const s = createWarehouseStructure()
    expect(s.zones?.some(zone => zone.kind === 'forklift')).toBe(true)
    expect(s.zones?.some(zone => zone.kind === 'fire')).toBe(true)
    expect(validatePlannerLayout(s, warehousePlannerScene.palletGroups)).toEqual([])
  })
  it('initializes the existing columns as editable 0.6 meter structures without blocking goods', () => {
    const s = createWarehouseStructure()
    expect(s.columns).toHaveLength(9)
    expect(s.columns).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'column-1', width: 1, height: 1.5 }),
    ]))
    expect(validatePlannerLayout(s, warehousePlannerScene.palletGroups)).toEqual([])
  })
  it('keeps columns clear of walls, elevators, passages, other columns and goods while allowing ordinary areas', () => {
    const column = { id: 'column-test', left: 40, top: 40, width: 1, height: 1.5 }
    const s: WarehouseStructure = { ...createWarehouseStructure(), columns: [column], elevators: [], zones: [] }
    const pile = { ...warehousePlannerScene.palletGroups[0]!, left: 40.2, top: 40.2, width: .4, height: .4 }
    expect(palletStructureConflicts(s, pile)).toContain(column.id)
    expect(validatePlannerLayout(s, [pile]).some(issue => issue.objectIds.includes(column.id) && issue.objectIds.includes(pile.id))).toBe(true)

    s.elevators = [{ id: 'lift-column', left: 40.5, top: 40.5, width: 5, height: 7.5 }]
    expect(validateStructure(s).some(issue => issue.objectIds.includes(column.id) && issue.objectIds.includes('lift-column'))).toBe(true)
    s.elevators = []
    s.columns!.push({ ...column, id: 'column-other', left: 40.5 })
    expect(validateStructure(s).some(issue => issue.objectIds.includes(column.id) && issue.objectIds.includes('column-other'))).toBe(true)

    s.columns = [column]
    s.zones = [{ id: 'passage-column', label: '通道', tone: 'blue', kind: 'forklift', left: 39, top: 39, width: 5, height: 5 }]
    expect(validateStructure(s).some(issue => issue.objectIds.includes(column.id) && issue.objectIds.includes('passage-column'))).toBe(true)
    s.zones = [{ id: 'area-column', label: '普通区域', tone: 'green', kind: 'area', left: 39, top: 39, width: 5, height: 5 }]
    expect(validateStructure(s)).toEqual([])
  })
  it('allows piles inside zones but rejects overlapping zones and elevators', () => {
    const zone = { id: 'zone-test', label: '存货区', tone: 'green' as const, left: 40, top: 40, width: 10, height: 10 }
    const s = { ...createWarehouseStructure(), zones: [zone] }
    const pile = { ...warehousePlannerScene.palletGroups[0]!, left: 41, top: 41, width: 2, height: 2 }
    expect(validatePlannerLayout(s, [pile])).toEqual([])
    s.zones.push({ ...zone, id: 'zone-other', left: 49 })
    expect(validateStructure(s).some(v => v.objectIds.includes('zone-test') && v.objectIds.includes('zone-other'))).toBe(true)
    s.zones[1]!.left = 50
    expect(validateStructure(s)).toEqual([])
    s.elevators = [{ id: 'lift-zone', left: 42, top: 42, width: 5, height: 7.5 }]
    expect(validateStructure(s).some(v => v.objectIds.includes('zone-test') && v.objectIds.includes('lift-zone'))).toBe(true)
  })
  it('rejects zones outside the sloped wall, over partitions, or with invalid geometry', () => {
    const zone = { id: 'zone-test', label: '测试区', tone: 'blue' as const, left: 88, top: 15, width: 8, height: 10 }
    const s = { ...createWarehouseStructure(), zones: [zone] }
    expect(validateStructure(s).some(v => v.objectIds.includes(zone.id) && v.objectIds.includes('outline'))).toBe(true)
    Object.assign(zone, { left: 15, top: 78, width: 10, height: 5 })
    expect(validateStructure(s).some(v => v.objectIds.includes(zone.id) && v.objectIds.includes('room-top'))).toBe(true)
    for (const width of [0, -1, NaN, Infinity]) {
      Object.assign(zone, { left: 40, top: 40, width })
      expect(validateStructure(s).some(v => v.id === 'zone-invalid:zone-test')).toBe(true)
    }
  })
  it('validates elevator footprint, overlapping elevators and pile collisions', () => {
    const s=createWarehouseStructure()
    s.zones=[] // This fixture isolates elevator/pile geometry from area allocation.
    s.elevators=[{id:'lift-1',left:40,top:15,width:5,height:7.5}]
    expect(validateStructure(s)).toEqual([])
    expect(palletStructureConflicts(s,{left:41,top:16,width:2,height:2})).toContain('lift-1')
    const pile={...warehousePlannerScene.palletGroups[0]!,left:41,top:16}
    expect(validatePlannerLayout(s,[pile]).find(v=>v.message.includes('电梯'))?.objectIds).toEqual([pile.id,'lift-1'])
    s.elevators.push({...s.elevators[0]!,id:'lift-2'})
    expect(validateStructure(s).some(v=>v.message==='两部电梯重叠')).toBe(true)
    s.elevators=[{id:'lift-1',left:1,top:15,width:5,height:7.5}]
    expect(validateStructure(s).some(v=>v.message==='电梯超出仓库边界')).toBe(true)
  })
  it('initializes a valid scene with every existing pile inside', () => {
    const s = createWarehouseStructure()
    expect(validateStructure(s)).toEqual([])
    expect(warehousePlannerScene.palletGroups.filter((p) => palletStructureConflicts(s, p).length)).toEqual([])
  })
  it('rejects crossing, folded and degenerate outer walls', () => {
    expect(validWall(wall([[10,10],[90,90],[10,90],[90,10]]))).toBe(false)
    expect(validWall(wall([[10,10],[50,10],[30,10],[50,50],[10,50]]))).toBe(false)
    expect(validWall(wall([[10,10],[20,10],[30,10]]))).toBe(false)
  })
  it('detects a concave notch even when all four pile corners are inside', () => {
    const outline = wall([[5,5],[45,5],[45,50],[55,50],[55,5],[95,5],[95,95],[5,95]])
    const s: WarehouseStructure = { outline, partitions: [], doors: [] }
    for (const p of [{x:35,y:35},{x:65,y:35},{x:65,y:65},{x:35,y:65}]) expect(pointInOutline(p, outline.nodes)).toBe(true)
    expect(palletStructureConflicts(s, { left:35,top:35,width:30,height:30 })).toContain('outline')
  })
  it('moves doors and partition attachments with their wall', () => {
    const s = createWarehouseStructure(), before = doorGeometry(s,s.doors[0]!).center
    const next = moveStructureNode(s,'outline','outer-0',{x:6.3,y:14})
    expect(doorGeometry(next,next.doors[0]!).center.y).toBeGreaterThan(before.y)
    const endpoint = next.partitions[0]!.nodes[0]!
    expect(endpoint.y).toBeGreaterThan(s.partitions[0]!.nodes[0]!.y)
    expect(s.outline.nodes[0]!.y).toBe(9.5)
  })
  it('marks doors invalid when their wall is too short', () => {
    const s = createWarehouseStructure()
    const next = moveStructureNode(s,'outline','outer-1',{x:8,y:9.5})
    expect(validateStructure(next).some((v) => v.id.startsWith('door:'))).toBe(true)
  })
  it('preserves door centers when a straight segment is inserted or merged', () => {
    const s = createWarehouseStructure(), center = doorGeometry(s,s.doors[0]!).center
    const inserted = insertStructureNode(s, 'outline', wallSegments(s.outline)[0]!.id, {x:40,y:9.5}, 'inserted')
    expect(doorGeometry(inserted, inserted.doors[0]!).center).toEqual(center)
    const removed = removeStructureNode(inserted,'outline','inserted')
    expect(doorGeometry(removed, removed.doors[0]!).center.x).toBeCloseTo(center.x)
    expect(doorGeometry(removed, removed.doors[0]!).center.y).toBeCloseTo(center.y)
  })
  it('detects a partition contained entirely in a pile and allows contact with wall surface', () => {
    const s: WarehouseStructure = { outline: wall([[5,5],[95,5],[95,95],[5,95]]), partitions: [], doors: [] }
    expect(palletStructureConflicts(s,{left:5.2,top:20,width:10,height:10})).toEqual([])
    s.partitions.push({id:'partition',closed:false,nodes:[{id:'a',x:30,y:30},{id:'b',x:40,y:30}]})
    expect(palletStructureConflicts(s,{left:25,top:25,width:20,height:10})).toContain('partition')
  })
  it('rejects a small collinear overlap between walls even when their midpoints are separate', () => {
    const s:WarehouseStructure={outline:wall([[5,5],[95,5],[95,95],[5,95]]),doors:[],partitions:[
      {id:'a',closed:false,nodes:[{id:'a0',x:20,y:20},{id:'a1',x:40,y:20}]},
      {id:'b',closed:false,nodes:[{id:'b0',x:39,y:20},{id:'b1',x:60,y:20}]},
    ]}
    expect(validateStructure(s).some(v=>v.id.startsWith('cross:'))).toBe(true)
    s.partitions[1]!.nodes[0]!.x=40
    expect(validateStructure(s)).toEqual([])
  })
  it('rejects partitions crossing a concave exterior gap', () => {
    const s:WarehouseStructure={outline:wall([[5,5],[45,5],[45,50],[55,50],[55,5],[95,5],[95,95],[5,95]]),doors:[],partitions:[
      {id:'cut',closed:false,nodes:[{id:'c0',x:35,y:35},{id:'c1',x:65,y:35}]},
    ]}
    expect(validateStructure(s).some(v=>v.id==='outside:cut')).toBe(true)
  })
  it('rejects overlapping doors and validates a detached door', () => {
    const s=createWarehouseStructure()
    s.doors.push({...s.doors[0]!,id:'duplicate'})
    expect(validateStructure(s).some(v=>v.id.startsWith('doors:'))).toBe(true)
    s.doors[3]!.attachment=null
    expect(validateStructure(s).some(v=>v.id==='door:duplicate')).toBe(true)
  })
})
