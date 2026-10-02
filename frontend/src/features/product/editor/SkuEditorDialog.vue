<script setup lang="ts">
import { ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import type { ProductService, SkuForm } from '../types';
import ProductImageUpload from './ProductImageUpload.vue';
import { cloneSku, createBlankSku, isValidDecimal, MAX_SAFE_MONEY } from './productEditorState';

const props = withDefaults(defineProps<{
  open: boolean;
  sku?: SkuForm;
  title: string;
  service: ProductService;
  preview?: string;
}>(), { preview: '' });

const emit = defineEmits<{
  cancel: [];
  save: [sku: SkuForm, preview: string];
  'update:uploading': [value: boolean];
}>();

const draft = ref<SkuForm>(createBlankSku());
const specificationText = ref('');
const imagePreview = ref('');
const errors = ref<Record<string, string>>({});

watch(
  () => [props.open, props.sku, props.preview] as const,
  ([open]) => {
    if (!open) return;
    draft.value = cloneSku(props.sku ?? createBlankSku());
    specificationText.value = draft.value.specificationValues.join(' / ');
    imagePreview.value = props.preview;
    errors.value = {};
  },
  { immediate: true, deep: true }
);

function save() {
  const nextErrors: Record<string, string> = {};
  if (!draft.value.skuName.trim()) nextErrors.skuName = '请输入 SKU 名称';
  if (!draft.value.salesUnit.trim()) nextErrors.salesUnit = '请输入销售单位';
  if (draft.value.defaultSalePrice !== null && draft.value.defaultSalePrice < 0) nextErrors.defaultSalePrice = '默认售价不能小于 0';
  if (draft.value.standardCost !== null && draft.value.standardCost < 0) nextErrors.standardCost = '标准成本不能小于 0';
  if (draft.value.safetyStockQuantity !== null && draft.value.safetyStockQuantity < 0) nextErrors.safetyStockQuantity = '安全库存不能小于 0';
  if (draft.value.defaultSalePrice !== null && draft.value.defaultSalePrice >= 0 && !isValidDecimal(draft.value.defaultSalePrice, 15, 4)) nextErrors.defaultSalePrice = '默认售价最多允许 15 位整数和 4 位小数';
  if (draft.value.standardCost !== null && draft.value.standardCost >= 0 && !isValidDecimal(draft.value.standardCost, 15, 4)) nextErrors.standardCost = '标准成本最多允许 15 位整数和 4 位小数';
  if (draft.value.safetyStockQuantity !== null && draft.value.safetyStockQuantity >= 0 && !isValidDecimal(draft.value.safetyStockQuantity, 14, 4)) nextErrors.safetyStockQuantity = '安全库存最多允许 14 位整数和 4 位小数';
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) return;
  emit('save', {
    ...cloneSku(draft.value),
    skuName: draft.value.skuName.trim(),
    skuCode: draft.value.skuCode.trim(),
    barcode: draft.value.barcode.trim(),
    salesUnit: draft.value.salesUnit.trim(),
    specificationValues: specificationText.value.split('/').map((value) => value.trim()).filter(Boolean)
  }, imagePreview.value);
}

function updateStatus() {
  if (draft.value.status === 'disabled') draft.value.defaultSku = false;
}
</script>

