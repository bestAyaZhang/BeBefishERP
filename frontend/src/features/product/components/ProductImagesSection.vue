<script setup lang="ts">
import { computed } from 'vue';
import type { Product } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ product: Product }>();
const primarySku = computed(() => props.product.skus.find((sku) => sku.defaultSku) ?? props.product.skus[0] ?? null);
</script>

<template>
  <section data-testid="product-images-section" class="min-h-[220px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[19px] lg:h-[220px] lg:overflow-hidden">
    <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">图片资料</h2>

    <div class="mt-3 grid gap-4 md:grid-cols-3">
      <figure class="min-w-0">
        <figcaption class="mb-1.5 text-xs font-medium leading-[18px] text-[#64748b]">商品主图</figcaption>
        <ProductDetailImage
          :src="product.mainImageUrl"
          :alt="`${product.productName}商品主图`"
          test-id="product-image-main"
          size="preview"
        />
      </figure>
      <figure class="min-w-0">
        <figcaption class="mb-1.5 text-xs font-medium leading-[18px] text-[#64748b]">外箱图片</figcaption>
        <ProductDetailImage
          :src="primarySku?.cartonImageUrl"
          :alt="`${primarySku?.skuName || primarySku?.skuCode || product.productName}外箱图片`"
          :test-id="`packaging-carton-image-${primarySku?.id ?? 'empty'}`"
          size="preview"
        />
      </figure>
      <figure class="min-w-0">
        <figcaption class="mb-1.5 text-xs font-medium leading-[18px] text-[#64748b]">内盒包装图</figcaption>
        <ProductDetailImage
          :src="primarySku?.packageImageUrl"
          :alt="`${primarySku?.skuName || primarySku?.skuCode || product.productName}内盒包装图片`"
          :test-id="`packaging-package-image-${primarySku?.id ?? 'empty'}`"
          size="preview"
        />
      </figure>
    </div>
  </section>
</template>
