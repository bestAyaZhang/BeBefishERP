<script setup lang="ts">
import { ChevronDown, ChevronLeft, ChevronRight, ClipboardList, Filter, ImageOff, PackageCheck, PackageOpen, Plus, Search, Warehouse } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { PageResult, RecordStatus } from '../../masterdata/types';
import type { Product, ProductService } from '../types';

const props = defineProps<{ service: ProductService; categories?: Array<{ id: number; categoryName: string }> }>();
const emit = defineEmits<{ create: []; edit: [product: Product]; select: [product: Product] }>();

const result = ref<PageResult<Product>>({ records: [], page: 1, pageSize: 20, total: 0 });
const keyword = ref('');
const loading = ref(false);
const errorMessage = ref('');
const activeStatus = ref<RecordStatus | ''>('');
const statusFilterOpen = ref(false);
const activeProductFilterMenu = ref<'brand' | 'supplier' | 'category' | null>(null);
const allBrandOption = '全部品牌';
const allSupplierOption = '全部供应商';
const allCategoryOption = '全部分类';
const selectedBrand = ref(allBrandOption);
const selectedSupplier = ref(allSupplierOption);
const selectedCategory = ref(allCategoryOption);
const appliedStatus = ref<RecordStatus | ''>('');
const appliedBrand = ref(allBrandOption);
const appliedSupplier = ref(allSupplierOption);
const appliedCategory = ref(allCategoryOption);
const statusFilterOptions: Array<{ label: string; value: RecordStatus | '' }> = [
  { label: '全部状态', value: '' },
  { label: '在售', value: 'enabled' },
  { label: '已停用', value: 'disabled' }
];
const specificationUnits: Record<string, string> = {
  口径: 'mm',
  高度: 'mm',
  容量: 'ml',
  重量: 'g'
};

const overviewStats = [
  { label: 'SKU 总数', value: () => filteredRecords.value.reduce((total, product) => total + product.skus.length, 0), caption: '+18 本月新增', icon: PackageOpen, tone: 'bg-blue-50 text-[#536dff]' },
  { label: '在售商品', value: () => filteredRecords.value.filter((product) => product.status === 'enabled').length, caption: '87% 已上架', icon: PackageCheck, tone: 'bg-emerald-50 text-emerald-500' },
  { label: '库存预警', value: () => '—', caption: '低于安全线', icon: Warehouse, tone: 'bg-cyan-50 text-[#36bee3]' },
  { label: '待完善资料', value: () => filteredRecords.value.filter((product) => !product.mainImageFileId).length, caption: '图片/条码缺失', icon: ClipboardList, tone: 'bg-amber-50 text-amber-500' }
];

const productBrandOptions = computed(() => [allBrandOption, ...Array.from(new Set(result.value.records.map((product) => product.brand).filter(Boolean)))]);
const productSupplierOptions = computed(() => [allSupplierOption, ...Array.from(new Set(result.value.records.map((product) => product.defaultSupplierName).filter((value): value is string => Boolean(value))))]);
const categoryNameById = computed(() => new Map((props.categories ?? []).map((category) => [category.id, category.categoryName])));
const productCategoryOptions = computed(() => {
  const categoryNames = (props.categories ?? []).map((category) => category.categoryName).filter(Boolean);
  const recordCategoryNames = result.value.records
    .map((product) => product.categoryId)
    .filter((value): value is number => value !== null && value !== undefined)
    .map((value) => categoryNameById.value.get(value) ?? '未分类');
  return [allCategoryOption, ...Array.from(new Set([...categoryNames, ...recordCategoryNames]))];
});
const filteredRecords = computed(() => result.value.records.filter((product) => {
  const matchesBrand = appliedBrand.value === allBrandOption || product.brand === appliedBrand.value;
  const matchesSupplier = appliedSupplier.value === allSupplierOption || product.defaultSupplierName === appliedSupplier.value;
  const categoryLabel = product.categoryId === null || product.categoryId === undefined ? '未分类' : categoryNameById.value.get(product.categoryId) ?? '未分类';
  const matchesCategory = appliedCategory.value === allCategoryOption || categoryLabel === appliedCategory.value;
  const matchesStatus = appliedStatus.value === '' || product.status === appliedStatus.value;
  return matchesBrand && matchesSupplier && matchesCategory && matchesStatus;
}));

async function load(page = result.value.page) {
  loading.value = true;
  errorMessage.value = '';
  try {
    result.value = await props.service.listProducts({ page, size: result.value.pageSize, keyword: keyword.value, status: appliedStatus.value || undefined });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '产品加载失败';
  } finally {
    loading.value = false;
  }
}

function searchProducts() {
  appliedStatus.value = activeStatus.value;
  appliedBrand.value = selectedBrand.value;
  appliedSupplier.value = selectedSupplier.value;
  appliedCategory.value = selectedCategory.value;
  void load(1);
}

