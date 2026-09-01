<script setup lang="ts">
import { ImageOff, PackageSearch } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { Category } from '../../masterdata/types';
import type { Product } from '../types';

const props = withDefaults(defineProps<{
  products?: Product[];
  categories?: Category[];
  categoryLookupFailed?: boolean;
  loading?: boolean;
  showEmpty?: boolean;
}>(), {
  products: () => [],
  categories: () => [],
  categoryLookupFailed: false,
  loading: false,
  showEmpty: true
});

const emit = defineEmits<{
  'open-product': [product: Product];
}>();

const categoryNames = computed(() => new Map(
  props.categories.map((category) => [category.id, category.categoryName])
));
const failedImageUrls = ref<Record<number, string>>({});

function categoryDisplay(product: Product) {
  if (product.categoryName) {
    return { label: product.categoryName, hint: '' };
  }
  if (product.categoryId != null && categoryNames.value.has(product.categoryId)) {
    return { label: categoryNames.value.get(product.categoryId)!, hint: '' };
  }
  if (props.categoryLookupFailed) {
    return { label: '--', hint: '分类加载失败' };
  }
  return {
    label: '--',
    hint: product.categoryId == null ? '未设置分类' : '分类不存在'
  };
}

function hasProductImage(product: Product) {
  return Boolean(product.mainImageUrl)
    && failedImageUrls.value[product.id] !== product.mainImageUrl;
}

function markImageFailed(product: Product) {
  if (!product.mainImageUrl) return;
  failedImageUrls.value = {
    ...failedImageUrls.value,
    [product.id]: product.mainImageUrl
  };
}

function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--';
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 3 });
}

function formatPrice(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--';
  return `¥${Number(value).toFixed(2)}`;
}

function openProduct(product: Product) {
  emit('open-product', product);
}
</script>

