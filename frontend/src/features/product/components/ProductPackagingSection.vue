<script setup lang="ts">
import { ChevronDown } from 'lucide-vue-next';
import { formatText } from '../productDetailFormatting';
import type { ProductSku } from '../types';
import ProductPackagingFacts from './ProductPackagingFacts.vue';

defineProps<{ sku: ProductSku }>();
</script>

<template>
  <details :data-testid="`sku-packaging-details-${sku.id}`" class="pd-sku-packaging-details">
    <summary :aria-label="`查看完整包装参数（${formatText(sku.skuCode || sku.skuName)}）`">
      <ChevronDown aria-hidden="true" />查看完整包装参数
    </summary>
    <ProductPackagingFacts :sku="sku" />
  </details>
</template>
