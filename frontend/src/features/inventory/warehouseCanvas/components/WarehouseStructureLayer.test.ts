import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseStructureLayer from './WarehouseStructureLayer.vue'
import { createWarehouseStructure, doorGeometry, solidWallSegments, validateStructure, wallSegments } from '../warehouseStructure'
import type { WarehouseStructure } from '../warehouseStructure'
import { warehousePlannerScene } from '../warehousePlannerScene'
const wrappers: ReturnType<typeof mount>[]=[]
function setup(){
  const structure=createWarehouseStructure()
  structure.zones=[]
  const wrapper=mount(WarehouseStructureLayer,{props:{structure,editing:true,gridSnapping:false,issues:[]}})
  wrappers.push(wrapper)
  const svg=wrapper.get('svg.structure-svg')
  Object.defineProperty(svg.element,'getBoundingClientRect',{value:()=>({left:0,top:0,width:1000,height:800})})
  Object.defineProperty(svg.element,'setPointerCapture',{value:vi.fn()})
  return {wrapper,svg,structure}
}
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.unstubAllGlobals()})
describe('wall editing gestures',()=>{
  it('renders each solid wall segment as a light architectural double line',()=>{
    const {wrapper,structure}=setup()
    const visibleSegments=solidWallSegments(structure)
    const edges=wrapper.findAll('.wall-edge')
    const gaps=wrapper.findAll('.wall-gap')

    expect(edges).toHaveLength(visibleSegments.length)
    expect(gaps).toHaveLength(visibleSegments.length)
    expect(gaps[0]!.attributes()).toMatchObject({
      x1:edges[0]!.attributes('x1'),
      y1:edges[0]!.attributes('y1'),
      x2:edges[0]!.attributes('x2'),
      y2:edges[0]!.attributes('y2'),
    })
  })
  it('distinguishes exterior and partition wall strokes',()=>{
    const {wrapper}=setup()

    expect(wrapper.findAll('.wall-edge.exterior-wall').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.wall-edge.partition-wall').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.wall-gap.partition-wall').length).toBeGreaterThan(0)
  })
  it('renders each loading door as paired rails with two short jambs',()=>{
    const {wrapper}=setup()
    const door=wrapper.get('[data-testid="planner-loading-door-door-1"]')

    expect(door.findAll('.loading-door-rail')).toHaveLength(2)
    expect(door.findAll('.loading-door-jamb')).toHaveLength(2)
  })
  it('places, moves and deletes a 0.6 meter column through structure tools',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-column"]').trigger('click')
    await svg.trigger('click',{clientX:500,clientY:200})
    const added=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(added.columns).toHaveLength(10)
    const column=added.columns!.at(-1)!
    expect(column).toMatchObject({left:49.5,top:24.25,width:1,height:1.5})
    await wrapper.setProps({structure:added})
    await wrapper.get(`[data-testid="planner-column-${column.id}"]`).trigger('pointerdown',{button:0,pointerId:70,clientX:500,clientY:200})
    await svg.trigger('pointerup',{pointerId:70,clientX:600,clientY:200})
    const moved=wrapper.emitted('commit')![1]![0] as WarehouseStructure
    expect(moved.columns!.at(-1)!.left).toBe(59.5)
    await wrapper.setProps({structure:moved})
    await wrapper.get('[data-testid="structure-delete"]').trigger('click')
    expect((wrapper.emitted('commit')![2]![0] as WarehouseStructure).columns).toHaveLength(9)
  })
  it('rejects a column on goods and rolls an invalid column drag back to its origin',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.setProps({pallets:warehousePlannerScene.palletGroups})
    await wrapper.get('[data-testid="structure-tool-column"]').trigger('click')
    await svg.trigger('click',{clientX:140,clientY:328})
    expect(wrapper.emitted('commit')).toBeUndefined()
    expect(wrapper.text()).toContain('与柱子重叠')
    expect(wrapper.emitted('conflict')?.at(-1)?.[0]).toContain('pallet-a01')
    await svg.trigger('click',{clientX:500,clientY:200})
    const added=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    const column=added.columns!.at(-1)!
    await wrapper.setProps({structure:added})
    await wrapper.get(`[data-testid="planner-column-${column.id}"]`).trigger('pointerdown',{button:0,pointerId:71,clientX:500,clientY:200})
    await svg.trigger('pointerup',{pointerId:71,clientX:0,clientY:200})
    expect(wrapper.emitted('commit')).toHaveLength(1)
    expect(wrapper.text()).toContain('已回到原位')
    expect(wrapper.get(`[data-testid="planner-column-${column.id}"]`).attributes('transform')).toBe('translate(297,97)')
  })
  it('places, moves and deletes an elevator through structure tools',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-elevator"]').trigger('click')
    await svg.trigger('click',{clientX:500,clientY:200})
    const added=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(added.elevators).toHaveLength(1)
    const lift=added.elevators![0]!
    expect(lift).toMatchObject({left:47.5,top:21.25,width:5,height:7.5})
    await wrapper.setProps({structure:added})
    await wrapper.get(`[data-testid="planner-elevator-${lift.id}"]`).trigger('pointerdown',{button:0,pointerId:7,clientX:500,clientY:200})
    await svg.trigger('pointerup',{pointerId:7,clientX:600,clientY:200})
    const moved=wrapper.emitted('commit')![1]![0] as WarehouseStructure
    expect(moved.elevators![0]!.left).toBe(57.5)
    await wrapper.setProps({structure:moved})
    await wrapper.get('[data-testid="structure-delete"]').trigger('click')
    expect((wrapper.emitted('commit')![2]![0] as WarehouseStructure).elevators).toEqual([])
  })
  it('rejects elevators placed on piles and rolls back an outside drag',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.setProps({pallets:warehousePlannerScene.palletGroups})
    await wrapper.get('[data-testid="structure-tool-elevator"]').trigger('click')
    await svg.trigger('click',{clientX:140,clientY:328})
    expect(wrapper.emitted('commit')).toBeUndefined()
    expect(wrapper.text()).toContain('与电梯重叠')
    await svg.trigger('click',{clientX:500,clientY:200})
    const added=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    await wrapper.setProps({structure:added})
    await wrapper.get(`[data-testid="planner-elevator-${added.elevators![0]!.id}"]`).trigger('pointerdown',{button:0,pointerId:8,clientX:500,clientY:200})
    await svg.trigger('pointerup',{pointerId:8,clientX:0,clientY:200})
    expect(wrapper.emitted('commit')).toHaveLength(1)
    expect(wrapper.text()).toContain('已回到原位')
    expect(wrapper.get('.elevator').attributes('transform')).toBe('translate(285,85)')
  })
  it('closes a manual exterior and retains original doors for reassignment',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-outline"]').trigger('click')
    for(const [clientX,clientY] of [[100,100],[900,100],[900,700],[100,700],[100,100]])await svg.trigger('click',{clientX,clientY})
    const next=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(next.outline.nodes).toHaveLength(4)
    expect(next.doors.every(d=>d.attachment===null)).toBe(true)
    expect(wrapper.emitted('busy')?.at(-1)).toEqual([false])
  })
  it('cancels unfinished walls without changing the layout',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-outline"]').trigger('click')
    await svg.trigger('click',{clientX:100,clientY:100})
    window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))
    expect(wrapper.emitted('commit')).toBeUndefined()
    expect(wrapper.emitted('busy')?.at(-1)).toEqual([false])
  })
  it('does not let existing doors swallow points while redrawing an exterior',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-outline"]').trigger('click')
    await svg.trigger('click',{clientX:100,clientY:100})
    await wrapper.get('[data-testid="planner-loading-door-door-3"]').trigger('click',{clientX:790,clientY:76})
    await svg.trigger('click',{clientX:850,clientY:700})
    await svg.trigger('click',{clientX:100,clientY:700})
    await wrapper.get('[data-testid="structure-finish"]').trigger('click')
    const next=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(next.outline.nodes).toHaveLength(4)
    expect(next.outline.nodes[1]!.x).toBe(79)
  })
  it('adds a partition with snapped wall endpoint and ends with Enter',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-partition"]').trigger('click')
    await svg.trigger('click',{clientX:400,clientY:76})
    await svg.trigger('click',{clientX:400,clientY:200})
    window.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'}))
    const next=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(next.partitions).toHaveLength(3)
    expect(next.partitions[2]!.nodes[0]!.attachment?.wallId).toBe('outline')
    expect(validateStructure(next)).toEqual([])
  })
  it('places ordinary doors only near valid walls',async()=>{
    const {wrapper,svg}=setup()
    await wrapper.get('[data-testid="structure-tool-ordinary"]').trigger('click')
    await svg.trigger('click',{clientX:500,clientY:400})
    expect(wrapper.emitted('commit')).toBeUndefined()
    await svg.trigger('click',{clientX:400,clientY:76})
    const next=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(next.doors).toHaveLength(4)
    expect(next.doors[3]!.kind).toBe('ordinary')
    expect(doorGeometry(next,next.doors[3]!).valid).toBe(true)
  })
  it('commits a node drag once and ignores a node selection click',async()=>{
    const {wrapper,svg,structure}=setup()
    await wrapper.get(`[data-testid="wall-segment-outline-${wallSegments(structure.outline)[0]!.id}"]`).trigger('click')
    const node=wrapper.get('[data-testid="wall-node-outer-0"]')
    await node.trigger('pointerdown',{button:0,pointerId:1,clientX:63,clientY:76})
    await svg.trigger('pointerup',{pointerId:1,clientX:63,clientY:76})
    expect(wrapper.emitted('commit')).toBeUndefined()
    await node.trigger('pointerdown',{button:0,pointerId:2,clientX:63,clientY:76})
    await svg.trigger('pointerup',{pointerId:2,clientX:90,clientY:100})
    expect(wrapper.emitted('commit')).toHaveLength(1)
    const next=wrapper.emitted('commit')![0]![0] as WarehouseStructure
    expect(next.outline.nodes[0]!.x).toBe(9)
    expect(wrapper.emitted('busy')?.at(-1)).toEqual([false])
  })
  it('rejects self intersecting node drops',async()=>{
    const {wrapper,svg,structure}=setup()
    await wrapper.get(`[data-testid="wall-segment-outline-${wallSegments(structure.outline)[0]!.id}"]`).trigger('click')
    await wrapper.get('[data-testid="wall-node-outer-0"]').trigger('pointerdown',{button:0,pointerId:1,clientX:63,clientY:76})
    await svg.trigger('pointerup',{pointerId:1,clientX:995,clientY:400})
    expect(wrapper.emitted('commit')).toBeUndefined()
    expect(wrapper.text()).toContain('已回到原位')
  })
})
