<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { masterdataService } from '../../../masterdata/masterdataService'
import type { MasterdataService, Warehouse } from '../../../masterdata/types'
import { blankWarehouseLayout, warehouseLayoutService } from '../warehouseLayoutService'
import type { WarehouseLayoutDocument } from '../warehouseLayoutService'
import FigmaSelect from '../../../masterdata/components/FigmaSelect.vue'

const props = withDefaults(defineProps<{ document:WarehouseLayoutDocument; busy:boolean; viewing?:boolean; inventoryViewing?:boolean; embedded?:boolean }>(), {
  viewing: false,
  inventoryViewing: false,
  embedded: false,
})
const emit = defineEmits<{ loaded:[document:WarehouseLayoutDocument]; name:[name:string]; available:[ready:boolean]; warehouse:[id:number] }>()
const warehouses = inject<MasterdataService>('masterdataService',masterdataService)
const layouts = inject('warehouseLayoutService',warehouseLayoutService)
const route = inject(routeLocationKey,null)
const choices = ref<Warehouse[]>([])
const selected = ref<number | null>(null)
const revision = ref(0)
const baseline = ref('')
const loading = ref(false)
const saving = ref(false)
const ready = ref(false)
const error = ref('')
const comparable = (document:WarehouseLayoutDocument) => JSON.stringify({structure:document.structure,palletGroups:document.palletGroups})
const dirty = computed(() => ready.value && comparable(props.document) !== baseline.value)
const warehouseOptions = computed(() => [
  { value: null, label: '选择仓库', disabled: true },
  ...choices.value.map(warehouse => ({ value: warehouse.id, label: warehouse.warehouseName }))
])
let disposed = false
async function load(id:number) {
  loading.value = true; ready.value = false; emit('available',false); error.value = ''
  selected.value = id
  emit('warehouse', id)
  emit('name',choices.value.find(w => w.id === id)?.warehouseName ?? '仓库')
  try {
    const result = await layouts.load(id)
    if (disposed) return
    const document = result.document ?? blankWarehouseLayout()
    // Reject unsupported or corrupt documents before replacing the current canvas.
    if (document.schemaVersion !== 1 || !Array.isArray(document.structure?.outline?.nodes) || !Array.isArray(document.palletGroups)) throw new Error('保存的规划格式不受支持，无法加载')
    revision.value = result.revision
    baseline.value = comparable(document)
    emit('loaded',document)
    ready.value = true; emit('available',true)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '规划加载失败' }
  finally { loading.value = false }
}
async function initialize() {
  loading.value = true; error.value = ''
  try {
    const all: Warehouse[] = []
    if (props.inventoryViewing) {
      all.push(...(await warehouses.listActiveWarehouses()).filter(warehouse => warehouse.status === 'enabled'))
    } else {
      for (let page=1; ; page++) {
        const result = await warehouses.listWarehouses({page,size:100})
        all.push(...result.records)
        if (all.length >= result.total || result.records.length === 0) break
      }
    }
    if (disposed) return
    choices.value = all
    const requested = Number(route?.query.warehouseId)
    const hasValidRequestedId = Number.isSafeInteger(requested) && requested > 0
    if (!props.inventoryViewing && route && !hasValidRequestedId) { error.value = '请从仓库列表选择需要管理的仓库'; return }
    const requestedWarehouse = hasValidRequestedId ? all.find(w => w.id === requested) : undefined
    if (!props.inventoryViewing && route && !requestedWarehouse) { error.value = '指定仓库不存在或无权访问，请返回仓库列表重新选择'; return }
    const warehouse = requestedWarehouse ?? all.find(w => w.defaultWarehouse) ?? all[0]
    if (!warehouse) { error.value = props.inventoryViewing ? '暂无启用仓库，请先在仓库管理中启用仓库' : requested ? '指定仓库不存在或无权访问，请选择仓库' : '暂无可用仓库，请先在仓库管理中创建'; return }
    await load(warehouse.id)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '仓库列表加载失败' }
  finally { loading.value = false }
}
async function change(value:string | number | null) {
  const id = Number(value)
  if (!id || id === selected.value) return
  if (dirty.value && !window.confirm('当前规划尚未保存，切换仓库将放弃这些改动。是否继续？')) return
  await load(id)
}
async function saveDocument(document = props.document): Promise<boolean> {
  if (!ready.value || !selected.value || saving.value || props.busy) return false
  saving.value = true; error.value = ''
  try {
    const snapshot = JSON.parse(JSON.stringify(document)) as WarehouseLayoutDocument
    const saved = await layouts.save(selected.value,{revision:revision.value,document:snapshot})
    revision.value = saved.revision; baseline.value = comparable(snapshot)
    return true
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存失败，请重试'; return false }
  finally { saving.value = false }
}
function beforeUnload(event:BeforeUnloadEvent) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { window.addEventListener('beforeunload',beforeUnload); void initialize() })
onBeforeUnmount(() => { disposed = true; window.removeEventListener('beforeunload',beforeUnload) })
defineExpose({ saveDocument })
</script>
<template>
  <div class="layout-controls" :class="{ 'inventory-controls': inventoryViewing, 'embedded-controls': embedded }" data-testid="warehouse-layout-controls">
    <a v-if="!inventoryViewing" href="/warehouses">返回仓库列表</a>
    <FigmaSelect
      v-if="inventoryViewing || !route"
      class="layout-warehouse-select"
      :model-value="selected"
      :options="warehouseOptions"
      label="仓库"
      accessible-label="选择仓库"
      test-id-prefix="warehouse-layout"
      trigger-test-id="warehouse-layout-selector"
      :inline="embedded"
      :disabled="loading || saving || busy"
      @update:model-value="change"
    />
    <span role="status">{{ loading ? '加载中…' : saving ? '保存中…' : !ready ? '尚未加载规划' : dirty ? '未保存' : revision ? '已保存' : '尚未创建规划' }}</span>
    <button v-if="!viewing" :disabled="!ready || loading || saving || busy || !dirty" @click="saveDocument()">保存规划</button>
    <span v-if="error" role="alert">{{error}}</span>
    <button v-if="error && !ready" :disabled="loading" @click="selected ? load(selected) : initialize()">重试加载</button>
  </div>
</template>
<style scoped>
.layout-controls {display:flex;align-items:center;flex-wrap:wrap;gap:10px;min-height:44px;padding:6px 16px;border-bottom:1px solid #e2e8f0;background:#fff;color:#64748b;font-size:12px;}
.inventory-controls {align-items:flex-start;min-height:74px;}
.layout-warehouse-select {width:230px;height:62px;}
.inventory-controls > [role=status] {margin-top:22px;}
.embedded-controls {width:100%;min-width:0;min-height:40px;padding:0;border:0;background:transparent;flex-wrap:nowrap;}
.embedded-controls .layout-warehouse-select {min-width:0;height:40px;flex:1 1 230px;}
.embedded-controls > [role=status] {min-width:0;margin-top:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
button {padding:5px 10px;border:1px solid #dce3ef;border-radius:5px;background:#eef2ff;color:#536dff;}
button:disabled {opacity:.45;cursor:not-allowed;}
[role=alert] {color:#b42338;}
</style>
