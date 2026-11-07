<script setup lang="ts">
import { Image, Info } from 'lucide-vue-next';
import type { ProductEditorState } from '../productEditorState';

defineProps<{
  state: ProductEditorState;
  categoryName: string;
  checks: Array<{ title: string; detail: string; done: boolean }>;
  isEdit: boolean;
  valid: boolean;
}>();
const emit = defineEmits<{ 'edit-step': [index: number] }>();
</script>

<template>
  <div class="product-confirm">
    <div class="product-confirm-overview">
      <img v-if="state.imagePreviews.main" :src="state.imagePreviews.main" alt="商品主图" />
      <span v-else class="product-confirm-placeholder"><Image class="h-6 w-6" aria-hidden="true" /></span>
      <div>
        <h3>{{ state.productName.trim() || '商品名称待填写' }}</h3>
        <p>{{ state.itemNo.trim() || '货号待填写' }} · {{ categoryName || '分类待选择' }} · {{ state.skus.length }} 个 SKU</p>
      </div>
    </div>

    <ul class="product-confirm-checks">
      <li v-for="(check, index) in checks" :key="check.title">
        <div>
          <h3>{{ check.title }}</h3>
          <p>{{ check.detail }}</p>
        </div>
        <div class="product-confirm-actions">
          <span class="product-editor-badge" :class="{ 'is-complete': check.done }">{{ check.done ? '已填写' : '待补充' }}</span>
          <button type="button" class="product-editor-text-button" :aria-label="`查看${check.title}`" @click="emit('edit-step', index)">查看</button>
        </div>
      </li>
    </ul>

    <p class="product-editor-note">
      <Info class="h-4 w-4 shrink-0" aria-hidden="true" />
      <span v-if="!valid">保存前请核对必填项和格式。保存时将定位到首个需要调整的步骤。</span>
      <span v-else-if="isEdit">必填项与格式检查通过。可选资料可稍后补充。</span>
      <span v-else>必填项与格式检查通过。创建商品后可单独录入期初库存。</span>
    </p>
  </div>
</template>
