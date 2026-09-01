<script setup lang="ts">
import type { Product } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

defineProps<{ product: Product }>();
</script>

<template>
  <section data-testid="product-images-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">图片</h2>
    </div>

    <div class="grid gap-x-6 lg:grid-cols-2">
      <figure class="flex min-w-0 items-center gap-4 border-b border-slate-100 py-4">
        <ProductDetailImage
          :src="product.mainImageUrl"
          :alt="`${product.productName}商品主图`"
          test-id="product-image-main"
        />
        <figcaption class="min-w-0">
          <p class="text-sm font-bold text-[#25314d]">商品主图</p>
          <p class="mt-1 break-all text-xs font-medium text-slate-400">{{ product.itemNo }}</p>
        </figcaption>
      </figure>

      <template v-for="sku in product.skus" :key="sku.id">
        <figure class="flex min-w-0 items-center gap-4 border-b border-slate-100 py-4">
          <ProductDetailImage
            :src="sku.skuImageUrl"
            :alt="`${sku.skuName || sku.skuCode} SKU 图片`"
            :test-id="`product-image-sku-${sku.id}`"
          />
          <figcaption class="min-w-0">
            <p class="text-sm font-bold text-[#25314d]">SKU 图</p>
            <p class="mt-1 break-all text-xs font-medium text-slate-400">{{ sku.skuCode }}</p>
          </figcaption>
        </figure>
        <figure class="flex min-w-0 items-center gap-4 border-b border-slate-100 py-4">
          <ProductDetailImage
            :src="sku.cartonImageUrl"
            :alt="`${sku.skuName || sku.skuCode}外箱图片`"
            :test-id="`product-image-carton-${sku.id}`"
          />
          <figcaption class="min-w-0">
            <p class="text-sm font-bold text-[#25314d]">外箱图</p>
            <p class="mt-1 break-all text-xs font-medium text-slate-400">{{ sku.skuCode }}</p>
          </figcaption>
        </figure>
        <figure class="flex min-w-0 items-center gap-4 border-b border-slate-100 py-4">
          <ProductDetailImage
            :src="sku.packageImageUrl"
            :alt="`${sku.skuName || sku.skuCode}内盒包装图片`"
            :test-id="`product-image-package-${sku.id}`"
          />
          <figcaption class="min-w-0">
            <p class="text-sm font-bold text-[#25314d]">内盒包装图</p>
            <p class="mt-1 break-all text-xs font-medium text-slate-400">{{ sku.skuCode }}</p>
          </figcaption>
        </figure>
      </template>
    </div>
  </section>
</template>
