import type { Product, ProductSku } from './types';

export type ProductCatalogColumnId =
  | 'productInfo'
  | 'productCode'
  | 'category'
  | 'productType'
  | 'brandSupplier'
  | 'stock'
  | 'safetyStock'
  | 'price'
  | 'skuCount'
  | 'completenessStatus'
  | 'completenessPercent'
  | 'recordStatus'
  | 'updatedAt'
  | 'remark'
  | 'productDimensions'
  | 'capacity'
  | 'packageVolume'
  | 'cartonQuantity'
  | 'packagingMethod'
  | 'grossWeight'
  | 'netWeight'
  | 'gramWeight';

export interface ProductCatalogColumnDefinition {
  id: ProductCatalogColumnId;
  label: string;
  width: number;
  align: 'left' | 'center' | 'right';
}

export const PRODUCT_CATALOG_STORAGE_KEY = 'bebefish.product.catalog.columns.v1';
export const MIN_CONFIGURABLE_PRODUCT_COLUMNS = 6;
export const DEFAULT_PRODUCT_COLUMN_IDS: ProductCatalogColumnId[] = [
  'productInfo',
  'category',
  'brandSupplier',
  'stock',
  'price',
  'completenessStatus'
];

export const PRODUCT_CATALOG_COLUMNS = [
  { id: 'productInfo', label: '商品信息', width: 272, align: 'left' },
  { id: 'productCode', label: '商品编码', width: 140, align: 'left' },
  { id: 'category', label: '分类', width: 100, align: 'left' },
  { id: 'productType', label: '商品类型', width: 96, align: 'center' },
  { id: 'brandSupplier', label: '品牌 / 供应商', width: 116, align: 'left' },
  { id: 'stock', label: '库存', width: 72, align: 'right' },
  { id: 'safetyStock', label: '安全库存', width: 96, align: 'right' },
  { id: 'price', label: '价格', width: 84, align: 'right' },
  { id: 'skuCount', label: 'SKU 数量', width: 96, align: 'right' },
  { id: 'completenessStatus', label: '资料状态', width: 106, align: 'center' },
  { id: 'completenessPercent', label: '资料完整度', width: 112, align: 'right' },
  { id: 'recordStatus', label: '启停状态', width: 96, align: 'center' },
  { id: 'updatedAt', label: '更新时间', width: 160, align: 'left' },
  { id: 'remark', label: '备注', width: 180, align: 'left' },
  { id: 'productDimensions', label: '产品尺寸', width: 190, align: 'right' },
  { id: 'capacity', label: '容量', width: 100, align: 'right' },
  { id: 'packageVolume', label: '箱规体积', width: 120, align: 'right' },
  { id: 'cartonQuantity', label: '装箱数量', width: 120, align: 'right' },
  { id: 'packagingMethod', label: '内盒包装', width: 120, align: 'left' },
  { id: 'grossWeight', label: '毛重', width: 100, align: 'right' },
  { id: 'netWeight', label: '净重', width: 100, align: 'right' },
  { id: 'gramWeight', label: '克重', width: 100, align: 'right' }
] satisfies readonly ProductCatalogColumnDefinition[];

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

const knownIds = new Set<ProductCatalogColumnId>(
  PRODUCT_CATALOG_COLUMNS.map(({ id }) => id)
);

function defaultProductColumnIds(): ProductCatalogColumnId[] {
  return [...DEFAULT_PRODUCT_COLUMN_IDS];
}

export function normalizeProductColumnIds(ids: unknown): ProductCatalogColumnId[] {
  if (!Array.isArray(ids)) return defaultProductColumnIds();

  const normalized: ProductCatalogColumnId[] = [];
  const seen = new Set<ProductCatalogColumnId>();
  for (const value of ids) {
    const id = value as ProductCatalogColumnId;
    if (!knownIds.has(id) || seen.has(id)) continue;
    seen.add(id);
    normalized.push(id);
  }

  return normalized.length >= MIN_CONFIGURABLE_PRODUCT_COLUMNS
    ? normalized
    : defaultProductColumnIds();
}

export function loadProductColumnIds(storage?: StorageLike): ProductCatalogColumnId[] {
  try {
    const storedValue = (storage ?? window.localStorage).getItem(PRODUCT_CATALOG_STORAGE_KEY);
    return storedValue === null
      ? defaultProductColumnIds()
      : normalizeProductColumnIds(JSON.parse(storedValue));
  } catch {
    return defaultProductColumnIds();
  }
}

export function saveProductColumnIds(
  ids: ProductCatalogColumnId[],
  storage?: StorageLike
): void {
  (storage ?? window.localStorage).setItem(
    PRODUCT_CATALOG_STORAGE_KEY,
    JSON.stringify(normalizeProductColumnIds(ids))
  );
}

export function resetProductColumnIds(storage?: StorageLike): ProductCatalogColumnId[] {
  (storage ?? window.localStorage).removeItem(PRODUCT_CATALOG_STORAGE_KEY);
  return defaultProductColumnIds();
}

export function resolveCatalogSku(product: Product): ProductSku | undefined {
  return product.skus.find((sku) => sku.status === 'enabled' && sku.defaultSku)
    ?? product.skus.find((sku) => sku.status === 'enabled')
    ?? product.skus[0];
}

export function formatCatalogDimensions(sku?: ProductSku): string {
  if (!sku || [sku.productLengthCm, sku.productWidthCm, sku.productHeightCm]
    .some((value) => value == null)) {
    return '--';
  }
  return `${sku.productLengthCm} × ${sku.productWidthCm} × ${sku.productHeightCm} cm`;
}

export function formatCatalogUnit(
  value: number | null | undefined,
  unit: string
): string {
  return value == null ? '--' : `${value} ${unit}`;
}
