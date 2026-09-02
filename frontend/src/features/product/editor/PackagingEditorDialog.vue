<script setup lang="ts">
import { ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import type { ProductService, SkuForm } from '../types';
import ProductImageUpload from './ProductImageUpload.vue';
import { CARTON_QUANTITY_ERROR, cloneSku, isValidCartonQuantity, isValidDecimal, MAX_CARTON_QUANTITY, MAX_SAFE_DIMENSION, MAX_SAFE_VOLUME } from './productEditorState';

const props = withDefaults(defineProps<{
  open: boolean;
  sku?: SkuForm;
  service: ProductService;
  packagePreview?: string;
  cartonPreview?: string;
}>(), { packagePreview: '', cartonPreview: '' });

const emit = defineEmits<{
  cancel: [];
  save: [sku: SkuForm, packagePreview: string, cartonPreview: string];
  'update:uploading': [value: boolean];
}>();

type NumericPackagingField = 'packageLengthCm' | 'packageWidthCm' | 'packageHeightCm' | 'packageVolumeCm3' | 'innerPackageLengthCm' | 'innerPackageWidthCm' | 'innerPackageHeightCm' | 'productLengthCm' | 'productWidthCm' | 'productHeightCm' | 'capacityMl' | 'netWeightKg' | 'grossWeightKg' | 'gramWeightG' | 'innerPackageWeightKg' | 'cartonQuantity';
const fields: Array<{ field: NumericPackagingField; label: string }> = [
  { field: 'packageLengthCm', label: '外箱长' }, { field: 'packageWidthCm', label: '外箱宽' }, { field: 'packageHeightCm', label: '外箱高' },
  { field: 'packageVolumeCm3', label: '包装体积' }, { field: 'innerPackageLengthCm', label: '内盒长' }, { field: 'innerPackageWidthCm', label: '内盒宽' },
  { field: 'innerPackageHeightCm', label: '内盒高' }, { field: 'productLengthCm', label: '产品长' }, { field: 'productWidthCm', label: '产品宽' },
  { field: 'productHeightCm', label: '产品高' }, { field: 'capacityMl', label: '容量' }, { field: 'netWeightKg', label: '净重' }, { field: 'grossWeightKg', label: '毛重' },
  { field: 'gramWeightG', label: '克重' }, { field: 'innerPackageWeightKg', label: '内包装重量' }, { field: 'cartonQuantity', label: '装箱数' }
];
const packagingOptions = ['纸盒彩盒', 'PE 袋', '吸塑包装', '泡沫内衬'];
const draft = ref<SkuForm>();
const localPackagePreview = ref('');
const localCartonPreview = ref('');
const errors = ref<Partial<Record<NumericPackagingField, string>>>({});

watch(
  () => [props.open, props.sku, props.packagePreview, props.cartonPreview] as const,
  ([open]) => {
    if (!open || !props.sku) return;
    draft.value = cloneSku(props.sku);
    localPackagePreview.value = props.packagePreview;
    localCartonPreview.value = props.cartonPreview;
    errors.value = {};
  },
  { immediate: true, deep: true }
);

function updateNumber(field: NumericPackagingField, event: Event) {
  if (!draft.value) return;
  const raw = (event.target as HTMLInputElement).value;
  draft.value[field] = raw === '' ? null : Number(raw);
}

function save() {
  if (!draft.value) return;
  const nextErrors: Partial<Record<NumericPackagingField, string>> = {};
  for (const { field, label } of fields) {
    const value = draft.value[field];
    if (typeof value === 'number' && value < 0) nextErrors[field] = `${label}不能小于 0`;
    else if (typeof value === 'number' && field !== 'cartonQuantity') {
      const integerDigits = field === 'packageVolumeCm3' ? 15 : 9;
      if (!isValidDecimal(value, integerDigits, 3)) nextErrors[field] = `${label}最多允许 ${integerDigits} 位整数和 3 位小数`;
    }
  }
  if (!isValidCartonQuantity(draft.value.cartonQuantity)) nextErrors.cartonQuantity = CARTON_QUANTITY_ERROR;
  if (!nextErrors.grossWeightKg && draft.value.netWeightKg !== null && draft.value.grossWeightKg !== null && draft.value.grossWeightKg < draft.value.netWeightKg) nextErrors.grossWeightKg = '毛重不能小于净重';
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) return;
  emit('save', cloneSku(draft.value), localPackagePreview.value, localCartonPreview.value);
}

