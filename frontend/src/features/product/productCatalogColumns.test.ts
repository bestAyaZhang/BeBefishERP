import { describe, expect, it } from 'vitest';
import { productFixture } from './productTestFixtures';
import type { Product, ProductSku } from './types';
import {
  DEFAULT_PRODUCT_COLUMN_IDS,
  MIN_CONFIGURABLE_PRODUCT_COLUMNS,
  PRODUCT_CATALOG_COLUMNS,
  PRODUCT_CATALOG_STORAGE_KEY,
  formatCatalogDimensions,
  formatCatalogUnit,
  loadProductColumnIds,
  normalizeProductColumnIds,
  resetProductColumnIds,
  resolveCatalogSku,
  saveProductColumnIds
} from './productCatalogColumns';
import type { ProductCatalogColumnId, StorageLike } from './productCatalogColumns';

const ALL_COLUMN_IDS: ProductCatalogColumnId[] = [
  'productInfo',
  'productCode',
  'category',
  'productType',
  'brandSupplier',
  'stock',
  'safetyStock',
  'price',
  'skuCount',
  'completenessStatus',
  'completenessPercent',
  'recordStatus',
  'updatedAt',
  'remark',
  'productDimensions',
  'capacity',
  'packageVolume',
  'cartonQuantity',
  'packagingMethod',
  'grossWeight',
  'netWeight',
  'gramWeight'
];

const DEFAULT_COLUMN_IDS: ProductCatalogColumnId[] = [
  'productInfo',
  'category',
  'brandSupplier',
  'stock',
  'price',
  'completenessStatus'
];

function createMemoryStorage(entries: Record<string, string> = {}) {
  const storage: StorageLike & { values: Map<string, string> } = {
    values: new Map(Object.entries(entries)),
    getItem(key: string) {
      return this.values.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      this.values.set(key, value);
    },
    removeItem(key: string) {
      this.values.delete(key);
    }
  };
  return storage;
}

type ProductFixtureInput = Parameters<typeof productFixture>[0];
type SkuFixtureInput = ProductFixtureInput['skus'][number];

function skuFixture(
  id: number,
  status: ProductSku['status'],
  defaultSku: boolean,
  overrides: Partial<ProductSku> = {}
): SkuFixtureInput {
  return {
    id,
    skuCode: `SKU-${id}`,
    barcode: null,
    skuName: `SKU ${id}`,
    specificationValues: [],
    salesUnit: '件',
    defaultSalePrice: 10,
    standardCost: 5,
    packageLengthCm: null,
    packageWidthCm: null,
    packageHeightCm: null,
    packageVolumeCm3: null,
    netWeightKg: null,
    grossWeightKg: null,
    gramWeightG: null,
    packagingMethod: null,
    cartonQuantity: null,
    skuImageFileId: null,
    packageImageFileId: null,
    cartonImageFileId: null,
    defaultSku,
    status,
    ...overrides
  };
}

function productWithSkus(skus: SkuFixtureInput[]): Product {
  return productFixture({
    id: 1,
    productCode: 'P-001',
    itemNo: 'ITEM-001',
    productName: '测试商品',
    categoryId: 10,
    brand: '测试品牌',
    productType: 'variant',
    mainImageFileId: null,
    status: 'enabled',
    remark: null,
    specifications: [],
    skus
  });
}

describe('product catalog column registry', () => {
  it('defines configurable columns in canonical order without the fixed operation column', () => {
    expect(PRODUCT_CATALOG_COLUMNS.map(({ id }) => id)).toEqual(ALL_COLUMN_IDS);
    expect(PRODUCT_CATALOG_COLUMNS.map(({ label }) => label)).toEqual([
      '商品信息',
      '商品编码',
      '分类',
      '商品类型',
      '品牌 / 供应商',
      '库存',
      '安全库存',
      '价格',
      'SKU 数量',
      '资料状态',
      '资料完整度',
      '启停状态',
      '更新时间',
      '备注',
      '产品尺寸',
      '容量',
      '箱规体积',
      '装箱数量',
      '内盒包装',
      '毛重',
      '净重',
      '克重'
    ]);
    expect(PRODUCT_CATALOG_COLUMNS.every(({ width }) => width > 0)).toBe(true);
    expect(PRODUCT_CATALOG_COLUMNS.some(({ id }) => (id as string) === 'operation')).toBe(false);
  });

  it('uses six configurable defaults for seven total columns with operation', () => {
    expect(DEFAULT_PRODUCT_COLUMN_IDS).toEqual(DEFAULT_COLUMN_IDS);
    expect(MIN_CONFIGURABLE_PRODUCT_COLUMNS).toBe(6);
    expect(DEFAULT_PRODUCT_COLUMN_IDS).toHaveLength(6);
    expect(DEFAULT_PRODUCT_COLUMN_IDS.length + 1).toBe(7);
  });

  it('filters known identifiers while preserving the requested order', () => {
    expect(normalizeProductColumnIds([
      'capacity',
      'stock',
      'unknown',
      'productInfo',
      'price',
      'category',
      'capacity',
      'brandSupplier',
      'completenessStatus'
    ])).toEqual([
      'capacity',
      'stock',
      'productInfo',
      'price',
      'category',
      'brandSupplier',
      'completenessStatus'
    ]);
  });

  it.each([
    null,
    'not-an-array',
    [],
    ['unknown'],
    ['productInfo', 'category', 'brandSupplier', 'stock', 'price']
  ])('restores defaults for invalid or below-minimum input %#', (ids) => {
    expect(normalizeProductColumnIds(ids)).toEqual(DEFAULT_COLUMN_IDS);
  });
});

