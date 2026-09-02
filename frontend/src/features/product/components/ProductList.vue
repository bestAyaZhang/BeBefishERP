<script setup lang="ts">
import { CircleAlert, Plus, Search } from 'lucide-vue-next';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { Category, PageResult } from '../../masterdata/types';
import type { Product, ProductQuery, ProductService } from '../types';
import ProductCategoryTree from './ProductCategoryTree.vue';
import ProductPagination from './ProductPagination.vue';
import ProductTable from './ProductTable.vue';

interface CatalogRouteState {
  categoryId?: number;
  keyword?: string;
  page: number;
  size: number;
}

const allowedPageSizes = [10, 20, 50] as const;
const defaultPageSize = 20;

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
  const categoryId = positiveInteger(route.query.categoryId);
  const keyword = singleQueryValue(route.query.keyword)?.trim() || undefined;
  const page = positiveInteger(route.query.page) ?? 1;
  const requestedSize = positiveInteger(route.query.size);
  const size = allowedPageSizes.includes(requestedSize as (typeof allowedPageSizes)[number])
    ? requestedSize!
    : defaultPageSize;
  return { categoryId, keyword, page, size };
});

function serializeRouteState(state: CatalogRouteState): Record<string, string> {
  return {
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
  void updateRoute({ ...routeState.value, categoryId, page: 1 });
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
});
</script>

<template>
  <section class="min-w-0 space-y-4">
    <header
      data-testid="product-page-header"
      class="flex h-24 min-w-0 items-center justify-between gap-6"
    >
      <div class="min-w-0">
        <div class="flex min-w-0 items-center gap-3">
          <h1 data-testid="product-page-title" class="truncate text-page-title text-[#25314d]">商品资料</h1>
          <span
            data-testid="product-page-count"
            class="inline-flex h-6 min-w-6 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white px-1.5 text-xs font-medium tabular-nums text-slate-500"
          >
            {{ allProductTotal ?? result.total }}
          </span>
        </div>
        <p data-testid="product-page-subtitle" class="mt-2 truncate text-sm text-slate-500">
          维护商品主数据、包装规格、渠道和资料完整度。
        </p>
      </div>
      <button
        data-testid="add-product"
        type="button"
        class="inline-flex h-10 w-32 shrink-0 items-center justify-center gap-2 rounded-md bg-[#536dff] px-5 text-sm font-medium text-white shadow-sm transition hover:bg-[#465eea] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
        @click="emit('create-product')"
      >
        <Plus class="h-4 w-4" aria-hidden="true" />
        新增商品
      </button>
    </header>

    <div
      data-testid="product-workspace"
      class="grid min-w-0 gap-4 lg:min-h-[calc(100vh-220px)] lg:grid-cols-[260px_minmax(0,1fr)] lg:items-stretch"
    >
      <ProductCategoryTree
        :categories="categories"
        :category-counts="categoryCounts"
        :all-product-total="allProductTotal"
        :selected-category-id="routeState.categoryId"
        :loading="categoryLoading"
        :error="categoryError"
        @select-category="selectCategory"
        @retry="emit('retry-categories')"
      />

      <div
        data-testid="product-list-panel"
        class="flex min-h-[560px] min-w-0 flex-col overflow-hidden rounded-lg border border-slate-200 bg-white lg:min-h-0"
      >
        <header
          data-testid="product-list-heading"
          class="flex h-14 min-w-0 shrink-0 items-center justify-between gap-4 border-b border-slate-200 px-4"
        >
          <h2 class="text-card-title text-[#25314d]">商品列表</h2>
          <p data-testid="product-list-total" class="shrink-0 text-sm text-slate-500">共 {{ result.total }} 件商品</p>
        </header>

        <div
          data-testid="product-filter-bar"
          class="flex min-h-[72px] min-w-0 shrink-0 flex-wrap items-center gap-2 border-b border-slate-200 px-4 py-3"
        >
          <label class="relative min-w-[220px] flex-1 sm:max-w-[268px]">
            <span class="sr-only">货号 / SKU</span>
            <Search class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" aria-hidden="true" />
            <input
              v-model="keywordDraft"
              data-testid="product-keyword"
              type="search"
              class="h-10 w-full rounded-md border border-slate-200 bg-white pl-10 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
              placeholder="输入货号 / SKU"
              @keyup.enter="searchProducts"
            />
          </label>

          <button
            data-testid="product-search"
            type="button"
            class="inline-flex h-10 w-20 items-center justify-center rounded-md bg-[#536dff] px-3 text-sm font-medium text-white transition hover:bg-[#465eea] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
            @click="searchProducts"
          >
            查询
          </button>
          <button
            data-testid="product-reset"
            type="button"
            class="inline-flex h-10 w-16 items-center justify-center rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-[#25314d] transition hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
            @click="resetKeyword"
          >
            重置
          </button>
        </div>

        <div
          v-if="errorMessage"
          data-testid="product-list-error"
          class="flex flex-wrap items-center justify-between gap-3 border-b border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 sm:px-5"
          role="alert"
        >
          <span class="flex min-w-0 items-center gap-2">
            <CircleAlert class="h-4 w-4 shrink-0" aria-hidden="true" />
            <span class="truncate" :title="errorMessage">{{ errorMessage }}</span>
          </span>
          <button
            data-testid="product-list-retry"
            type="button"
            class="h-8 rounded-md border border-rose-300 bg-white px-3 text-xs font-semibold text-rose-700 transition hover:bg-rose-100"
            @click="loadProducts()"
          >
            重新加载
          </button>
        </div>

        <div v-if="loading && result.records.length > 0" data-testid="product-list-refreshing" class="h-0.5 overflow-hidden bg-blue-100">
          <div class="h-full w-1/2 animate-pulse bg-blue-600"></div>
        </div>

        <div class="min-h-0 min-w-0 flex-1">
          <ProductTable
            :products="result.records"
            :categories="categories"
            :category-lookup-failed="categoryLookupFailed"
            :loading="loading"
            :show-empty="hasSuccessfulLoad && !errorMessage"
            @open-product="emit('open-product', $event)"
          />
        </div>
        <ProductPagination
          :page="routeState.page"
          :size="routeState.size"
          :total="result.total"
          @change-page="changePage"
          @change-size="changeSize"
        />
      </div>
    </div>
  </section>
</template>
