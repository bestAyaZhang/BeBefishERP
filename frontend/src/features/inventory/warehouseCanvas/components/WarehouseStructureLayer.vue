<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MousePointer2, Hexagon, Minus, DoorOpen, Warehouse, Plus, Trash2, X, Check, ArrowUpDown, Square } from 'lucide-vue-next'
import { cloneStructure, distance, doorGeometry, insertStructureNode, interpolate, moveStructureNode, project, removeStructureNode, resolveAttachment, solidWallSegments, structureSegments, updateAttachments, validateStructure, validWall, wallSegments } from '../warehouseStructure'
import { validatePlannerLayout } from '../warehouseStructure'
import type { StructureColumn, StructureDoor, StructureElevator, StructureIssue, StructureNode, StructurePoint, StructureSegment, StructureWall, WarehouseStructure, WallAttachment } from '../warehouseStructure'
import type { PlannerPalletGroup } from '../warehousePlannerScene'

const props = defineProps<{ structure: WarehouseStructure; editing: boolean; gridSnapping: boolean; issues: StructureIssue[]; focusedId?: string | null; pallets?: readonly PlannerPalletGroup[]; toolbarTarget?: HTMLElement | null }>()
const emit = defineEmits<{ commit: [value: WarehouseStructure]; preview: [value: WarehouseStructure | null]; busy: [value: boolean]; conflict: [ids: string[]] }>()
type Tool = 'select' | 'outline' | 'partition' | 'loading' | 'ordinary' | 'insert' | 'elevator' | 'column'
const tool = ref<Tool>('select')
const svg = ref<SVGSVGElement>()
const draft = ref<StructureNode[]>([])
const hover = ref<StructurePoint | null>(null)
const preview = ref<WarehouseStructure | null>(null)
const current = computed(() => preview.value ?? props.structure)
const selectedWall = ref<string | null>(null)
const selectedNode = ref<string | null>(null)
const selectedDoor = ref<string | null>(null)
const selectedElevator = ref<string | null>(null)
const selectedColumn = ref<string | null>(null)
const elevatorPreview = ref<StructureElevator | null>(null)
const columnPreview = ref<StructureColumn | null>(null)
const message = ref('')
const guide = ref<{x?:number;y?:number}>({})
let sequence = 0
const uid = () => `structure-${Date.now().toString(36)}-${++sequence}`
type Drag = { pointerId: number; start: WarehouseStructure; startX:number; startY:number; wallId?: string; nodeId?: string; doorId?: string; elevatorId?: string; columnId?: string; capture: SVGSVGElement; bounds: DOMRect }
let drag: Drag | null = null
let suppressClick = false
let frame: number | null = null
let sample: {clientX:number;clientY:number} | null = null
const tools = [
  { id: 'select' as Tool, name: '选择', icon: MousePointer2 }, { id: 'outline' as Tool, name: '外围墙', icon: Hexagon },
  { id: 'partition' as Tool, name: '内部隔墙', icon: Minus }, { id: 'loading' as Tool, name: '装卸门', icon: Warehouse },
  { id: 'ordinary' as Tool, name: '普通门', icon: DoorOpen },
  { id: 'elevator' as Tool, name: '电梯', icon: ArrowUpDown },
  { id: 'column' as Tool, name: '柱子', icon: Square },
]
const walls = computed(() => [current.value.outline, ...current.value.partitions])
const activeWall = computed(() => walls.value.find((w) => w.id === selectedWall.value))
const invalidIds = computed(() => new Set([...props.issues, ...validateStructure(current.value)].flatMap((v) => v.objectIds)))
const points = (nodes: StructurePoint[]) => nodes.map((p) => `${p.x*6},${p.y*4}`).join(' ')
const hint = computed(() => message.value || (tool.value === 'column' ? '点击空白处放置柱子 · 默认 0.6 × 0.6 米 · 选择后可拖动或删除' : tool.value === 'elevator' ? '点击空白处放置电梯 · 默认占地 3 × 3 米 · 选择后可拖动或删除' : tool.value === 'outline' ? '依次点击拐点 · 点击起点闭合 · Esc 取消重绘' : tool.value === 'partition' ? '点击添加隔墙节点 · 双击或 Enter 结束 · Esc 取消' : tool.value === 'loading' || tool.value === 'ordinary' ? '靠近墙体预览门的位置 · 点击放置' : tool.value === 'insert' ? '点击选中墙段插入节点' : '点击墙体编辑节点 · 拖动门、电梯或柱子调整位置'))
function setPreview(value: WarehouseStructure | null) { preview.value = value; emit('preview', value) }
function clearFrame() { if (frame !== null) cancelAnimationFrame(frame); frame = null; sample = null }
function cancel() {
  clearFrame()
  if (drag) { if (drag.capture.hasPointerCapture?.(drag.pointerId)) drag.capture.releasePointerCapture(drag.pointerId); drag = null }
  draft.value = []; hover.value = null; elevatorPreview.value=null; columnPreview.value=null; guide.value = {}; setPreview(null); emit('busy', false)
}
function choose(next: Tool) { cancel(); suppressClick=false; tool.value = next; message.value = ''; selectedNode.value = null; selectedDoor.value = null; selectedElevator.value=null; selectedColumn.value=null; if(next==='elevator'||next==='column')selectedWall.value=null }
watch(() => props.editing, () => { cancel(); tool.value = 'select' })
watch(() => props.structure, () => {
  cancel()
  message.value = ''
  if (!walls.value.some((w) => w.id === selectedWall.value)) selectedWall.value = null
  if (!walls.value.some((w) => w.nodes.some((n) => n.id === selectedNode.value))) selectedNode.value = null
  if (!props.structure.doors.some((d) => d.id === selectedDoor.value)) selectedDoor.value = null
  if (!(props.structure.elevators??[]).some((e) => e.id === selectedElevator.value)) selectedElevator.value = null
  if (!(props.structure.columns??[]).some((column) => column.id === selectedColumn.value)) selectedColumn.value = null
})
watch(() => props.focusedId, (id) => {
  if (!id) return
  selectedElevator.value=null
  selectedColumn.value=null
  if((props.structure.elevators??[]).some(e=>e.id===id)){selectedElevator.value=id;selectedWall.value=null;selectedDoor.value=null;selectedNode.value=null;return}
  if((props.structure.columns??[]).some(column=>column.id===id)){selectedColumn.value=id;selectedWall.value=null;selectedDoor.value=null;selectedNode.value=null;return}
  if (props.structure.doors.some((d) => d.id === id)) { selectedDoor.value = id; selectedWall.value = null }
  else { selectedWall.value = id; selectedDoor.value = null }
})
function rawPoint(event: {clientX:number;clientY:number}, bounds = svg.value!.getBoundingClientRect()): StructurePoint {
  return { x: Math.max(0,Math.min(100,(event.clientX-bounds.left)/bounds.width*100)), y: Math.max(0,Math.min(100,(event.clientY-bounds.top)/bounds.height*100)) }
}
function snap(p: StructurePoint, anchors: StructurePoint[], bounds: DOMRect): StructurePoint {
  const next = { ...p }; guide.value = {}
  if (props.gridSnapping) { next.x = Math.round(p.x*1.2)/1.2; next.y = Math.round(p.y*.8)/.8 }
  const x = anchors.find((a) => Math.abs(a.x-p.x)*bounds.width/100 < 6)
  const y = anchors.find((a) => Math.abs(a.y-p.y)*bounds.height/100 < 6)
  if (x) { next.x=x.x; guide.value.x=x.x }
  if (y) { next.y=y.y; guide.value.y=y.y }
  return next
}
function nearest(p: StructurePoint, bounds: DOMRect, segs = structureSegments(props.structure)) {
  return segs.map((seg) => ({seg,...project(p,seg.a,seg.b)}))
    .map((hit) => ({...hit,pixels:Math.hypot((hit.point.x-p.x)*bounds.width/100,(hit.point.y-p.y)*bounds.height/100)}))
    .filter((hit) => hit.pixels <= 12).sort((a,b)=>a.pixels-b.pixels)[0]
}
function nodePoint(p: StructurePoint, bounds: DOMRect, wallId?: string, nodeId?: string): StructureNode {
  const wallIndex = wallId ? [props.structure.outline,...props.structure.partitions].findIndex((w)=>w.id===wallId) : Infinity
  const wall = [props.structure.outline,...props.structure.partitions].find((w)=>w.id===wallId)
  const isEndpoint = !wall || nodeId===wall.nodes[0]?.id || nodeId===wall.nodes.at(-1)?.id
  const earlier = [props.structure.outline,...props.structure.partitions].slice(0,wallIndex).flatMap(wallSegments)
  const hit = tool.value !== 'outline' && wallId !== props.structure.outline.id && isEndpoint ? nearest(p,bounds,earlier) : undefined
  const attachment: WallAttachment | undefined = hit ? {wallId:hit.seg.wallId,segmentId:hit.seg.id,t:hit.t} : undefined
  return { id: nodeId ?? uid(), ...(hit?.point ?? snap(p,[...props.structure.outline.nodes,...props.structure.partitions.flatMap((w)=>w.nodes),...draft.value].filter((n)=>n.id!==nodeId),bounds)), attachment }
}
function doorAt(p: StructurePoint, bounds: DOMRect, existing?: StructureDoor): StructureDoor | null {
  let segs = structureSegments(props.structure)
  const kind = existing?.kind ?? (tool.value === 'loading' ? 'loading' : 'ordinary')
  if (existing?.attachment) segs = segs.filter((s)=>s.id===existing.attachment!.segmentId && s.wallId===existing.attachment!.wallId)
  if (kind==='loading') segs=segs.filter((s)=>s.wallId===props.structure.outline.id)
  const hit = existing?.attachment ? segs.map((seg)=>({seg,...project(p,seg.a,seg.b)}))[0] : nearest(p,bounds,segs)
  if (!hit) return null
  const width = existing?.width ?? (kind==='loading'?4:1)
  const half = width/(2*distance(hit.seg.a,hit.seg.b))
  const t = half>.5 ? .5 : Math.max(half,Math.min(1-half,hit.t))
  return {id:existing?.id ?? 'door-preview', kind, width, position:interpolate(hit.seg.a,hit.seg.b,t), attachment:{wallId:hit.seg.wallId,segmentId:hit.seg.id,t}}
}
const doorPreview = ref<StructureDoor | null>(null)
function elevatorAt(p: StructurePoint,bounds: DOMRect): StructureElevator {
  const point=snap({x:p.x-2.5,y:p.y-3.75},[],bounds)
  return {id:'elevator-preview',left:point.x,top:point.y,width:5,height:7.5}
}
function elevatorIssue(lift: StructureElevator) {
  const next={...props.structure,elevators:[...(props.structure.elevators??[]).filter(e=>e.id!==lift.id),lift]}
  return validatePlannerLayout(next,props.pallets??[]).find(issue=>issue.objectIds.includes(lift.id))
}
function columnAt(p: StructurePoint,bounds: DOMRect): StructureColumn {
  const point=snap({x:p.x-.5,y:p.y-.75},[],bounds)
  return {id:'column-preview',left:point.x,top:point.y,width:1,height:1.5}
}
function columnIssue(column: StructureColumn) {
  const next={...props.structure,columns:[...(props.structure.columns??[]).filter(item=>item.id!==column.id),column]}
  return validatePlannerLayout(next,props.pallets??[]).find(issue=>issue.objectIds.includes(column.id))
}
function finishDraft() {
  const closed = tool.value==='outline'
  const wall: StructureWall = {id:closed ? props.structure.outline.id : uid(),closed,nodes:draft.value.map((n)=>({...n}))}
  if (!validWall(wall)) {message.value=closed?'至少三个拐点，外围墙不能交叉或重叠':'隔墙至少两个节点，不能交叉或重叠';return}
  const next=cloneStructure(props.structure)
  if (closed) {
    for (const door of next.doors) if (door.attachment?.wallId===next.outline.id) {door.position=doorGeometry(next,door).center;door.attachment=null}
    for (const w of next.partitions) for (const n of w.nodes) if (n.attachment?.wallId===next.outline.id) n.attachment=undefined
    next.outline=wall
  } else {
    // Only endpoints retain attachments; intermediate nodes are independent bends.
    wall.nodes.slice(1,-1).forEach((n)=>{n.attachment=undefined})
    next.partitions.push(wall)
    const issue=validateStructure(next).find((v)=>v.objectIds.includes(wall.id))
    if(issue){message.value=issue.message;return}
  }
  cancel(); selectedWall.value=wall.id; tool.value='select'; message.value=closed?'外围墙已更新，原外墙上的门请重新放置':''; emit('commit',next)
}
function backgroundClick(event: MouseEvent) {
  if(suppressClick){suppressClick=false;return}
  if(!props.editing || drag || event.detail>1)return
  const bounds=svg.value!.getBoundingClientRect(), p=rawPoint(event,bounds)
  if(tool.value==='column'){
    const column={...columnAt(p,bounds),id:uid()},issue=columnIssue(column)
    if(issue){message.value=issue.message;emit('conflict',issue.objectIds);return}
    const next=cloneStructure(props.structure);next.columns=[...(next.columns??[]),column]
    selectedColumn.value=column.id;selectedElevator.value=null;selectedWall.value=null;selectedDoor.value=null;selectedNode.value=null;tool.value='select';columnPreview.value=null;emit('commit',next);return
  }
  if(tool.value==='elevator'){
    const lift={...elevatorAt(p,bounds),id:uid()},issue=elevatorIssue(lift)
    if(issue){message.value=issue.message;return}
    const next=cloneStructure(props.structure);next.elevators=[...(next.elevators??[]),lift]
    selectedElevator.value=lift.id;selectedWall.value=null;selectedDoor.value=null;tool.value='select';elevatorPreview.value=null;emit('commit',next);return
  }
  if(tool.value==='outline'||tool.value==='partition'){
    if(tool.value==='outline'&&draft.value.length>=3&&Math.hypot((p.x-draft.value[0]!.x)*bounds.width/100,(p.y-draft.value[0]!.y)*bounds.height/100)<12){finishDraft();return}
    const n=nodePoint(p,bounds)
    if(draft.value.length && distance(n,draft.value.at(-1)!)<.1)return
    draft.value=[...draft.value,n];emit('busy',true);message.value='';return
  }
  if(tool.value==='loading'||tool.value==='ordinary'){
    const door=doorAt(p,bounds)
    if(!door){message.value='请靠近墙体放置门';return}
    const next=cloneStructure(props.structure);door.id=uid();next.doors.push(door)
    const issue=validateStructure(next).find((v)=>v.objectIds.includes(door.id))
    if(issue){message.value=issue.message;return}
    selectedDoor.value=door.id;doorPreview.value=null;emit('commit',next);message.value='门已放置，可继续放置或切换选择';return
  }
  selectedWall.value=null;selectedNode.value=null;selectedDoor.value=null;selectedElevator.value=null;selectedColumn.value=null
}
function segmentClick(event: MouseEvent,seg: StructureSegment) {
  if(tool.value!=='select'&&tool.value!=='insert'){backgroundClick(event);return}
  event.stopPropagation();selectedDoor.value=null;selectedElevator.value=null;selectedColumn.value=null;selectedNode.value=null;selectedWall.value=seg.wallId
  if(tool.value==='insert'){
    const id=uid(), p=project(rawPoint(event),seg.a,seg.b).point
    const next=insertStructureNode(props.structure,seg.wallId,seg.id,p,id)
    const wall=[next.outline,...next.partitions].find((w)=>w.id===seg.wallId)!
    if(!validWall(wall)){message.value='节点太靠近端点';return}
    selectedNode.value=id;tool.value='select';emit('commit',next)
  }
}
function doorClick(event: MouseEvent,door: StructureDoor) {
  if(!props.editing)return
  if(tool.value!=='select'){backgroundClick(event);return}
  selectedDoor.value=door.id;selectedWall.value=null;selectedNode.value=null;selectedElevator.value=null;selectedColumn.value=null
}
function elevatorClick(event:MouseEvent,lift:StructureElevator){
  if(!props.editing)return
  if(tool.value!=='select'){backgroundClick(event);return}
  selectedElevator.value=lift.id;selectedColumn.value=null;selectedWall.value=null;selectedDoor.value=null;selectedNode.value=null
}
function columnClick(event:MouseEvent,column:StructureColumn){
  if(!props.editing)return
  if(tool.value!=='select'){backgroundClick(event);return}
  selectedColumn.value=column.id;selectedElevator.value=null;selectedWall.value=null;selectedDoor.value=null;selectedNode.value=null
}
function startDrag(event: PointerEvent, wallId?: string,nodeId?: string,doorId?: string,elevatorId?:string,columnId?:string) {
  if(!props.editing||tool.value!=='select'||event.button!==0)return
  event.preventDefault();event.stopPropagation();message.value=''
  selectedWall.value=wallId??null;selectedNode.value=nodeId??null;selectedDoor.value=doorId??null
  selectedElevator.value=elevatorId??null
  selectedColumn.value=columnId??null
  const element=svg.value!;element.focus({preventScroll:true});drag={pointerId:event.pointerId,start:cloneStructure(props.structure),startX:event.clientX,startY:event.clientY,wallId,nodeId,doorId,elevatorId,columnId,capture:element,bounds:element.getBoundingClientRect()}
  element.setPointerCapture?.(event.pointerId);emit('busy',true)
}
function dragPreview(event: {clientX:number;clientY:number}) {
  if(!drag)return
  const p=rawPoint(event,drag.bounds)
  if(drag.columnId){
    const next=cloneStructure(drag.start),column=next.columns!.find(item=>item.id===drag!.columnId)!
    const position=snap({x:column.left+(event.clientX-drag.startX)/drag.bounds.width*100,y:column.top+(event.clientY-drag.startY)/drag.bounds.height*100},(next.columns??[]).filter(item=>item.id!==column.id).map(item=>({x:item.left,y:item.top})),drag.bounds)
    column.left=position.x;column.top=position.y;setPreview(next);return
  }
  if(drag.elevatorId){
    const next=cloneStructure(drag.start),lift=next.elevators!.find(e=>e.id===drag!.elevatorId)!
    const position=snap({x:lift.left+(event.clientX-drag.startX)/drag.bounds.width*100,y:lift.top+(event.clientY-drag.startY)/drag.bounds.height*100},(next.elevators??[]).filter(e=>e.id!==lift.id).map(e=>({x:e.left,y:e.top})),drag.bounds)
    lift.left=position.x;lift.top=position.y;setPreview(next);return
  }
  if(drag.doorId){
    const original=drag.start.doors.find((d)=>d.id===drag!.doorId)!, nextDoor=doorAt(p,drag.bounds,original)
    if(!nextDoor)return
    const next=cloneStructure(drag.start);next.doors=next.doors.map((d)=>d.id===nextDoor.id?nextDoor:d);setPreview(next)
  }else{
    const node=nodePoint(p,drag.bounds,drag.wallId,drag.nodeId)
    setPreview(moveStructureNode(drag.start,drag.wallId!,drag.nodeId!,node,node.attachment))
  }
}
function pointerMove(event: PointerEvent) {
  if(!props.editing)return
  if(drag){
    if(event.pointerId!==drag.pointerId)return
    sample={clientX:event.clientX,clientY:event.clientY}
    if(frame===null)frame=requestAnimationFrame(()=>{frame=null;const p=sample;sample=null;if(p)dragPreview(p)})
    return
  }
  const bounds=svg.value!.getBoundingClientRect(),p=rawPoint(event,bounds)
  if(tool.value==='column'){columnPreview.value=columnAt(p,bounds);return}
  if(tool.value==='elevator'){elevatorPreview.value=elevatorAt(p,bounds);return}
  if(tool.value==='loading'||tool.value==='ordinary')doorPreview.value=doorAt(p,bounds)
  else if(draft.value.length)hover.value=snap(p,draft.value,bounds)
}
function pointerUp(event: PointerEvent) {
  if(!drag||event.pointerId!==drag.pointerId)return
  suppressClick=true
  if(Math.hypot(event.clientX-drag.startX,event.clientY-drag.startY)<3){cancel();return}
  clearFrame();dragPreview(event)
  const next=preview.value, original=drag.start, doorId=drag.doorId, elevatorId=drag.elevatorId, columnId=drag.columnId
  if(elevatorId||columnId){
    const objectId=elevatorId??columnId!
    const issues=next ? validatePlannerLayout(next,props.pallets??[]).filter(v=>v.objectIds.includes(objectId)) : []
    cancel()
    if(issues.length){
      message.value=`${issues[0]!.message}，已回到原位`
      emit('conflict',[...new Set(issues.flatMap(issue=>issue.objectIds))])
      return
    }
    if(next&&JSON.stringify(next)!==JSON.stringify(original))emit('commit',next)
    return
  }
  const bad=next && (doorId ? validateStructure(next).find((v)=>v.objectIds.includes(doorId)) : [next.outline,...next.partitions].some((w)=>!validWall(w)) || validateStructure(next).some((issue)=>issue.id.startsWith('cross:')))
  cancel()
  if(bad){message.value=doorId?'门位置无效，已回到原位':'墙体不能交叉或重叠，已回到原位';return}
  if(next&&JSON.stringify(next)!==JSON.stringify(original))emit('commit',next)
}
function doorSwing(door: StructureDoor) {
  const g=doorGeometry(current.value,door),x=g.a.x*6,y=g.a.y*4,dx=(g.b.x-g.a.x)*6,dy=(g.b.y-g.a.y)*4,r=Math.hypot(dx,dy)
  return `M ${x} ${y} L ${x-dy} ${y+dx} M ${g.b.x*6} ${g.b.y*4} A ${r} ${r} 0 0 1 ${x-dy} ${y+dx}`
}
function loadingDoorSymbol(door: StructureDoor) {
  const g=doorGeometry(current.value,door)
  const a={x:g.a.x*6,y:g.a.y*4},b={x:g.b.x*6,y:g.b.y*4}
  const dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy)||1
  const normal={x:-dy/length*.55,y:dx/length*.55}
  const rails=[
    {x1:a.x+normal.x,y1:a.y+normal.y,x2:b.x+normal.x,y2:b.y+normal.y},
    {x1:a.x-normal.x,y1:a.y-normal.y,x2:b.x-normal.x,y2:b.y-normal.y},
  ]
  const jambs=[
    {x1:rails[0]!.x1,y1:rails[0]!.y1,x2:rails[1]!.x1,y2:rails[1]!.y1},
    {x1:rails[0]!.x2,y1:rails[0]!.y2,x2:rails[1]!.x2,y2:rails[1]!.y2},
  ]
  return {rails,jambs}
}
function removeSelection() {
  const next=cloneStructure(props.structure)
  if(selectedColumn.value){next.columns=(next.columns??[]).filter(column=>column.id!==selectedColumn.value);selectedColumn.value=null;emit('commit',next);return}
  if(selectedElevator.value){next.elevators=(next.elevators??[]).filter(e=>e.id!==selectedElevator.value);selectedElevator.value=null;emit('commit',next);return}
  if(selectedDoor.value){next.doors=next.doors.filter((d)=>d.id!==selectedDoor.value);selectedDoor.value=null}
  else if(selectedNode.value&&activeWall.value){
    if(activeWall.value.nodes.length<=(activeWall.value.closed?3:2)){message.value='外围墙至少保留三个节点，隔墙至少保留两个节点';return}
    const updated=removeStructureNode(next,activeWall.value.id,selectedNode.value)
    if(!validWall([updated.outline,...updated.partitions].find((w)=>w.id===activeWall.value!.id)!)){message.value='删除后墙体会交叉，无法删除';return}
    selectedNode.value=null;emit('commit',updated);return
  }else if(activeWall.value&&!activeWall.value.closed){
    const id=activeWall.value.id;next.partitions=next.partitions.filter((w)=>w.id!==id);next.doors=next.doors.filter((d)=>d.attachment?.wallId!==id);selectedWall.value=null;message.value='隔墙和附属门已删除，可撤销恢复'
  }else return
  emit('commit',updateAttachments(next))
}
function keydown(event: KeyboardEvent) {
  if(!props.editing || (event.target instanceof HTMLElement && ['INPUT','TEXTAREA','SELECT'].includes(event.target.tagName)))return
  if(event.key==='Escape'){event.preventDefault();cancel();tool.value='select';message.value='已取消当前操作'}
  if(event.key==='Enter'&&draft.value.length){event.preventDefault();finishDraft()}
  if(event.key==='Delete' || event.key==='Backspace'){event.preventDefault();removeSelection()}
}
watch(()=>props.editing,(editing)=>{if(editing)window.addEventListener('keydown',keydown);else window.removeEventListener('keydown',keydown)},{immediate:true})
onBeforeUnmount(()=>{cancel();window.removeEventListener('keydown',keydown)})
</script>

