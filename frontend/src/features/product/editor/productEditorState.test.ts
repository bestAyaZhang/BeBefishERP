import { describe, expect, it } from 'vitest';
import { productFixture } from '../productTestFixtures';
import type { Product, ProductSupplierQuoteInput, SkuForm } from '../types';
import {
  applyUnifiedPackaging,
  createEditorState,
  detectPackagingMode,
  insertSku,
  packagingFromSku,
  setEditorImageFileId,
  setEditorImagePreview,
  setDefaultQuote,
  setPackagingMode,
  toProductPayload,
  updateUnifiedPackaging,
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

function loadedProductFixture(productType: Product['productType']): Product {
  return productFixture({
    id: productType === 'simple' ? 51 : 52,
    productCode: productType === 'simple' ? 'PRD-000051' : 'PRD-000052',
    itemNo: productType === 'simple' ? 'BBF-051' : 'BBF-052',
    productName: '已加载商品',
    categoryId: 8,
    brand: null,
    productType,
    mainImageFileId: null,
    status: 'enabled',
    remark: null,
    specifications: [],
    skus: [{
      id: productType === 'simple' ? 510 : 520,
      skuCode: productType === 'simple' ? 'PRD-000051-DEFAULT' : 'PRD-000052-001',
      barcode: null,
      skuName: '默认 SKU',
      specificationValues: [],
      salesUnit: '只',
      defaultSalePrice: 0,
      standardCost: 0,
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
      defaultSku: true,
      status: 'enabled'
    }]
  });
}

describe('product editor state', () => {
  it('copies unified packaging to every sku without sharing object references', () => {
    const result = applyUnifiedPackaging(twoSkus, unifiedPackaging);

    expect(result.map((item) => item.innerPackageLengthCm)).toEqual([36, 36]);
    expect(result.map((item) => item.cartonImageFileId)).toEqual([91, 91]);
    expect(result[0]).not.toBe(result[1]);
  });

  it('gives a SKU added in unified mode every packaging field and synchronized image preview', () => {
    const state = createEditorState();
    insertSku(state, sku({ skuName: '透明款', supplierQuotes: [quote()] }));
    updateUnifiedPackaging(state, {
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
      packageImageFileId: null,
      cartonImageFileId: null
    });
    setEditorImageFileId(state, 'package', null, 90);
    setEditorImagePreview(state, 'package', null, '/uploads/package.png');
    setEditorImageFileId(state, 'carton', null, 91);
    setEditorImagePreview(state, 'carton', null, '/uploads/carton.png');

    const added = sku({ skuName: '烟灰款', supplierQuotes: [quote({ supplierId: 8 })] });
    insertSku(state, added);

    expect(packagingFromSku(state.skus[1])).toEqual(state.unifiedPackaging);
    expect(state.skus[1]).not.toBe(state.skus[0]);
    expect(state.skus[1].supplierQuotes).not.toBe(added.supplierQuotes);
    expect(state.imagePreviews.package).toEqual(['/uploads/package.png', '/uploads/package.png']);
    expect(state.imagePreviews.carton).toEqual(['/uploads/carton.png', '/uploads/carton.png']);
    expect(state.imagePreviewFileIds.package).toEqual([90, 90]);
    expect(state.imagePreviewFileIds.carton).toEqual([91, 91]);
  });

  it('synchronizes ids and previews across packaging mode transitions without leaking stale previews', () => {
    const state = createEditorState();
    insertSku(state, sku({ skuName: '透明款' }));
    insertSku(state, sku({ skuName: '烟灰款' }));
    setPackagingMode(state, 'perSku');
    state.skus[0].packageLengthCm = 42;
    state.skus[1].packageLengthCm = 44;
    setEditorImageFileId(state, 'package', 0, 90);
    setEditorImagePreview(state, 'package', 0, '/uploads/package-90.png');
    setEditorImageFileId(state, 'package', 1, 92);
    setEditorImagePreview(state, 'package', 1, '/uploads/package-92.png');

    setPackagingMode(state, 'unified');

    expect(state.skus.map((item) => item.packageLengthCm)).toEqual([42, 42]);
    expect(state.skus.map((item) => item.packageImageFileId)).toEqual([90, 90]);
    expect(state.imagePreviews.package).toEqual(['/uploads/package-90.png', '/uploads/package-90.png']);
    expect(state.imagePreviewFileIds.package).toEqual([90, 90]);

    setPackagingMode(state, 'perSku');
    expect(state.imagePreviews.package).toEqual(['/uploads/package-90.png', '/uploads/package-90.png']);
    state.imagePreviewFileIds.package[0] = 999;
    state.imagePreviews.package[0] = '/uploads/stale.png';
    setPackagingMode(state, 'unified');
    expect(state.imagePreviews.package).toEqual(['', '']);
    expect(state.imagePreviewFileIds.package).toEqual([90, 90]);
  });

  it('clears a unified preview when a packaging update changes its file id', () => {
    const state = createEditorState();
    insertSku(state, sku({ skuName: '透明款' }));
    setEditorImageFileId(state, 'package', null, 90);
    setEditorImagePreview(state, 'package', null, '/uploads/package-90.png');

    updateUnifiedPackaging(state, { ...state.unifiedPackaging, packageImageFileId: 91 });

    expect(state.skus[0].packageImageFileId).toBe(91);
    expect(state.imagePreviews.package).toEqual(['']);
    expect(state.imagePreviewFileIds.package).toEqual([91]);
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

  it('mirrors backend nonnegative SKU amounts and packaging relationships', () => {
    const state = createEditorState();
    state.skus = [sku({
      defaultSalePrice: -0.01,
      standardCost: -1,
      safetyStockQuantity: -2,
      packageLengthCm: -3,
      innerPackageWeightKg: -0.1,
      netWeightKg: 3.2,
      grossWeightKg: 3.1,
      cartonQuantity: 0
    })];

    expect(validateStep(state, 'sku')).toEqual(expect.objectContaining({
      'skus.0.defaultSalePrice': '默认售价不能小于 0',
      'skus.0.standardCost': '标准成本不能小于 0',
      'skus.0.safetyStockQuantity': '安全库存不能小于 0'
    }));
    expect(validateStep(state, 'packaging')).toEqual(expect.objectContaining({
      'skus.0.packageLengthCm': '包装长不能小于 0',
      'skus.0.innerPackageWeightKg': '内包装重量不能小于 0',
      'skus.0.grossWeightKg': '毛重不能小于净重',
      'skus.0.cartonQuantity': '装箱数必须大于 0'
    }));
  });

  it('mirrors backend supplier quote validity, uniqueness, status, and default rules', () => {
    const state = createEditorState();
    state.skus = [sku({ supplierQuotes: [
      quote({ supplierId: 4, minPurchaseQuantity: 0, defaultQuote: true, status: 'disabled' }),
      quote({ supplierId: 4, purchasePrice: -0.01, defaultQuote: true }),
      quote({ supplierId: 99 })
    ] })];

    expect(validateStep(state, 'procurement', { validSupplierIds: new Set([4, 8]) })).toEqual(expect.objectContaining({
      'skus.0.supplierQuotes.0.minPurchaseQuantity': '最小采购量必须大于 0',
      'skus.0.supplierQuotes.0.defaultQuote': '禁用报价不能设为默认报价',
      'skus.0.supplierQuotes.1.supplierId': '同一 SKU 的供应商报价不能重复',
      'skus.0.supplierQuotes.1.purchasePrice': '采购价不能小于 0',
      'skus.0.supplierQuotes.2.supplierId': '请选择有效供应商',
      'skus.0.supplierQuotes': '每个 SKU 最多只能有一个默认报价'
    }));
  });

  it('keeps exactly one default supplier quote in a sku', () => {
    const quotes = [quote({ supplierId: 4, defaultQuote: true }), quote({ supplierId: 8 })];

    const result = setDefaultQuote(quotes, 1);

    expect(result.map((item) => item.defaultQuote)).toEqual([false, true]);
    expect(result[0]).not.toBe(quotes[0]);
  });

  it('derives a new one-SKU product with specification values as variant', () => {
    const state = createEditorState();
    state.skus = [sku({ specificationValues: ['透明'] })];

    expect(toProductPayload(state).productType).toBe('variant');
  });

  it('preserves valid loaded simple and variant product types during hydration', () => {
    const loadedSimple = loadedProductFixture('simple');
    const loadedVariant = loadedProductFixture('variant');

    expect(createEditorState(loadedSimple).loadedProductType).toBe('simple');
    expect(toProductPayload(createEditorState(loadedSimple)).productType).toBe('simple');
    expect(createEditorState(loadedVariant).loadedProductType).toBe('variant');
    expect(toProductPayload(createEditorState(loadedVariant)).productType).toBe('variant');
  });

  it('promotes a loaded simple product when an edit adds specification values', () => {
    const state = createEditorState(loadedProductFixture('simple'));
    state.skus[0].specificationValues = ['透明'];

    expect(toProductPayload(state).productType).toBe('variant');
  });
});
