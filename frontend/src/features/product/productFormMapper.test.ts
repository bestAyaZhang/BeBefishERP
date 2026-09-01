import { describe, expect, expectTypeOf, it } from 'vitest';
import { toProductFormPayload } from './productFormMapper';
import type { Product, ProductFormPayload } from './types';

const backendProduct = {
  id: 8,
  productCode: 'PRD-000008',
  itemNo: 'EW43249',
  productName: '高脚玻璃杯',
  categoryId: 2,
  categoryName: null,
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
  missingGroups: ['采购信息'],
  status: 'enabled',
  remark: null,
  specifications: [],
  skus: [{
    id: 21,
    skuCode: 'PRD-000008-DEFAULT',
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
      id: 31,
      skuId: 21,
      supplierId: 3,
      supplierName: '测试供应商',
      supplierItemNo: null,
      purchasePrice: 0,
      minPurchaseQuantity: 1,
      defaultQuote: true,
      status: 'enabled'
    }],
    defaultSku: true,
    status: 'enabled'
  }],
  createdAt: '2026-09-01T08:00:00',
  updatedAt: '2026-09-01T09:00:00'
} satisfies Product;

describe('product form mapper', () => {
  it('maps nullable backend fields to editable empty values without losing zeroes', () => {
    const payload = toProductFormPayload(backendProduct);

    expectTypeOf(payload).toEqualTypeOf<ProductFormPayload>();
    expect(payload.brand).toBe('');
    expect(payload.remark).toBe('');
    expect(payload.skus[0]).toEqual(expect.objectContaining({
      barcode: '',
      packagingMethod: '',
      defaultSalePrice: 0,
      standardCost: 0,
      safetyStockQuantity: 0
    }));
    expect(payload.skus[0].supplierQuotes).toEqual([expect.objectContaining({
      id: 31,
      supplierItemNo: '',
      purchasePrice: 0,
      minPurchaseQuantity: 1
    })]);
  });

  it('does not copy read-only response fields into the form payload', () => {
    const payload = toProductFormPayload(backendProduct);
    const sku: object = payload.skus[0];

    for (const field of ['stockQuantity', 'skuImageUrl', 'packageImageUrl', 'cartonImageUrl', 'specText', 'defaultSku', 'status']) {
      expect(sku).not.toHaveProperty(field);
    }
    for (const field of ['productCode', 'mainImageUrl', 'totalStock', 'createdAt', 'updatedAt']) {
      expect(payload).not.toHaveProperty(field);
    }
  });
});
