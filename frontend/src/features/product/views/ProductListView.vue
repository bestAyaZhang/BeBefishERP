<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category } from '../../masterdata/types';
import ProductList from '../components/ProductList.vue';
import { deduplicateCategories } from '../productCategoryTree';
import { productService } from '../productService';
import type { Product } from '../types';

const router = useRouter();
const categories = ref<Category[]>([]);
const categoryCounts = ref<Record<number, number>>({});
const allProductTotal = ref<number | null>(null);
const categoryLookupFailed = ref(false);
const categoryLoading = ref(false);
const categoryError = ref('');
let latestCategoryRequestId = 0;
let latestTotalRequestId = 0;
let pendingTotalRequest: Promise<number> | null = null;

const categoryPageSize = 100;
const maximumCategoryPages = 100;

async function listAllCategories() {
  const categoriesById = new Map<number, Category>();
  let expectedTotal = 0;
  let totalPages = 1;

  for (let page = 1; page <= maximumCategoryPages; page += 1) {
    const result = await masterdataService.listCategories({ page, size: categoryPageSize });
    const responsePageSize = result.pageSize > 0 ? result.pageSize : categoryPageSize;
    const responsePage = result.page > 0 ? result.page : page;
    expectedTotal = Math.max(expectedTotal, result.total);
    totalPages = Math.max(totalPages, responsePage, Math.max(1, Math.ceil(result.total / responsePageSize)));
    if (totalPages > maximumCategoryPages) throw new Error('分类页数超过安全上限');

    for (const category of result.records) {
      const existing = categoriesById.get(category.id);
      if (!existing) {
        categoriesById.set(category.id, category);
        continue;
      }
      const [selected] = deduplicateCategories([existing, category]);
      if (selected) categoriesById.set(category.id, selected);
    }

    if (page >= totalPages) {
      if (categoriesById.size < expectedTotal) {
        throw new Error(`分类唯一记录不足：期望 ${expectedTotal}，实际 ${categoriesById.size}`);
      }
      return deduplicateCategories([...categoriesById.values()]);
    }
  }
  throw new Error('分类页数超过安全上限');
}

function loadAllProductTotal() {
  if (allProductTotal.value !== null) return Promise.resolve(allProductTotal.value);
  if (pendingTotalRequest) return pendingTotalRequest;

  const requestId = ++latestTotalRequestId;
  const request = productService.listProducts({ page: 1, size: 1 })
    .then((result) => {
      if (requestId === latestTotalRequestId) allProductTotal.value = result.total;
      return result.total;
    })
    .finally(() => {
      if (pendingTotalRequest === request) pendingTotalRequest = null;
    });
  pendingTotalRequest = request;
  return request;
}

async function loadCategories() {
  const requestId = ++latestCategoryRequestId;
  categoryLoading.value = true;
  categoryError.value = '';

  const [categoryResult, countResult, totalResult] = await Promise.allSettled([
    listAllCategories(),
    productService.getCategoryCounts(),
    loadAllProductTotal()
  ]);
  if (requestId !== latestCategoryRequestId) return;

  const failures: string[] = [];
  if (categoryResult.status === 'fulfilled') {
    categories.value = categoryResult.value;
    categoryLookupFailed.value = false;
  } else {
    categoryLookupFailed.value = categories.value.length === 0;
    failures.push('分类');
  }

  if (countResult.status === 'fulfilled' && countResult.value && typeof countResult.value === 'object') {
    categoryCounts.value = countResult.value;
  } else {
    failures.push('商品数量');
  }

  if (totalResult.status === 'rejected') failures.push('全部商品总数');

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
  latestTotalRequestId += 1;
});
</script>

<template>
  <section data-testid="product-list-page" class="min-w-0">
    <ProductList
      :service="productService"
      :categories="categories"
      :category-counts="categoryCounts"
      :all-product-total="allProductTotal"
      :category-lookup-failed="categoryLookupFailed"
      :category-loading="categoryLoading"
      :category-error="categoryError"
      @retry-categories="loadCategories"
      @create-product="createProduct"
      @open-product="openProduct"
    />
  </section>
</template>
