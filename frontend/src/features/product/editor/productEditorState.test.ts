import { describe, expect, it } from 'vitest';
import { productFixture } from '../productTestFixtures';
import type { Product, ProductSupplierQuoteInput, SkuForm } from '../types';
import {
  applyUnifiedPackaging,
  createEditorState,
  detectPackagingMode,
  packagingFromSku,
  setDefaultQuote,
  toProductPayload,
  validateStep
} from './productEditorState';

function sku(overrides: Partial<SkuForm> = {}): SkuForm {
  return {
    skuCode: '',
    barcode: '',
    skuName: '默认规格',
    specificationValues: [],
    salesUnit: '只',
    defaultSalePrice: null,
    standardCost: null,
    safetyStockQuantity: null,
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
    packagingMethod: '',
    cartonQuantity: null,
    skuImageFileId: null,
    packageImageFileId: null,
    cartonImageFileId: null,
    supplierQuotes: [],
    ...overrides
  };
}

const twoSkus = [sku({ skuName: '透明款' }), sku({ skuName: '烟灰款' })];
const unifiedPackaging = {
  ...packagingFromSku(sku()),
  packageLengthCm: 42,
  innerPackageLengthCm: 36,
  grossWeightKg: 3.1,
  packageImageFileId: 90,
  cartonImageFileId: 91
};

function quote(overrides: Partial<ProductSupplierQuoteInput> = {}): ProductSupplierQuoteInput {
  return {
    supplierId: 4,
    supplierItemNo: 'SUP-001',
    purchasePrice: 8.6,
    minPurchaseQuantity: 24,
    defaultQuote: false,
    status: 'enabled',
    ...overrides
  };
}

describe('product editor state', () => {
  it('copies unified packaging to every sku without sharing object references', () => {
    const result = applyUnifiedPackaging(twoSkus, unifiedPackaging);

    expect(result.map((item) => item.innerPackageLengthCm)).toEqual([36, 36]);
    expect(result.map((item) => item.cartonImageFileId)).toEqual([91, 91]);
    expect(result[0]).not.toBe(result[1]);
  });

  it('detects per sku mode when one weight differs', () => {
    expect(detectPackagingMode([
      sku({ grossWeightKg: 3.1 }),
      sku({ grossWeightKg: 3.35 })
    ])).toBe('perSku');
  });

  it('maps supplier quotes and uploaded image ids into the final payload', () => {
    const state = createEditorState();
    state.itemNo = 'BBF-021';
    state.productName = '按压瓶';
    state.categoryId = 8;
    state.skus = [sku({
      skuName: '白色款',
      cartonImageFileId: 91,
      supplierQuotes: [quote({ defaultQuote: true })]
    })];

    const payload = toProductPayload(state);

    expect(payload.skus[0].supplierQuotes?.[0].supplierId).toBe(4);
    expect(payload.skus[0].cartonImageFileId).toBe(91);
    expect(payload).not.toHaveProperty('packagingMode');
  });

  it('hydrates every persisted field and response image preview for editing', () => {
    const product = productFixture({
      id: 42,
      productCode: 'PRD-000042',
      itemNo: 'BBF-042',
      productName: '玻璃杯',
      categoryId: 9,
      brand: 'BeBefish',
      productType: 'simple',
      mainImageFileId: 100,
      mainImageUrl: '/uploads/main.png',
      remark: '编辑备注',
      specifications: [{ name: '颜色', values: ['透明'] }],
      skus: [{
        id: 420,
        skuCode: 'BBF-042-001',
        barcode: '69700042',
        skuName: '透明款',
        specificationValues: ['透明'],
        salesUnit: '只',
        defaultSalePrice: 19.9,
        standardCost: 8.6,
        safetyStockQuantity: 12,
        packageLengthCm: 42,
        packageWidthCm: 31,
        packageHeightCm: 28,
        packageVolumeCm3: 36456,
        innerPackageLengthCm: 36,
        innerPackageWidthCm: 25,
        innerPackageHeightCm: 22,
        netWeightKg: 8.5,
        grossWeightKg: 9.2,
        gramWeightG: 350,
        innerPackageWeightKg: 1.1,
        packagingMethod: '彩盒',
        cartonQuantity: 12,
        skuImageFileId: 101,
        skuImageUrl: '/uploads/sku.png',
        packageImageFileId: 102,
        packageImageUrl: '/uploads/package.png',
        cartonImageFileId: 103,
        cartonImageUrl: '/uploads/carton.png',
        supplierQuotes: [{
          id: 301,
          skuId: 420,
          supplierId: 4,
          supplierName: '义乌玻璃厂',
          supplierItemNo: 'YW-42',
          purchasePrice: 8.6,
          minPurchaseQuantity: 24,
          defaultQuote: true,
          status: 'enabled'
        }],
        defaultSku: true,
        status: 'enabled'
      }],
      status: 'enabled'
    }) as Product;

    const state = createEditorState(product);

    expect(toProductPayload(state)).toEqual(expect.objectContaining({
      itemNo: 'BBF-042',
      mainImageFileId: 100,
      skus: [expect.objectContaining({
        id: 420,
        innerPackageWeightKg: 1.1,
        packageImageFileId: 102,
        supplierQuotes: [expect.objectContaining({ id: 301, supplierId: 4 })]
      })]
    }));
    expect(state.imagePreviews).toEqual({
      main: '/uploads/main.png',
      sku: ['/uploads/sku.png'],
      package: ['/uploads/package.png'],
      carton: ['/uploads/carton.png']
    });
  });

  it('returns field errors for the first two required steps and invalid weights', () => {
    const state = createEditorState();

    expect(validateStep(state, 'basic')).toEqual({
      itemNo: '请输入货号',
      productName: '请输入商品名称',
      categoryId: '请选择商品分类'
    });
    expect(validateStep(state, 'sku')).toEqual({ skus: '请至少添加一个 SKU' });

    state.skus = [sku({ netWeightKg: 3.2, grossWeightKg: 3.1 })];
    expect(validateStep(state, 'packaging')).toEqual({
      'skus.0.grossWeightKg': '毛重不能小于净重'
    });
  });

  it('keeps exactly one default supplier quote in a sku', () => {
    const quotes = [quote({ supplierId: 4, defaultQuote: true }), quote({ supplierId: 8 })];

    const result = setDefaultQuote(quotes, 1);

    expect(result.map((item) => item.defaultQuote)).toEqual([false, true]);
    expect(result[0]).not.toBe(quotes[0]);
  });

  it('preserves a loaded single-SKU product type in the update payload', () => {
    const state = createEditorState();
    state.productType = 'simple';
    state.skus = [sku({ specificationValues: ['透明'] })];

    expect(toProductPayload(state).productType).toBe('simple');
  });
});