function toggleProductFilterMenu(key: 'brand' | 'supplier' | 'category') {
  activeProductFilterMenu.value = activeProductFilterMenu.value === key ? null : key;
}

function selectProductFilter(key: 'brand' | 'supplier' | 'category', value: string) {
  if (key === 'brand') selectedBrand.value = value;
  if (key === 'supplier') selectedSupplier.value = value;
  if (key === 'category') selectedCategory.value = value;
  activeProductFilterMenu.value = null;
}

function selectStatus(status: RecordStatus | '') {
  activeStatus.value = status;
  statusFilterOpen.value = false;
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target;
  if (!(target instanceof Element)) return;
  if (target.closest('[data-product-filter-control], [data-product-filter-menu]')) return;
  activeProductFilterMenu.value = null;
}

function resetProductFilters() {
  keyword.value = '';
  activeStatus.value = '';
  appliedStatus.value = '';
  selectedBrand.value = allBrandOption;
  selectedSupplier.value = allSupplierOption;
  selectedCategory.value = allCategoryOption;
  appliedBrand.value = allBrandOption;
  appliedSupplier.value = allSupplierOption;
  appliedCategory.value = allCategoryOption;
  activeProductFilterMenu.value = null;
  statusFilterOpen.value = false;
  void load(1);
}

function defaultSku(product: Product) {
  return product.skus.find((sku) => sku.defaultSku) ?? product.skus[0];
}

function formatDisplayNumber(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '-';
  return Number(value).toFixed(3).replace(/\.?(0)+$/, '');
}

function specificationLines(product: Product) {
  const lines = product.specifications
    .map((specification) => {
      const unit = specificationUnits[specification.name] ?? '';
      const values = specification.values
        .map((value) => {
          const normalizedValue = value.trim();
          if (!unit || !normalizedValue || new RegExp(`${unit}$`, 'i').test(normalizedValue)) return normalizedValue;
          return `${normalizedValue}${unit}`;
        })
        .filter(Boolean);
      return values.length > 0 ? `${specification.name}：${values.join(' / ')}` : '';
    })
    .filter(Boolean);
  if (lines.length > 0) return lines;

  const values = defaultSku(product)?.specificationValues ?? [];
  return [values.join(' / ') || '无规格'];
}

function formatPrice(product: Product) {
  const price = defaultSku(product)?.defaultSalePrice;
  return price === null || price === undefined ? '-' : `¥${Number(price).toFixed(2)}`;
}

function formatGrossWeight(product: Product) {
  const weight = defaultSku(product)?.grossWeightKg;
  return weight === null || weight === undefined ? '-' : `${formatDisplayNumber(weight)} kg`;
}

function packagingText(product: Product) {
  const sku = defaultSku(product);
  if (!sku) return '-';
  if (sku.packagingMethod?.trim()) return sku.packagingMethod.trim();
  return sku.cartonQuantity === null || sku.cartonQuantity === undefined ? '-' : `${sku.cartonQuantity}件/箱`;
}

function cartonSizeLines(product: Product) {
  const packaging = defaultSku(product);
  if (!packaging) return ['-'];
  const dimensions = [packaging.packageLengthCm, packaging.packageWidthCm, packaging.packageHeightCm];
  const hasDimensions = dimensions.every((value) => value !== null && value !== undefined);
  const dimensionText = hasDimensions ? `${dimensions.map(formatDisplayNumber).join('*')}CM` : '';
  const volumeText = packaging.packageVolumeCm3 === null || packaging.packageVolumeCm3 === undefined
    ? ''
    : `${formatDisplayNumber(Number(packaging.packageVolumeCm3) / 1_000_000)}立方`;
  return [dimensionText, volumeText].filter(Boolean).length > 0
    ? [dimensionText, volumeText].filter(Boolean)
    : ['-'];
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick);
  void load(1);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick);
});
</script>

