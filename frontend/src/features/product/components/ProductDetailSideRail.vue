<script setup lang="ts">
import { ChevronRight } from 'lucide-vue-next';
import { formatMoney, formatRecordStatus, formatText, formatWithUnit } from '../productDetailFormatting';
import { getDefaultSupplierQuote } from '../productPresentation';
import type { ProductSku } from '../types';

defineProps<{ sku: ProductSku | null }>();
</script>

<template>
  <aside class="pd-side-rail" aria-label="当前 SKU 资料">
    <div data-testid="selected-sku-quotes" class="pd-panel pd-quotes-summary">
      <div class="pd-panel-heading"><h2>供应商报价</h2><span>{{ sku?.supplierQuotes.length ?? 0 }} 条</span></div>
      <div v-if="sku?.supplierQuotes.length" class="pd-quote-list">
        <article v-for="quote in sku.supplierQuotes" :key="quote.id" class="pd-quote" :class="{ 'pd-quote-disabled': quote.status === 'disabled' }">
          <div class="pd-quote-main"><strong>{{ formatText(quote.supplierName) }}</strong><span v-if="getDefaultSupplierQuote(sku)?.id === quote.id" class="pd-tag">默认</span><span v-if="quote.status !== 'enabled'" class="pd-tag">{{ formatRecordStatus(quote.status) }}</span><b>{{ formatMoney(quote.purchasePrice) }}</b></div>
          <p><span>起订 {{ formatWithUnit(quote.minPurchaseQuantity, sku.salesUnit?.trim() || '') }}</span><span>{{ formatText(quote.supplierItemNo) }}</span></p>
        </article>
      </div>
      <p v-else class="pd-summary-empty">当前 SKU 暂无供应商报价</p>
      <a class="pd-summary-link" href="#product-procurement">查看全部采购报价 <ChevronRight aria-hidden="true" /></a>
    </div>
  </aside>
</template>
