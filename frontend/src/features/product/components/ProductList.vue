<script setup lang="ts">
import { ChevronDown, CircleAlert, Download, Folder, Plus, Search } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { Category, PageResult } from '../../masterdata/types';
import type { Product, ProductQuery, ProductService } from '../types';
import {
  loadProductColumnIds,
  resetProductColumnIds,
  saveProductColumnIds
} from '../productCatalogColumns';
import type { ProductCatalogColumnId } from '../productCatalogColumns';
import { downloadProductCsv } from '../productPresentation';
import ProductCategoryTree from './ProductCategoryTree.vue';
import ProductColumnSelector from './ProductColumnSelector.vue';
import ProductPagination from './ProductPagination.vue';
import ProductTable from './ProductTable.vue';

interface CatalogRouteState {
  status: 'published' | 'draft' | 'all';
  categoryId?: number;
  keyword?: string;
  page: number;
  size: number;
}

const allowedPageSizes = [10, 20, 50] as const;
const defaultPageSize = 20;
const publicationFilters = [
  { value: 'published', label: '正式商品' },
  { value: 'draft', label: '草稿' },
  { value: 'all', label: '全部' }
] as const;

const props = withDefaults(defineProps<{
  service: ProductService;
  categories?: Category[];
  categoryCounts?: Record<number, number>;
  allProductTotal?: number | null;
  categoryLoading?: boolean;
  categoryError?: string;
  categoryLookupFailed?: boolean;
}>(), {
  categories: () => [],
  categoryCounts: () => ({}),
  allProductTotal: null,
  categoryLoading: false,
  categoryError: '',
  categoryLookupFailed: false
});

const emit = defineEmits<{
  'open-product': [product: Product];
  'create-product': [];
  'retry-categories': [];
}>();

const route = useRoute();
const router = useRouter();
const result = ref<PageResult<Product>>({ records: [], page: 1, pageSize: defaultPageSize, total: 0 });
const keywordDraft = ref('');
const loading = ref(false);
const errorMessage = ref('');
const hasSuccessfulLoad = ref(false);
const productColumnIds = ref<ProductCatalogColumnId[]>(loadProductColumnIds());
const categoryRoot = ref<HTMLElement>();
const categoryTrigger = ref<HTMLButtonElement>();
const categoryOpen = ref(false);
const selectedCategoryName = computed(() => routeState.value.categoryId
  ? props.categories.find((category) => category.id === routeState.value.categoryId)?.categoryName || '所选分类'
  : '商品分类');
const currentPageSkuCount = computed(() => result.value.records.reduce((count, product) => count + product.skus.length, 0));
let latestRequestId = 0;

function singleQueryValue(value: unknown) {
  return typeof value === 'string' ? value : undefined;
}

function positiveInteger(value: unknown) {
  const text = singleQueryValue(value);
  if (!text || !/^\d+$/.test(text)) return undefined;
  const parsed = Number(text);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

const routeState = computed<CatalogRouteState>(() => {
  const requestedStatus = singleQueryValue(route.query.status);
  const status = requestedStatus === 'draft' || requestedStatus === 'all' ? requestedStatus : 'published';
  const categoryId = positiveInteger(route.query.categoryId);
  const keyword = singleQueryValue(route.query.keyword)?.trim() || undefined;
  const page = positiveInteger(route.query.page) ?? 1;
  const requestedSize = positiveInteger(route.query.size);
  const size = allowedPageSizes.includes(requestedSize as (typeof allowedPageSizes)[number])
    ? requestedSize!
    : defaultPageSize;
  return { categoryId, keyword, page, size, status };
});

function serializeRouteState(state: CatalogRouteState): Record<string, string> {
  return {
    ...(state.status !== 'published' ? { status: state.status } : {}),
    ...(state.categoryId ? { categoryId: String(state.categoryId) } : {}),
    ...(state.keyword ? { keyword: state.keyword } : {}),
    page: String(state.page),
    size: String(state.size)
  };
}

function isCanonicalQuery(query: Record<string, unknown>, canonical: Record<string, string>) {
  const queryKeys = Object.keys(query).sort();
  const canonicalKeys = Object.keys(canonical).sort();
  if (queryKeys.length !== canonicalKeys.length) return false;

  return canonicalKeys.every((key, index) => (
    queryKeys[index] === key
    && typeof query[key] === 'string'
    && query[key] === canonical[key]
  ));
}

function productQuery(state: CatalogRouteState): ProductQuery {
  return {
    ...(state.status !== 'all' ? { status: state.status } : {}),
    page: state.page,
    size: state.size,
    ...(state.categoryId ? { categoryId: state.categoryId } : {}),
    ...(state.keyword ? { keyword: state.keyword } : {})
  };
}

async function loadProducts(state: CatalogRouteState = routeState.value) {
  const requestId = ++latestRequestId;
  loading.value = true;
  errorMessage.value = '';
  try {
    const nextResult = await props.service.listProducts(productQuery(state));
    if (requestId !== latestRequestId) return;

    const lastPage = Math.max(1, Math.ceil(nextResult.total / state.size));
    if (state.page > lastPage) {
      await router.replace({ query: serializeRouteState({ ...state, page: lastPage }) });
      return;
    }
    result.value = nextResult;
    hasSuccessfulLoad.value = true;
  } catch (error) {
    if (requestId !== latestRequestId) return;
    errorMessage.value = error instanceof Error && error.message
      ? error.message
      : '商品列表加载失败，请稍后重试。';
  } finally {
    if (requestId === latestRequestId) loading.value = false;
  }
}

function updateRoute(nextState: CatalogRouteState) {
  return router.push({ query: serializeRouteState(nextState) });
}

function selectCategory(categoryId?: number) {
  closeCategoryPopover(true);
  void updateRoute({ ...routeState.value, categoryId, page: 1 });
}

function closeCategoryPopover(restoreFocus = false) {
  categoryOpen.value = false;
  if (restoreFocus) void nextTick(() => categoryTrigger.value?.focus());
}

function onCategoryKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return;
  event.preventDefault();
  closeCategoryPopover(true);
}

