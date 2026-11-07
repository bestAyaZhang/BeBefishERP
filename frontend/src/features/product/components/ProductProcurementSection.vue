<script setup lang="ts">
import { computed } from 'vue';
import { formatMoney, formatRecordStatus, formatText } from '../productDetailFormatting';
import { formatProductNumber, getDefaultSupplierQuote } from '../productPresentation';
import type { ProductCatalogSupplierQuote, ProductSku } from '../types';

const props = defineProps<{ skus: ProductSku[]; selectedSkuId: number | null }>();
interface ProcurementRow { sku: ProductSku; quote: ProductCatalogSupplierQuote | null }
const rows = computed<ProcurementRow[]>(() => props.skus.flatMap<ProcurementRow>((sku) => sku.supplierQuotes.length
  ? sku.supplierQuotes.map((quote) => ({ sku, quote })) : [{ sku, quote: null }]));
const quoteCount = computed(() => props.skus.reduce((total, sku) => total + sku.supplierQuotes.length, 0));
const enabledCount = computed(() => props.skus.reduce((total, sku) => total + sku.supplierQuotes.filter((quote) => quote.status === 'enabled').length, 0));
</script>

<template>
  <section id="product-procurement" data-testid="product-procurement-section" class="pd-panel pd-full-section">
    <div class="pd-panel-heading"><h2>采购报价 <span class="pd-heading-count">{{ quoteCount }}</span></h2><span>全部 SKU · {{ enabledCount }} 条启用报价</span></div>
    <div v-if="rows.length" class="pd-table-scroll">
      <table class="pd-data-table pd-procurement-table">
        <thead><tr><th>SKU货号 / 规格</th><th>供应商 / 供应商货号</th><th class="pd-num">采购价</th><th class="pd-num">起订量</th><th class="pd-num">标准成本</th><th>报价状态</th></tr></thead>
        <tbody>
          <tr v-for="(row, index) in rows" :key="row.quote?.id ?? `empty-${row.sku.id}-${index}`" :class="{ 'pd-selected-sku': row.sku.id === selectedSkuId }">
            <td><strong>{{ formatText(row.sku.skuCode) }}</strong><small>{{ formatText(row.sku.specText || row.sku.specificationValues.join(' / ')) }}</small></td>
            <template v-if="row.quote">
              <td>{{ formatText(row.quote.supplierName) }}<span v-if="getDefaultSupplierQuote(row.sku)?.id === row.quote.id" class="pd-tag">默认</span><small>{{ formatText(row.quote.supplierItemNo) }}</small></td>
              <td class="pd-num">{{ formatMoney(row.quote.purchasePrice) }}</td>
              <td class="pd-num">{{ formatProductNumber(row.quote.minPurchaseQuantity) }}<small>{{ formatText(row.sku.salesUnit) }}</small></td>
              <td class="pd-num">{{ formatMoney(row.sku.standardCost) }}</td>
              <td><span class="pd-status" :class="{ 'pd-status-enabled': row.quote.status === 'enabled' }"><i aria-hidden="true"></i>{{ formatRecordStatus(row.quote.status) }}</span></td>
            </template>
            <td v-else :data-testid="`procurement-empty-${row.sku.id}`" colspan="5" class="pd-muted">暂无供应商报价</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="pd-empty">暂无 SKU，无可展示的采购信息</p>
    <p class="pd-panel-footer">默认采购价仅使用当前 SKU 已启用的默认报价。报价资料通过“编辑商品”维护。</p>
  </section>
</template>
