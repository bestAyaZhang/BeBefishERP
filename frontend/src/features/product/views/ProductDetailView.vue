<script setup lang="ts">
import { AlertCircle, ArrowLeft, Check, Copy, Download, Pencil, RefreshCw } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ProductAuditSection from '../components/ProductAuditSection.vue';
import ProductDetailSideRail from '../components/ProductDetailSideRail.vue';
import ProductImagesSection from '../components/ProductImagesSection.vue';
import ProductOverviewSection from '../components/ProductOverviewSection.vue';
import ProductProcurementSection from '../components/ProductProcurementSection.vue';
import ProductSkuSection from '../components/ProductSkuSection.vue';
import { formatProductStatus, formatText } from '../productDetailFormatting';
import { downloadProductCsv } from '../productPresentation';
import { productService } from '../productService';
import type { Product } from '../types';
import '../productDetail.css';

const route = useRoute();
const router = useRouter();
const product = ref<Product | null>(null);
const loading = ref(true);
const errorMessage = ref('');
const selectedSkuId = ref<number | null>(null);
const selectedSku = computed(() => product.value?.skus.find((sku) => sku.id === selectedSkuId.value) ?? null);
const activeSection = ref('product-skus');
const actionMessage = ref('');
const copied = ref(false);
const sectionLinks = [
  { id: 'product-skus', label: 'SKU 明细' },
  { id: 'product-packaging', label: '包装与重量' },
  { id: 'product-procurement', label: '采购报价' },
  { id: 'product-images', label: '图片资料' },
  { id: 'product-audit', label: '审计信息' }
];
let feedbackTimer: ReturnType<typeof setTimeout> | undefined;
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
  selectedSkuId.value = null;
  activeSection.value = 'product-skus';
  actionMessage.value = '';
  copied.value = false;
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
    if (active && version === requestVersion) {
      product.value = loadedProduct;
      selectedSkuId.value = (loadedProduct.skus.find((sku) => sku.status === 'enabled' && sku.defaultSku)
        ?? loadedProduct.skus.find((sku) => sku.status === 'enabled')
        ?? loadedProduct.skus[0])?.id ?? null;
    }
  } catch (error) {
    if (active && version === requestVersion) {
      errorMessage.value = error instanceof Error && error.message ? error.message : '商品详情加载失败';
    }
  } finally {
    if (active && version === requestVersion) loading.value = false;
  }
}

function backToList() { void router.push({ name: 'products' }); }
function editProduct() {
  if (product.value) void router.push({ name: 'product-edit', params: { id: product.value.id } });
}
function retry() { void loadProduct(); }
function selectSku(id: number) {
  if (product.value?.skus.some((sku) => sku.id === id)) selectedSkuId.value = id;
}
function showFeedback(message: string) {
  actionMessage.value = message;
  clearTimeout(feedbackTimer);
  feedbackTimer = setTimeout(() => { actionMessage.value = ''; copied.value = false; }, 3500);
}
async function copyItemNo() {
  const currentProduct = product.value;
  if (!currentProduct) return;
  try {
    await navigator.clipboard.writeText(currentProduct.itemNo);
    if (!active || currentProduct !== product.value) return;
    copied.value = true;
    showFeedback('货号已复制');
  } catch {
    if (active && currentProduct === product.value) {
      copied.value = false;
      showFeedback('复制失败，请手动复制货号');
    }
  }
}
function exportProduct() {
  if (!product.value) return;
  try {
    downloadProductCsv([product.value], `商品资料-${product.value.id}.csv`);
    showFeedback('已导出当前商品全部 SKU');
  } catch {
    showFeedback('导出失败，请重试');
  }
}

watch(() => route.params.id, (id) => { void loadProduct(id); }, { immediate: true });
onMounted(() => { document.documentElement.classList.add(pageScrollClass); });
onBeforeUnmount(() => {
  active = false;
  requestVersion += 1;
  clearTimeout(feedbackTimer);
  document.documentElement.classList.remove(pageScrollClass);
});
</script>

