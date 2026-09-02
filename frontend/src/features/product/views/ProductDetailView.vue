<script setup lang="ts">
import { AlertCircle, ArrowLeft, Pencil, RefreshCw } from 'lucide-vue-next';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
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
const pageScrollClass = 'product-detail-page-active';

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
  if (!product.value) return;
  void router.push({ name: 'product-edit', params: { id: product.value.id } });
}

function retry() {
  void loadProduct();
}

watch(() => route.params.id, (id) => {
  void loadProduct(id);
}, { immediate: true });

onMounted(() => {
  document.documentElement.classList.add(pageScrollClass);
});

onBeforeUnmount(() => {
  active = false;
  requestVersion += 1;
  document.documentElement.classList.remove(pageScrollClass);
});
</script>

<template>
  <section data-testid="product-detail-view" class="-mt-2 w-full overflow-x-hidden pb-[26px]">
    <header data-testid="product-detail-header" class="flex h-20 items-center justify-between gap-4">
      <div class="min-w-0 self-start pt-0.5">
        <button data-testid="product-detail-back" type="button" class="inline-flex h-[18px] items-center gap-2 text-xs font-normal leading-[18px] text-[#64748b] transition hover:text-[#536dff]" @click="backToList">
          <ArrowLeft class="h-[18px] w-[18px]" aria-hidden="true" />
          返回商品列表
        </button>
        <template v-if="product && !loading && !errorMessage">
          <div class="mt-1 flex min-w-0 items-center gap-3">
            <h1 data-testid="product-detail-title" class="min-w-0 truncate text-[24px] font-bold leading-8 text-[#25314d]">{{ product.productName }}</h1>
            <span class="inline-flex h-7 shrink-0 items-center gap-2 rounded-lg px-3 text-xs font-medium leading-[18px]" :class="product.status === 'enabled' ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-200/70 text-[#64748b]'">
              <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></span>
              {{ formatProductStatus(product.status) }}
            </span>
          </div>
          <p class="mt-0.5 truncate text-xs font-normal leading-[18px] text-[#94a3b8]">{{ product.itemNo }} · {{ product.productCode }}</p>
        </template>
        <h1 v-else data-testid="product-detail-title" class="mt-1 text-[24px] font-bold leading-8 text-[#25314d]">商品详情</h1>
      </div>
      <button v-if="product && !loading && !errorMessage" data-testid="edit-product" type="button" class="inline-flex h-10 w-36 shrink-0 items-center justify-center gap-2 rounded-lg bg-[#536dff] text-sm font-medium leading-[22px] text-white transition hover:bg-[#465eea]" @click="editProduct">
        <Pencil class="h-[18px] w-[18px]" aria-hidden="true" />
        编辑商品
      </button>
    </header>

    <div v-if="loading" data-testid="product-detail-skeleton" class="space-y-4" aria-label="正在加载商品详情">
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
      <div data-testid="product-detail-body" class="mt-4 space-y-4">
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

<style>
html.product-detail-page-active {
  scrollbar-width: none;
}

html.product-detail-page-active::-webkit-scrollbar {
  width: 0;
  height: 0;
}
</style>