function fieldMax(field: NumericPackagingField) {
  if (field === 'cartonQuantity') return MAX_CARTON_QUANTITY;
  return field === 'packageVolumeCm3' ? MAX_SAFE_VOLUME : MAX_SAFE_DIMENSION;
}
</script>

<template>
  <AccessibleDialog
    v-if="draft"
    :open="open"
    title="维护 SKU 包装与重量"
    description="包装信息仅作用于当前 SKU"
    test-id="packaging-editor-dialog"
    body-test-id="packaging-dialog-body"
    footer-test-id="packaging-dialog-footer"
    close-test-id="packaging-dialog-close"
    overlay-class="items-start justify-center pt-32"
    panel-class="h-[748px] w-[1000px]"
    header-class="h-[82px] px-6"
    body-class="h-[594px] overflow-hidden px-6 pb-4 pt-5"
    footer-class="h-[72px] items-center px-6"
    @cancel="$emit('cancel')"
  >
    <section class="flex h-[70px] items-center justify-between rounded-lg bg-[#f6f8fc] px-4">
      <div class="flex items-center gap-3">
        <span class="grid h-12 w-12 place-items-center rounded-lg bg-[#eef2ff] text-xs font-medium text-[#536dff]">SKU</span>
        <div><p class="text-sm font-medium text-[#25314d]">{{ draft.skuCode || '保存后生成' }}</p><p class="mt-1 text-xs text-[#8292ae]">{{ draft.specificationValues.join(' / ') || draft.skuName }}</p></div>
      </div>
      <span class="inline-flex h-8 w-40 items-center justify-center rounded-lg bg-[#f1f4ff] text-xs font-medium text-[#536dff]">按 SKU 单独维护</span>
    </section>

    <div class="mt-4 grid h-[398px] grid-cols-[604px_328px] gap-4">
      <section class="h-full min-w-0 overflow-y-auto pr-2">
        <h3 class="text-sm font-medium text-[#25314d]">包装尺寸</h3>
        <fieldset class="mt-3"><legend class="mb-1.5 text-xs text-[#8292ae]">外箱尺寸</legend><div data-testid="packaging-dialog-package-dimensions" class="dimension-formula"><label class="pack-field"><span data-dimension-label>长</span><span class="pack-unit-input"><input data-testid="packaging-dialog-package-length" :value="draft.packageLengthCm ?? ''" type="number" min="0" :max="fieldMax('packageLengthCm')" step="0.001" @input="updateNumber('packageLengthCm', $event)" /><span data-testid="packaging-dialog-package-length-unit" class="pack-unit-suffix">cm</span></span></label><span data-dimension-separator class="dialog-dimension-separator">×</span><label class="pack-field"><span data-dimension-label>宽</span><span class="pack-unit-input"><input data-testid="packaging-dialog-package-width" :value="draft.packageWidthCm ?? ''" type="number" min="0" :max="fieldMax('packageWidthCm')" step="0.001" @input="updateNumber('packageWidthCm', $event)" /><span class="pack-unit-suffix">cm</span></span></label><span data-dimension-separator class="dialog-dimension-separator">×</span><label class="pack-field"><span data-dimension-label>高</span><span class="pack-unit-input"><input data-testid="packaging-dialog-package-height" :value="draft.packageHeightCm ?? ''" type="number" min="0" :max="fieldMax('packageHeightCm')" step="0.001" @input="updateNumber('packageHeightCm', $event)" /><span class="pack-unit-suffix">cm</span></span></label></div></fieldset>
        <fieldset class="mt-3"><legend class="mb-1.5 text-xs text-[#8292ae]">内盒尺寸</legend><div class="dimension-formula"><label class="pack-field"><span data-dimension-label>长</span><span class="pack-unit-input"><input data-testid="packaging-dialog-inner-package-length" :value="draft.innerPackageLengthCm ?? ''" type="number" min="0" :max="fieldMax('innerPackageLengthCm')" step="0.001" @input="updateNumber('innerPackageLengthCm', $event)" /><span class="pack-unit-suffix">cm</span></span></label><span data-dimension-separator class="dialog-dimension-separator">×</span><label class="pack-field"><span data-dimension-label>宽</span><span class="pack-unit-input"><input data-testid="packaging-dialog-inner-package-width" :value="draft.innerPackageWidthCm ?? ''" type="number" min="0" :max="fieldMax('innerPackageWidthCm')" step="0.001" @input="updateNumber('innerPackageWidthCm', $event)" /><span class="pack-unit-suffix">cm</span></span></label><span data-dimension-separator class="dialog-dimension-separator">×</span><label class="pack-field"><span data-dimension-label>高</span><span class="pack-unit-input"><input data-testid="packaging-dialog-inner-package-height" :value="draft.innerPackageHeightCm ?? ''" type="number" min="0" :max="fieldMax('innerPackageHeightCm')" step="0.001" @input="updateNumber('innerPackageHeightCm', $event)" /><span class="pack-unit-suffix">cm</span></span></label></div></fieldset>

        <h3 class="mt-3 text-sm font-medium text-[#25314d]">产品尺寸与容量</h3>
        <div class="mt-2 grid grid-cols-[minmax(0,2fr)_minmax(0,1fr)] gap-3">
          <fieldset><legend class="mb-1.5 text-xs text-[#8292ae]">产品尺寸</legend><div class="dimension-formula product-dimension-formula"><label class="pack-field"><span>长</span><span class="pack-unit-input"><input data-testid="packaging-dialog-product-length" :value="draft.productLengthCm ?? ''" type="number" min="0" :max="fieldMax('productLengthCm')" step="0.001" @input="updateNumber('productLengthCm', $event)" /><span data-testid="packaging-dialog-product-length-unit" class="pack-unit-suffix">cm</span></span><small v-if="errors.productLengthCm" data-testid="packaging-dialog-product-length-error">{{ errors.productLengthCm }}</small></label><span data-testid="packaging-dialog-product-dimension-separator" class="dialog-dimension-separator">×</span><label class="pack-field"><span>宽</span><span class="pack-unit-input"><input data-testid="packaging-dialog-product-width" :value="draft.productWidthCm ?? ''" type="number" min="0" :max="fieldMax('productWidthCm')" step="0.001" @input="updateNumber('productWidthCm', $event)" /><span class="pack-unit-suffix">cm</span></span><small v-if="errors.productWidthCm" data-testid="packaging-dialog-product-width-error">{{ errors.productWidthCm }}</small></label><span data-testid="packaging-dialog-product-dimension-separator" class="dialog-dimension-separator">×</span><label class="pack-field"><span>高</span><span class="pack-unit-input"><input data-testid="packaging-dialog-product-height" :value="draft.productHeightCm ?? ''" type="number" min="0" :max="fieldMax('productHeightCm')" step="0.001" @input="updateNumber('productHeightCm', $event)" /><span class="pack-unit-suffix">cm</span></span><small v-if="errors.productHeightCm" data-testid="packaging-dialog-product-height-error">{{ errors.productHeightCm }}</small></label></div></fieldset>
          <label class="pack-field"><span>容量</span><span class="pack-unit-input"><input data-testid="packaging-dialog-capacity" :value="draft.capacityMl ?? ''" type="number" min="0" :max="fieldMax('capacityMl')" step="0.001" @input="updateNumber('capacityMl', $event)" /><span data-testid="packaging-dialog-capacity-unit" class="pack-unit-suffix">ml</span></span><small v-if="errors.capacityMl" data-testid="packaging-dialog-capacity-error">{{ errors.capacityMl }}</small></label>
        </div>

        <h3 class="mt-3 text-sm font-medium text-[#25314d]">包装配置与重量</h3>
        <div class="mt-2 grid grid-cols-3 gap-3">
          <label class="pack-field"><span>单箱数量</span><span class="pack-unit-input"><input data-testid="packaging-dialog-carton-quantity" :value="draft.cartonQuantity ?? ''" type="number" min="1" :max="MAX_CARTON_QUANTITY" step="1" @input="updateNumber('cartonQuantity', $event)" /><span data-testid="packaging-dialog-carton-quantity-unit" class="pack-unit-suffix">{{ draft.salesUnit || '件' }}</span></span><small v-if="errors.cartonQuantity" data-testid="packaging-dialog-carton-quantity-error">{{ errors.cartonQuantity }}</small></label>
          <label class="pack-field"><span>内盒包装</span><select v-model="draft.packagingMethod" data-testid="packaging-dialog-method"><option value="">请选择</option><option v-if="draft.packagingMethod && !packagingOptions.includes(draft.packagingMethod)" :value="draft.packagingMethod">{{ draft.packagingMethod }}</option><option v-for="option in packagingOptions" :key="option" :value="option">{{ option }}</option></select></label>
          <label class="pack-field"><span>克重</span><span class="pack-unit-input"><input data-testid="packaging-dialog-gram-weight" :value="draft.gramWeightG ?? ''" type="number" min="0" :max="fieldMax('gramWeightG')" step="0.001" @input="updateNumber('gramWeightG', $event)" /><span data-testid="packaging-dialog-gram-weight-unit" class="pack-unit-suffix">g</span></span></label>
        </div>
        <div class="mt-3 grid grid-cols-2 gap-4"><label class="pack-field"><span>净重</span><span class="pack-unit-input"><input data-testid="packaging-dialog-net-weight" :value="draft.netWeightKg ?? ''" type="number" min="0" :max="fieldMax('netWeightKg')" step="0.001" @input="updateNumber('netWeightKg', $event)" /><span data-testid="packaging-dialog-net-weight-unit" class="pack-unit-suffix">kg</span></span></label><label class="pack-field"><span>毛重</span><span class="pack-unit-input"><input data-testid="packaging-dialog-gross-weight" :value="draft.grossWeightKg ?? ''" type="number" min="0" :max="fieldMax('grossWeightKg')" step="0.001" @input="updateNumber('grossWeightKg', $event)" /><span class="pack-unit-suffix">kg</span></span><small v-if="errors.grossWeightKg">{{ errors.grossWeightKg }}</small></label></div>
      </section>

      <section><h3 class="text-sm font-medium text-[#25314d]">包装图片</h3><div class="mt-3 space-y-2.5"><ProductImageUpload :file-id="draft.cartonImageFileId" :preview="localCartonPreview" :service="service" label="外箱图片" test-id="packaging-dialog-carton-image" variant="dialog-packaging" @update:file-id="draft.cartonImageFileId = $event" @update:preview="localCartonPreview = $event" @update:uploading="$emit('update:uploading', $event)" /><ProductImageUpload :file-id="draft.packageImageFileId" :preview="localPackagePreview" :service="service" label="内盒包装图" test-id="packaging-dialog-package-image" variant="dialog-packaging" @update:file-id="draft.packageImageFileId = $event" @update:preview="localPackagePreview = $event" @update:uploading="$emit('update:uploading', $event)" /></div></section>
    </div>

    <div class="mt-4 flex h-[58px] items-center gap-3 rounded-lg bg-[#f1f4ff] px-4 text-xs text-[#536dff]"><span class="text-base">i</span>保存后仅更新 {{ draft.skuCode || draft.skuName }}；切换为统一模式时会覆盖 SKU 差异。</div>
    <div class="sr-only" aria-hidden="true"><input data-testid="packaging-dialog-package-volume" :value="draft.packageVolumeCm3 ?? ''" type="number" tabindex="-1" @input="updateNumber('packageVolumeCm3', $event)" /><input data-testid="packaging-dialog-inner-package-weight" :value="draft.innerPackageWeightKg ?? ''" type="number" tabindex="-1" @input="updateNumber('innerPackageWeightKg', $event)" /></div>

    <template #footer><button data-testid="packaging-dialog-cancel" type="button" class="h-10 w-24 rounded-lg border border-[#dbe4f1] bg-white text-sm font-medium text-[#25314d]" @click="$emit('cancel')">取消</button><button data-testid="packaging-dialog-save" type="button" class="h-10 w-[132px] rounded-lg bg-[#536dff] text-sm font-medium text-white hover:bg-[#465eea]" @click="save">保存包装信息</button></template>
  </AccessibleDialog>