<template>
  <section data-testid="product-detail-view" class="pd-page w-full">
    <header data-testid="product-detail-header" class="pd-header">
      <div class="pd-title-wrap">
        <button data-testid="product-detail-back" type="button" class="pd-back" aria-label="返回商品列表" title="返回商品列表" @click="backToList"><ArrowLeft aria-hidden="true" /></button>
        <div class="pd-title-content">
          <template v-if="product && !loading && !errorMessage">
            <div class="pd-title-line"><h1 data-testid="product-detail-title">{{ product.productName }}</h1><span class="pd-product-status" :class="{ 'pd-product-enabled': product.status === 'enabled' }"><i aria-hidden="true"></i>{{ formatProductStatus(product.status) }}</span></div>
            <div class="pd-subtitle"><span>货号 <strong>{{ formatText(product.itemNo) }}</strong></span><button data-testid="copy-product-item-no" class="pd-copy" type="button" aria-label="复制商品货号" @click="copyItemNo"><Check v-if="copied" aria-hidden="true" /><Copy v-else aria-hidden="true" /></button><span class="pd-subtitle-divider" aria-hidden="true"></span><span>{{ formatText(product.categoryName) }}</span><span class="pd-subtitle-divider" aria-hidden="true"></span><span>{{ formatText(product.brand) }}</span></div>
          </template>
          <h1 v-else data-testid="product-detail-title">商品详情</h1>
        </div>
      </div>
      <div v-if="product && !loading && !errorMessage" class="pd-header-actions"><button data-testid="export-product-detail" type="button" class="pd-button" @click="exportProduct"><Download aria-hidden="true" />导出当前商品</button><button data-testid="edit-product" type="button" class="pd-button pd-primary" @click="editProduct"><Pencil aria-hidden="true" />编辑商品</button></div>
    </header>
    <p v-if="actionMessage" data-testid="product-detail-feedback" role="status" class="pd-feedback">{{ actionMessage }}</p>

    <div v-if="loading" data-testid="product-detail-skeleton" class="pd-skeleton" aria-label="正在加载商品详情"><div v-for="index in 6" :key="index" class="h-40 animate-pulse rounded-lg border border-slate-200 bg-white"></div></div>
    <div v-else-if="errorMessage" data-testid="product-detail-error" class="pd-panel pd-error"><AlertCircle aria-hidden="true" /><p>{{ errorMessage }}</p><button data-testid="retry-product-detail" type="button" class="pd-button" @click="retry"><RefreshCw aria-hidden="true" />重试</button></div>
    <div v-else-if="product" data-testid="product-detail-body" class="pd-body">
      <ProductOverviewSection :product="product" :selected-sku="selectedSku" @select-sku="selectSku" />
      <nav data-testid="product-detail-section-nav" class="pd-section-nav" aria-label="商品详情章节"><a v-for="link in sectionLinks" :key="link.id" :href="`#${link.id}`" :class="{ 'pd-section-active': activeSection === link.id }" :aria-current="activeSection === link.id ? 'location' : undefined" @click="activeSection = link.id">{{ link.label }}<span v-if="link.id === 'product-skus'">{{ product.skus.length }}</span></a><span class="pd-current-spec">当前：{{ formatText(selectedSku?.specText || selectedSku?.skuName) }}</span></nav>
      <div class="pd-work-grid"><ProductSkuSection :skus="product.skus" :selected-sku-id="selectedSkuId" @select-sku="selectSku" /><ProductDetailSideRail :sku="selectedSku" /></div>
      <ProductProcurementSection :skus="product.skus" :selected-sku-id="selectedSkuId" />
      <ProductImagesSection :product="product" :selected-sku="selectedSku" />
      <ProductAuditSection :product="product" />
    </div>
  </section>
</template>
