<script setup lang="ts">
import { formatDate, formatProductStatus } from '../productDetailFormatting';
import type { Product } from '../types';

defineProps<{ product: Product }>();
</script>

<template>
  <section data-testid="product-audit-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">审计</h2>
    </div>
    <dl class="grid grid-cols-1 gap-x-6 sm:grid-cols-3">
      <div class="border-b border-slate-100 py-3 sm:border-b-0">
        <dt class="text-xs font-bold text-slate-400">创建时间</dt>
        <dd data-testid="product-created-at" class="mt-1 text-sm font-bold text-[#25314d]">{{ formatDate(product.createdAt) }}</dd>
      </div>
      <div class="border-b border-slate-100 py-3 sm:border-b-0">
        <dt class="text-xs font-bold text-slate-400">更新时间</dt>
        <dd data-testid="product-updated-at" class="mt-1 text-sm font-bold text-[#25314d]">{{ formatDate(product.updatedAt) }}</dd>
      </div>
      <div class="py-3">
        <dt class="text-xs font-bold text-slate-400">当前状态</dt>
        <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatProductStatus(product.status) }}</dd>
      </div>
    </dl>
  </section>
</template>
