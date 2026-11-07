<script setup lang="ts">
import { ChevronDown, ChevronRight, Package, PackageSearch } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { Category } from '../../masterdata/types';
import type { Product, ProductSku } from '../types';
import {
  DEFAULT_PRODUCT_COLUMN_IDS, PRODUCT_CATALOG_COLUMNS,
  formatCatalogDimensions, formatCatalogUnit, resolveCatalogSku
} from '../productCatalogColumns';
import type { ProductCatalogColumnDefinition, ProductCatalogColumnId } from '../productCatalogColumns';
import { formatProductNumber, formatProductPriceRange, getDefaultSupplierQuote } from '../productPresentation';
import { formatDimensions } from '../productDetailFormatting';
import ProductDetailImage from './ProductDetailImage.vue';

const numericColumns = new Set<ProductCatalogColumnId>([
  'stock', 'safetyStock', 'price', 'skuCount', 'completenessPercent', 'productDimensions',
  'capacity', 'packageVolume', 'cartonQuantity', 'grossWeight', 'netWeight', 'gramWeight'
]);
const props = withDefaults(defineProps<{
  products?: Product[];
  categories?: Category[];
  categoryLookupFailed?: boolean;
  loading?: boolean;
  showEmpty?: boolean;
  columnIds?: ProductCatalogColumnId[];
}>(), {
  products: () => [], categories: () => [], categoryLookupFailed: false,
  loading: false, showEmpty: true, columnIds: () => [...DEFAULT_PRODUCT_COLUMN_IDS]
});
const emit = defineEmits<{ 'open-product': [product: Product] }>();
const categoryNames = computed(() => new Map(props.categories.map((category) => [category.id, category.categoryName])));
const failedImageUrls = ref<Record<number, string>>({});
const expandedIds = ref<Set<number>>(new Set());
const catalogColumnById = new Map<ProductCatalogColumnId, ProductCatalogColumnDefinition>(
  PRODUCT_CATALOG_COLUMNS.map((column) => [column.id, column])
);
const activeColumns = computed(() => props.columnIds.map((id) => catalogColumnById.get(id === 'supplierSource' ? 'cartonMetrics' : id))
  .filter((column): column is ProductCatalogColumnDefinition => Boolean(column)));
const totalColumnCount = computed(() => activeColumns.value.length + 1);
const minimumWidth = computed(() => activeColumns.value.reduce((sum, column) => sum + column.width, 72));

