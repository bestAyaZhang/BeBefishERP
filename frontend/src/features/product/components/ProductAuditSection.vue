<script setup lang="ts">
import { formatDate, formatProductStatus, formatText, formatWithUnit } from '../productDetailFormatting';
import type { Product } from '../types';

defineProps<{ product: Product }>();
</script>

<template>
  <section id="product-audit" data-testid="product-audit-section" class="pd-audit">
    <h2>审计信息</h2>
    <dl>
      <div><dt>创建时间</dt><dd data-testid="product-created-at">{{ formatDate(product.createdAt) }}</dd></div>
      <div><dt>更新时间</dt><dd data-testid="product-updated-at">{{ formatDate(product.updatedAt) }}</dd></div>
      <div><dt>当前状态</dt><dd>{{ formatProductStatus(product.status) }}</dd></div>
      <div><dt>资料完整度</dt><dd>{{ formatWithUnit(product.completenessPercent, '%') }}</dd></div>
    </dl>
    <p v-if="product.remark" class="pd-remark">备注：{{ formatText(product.remark) }}</p>
  </section>
</template>
