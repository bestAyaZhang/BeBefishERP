<script setup lang="ts">
import { formatDate, formatProductStatus } from '../productDetailFormatting';
import type { Product } from '../types';

defineProps<{ product: Product }>();
</script>

<template>
  <section data-testid="product-audit-section" class="min-h-[162px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[19px] lg:h-[162px] lg:overflow-hidden">
    <h2 class="text-[18px] font-medium leading-[26px] text-[#19233d]">审计信息</h2>
    <dl class="mt-3 grid grid-cols-1 gap-x-5 gap-y-2 sm:grid-cols-2">
      <div class="min-h-[38px] min-w-0">
        <dt class="text-xs font-normal leading-[18px] text-[#8492aa]">创建时间</dt>
        <dd data-testid="product-created-at" class="truncate text-sm font-medium leading-5 text-[#19233d]">{{ formatDate(product.createdAt) }}</dd>
      </div>
      <div class="min-h-[38px] min-w-0">
        <dt class="text-xs font-normal leading-[18px] text-[#8492aa]">更新时间</dt>
        <dd data-testid="product-updated-at" class="truncate text-sm font-medium leading-5 text-[#19233d]">{{ formatDate(product.updatedAt) }}</dd>
      </div>
      <div class="min-h-[38px] min-w-0">
        <dt class="text-xs font-normal leading-[18px] text-[#8492aa]">当前状态</dt>
        <dd class="truncate text-sm font-medium leading-5 text-[#4f6bff]">{{ formatProductStatus(product.status) }}</dd>
      </div>
      <div class="min-h-[38px] min-w-0">
        <dt class="text-xs font-normal leading-[18px] text-[#8492aa]">资料完整度</dt>
        <dd class="truncate text-sm font-medium leading-5 text-[#19233d]">{{ product.completenessPercent }}%</dd>
      </div>
    </dl>
  </section>
</template>