<template>
  <section class="space-y-5">
    <div data-testid="product-overview-stats" class="grid grid-cols-1 gap-4 sm:grid-cols-2 min-[1180px]:grid-cols-4"><article v-for="stat in overviewStats" :key="stat.label" class="rounded-[18px] border border-slate-200 bg-white p-5 shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><div class="mb-4 flex items-center justify-between"><span data-testid="product-overview-stat-icon" class="flex h-11 w-11 items-center justify-center rounded-full" :class="stat.tone"><component :is="stat.icon" class="h-5 w-5" aria-hidden="true" /></span><span data-testid="product-overview-stat-badge" class="rounded-full bg-slate-50 px-3 py-1 text-[11px] font-bold text-slate-400">{{ stat.caption }}</span></div><p class="text-sm font-bold text-slate-400">{{ stat.label }}</p><p class="mt-2 text-3xl font-black leading-none tracking-tight text-[#25314d]">{{ stat.value() }}</p></article></div>
    <section data-testid="product-table" class="overflow-hidden rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><header data-testid="product-table-header" class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-6 py-5"><div><h1 data-testid="product-table-title" class="text-2xl font-black">SKU 主数据</h1><p data-testid="product-table-subtitle" class="mt-1 text-sm font-medium text-slate-400">{{ result.total }} 个商品资料，按资料状态、库存风险和渠道快速维护。</p></div><div class="flex items-center gap-3"><button data-testid="product-filter-toggle" type="button" class="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-600 transition hover:bg-slate-50" @click="statusFilterOpen = !statusFilterOpen"><Filter class="h-4 w-4" aria-hidden="true" />筛选</button><button data-testid="add-product" type="button" class="inline-flex h-10 items-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-bold text-white shadow-lg shadow-blue-200" @click="emit('create')"><Plus class="h-4 w-4" aria-hidden="true" />新增商品</button></div></header>
    <div data-testid="product-filter-bar" class="border-b border-slate-100 bg-slate-50/45 px-6 py-4"><div class="grid gap-3 md:grid-cols-2 min-[1280px]:grid-cols-[1.1fr_0.9fr_1fr_1fr_auto_auto]"><label class="min-w-0"><span class="mb-2 block text-xs font-black text-slate-400">货号</span><input data-testid="product-filter-article" v-model="keyword" class="h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff]" placeholder="输入货号 / SKU" @keyup.enter="searchProducts" /></label><label class="relative min-w-0"><span class="mb-2 block text-xs font-black text-slate-400">品牌</span><button data-product-filter-control data-testid="product-filter-brand-button" type="button" class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50" @click="toggleProductFilterMenu('brand')"><span class="truncate">{{ selectedBrand }}</span><ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" /></button><div v-if="activeProductFilterMenu === 'brand'" data-product-filter-menu data-testid="product-filter-brand-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 overflow-hidden rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]"><button v-for="brand in productBrandOptions" :key="brand" type="button" class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition" :class="selectedBrand === brand ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'" @click="selectProductFilter('brand', brand)">{{ brand }}</button></div></label><label class="relative min-w-0"><span class="mb-2 block text-xs font-black text-slate-400">供应商</span><button data-product-filter-control data-testid="product-filter-supplier-button" type="button" class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50" @click="toggleProductFilterMenu('supplier')"><span class="truncate">{{ selectedSupplier }}</span><ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" /></button><div v-if="activeProductFilterMenu === 'supplier'" data-product-filter-menu data-testid="product-filter-supplier-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]"><button v-for="supplier in productSupplierOptions" :key="supplier" type="button" class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition" :class="selectedSupplier === supplier ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'" @click="selectProductFilter('supplier', supplier)">{{ supplier }}</button></div></label><label class="relative min-w-0"><span class="mb-2 block text-xs font-black text-slate-400">分类</span><button data-product-filter-control data-testid="product-filter-category-button" type="button" class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50" @click="toggleProductFilterMenu('category')"><span class="truncate">{{ selectedCategory }}</span><ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" /></button><div v-if="activeProductFilterMenu === 'category'" data-product-filter-menu data-testid="product-filter-category-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]"><button v-for="category in productCategoryOptions" :key="category" type="button" class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition" :class="selectedCategory === category ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'" @click="selectProductFilter('category', category)">{{ category }}</button></div></label><button data-testid="product-filter-search" type="button" :disabled="loading" class="mt-6 inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-md shadow-blue-100 transition hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" @click="searchProducts"><Search class="h-4 w-4" aria-hidden="true" />搜索</button><button data-testid="product-filter-reset" type="button" class="mt-6 h-10 rounded-xl border border-slate-200 px-4 text-sm font-black text-slate-500 transition hover:bg-white hover:text-[#25314d]" @click="resetProductFilters">重置</button></div></div>
    <div v-if="statusFilterOpen" data-testid="product-status-filter-panel" class="flex flex-wrap items-center gap-2 border-b border-slate-100 px-6 py-4"><span class="mr-2 text-xs font-black text-slate-400">资料状态</span><button v-for="option in statusFilterOptions" :key="option.label" type="button" class="h-9 rounded-xl px-4 text-sm font-bold transition" :class="activeStatus === option.value ? 'bg-[#536dff] text-white shadow-md shadow-blue-100' : 'border border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-[#25314d]'" @click="selectStatus(option.value)">{{ option.label }}</button></div>
    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>
    <div class="overflow-x-auto"><table class="w-full min-w-[1200px] border-collapse text-left text-sm"><thead class="bg-slate-50 text-xs font-black text-slate-400"><tr><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">商品信息</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">产品名称</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">规格</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">售价</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">毛重</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">包装</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">外箱尺寸</th><th class="whitespace-nowrap border-b border-slate-100 px-5 py-3">供应商</th><th class="w-20 whitespace-nowrap border-b border-slate-100 px-5 py-3">状态</th><th class="w-24 whitespace-nowrap border-b border-slate-100 px-5 py-3 text-right">操作</th></tr></thead><tbody class="divide-y divide-slate-100 text-slate-700"><tr v-if="loading"><td colspan="10" class="h-40 px-5 text-center text-sm font-bold text-slate-400">正在加载...</td></tr><tr v-else-if="filteredRecords.length === 0"><td colspan="10" class="h-40 px-5 text-center text-sm font-bold text-slate-400">暂无产品</td></tr><template v-for="product in filteredRecords" :key="product.id"><tr :data-testid="`product-row-${product.id}`" class="min-h-[76px] cursor-pointer bg-white text-sm transition hover:bg-slate-50" @click="emit('select', product)"><td :data-testid="`product-info-${product.id}`" class="max-w-72 px-5 py-4 align-middle"><div :data-testid="`product-info-content-${product.id}`" class="flex min-w-0 items-center gap-3"><div :data-testid="`product-info-thumb-${product.id}`" class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-slate-100 bg-slate-50"><img v-if="product.mainImageUrl" :data-testid="`product-image-${product.id}`" :src="product.mainImageUrl" :alt="product.productName" class="h-full w-full object-cover" /><span v-else :data-testid="`product-image-placeholder-${product.id}`" class="flex h-full w-full items-center justify-center text-slate-400"><ImageOff class="h-5 w-5" aria-hidden="true" /></span></div><div class="min-w-0"><p class="truncate text-xs font-bold leading-5 text-slate-700">{{ product.itemNo }}</p><p class="mt-1 truncate text-xs font-medium leading-5 text-slate-400">{{ product.brand || '未设置' }}</p></div></div></td><td :data-testid="`product-name-${product.id}`" class="max-w-56 px-5 py-4 align-middle font-bold text-[#25314d]">{{ product.productName }}</td><td :data-testid="`product-specifications-${product.id}`" class="max-w-72 px-5 py-4 align-middle font-bold text-[#25314d]"><p v-for="line in specificationLines(product)" :key="line" class="whitespace-nowrap leading-6">{{ line }}</p></td><td class="whitespace-nowrap px-5 py-4 align-middle font-bold text-[#25314d]">{{ formatPrice(product) }}</td><td class="whitespace-nowrap px-5 py-4 align-middle font-bold text-[#25314d]">{{ formatGrossWeight(product) }}</td><td :data-testid="`product-packaging-${product.id}`" class="max-w-56 whitespace-nowrap px-5 py-4 align-middle font-bold text-[#25314d]">{{ packagingText(product) }}</td><td :data-testid="`product-carton-size-${product.id}`" class="max-w-56 px-5 py-4 align-middle font-bold text-[#25314d]"><p v-for="line in cartonSizeLines(product)" :key="line" class="whitespace-nowrap leading-6">{{ line }}</p></td><td :data-testid="`product-supplier-${product.id}`" class="max-w-48 whitespace-nowrap px-5 py-4 align-middle font-bold text-[#25314d]">{{ product.defaultSupplierName || '待维护' }}</td><td class="w-20 whitespace-nowrap px-5 py-4 align-middle"><span class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="product.status === 'enabled' ? 'bg-emerald-50 text-emerald-500' : 'bg-slate-100 text-slate-500'">{{ product.status === 'enabled' ? '在售' : '已停用' }}</span></td><td data-testid="`product-operation-${product.id}`" class="w-24 whitespace-nowrap px-5 py-4 text-right align-middle"><button type="button" class="shrink-0 whitespace-nowrap font-bold text-slate-500 transition hover:text-[#536dff]" @click.stop="emit('edit', product)">编辑</button></td></tr></template></tbody></table></div><footer data-testid="product-table-pagination" class="mt-4 flex min-w-[900px] flex-wrap items-center justify-end gap-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 text-sm font-bold text-slate-500"><span class="mr-auto whitespace-nowrap text-slate-600">共 {{ filteredRecords.length }} 条</span><button data-testid="product-page-prev" type="button" :disabled="result.page <= 1" class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300" aria-label="上一页" @click="load(result.page - 1)"><ChevronLeft class="h-4 w-4" aria-hidden="true" /></button><button data-testid="product-page-number" type="button" class="flex h-8 min-w-8 items-center justify-center rounded-lg border border-[#536dff] bg-blue-50 px-2 text-[#536dff] shadow-sm shadow-blue-100" aria-current="page">{{ result.page }}</button><button data-testid="product-page-next" type="button" :disabled="result.page * result.pageSize >= result.total" class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300" aria-label="下一页" @click="load(result.page + 1)"><ChevronRight class="h-4 w-4" aria-hidden="true" /></button></footer></section>
  </section>
</template>
