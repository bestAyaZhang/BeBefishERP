<script setup lang="ts">
import { Pencil } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { SkuForm } from '../../types';
import PackagingEditorDialog from '../PackagingEditorDialog.vue';
import {
  setPackagingMode,
  type PackagingForm,
  type PackagingMode,
  type ProductEditorState,
  updateUnifiedPackaging
} from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{ errors: Record<string, string> }>();

type NumericPackagingField = Exclude<keyof PackagingForm,
  'packagingMethod' | 'packageImageFileId' | 'cartonImageFileId'>;

const fields: Array<{ field: NumericPackagingField; label: string; testId: string; step: string }> = [
  { field: 'packageLengthCm', label: '包装长 (cm)', testId: 'package-length', step: '0.01' },
  { field: 'packageWidthCm', label: '包装宽 (cm)', testId: 'package-width', step: '0.01' },
  { field: 'packageHeightCm', label: '包装高 (cm)', testId: 'package-height', step: '0.01' },
  { field: 'packageVolumeCm3', label: '包装体积 (cm³)', testId: 'package-volume', step: '0.01' },
  { field: 'innerPackageLengthCm', label: '内包装长 (cm)', testId: 'inner-package-length', step: '0.01' },
  { field: 'innerPackageWidthCm', label: '内包装宽 (cm)', testId: 'inner-package-width', step: '0.01' },
  { field: 'innerPackageHeightCm', label: '内包装高 (cm)', testId: 'inner-package-height', step: '0.01' },
  { field: 'netWeightKg', label: '净重 (kg)', testId: 'net-weight', step: '0.001' },
  { field: 'grossWeightKg', label: '毛重 (kg)', testId: 'gross-weight', step: '0.001' },
  { field: 'gramWeightG', label: '克重 (g)', testId: 'gram-weight', step: '0.01' },
  { field: 'innerPackageWeightKg', label: '内包装重量 (kg)', testId: 'inner-package-weight', step: '0.001' },
  { field: 'cartonQuantity', label: '装箱数', testId: 'carton-quantity', step: '1' }
];

const dialogIndex = ref<number | null>(null);
const dialogSku = computed(() => dialogIndex.value === null ? undefined : state.value.skus[dialogIndex.value]);

function switchMode(mode: PackagingMode) {
  setPackagingMode(state.value, mode);
}

function updateNumber(field: NumericPackagingField, event: Event) {
  const raw = (event.target as HTMLInputElement).value;
  updateUnifiedPackaging(state.value, {
    ...state.value.unifiedPackaging,
    [field]: raw === '' ? null : Number(raw)
  });
}

function updateMethod(event: Event) {
  updateUnifiedPackaging(state.value, {
    ...state.value.unifiedPackaging,
    packagingMethod: (event.target as HTMLInputElement).value
  });
}

function saveSku(sku: SkuForm) {
  if (dialogIndex.value === null) return;
  state.value.skus.splice(dialogIndex.value, 1, sku);
  dialogIndex.value = null;
}

function formatDimensions(sku: SkuForm) {
  const values = [sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm];
  return values.some((value) => value !== null) ? values.map((value) => value ?? '--').join(' × ') : '--';
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-5">
      <div>
        <h3 class="text-sm font-black text-[#25314d]">包装维护方式</h3>
        <p class="mt-1 text-xs font-medium text-slate-500">统一模式会把包装、重量和包装图片复制到每个 SKU。</p>
      </div>
      <div class="inline-flex rounded-lg border border-slate-300 bg-slate-100 p-1" role="group" aria-label="包装维护方式">
        <button data-testid="packaging-mode-unified" type="button" class="h-9 rounded-md px-4 text-sm font-bold transition" :class="state.packagingMode === 'unified' ? 'bg-white text-[#536dff] shadow-sm' : 'text-slate-500'" @click="switchMode('unified')">统一包装</button>
        <button data-testid="packaging-mode-per-sku" type="button" class="h-9 rounded-md px-4 text-sm font-bold transition" :class="state.packagingMode === 'perSku' ? 'bg-white text-[#536dff] shadow-sm' : 'text-slate-500'" @click="switchMode('perSku')">按 SKU</button>
      </div>
    </div>

    <div v-if="state.packagingMode === 'unified'" class="grid gap-5 pt-6 sm:grid-cols-2 lg:grid-cols-4">
      <label v-for="field in fields" :key="field.field" class="space-y-2 text-sm font-bold text-slate-600">
        <span>{{ field.label }}</span>
        <input
          :data-testid="`unified-${field.testId}`"
          :value="state.unifiedPackaging[field.field] ?? ''"
          type="number"
          min="0"
          :step="field.step"
          class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15"
          @input="updateNumber(field.field, $event)"
        />
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600 sm:col-span-2 lg:col-span-4">
        <span>包装方式</span>
        <input :value="state.unifiedPackaging.packagingMethod" maxlength="100" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="例如：彩盒 + 防潮袋" @input="updateMethod" />
      </label>
    </div>

    <div v-else class="overflow-x-auto pt-6">
      <table class="w-full min-w-[760px] border-collapse text-left text-sm">
        <thead class="bg-slate-50 text-xs font-black text-slate-500">
          <tr><th class="px-4 py-3">SKU</th><th class="px-4 py-3">外包装尺寸 (cm)</th><th class="px-4 py-3">净重 / 毛重</th><th class="px-4 py-3">包装方式</th><th class="w-20 px-4 py-3 text-right">操作</th></tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" :data-testid="`packaging-row-${index}`">
            <td class="px-4 py-4 font-black text-[#25314d]">{{ sku.skuName || sku.skuCode }}</td>
            <td class="px-4 py-4 font-semibold tabular-nums text-slate-600">{{ formatDimensions(sku) }}</td>
            <td class="px-4 py-4 font-semibold tabular-nums text-slate-600">{{ sku.netWeightKg ?? '--' }} / {{ sku.grossWeightKg ?? '--' }} kg</td>
            <td class="px-4 py-4 font-semibold text-slate-600">{{ sku.packagingMethod || '--' }}</td>
            <td class="px-4 py-4 text-right">
              <button :data-testid="`edit-packaging-${index}`" type="button" class="inline-grid h-9 w-9 place-items-center rounded-lg text-slate-500 hover:bg-blue-50 hover:text-[#536dff]" :aria-label="`维护 ${sku.skuName} 包装`" @click="dialogIndex = index">
                <Pencil class="h-4 w-4" aria-hidden="true" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="Object.keys(errors).length" class="mt-5 space-y-1 rounded-lg bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">
      <p v-for="(error, field) in errors" :key="field">{{ error }}</p>
    </div>

    <PackagingEditorDialog :open="dialogIndex !== null" :sku="dialogSku" @cancel="dialogIndex = null" @save="saveSku" />
  </div>
</template>
