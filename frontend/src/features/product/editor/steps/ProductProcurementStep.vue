<script setup lang="ts">
import { Plus } from 'lucide-vue-next';
import { computed } from 'vue';
import type { Supplier } from '../../../masterdata/types';
import { MAX_SAFE_MONEY, reconcileQuoteDefaults, setDefaultQuote, type ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{
  suppliers: Supplier[];
  errors: Record<string, string>;
}>();

const primarySkuIndex = computed(() => {
  const index = state.value.skus.findIndex((sku) => sku.defaultSku);
  return index >= 0 ? index : 0;
});
const primarySku = computed(() => state.value.skus[primarySkuIndex.value]);
const primaryQuote = computed(() => primarySku.value?.supplierQuotes?.find((quote) => quote.defaultQuote) ?? primarySku.value?.supplierQuotes?.[0]);
const quoteRows = computed(() => state.value.skus.flatMap((sku, skuIndex) => (sku.supplierQuotes ?? []).map((quote, quoteIndex) => ({ sku, skuIndex, quote, quoteIndex }))));

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
    minPurchaseQuantity: 1,
    defaultQuote: !quotes.some((quote) => quote.defaultQuote && quote.status === 'enabled'),
    status: 'enabled'
  });
  const sku = state.value.skus[skuIndex];
  if (sku) sku.supplierQuotes = reconcileQuoteDefaults(quotes);
}

function makeDefault(skuIndex: number, quoteIndex: number) {
  const sku = state.value.skus[skuIndex];
  if (sku) sku.supplierQuotes = setDefaultQuote(quotesFor(skuIndex), quoteIndex);
}

function changeStatus(skuIndex: number, quoteIndex: number, event: Event) {
  const sku = state.value.skus[skuIndex];
  const quote = sku?.supplierQuotes?.[quoteIndex];
  if (!sku || !quote) return;
  quote.status = (event.target as HTMLSelectElement).value as 'enabled' | 'disabled';
  sku.supplierQuotes = reconcileQuoteDefaults(sku.supplierQuotes ?? []);
}

function removeQuote(skuIndex: number, quoteIndex: number) {
  const sku = state.value.skus[skuIndex];
  if (!sku) return;
  const quotes = quotesFor(skuIndex);
  quotes.splice(quoteIndex, 1);
  sku.supplierQuotes = reconcileQuoteDefaults(quotes);
}

function fieldError(skuIndex: number, quoteIndex: number, field: string) {
  return props.errors[`skus.${skuIndex}.supplierQuotes.${quoteIndex}.${field}`] ?? '';
}
</script>

