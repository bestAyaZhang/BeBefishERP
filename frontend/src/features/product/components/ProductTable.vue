<script setup lang="ts">
import { Package, PackageSearch } from 'lucide-vue-next';
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

function productImageTone(product: Product) {
  if (hasProductImage(product)) return 'bg-white text-slate-400';
  const tones = [
    'bg-blue-50 text-[#536dff]',
    'bg-violet-50 text-violet-500',
    'bg-cyan-50 text-cyan-500',
    'bg-emerald-50 text-emerald-500',
    'bg-rose-50 text-rose-500'
  ];
  return tones[Math.abs(product.id) % tones.length];
}

function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--';
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 3 });
}

function formatPrice(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--';
  return `¥${Number(value).toFixed(2)}`;
}

function displayStatus(product: Product) {
  if (product.status !== 'enabled') {
    return {
      label: '已停用',
      background: 'bg-slate-200/70',
      dot: 'bg-slate-400',
      text: 'text-slate-600'
    };
  }
  if (product.completenessStatus === 'complete') {
    return {
      label: '资料完整',
      background: 'bg-emerald-100/80',
      dot: 'bg-emerald-600',
      text: 'text-[#25314d]'
    };
  }
  return {
    label: '待完善',
    background: 'bg-amber-100/80',
    dot: 'bg-amber-500',
    text: 'text-[#25314d]'
  };
}

function openProduct(product: Product) {
  emit('open-product', product);
}
</script>

<template>
  <div data-testid="product-table-scroll" class="h-full min-w-0 overflow-x-auto">
    <table
      data-testid="product-table"
      class="w-full min-w-[854px] table-fixed border-collapse text-left text-sm"
      :aria-busy="loading"
    >
      <thead class="bg-slate-50 text-xs font-medium text-slate-500">
        <tr class="h-10">
          <th class="w-[272px] min-w-[272px] border-b border-slate-200 px-3">商品信息</th>
          <th class="w-[100px] min-w-[100px] border-b border-slate-200 px-3">分类</th>
          <th class="w-[116px] min-w-[116px] border-b border-slate-200 px-3">品牌 / 供应商</th>
          <th class="w-[72px] min-w-[72px] border-b border-slate-200 px-3 text-right">库存</th>
          <th class="w-[84px] min-w-[84px] border-b border-slate-200 px-3 text-right">价格</th>
          <th class="w-[106px] min-w-[106px] border-b border-slate-200 px-1 text-center">资料状态</th>
          <th class="w-[104px] min-w-[104px] border-b border-slate-200 px-3 text-center">操作</th>
        </tr>
      </thead>
      <tbody class="text-slate-700">
        <tr v-if="loading && products.length === 0">
          <td colspan="7" class="h-48 px-5 text-center text-sm text-slate-500">
            <span class="inline-flex items-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600"></span>
              正在加载商品
            </span>
          </td>
        </tr>
        <tr v-else-if="showEmpty && products.length === 0">
          <td colspan="7" class="h-56 px-5 text-center">
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
          class="h-[72px] cursor-pointer border-b border-slate-200 odd:bg-white even:bg-slate-50/70 hover:bg-blue-50/50 focus-visible:bg-blue-50 focus-visible:outline-none"
          @click="openProduct(product)"
          @keyup.enter.self="openProduct(product)"
        >
          <td class="px-3 align-middle">
            <div class="flex min-w-0 items-center gap-2.5">
              <div
                :data-testid="`product-thumb-${product.id}`"
                class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200"
                :class="productImageTone(product)"
              >
                <img
                  v-if="hasProductImage(product)"
                  :data-testid="`product-image-${product.id}`"
                  :src="product.mainImageUrl ?? undefined"
                  :alt="product.productName"
                  class="h-full w-full object-cover"
                  @error="markImageFailed(product)"
                />
                <Package
                  v-else
                  :data-testid="`product-image-placeholder-${product.id}`"
                  class="h-5 w-5 stroke-[1.8]"
                  aria-hidden="true"
                />
              </div>
              <div class="min-w-0">
                <p class="truncate font-medium leading-[22px] text-[#25314d]" :title="product.productName">{{ product.productName }}</p>
                <p class="truncate text-xs leading-[18px] text-slate-400" :title="product.itemNo">{{ product.itemNo || '--' }}</p>
              </div>
            </div>
          </td>
          <td :data-testid="`product-category-${product.id}`" class="px-3 align-middle">
            <p class="truncate" :title="categoryDisplay(product).label">{{ categoryDisplay(product).label }}</p>
            <p
              v-if="categoryDisplay(product).hint"
              class="mt-1 truncate text-xs text-slate-400"
              :title="categoryDisplay(product).hint"
            >
              {{ categoryDisplay(product).hint }}
            </p>
          </td>
          <td class="px-3 align-middle">
            <p class="truncate text-[#25314d]" :title="product.brand || '未设置品牌'">{{ product.brand || '未设置品牌' }}</p>
            <p class="truncate text-xs leading-[18px] text-slate-400" :title="product.defaultSupplierName || '未设置供应商'">
              {{ product.defaultSupplierName || '未设置供应商' }}
            </p>
          </td>
          <td
            :data-testid="`product-stock-${product.id}`"
            class="w-[72px] max-w-[72px] overflow-hidden px-3 text-right align-middle font-semibold text-[#25314d]"
            :class="Number(product.totalStock) === 0 ? 'text-rose-500' : ''"
          >
            <span class="block truncate font-numeric text-table-number tabular-nums" :title="formatNumber(product.totalStock)">
              {{ formatNumber(product.totalStock) }}
            </span>
          </td>
          <td
            :data-testid="`product-price-${product.id}`"
            class="w-[84px] max-w-[84px] overflow-hidden px-3 text-right align-middle font-semibold text-[#25314d]"
          >
            <span class="block truncate font-numeric text-table-number tabular-nums" :title="formatPrice(product.defaultSalePrice)">
              {{ formatPrice(product.defaultSalePrice) }}
            </span>
          </td>
          <td class="px-1 text-center align-middle">
            <span
              class="inline-flex h-7 w-[104px] max-w-full items-center justify-center gap-2 rounded-lg px-2 text-xs font-medium"
              :class="[displayStatus(product).background, displayStatus(product).text]"
              :title="`${product.completenessPercent}%`"
            >
              <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="displayStatus(product).dot"></span>
              <span class="truncate">{{ displayStatus(product).label }}</span>
            </span>
          </td>
          <td class="px-3 text-center align-middle">
            <button
              :data-testid="`product-detail-${product.id}`"
              type="button"
              class="h-8 whitespace-nowrap font-medium text-[#536dff] transition hover:text-[#465eea] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
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