function categoryDisplay(product: Product) {
  if (product.categoryName) return { label: product.categoryName, hint: '' };
  if (product.categoryId != null && categoryNames.value.has(product.categoryId)) {
    return { label: categoryNames.value.get(product.categoryId)!, hint: '' };
  }
  if (props.categoryLookupFailed) return { label: '--', hint: '分类加载失败' };
  return { label: '--', hint: product.categoryId == null ? '未设置分类' : '分类不存在' };
}
function hasProductImage(product: Product) {
  return Boolean(product.mainImageUrl) && failedImageUrls.value[product.id] !== product.mainImageUrl;
}
function markImageFailed(product: Product) {
  if (product.mainImageUrl) failedImageUrls.value = { ...failedImageUrls.value, [product.id]: product.mainImageUrl };
}
function formatPrice(value: number | null | undefined) {
  return value == null || !Number.isFinite(value) ? '--' : `¥${value.toFixed(2)}`;
}
function formatDateTime(value: string | null | undefined) {
  if (!value) return '--';
  const normalized = value.replace('T', ' ');
  return /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(normalized) ? normalized.slice(0, 16) : value;
}
function formatDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(0, 10) : formatDateTime(value);
}
function recordStatusLabel(product: Product) {
  return product.status === 'draft' ? '草稿' : product.status === 'enabled' ? '启用' : '停用';
}
function alignmentClass(column: ProductCatalogColumnDefinition) {
  return column.align === 'right' ? 'text-right' : column.align === 'center' ? 'text-center' : 'text-left';
}
function cellTestId(id: ProductCatalogColumnId, product: Product) {
  if (id === 'stock' || id === 'inventory') return `product-stock-${product.id}`;
  if (id === 'price' || id === 'pricing') return `product-price-${product.id}`;
  return `product-cell-${id}-${product.id}`;
}
function cartonText(sku?: ProductSku) {
  return sku?.cartonQuantity == null || !Number.isFinite(sku.cartonQuantity)
    ? '--' : `${sku.cartonQuantity} ${sku.salesUnit || ''}/箱`;
}
function specificationText(sku?: ProductSku) {
  return sku?.specText || sku?.specificationValues.filter(Boolean).join(' / ') || '--';
}
function cartonDimensions(sku?: ProductSku) {
  return formatDimensions(sku?.packageLengthCm, sku?.packageWidthCm, sku?.packageHeightCm);
}
function cartonVolume(sku?: ProductSku) {
  let volumeCm3 = sku?.packageVolumeCm3;
  if (volumeCm3 == null && sku && cartonDimensions(sku) !== '--') {
    volumeCm3 = sku.packageLengthCm! * sku.packageWidthCm! * sku.packageHeightCm!;
  }
  if (volumeCm3 == null || !Number.isFinite(volumeCm3)) return { label: '--', title: '--' };
  const volumeM3 = volumeCm3 / 1_000_000;
  const value = Math.abs(volumeM3) >= 0.000001 || volumeM3 === 0
    ? String(Number(volumeM3.toFixed(6))) : String(volumeM3);
  return { label: `${value} m³`, title: `${volumeCm3} cm³ = ${volumeM3} m³` };
}
function cellText(id: ProductCatalogColumnId, product: Product) {
  const sku = resolveCatalogSku(product);
  switch (id) {
    case 'productCode': return product.productCode || '--';
    case 'productType': return product.productType === 'variant' ? '多规格' : '单品';
    case 'stock': return formatProductNumber(product.totalStock);
    case 'safetyStock': return formatProductNumber(product.totalSafetyStock);
    case 'price': return formatPrice(product.defaultSalePrice);
    case 'skuCount': return String(product.skus.length);
    case 'completenessPercent': return Number.isFinite(product.completenessPercent) ? `${product.completenessPercent}%` : '--';
    case 'recordStatus': return recordStatusLabel(product);
    case 'updatedAt': return formatDateTime(product.updatedAt);
    case 'remark': return product.remark || '--';
    case 'productDimensions': return formatCatalogDimensions(sku);
    case 'capacity': return formatCatalogUnit(sku?.capacityMl, 'ml');
    case 'packageVolume': return formatCatalogUnit(sku?.packageVolumeCm3, 'cm³');
    case 'cartonQuantity': return cartonText(sku);
    case 'packagingMethod': return sku?.packagingMethod || '--';
    case 'grossWeight': return formatCatalogUnit(sku?.grossWeightKg, 'kg');
    case 'netWeight': return formatCatalogUnit(sku?.netWeightKg, 'kg');
    case 'gramWeight': return formatCatalogUnit(sku?.gramWeightG, 'g');
    default: return '--';
  }
}
function displayStatus(product: Product) {
  if (product.status === 'draft') return { label: '草稿', tone: 'draft' };
  if (product.status !== 'enabled') return { label: '已停用', tone: 'disabled' };
  return { label: '已发布', tone: 'enabled' };
}
function completenessLabel(product: Product) {
  if (product.status === 'draft') return '草稿';
  if (product.status !== 'enabled') return '已停用';
  return product.completenessStatus === 'complete' ? '资料完整' : '待完善';
}
function stockDeficit(product: Product) {
  return Number.isFinite(product.totalStock) && Number.isFinite(product.totalSafetyStock)
    ? Math.max(0, product.totalSafetyStock - product.totalStock) : 0;
}
function defaultQuote(product: Product) { return getDefaultSupplierQuote(resolveCatalogSku(product)); }
function toggleSkus(product: Product) {
  const next = new Set(expandedIds.value);
  if (next.has(product.id)) next.delete(product.id);
  else next.add(product.id);
  expandedIds.value = next;
}
function openProduct(product: Product) { emit('open-product', product); }
</script>

