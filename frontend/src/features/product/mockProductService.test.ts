import { describe, expect, it } from 'vitest';
import { createMockProductService } from './mockProductService';
import type { Category } from '../masterdata/types';
import type { Product, ProductFormPayload, ProductSupplierQuoteInput } from './types';

function seedProduct(): Product {
  return {
    id: 1,
    productCode: 'PRD-000001',
    itemNo: 'ITEM-001',
    productName: '测试商品',
    categoryId: 2,
    categoryName: '测试分类',
    brand: null,
    productType: 'simple',
    mainImageFileId: null,
    mainImageUrl: null,
    defaultSupplierName: null,
    totalStock: 0,
    totalSafetyStock: 0,
    defaultSalePrice: 0,
    completenessPercent: 40,
    completenessStatus: 'incomplete',
    missingGroups: [],
    status: 'enabled',
    remark: null,
    specifications: [],
    skus: [{
      id: 11,
      skuCode: 'SKU-001',
      barcode: null,
      skuName: '默认规格',
      specText: null,
      specificationValues: [],
      salesUnit: '只',
      defaultSalePrice: 0,
      standardCost: 0,
      safetyStockQuantity: 0,
      stockQuantity: 0,
      packageLengthCm: null,
      packageWidthCm: null,
      packageHeightCm: null,
      packageVolumeCm3: null,
      innerPackageLengthCm: null,
      innerPackageWidthCm: null,
      innerPackageHeightCm: null,
      productLengthCm: null,
      productWidthCm: null,
      productHeightCm: null,
      capacityMl: null,
      netWeightKg: null,
      grossWeightKg: null,
      gramWeightG: null,
      innerPackageWeightKg: null,
      packagingMethod: null,
      cartonQuantity: null,
      skuImageFileId: null,
      skuImageUrl: null,
      packageImageFileId: null,
      packageImageUrl: null,
      cartonImageFileId: null,
      cartonImageUrl: null,
      supplierQuotes: [{
        id: 21,
        skuId: 11,
        supplierId: 3,
        supplierName: '测试供应商',
        supplierItemNo: null,
        purchasePrice: 6.8,
        minPurchaseQuantity: 48,
        defaultQuote: true,
        status: 'enabled'
      }],
      defaultSku: true,
      status: 'enabled'
    }],
    createdAt: '2026-09-01T08:00:00',
    updatedAt: '2026-09-01T09:00:00'
  };
}

function updatePayload(supplierQuotes?: ProductSupplierQuoteInput[]): ProductFormPayload {
  return {
    itemNo: 'ITEM-001',
    productName: '更新后商品',
    categoryId: 2,
    brand: '',
    productType: 'simple',
    status: 'enabled',
    mainImageFileId: null,
    remark: '',
    specifications: [],
    skus: [{
      id: 11,
      skuCode: 'SKU-001',
      barcode: '',
      skuName: '默认规格',
      specificationValues: [],
      salesUnit: '只',
      defaultSalePrice: 0,
      standardCost: 0,
      safetyStockQuantity: 0,
      packageLengthCm: null,
      packageWidthCm: null,
      packageHeightCm: null,
      packageVolumeCm3: null,
      innerPackageLengthCm: null,
      innerPackageWidthCm: null,
      innerPackageHeightCm: null,
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450,
      netWeightKg: null,
      grossWeightKg: null,
      gramWeightG: null,
      innerPackageWeightKg: null,
      packagingMethod: '',
      cartonQuantity: null,
      skuImageFileId: null,
      packageImageFileId: null,
      cartonImageFileId: null,
      supplierQuotes,
      defaultSku: true,
      status: 'enabled'
    }]
  };
}

describe('mock product service', () => {
  it('searches SKU codes and barcodes as part of the product keyword contract', async () => {
    const product = seedProduct();
    product.skus[0].barcode = '6971234567890';
    const service = createMockProductService([product]);

    expect((await service.listProducts({ page: 1, size: 20, keyword: 'sku-001' })).total).toBe(1);
    expect((await service.listProducts({ page: 1, size: 20, keyword: '697123' })).total).toBe(1);
  });

  it('rolls descendant products into parent category counts', async () => {
    const categories: Category[] = [
      { id: 1, categoryCode: 'ROOT', categoryName: '根分类', parentId: null, level: 1, sortOrder: 1, status: 'enabled', remark: '' },
      { id: 2, categoryCode: 'CHILD', categoryName: '子分类', parentId: 1, level: 2, sortOrder: 1, status: 'enabled', remark: '' }
    ];
    const factory = createMockProductService as unknown as (seed: Product[], categories: Category[]) => ReturnType<typeof createMockProductService>;
    const service = factory([seedProduct()], categories);

    expect(await service.getCategoryCounts()).toEqual({ 1: 1, 2: 1 });
  });

  it('preserves existing quotes when an update omits supplierQuotes', async () => {
    const service = createMockProductService([seedProduct()]);

    await service.updateProduct(1, updatePayload(undefined));

    expect((await service.getProduct(1)).skus[0].supplierQuotes).toHaveLength(1);
    expect(await service.listSupplierQuotes(11)).toHaveLength(1);
  });

  it('preserves physical fields when materializing an updated product', async () => {
    const service = createMockProductService([seedProduct()]);

    await service.updateProduct(1, updatePayload());

    expect((await service.getProduct(1)).skus[0]).toMatchObject({
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450
    });
  });

  it('clears existing quotes only when an update sends an empty array', async () => {
    const service = createMockProductService([seedProduct()]);

    await service.updateProduct(1, updatePayload([]));

    expect((await service.getProduct(1)).skus[0].supplierQuotes).toEqual([]);
    expect(await service.listSupplierQuotes(11)).toEqual([]);
  });

  it('keeps embedded and standalone quote reads consistent after save and delete', async () => {
    const service = createMockProductService([seedProduct()]);
    const expectConsistentReads = async () => {
      const embedded = (await service.getProduct(1)).skus[0].supplierQuotes;
      const standalone = await service.listSupplierQuotes(11);
      expect(embedded.map(({ supplierName: _supplierName, ...quote }) => quote)).toEqual(standalone);
      expect(embedded.every((quote) => quote.supplierName.length > 0)).toBe(true);
    };
    const saved = await service.saveSupplierQuote(11, {
      supplierId: 4,
      supplierItemNo: 'SUP-004',
      purchasePrice: 7.2,
      minPurchaseQuantity: 24,
      defaultQuote: false,
      syncStandardCost: false
    });

    await expectConsistentReads();

    await service.deleteSupplierQuote(11, saved.id);

    await expectConsistentReads();
    expect(await service.listSupplierQuotes(11)).toHaveLength(1);
  });

  it('isolates instances and returns defensive copies', async () => {
    const first = createMockProductService([seedProduct()]);
    const second = createMockProductService([seedProduct()]);
    const returned = await first.getProduct(1);

    returned.productName = '被调用方篡改';
    returned.skus[0].supplierQuotes[0].purchasePrice = 999;
    const page = await first.listProducts({ page: 1, size: 20 });
    page.records[0].missingGroups.push('外部污染');
    await first.changeProductStatus(1, 'disabled');

    expect((await first.getProduct(1)).productName).toBe('测试商品');
    expect((await first.getProduct(1)).skus[0].supplierQuotes[0].purchasePrice).toBe(6.8);
    expect((await first.listProducts({ page: 1, size: 20 })).records[0].missingGroups).toEqual([]);
    expect((await second.getProduct(1)).status).toBe('enabled');
  });
});