function onCategoryPointerdown(event: PointerEvent) {
  if (!categoryRoot.value?.contains(event.target as Node)) closeCategoryPopover();
}

function onCategoryFocusout(event: FocusEvent) {
  if (event.relatedTarget && !categoryRoot.value?.contains(event.relatedTarget as Node)) closeCategoryPopover();
}

function removeCategoryListeners() {
  document.removeEventListener('keydown', onCategoryKeydown);
  document.removeEventListener('pointerdown', onCategoryPointerdown);
}

watch(categoryOpen, (open) => {
  if (!open) {
    removeCategoryListeners();
    return;
  }
  document.addEventListener('keydown', onCategoryKeydown);
  document.addEventListener('pointerdown', onCategoryPointerdown);
  void nextTick(() => categoryRoot.value?.querySelector<HTMLInputElement>('[data-testid="category-search"]')?.focus());
});

function exportCurrentPage() {
  if (loading.value || errorMessage.value || !result.value.records.length) return;
  downloadProductCsv(result.value.records, `商品资料-当前页-${routeState.value.page}.csv`);
}

function selectPublicationStatus(status: CatalogRouteState['status']) {
  void updateRoute({ ...routeState.value, status, page: 1 });
}

function searchProducts() {
  const keyword = keywordDraft.value.trim() || undefined;
  void updateRoute({ ...routeState.value, keyword, page: 1 });
}

function resetKeyword() {
  keywordDraft.value = '';
  void updateRoute({ ...routeState.value, keyword: undefined, page: 1 });
}

function changePage(page: number) {
  if (!Number.isSafeInteger(page) || page < 1 || page === routeState.value.page) return;
  void updateRoute({ ...routeState.value, page });
}

function changeSize(size: number) {
  if (!allowedPageSizes.includes(size as (typeof allowedPageSizes)[number])) return;
  void updateRoute({ ...routeState.value, size, page: 1 });
}

function updateProductColumns(ids: ProductCatalogColumnId[]) {
  productColumnIds.value = [...ids];
  saveProductColumnIds(productColumnIds.value);
}

function resetProductColumns() {
  productColumnIds.value = resetProductColumnIds();
}

watch(() => route.query, (query) => {
  const state = routeState.value;
  keywordDraft.value = state.keyword ?? '';
  const canonical = serializeRouteState(state);

  if (!isCanonicalQuery(query, canonical)) {
    void router.replace({ query: canonical });
    return;
  }

  void loadProducts(state);
}, { immediate: true });

onBeforeUnmount(() => {
  latestRequestId += 1;
  removeCategoryListeners();
});
</script>

