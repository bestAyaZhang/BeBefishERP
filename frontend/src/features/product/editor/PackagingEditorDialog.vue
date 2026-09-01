<script setup lang="ts">
import { Save, X } from 'lucide-vue-next';
import { ref, watch } from 'vue';
import type { SkuForm } from '../types';
import {
  CARTON_QUANTITY_ERROR,
  cloneSku,
  isValidCartonQuantity,
  MAX_CARTON_QUANTITY
} from './productEditorState';

const props = defineProps<{
  open: boolean;
  sku?: SkuForm;
}>();

const emit = defineEmits<{
  cancel: [];
  save: [sku: SkuForm];
}>();

type NumericPackagingField =
  | 'packageLengthCm'
  | 'packageWidthCm'
  | 'packageHeightCm'
  | 'packageVolumeCm3'
  | 'innerPackageLengthCm'
  | 'innerPackageWidthCm'
  | 'innerPackageHeightCm'
  | 'netWeightKg'
  | 'grossWeightKg'
  | 'gramWeightG'
  | 'innerPackageWeightKg'
  | 'cartonQuantity';

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

const draft = ref<SkuForm>();
const errors = ref<Partial<Record<NumericPackagingField, string>>>({});

watch(
  () => [props.open, props.sku] as const,
  ([open]) => {
    if (!open || !props.sku) return;
    draft.value = cloneSku(props.sku);
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
    if (typeof value === 'number' && value < 0) nextErrors[field] = `${label.replace(/\s*\(.+\)$/, '')}不能小于 0`;
  }
  if (!isValidCartonQuantity(draft.value.cartonQuantity)) {
    nextErrors.cartonQuantity = CARTON_QUANTITY_ERROR;
  }
  if (
    !nextErrors.grossWeightKg
    && draft.value.netWeightKg !== null
    && draft.value.grossWeightKg !== null
    && draft.value.grossWeightKg < draft.value.netWeightKg
  ) {
    nextErrors.grossWeightKg = '毛重不能小于净重';
  }
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) return;
  emit('save', cloneSku(draft.value));
}
</script>

<template>
  <div v-if="open && draft" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-6" @mousedown.self="$emit('cancel')">
    <section
      data-testid="packaging-editor-dialog"
      class="w-[min(960px,calc(100vw-48px))] max-w-none overflow-hidden rounded-lg border border-slate-200 bg-white shadow-2xl"
      role="dialog"
      aria-modal="true"
      aria-labelledby="packaging-dialog-title"
    >
      <header class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
        <div class="min-w-0">
          <h2 id="packaging-dialog-title" class="truncate text-lg font-black text-[#25314d]">维护 {{ draft.skuName || draft.skuCode }} 包装资料</h2>
          <p class="mt-1 text-sm font-medium text-slate-500">仅保存当前 SKU，其他 SKU 的包装差异保持不变。</p>
        </div>
        <button type="button" class="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="关闭包装编辑" @click="$emit('cancel')">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </header>

      <div data-testid="packaging-dialog-body" class="max-h-[calc(100vh-180px)] overflow-y-auto px-6 py-5 pb-8">
        <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <label v-for="field in fields" :key="field.field" class="space-y-2 text-sm font-bold text-slate-600">
            <span>{{ field.label }}</span>
            <input
              :data-testid="`packaging-dialog-${field.testId}`"
              :value="draft[field.field] ?? ''"
              type="number"
              :min="field.field === 'cartonQuantity' ? '1' : '0'"
              :max="field.field === 'cartonQuantity' ? MAX_CARTON_QUANTITY : undefined"
              :step="field.step"
              class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15"
              :class="errors[field.field] ? 'border-rose-400' : 'border-slate-300'"
              @input="updateNumber(field.field, $event)"
            />
            <span v-if="errors[field.field]" :data-testid="`packaging-dialog-${field.testId}-error`" class="block text-xs text-rose-600">{{ errors[field.field] }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600 sm:col-span-2 lg:col-span-3">
            <span>包装方式</span>
            <input v-model="draft.packagingMethod" data-testid="packaging-dialog-method" maxlength="100" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="例如：彩盒 + 防潮袋" />
          </label>
          <div class="flex min-h-11 items-end text-sm font-semibold text-slate-500">包装图片在“图片资料”步骤维护。</div>
        </div>
      </div>

      <footer data-testid="packaging-dialog-footer" class="sticky bottom-0 z-10 flex justify-end gap-3 border-t border-slate-200 bg-white px-6 py-4">
        <button data-testid="packaging-dialog-cancel" type="button" class="h-10 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="$emit('cancel')">取消</button>
        <button data-testid="packaging-dialog-save" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white hover:bg-[#435be0]" @click="save">
          <Save class="h-4 w-4" aria-hidden="true" />
          保存包装资料
        </button>
      </footer>
    </section>
  </div>
</template>
