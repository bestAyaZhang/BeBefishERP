<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category } from '../../masterdata/types';
import ProductDetailDrawer from '../components/ProductDetailDrawer.vue';
import ProductForm from '../components/ProductForm.vue';
import { productService } from '../productService';
import type { Product } from '../types';

const route = useRoute();
const router = useRouter();
const product = ref<Product | null>(null);
const categories = ref<Category[]>([]);
const loading = ref(true);
const editing = ref(false);
const errorMessage = ref('');
const DETAIL_REQUEST_TIMEOUT_MS = 2500;

async function getProductWithTimeout(id: number) {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      productService.getProduct(id),
      new Promise<never>((_, reject) => {
        timeoutId = setTimeout(() => reject(new Error('产品详情请求超时')), DETAIL_REQUEST_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
}

async function loadProductFromList(id: number) {
  const page = await productService.listProducts({ page: 1, size: 100 });
  const matchedProduct = page.records.find((record) => record.id === id);
  if (!matchedProduct) throw new Error('产品不存在');
  return matchedProduct;
}

async function loadProduct() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const id = Number(route.params.id);
    if (!Number.isInteger(id)) throw new Error('产品编号无效');
    try {
      product.value = await getProductWithTimeout(id);
    } catch (detailError) {
      product.value = await loadProductFromList(id);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '产品详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function startEdit() {
  if (categories.value.length === 0) {
    try {
      const page = await masterdataService.listCategories({ page: 1, size: 100, status: 'enabled' });
      categories.value = page.records;
    } catch {
      categories.value = [];
    }
  }
  editing.value = true;
}

function backToList() {
  void router.push({ name: 'products' });
}

function finishEdit() {
  editing.value = false;
  void loadProduct();
}

onMounted(loadProduct);
</script>

<template>
  <section data-testid="product-detail-view" class="mx-auto max-w-[1440px]">
    <div v-if="loading" class="rounded-[22px] border border-slate-200 bg-white p-8 text-sm font-bold text-slate-400">正在加载产品详情...</div>
    <div v-else-if="errorMessage" class="rounded-[22px] border border-rose-100 bg-rose-50 p-8 text-sm font-bold text-rose-500">{{ errorMessage }}</div>
    <ProductForm v-else-if="editing && product" :service="productService" :categories="categories" :initial-value="product" @saved="finishEdit" @cancel="editing = false" />
    <ProductDetailDrawer v-else-if="product" :product="product" presentation="page" @close="backToList" @edit="startEdit" />
  </section>
</template>
