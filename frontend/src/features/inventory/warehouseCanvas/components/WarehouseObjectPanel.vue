<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRightLeft, Lock, Trash2, X } from 'lucide-vue-next'
import { formatCaseBreakdown } from '../warehouseCanvasModel'
import type { CanvasRect, WarehouseArea, WarehouseSku, WarehouseSkuBlock } from '../types'

const props = defineProps<{ area?: WarehouseArea; block?: WarehouseSkuBlock; sku?: WarehouseSku; areas: WarehouseArea[]; error: string }>()
const emit = defineEmits<{
  close: []; rename: [name: string]; 'area-rect': [rect: CanvasRect]; 'block-rect': [rect: CanvasRect]
  units: [units: number]; lock: []; 'delete-area': []; 'delete-block': []; 'move-whole': [areaId: string]; 'partial-move': []
}>()
const targetAreaId = ref('')
const locked = computed(() => props.area?.locked ?? props.areas.find((area) => area.id === props.block?.areaId)?.locked ?? false)
const targets = computed(() => props.areas.filter((area) => area.id !== props.block?.areaId && area.visible && !area.locked))
const caseCopy = computed(() => props.block && props.sku ? formatCaseBreakdown(props.block.units, props.sku.unitsPerCase) : '')
watch(() => [props.block?.id, props.block?.areaId, targets.value.map((area) => area.id).join(',')], () => { targetAreaId.value = targets.value[0]?.id ?? '' }, { immediate: true })
const fields = [{ key: 'x', label: 'X 坐标' }, { key: 'y', label: 'Y 坐标' }, { key: 'width', label: '宽度' }, { key: 'height', label: '高度' }] as const
function numberValue(event: Event) { const value = (event.target as HTMLInputElement).value; return value === '' ? NaN : Number(value) }
function editRect(field: keyof CanvasRect, event: Event) {
  const object = props.block ?? props.area
  if (!object) return
  const rect = { x: object.x, y: object.y, width: object.width, height: object.height, [field]: numberValue(event) }
  if (props.block) emit('block-rect', rect)
  else emit('area-rect', rect)
}
</script>

<template>
  <aside class="object-panel" :data-testid="block ? 'warehouse-block-properties' : 'warehouse-area-properties'">
    <header><h2>{{ block ? '产品块属性' : '区域属性' }}</h2><button aria-label="返回产品库" @click="emit('close')"><X :size="16" /></button></header>
    <template v-if="block && sku">
      <label>SKU<input readonly :value="sku.skuCode" /></label><small>{{ sku.productName }}</small>
      <label>库存个数<input data-testid="sku-units-input" type="number" min="0" step="1" :value="block.units" :disabled="locked" @change="emit('units', numberValue($event))" /></label>
      <small>权威库存单位：个 · {{ sku.unitsPerCase }} 个/件</small>
      <label>件数换算（只读）<input data-testid="sku-case-readonly" readonly :value="caseCopy" /></label>
      <output data-testid="sku-case-copy">{{ caseCopy }}</output>
    </template>
    <template v-else-if="area">
      <label>区域名称<input data-testid="area-name-property" :value="area.name" :disabled="locked" @change="emit('rename', ($event.target as HTMLInputElement).value)" /></label>
      <button data-testid="area-lock-property" :aria-pressed="area.locked" @click="emit('lock')"><Lock :size="14" />{{ area.locked ? '解锁区域' : '锁定区域' }}</button>
    </template>
    <div class="rect-grid"><label v-for="field in fields" :key="field.key">{{ field.label }}<input :data-testid="`${block ? 'block' : 'area'}-${field.key}-input`" type="number" :value="(block ?? area)?.[field.key]" :disabled="locked" @change="editRect(field.key, $event)" /></label></div>
    <p>视觉大小不改变库存。布局尺寸不代表容量。</p>
    <p v-if="locked">所属区域已锁定，请先解锁后编辑。</p>
    <template v-if="block">
      <h3><ArrowRightLeft :size="14" />跨区域移动</h3>
      <label>目标区域<select v-model="targetAreaId" data-testid="move-whole-area" :disabled="locked"><option value="" disabled>选择目标区域</option><option v-for="target in targets" :key="target.id" :value="target.id">{{ target.name }}</option></select></label>
      <button data-testid="move-whole-confirm" :disabled="locked || !targetAreaId" @click="emit('move-whole', targetAreaId)">整块移动</button>
      <button data-testid="open-partial-move" class="primary" :disabled="locked || !targets.length || block.units < 2" @click="emit('partial-move')">移动部分库存</button>
      <button data-testid="delete-warehouse-block" class="danger" :disabled="locked" @click="emit('delete-block')"><Trash2 :size="14" />删除产品块</button>
    </template>
    <button v-else data-testid="delete-warehouse-area" class="danger" :disabled="locked" @click="emit('delete-area')"><Trash2 :size="14" />删除区域</button>
    <p v-if="error" class="danger" role="alert">{{ error }}</p>
  </aside>
</template>

<style scoped>
.object-panel { display:grid; align-content:start; gap:8px; padding:20px; border:1px solid #E2E8F0; border-radius:8px; background:#FFF; min-width:0; font-size:12px; }
.object-panel[data-testid="warehouse-block-properties"] { position:fixed; z-index:30; top:168px; right:0; bottom:0; width:360px; overflow:auto; }
.object-panel[data-testid="warehouse-area-properties"] { height:743px; overflow:auto; padding:16px; }
.object-panel[data-testid="warehouse-block-properties"] header { position:absolute; right:8px; top:8px; }
.object-panel[data-testid="warehouse-block-properties"] header h2 { display:none; }
.object-panel[data-testid="warehouse-block-properties"] header button { width:24px; min-height:24px; padding:2px; border:0; }
.object-panel[data-testid="warehouse-block-properties"] label:first-of-type { padding-right:0; }
header,h3 { display:flex; align-items:center; justify-content:space-between; gap:6px; } h2,h3 { font-weight:700; } h2 { font-size:13px; }
small,p { color:#64748B; } p { line-height:1.7; } label { display:grid; gap:6px; } input,select,button { width:100%; min-height:40px; border:1px solid #E2E8F0; border-radius:8px; padding:8px; background:white; }
input,select { font-size:14px; line-height:22px; }
button { display:flex; align-items:center; justify-content:center; gap:6px; } header button { width:40px; } input[readonly],input:disabled { background:#F8FAFC; color:#64748B; } button:disabled,select:disabled { opacity:.45; cursor:not-allowed; }
.rect-grid { display:grid; grid-template-columns:1fr; gap:8px; } .rect-grid input { min-width:0; } .danger { color:#EF476F; } output { color:#64748b; font-size:12px; }
.primary { background:#536dff; color:#fff; border-color:#536dff; }
button.danger { background:#ef476f; color:#fff; border-color:#ef476f; }
[data-testid="sku-case-copy"] { position:absolute; width:1px; height:1px; overflow:hidden; }
@media (max-width:1023px) { .object-panel[data-testid="warehouse-block-properties"] { width:320px; top:100px; } }
</style>
