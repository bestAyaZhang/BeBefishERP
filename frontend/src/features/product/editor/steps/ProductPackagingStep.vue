<script setup lang="ts">
import { computed, ref } from 'vue';
import AccessibleDialog from '../../../../components/AccessibleDialog.vue';
import type { ProductService, SkuForm } from '../../types';
import PackagingEditorDialog from '../PackagingEditorDialog.vue';
import {
  MAX_CARTON_QUANTITY,
  MAX_SAFE_DIMENSION,
  MAX_SAFE_VOLUME,
  packagingFromSku,
  setEditorImageFileId,
  setEditorImagePreview,
  setPackagingMode,
  type PackagingForm,
  type PackagingMode,
  type ProductEditorState,
  updateUnifiedPackaging
} from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{ errors: Record<string, string>; service: ProductService }>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();

type NumericPackagingField = Exclude<keyof PackagingForm, 'packagingMethod' | 'packageImageFileId' | 'cartonImageFileId'>;

const dialogIndex = ref<number | null>(null);
const unifyConfirmationOpen = ref(false);
const dialogSku = computed(() => dialogIndex.value === null ? undefined : state.value.skus[dialogIndex.value]);
const dialogPackagePreview = computed(() => dialogIndex.value === null ? '' : state.value.imagePreviews.package[dialogIndex.value] ?? '');
const dialogCartonPreview = computed(() => dialogIndex.value === null ? '' : state.value.imagePreviews.carton[dialogIndex.value] ?? '');
const primarySku = computed(() => state.value.skus.find((sku) => sku.defaultSku) ?? state.value.skus[0]);

function switchMode(mode: PackagingMode) {
  if (mode === 'unified' && state.value.packagingMode === 'perSku' && hasPackagingDifferences()) {
    unifyConfirmationOpen.value = true;
    return;
  }
  setPackagingMode(state.value, mode);
}

function hasPackagingDifferences() {
  const first = state.value.skus[0];
  if (!first) return false;
  const baseline = JSON.stringify(packagingFromSku(first));
  const packagingDiffers = state.value.skus.slice(1).some((sku) => JSON.stringify(packagingFromSku(sku)) !== baseline);
  const previewDiffers = (['package', 'carton'] as const).some((kind) => (
    state.value.imagePreviews[kind].slice(1).some((preview) => preview !== state.value.imagePreviews[kind][0])
    || state.value.imagePreviewFileIds[kind].slice(1).some((fileId) => fileId !== state.value.imagePreviewFileIds[kind][0])
  ));
  return packagingDiffers || previewDiffers;
}

function confirmUnifiedMode() {
  setPackagingMode(state.value, 'unified');
  unifyConfirmationOpen.value = false;
}

function updateNumber(field: NumericPackagingField, event: Event) {
  const raw = (event.target as HTMLInputElement).value;
  updateUnifiedPackaging(state.value, { ...state.value.unifiedPackaging, [field]: raw === '' ? null : Number(raw) });
}

function updateMethod(event: Event) {
  updateUnifiedPackaging(state.value, { ...state.value.unifiedPackaging, packagingMethod: (event.target as HTMLInputElement).value });
}

function saveSku(sku: SkuForm, packagePreview: string, cartonPreview: string) {
  if (dialogIndex.value === null) return;
  const index = dialogIndex.value;
  state.value.skus.splice(index, 1, sku);
  setEditorImageFileId(state.value, 'package', index, sku.packageImageFileId);
  setEditorImagePreview(state.value, 'package', index, packagePreview);
  setEditorImageFileId(state.value, 'carton', index, sku.cartonImageFileId);
  setEditorImagePreview(state.value, 'carton', index, cartonPreview);
  dialogIndex.value = null;
}

function dimensions(sku: SkuForm | PackagingForm | undefined, inner = false) {
  if (!sku) return '--';
  const values = inner
    ? [sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm]
    : [sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm];
  return values.some((value) => value !== null) ? `${values.map((value) => value ?? '--').join(' × ')} cm` : '--';
}

function maxFor(field: NumericPackagingField) {
  if (field === 'cartonQuantity') return MAX_CARTON_QUANTITY;
  return field === 'packageVolumeCm3' ? MAX_SAFE_VOLUME : MAX_SAFE_DIMENSION;
}
</script>

