<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next';
import type { Supplier } from '../../../masterdata/types';
import { setDefaultQuote, type ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{
  suppliers: Supplier[];
  errors: Record<string, string>;
}>();

function quotesFor(skuIndex: number) {
  const sku = state.value.skus[skuIndex];
  if (!sku) return [];
  if (!sku.supplierQuotes) sku.supplierQuotes = [];
  return sku.supplierQuotes;
}

function addQuote(skuIndex: number) {
  const quotes = quotesFor(skuIndex);
  quotes.push({
    supplierId: 0,
    supplierItemNo: '',
    purchasePrice: 0,
    minPurchaseQuantity: 0,
    defaultQuote: quotes.length === 0,
    status: 'enabled'
  });
}

function makeDefault(skuIndex: number, quoteIndex: number) {
  const sku = state.value.skus[skuIndex];
  if (!sku) return;
  sku.supplierQuotes = setDefaultQuote(quotesFor(skuIndex), quoteIndex);
}

function removeQuote(skuIndex: number, quoteIndex: number) {
  const sku = state.value.skus[skuIndex];
  if (!sku) return;
  const quotes = quotesFor(skuIndex);
  const removedDefault = quotes[quoteIndex]?.defaultQuote ?? false;
  quotes.splice(quoteIndex, 1);
  if (removedDefault && quotes.length > 0) sku.supplierQuotes = setDefaultQuote(quotes, 0);
}

function fieldError(skuIndex: number, quoteIndex: number, field: string) {
  return props.errors[`skus.${skuIndex}.supplierQuotes.${quoteIndex}.${field}`] ?? '';
}
</script>

<template>
  <div class="divide-y divide-slate-200 border-y border-slate-200">
    <section v-for="(sku, skuIndex) in state.skus" :key="sku.id ?? `sku-${skuIndex}`" :data-testid="`procurement-sku-${skuIndex}`" class="py-6 first:pt-0 last:pb-0">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-sm font-black text-[#25314d]">{{ sku.skuName || sku.skuCode || `SKU ${skuIndex + 1}` }}</h3>
          <p class="mt-1 text-xs font-semibold text-slate-500">{{ sku.barcode || '未填写 SKU 货号' }} · {{ sku.supplierQuotes?.length ?? 0 }} 条报价</p>
        </div>
        <button :data-testid="`add-quote-${skuIndex}`" type="button" class="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-300 px-3 text-xs font-bold text-slate-600 hover:border-[#536dff] hover:text-[#536dff]" @click="addQuote(skuIndex)">
          <Plus class="h-4 w-4" aria-hidden="true" />
          添加报价
        </button>
      </div>

      <div v-if="sku.supplierQuotes?.length" class="mt-4 overflow-x-auto">
        <table class="w-full min-w-[900px] border-collapse text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black text-slate-500">
            <tr>
              <th class="px-3 py-3">供应商</th>
              <th class="px-3 py-3">供应商货号</th>
              <th class="px-3 py-3">采购价</th>
              <th class="px-3 py-3">最小采购量</th>
              <th class="px-3 py-3">状态</th>
              <th class="px-3 py-3 text-center">默认</th>
              <th class="w-14 px-3 py-3"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="(quote, quoteIndex) in sku.supplierQuotes" :key="quote.id ?? `quote-${quoteIndex}`">
              <td class="px-3 py-3 align-top">
                <select :data-testid="`quote-supplier-${skuIndex}-${quoteIndex}`" v-model.number="quote.supplierId" class="h-10 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="fieldError(skuIndex, quoteIndex, 'supplierId') ? 'border-rose-400' : 'border-slate-300'">
                  <option :value="0" disabled>请选择供应商</option>
                  <option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">{{ supplier.supplierName }}{{ supplier.status === 'disabled' ? '（已停用）' : '' }}</option>
                </select>
                <p v-if="fieldError(skuIndex, quoteIndex, 'supplierId')" :data-testid="`quote-supplier-error-${skuIndex}-${quoteIndex}`" class="mt-1 text-xs font-bold text-rose-600">{{ fieldError(skuIndex, quoteIndex, 'supplierId') }}</p>
              </td>
              <td class="px-3 py-3"><input v-model="quote.supplierItemNo" maxlength="100" class="h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold outline-none focus:border-[#536dff]" /></td>
              <td class="px-3 py-3"><input :data-testid="`quote-price-${skuIndex}-${quoteIndex}`" v-model.number="quote.purchasePrice" type="number" min="0" step="0.01" class="h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold tabular-nums outline-none focus:border-[#536dff]" /></td>
              <td class="px-3 py-3"><input :data-testid="`quote-min-quantity-${skuIndex}-${quoteIndex}`" v-model.number="quote.minPurchaseQuantity" type="number" min="0" step="0.01" class="h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold tabular-nums outline-none focus:border-[#536dff]" /></td>
              <td class="px-3 py-3">
                <select v-model="quote.status" class="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none focus:border-[#536dff]">
                  <option value="enabled">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </td>
              <td class="px-3 py-3 text-center">
                <input :data-testid="`quote-default-${skuIndex}-${quoteIndex}`" :checked="quote.defaultQuote" type="radio" :name="`default-quote-${skuIndex}`" class="h-4 w-4 accent-[#536dff]" :aria-label="`设为 ${sku.skuName} 默认报价`" @change="makeDefault(skuIndex, quoteIndex)" />
              </td>
              <td class="px-3 py-3 text-right">
                <button :data-testid="`remove-quote-${skuIndex}-${quoteIndex}`" type="button" class="grid h-9 w-9 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600" aria-label="删除报价" @click="removeQuote(skuIndex, quoteIndex)">
                  <Trash2 class="h-4 w-4" aria-hidden="true" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="mt-4 border-t border-dashed border-slate-200 pt-4 text-sm font-medium text-slate-400">当前 SKU 暂无供应商报价。</p>
      <p v-if="errors[`skus.${skuIndex}.supplierQuotes`]" class="mt-3 text-xs font-bold text-rose-600">{{ errors[`skus.${skuIndex}.supplierQuotes`] }}</p>
    </section>
    <p v-if="state.skus.length === 0" class="py-12 text-center text-sm font-semibold text-slate-400">请先在 SKU 信息步骤添加 SKU。</p>
  </div>
</template>
