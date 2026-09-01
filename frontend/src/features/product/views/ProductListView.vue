<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category } from '../../masterdata/types';
import ProductList from '../components/ProductList.vue';
import { productService } from '../productService';
import type { Product } from '../types';

const router = useRouter();
const categories = ref<Category[]>([]);
const categoryCounts = ref<Record<number, number>>({});
const categoryLoading = ref(false);
const categoryError = ref('');
let latestCategoryRequestId = 0;

async function loadCategories() {
  const requestId = ++latestCategoryRequestId;
  categoryLoading.value = true;
  categoryError.value = '';

  const [categoryResult, countResult] = await Promise.allSettled([
    masterdataService.listCategories({ page: 1, size: 100, status: 'enabled' }),
    productService.getCategoryCounts()
  ]);
  if (requestId !== latestCategoryRequestId) return;

  const failures: string[] = [];
  if (categoryResult.status === 'fulfilled') categories.value = categoryResult.value.records;
  else failures.push('分类');

  if (countResult.status === 'fulfilled' && countResult.value && typeof countResult.value === 'object') {
    categoryCounts.value = countResult.value;
  } else {
    failures.push('商品数量');
  }

  if (failures.length > 0) categoryError.value = `${failures.join('和')}加载失败，可单独重试。`;
  categoryLoading.value = false;
}

function createProduct() {
  void router.push({ name: 'product-new' });
}

function openProduct(product: Product) {
  void router.push({ name: 'product-detail', params: { id: product.id } });
}

onMounted(() => {
  void loadCategories();
});

onBeforeUnmount(() => {
  latestCategoryRequestId += 1;
});
</script>

<template>
  <section data-testid="product-list-page" class="min-w-0">
    <ProductList
      :service="productService"
      :categories="categories"
      :category-counts="categoryCounts"
      :category-loading="categoryLoading"
      :category-error="categoryError"
      @retry-categories="loadCategories"
      @create-product="createProduct"
      @open-product="openProduct"
    />
  </section>
</template>
