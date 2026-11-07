<script setup lang="ts">
import { computed, ref } from 'vue';
import AccessibleDialog from '../../../../components/AccessibleDialog.vue';
import type { ProductService, SkuForm } from '../../types';
import PackagingEditorDialog from '../PackagingEditorDialog.vue';
import PackagingFieldGroups from '../PackagingFieldGroups.vue';
import {
  PACKAGING_IMAGE_KINDS,
  packagingFromSku,
  packagingImageFields,
  setEditorImageFileId,
  setEditorImagePreview,
  setPackagingMode,
  type PackagingImageKind,
  type PackagingMode,
  type ProductEditorState,
  updateUnifiedPackaging
} from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{ errors: Record<string, string>; service: ProductService; disabled?: boolean }>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();

const dialogIndex = ref<number | null>(null);
const unifyConfirmationOpen = ref(false);
const uploading = ref(false);
const dialogSku = computed(() => dialogIndex.value === null ? undefined : state.value.skus[dialogIndex.value]);
const primarySku = computed(() => state.value.skus.find((sku) => sku.defaultSku) ?? state.value.skus[0]);
const unifiedErrors = computed(() => Object.fromEntries(
  Object.entries(props.errors).filter(([field]) => field.startsWith('skus.0.'))
    .map(([field, error]) => [field.slice('skus.0.'.length), error])
));

function previewsAt(index: number) {
  return {
    product: state.value.imagePreviews.product[index] ?? '',
    package: state.value.imagePreviews.package[index] ?? '',
    carton: state.value.imagePreviews.carton[index] ?? ''
  };
}

function uploadStatus(value: boolean) {
  uploading.value = value;
  emit('update:uploading', value);
}

function switchMode(mode: PackagingMode) {
  if (uploading.value) return;
  if (mode === 'unified' && state.value.packagingMode === 'perSku' && hasPackagingDifferences()) {
    unifyConfirmationOpen.value = true;
    return;
  }
  setPackagingMode(state.value, mode);
}

function hasPackagingDifferences() {
  const [first, ...others] = state.value.skus;
  if (!first) return false;
  const initial = JSON.stringify(packagingFromSku(first));
  return others.some((sku) => JSON.stringify(packagingFromSku(sku)) !== initial)
    || PACKAGING_IMAGE_KINDS.some((kind) => (
      state.value.imagePreviews[kind].some((preview) => preview !== state.value.imagePreviews[kind][0])
      || state.value.imagePreviewFileIds[kind].some((fileId) => fileId !== state.value.imagePreviewFileIds[kind][0])
    ));
}

function confirmUnifiedMode() {
  setPackagingMode(state.value, 'unified');
  unifyConfirmationOpen.value = false;
}

function saveSku(sku: SkuForm, previews: Record<PackagingImageKind, string>) {
  if (dialogIndex.value === null) return;
  const index = dialogIndex.value;
  state.value.skus.splice(index, 1, sku);
  for (const kind of PACKAGING_IMAGE_KINDS) {
    setEditorImageFileId(state.value, kind, index, sku[packagingImageFields[kind]] ?? null);
    setEditorImagePreview(state.value, kind, index, previews[kind]);
  }
  dialogIndex.value = null;
}

function dimensions(sku: SkuForm, inner = false) {
  const values = inner
    ? [sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm]
    : [sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm];
  return values.some((value) => value !== null) ? `${values.map((value) => value ?? '--').join(' × ')} cm` : '--';
}
</script>