<template>
  <div class="structure-layer" :class="{ editing }" data-testid="structure-layer">
    <svg ref="svg" class="structure-svg" viewBox="0 0 600 400" preserveAspectRatio="none" tabindex="0" aria-label="仓库墙体编辑画布"
      @click="backgroundClick" @dblclick.prevent="tool==='partition'&&finishDraft()" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="cancel">
      <polygon :points="points(current.outline.nodes)" class="structure-floor" />
      <g class="wall-drawing" aria-hidden="true">
        <template v-for="(seg,i) in solidWallSegments(current)" :key="`${seg.id}-${i}`">
          <line :x1="seg.a.x*6" :y1="seg.a.y*4" :x2="seg.b.x*6" :y2="seg.b.y*4" class="wall-line wall-edge" :class="{invalid:invalidIds.has(seg.wallId),chosen:selectedWall===seg.wallId&&editing,'exterior-wall':seg.wallId===current.outline.id,'partition-wall':seg.wallId!==current.outline.id}" />
          <line :x1="seg.a.x*6" :y1="seg.a.y*4" :x2="seg.b.x*6" :y2="seg.b.y*4" class="wall-gap" :class="{'exterior-wall':seg.wallId===current.outline.id,'partition-wall':seg.wallId!==current.outline.id}" />
        </template>
      </g>
      <g v-if="editing" class="wall-hits">
        <line v-for="seg in structureSegments(current)" :key="`${seg.wallId}-${seg.id}`" :data-testid="`wall-segment-${seg.wallId}-${seg.id}`" :x1="seg.a.x*6" :y1="seg.a.y*4" :x2="seg.b.x*6" :y2="seg.b.y*4" class="wall-hit" @click.stop="segmentClick($event,seg)" />
      </g>
      <g v-for="door in current.doors" :key="door.id" :data-testid="`${door.kind==='loading'?'planner-loading-door':'planner-ordinary-door'}-${door.id}`" class="door" :class="{invalid:invalidIds.has(door.id),chosen:selectedDoor===door.id&&editing}" @pointerdown="startDrag($event,undefined,undefined,door.id)" @click.stop="doorClick($event,door)">
        <title>{{ door.kind==='loading'?'装卸门':'普通门' }}</title>
        <template v-if="resolveAttachment(current,door.attachment)">
          <template v-if="door.kind==='loading'">
            <line v-for="(line,index) in loadingDoorSymbol(door).rails" :key="`rail-${index}`" v-bind="line" class="door-line loading-door-rail" />
            <line v-for="(line,index) in loadingDoorSymbol(door).jambs" :key="`jamb-${index}`" v-bind="line" class="door-line loading-door-jamb" />
          </template>
          <line v-else :x1="doorGeometry(current,door).a.x*6" :y1="doorGeometry(current,door).a.y*4" :x2="doorGeometry(current,door).b.x*6" :y2="doorGeometry(current,door).b.y*4" class="door-line ordinary-door-line" />
          <line :x1="doorGeometry(current,door).a.x*6" :y1="doorGeometry(current,door).a.y*4" :x2="doorGeometry(current,door).b.x*6" :y2="doorGeometry(current,door).b.y*4" class="door-hit" />
          <path v-if="door.kind==='ordinary'" :d="doorSwing(door)" class="door-swing" />
        </template>
        <rect v-else :x="door.position.x*6-8" :y="door.position.y*4-5" width="16" height="10" class="orphan-door" />
        <text class="door-label" :x="doorGeometry(current,door).center.x*6" :y="doorGeometry(current,door).center.y*4+7" text-anchor="middle">{{ door.kind==='loading'?'装卸门':'普通门' }}{{ !door.attachment?' · 待放置':'' }}</text>
      </g>
      <g v-if="editing&&activeWall&&tool==='select'">
        <circle v-for="node in activeWall.nodes" :key="node.id" :data-testid="`wall-node-${node.id}`" :cx="node.x*6" :cy="node.y*4" r="3.2" class="wall-node" :class="{active:selectedNode===node.id}" tabindex="0" role="button" aria-label="墙体节点，拖动调整" @pointerdown="startDrag($event,activeWall.id,node.id)" @click.stop="selectedNode=node.id" @keydown.enter.stop="selectedNode=node.id" />
      </g>
      <g v-for="column in [...(current.columns??[])].sort((a,b)=>Number(a.id===selectedColumn)-Number(b.id===selectedColumn))" :key="column.id" :data-testid="`planner-column-${column.id}`"
        class="column" :class="{chosen:editing&&selectedColumn===column.id,invalid:invalidIds.has(column.id)}"
        :transform="`translate(${column.left*6},${column.top*4})`" aria-label="柱子，0.6 × 0.6 米" role="button" :tabindex="editing?0:-1"
        @pointerdown="startDrag($event,undefined,undefined,undefined,undefined,column.id)" @click.stop="columnClick($event,column)" @keydown.enter.stop="columnClick($event as unknown as MouseEvent,column)">
        <rect class="column-body" :width="column.width*6" :height="column.height*4" />
      </g>
      <g v-for="lift in [...(current.elevators??[])].sort((a,b)=>Number(a.id===selectedElevator)-Number(b.id===selectedElevator))" :key="lift.id" :data-testid="`planner-elevator-${lift.id}`"
        class="elevator" :class="{chosen:editing&&selectedElevator===lift.id,invalid:invalidIds.has(lift.id)}"
        :transform="`translate(${lift.left*6},${lift.top*4})`" :aria-label="'电梯'" role="button" :tabindex="editing?0:-1"
        @pointerdown="startDrag($event,undefined,undefined,undefined,lift.id)" @click.stop="elevatorClick($event,lift)" @keydown.enter.stop="elevatorClick($event as unknown as MouseEvent,lift)">
        <rect class="elevator-body" :width="lift.width*6" :height="lift.height*4" />
        <rect class="elevator-cabin" x="2" y="2" :width="lift.width*6-4" :height="lift.height*4-4" />
        <path class="elevator-doors" :d="`M ${lift.width*1.5} ${lift.height*4} h ${lift.width*3} M ${lift.width*1.5} ${lift.height*4-2} h ${lift.width*3} M ${lift.width*3} ${lift.height*4-2} v 2`" />
        <ArrowUpDown class="elevator-direction" :x="lift.width*3-4" y="5" :width="8" :height="8" :stroke-width="1.2" />
        <text :x="lift.width*3" :y="lift.height*4-7" text-anchor="middle">电梯</text>
      </g>
      <g v-if="editing&&tool==='elevator'&&elevatorPreview" class="elevator elevator-preview" :class="{invalid:!!elevatorIssue(elevatorPreview)}" :transform="`translate(${elevatorPreview.left*6},${elevatorPreview.top*4})`">
        <rect class="elevator-body" :width="elevatorPreview.width*6" :height="elevatorPreview.height*4" />
        <rect class="elevator-cabin" x="2" y="2" :width="elevatorPreview.width*6-4" :height="elevatorPreview.height*4-4" />
        <path class="elevator-doors" :d="`M ${elevatorPreview.width*1.5} ${elevatorPreview.height*4} h ${elevatorPreview.width*3} M ${elevatorPreview.width*1.5} ${elevatorPreview.height*4-2} h ${elevatorPreview.width*3} M ${elevatorPreview.width*3} ${elevatorPreview.height*4-2} v 2`" />
        <ArrowUpDown class="elevator-direction" :x="elevatorPreview.width*3-4" y="5" :width="8" :height="8" :stroke-width="1.2" />
        <text :x="elevatorPreview.width*3" :y="elevatorPreview.height*4-7" text-anchor="middle">电梯</text>
      </g>
      <g v-if="editing&&tool==='column'&&columnPreview" class="column column-preview" :class="{invalid:!!columnIssue(columnPreview)}" :transform="`translate(${columnPreview.left*6},${columnPreview.top*4})`">
        <rect class="column-body" :width="columnPreview.width*6" :height="columnPreview.height*4" />
      </g>
      <g v-if="editing&&draft.length" class="draft">
        <polyline :points="points([...draft,...(hover?[hover]:[])])" />
        <circle v-for="(node,i) in draft" :key="node.id" :cx="node.x*6" :cy="node.y*4" :r="i===0?4:2.5" @click.stop="i===0&&tool==='outline'&&finishDraft()" />
      </g>
      <g v-if="editing&&doorPreview&&(tool==='loading'||tool==='ordinary')" class="door-preview">
        <line :x1="doorGeometry(current,doorPreview).a.x*6" :y1="doorGeometry(current,doorPreview).a.y*4" :x2="doorGeometry(current,doorPreview).b.x*6" :y2="doorGeometry(current,doorPreview).b.y*4" />
      </g>
      <line v-if="editing&&guide.x!==undefined" :x1="guide.x*6" y1="0" :x2="guide.x*6" y2="400" class="guide" />
      <line v-if="editing&&guide.y!==undefined" x1="0" :y1="guide.y*4" x2="600" :y2="guide.y*4" class="guide" />
    </svg>
    <Teleport :to="toolbarTarget ?? 'body'" :disabled="!toolbarTarget">
    <div v-if="editing" class="structure-toolbar" :class="{ 'external-toolbar': toolbarTarget }" aria-label="结构绘制工具">
      <button v-for="item in tools" :key="item.id" :data-testid="`structure-tool-${item.id}`" :aria-pressed="tool===item.id" @click="choose(item.id)"><component :is="item.icon" :size="16"/>{{item.name}}</button>
      <span v-if="activeWall||selectedDoor||selectedElevator||selectedColumn" class="separator"/>
      <button v-if="activeWall" data-testid="structure-insert" :aria-pressed="tool==='insert'" @click="choose('insert')"><Plus :size="15"/>插入节点</button>
      <button v-if="selectedColumn||selectedElevator||selectedNode||selectedDoor||(activeWall&&!activeWall.closed)" data-testid="structure-delete" @click="removeSelection"><Trash2 :size="15"/>删除{{selectedColumn?'柱子':selectedElevator?'电梯':selectedDoor?'门':selectedNode?'节点':'隔墙'}}</button>
      <button v-if="draft.length" data-testid="structure-finish" @click="finishDraft"><Check :size="15"/>{{tool==='outline'?'闭合':'结束'}}</button>
      <button v-if="draft.length" @click="cancel"><X :size="15"/>取消</button>
    </div>
    </Teleport>
    <p v-if="editing" class="structure-hint" role="status">{{ !current.outline.nodes.length && tool === 'select' ? '空白仓库：选择「外围墙」，依次点击拐点并闭合，开始规划' : hint }}</p>
  </div>
