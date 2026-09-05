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
  <AccessibleDialog :open="open" title="添加产品" description="库存以“个”为权威值，件数由包装规格自动换算。" test-id="add-product-dialog" body-test-id="add-product-body" footer-test-id="add-product-footer" close-test-id="add-product-close" overlay-class="items-stretch justify-end" panel-class="w-[400px] max-w-full !rounded-none" @cancel="emit('cancel')">
    <div class="drawer-fields">
      <label>SKU 产品<select v-model="skuId" data-testid="add-product-sku"><option v-for="item in catalog" :key="item.skuId" :value="item.skuId">{{ item.productName }} · {{ item.skuCode }}</option></select></label>
      <label>每件个数<input data-testid="add-product-packaging" readonly :value="`${sku?.unitsPerCase ?? 0} 个/件`" /></label>
      <label>库存个数<input v-model="units" data-testid="add-product-units" type="number" min="0" step="1" /></label>
      <div class="derived"><span>件数换算（只读）</span><output data-testid="add-product-case-copy">{{ caseCopy }}</output></div>
      <label>目标区域<select v-model="areaId" data-testid="add-product-area"><option value="" disabled>选择区域</option><option v-for="area in areas" :key="area.id" :value="area.id" :disabled="area.locked || !area.visible">{{ area.name }}{{ area.locked ? '（已锁定）' : '' }}</option></select></label>
      <p>产品块视觉大小不改变库存数量。</p><p v-if="error" role="alert" class="error">{{ error }}</p>
    </div>
    <template #footer><button data-testid="add-product-cancel" @click="emit('cancel')">取消</button><button data-testid="add-product-confirm" class="primary" @click="emit('confirm', { skuId: Number(skuId), areaId, units: units === '' ? NaN : Number(units) })">确认放入</button></template>
  </AccessibleDialog>
</template>

<style scoped>
.drawer-fields { display:grid; gap:20px; font-size:12px; color:#25314D; }
label,.derived { display:grid; gap:8px; } input,select,button { min-height:40px; border:1px solid #E2E8F0; border-radius:8px; padding:8px 12px; background:#FFF; width:100%; }
input[readonly],.derived { background:#F8FAFC; color:#64748B; } .derived { padding:12px; border-radius:8px; } output { font-size:16px; color:#25314D; font-weight:600; }
button { width:auto; } .primary { background:#536DFF; color:white; border-color:#536DFF; }.primary:hover { background:#465EEA; } p { color:#64748B; }.error { color:#EF476F; }
</style>
