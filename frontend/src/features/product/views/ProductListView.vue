<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category } from '../../masterdata/types';
import ProductForm from '../components/ProductForm.vue';
import ProductList from '../components/ProductList.vue';
import { productService } from '../productService';
import type { Product } from '../types';

const mode = ref<'list' | 'create' | 'edit'>('list');
const listKey = ref(0);
const categories = ref<Category[]>([]);
const editingProduct = ref<Product | null>(null);
const router = useRouter();

function showList() {
  mode.value = 'list';
  editingProduct.value = null;
  listKey.value += 1;
}

function startCreate() {
  editingProduct.value = null;
  mode.value = 'create';
}

function startEdit(product: Product) {
  editingProduct.value = product;
  mode.value = 'edit';
}

async function openProductDetail(product: Product) {
  await router.push({ name: 'product-detail', params: { id: product.id } });
}

onMounted(async () => {
  try {
    const page = await masterdataService.listCategories({ page: 1, size: 100, status: 'enabled' });
    categories.value = page.records;
  } catch {
    categories.value = [];
  }
});
</script>

<template>
  <section data-testid="product-list-page" class="mx-auto max-w-[1440px]">
    <ProductList v-if="mode === 'list'" :key="listKey" :service="productService" :categories="categories" @create="startCreate" @select="openProductDetail" @edit="startEdit" />
    <div v-else class="space-y-5"><ProductForm :service="productService" :categories="categories" :initial-value="editingProduct ?? undefined" @saved="showList" @cancel="showList" /></div>
  </section>
</template>
