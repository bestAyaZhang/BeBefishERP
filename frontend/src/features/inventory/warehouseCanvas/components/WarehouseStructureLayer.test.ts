import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseStructureLayer from './WarehouseStructureLayer.vue'
import { createWarehouseStructure, doorGeometry, validateStructure, wallSegments } from '../warehouseStructure'
import type { WarehouseStructure } from '../warehouseStructure'
const wrappers: ReturnType<typeof mount>[]=[]
function setup(){
  const structure=createWarehouseStructure()
  const wrapper=mount(WarehouseStructureLayer,{props:{structure,editing:true,gridSnapping:false,issues:[]}})
  wrappers.push(wrapper)
  const svg=wrapper.get('svg.structure-svg')
  Object.defineProperty(svg.element,'getBoundingClientRect',{value:()=>({left:0,top:0,width:1000,height:800})})
  Object.defineProperty(svg.element,'setPointerCapture',{value:vi.fn()})
  return {wrapper,svg,structure}
}
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.unstubAllGlobals()})
describe('wall editing gestures',()=>{
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