<template>
  <div class="h-[490px] overflow-hidden">
    <span class="absolute right-6 top-9 inline-flex h-7 items-center rounded-lg bg-[#f1f4ff] px-3 text-xs font-medium text-[#536dff]">
      {{ state.packagingMode === 'unified' ? `适用于全部 ${state.skus.length} 个 SKU` : `${state.skus.length} 个 SKU 单独配置` }}
    </span>

    <div class="flex h-11 items-center justify-between">
      <div class="inline-flex h-10 w-[354px] rounded-lg bg-[#f1f5f9] p-[3px]" role="group" aria-label="包装维护方式">
        <button data-testid="packaging-mode-unified" type="button" class="h-[34px] w-[172px] rounded-md text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-[#536dff]/30" :class="state.packagingMode === 'unified' ? 'bg-[#536dff] text-white' : 'text-[#64748b]'" @click="switchMode('unified')">全部 SKU 统一</button>
        <button data-testid="packaging-mode-per-sku" type="button" class="h-[34px] w-[172px] rounded-md text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-[#6d42f5]/30" :class="state.packagingMode === 'perSku' ? 'bg-[#6d42f5] text-white' : 'text-[#64748b]'" @click="switchMode('perSku')">按 SKU 单独维护</button>
      </div>
      <p class="text-xs font-normal text-[#8292ae]">{{ state.packagingMode === 'unified' ? '当前输入将同步应用于所有 SKU' : '切换回统一模式将覆盖各 SKU 差异' }}</p>
    </div>

    <div class="mt-3 flex h-[46px] items-center gap-3 rounded-lg px-4 text-xs font-normal" :class="state.packagingMode === 'unified' ? 'bg-[#f1f4ff] text-[#64748b]' : 'bg-[#f5f0ff] text-[#6d42f5]'">
      <span class="h-2 w-2 rounded-full" :class="state.packagingMode === 'unified' ? 'bg-[#536dff]' : 'bg-[#6d42f5]'"></span>
      {{ state.packagingMode === 'unified' ? '全部 SKU 使用同一套包装与重量数据；切换模式后可逐个 SKU 配置。' : '包装与重量按 SKU 保存；仓储物流仍为商品级信息。' }}
    </div>

    <div v-if="state.packagingMode === 'unified'" class="mt-4 grid grid-cols-2 gap-8">
      <section>
        <h3 class="mb-3 text-sm font-medium text-[#25314d]">包装箱规</h3>
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-4">
            <fieldset class="package-group"><legend>外箱尺寸</legend><div data-testid="unified-package-dimensions" class="dimension-control"><span class="dimension-cell"><span data-dimension-label class="dimension-label">长</span><input data-testid="unified-package-length" :value="state.unifiedPackaging.packageLengthCm ?? ''" type="number" min="0" :max="maxFor('packageLengthCm')" step="0.001" aria-label="外箱长度" @input="updateNumber('packageLengthCm', $event)" /><span data-testid="unified-package-length-unit" class="dimension-unit">cm</span></span><span data-dimension-separator class="dimension-separator">×</span><span class="dimension-cell"><span data-dimension-label class="dimension-label">宽</span><input data-testid="unified-package-width" :value="state.unifiedPackaging.packageWidthCm ?? ''" type="number" min="0" :max="maxFor('packageWidthCm')" step="0.001" aria-label="外箱宽度" @input="updateNumber('packageWidthCm', $event)" /><span class="dimension-unit">cm</span></span><span data-dimension-separator class="dimension-separator">×</span><span class="dimension-cell"><span data-dimension-label class="dimension-label">高</span><input data-testid="unified-package-height" :value="state.unifiedPackaging.packageHeightCm ?? ''" type="number" min="0" :max="maxFor('packageHeightCm')" step="0.001" aria-label="外箱高度" @input="updateNumber('packageHeightCm', $event)" /><span class="dimension-unit">cm</span></span></div></fieldset>
            <fieldset class="package-group"><legend>内盒尺寸</legend><div data-testid="unified-inner-package-dimensions" class="dimension-control"><span class="dimension-cell"><span data-dimension-label class="dimension-label">长</span><input data-testid="unified-inner-package-length" :value="state.unifiedPackaging.innerPackageLengthCm ?? ''" type="number" min="0" :max="maxFor('innerPackageLengthCm')" step="0.001" aria-label="内盒长度" @input="updateNumber('innerPackageLengthCm', $event)" /><span class="dimension-unit">cm</span></span><span data-dimension-separator class="dimension-separator">×</span><span class="dimension-cell"><span data-dimension-label class="dimension-label">宽</span><input data-testid="unified-inner-package-width" :value="state.unifiedPackaging.innerPackageWidthCm ?? ''" type="number" min="0" :max="maxFor('innerPackageWidthCm')" step="0.001" aria-label="内盒宽度" @input="updateNumber('innerPackageWidthCm', $event)" /><span class="dimension-unit">cm</span></span><span data-dimension-separator class="dimension-separator">×</span><span class="dimension-cell"><span data-dimension-label class="dimension-label">高</span><input data-testid="unified-inner-package-height" :value="state.unifiedPackaging.innerPackageHeightCm ?? ''" type="number" min="0" :max="maxFor('innerPackageHeightCm')" step="0.001" aria-label="内盒高度" @input="updateNumber('innerPackageHeightCm', $event)" /><span class="dimension-unit">cm</span></span></div></fieldset>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="package-field"><span>内盒包装</span><input :value="state.unifiedPackaging.packagingMethod" maxlength="100" placeholder="请选择包装方式" @input="updateMethod" /></label>
            <label class="package-field"><span>单箱数量</span><span class="unit-input"><input data-testid="unified-carton-quantity" :value="state.unifiedPackaging.cartonQuantity ?? ''" type="number" min="1" :max="MAX_CARTON_QUANTITY" step="1" placeholder="请输入数量" @input="updateNumber('cartonQuantity', $event)" /><span data-testid="unified-carton-quantity-unit" class="unit-suffix">{{ primarySku?.salesUnit || '件' }}</span></span></label>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="package-field"><span>单杯条码</span><input :value="primarySku?.barcode || '--'" readonly /></label>
            <label class="package-field"><span>包装单位</span><input :value="primarySku?.salesUnit || '--'" readonly /></label>
          </div>
        </div>
      </section>

      <section>
        <h3 class="mb-3 text-sm font-medium text-[#25314d]">重量信息</h3>
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-4">
            <label class="package-field"><span>克重</span><span class="unit-input"><input data-testid="unified-gram-weight" :value="state.unifiedPackaging.gramWeightG ?? ''" type="number" min="0" :max="maxFor('gramWeightG')" step="0.001" placeholder="请输入" @input="updateNumber('gramWeightG', $event)" /><span data-testid="unified-gram-weight-unit" class="unit-suffix">g</span></span></label>
            <label class="package-field"><span>内盒重量</span><span class="unit-input"><input data-testid="unified-inner-package-weight" :value="state.unifiedPackaging.innerPackageWeightKg ?? ''" type="number" min="0" :max="maxFor('innerPackageWeightKg')" step="0.001" placeholder="请输入" @input="updateNumber('innerPackageWeightKg', $event)" /><span class="unit-suffix">kg</span></span></label>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="package-field"><span>净重</span><span class="unit-input"><input data-testid="unified-net-weight" :value="state.unifiedPackaging.netWeightKg ?? ''" type="number" min="0" :max="maxFor('netWeightKg')" step="0.001" placeholder="请输入" @input="updateNumber('netWeightKg', $event)" /><span data-testid="unified-net-weight-unit" class="unit-suffix">kg</span></span></label>
            <label class="package-field"><span>毛重</span><span class="unit-input"><input data-testid="unified-gross-weight" :value="state.unifiedPackaging.grossWeightKg ?? ''" type="number" min="0" :max="maxFor('grossWeightKg')" step="0.001" placeholder="请输入" @input="updateNumber('grossWeightKg', $event)" /><span class="unit-suffix">kg</span></span></label>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="package-field"><span>计费重量</span><span class="unit-input"><input :value="state.unifiedPackaging.grossWeightKg ?? ''" placeholder="自动计算" readonly /><span class="unit-suffix">kg</span></span></label>
            <label class="package-field"><span>箱规体积</span><span class="unit-input"><input data-testid="unified-package-volume" :value="state.unifiedPackaging.packageVolumeCm3 ?? ''" type="number" min="0" :max="maxFor('packageVolumeCm3')" step="0.001" placeholder="自动计算" @input="updateNumber('packageVolumeCm3', $event)" /><span data-testid="unified-package-volume-unit" class="unit-suffix unit-suffix-wide">cm³</span></span></label>
          </div>
        </div>
      </section>
    </div>

    <div v-else class="mt-3 overflow-hidden rounded-lg border border-[#dbe4f1]">
      <table class="w-full table-fixed border-collapse text-left text-xs">
        <thead class="h-11 bg-[#f6f8fc] font-medium text-[#64748b]"><tr><th class="w-[236px] px-3">SKU 货号 / 规格</th><th class="w-[176px] px-3">外箱尺寸</th><th class="w-[176px] px-3">内盒尺寸</th><th class="w-[110px] px-3">单箱数量</th><th class="w-[104px] px-3">净重</th><th class="w-[104px] px-3">毛重</th><th class="px-3 text-center">操作</th></tr></thead>
        <tbody class="divide-y divide-[#edf1f6]">
          <tr v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" :data-testid="`packaging-row-${index}`" class="h-[104px] odd:bg-white even:bg-[#fbfcfe]">
            <td class="px-3"><p class="font-medium text-[#536dff]">{{ sku.skuCode || '保存后生成' }}</p><p class="mt-1 text-[#8292ae]">{{ sku.specificationValues.join(' / ') || sku.skuName }}</p></td>
            <td class="px-3 text-[#25314d]">{{ dimensions(sku) }}</td><td class="px-3 text-[#25314d]">{{ dimensions(sku, true) }}</td><td class="px-3 text-[#25314d]">{{ sku.cartonQuantity ?? '--' }}</td><td class="px-3 text-[#25314d]">{{ sku.netWeightKg ?? '--' }} kg</td><td class="px-3 text-[#25314d]">{{ sku.grossWeightKg ?? '--' }} kg</td>
            <td class="px-3 text-center"><button :data-testid="`edit-packaging-${index}`" type="button" class="h-[34px] w-[104px] rounded-lg bg-[#f1f4ff] text-xs font-medium text-[#536dff]" :aria-label="`维护 ${sku.skuName} 包装`" @click="dialogIndex = index">维护包装</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="Object.keys(errors).length" class="absolute bottom-2 left-6 right-6 rounded-lg bg-[#fff1f2] px-4 py-2 text-xs font-medium text-[#ef476f]">
      <p v-for="(error, field) in errors" :key="field">{{ error }}</p>
    </div>

    <PackagingEditorDialog
      :open="dialogIndex !== null"
      :sku="dialogSku"
      :service="props.service"
      :package-preview="dialogPackagePreview"
      :carton-preview="dialogCartonPreview"
      @cancel="dialogIndex = null"
      @save="saveSku"
      @update:uploading="emit('update:uploading', $event)"
    />
    <AccessibleDialog
      :open="unifyConfirmationOpen"
      title="统一所有 SKU 包装资料"
      description="继续后将使用第一个 SKU 的包装、重量和包装图片覆盖其他 SKU。"
      test-id="packaging-unify-confirm-dialog"
      body-test-id="packaging-unify-confirm-body"
      footer-test-id="packaging-unify-confirm-footer"
      close-test-id="packaging-unify-close"
      @cancel="unifyConfirmationOpen = false"
    >
      <p class="text-sm font-medium leading-6 text-[#64748b]">此操作会覆盖当前按 SKU 维护的差异，保存商品后才能再次恢复。</p>
      <template #footer>
        <button data-testid="packaging-unify-cancel" type="button" class="h-10 rounded-lg border border-[#dbe4f1] px-4 text-sm font-medium text-[#25314d]" @click="unifyConfirmationOpen = false">取消</button>
        <button data-testid="packaging-unify-confirm" type="button" class="h-10 rounded-lg bg-[#ef476f] px-4 text-sm font-medium text-white" @click="confirmUnifiedMode">确认覆盖</button>
      </template>
    </AccessibleDialog>
  </div>
</template>

<style scoped>
.package-group { min-width: 0; height: 66px; color: #64748b; font-size: 12px; line-height: 18px; }
.package-group legend { margin-bottom: 6px; }
.dimension-control { display: grid; height: 44px; grid-template-columns: minmax(0, 1fr) 14px minmax(0, 1fr) 14px minmax(0, 1fr); align-items: center; }
.dimension-cell { position: relative; display: block; min-width: 0; height: 44px; overflow: hidden; border: 1px solid #dbe4f1; border-radius: 8px; background: #fff; }
.dimension-control input { height: 100%; min-width: 0; width: 100%; border: 0; padding: 0 27px 0 25px; color: #25314d; font-size: 13px; outline: 0; }
.dimension-label { position: absolute; left: 7px; top: 50%; z-index: 1; transform: translateY(-50%); color: #8292ae; font-size: 10px; pointer-events: none; }
.dimension-unit { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); color: #94a3b8; font-size: 10px; pointer-events: none; }
.dimension-separator { display: grid; height: 44px; place-items: center; color: #8292ae; font-size: 13px; font-weight: 500; }
.package-field { display: flex; min-width: 0; height: 66px; flex-direction: column; gap: 6px; color: #64748b; font-size: 12px; line-height: 18px; }
.package-field input { height: 44px; width: 100%; border: 1px solid #dbe4f1; border-radius: 8px; background: #fff; padding: 0 12px; color: #25314d; font-size: 14px; outline: 0; }
.package-field .unit-input { position: relative; display: block; min-width: 0; }
.package-field .unit-input input { padding-right: 50px; }
.package-field .unit-suffix { position: absolute; right: 1px; top: 50%; display: flex; height: 24px; min-width: 40px; transform: translateY(-50%); align-items: center; justify-content: center; border-left: 1px solid #edf1f6; color: #8292ae; font-size: 11px; pointer-events: none; }
.package-field .unit-suffix-wide { min-width: 46px; }
.package-field input[readonly] { background: #f8fafc; color: #64748b; }
.package-field input:focus,
.dimension-cell:focus-within { border-color: #536dff; box-shadow: 0 0 0 2px rgb(83 109 255 / 12%); }
</style>