<template>
  <div data-testid="product-table-scroll" class="min-w-0 overflow-x-auto">
    <table class="w-full min-w-[1120px] table-fixed border-collapse text-left text-sm" :aria-busy="loading">
      <thead class="bg-slate-50 text-xs font-semibold text-slate-500">
        <tr>
          <th class="w-[280px] min-w-[280px] border-b border-slate-200 px-5 py-3">商品</th>
          <th class="w-[140px] min-w-[140px] border-b border-slate-200 px-4 py-3">分类</th>
          <th class="w-[200px] min-w-[200px] border-b border-slate-200 px-4 py-3">品牌 / 默认供应商</th>
          <th class="w-[110px] min-w-[110px] border-b border-slate-200 px-4 py-3 text-right">库存</th>
          <th class="w-[120px] min-w-[120px] border-b border-slate-200 px-4 py-3 text-right">价格</th>
          <th class="w-[140px] min-w-[140px] border-b border-slate-200 px-4 py-3">资料状态</th>
          <th class="w-[110px] min-w-[110px] border-b border-slate-200 px-4 py-3">业务状态</th>
          <th class="w-[110px] min-w-[110px] border-b border-slate-200 px-4 py-3 text-right">操作</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-slate-100 bg-white text-slate-700">
        <tr v-if="loading && products.length === 0">
          <td colspan="8" class="h-48 px-5 text-center text-sm text-slate-500">
            <span class="inline-flex items-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600"></span>
              正在加载商品
            </span>
          </td>
        </tr>
        <tr v-else-if="showEmpty && products.length === 0">
          <td colspan="8" class="h-56 px-5 text-center">
            <div data-testid="product-empty" class="mx-auto flex max-w-xs flex-col items-center text-slate-500">
              <PackageSearch class="mb-3 h-8 w-8 text-slate-300" aria-hidden="true" />
              <p class="font-medium text-slate-700">暂无商品</p>
              <p class="mt-1 text-xs">当前筛选条件下没有商品记录</p>
            </div>
          </td>
        </tr>
        <tr
          v-for="product in products"
          v-else
          :key="product.id"
          :data-testid="`product-row-${product.id}`"
          tabindex="0"
          role="link"
          :aria-label="`查看${product.productName}详情`"
          class="cursor-pointer transition-colors hover:bg-slate-50 focus-visible:bg-blue-50 focus-visible:outline-none"
          @click="openProduct(product)"
          @keyup.enter.self="openProduct(product)"
        >
          <td class="px-5 py-3.5 align-middle">
            <div class="flex min-w-0 items-center gap-3">
              <div
                :data-testid="`product-thumb-${product.id}`"
                class="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-200 bg-slate-50 text-slate-400"
              >
                <img
                  v-if="hasProductImage(product)"
                  :data-testid="`product-image-${product.id}`"
                  :src="product.mainImageUrl ?? undefined"
                  :alt="product.productName"
                  class="h-full w-full object-cover"
                  @error="markImageFailed(product)"
                />
                <ImageOff
                  v-else
                  :data-testid="`product-image-placeholder-${product.id}`"
                  class="h-5 w-5"
                  aria-hidden="true"
                />
              </div>
              <div class="min-w-0">
                <p class="truncate font-semibold text-slate-900" :title="product.productName">{{ product.productName }}</p>
                <p class="mt-1 truncate text-xs text-slate-500" :title="product.itemNo">{{ product.itemNo || '--' }}</p>
              </div>
            </div>
          </td>
          <td :data-testid="`product-category-${product.id}`" class="px-4 py-3.5 align-middle">
            <p class="truncate" :title="categoryDisplay(product).label">{{ categoryDisplay(product).label }}</p>
            <p
              v-if="categoryDisplay(product).hint"
              class="mt-1 truncate text-xs text-slate-400"
              :title="categoryDisplay(product).hint"
            >
              {{ categoryDisplay(product).hint }}
            </p>
          </td>
          <td class="px-4 py-3.5 align-middle">
            <p class="truncate font-medium text-slate-800" :title="product.brand || '未设置品牌'">{{ product.brand || '未设置品牌' }}</p>
            <p class="mt-1 truncate text-xs text-slate-500" :title="product.defaultSupplierName || '未设置供应商'">
              {{ product.defaultSupplierName || '未设置供应商' }}
            </p>
          </td>
          <td
            :data-testid="`product-stock-${product.id}`"
            class="w-[110px] max-w-[110px] overflow-hidden px-4 py-3.5 text-right align-middle font-medium text-slate-900"
          >
            <span class="block truncate tabular-nums" :title="formatNumber(product.totalStock)">
              {{ formatNumber(product.totalStock) }}
            </span>
          </td>
          <td
            :data-testid="`product-price-${product.id}`"
            class="w-[120px] max-w-[120px] overflow-hidden px-4 py-3.5 text-right align-middle font-medium text-slate-900"
          >
            <span class="block truncate tabular-nums" :title="formatPrice(product.defaultSalePrice)">
              {{ formatPrice(product.defaultSalePrice) }}
            </span>
          </td>
          <td class="px-4 py-3.5 align-middle">
            <span
              class="inline-flex max-w-full items-center gap-1.5 rounded px-2 py-1 text-xs font-medium"
              :class="product.completenessStatus === 'complete' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'"
              :title="`${product.completenessPercent}%`"
            >
              <span class="truncate">{{ product.completenessStatus === 'complete' ? '完整' : '待完善' }}</span>
              <span class="tabular-nums">{{ product.completenessPercent }}%</span>
            </span>
          </td>
          <td class="px-4 py-3.5 align-middle">
            <span
              class="inline-flex rounded px-2 py-1 text-xs font-medium"
              :class="product.status === 'enabled' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-600'"
            >
              {{ product.status === 'enabled' ? '在售' : '已停用' }}
            </span>
          </td>
          <td class="px-4 py-3.5 text-right align-middle">
            <button
              :data-testid="`product-detail-${product.id}`"
              type="button"
              class="whitespace-nowrap font-medium text-blue-600 transition hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
              @click.stop="openProduct(product)"
              @keydown.enter.stop.prevent="openProduct(product)"
            >
              查看详情
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
