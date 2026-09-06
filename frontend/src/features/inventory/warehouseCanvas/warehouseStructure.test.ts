import { describe, expect, it } from 'vitest'
import { createWarehouseStructure, doorGeometry, insertStructureNode, moveStructureNode, palletStructureConflicts, pointInOutline, removeStructureNode, validateStructure, validWall, wallSegments } from './warehouseStructure'
import type { StructureWall, WarehouseStructure } from './warehouseStructure'
import { warehousePlannerScene } from './warehousePlannerScene'

const wall = (coords: number[][]): StructureWall => ({ id: 'outline', closed: true, nodes: coords.map(([x,y], i) => ({ id: `${i}`, x: x!, y: y! })) })
describe('warehouse structure geometry', () => {
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
