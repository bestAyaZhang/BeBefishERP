<script setup lang="ts">
import { computed } from 'vue';
import type { Product } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ product: Product }>();
const primarySku = computed(() => props.product.skus.find((sku) => sku.defaultSku) ?? props.product.skus[0] ?? null);
const hasPerSkuPackagingImages = computed(() => {
  const [first, ...rest] = props.product.skus;
  if (!first) return false;
  return rest.some((sku) => (
    sku.packageImageFileId !== first.packageImageFileId
    || sku.cartonImageFileId !== first.cartonImageFileId
  ));
});
const packagingImageSkus = computed(() => (
  hasPerSkuPackagingImages.value
    ? props.product.skus
    : primarySku.value ? [primarySku.value] : []
));
</script>

<template>
  <section
    data-testid="product-images-section"
    class="min-h-[220px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[19px]"
    :class="hasPerSkuPackagingImages ? '' : 'lg:h-[220px] lg:overflow-hidden'"
  >
    <div class="flex min-h-[26px] items-center gap-2">
      <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">图片资料</h2>
      <span v-if="hasPerSkuPackagingImages" class="inline-flex h-[26px] items-center rounded-lg bg-[#eef2ff] px-2 text-xs font-medium leading-[18px] text-[#536dff]">按 SKU 展示</span>
    </div>

    <div class="mt-3 grid gap-4 md:grid-cols-3" :class="hasPerSkuPackagingImages ? 'lg:grid-cols-5' : 'lg:grid-cols-3'">
      <figure class="min-w-0">
        <figcaption class="mb-1.5 text-xs font-medium leading-[18px] text-[#64748b]">商品主图</figcaption>
        <ProductDetailImage
          :src="product.mainImageUrl"
          :alt="`${product.productName}商品主图`"
          test-id="product-image-main"
          size="preview"
        />
      </figure>
      <template v-for="sku in packagingImageSkus" :key="sku.id">
        <figure class="min-w-0">
          <figcaption class="mb-1.5 truncate text-xs font-medium leading-[18px] text-[#64748b]" :title="hasPerSkuPackagingImages ? `${sku.skuCode} · 外箱图片` : '外箱图片'">
            {{ hasPerSkuPackagingImages ? `${sku.skuCode} · 外箱图片` : '外箱图片' }}
          </figcaption>
          <ProductDetailImage
            :src="sku.cartonImageUrl"
            :alt="`${sku.skuName || sku.skuCode || product.productName}外箱图片`"
            :test-id="`packaging-carton-image-${sku.id}`"
            size="preview"
          />
        </figure>
        <figure class="min-w-0">
          <figcaption class="mb-1.5 truncate text-xs font-medium leading-[18px] text-[#64748b]" :title="hasPerSkuPackagingImages ? `${sku.skuCode} · 内盒包装图` : '内盒包装图'">
            {{ hasPerSkuPackagingImages ? `${sku.skuCode} · 内盒包装图` : '内盒包装图' }}
          </figcaption>
          <ProductDetailImage
            :src="sku.packageImageUrl"
            :alt="`${sku.skuName || sku.skuCode || product.productName}内盒包装图片`"
            :test-id="`packaging-package-image-${sku.id}`"
            size="preview"
          />
        </figure>
      </template>
    </div>
  </section>
</template>
