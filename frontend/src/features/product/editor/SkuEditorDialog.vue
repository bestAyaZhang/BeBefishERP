<script setup lang="ts">
import { Save } from 'lucide-vue-next';
import { ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import type { SkuForm } from '../types';
import {
  cloneSku,
  createBlankSku,
  isValidDecimal,
  MAX_SAFE_MONEY
} from './productEditorState';

const props = defineProps<{
  open: boolean;
  sku?: SkuForm;
  title: string;
}>();

const emit = defineEmits<{
  cancel: [];
  save: [sku: SkuForm];
}>();

const draft = ref<SkuForm>(createBlankSku());
const specificationText = ref('');
const errors = ref<Record<string, string>>({});

watch(
  () => [props.open, props.sku] as const,
  ([open]) => {
    if (!open) return;
    draft.value = cloneSku(props.sku ?? createBlankSku());
    specificationText.value = draft.value.specificationValues.join(' / ');
    errors.value = {};
  },
  { immediate: true, deep: true }
);

function save() {
  const nextErrors: Record<string, string> = {};
  if (!draft.value.skuName.trim()) nextErrors.skuName = '请输入 SKU 名称';
  if (!draft.value.salesUnit.trim()) nextErrors.salesUnit = '请输入销售单位';
  if (draft.value.defaultSalePrice !== null && draft.value.defaultSalePrice < 0) {
    nextErrors.defaultSalePrice = '默认售价不能小于 0';
  }
  if (draft.value.standardCost !== null && draft.value.standardCost < 0) {
    nextErrors.standardCost = '标准成本不能小于 0';
  }
  if (draft.value.safetyStockQuantity !== null && draft.value.safetyStockQuantity < 0) {
    nextErrors.safetyStockQuantity = '安全库存不能小于 0';
  }
  if (draft.value.defaultSalePrice !== null && draft.value.defaultSalePrice >= 0 && !isValidDecimal(draft.value.defaultSalePrice, 15, 4)) {
    nextErrors.defaultSalePrice = '默认售价最多允许 15 位整数和 4 位小数';
  }
  if (draft.value.standardCost !== null && draft.value.standardCost >= 0 && !isValidDecimal(draft.value.standardCost, 15, 4)) {
    nextErrors.standardCost = '标准成本最多允许 15 位整数和 4 位小数';
  }
  if (draft.value.safetyStockQuantity !== null && draft.value.safetyStockQuantity >= 0 && !isValidDecimal(draft.value.safetyStockQuantity, 14, 4)) {
    nextErrors.safetyStockQuantity = '安全库存最多允许 14 位整数和 4 位小数';
  }
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) return;
  emit('save', {
    ...cloneSku(draft.value),
    skuName: draft.value.skuName.trim(),
    skuCode: draft.value.skuCode.trim(),
    barcode: draft.value.barcode.trim(),
    salesUnit: draft.value.salesUnit.trim(),
    specificationValues: specificationText.value
      .split('/')
      .map((value) => value.trim())
      .filter(Boolean)
  });
}

function updateStatus() {
  if (draft.value.status === 'disabled') draft.value.defaultSku = false;
}
</script>

<template>
  <AccessibleDialog
    :open="open"
    :title="title"
    description="维护 SKU 识别信息、规格、单位和成本库存数据。"
    test-id="sku-editor-dialog"
    body-test-id="sku-dialog-body"
    footer-test-id="sku-dialog-footer"
    close-test-id="sku-dialog-close"
    @cancel="$emit('cancel')"
  >
        <div class="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>SKU 货号</span>
            <input data-testid="sku-dialog-sku-code" v-model="draft.skuCode" maxlength="50" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="可留空自动生成" />
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>单杯条码</span>
            <input data-testid="sku-dialog-barcode" v-model="draft.barcode" maxlength="100" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="请输入条码" />
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>SKU 名称 <em class="not-italic text-rose-500">*</em></span>
            <input data-testid="sku-dialog-name" v-model="draft.skuName" maxlength="200" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.skuName ? 'border-rose-400' : 'border-slate-300'" placeholder="例如：透明款" />
            <span v-if="errors.skuName" class="block text-xs text-rose-600">{{ errors.skuName }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600 md:col-span-2">
            <span>规格值</span>
            <input data-testid="sku-dialog-specification" v-model="specificationText" maxlength="500" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="多个规格值使用 / 分隔" />
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>销售单位 <em class="not-italic text-rose-500">*</em></span>
            <input data-testid="sku-dialog-sales-unit" v-model="draft.salesUnit" maxlength="20" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.salesUnit ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.salesUnit" class="block text-xs text-rose-600">{{ errors.salesUnit }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>SKU 状态</span>
            <select data-testid="sku-dialog-status" v-model="draft.status" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none focus:border-[#536dff]" @change="updateStatus">
              <option value="enabled">启用</option>
              <option value="disabled">停用</option>
            </select>
          </label>
          <label class="flex min-h-11 items-center gap-3 self-end text-sm font-bold text-slate-600">
            <input data-testid="sku-dialog-default" v-model="draft.defaultSku" type="checkbox" :disabled="draft.status === 'disabled'" class="h-4 w-4 rounded border-slate-300 text-[#536dff]" />
            设为默认 SKU
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>默认售价</span>
            <input data-testid="sku-dialog-sale-price" v-model.number="draft.defaultSalePrice" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.defaultSalePrice ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.defaultSalePrice" data-testid="sku-dialog-sale-price-error" class="block text-xs text-rose-600">{{ errors.defaultSalePrice }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>标准成本</span>
            <input data-testid="sku-dialog-standard-cost" v-model.number="draft.standardCost" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.standardCost ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.standardCost" class="block text-xs text-rose-600">{{ errors.standardCost }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>安全库存</span>
            <input data-testid="sku-dialog-safety-stock" v-model.number="draft.safetyStockQuantity" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.safetyStockQuantity ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.safetyStockQuantity" class="block text-xs text-rose-600">{{ errors.safetyStockQuantity }}</span>
          </label>
        </div>
      <template #footer>
        <button data-testid="sku-dialog-cancel" type="button" class="h-10 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="$emit('cancel')">取消</button>
        <button data-testid="sku-dialog-save" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white hover:bg-[#435be0]" @click="save">
          <Save class="h-4 w-4" aria-hidden="true" />
          保存 SKU
        </button>
      </template>
  </AccessibleDialog>
</template>