<template>
  <div class="min-w-0 space-y-4">
    <div v-if="Object.keys(errors).length" role="alert" class="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
      <p v-for="(error, field) in errors" :key="field">{{ error }}</p>
    </div>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="pack-mode" role="group" aria-label="包装维护方式">
        <button data-testid="packaging-mode-unified" type="button" :aria-pressed="state.packagingMode === 'unified'" :disabled="uploading" @click="switchMode('unified')">统一包装</button>
        <button data-testid="packaging-mode-per-sku" type="button" :aria-pressed="state.packagingMode === 'perSku'" :disabled="uploading" @click="switchMode('perSku')">按 SKU 维护</button>
      </div>
      <p class="text-xs text-slate-600">{{ state.packagingMode === 'unified' ? `尺寸和照片应用到 ${state.skus.length} 个 SKU` : '各 SKU 分别维护尺寸、重量和实拍图' }}</p>
    </div>

    <p v-if="!state.skus.length" class="rounded-md border border-dashed border-slate-300 px-4 py-6 text-sm text-slate-600">
      先在「SKU 规格」添加规格，再维护尺寸与照片。
    </p>
    <PackagingFieldGroups
      v-else-if="state.packagingMode === 'unified'"
      context="unified"
      :packaging="state.unifiedPackaging"
      :sales-unit="primarySku?.salesUnit || '件'"
      :previews="previewsAt(0)"
      :service="props.service"
      :errors="unifiedErrors"
      :disabled="disabled"
      @update="updateUnifiedPackaging(state, $event)"
      @update:file-id="(kind, fileId) => setEditorImageFileId(state, kind, null, fileId)"
      @update:preview="(kind, url) => setEditorImagePreview(state, kind, null, url)"
      @update:uploading="uploadStatus"
    />
    <div v-else class="max-w-full overflow-x-auto rounded-md border border-slate-200">
      <table class="min-w-[620px] w-full text-left text-sm">
        <thead class="bg-slate-50 text-xs text-slate-600">
          <tr><th>SKU 货号 / 规格</th><th>外箱尺寸</th><th>内盒尺寸</th><th>单箱数量</th><th>净重 / 毛重</th><th>操作</th></tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="(sku, index) in state.skus" :key="sku.id ?? index" :data-testid="`packaging-row-${index}`">
            <td><strong>{{ sku.skuCode || '保存后生成' }}</strong><p class="text-xs text-slate-600">{{ sku.specificationValues.join(' / ') || sku.skuName }}</p></td>
            <td>{{ dimensions(sku) }}</td>
            <td>{{ dimensions(sku, true) }}</td>
            <td>{{ sku.cartonQuantity ?? '--' }}</td>
            <td>{{ sku.netWeightKg ?? '--' }} / {{ sku.grossWeightKg ?? '--' }} kg</td>
            <td><button :data-testid="`edit-packaging-${index}`" type="button" class="min-h-9 whitespace-nowrap text-[#0968b6]" :aria-label="`维护 ${sku.skuName} 包装`" @click="dialogIndex = index">维护包装</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <PackagingEditorDialog
      :open="dialogIndex !== null"
      :sku="dialogSku"
      :service="props.service"
      :previews="previewsAt(dialogIndex ?? 0)"
      @cancel="dialogIndex = null"
      @save="saveSku"
      @update:uploading="uploadStatus"
    />
    <AccessibleDialog
      :open="unifyConfirmationOpen"
      title="统一所有 SKU 包装资料"
      description="继续后将使用第一个 SKU 的尺寸、重量和实拍图覆盖其他 SKU。"
      test-id="packaging-unify-confirm-dialog"
      body-test-id="packaging-unify-confirm-body"
      footer-test-id="packaging-unify-confirm-footer"
      close-test-id="packaging-unify-close"
      @cancel="unifyConfirmationOpen = false"
    >
      <p class="text-sm leading-6 text-slate-600">请核对第一个 SKU 的资料，再确认统一。</p>
      <template #footer>
        <button data-testid="packaging-unify-cancel" type="button" class="h-10 rounded-md border border-slate-200 px-4 text-sm" @click="unifyConfirmationOpen = false">取消</button>
        <button data-testid="packaging-unify-confirm" type="button" class="h-10 rounded-md bg-[#0968b6] px-4 text-sm text-white" @click="confirmUnifiedMode">确认覆盖</button>
      </template>
    </AccessibleDialog>
  </div>
</template>

<style scoped>
.pack-mode { display: inline-flex; max-width: 100%; flex-wrap: wrap; gap: 3px; padding: 3px; border-radius: 6px; background: #edf3f9; }
.pack-mode button { min-height: 36px; border: 1px solid transparent; border-radius: 5px; padding: 0 12px; color: #52647a; font-size: 13px; }
.pack-mode button[aria-pressed="true"] { color: #0968b6; border-color: #d3e2ef; background: #fff; }
th, td { padding: 11px 10px; vertical-align: middle; }
</style>