<template>
  <div data-testid="product-table-scroll" class="catalog-scroll h-full min-w-0 overflow-auto">
    <table data-testid="product-table" class="catalog-table w-full table-fixed border-collapse text-left text-sm" :style="{ minWidth: `${minimumWidth}px` }" :aria-busy="loading">
      <thead class="sticky top-0 z-10 bg-white text-xs font-medium text-slate-500">
        <tr class="h-10">
          <th v-for="column in activeColumns" :key="column.id" scope="col" class="border-b border-slate-200 px-3" :class="[alignmentClass(column), { 'identity-heading': column.id === 'productInfo' }]" :style="{ width: `${column.width}px` }">
            {{ column.label }}<span v-if="column.id === 'packaging'" class="column-hint">默认 SKU</span>
          </th>
          <th scope="col" class="action-cell border-b border-slate-200 px-3 text-right">操作</th>
        </tr>
      </thead>
      <tbody class="text-slate-700">
        <tr v-if="loading && products.length === 0">
          <td data-testid="product-table-loading" :colspan="totalColumnCount" class="state-cell h-48 px-5 text-center text-sm text-slate-500">
            <span class="inline-flex items-center gap-2"><span class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600"></span>正在加载商品</span>
          </td>
        </tr>
        <tr v-else-if="showEmpty && products.length === 0">
          <td data-testid="product-table-empty" :colspan="totalColumnCount" class="state-cell h-56 px-5 text-center">
            <div data-testid="product-empty" class="mx-auto flex max-w-xs flex-col items-center text-slate-500">
              <PackageSearch class="mb-3 h-8 w-8 text-slate-300" aria-hidden="true" />
              <p class="font-medium text-slate-700">暂无商品</p><p class="mt-1 text-xs">当前筛选条件下没有商品记录</p>
            </div>
          </td>
        </tr>
        <template v-for="product in products" v-else :key="product.id">
          <tr :data-testid="`product-row-${product.id}`" tabindex="0" role="link" :aria-label="`查看${product.productName}详情`" class="product-record h-[88px] cursor-pointer border-b border-slate-200 bg-white hover:bg-blue-50/40 focus-visible:bg-blue-50 focus-visible:outline-none" @click="openProduct(product)" @keyup.enter.self="openProduct(product)">
            <td v-for="column in activeColumns" :key="column.id" :data-testid="cellTestId(column.id, product)" :data-label="column.label" class="catalog-cell overflow-hidden px-3 align-middle" :class="[alignmentClass(column), { 'product-info-cell': column.id === 'productInfo' }]" :style="{ width: `${column.width}px`, maxWidth: `${column.width}px` }">
              <div v-if="column.id === 'productInfo'" class="flex min-w-0 items-center gap-2.5 text-left">
                <div :data-testid="`product-thumb-${product.id}`" class="product-thumb flex h-14 w-[52px] shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-200 bg-slate-50 text-slate-400">
                  <img v-if="hasProductImage(product)" :data-testid="`product-image-${product.id}`" :src="product.mainImageUrl ?? undefined" :alt="product.productName" class="h-full w-full object-contain" @error="markImageFailed(product)" />
                  <Package v-else :data-testid="`product-image-placeholder-${product.id}`" class="h-5 w-5 stroke-[1.5]" aria-hidden="true" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="product-name truncate font-medium text-[#243449]" :title="product.productName">{{ product.productName }}</p>
                  <div class="product-meta flex min-w-0 items-center gap-1 text-xs text-slate-500">
                    <span class="truncate" :title="product.itemNo">{{ product.itemNo || '--' }}</span><span aria-hidden="true">·</span>
                    <button :data-testid="`product-sku-toggle-${product.id}`" type="button" class="sku-toggle inline-flex shrink-0 items-center gap-0.5 text-[#0968b6]" :aria-label="`${expandedIds.has(product.id) ? '收起' : '展开'}${product.productName}的 ${product.skus.length} 个 SKU`" :aria-expanded="expandedIds.has(product.id)" :aria-controls="`product-skus-${product.id}`" @click.stop="toggleSkus(product)" @keydown.enter.stop.prevent="toggleSkus(product)" @keyup.enter.stop @keydown.space.stop.prevent="toggleSkus(product)" @keyup.space.stop>
                      {{ product.skus.length }} 个 SKU<ChevronDown class="h-3 w-3 transition-transform" :class="{ 'rotate-180': expandedIds.has(product.id) }" aria-hidden="true" />
                    </button>
                  </div>
                  <p :data-testid="`product-category-${product.id}`" class="product-sub truncate text-xs text-slate-500" :title="[categoryDisplay(product).label, categoryDisplay(product).hint, product.brand].filter(Boolean).join(' · ')">
                    {{ categoryDisplay(product).label }}<span v-if="categoryDisplay(product).hint"> · {{ categoryDisplay(product).hint }}</span><span class="brand-divider">品牌 {{ product.brand || '--' }}</span>
                  </p>
                </div>
              </div>
              <div v-else-if="column.id === 'specification'" class="cell-lines">
                <p :title="formatCatalogDimensions(resolveCatalogSku(product))">{{ formatCatalogDimensions(resolveCatalogSku(product)) }}</p>
                <p class="cell-sub">{{ formatCatalogUnit(resolveCatalogSku(product)?.capacityMl, 'ml') }}</p>
                <p class="cell-sub" :title="specificationText(resolveCatalogSku(product))">{{ specificationText(resolveCatalogSku(product)) }}</p>
              </div>
              <div v-else-if="column.id === 'packaging'" class="cell-lines">
                <p :title="resolveCatalogSku(product)?.packagingMethod || '--'">{{ resolveCatalogSku(product)?.packagingMethod || '--' }}</p>
                <p class="cell-sub">{{ cartonText(resolveCatalogSku(product)) }}</p>
                <p class="cell-sub">{{ formatCatalogUnit(resolveCatalogSku(product)?.gramWeightG, 'g') }}</p>
              </div>
              <div v-else-if="column.id === 'cartonMetrics'" class="cell-lines">
                <p :title="cartonDimensions(resolveCatalogSku(product))">{{ cartonDimensions(resolveCatalogSku(product)) }}</p>
                <p class="cell-sub tabular-nums" :title="cartonVolume(resolveCatalogSku(product)).title">体积 {{ cartonVolume(resolveCatalogSku(product)).label }}</p>
                <p class="cell-sub tabular-nums" :title="`毛重 ${formatCatalogUnit(resolveCatalogSku(product)?.grossWeightKg, 'kg')} · 净重 ${formatCatalogUnit(resolveCatalogSku(product)?.netWeightKg, 'kg')}`">毛重 {{ formatCatalogUnit(resolveCatalogSku(product)?.grossWeightKg, 'kg') }} · 净重 {{ formatCatalogUnit(resolveCatalogSku(product)?.netWeightKg, 'kg') }}</p>
              </div>
              <div v-else-if="column.id === 'pricing'" class="cell-lines">
                <span class="block truncate font-numeric text-table-number font-semibold tabular-nums text-[#243449]" :title="formatProductPriceRange(product)">{{ formatProductPriceRange(product) }}</span>
                <p class="cell-sub tabular-nums">采购 {{ formatPrice(defaultQuote(product)?.purchasePrice) }}</p>
              </div>
              <div v-else-if="column.id === 'inventory'" class="cell-lines">
                <span class="block truncate font-numeric text-table-number font-semibold tabular-nums text-[#243449]" :title="formatProductNumber(product.totalStock)">{{ formatProductNumber(product.totalStock) }}<small class="ml-0.5 font-normal text-slate-400">汇总</small></span>
                <p class="cell-sub tabular-nums">安全 {{ formatProductNumber(product.totalSafetyStock) }}</p>
                <p v-if="stockDeficit(product) > 0" class="cell-sub stock-warning">缺口 {{ formatProductNumber(stockDeficit(product)) }}</p>
              </div>
              <div v-else-if="column.id === 'lifecycle'" class="cell-lines">
                <span class="record-status" :class="displayStatus(product).tone"><i aria-hidden="true"></i>{{ displayStatus(product).label }}</span>
                <p class="cell-sub tabular-nums" :title="formatDateTime(product.updatedAt)">{{ formatDate(product.updatedAt) }}</p>
                <p :data-testid="`product-cell-completenessStatus-${product.id}`" class="cell-sub">{{ completenessLabel(product) }}</p>
              </div>
              <div v-else-if="column.id === 'category'" :data-testid="`product-category-${product.id}`" class="cell-lines text-left">
                <p :title="categoryDisplay(product).label">{{ categoryDisplay(product).label }}</p><p v-if="categoryDisplay(product).hint" class="cell-sub">{{ categoryDisplay(product).hint }}</p>
              </div>
              <div v-else-if="column.id === 'brandSupplier'" class="cell-lines text-left"><p>{{ product.brand || '--' }}</p><p class="cell-sub">{{ product.defaultSupplierName || '--' }}</p></div>
              <span v-else-if="column.id === 'completenessStatus'" class="record-status" :class="displayStatus(product).tone" :title="cellText('completenessPercent', product)"><i aria-hidden="true"></i>{{ completenessLabel(product) }}</span>
              <span v-else class="block truncate text-[#243449]" :class="numericColumns.has(column.id) ? 'font-numeric text-table-number tabular-nums' : ''" :title="cellText(column.id, product)">{{ cellText(column.id, product) }}</span>
            </td>
            <td class="action-cell px-3 text-right align-middle">
              <button :data-testid="`product-detail-${product.id}`" type="button" class="row-action inline-flex h-8 items-center whitespace-nowrap text-sm text-[#0968b6]" @click.stop="openProduct(product)" @keydown.enter.stop.prevent="openProduct(product)" @keyup.enter.stop>查看详情<ChevronRight class="h-3 w-3" aria-hidden="true" /></button>
            </td>
          </tr>
          <tr v-if="expandedIds.has(product.id)" :id="`product-skus-${product.id}`" :data-testid="`product-sku-panel-${product.id}`" class="sku-panel-row">
            <td :colspan="totalColumnCount" class="sku-panel-cell">
              <div class="sku-panel" role="region" :aria-label="`${product.productName}的 SKU 明细`">
                <div class="sku-panel-heading"><span>SKU 明细</span><span class="text-xs font-normal text-slate-500">{{ product.skus.length }} 个规格 · 产品规格及箱规信息按各 SKU 展示</span></div>
                <p v-if="!product.skus.length" class="py-4 text-sm text-slate-500">暂无 SKU 资料</p>
                <div v-for="sku in product.skus" :key="sku.id" class="sku-record">
                  <div class="flex min-w-0 items-center gap-2.5" @click.stop @keydown.enter.stop @keyup.enter.stop @keydown.space.stop @keyup.space.stop>
                    <ProductDetailImage :src="sku.skuImageUrl || product.mainImageUrl" :alt="`${product.productName} ${sku.skuName || sku.skuCode}商品图`" :test-id="`catalog-sku-image-${product.id}-${sku.id}`" size="small" />
                    <div class="cell-lines"><p class="font-medium" :title="sku.skuName || '--'">{{ sku.skuName || '--' }}<span v-if="sku.id === resolveCatalogSku(product)?.id" class="default-sku">默认展示</span></p><p class="cell-sub" :title="sku.skuCode || '--'">{{ sku.skuCode || '--' }}</p><p class="cell-sub" :title="sku.barcode || '--'">条码 {{ sku.barcode || '--' }}</p></div>
                  </div>
                  <div class="cell-lines" data-label="产品规格"><p :title="formatCatalogDimensions(sku)">{{ formatCatalogDimensions(sku) }}</p><p class="cell-sub">{{ formatCatalogUnit(sku.capacityMl, 'ml') }}</p><p class="cell-sub" :title="specificationText(sku)">{{ specificationText(sku) }}</p></div>
                  <div class="cell-lines" data-label="包装信息"><p :title="sku.packagingMethod || '--'">{{ sku.packagingMethod || '--' }}</p><p class="cell-sub">{{ cartonText(sku) }}</p><p class="cell-sub">{{ formatCatalogUnit(sku.gramWeightG, 'g') }}</p></div>
                  <div class="cell-lines" data-label="箱规 / 体积 / 重量"><p :title="cartonDimensions(sku)">{{ cartonDimensions(sku) }}</p><p class="cell-sub tabular-nums" :title="cartonVolume(sku).title">体积 {{ cartonVolume(sku).label }}</p><p class="cell-sub tabular-nums" :title="`毛重 ${formatCatalogUnit(sku.grossWeightKg, 'kg')} · 净重 ${formatCatalogUnit(sku.netWeightKg, 'kg')}`">毛重 {{ formatCatalogUnit(sku.grossWeightKg, 'kg') }} · 净重 {{ formatCatalogUnit(sku.netWeightKg, 'kg') }}</p></div>
                  <div class="cell-lines" data-label="销售价 / 采购价"><p class="tabular-nums">{{ formatPrice(sku.defaultSalePrice) }}</p><p class="cell-sub tabular-nums">采购 {{ formatPrice(getDefaultSupplierQuote(sku)?.purchasePrice) }}</p></div>
                  <div class="cell-lines" data-label="库存 / 状态"><p class="tabular-nums">{{ formatProductNumber(sku.stockQuantity) }} {{ sku.salesUnit || '' }}</p><p class="cell-sub">安全 {{ formatProductNumber(sku.safetyStockQuantity) }} {{ sku.salesUnit || '' }}</p><p class="cell-sub">{{ sku.status === 'enabled' ? '启用' : '已停用' }}</p></div>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.catalog-scroll { max-width: 100%; }
