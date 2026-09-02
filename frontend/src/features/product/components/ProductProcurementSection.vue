<script setup lang="ts">
import { computed } from 'vue';
import { formatMoney, formatRecordStatus, formatText, formatWithUnit } from '../productDetailFormatting';
import type { ProductCatalogSupplierQuote, ProductSku } from '../types';

const props = defineProps<{ skus: ProductSku[] }>();

interface ProcurementRow {
  sku: ProductSku;
  quote: ProductCatalogSupplierQuote | null;
}

const rows = computed<ProcurementRow[]>(() => props.skus.flatMap<ProcurementRow>((sku) => (
  sku.supplierQuotes.length
    ? sku.supplierQuotes.map((quote) => ({ sku, quote }))
    : [{ sku, quote: null }]
)));
const primarySku = computed(() => props.skus.find((sku) => sku.defaultSku) ?? props.skus[0] ?? null);
const defaultQuote = computed(() => (
  primarySku.value?.supplierQuotes.find((quote) => quote.defaultQuote)
  ?? primarySku.value?.supplierQuotes[0]
  ?? null
));
const quoteCount = computed(() => props.skus.reduce((total, sku) => total + sku.supplierQuotes.length, 0));
</script>

<template>
  <section data-testid="product-procurement-section" class="min-h-[232px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[19px]">
    <div class="flex min-h-[26px] items-center justify-between gap-4">
      <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">采购信息</h2>
      <p class="hidden text-xs font-normal leading-[18px] text-[#94a3b8] lg:block">按 SKU 汇总供应商报价与采购状态</p>
    </div>

    <div v-if="rows.length" class="mt-[14px] grid min-h-[152px] gap-6 lg:grid-cols-[minmax(0,1fr)_1px_minmax(0,1.05fr)]">
      <div class="min-w-0">
        <h3 class="text-sm font-medium leading-[22px] text-[#25314d]">默认采购</h3>
        <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-2 xl:grid-cols-3">
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">成本价</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatMoney(primarySku?.standardCost) }}</dd>
          </div>
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">默认供应商</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(defaultQuote?.supplierName) }}</dd>
          </div>
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">供应商货号</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(defaultQuote?.supplierItemNo) }}</dd>
          </div>
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">最小采购量</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatWithUnit(defaultQuote?.minPurchaseQuantity, primarySku?.salesUnit || '件') }}</dd>
          </div>
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">默认采购价</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatMoney(defaultQuote?.purchasePrice) }}</dd>
          </div>
          <div class="min-h-12 min-w-0">
            <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">有效报价</dt>
            <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ quoteCount }}</dd>
          </div>
        </dl>
      </div>

      <div class="hidden bg-[#e2e8f0] lg:block" aria-hidden="true"></div>

      <div class="min-w-0 overflow-hidden">
        <h3 class="text-sm font-medium leading-[22px] text-[#25314d]">供应商报价</h3>
        <div class="mt-1 max-h-[126px] overflow-auto">
          <table class="w-full min-w-[470px] table-fixed text-left text-sm">
            <thead class="bg-[#f8fafc] text-xs font-normal leading-[18px] text-[#94a3b8]">
              <tr>
                <th class="h-[26px] w-[28%] px-2 font-normal">SKU货号</th>
                <th class="h-[26px] w-[28%] px-2 font-normal">供应商</th>
                <th class="h-[26px] w-[18%] px-2 text-right font-normal">采购价</th>
                <th class="h-[26px] w-[14%] px-2 font-normal">起订量</th>
                <th class="h-[26px] w-[12%] px-2 font-normal">状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-[#e2e8f0] text-[#25314d]">
              <tr v-for="(row, index) in rows" :key="row.quote?.id ?? `empty-${row.sku.id}-${index}`" class="h-8">
                <td class="truncate px-2 py-1 text-xs font-medium text-[#4f6bff]">{{ formatText(row.sku.skuCode) }}</td>
                <template v-if="row.quote">
                  <td class="truncate px-2 py-1 text-xs">{{ formatText(row.quote.supplierName) }}</td>
                  <td class="px-2 py-1 text-right text-xs tabular-nums">{{ formatMoney(row.quote.purchasePrice) }}</td>
                  <td class="px-2 py-1 text-xs tabular-nums">{{ formatText(row.quote.minPurchaseQuantity) }}</td>
                  <td class="px-2 py-1 text-xs" :class="row.quote.status === 'enabled' ? 'text-emerald-600' : 'text-[#94a3b8]'">{{ formatRecordStatus(row.quote.status) }}</td>
                </template>
                <td v-else :data-testid="`procurement-empty-${row.sku.id}`" colspan="4" class="px-2 py-1 text-center text-xs font-normal text-[#94a3b8]">暂无供应商报价</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <p v-else class="mt-3 rounded-lg border border-dashed border-[#e2e8f0] py-14 text-center text-sm font-normal text-[#94a3b8]">暂无 SKU，无可展示的采购信息</p>
  </section>
</template>