</template>

<style scoped>
.pack-field { display: flex; min-width: 0; flex-direction: column; gap: 4px; color: #8292ae; font-size: 11px; line-height: 16px; }
.pack-field input,
.pack-field select { height: 40px; width: 100%; border: 1px solid #dbe4f1; border-radius: 6px; background: #fff; padding: 0 12px; color: #25314d; font-size: 13px; outline: none; }
.pack-unit-input { position: relative; display: block; min-width: 0; }
.pack-field .pack-unit-input input { padding-right: 48px; }
.pack-unit-suffix { position: absolute; right: 1px; top: 50%; display: flex; height: 22px; min-width: 38px; transform: translateY(-50%); align-items: center; justify-content: center; border-left: 1px solid #edf1f6; color: #8292ae; font-size: 10px; pointer-events: none; }
.dimension-formula { display: grid; grid-template-columns: minmax(0, 1fr) 18px minmax(0, 1fr) 18px minmax(0, 1fr); align-items: end; gap: 4px; }
.product-dimension-formula { align-items: start; }
.product-dimension-formula .dialog-dimension-separator { margin-top: 20px; }
.dialog-dimension-separator { display: grid; height: 40px; place-items: center; color: #8292ae; font-size: 14px; font-weight: 500; }
.pack-field input:focus,
.pack-field select:focus { border-color: #536dff; box-shadow: 0 0 0 2px rgb(83 109 255 / 12%); }
.pack-field small { color: #ef476f; font-size: 10px; line-height: 12px; }
</style>