.catalog-table { color: #334155; font-size: 13px; }
.catalog-table > thead { height: 42px; }
.catalog-table > thead th { font-weight: 400; white-space: nowrap; }
.catalog-table > thead th:first-child, .product-record > td:first-child { padding-left: 20px; }
.action-cell { width: 72px; }
.column-hint { margin-left: 5px; font-size: 11px; color: #7c8798; }
.product-name { line-height: 22px; font-size: 14px; }
.product-meta, .product-sub { line-height: 21px; }
.brand-divider { margin-left: 6px; padding-left: 6px; border-left: 1px solid #e4e9ef; }
.cell-lines { min-width: 0; line-height: 22px; }
.cell-lines > p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cell-sub { font-size: 12px; color: #6b7788; line-height: 21px; }
.stock-warning { color: #99702d; }
.record-status { display: inline-flex; align-items: center; justify-content: inherit; gap: 5px; font-size: 12px; color: #5c6a7d; }
.record-status i { width: 5px; height: 5px; border-radius: 50%; background: #8290a0; }
.record-status.enabled i { background: #34876f; }
.record-status.draft i { background: #b28b43; }
.row-action { font-size: 12px; gap: 1px; }
.row-action:hover, .sku-toggle:hover { text-decoration: underline; }
button:focus-visible { outline: 2px solid #0968b6; outline-offset: 3px; border-radius: 3px; }
.sku-panel-cell { padding: 0; }
.sku-panel { padding: 16px 20px 16px 82px; border-bottom: 1px solid #dce5ed; background: #f6f9fc; }
.sku-panel-heading { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; padding-bottom: 8px; font-size: 13px; font-weight: 500; color: #243449; }
.sku-record { display: grid; grid-template-columns: 1.35fr 1.1fr .75fr 1.15fr .9fr .7fr; gap: 16px; padding: 12px 0; border-top: 1px solid #e3eaf1; font-size: 12px; }
.default-sku { margin-left: 6px; font-size: 11px; font-weight: 400; color: #0968b6; }
@media (min-width: 641px) and (max-width: 1350px) {
  .identity-heading, .product-record > .product-info-cell { position: sticky; left: 0; z-index: 1; background: white; }
  .catalog-table .action-cell { position: sticky; right: 0; z-index: 1; background: white; border-left: 1px solid #edf1f5; }
  .product-record:hover > .product-info-cell, .product-record:hover > .action-cell,
  .product-record:focus-visible > .product-info-cell, .product-record:focus-visible > .action-cell { background: #f5f9fe; }
}
@media (max-width: 640px) {
  .catalog-scroll { scrollbar-gutter: auto; overflow-x: hidden; }
  .catalog-table { display: block; min-width: 0 !important; width: 100%; }
  .catalog-table > thead { display: none; }
  .catalog-table > tbody { display: block; }
  .product-record { display: grid; height: auto; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; padding: 18px 16px; }
  .product-record > .catalog-cell { display: block; width: auto !important; max-width: none !important; padding: 0; text-align: left; }
  .catalog-cell:not(.product-info-cell)::before { content: attr(data-label); display: block; margin-bottom: 3px; color: #7a8798; font-size: 11px; }
  .product-record > .product-info-cell { grid-column: 1 / -1; }
  .product-record > .action-cell { width: auto; padding: 0; text-align: left; align-self: end; }
  .product-record > .action-cell .row-action { font-size: 13px; }
  .product-name { font-size: 15px; }
  .product-thumb { width: 54px; height: 60px; }
  .cell-lines > p { white-space: normal; overflow-wrap: anywhere; }
  .state-cell { display: block; width: 100%; padding-top: 48px; }
  .sku-panel-row, .sku-panel-cell { display: block; width: 100%; }
  .sku-panel { padding: 16px; }
  .sku-record { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; }
  .sku-record > div:first-child { grid-column: 1 / -1; }
  .sku-record [data-label]::before { content: attr(data-label); display: block; font-size: 11px; color: #7a8798; }
}
</style>