</template>

<style scoped>
.structure-layer { position:absolute;inset:0;pointer-events:none;z-index:1; }
.structure-layer.editing { z-index:22; }
.structure-svg {width:100%;height:100%;overflow:visible;outline:none;pointer-events:none;}
.editing .structure-svg {pointer-events:auto;touch-action:none;}
.structure-floor {fill:#fbfcfd;fill-opacity:.94;pointer-events:none;}
.editing .structure-floor {fill-opacity:.12;}
.wall-line {stroke:#a8b1bb;stroke-width:.88;stroke-linejoin:round;stroke-linecap:square;}
.wall-gap {stroke:#fbfcfd;stroke-width:.34;stroke-linejoin:round;stroke-linecap:square;pointer-events:none;}
.wall-line.partition-wall {stroke:#bcc3cb;stroke-width:.64;}
.wall-gap.partition-wall {stroke-width:.22;}
.wall-line.chosen {stroke:#536dff;}
.wall-line.invalid {stroke:#dc3545;}
.wall-hit {stroke:transparent;stroke-width:10;cursor:pointer;}
.door {pointer-events:none;color:#7b8794;}
.editing .door {pointer-events:all;cursor:grab;}
.door-line {stroke:#a7b0ba;stroke-width:.58;stroke-linecap:square;}
.ordinary-door-line {stroke:#9da8b3;stroke-width:.62;}
.door-hit {stroke:transparent;stroke-width:12;}
.door-swing {fill:none;stroke:currentColor;stroke-width:.5;}
.column {color:#566271;pointer-events:none;}
.editing .column {pointer-events:all;cursor:grab;}
.column-body {fill:#6b7786;stroke:#46515f;stroke-width:.8;}
.column.chosen .column-body {fill:#6d7ff0;stroke:#536dff;stroke-width:1.5;}
.column.invalid .column-body {fill:#e5484d;stroke:#c83248;stroke-width:1.5;}
.editing .column-preview {opacity:.7;pointer-events:none;stroke-dasharray:2 1;}
.elevator {color:#88939f;pointer-events:none;}
.editing .elevator {pointer-events:all;cursor:grab;}
.elevator-body {fill:#f7f8fa;stroke:currentColor;stroke-width:.75;}
.elevator-cabin {fill:#fff;stroke:currentColor;stroke-width:.4;}
.elevator-doors {fill:none;stroke:currentColor;stroke-width:.6;stroke-linecap:square;}
.elevator-direction {pointer-events:none;}
.elevator text {fill:currentColor;font-size:5px;pointer-events:none;}
.elevator.chosen {color:#536dff;}
.elevator.chosen .elevator-body {stroke-width:1.2;fill:#eef2ff;}
.elevator.invalid {color:#dc3545;}
.elevator.invalid .elevator-body {fill:#fff1f2;stroke-width:1.2;}
.editing .elevator-preview {opacity:.7;pointer-events:none;}
.elevator-preview .elevator-body {stroke-dasharray:3 2;}
.door text {font-size:5px;fill:currentColor;paint-order:stroke;stroke:#fff;stroke-width:1.5;stroke-linejoin:round;}
.door.invalid {color:#dc3545;}
.door.invalid .door-line {stroke:#dc3545;}
.door.chosen .door-line {stroke:#536dff;stroke-width:1.4;}
.orphan-door {fill:#fff1f2;stroke:#dc3545;stroke-width:1.5;}
.wall-node {fill:white;stroke:#536dff;stroke-width:1.4;cursor:grab;}
.wall-node.active {fill:#536dff;stroke:white;}
.wall-node:focus-visible {stroke:#0f172a;stroke-width:2;}
.draft polyline {fill:none;stroke:#536dff;stroke-width:1.5;stroke-dasharray:4 3;pointer-events:none;}
.draft circle {fill:white;stroke:#536dff;stroke-width:1.4;cursor:pointer;}
.door-preview {pointer-events:none;stroke:#08a69b;stroke-width:4;opacity:.8;}
.guide {stroke:#08a69b;stroke-width:.6;stroke-dasharray:4 3;pointer-events:none;}
.structure-toolbar {position:absolute;top:8px;left:44px;right:10px;display:flex;align-items:center;flex-wrap:wrap;gap:3px;width:max-content;max-width:calc(100% - 54px);padding:5px;border:1px solid #dce3ed;border-radius:8px;background:rgba(255,255,255,.97);box-shadow:0 3px 12px #25314d15;pointer-events:auto;}
.structure-toolbar.external-toolbar {position:static;max-width:none;border:0;box-shadow:none;padding:0;background:transparent;}
.structure-toolbar button {display:flex;align-items:center;gap:5px;min-height:32px;padding:5px 8px;border:0;border-radius:5px;background:transparent;color:#536176;font:inherit;font-size:12px;cursor:pointer;}
.structure-toolbar button:hover,.structure-toolbar button[aria-pressed=true] {background:#eef2ff;color:#4663ee;}
.structure-toolbar button:focus-visible {outline:2px solid #536dff;outline-offset:1px;}
.separator {height:20px;width:1px;background:#dce3ed;margin:0 3px;}
.structure-hint {position:absolute;bottom:10px;left:12px;max-width:calc(100% - 210px);margin:0;padding:7px 12px;border:1px solid #e2e8f0;border-radius:6px;background:#fffffff5;color:#536176;font-size:12px;box-shadow:0 2px 8px #25314d10;}
</style>
