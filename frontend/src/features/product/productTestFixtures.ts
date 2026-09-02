import type { PageResult } from '../masterdata/types';
import type { Product, ProductSku } from './types';

type ProductResponseDefaults = Pick<Product,
  | 'categoryName'
  | 'mainImageUrl'
  | 'defaultSupplierName'
  | 'totalStock'
  | 'totalSafetyStock'
  | 'defaultSalePrice'
  | 'completenessPercent'
  | 'completenessStatus'
  | 'missingGroups'
  | 'createdAt'
  | 'updatedAt'
>;

type ProductSkuResponseDefaults = Pick<ProductSku,
  | 'specText'
  | 'safetyStockQuantity'
  | 'stockQuantity'
  | 'innerPackageLengthCm'
  | 'innerPackageWidthCm'
  | 'innerPackageHeightCm'
  | 'productLengthCm'
  | 'productWidthCm'
  | 'productHeightCm'
  | 'capacityMl'
  | 'innerPackageWeightKg'
  | 'skuImageUrl'
  | 'packageImageUrl'
  | 'cartonImageUrl'
  | 'supplierQuotes'
>;

type ProductSkuFixture = Omit<ProductSku, keyof ProductSkuResponseDefaults>
  & Partial<ProductSkuResponseDefaults>;

type ProductFixture = Omit<Product, keyof ProductResponseDefaults | 'skus'>
  & Partial<ProductResponseDefaults>
  & { skus: ProductSkuFixture[] };

export function productFixture(input: ProductFixture): Product {
  const { skus: inputSkus, ...product } = input;
  const skus = inputSkus.map((sku): ProductSku => ({
    specText: sku.specificationValues.length > 0 ? sku.specificationValues.join(' / ') : null,
    safetyStockQuantity: 0,
    stockQuantity: 0,
    innerPackageLengthCm: null,
    innerPackageWidthCm: null,
    innerPackageHeightCm: null,
    productLengthCm: null,
    productWidthCm: null,
    productHeightCm: null,
    capacityMl: null,
    innerPackageWeightKg: null,
    skuImageUrl: null,
    packageImageUrl: null,
    cartonImageUrl: null,
    supplierQuotes: [],
    ...sku
  }));
  const defaultSku = skus.find((sku) => sku.defaultSku) ?? skus[0];

  return {
    categoryName: null,
    mainImageUrl: null,
    defaultSupplierName: null,
    totalStock: skus.reduce((total, sku) => total + sku.stockQuantity, 0),
    totalSafetyStock: skus.reduce((total, sku) => total + sku.safetyStockQuantity, 0),
    defaultSalePrice: defaultSku?.defaultSalePrice ?? null,
    completenessPercent: 0,
    completenessStatus: 'incomplete',
    missingGroups: [],
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
    ...product,
    skus
  };
}

export function productPage(
  records: Product[],
  total = records.length,
  page = 1,
  pageSize = 20
): PageResult<Product> {
  return { records, page, pageSize, total };
}
