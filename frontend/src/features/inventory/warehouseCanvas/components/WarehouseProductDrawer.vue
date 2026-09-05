<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AccessibleDialog from '../../../../components/AccessibleDialog.vue'
import { formatCaseBreakdown } from '../warehouseCanvasModel'
import type { WarehouseArea, WarehouseSku } from '../types'

const props = defineProps<{ open: boolean; catalog: WarehouseSku[]; areas: WarehouseArea[]; initialSkuId?: number; initialAreaId?: string; error: string }>()
const emit = defineEmits<{ cancel: []; confirm: [input: { skuId: number; areaId: string; units: number }] }>()
const skuId = ref(0)
const areaId = ref('')
const units = ref<number | string>(250)
const sku = computed(() => props.catalog.find((item) => item.skuId === Number(skuId.value)))
const caseCopy = computed(() => sku.value && units.value !== '' && Number.isInteger(Number(units.value)) && Number(units.value) >= 0 ? formatCaseBreakdown(Number(units.value), sku.value.unitsPerCase) : '请输入有效库存个数')
watch(() => props.open, (open) => {
  if (!open) return
  skuId.value = props.initialSkuId ?? props.catalog[0]?.skuId ?? 0
  areaId.value = props.initialAreaId ?? props.areas.find((area) => area.visible && !area.locked)?.id ?? ''
  units.value = 250
})
</script>

<template>
  <AccessibleDialog :open="open" title="放入 SKU" :description="`${sku?.skuCode ?? ''} · ${sku?.productName ?? ''} · 目标区域 ${areas.find((area) => area.id === areaId)?.name ?? '未选择'}`" test-id="add-product-dialog" body-test-id="add-product-body" footer-test-id="add-product-footer" close-test-id="add-product-close" overlay-class="items-end justify-end" panel-class="warehouse-canvas-flow-drawer" body-class="warehouse-drawer-body" @cancel="emit('cancel')">
    <div class="drawer-fields">
      <label>SKU 产品<select v-model="skuId" data-testid="add-product-sku"><option v-for="item in catalog" :key="item.skuId" :value="item.skuId">{{ item.productName }} · {{ item.skuCode }}</option></select></label>
      <label>包装规格（自动读取/只读）<input data-testid="add-product-packaging" readonly :value="`${sku?.unitsPerCase ?? 0} 个/件`" /></label>
      <label>库存个数<input v-model="units" data-testid="add-product-units" type="number" min="0" step="1" /><small>仅支持大于或等于 0 的整数</small></label>
      <div class="derived"><span>件数换算（只读）</span><output data-testid="add-product-case-copy">{{ caseCopy }}</output></div>
      <label>目标区域<select v-model="areaId" data-testid="add-product-area"><option value="" disabled>选择区域</option><option v-for="area in areas" :key="area.id" :value="area.id" :disabled="area.locked || !area.visible">{{ area.name }}{{ area.locked ? '（已锁定）' : '' }}</option></select></label>
      <p>产品块视觉大小不改变库存数量。</p><p v-if="error" role="alert" class="error">{{ error }}</p>
    </div>
    <template #footer><button data-testid="add-product-cancel" @click="emit('cancel')">取消</button><button data-testid="add-product-confirm" class="primary" @click="emit('confirm', { skuId: Number(skuId), areaId, units: units === '' ? NaN : Number(units) })">放入 {{ areas.find((area) => area.id === areaId)?.name ?? '区域' }}</button></template>
  </AccessibleDialog>
</template>

<style scoped>
.drawer-fields { display:grid; gap:18px; font-size:12px; color:#25314D; padding:0; }
.drawer-fields > * { width:320px; max-width:100%; } small { color:#94a3b8; line-height:18px; }
label,.derived { display:grid; gap:8px; } input,select,button { min-height:40px; border:1px solid #E2E8F0; border-radius:8px; padding:8px 12px; background:#FFF; width:100%; }
input,select { font-size:14px; } input[readonly],.derived { background:#F8FAFC; color:#94a3b8; } .derived { padding:0; border-radius:8px; } output { font-size:14px; line-height:22px; border:1px solid #e2e8f0; padding:8px 12px; border-radius:8px; color:#94a3b8; font-weight:400; }
button { width:auto; } .primary { background:#536DFF; color:white; border-color:#536DFF; }.primary:hover { background:#465EEA; } p { color:#64748B; }.error { color:#EF476F; }
</style>
