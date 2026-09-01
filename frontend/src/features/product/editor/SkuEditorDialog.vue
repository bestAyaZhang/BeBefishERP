<script setup lang="ts">
import { Save, X } from 'lucide-vue-next';
import { ref, watch } from 'vue';
import type { SkuForm } from '../types';
import { cloneSku, createBlankSku } from './productEditorState';

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
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) return;
  emit('save', {
    ...cloneSku(draft.value),
    skuName: draft.value.skuName.trim(),
    barcode: draft.value.barcode.trim(),
    salesUnit: draft.value.salesUnit.trim(),
    specificationValues: specificationText.value
      .split('/')
      .map((value) => value.trim())
      .filter(Boolean)
  });
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-6" @mousedown.self="$emit('cancel')">
    <section
      data-testid="sku-editor-dialog"
      class="w-[min(960px,calc(100vw-48px))] max-w-none overflow-hidden rounded-lg border border-slate-200 bg-white shadow-2xl"
      role="dialog"
      aria-modal="true"
      aria-labelledby="sku-dialog-title"
    >
      <header class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
        <div>
          <h2 id="sku-dialog-title" class="text-lg font-black text-[#25314d]">{{ title }}</h2>
          <p class="mt-1 text-sm font-medium text-slate-500">维护 SKU 识别信息、规格、单位和成本库存数据。</p>
        </div>
        <button type="button" class="grid h-9 w-9 place-items-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="关闭 SKU 编辑" @click="$emit('cancel')">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </header>

      <div data-testid="sku-dialog-body" class="max-h-[calc(100vh-180px)] overflow-y-auto px-6 py-5 pb-8">
        <div class="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>SKU 编码</span>
            <input :value="draft.skuCode" disabled class="h-11 w-full rounded-lg border border-slate-200 bg-slate-100 px-3 text-sm font-semibold text-slate-400" placeholder="保存后自动生成" />
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>SKU 货号</span>
            <input data-testid="sku-dialog-barcode" v-model="draft.barcode" maxlength="100" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="请输入 SKU 货号" />
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
            <span>默认售价</span>
            <input data-testid="sku-dialog-sale-price" v-model.number="draft.defaultSalePrice" type="number" min="0" step="0.01" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.defaultSalePrice ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.defaultSalePrice" data-testid="sku-dialog-sale-price-error" class="block text-xs text-rose-600">{{ errors.defaultSalePrice }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>标准成本</span>
            <input data-testid="sku-dialog-standard-cost" v-model.number="draft.standardCost" type="number" min="0" step="0.01" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.standardCost ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.standardCost" class="block text-xs text-rose-600">{{ errors.standardCost }}</span>
          </label>
          <label class="space-y-2 text-sm font-bold text-slate-600">
            <span>安全库存</span>
            <input data-testid="sku-dialog-safety-stock" v-model.number="draft.safetyStockQuantity" type="number" min="0" step="0.01" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold tabular-nums text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.safetyStockQuantity ? 'border-rose-400' : 'border-slate-300'" />
            <span v-if="errors.safetyStockQuantity" class="block text-xs text-rose-600">{{ errors.safetyStockQuantity }}</span>
          </label>
        </div>
      </div>

      <footer data-testid="sku-dialog-footer" class="sticky bottom-0 z-10 flex justify-end gap-3 border-t border-slate-200 bg-white px-6 py-4">
        <button data-testid="sku-dialog-cancel" type="button" class="h-10 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="$emit('cancel')">取消</button>
        <button data-testid="sku-dialog-save" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white hover:bg-[#435be0]" @click="save">
          <Save class="h-4 w-4" aria-hidden="true" />
          保存 SKU
        </button>
      </footer>
    </section>
  </div>
</template>