<template>
  <section class="catalog-page flex h-full min-h-0 min-w-0 flex-col gap-4">
    <header data-testid="product-page-header" class="catalog-page-heading flex min-h-[72px] min-w-0 shrink-0 items-center justify-between gap-4">
      <div class="min-w-0">
        <div class="flex min-w-0 items-center gap-3">
          <h1 data-testid="product-page-title" class="truncate text-page-title text-[#243449]">商品管理</h1>
          <span data-testid="product-page-count" class="inline-flex h-6 min-w-6 shrink-0 items-center justify-center rounded border border-slate-200 bg-white px-2 text-xs font-normal tabular-nums text-slate-500">{{ allProductTotal ?? result.total }}</span>
        </div>
        <p data-testid="product-page-subtitle" class="mt-2 truncate text-xs text-slate-500">商品、SKU、包装与采购资料</p>
      </div>
      <div class="heading-actions flex shrink-0 items-center gap-2">
        <button data-testid="product-export-current-page" type="button" aria-label="导出当前页商品及 SKU" class="catalog-button export-button" :disabled="loading || !!errorMessage || !result.records.length" :title="`导出当前页已加载的 ${result.records.length} 款商品及其 SKU`" @click="exportCurrentPage"><Download class="h-4 w-4" aria-hidden="true" /><span>导出当前页</span></button>
        <button data-testid="add-product" type="button" class="catalog-button primary" @click="emit('create-product')"><Plus class="h-4 w-4" aria-hidden="true" />新增商品</button>
      </div>
    </header>
    <div data-testid="product-workspace" class="flex min-h-0 min-w-0 flex-1">
      <div data-testid="product-list-panel" class="catalog-panel flex min-h-0 min-w-0 flex-1 flex-col rounded-lg border border-slate-200 bg-white">
        <header data-testid="product-list-heading" class="flex h-12 min-w-0 shrink-0 items-center justify-between gap-4 border-b border-slate-200 px-5">
          <h2 class="sr-only text-card-title">商品列表</h2>
          <div data-testid="product-status-tabs" class="flex h-full min-w-0 items-center gap-7" role="group" aria-label="商品状态筛选">
            <button v-for="filter in publicationFilters" :key="filter.value" :data-testid="`product-filter-${filter.value}`" type="button" class="status-tab h-full border-b-2 px-0 text-sm transition" :class="routeState.status === filter.value ? 'border-[#0968b6] font-medium text-[#0968b6]' : 'border-transparent text-slate-500 hover:text-[#243449]'" :aria-pressed="routeState.status === filter.value" @click="selectPublicationStatus(filter.value)">{{ filter.label }}</button>
          </div>
          <p data-testid="product-list-total" class="shrink-0 text-xs text-slate-500">共 {{ result.total }} 件{{ routeState.status === 'draft' ? '草稿' : '商品' }}</p>
        </header>
        <div data-testid="product-filter-bar" class="catalog-filter-bar flex min-h-[60px] min-w-0 shrink-0 flex-wrap items-center gap-2 px-5 py-3">
          <div ref="categoryRoot" class="category-picker relative shrink-0" @focusout="onCategoryFocusout">
            <button ref="categoryTrigger" data-testid="product-category-trigger" type="button" class="catalog-button category-trigger" :aria-expanded="categoryOpen" aria-controls="product-category-popover" @click="categoryOpen = !categoryOpen"><Folder class="h-4 w-4 text-slate-500" aria-hidden="true" /><span class="max-w-[120px] truncate">{{ selectedCategoryName }}</span><ChevronDown class="h-3 w-3 text-slate-500" aria-hidden="true" /></button>
            <div v-show="categoryOpen" id="product-category-popover" data-testid="product-category-popover" class="category-popover absolute left-0 top-[calc(100%+8px)] z-30 w-[300px] rounded-lg bg-white shadow-xl" role="region" aria-label="商品分类筛选">
              <ProductCategoryTree :categories="categories" :category-counts="categoryCounts" :all-product-total="allProductTotal" :selected-category-id="routeState.categoryId" :loading="categoryLoading" :error="categoryError" @select-category="selectCategory" @retry="emit('retry-categories')" />
            </div>
          </div>
          <form class="catalog-search flex min-w-0 items-center gap-2" role="search" @submit.prevent="searchProducts">
            <label class="relative block min-w-0 flex-1">
              <span class="sr-only">货号 / SKU</span><Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input v-model="keywordDraft" data-testid="product-keyword" type="search" class="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-[#0968b6] focus:ring-2 focus:ring-blue-50" placeholder="搜索货号 / SKU" @keydown.enter.prevent @keyup.enter.prevent="searchProducts" />
            </label>
            <button data-testid="product-search" type="button" class="catalog-button search-submit" @click="searchProducts">查询</button>
            <button data-testid="product-reset" type="button" class="catalog-button reset-button" @click="resetKeyword">重置</button>
          </form>
          <ProductColumnSelector :model-value="productColumnIds" @update:model-value="updateProductColumns" @reset="resetProductColumns" />
        </div>
        <div class="catalog-results flex min-w-0 shrink-0 flex-wrap items-center gap-x-3 gap-y-1 px-5 pb-2 text-xs text-slate-500">
          <span>{{ routeState.categoryId ? selectedCategoryName : '全部分类' }}</span><span aria-hidden="true">·</span><span>{{ result.total }} 款商品</span><span aria-hidden="true">·</span><span>当前页 {{ currentPageSkuCount }} 个 SKU</span>
          <span v-if="routeState.keyword" class="search-summary">搜索：{{ routeState.keyword }}</span>
        </div>
        <div v-if="errorMessage" data-testid="product-list-error" class="flex flex-wrap items-center justify-between gap-3 border-y border-rose-200 bg-rose-50 px-5 py-3 text-sm text-rose-800" role="alert">
          <span class="flex min-w-0 items-center gap-2"><CircleAlert class="h-4 w-4 shrink-0" aria-hidden="true" /><span class="truncate" :title="errorMessage">{{ errorMessage }}</span></span>
          <button data-testid="product-list-retry" type="button" class="h-8 rounded-md border border-rose-300 bg-white px-3 text-xs font-medium text-rose-700 hover:bg-rose-100" @click="loadProducts()">重新加载</button>
        </div>
        <div v-if="loading && result.records.length > 0" data-testid="product-list-refreshing" class="h-0.5 overflow-hidden bg-blue-100"><div class="h-full w-1/2 animate-pulse bg-blue-600"></div></div>
        <div class="min-h-0 min-w-0 flex-1">
          <ProductTable :products="result.records" :categories="categories" :category-lookup-failed="categoryLookupFailed" :loading="loading" :show-empty="hasSuccessfulLoad && !errorMessage" :column-ids="productColumnIds" @open-product="emit('open-product', $event)" />
        </div>
        <ProductPagination :page="routeState.page" :size="routeState.size" :total="result.total" @change-page="changePage" @change-size="changeSize" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.catalog-page { width: 100%; max-width: 100%; color: #243449; }
.catalog-panel { max-width: 100%; }
.catalog-button { display: inline-flex; height: 36px; flex-shrink: 0; align-items: center; justify-content: center; gap: 7px; padding: 0 12px; border: 1px solid #dce3eb; border-radius: 6px; background: white; color: #34465a; font-size: 13px; white-space: nowrap; transition: background .15s, border-color .15s; }
.catalog-button:hover { background: #f8fafc; border-color: #bccbd9; }
.catalog-button.primary { background: #0968b6; border-color: #0968b6; color: white; padding: 0 16px; }
.catalog-button.primary:hover { background: #075a9e; }
.catalog-button:disabled { color: #93a0ae; cursor: not-allowed; background: #f8fafc; border-color: #e4e9ee; }
.catalog-button:focus-visible, .status-tab:focus-visible { outline: 2px solid #0968b6; outline-offset: 3px; }
.catalog-search { width: 410px; max-width: 100%; }
.catalog-search .catalog-button { padding: 0 11px; }
.search-submit { color: #0968b6; }
.catalog-results { min-height: 30px; }
.search-summary { max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-popover { max-width: calc(100vw - 64px); }
@media (max-width: 800px) {
  .catalog-search { width: min(390px, 100%); }
  .catalog-filter-bar { gap: 8px; }
}
@media (max-width: 640px) {
  .catalog-page { gap: 12px; }
  .catalog-page-heading { min-height: 76px; align-items: flex-start; padding-top: 6px; flex-wrap: wrap; }
  .catalog-page-heading h1 { font-size: 21px; }
  .heading-actions { gap: 6px; }
  .catalog-button { height: 34px; font-size: 12px; padding: 0 9px; }
  .heading-actions .primary { padding: 0 9px; }
  .export-button { width: 34px; padding: 0; }
  .export-button > span { display: none; }
  .catalog-filter-bar { padding: 12px 14px 10px; }
  .catalog-search { width: 100%; order: 3; }
  .catalog-search input { height: 36px; font-size: 13px; }
  .catalog-results { padding: 0 14px 10px; gap: 6px; font-size: 11px; }
  .category-popover { max-width: calc(100vw - 60px); }
  .catalog-panel > header { padding-left: 14px; padding-right: 14px; }
  .status-tab { font-size: 13px; }
}
</style>
