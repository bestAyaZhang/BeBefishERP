<script setup lang="ts">
import { computed } from 'vue';
import { formatMoney, formatText } from '../productDetailFormatting';
import type { Product } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ product: Product }>();

const category = computed(() => formatText(props.product.categoryName));
const primarySku = computed(() => props.product.skus.find((sku) => sku.defaultSku) ?? props.product.skus[0] ?? null);
const defaultQuote = computed(() => (
  primarySku.value?.supplierQuotes.find((quote) => quote.defaultQuote)
  ?? primarySku.value?.supplierQuotes[0]
  ?? null
));
const specification = computed(() => {
  if (primarySku.value?.specText) return primarySku.value.specText;
  if (primarySku.value?.specificationValues.length) return primarySku.value.specificationValues.join(' / ');
  return props.product.specifications.flatMap((item) => item.values).join(' / ');
});
</script>

<template>
  <section data-testid="product-overview-section" class="min-h-[280px] w-full rounded-lg border border-[#e2e8f0] bg-white p-[23px] lg:h-[280px] lg:overflow-hidden">
    <div class="grid gap-6 lg:grid-cols-[232px_minmax(0,1fr)]">
      <figure class="h-[232px] min-w-0 self-start overflow-hidden">
        <ProductDetailImage
          :src="product.mainImageUrl"
          :alt="`${product.productName}商品主图`"
          test-id="product-overview-main-image"
          size="large"
        />
        <figcaption class="mt-2 truncate text-xs font-normal leading-[18px] text-[#94a3b8]">商品主图 · {{ formatText(product.itemNo) }}</figcaption>
      </figure>

      <div class="h-auto min-w-0 lg:h-[232px]">
        <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">商品概览</h2>
        <dl class="mt-3 grid min-w-0 grid-cols-1 gap-x-4 gap-y-[10px] sm:grid-cols-2 xl:grid-cols-3">
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">货号</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.itemNo) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">SKU</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(primarySku?.skuCode) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">SPU</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.productCode) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">规格</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(specification) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">分类</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ category }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">品牌</dt>
          <dd data-testid="product-brand" class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.brand) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">供应商</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.defaultSupplierName || defaultQuote?.supplierName) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">渠道商</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">--</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">单价</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatMoney(product.defaultSalePrice ?? primarySku?.defaultSalePrice) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">库存</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.totalStock) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">安全库存</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(product.totalSafetyStock) }}</dd>
        </div>
        <div class="min-h-[42px] min-w-0">
          <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">单杯条码</dt>
          <dd class="truncate text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(primarySku?.barcode) }}</dd>
        </div>
        </dl>
      </div>
    </div>
  </section>
</template>