describe('product catalog column preference persistence', () => {
  it('uses the versioned storage key and saves the normalized user order', () => {
    const storage = createMemoryStorage();

    saveProductColumnIds([
      'capacity',
      'stock',
      'productInfo',
      'price',
      'category',
      'brandSupplier',
      'completenessStatus'
    ], storage);

    expect(PRODUCT_CATALOG_STORAGE_KEY).toBe('bebefish.product.catalog.columns.v1');
    expect(storage.getItem(PRODUCT_CATALOG_STORAGE_KEY)).toBe(JSON.stringify([
      'capacity',
      'stock',
      'productInfo',
      'price',
      'category',
      'brandSupplier',
      'completenessStatus'
    ]));
  });

  it('loads valid preferences in the saved user order', () => {
    const storage = createMemoryStorage({
      [PRODUCT_CATALOG_STORAGE_KEY]: JSON.stringify([
        'capacity',
        'stock',
        'productInfo',
        'price',
        'category',
        'brandSupplier',
        'completenessStatus'
      ])
    });

    expect(loadProductColumnIds(storage)).toEqual([
      'capacity',
      'stock',
      'productInfo',
      'price',
      'category',
      'brandSupplier',
      'completenessStatus'
    ]);
  });

  it.each([
    '{malformed',
    JSON.stringify(['unknown']),
    JSON.stringify(['productInfo', 'category', 'brandSupplier', 'stock', 'price'])
  ])('restores defaults for unusable stored preferences %#', (storedValue) => {
    const storage = createMemoryStorage({
      [PRODUCT_CATALOG_STORAGE_KEY]: storedValue
    });

    expect(loadProductColumnIds(storage)).toEqual(DEFAULT_COLUMN_IDS);
  });

  it('restores defaults when storage access fails', () => {
    const storage: StorageLike = {
      getItem() {
        throw new Error('storage unavailable');
      },
      setItem() {},
      removeItem() {}
    };

    expect(loadProductColumnIds(storage)).toEqual(DEFAULT_COLUMN_IDS);
  });

  it('removes the stored preference and returns a fresh default array on reset', () => {
    const storage = createMemoryStorage({
      [PRODUCT_CATALOG_STORAGE_KEY]: JSON.stringify(ALL_COLUMN_IDS)
    });

    const resetIds = resetProductColumnIds(storage);

    expect(storage.getItem(PRODUCT_CATALOG_STORAGE_KEY)).toBeNull();
    expect(resetIds).toEqual(DEFAULT_COLUMN_IDS);
    expect(resetIds).not.toBe(DEFAULT_PRODUCT_COLUMN_IDS);
  });
});

describe('catalog SKU fallback and physical formatting', () => {
  it('prefers the enabled default SKU', () => {
    const product = productWithSkus([
      skuFixture(1, 'enabled', false),
      skuFixture(2, 'enabled', true),
      skuFixture(3, 'disabled', true)
    ]);

    expect(resolveCatalogSku(product)).toBe(product.skus[1]);
  });

  it('falls back to the first enabled SKU when no enabled default exists', () => {
    const product = productWithSkus([
      skuFixture(1, 'disabled', true),
      skuFixture(2, 'enabled', false),
      skuFixture(3, 'enabled', false)
    ]);

    expect(resolveCatalogSku(product)).toBe(product.skus[1]);
  });

  it('falls back to the first SKU when none are enabled', () => {
    const product = productWithSkus([
      skuFixture(1, 'disabled', false),
      skuFixture(2, 'disabled', true)
    ]);

    expect(resolveCatalogSku(product)).toBe(product.skus[0]);
    expect(resolveCatalogSku(productWithSkus([]))).toBeUndefined();
  });

  it('formats complete dimensions and every physical numeric unit', () => {
    const product = productWithSkus([
      skuFixture(1, 'enabled', true, {
        productLengthCm: 12.5,
        productWidthCm: 8.25,
        productHeightCm: 20
      })
    ]);

    expect(formatCatalogDimensions(product.skus[0])).toBe('12.5 × 8.25 × 20 cm');
    expect(formatCatalogUnit(450, 'ml')).toBe('450 ml');
    expect(formatCatalogUnit(28224, 'cm³')).toBe('28224 cm³');
    expect(formatCatalogUnit(9.1, 'kg')).toBe('9.1 kg');
    expect(formatCatalogUnit(210, 'g')).toBe('210 g');
  });

  it('renders missing dimensions and numeric values as double hyphens', () => {
    const product = productWithSkus([
      skuFixture(1, 'enabled', true, {
        productLengthCm: 12.5,
        productWidthCm: null,
        productHeightCm: 20
      })
    ]);

    expect(formatCatalogDimensions(product.skus[0])).toBe('--');
    expect(formatCatalogDimensions()).toBe('--');
    expect(formatCatalogUnit(null, 'ml')).toBe('--');
    expect(formatCatalogUnit(undefined, 'kg')).toBe('--');
  });
});