<template>
  <AccessibleDialog
    :open="open"
    :title="title"
    :description="title === '新增 SKU' ? '为当前商品新增一个可销售规格' : '维护当前 SKU 的识别信息与销售状态'"
    test-id="sku-editor-dialog"
    body-test-id="sku-dialog-body"
    footer-test-id="sku-dialog-footer"
    close-test-id="sku-dialog-close"
    overlay-class="items-start justify-center pt-[174px]"
    panel-class="h-[604px] w-[760px]"
    header-class="h-[82px] px-6"
    body-class="h-[450px] overflow-hidden px-6 py-6"
    footer-class="h-[72px] items-center px-6"
    @cancel="$emit('cancel')"
  >
    <div class="grid grid-cols-[200px_1fr] gap-6">
      <ProductImageUpload
        :file-id="draft.skuImageFileId"
        :preview="imagePreview"
        :service="service"
        label="SKU 图片"
        test-id="sku-dialog-image"
        variant="dialog-sku"
        @update:file-id="draft.skuImageFileId = $event"
        @update:preview="imagePreview = $event"
        @update:uploading="$emit('update:uploading', $event)"
      />
      <div class="space-y-3">
        <label class="sku-dialog-field"><span>SKU 货号 <em>*</em></span><input v-model="draft.skuCode" data-testid="sku-dialog-sku-code" maxlength="50" placeholder="请输入 SKU 货号" /></label>
        <label class="sku-dialog-field"><span>规格组合 <em>*</em></span><input v-model="draft.skuName" data-testid="sku-dialog-name" maxlength="200" :class="errors.skuName ? 'border-[#ef476f]' : ''" placeholder="请输入颜色、功率、尺寸等规格" /><small v-if="errors.skuName">{{ errors.skuName }}</small></label>
        <label class="sku-dialog-field"><span>单杯条码（选填）</span><input v-model="draft.barcode" data-testid="sku-dialog-barcode" maxlength="100" placeholder="可留空，或输入商品条码" /></label>
      </div>
    </div>

    <div class="mt-4 grid grid-cols-2 gap-4">
      <label class="sku-dialog-field">
        <span>销售价 <em>*</em></span>
        <span class="unit-input">
          <input v-model.number="draft.defaultSalePrice" data-testid="sku-dialog-sale-price" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" :class="errors.defaultSalePrice ? 'border-[#ef476f]' : ''" placeholder="请输入销售价" />
          <span data-testid="sku-dialog-sale-price-unit" class="unit-suffix">元</span>
        </span>
        <small v-if="errors.defaultSalePrice" data-testid="sku-dialog-sale-price-error">{{ errors.defaultSalePrice }}</small>
      </label>
      <label class="sku-dialog-field"><span>SKU 状态 <em>*</em></span><select v-model="draft.status" data-testid="sku-dialog-status" @change="updateStatus"><option value="enabled">启用</option><option value="disabled">停用</option></select></label>
    </div>

    <div class="mt-4 flex h-[58px] items-center gap-3 rounded-lg bg-[#f1f4ff] px-4 text-xs text-[#536dff]"><span class="text-base">i</span>SKU 货号、规格组合不可重复；条码选填，填写后不可重复。</div>

    <div class="sr-only" aria-hidden="true">
      <input v-model="specificationText" data-testid="sku-dialog-specification" tabindex="-1" />
      <input v-model="draft.salesUnit" data-testid="sku-dialog-sales-unit" tabindex="-1" />
      <input v-model.number="draft.standardCost" data-testid="sku-dialog-standard-cost" type="number" tabindex="-1" />
      <input v-model.number="draft.safetyStockQuantity" data-testid="sku-dialog-safety-stock" type="number" tabindex="-1" />
      <input v-model="draft.defaultSku" data-testid="sku-dialog-default" type="checkbox" :disabled="draft.status === 'disabled'" tabindex="-1" />
    </div>

    <template #footer>
      <button data-testid="sku-dialog-cancel" type="button" class="h-10 w-24 rounded-lg border border-[#dbe4f1] bg-white text-sm font-medium text-[#25314d]" @click="$emit('cancel')">取消</button>
      <button data-testid="sku-dialog-save" type="button" class="h-10 w-28 rounded-lg bg-[#536dff] text-sm font-medium text-white hover:bg-[#465eea]" @click="save">保存 SKU</button>
    </template>
  </AccessibleDialog>
</template>

<style scoped>
.sku-dialog-field { display: flex; min-width: 0; flex-direction: column; gap: 6px; color: #25314d; font-size: 12px; font-weight: 500; line-height: 18px; }
.sku-dialog-field em { color: #ef476f; font-style: normal; }
.sku-dialog-field input,
.sku-dialog-field select { height: 42px; width: 100%; border: 1px solid #dbe4f1; border-radius: 6px; background: #fff; padding: 0 12px; color: #25314d; font-size: 14px; font-weight: 400; outline: none; }
.sku-dialog-field .unit-input { position: relative; display: block; min-width: 0; }
.sku-dialog-field .unit-input input { padding-right: 52px; }
.sku-dialog-field .unit-suffix { position: absolute; right: 1px; top: 50%; display: flex; height: 24px; min-width: 42px; transform: translateY(-50%); align-items: center; justify-content: center; border-left: 1px solid #edf1f6; color: #8292ae; font-size: 12px; font-weight: 400; pointer-events: none; }
.sku-dialog-field input:focus,
.sku-dialog-field select:focus { border-color: #536dff; box-shadow: 0 0 0 2px rgb(83 109 255 / 12%); }
.sku-dialog-field small { color: #ef476f; font-size: 11px; line-height: 12px; }
</style>
