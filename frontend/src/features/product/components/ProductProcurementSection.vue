<script setup lang="ts">
import { computed } from 'vue';
import { formatMoney, formatRecordStatus, formatText } from '../productDetailFormatting';
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
</script>

<template>
  <section data-testid="product-procurement-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">采购</h2>
    </div>

    <div v-if="rows.length" class="w-full overflow-x-auto">
      <table class="min-w-[980px] table-fixed text-left text-sm">
        <thead class="border-b border-slate-200 text-xs font-bold text-slate-400">
          <tr>
            <th class="w-44 px-3 py-3">SKU 货号</th>
            <th class="w-48 px-3 py-3">供应商</th>
            <th class="w-40 px-3 py-3">供应商货号</th>
            <th class="w-28 px-3 py-3 text-right">采购价</th>
            <th class="w-28 px-3 py-3 text-right">起订量</th>
            <th class="w-24 px-3 py-3">默认报价</th>
            <th class="w-24 px-3 py-3">状态</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 text-slate-600">
          <tr v-for="(row, index) in rows" :key="row.quote?.id ?? `empty-${row.sku.id}-${index}`">
            <td class="break-all px-3 py-3 font-bold text-[#25314d]">{{ formatText(row.sku.skuCode) }}</td>
            <template v-if="row.quote">
              <td class="break-words px-3 py-3">{{ formatText(row.quote.supplierName) }}</td>
              <td class="break-all px-3 py-3">{{ formatText(row.quote.supplierItemNo) }}</td>
              <td class="px-3 py-3 text-right tabular-nums">{{ formatMoney(row.quote.purchasePrice) }}</td>
              <td class="px-3 py-3 text-right tabular-nums">{{ formatText(row.quote.minPurchaseQuantity) }}</td>
              <td class="px-3 py-3 font-bold">{{ row.quote.defaultQuote ? '是' : '否' }}</td>
              <td class="px-3 py-3 font-bold" :class="row.quote.status === 'enabled' ? 'text-emerald-600' : 'text-slate-400'">{{ formatRecordStatus(row.quote.status) }}</td>
            </template>
            <td v-else :data-testid="`procurement-empty-${row.sku.id}`" colspan="6" class="px-3 py-5 text-center text-sm font-medium text-slate-400">暂无供应商报价</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="pt-4 text-sm font-medium text-slate-400">暂无 SKU，无可展示的采购信息</p>
  </section>
</template>
