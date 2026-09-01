<script setup lang="ts">
import { computed } from 'vue';
import {
  formatProductStatus,
  formatProductType,
  formatText
} from '../productDetailFormatting';
import type { Product } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ product: Product }>();

const category = computed(() => formatText(props.product.categoryName));
const completenessLabel = computed(() => (
  props.product.completenessStatus === 'complete' ? '资料完整' : '待完善'
));
</script>

<template>
  <section data-testid="product-overview-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">商品概览</h2>
      <span class="text-xs font-bold text-slate-500">{{ completenessLabel }} · {{ product.completenessPercent }}%</span>
    </div>

    <div class="grid gap-5 pt-4 lg:grid-cols-[176px_minmax(0,1fr)]">
      <figure class="min-w-0">
        <ProductDetailImage
          :src="product.mainImageUrl"
          :alt="`${product.productName}商品主图`"
          test-id="product-overview-main-image"
          size="large"
        />
        <figcaption class="mt-2 text-xs font-medium text-slate-400">商品主图</figcaption>
      </figure>

      <dl class="grid min-w-0 grid-cols-1 gap-x-6 sm:grid-cols-2 xl:grid-cols-4">
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">商品编码</dt>
          <dd class="mt-1 break-all text-sm font-bold text-[#25314d]">{{ formatText(product.productCode) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">货号</dt>
          <dd class="mt-1 break-all text-sm font-bold text-[#25314d]">{{ formatText(product.itemNo) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3 sm:col-span-2">
          <dt class="text-xs font-bold text-slate-400">名称</dt>
          <dd class="mt-1 break-words text-sm font-bold text-[#25314d]">{{ formatText(product.productName) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">分类</dt>
          <dd class="mt-1 break-words text-sm font-bold text-[#25314d]">{{ category }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">品牌</dt>
          <dd data-testid="product-brand" class="mt-1 break-words text-sm font-bold text-[#25314d]">{{ formatText(product.brand) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">类型</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatProductType(product.productType) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">状态</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatProductStatus(product.status) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3 sm:col-span-2 xl:col-span-4">
          <dt class="text-xs font-bold text-slate-400">备注</dt>
          <dd class="mt-1 whitespace-pre-wrap break-words text-sm font-medium leading-6 text-slate-600">{{ formatText(product.remark) }}</dd>
        </div>
        <div class="py-3 sm:col-span-2 xl:col-span-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <dt class="text-xs font-bold text-slate-400">资料完整度</dt>
            <dd class="text-sm font-black text-[#25314d]">{{ product.completenessPercent }}%</dd>
          </div>
          <div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
            <span class="block h-full bg-[#536dff]" :style="{ width: `${Math.min(100, Math.max(0, product.completenessPercent))}%` }"></span>
          </div>
          <p v-if="product.missingGroups.length" class="mt-2 text-xs font-medium text-slate-500">待完善：{{ product.missingGroups.join('、') }}</p>
        </div>
      </dl>
    </div>
  </section>
</template>
