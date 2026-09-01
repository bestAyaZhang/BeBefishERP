<script setup lang="ts">
import { AlertCircle, ArrowLeft, Pencil, RefreshCw } from 'lucide-vue-next';
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ProductAuditSection from '../components/ProductAuditSection.vue';
import ProductImagesSection from '../components/ProductImagesSection.vue';
import ProductOverviewSection from '../components/ProductOverviewSection.vue';
import ProductPackagingSection from '../components/ProductPackagingSection.vue';
import ProductProcurementSection from '../components/ProductProcurementSection.vue';
import ProductSkuSection from '../components/ProductSkuSection.vue';
import { formatProductStatus } from '../productDetailFormatting';
import { productService } from '../productService';
import type { Product } from '../types';

const route = useRoute();
const router = useRouter();
const product = ref<Product | null>(null);
const loading = ref(true);
const errorMessage = ref('');

let requestVersion = 0;
let active = true;

function rawRouteId(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate : '';
}

function parseProductId(value: unknown): number | null {
  const rawId = rawRouteId(value);
  if (!/^0*[1-9]\d*$/.test(rawId)) return null;
  const id = Number(rawId);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

async function loadProduct(value: unknown = route.params.id) {
  const version = ++requestVersion;
  const id = parseProductId(value);

  product.value = null;
  errorMessage.value = '';
  loading.value = true;

  if (id === null) {
    if (active && version === requestVersion) {
      errorMessage.value = '商品编号无效';
      loading.value = false;
    }
    return;
  }

  try {
    const loadedProduct = await productService.getProduct(id);
    if (active && version === requestVersion) product.value = loadedProduct;
  } catch (error) {
    if (active && version === requestVersion) {
      errorMessage.value = error instanceof Error && error.message
        ? error.message
        : '商品详情加载失败';
    }
  } finally {
    if (active && version === requestVersion) loading.value = false;
  }
}

function backToList() {
  void router.push({ name: 'products' });
}

function editProduct() {
  void router.push({ name: 'product-edit', params: { id: rawRouteId(route.params.id) } });
}

function retry() {
  void loadProduct();
}

watch(() => route.params.id, (id) => {
  void loadProduct(id);
}, { immediate: true });

onBeforeUnmount(() => {
  active = false;
  requestVersion += 1;
});
</script>

<template>
  <section data-testid="product-detail-view" class="mx-auto w-full max-w-[1600px] overflow-x-hidden">
    <div v-if="loading" data-testid="product-detail-skeleton" class="space-y-4" aria-label="正在加载商品详情">
      <div class="h-16 animate-pulse rounded-lg border border-slate-200 bg-white"></div>
      <div v-for="index in 6" :key="index" class="h-40 animate-pulse rounded-lg border border-slate-200 bg-white"></div>
    </div>

    <div v-else-if="errorMessage" data-testid="product-detail-error" class="rounded-lg border border-rose-200 bg-white px-5 py-10 text-center">
      <AlertCircle class="mx-auto h-8 w-8 text-rose-500" aria-hidden="true" />
      <p class="mt-3 text-sm font-bold text-[#25314d]">{{ errorMessage }}</p>
      <button data-testid="retry-product-detail" type="button" class="mt-5 inline-flex h-10 items-center gap-2 rounded-lg border border-slate-200 px-4 text-sm font-bold text-slate-600 transition hover:border-[#536dff] hover:text-[#536dff]" @click="retry">
        <RefreshCw class="h-4 w-4" aria-hidden="true" />
        重试
      </button>
    </div>

    <template v-else-if="product">
      <header class="mb-4 flex flex-col gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div class="flex min-w-0 items-center gap-3">
          <button data-testid="product-detail-back" type="button" class="inline-flex h-9 shrink-0 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-bold text-slate-600 transition hover:border-[#536dff] hover:text-[#536dff]" @click="backToList">
            <ArrowLeft class="h-4 w-4" aria-hidden="true" />
            返回商品列表
          </button>
          <div class="min-w-0 border-l border-slate-200 pl-3">
            <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
              <h1 class="min-w-0 break-words text-lg font-black text-[#25314d]">{{ product.productName }}</h1>
              <span class="text-xs font-bold text-slate-400">{{ product.itemNo }}</span>
              <span class="text-xs font-bold" :class="product.status === 'enabled' ? 'text-emerald-600' : 'text-slate-400'">{{ formatProductStatus(product.status) }}</span>
            </div>
          </div>
        </div>
        <button data-testid="edit-product" type="button" class="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white transition hover:bg-[#465eea] sm:self-auto" @click="editProduct">
          <Pencil class="h-4 w-4" aria-hidden="true" />
          编辑商品
        </button>
      </header>

      <div class="space-y-4">
        <ProductOverviewSection :product="product" />
        <ProductSkuSection :skus="product.skus" />
        <ProductProcurementSection :skus="product.skus" />
        <ProductPackagingSection :skus="product.skus" />
        <ProductImagesSection :product="product" />
        <ProductAuditSection :product="product" />
      </div>
    </template>
  </section>
</template>