<template>
  <div class="h-[490px] overflow-hidden">
    <section :data-testid="`procurement-sku-${primarySkuIndex}`">
      <span class="sr-only">{{ primarySku?.skuName }}</span>
      <h3 class="mb-3 text-sm font-medium text-[#25314d]">采购信息</h3>
      <div class="grid grid-cols-3 gap-x-4 gap-y-3">
        <label class="procurement-field">
          <span>默认供应商</span>
          <select v-if="primaryQuote" v-model.number="primaryQuote.supplierId">
            <option :value="0" disabled>请选择供应商</option>
            <option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">{{ supplier.supplierName }}{{ supplier.status === 'disabled' ? '（已停用）' : '' }}</option>
          </select>
          <input v-else value="请先新增供应商报价" readonly />
        </label>
        <label class="procurement-field"><span>供应商货号</span><input v-if="primaryQuote" v-model="primaryQuote.supplierItemNo" maxlength="100" /><input v-else value="--" readonly /></label>
        <label class="procurement-field"><span>成本价</span><span class="unit-input"><input v-if="primaryQuote" v-model.number="primaryQuote.purchasePrice" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" /><input v-else value="--" readonly /><span data-testid="primary-quote-price-unit" class="unit-suffix">元</span></span></label>
        <label class="procurement-field"><span>最小采购量</span><span class="unit-input"><input v-if="primaryQuote" v-model.number="primaryQuote.minPurchaseQuantity" type="number" min="0.0001" :max="MAX_SAFE_MONEY" step="0.0001" /><input v-else value="--" readonly /><span data-testid="primary-quote-min-quantity-unit" class="unit-suffix">{{ primarySku?.salesUnit || '件' }}</span></span></label>
        <label class="procurement-field"><span>采购周期</span><input value="7 天" readonly /></label>
        <label class="procurement-field"><span>含税税率</span><input value="13%" readonly /></label>
      </div>
    </section>

    <div class="my-3 border-t border-[#dbe4f1]"></div>

    <section>
      <div class="mb-3 flex h-10 items-center justify-between">
        <h3 class="text-sm font-medium text-[#25314d]">供应商报价</h3>
        <button :data-testid="`add-quote-${primarySkuIndex}`" type="button" :disabled="!primarySku" class="inline-flex h-9 items-center gap-2 rounded-lg border border-[#b8c9e3] bg-white px-3 text-xs font-medium text-[#536dff] disabled:cursor-not-allowed disabled:opacity-50" @click="addQuote(primarySkuIndex)">
          <Plus class="h-4 w-4" aria-hidden="true" />
          新增报价
        </button>
      </div>

      <div class="overflow-hidden rounded-lg border border-[#dbe4f1]">
        <table class="w-full table-fixed border-collapse text-left text-xs">
          <thead class="h-11 bg-[#f6f8fc] font-medium text-[#64748b]"><tr><th class="w-[180px] px-3">供应商 / SKU</th><th class="w-[180px] px-3">供应商货号</th><th class="w-[150px] px-3">采购价</th><th class="w-[150px] px-3">最小采购量</th><th class="w-[140px] px-3">状态</th><th class="px-3">操作</th></tr></thead>
          <tbody class="divide-y divide-[#edf1f6]">
            <tr v-for="row in quoteRows" :key="row.quote.id ?? `${row.skuIndex}-${row.quoteIndex}`" class="h-[58px] odd:bg-white even:bg-[#fbfcfe]">
              <td class="px-3 align-middle">
                <select :data-testid="`quote-supplier-${row.skuIndex}-${row.quoteIndex}`" v-model.number="row.quote.supplierId" class="h-9 w-full rounded-lg border bg-white px-2 text-xs outline-none" :class="fieldError(row.skuIndex, row.quoteIndex, 'supplierId') ? 'border-[#ef476f]' : 'border-[#dbe4f1]'">
                  <option :value="0" disabled>请选择供应商</option>
                  <option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">{{ supplier.supplierName }}</option>
                </select>
                <p v-if="fieldError(row.skuIndex, row.quoteIndex, 'supplierId')" :data-testid="`quote-supplier-error-${row.skuIndex}-${row.quoteIndex}`" class="mt-0.5 truncate text-[11px] text-[#ef476f]">{{ fieldError(row.skuIndex, row.quoteIndex, 'supplierId') }}</p>
              </td>
              <td class="px-3"><input v-model="row.quote.supplierItemNo" maxlength="100" class="h-9 w-full rounded-lg border border-[#dbe4f1] px-2 outline-none" /></td>
              <td class="px-3"><span class="unit-input unit-input-compact"><input :data-testid="`quote-price-${row.skuIndex}-${row.quoteIndex}`" v-model.number="row.quote.purchasePrice" type="number" min="0" :max="MAX_SAFE_MONEY" step="0.0001" class="h-9 w-full rounded-lg border px-2 outline-none" :class="fieldError(row.skuIndex, row.quoteIndex, 'purchasePrice') ? 'border-[#ef476f]' : 'border-[#dbe4f1]'" /><span :data-testid="`quote-price-unit-${row.skuIndex}-${row.quoteIndex}`" class="unit-suffix">元</span></span></td>
              <td class="px-3"><span class="unit-input unit-input-compact"><input :data-testid="`quote-min-quantity-${row.skuIndex}-${row.quoteIndex}`" v-model.number="row.quote.minPurchaseQuantity" type="number" min="0.0001" :max="MAX_SAFE_MONEY" step="0.0001" class="h-9 w-full rounded-lg border px-2 outline-none" :class="fieldError(row.skuIndex, row.quoteIndex, 'minPurchaseQuantity') ? 'border-[#ef476f]' : 'border-[#dbe4f1]'" /><span :data-testid="`quote-min-quantity-unit-${row.skuIndex}-${row.quoteIndex}`" class="unit-suffix">{{ row.sku.salesUnit || '件' }}</span></span></td>
              <td class="px-3">
                <select :data-testid="`quote-status-${row.skuIndex}-${row.quoteIndex}`" :value="row.quote.status" class="h-9 w-full rounded-lg border border-[#dbe4f1] bg-white px-2 outline-none" @change="changeStatus(row.skuIndex, row.quoteIndex, $event)"><option value="enabled">启用</option><option value="disabled">停用</option></select>
              </td>
              <td class="px-3">
                <div class="flex items-center gap-3 font-medium">
                  <label class="inline-flex items-center gap-1 text-[#536dff]"><input :data-testid="`quote-default-${row.skuIndex}-${row.quoteIndex}`" :checked="row.quote.defaultQuote" :disabled="row.quote.status !== 'enabled'" type="radio" :name="`default-quote-${row.skuIndex}`" class="h-3.5 w-3.5 accent-[#536dff]" :aria-label="`设为 ${row.sku.skuName} 默认报价`" @change="makeDefault(row.skuIndex, row.quoteIndex)" />默认</label>
                  <button :data-testid="`remove-quote-${row.skuIndex}-${row.quoteIndex}`" type="button" class="text-[#ef476f]" aria-label="删除报价" @click="removeQuote(row.skuIndex, row.quoteIndex)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="quoteRows.length === 0" class="h-[116px]"><td colspan="6" class="text-center text-sm text-[#8292ae]">当前暂无供应商报价。</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.procurement-field { display: flex; height: 66px; min-width: 0; flex-direction: column; gap: 6px; color: #64748b; font-size: 12px; line-height: 18px; }
.procurement-field > input,
.procurement-field > select { height: 44px; width: 100%; border: 1px solid #dbe4f1; border-radius: 8px; background: #fff; padding: 0 12px; color: #25314d; font-size: 14px; line-height: 22px; outline: none; }
.procurement-field > input[readonly] { background: #f8fafc; color: #64748b; }
.unit-input { position: relative; display: block; min-width: 0; }
.unit-input > input { height: 44px; width: 100%; border: 1px solid #dbe4f1; border-radius: 8px; background: #fff; padding: 0 50px 0 12px; color: #25314d; font-size: 14px; line-height: 22px; outline: none; }
.unit-input > input[readonly] { background: #f8fafc; color: #64748b; }
.unit-input-compact > input { height: 36px; padding-left: 8px; padding-right: 42px; font-size: 12px; }
.unit-suffix { position: absolute; right: 1px; top: 50%; display: flex; height: 22px; min-width: 38px; transform: translateY(-50%); align-items: center; justify-content: center; border-left: 1px solid #edf1f6; color: #8292ae; font-size: 11px; font-weight: 400; pointer-events: none; }
.procurement-field > input:focus,
.procurement-field > select:focus,
.unit-input > input:focus { border-color: #536dff; box-shadow: 0 0 0 2px rgb(83 109 255 / 12%); }
</style>
