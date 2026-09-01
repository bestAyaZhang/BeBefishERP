import { describe, expect, it } from 'vitest';
import { mapProductToPrototype } from './prototypeProductMapper';
import { productFixture } from './productTestFixtures';

describe('prototype product mapper', () => {
  it('maps API product and SKU data to the approved prototype row', () => {
    const row = mapProductToPrototype(productFixture({
      id: 8,
      productCode: 'GLASS-001',
      itemNo: 'GB-001',
      productName: '玻璃杯',
      categoryId: 2,
      brand: 'BeBefish',
      productType: 'simple',
      mainImageFileId: null,
      remark: '',
      specifications: [],
      status: 'enabled',
      skus: [{ id: 20, skuCode: 'GLASS-001-DEFAULT', skuName: '玻璃杯', specificationValues: [], salesUnit: '只', defaultSalePrice: 19.9, standardCost: 8.5, packageLengthCm: null, packageWidthCm: null, packageHeightCm: null, packageVolumeCm3: null, netWeightKg: null, grossWeightKg: null, gramWeightG: null, packagingMethod: '', cartonQuantity: null, skuImageFileId: null, packageImageFileId: null, cartonImageFileId: null, defaultSku: true, status: 'enabled', barcode: '' }]
    }), '玻璃杯');

    expect(row.name).toBe('玻璃杯');
    expect(row.sku).toBe('GLASS-001-DEFAULT');
    expect(row.category).toBe('玻璃杯');
    expect(row.price).toBe('¥19.90');
    expect(row.status).toBe('在售');
  });
});
