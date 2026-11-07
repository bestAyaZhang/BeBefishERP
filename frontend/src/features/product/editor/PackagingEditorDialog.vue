<script setup lang="ts">
import { ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import type { ProductService, SkuForm } from '../types';
import PackagingFieldGroups from './PackagingFieldGroups.vue';
import {
  CARTON_QUANTITY_ERROR,
  cloneSku,
  isValidCartonQuantity,
  isValidDecimal,
  packagingImageFields,
  type PackagingImageKind
} from './productEditorState';

const props = defineProps<{
  open: boolean;
  sku?: SkuForm;
  service: ProductService;
  previews: Record<PackagingImageKind, string>;
}>();
const emit = defineEmits<{
  cancel: [];
  save: [sku: SkuForm, previews: Record<PackagingImageKind, string>];
  'update:uploading': [value: boolean];
}>();

const measurements = {
  packageLengthCm: '外箱长', packageWidthCm: '外箱宽', packageHeightCm: '外箱高', packageVolumeCm3: '包装体积',
  innerPackageLengthCm: '内盒长', innerPackageWidthCm: '内盒宽', innerPackageHeightCm: '内盒高',
  productLengthCm: '产品长', productWidthCm: '产品宽', productMouthDiameterCm: '口径', productHeightCm: '产品高',
  capacityMl: '容量', netWeightKg: '净重', grossWeightKg: '毛重', gramWeightG: '克重', innerPackageWeightKg: '内包装重量'
} as const;

const draft = ref<SkuForm>();
const previews = ref<Record<PackagingImageKind, string>>({ product: '', package: '', carton: '' });
const errors = ref<Record<string, string>>({});
const uploading = ref(false);

watch(() => props.open, (open) => {
  if (!open || !props.sku) return;
  draft.value = cloneSku(props.sku);
  previews.value = { ...props.previews };
  errors.value = {};
}, { immediate: true });

function uploadStatus(value: boolean) {
  uploading.value = value;
  emit('update:uploading', value);
}

function setPhotoId(kind: PackagingImageKind, fileId: number | null) {
  if (draft.value) draft.value[packagingImageFields[kind]] = fileId;
}

function save() {
  const sku = draft.value;
  if (!sku || uploading.value) return;
  const nextErrors: Record<string, string> = {};
  for (const key of Object.keys(measurements) as Array<keyof typeof measurements>) {
    const value = sku[key];
    if (typeof value !== 'number') continue;
    const label = measurements[key];
    const digits = key === 'packageVolumeCm3' ? 15 : 9;
    if (value < 0) nextErrors[key] = `${label}不能小于 0`;
    else if (!isValidDecimal(value, digits, 3)) nextErrors[key] = `${label}最多允许 ${digits} 位整数和 3 位小数`;
  }
  if (!isValidCartonQuantity(sku.cartonQuantity)) nextErrors.cartonQuantity = CARTON_QUANTITY_ERROR;
  if (!nextErrors.grossWeightKg && sku.netWeightKg !== null && sku.grossWeightKg !== null && sku.grossWeightKg < sku.netWeightKg) {
    nextErrors.grossWeightKg = '毛重不能小于净重';
  }
  errors.value = nextErrors;
  if (!Object.keys(nextErrors).length) emit('save', cloneSku(sku), { ...previews.value });
}

function cancel() {
  if (!uploading.value) emit('cancel');
}
</script>

<template>
  <AccessibleDialog
    v-if="draft"
    :open="open"
    title="维护 SKU 尺寸包装"
    :description="draft.skuCode || draft.skuName"
    test-id="packaging-editor-dialog"
    body-test-id="packaging-dialog-body"
    footer-test-id="packaging-dialog-footer"
    close-test-id="packaging-dialog-close"
    overlay-class="items-center justify-center p-3 sm:p-6"
    panel-class="w-[min(820px,100%)]"
    header-class="px-4 py-3 sm:px-6"
    body-class="max-h-[min(65dvh,640px)] overflow-y-auto px-4 py-4 sm:px-6"
    footer-class="px-4 py-3 sm:px-6"
    @cancel="cancel"
  >
    <p v-if="Object.keys(errors).length" role="alert" class="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">请核对尺寸、数量和重量字段。</p>
    <PackagingFieldGroups
      context="dialog"
      :packaging="draft"
      :sales-unit="draft.salesUnit || '件'"
      :previews="previews"
      :service="service"
      :errors="errors"
      @update="draft = { ...draft, ...$event }"
      @update:file-id="setPhotoId"
      @update:preview="(kind, url) => previews[kind] = url"
      @update:uploading="uploadStatus"
    />
    <template #footer>
      <button data-testid="packaging-dialog-cancel" type="button" :disabled="uploading" class="h-10 rounded-md border border-slate-200 px-4 text-sm disabled:opacity-60" @click="cancel">取消</button>
      <button data-testid="packaging-dialog-save" type="button" :disabled="uploading" class="h-10 rounded-md bg-[#0968b6] px-4 text-sm text-white disabled:opacity-60" @click="save">保存包装信息</button>
    </template>
  </AccessibleDialog>
</template>
