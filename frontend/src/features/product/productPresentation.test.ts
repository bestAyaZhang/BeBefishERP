import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  buildProductCsv,
  downloadProductCsv,
  formatProductNumber,
  formatProductPriceRange,
  getDefaultSupplierQuote
} from './productPresentation';
import type { Product, ProductCatalogSupplierQuote, ProductSku } from './types';

function sku(overrides: Partial<ProductSku> = {}): ProductSku {
  return {
    id: 11, skuCode: 'CUP-450', barcode: '6900000000001', skuName: '450ml 透明',
    specText: '透明 / 450ml', specificationValues: ['透明', '450ml'], salesUnit: '只',
    defaultSalePrice: 12.5, standardCost: 6, stockQuantity: 23, safetyStockQuantity: 5,
    productLengthCm: 8, productWidthCm: 8, productHeightCm: 12, capacityMl: 450,
    packageLengthCm: 48, packageWidthCm: 32, packageHeightCm: 28, packageVolumeCm3: 43008,
    innerPackageLengthCm: 9, innerPackageWidthCm: 9, innerPackageHeightCm: 13,
    innerPackageWeightKg: 0.05, cartonQuantity: 24, packagingMethod: '彩盒',
    grossWeightKg: 8.6, netWeightKg: 7.2, gramWeightG: 300,
    skuImageFileId: null, skuImageUrl: null, packageImageFileId: null, packageImageUrl: null,
    cartonImageFileId: null, cartonImageUrl: null, supplierQuotes: [],
    defaultSku: true, status: 'enabled', ...overrides
  };
}

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1, productCode: 'P001', itemNo: 'CUP', productName: '玻璃杯', categoryId: 2,
    categoryName: '水杯', brand: 'BeBeFish', productType: 'variant', mainImageFileId: null,
    mainImageUrl: null, defaultSupplierName: '汇总供应商', totalStock: 900,
    totalSafetyStock: 50, defaultSalePrice: 99, completenessPercent: 80,
    completenessStatus: 'incomplete', missingGroups: [], status: 'enabled', remark: null,
    specifications: [{ name: '容量', values: ['450ml'] }], skus: [sku()],
    createdAt: '2026-11-01T12:00:00', updatedAt: '2026-11-05T10:00:00', ...overrides
  };
}

function quote(overrides: Partial<ProductCatalogSupplierQuote> = {}): ProductCatalogSupplierQuote {
  return {
    id: 101, skuId: 11, supplierId: 7, supplierName: '真实供应商', supplierItemNo: 'SUP-CUP',
    purchasePrice: 5.25, minPurchaseQuantity: 48, defaultQuote: true, status: 'enabled', ...overrides
  };
}

function csvRecords(csv: string): Record<string, string>[] {
  const [header, ...rows] = csv.slice(1).split('\r\n');
  const fields = header.split(',');
  return rows.filter(Boolean).map((row) => (
    Object.fromEntries(row.split(',').map((value, index) => [fields[index], value]))
  ));
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  document.body.innerHTML = '';
});

describe('product presentation', () => {
  it('uses only an explicitly default enabled quote without silently choosing another supplier', () => {
    const selected = quote({ id: 3 });
    expect(getDefaultSupplierQuote(sku({ supplierQuotes: [
      quote({ id: 1, status: 'disabled' }), quote({ id: 2, defaultQuote: false }), selected
    ] }))).toBe(selected);
    expect(getDefaultSupplierQuote(sku({ supplierQuotes: [quote({ defaultQuote: false })] }))).toBeUndefined();
    expect(getDefaultSupplierQuote(sku({ supplierQuotes: [quote({ status: 'disabled' })] }))).toBeUndefined();
    expect(getDefaultSupplierQuote()).toBeUndefined();
  });

  it('prices the enabled finite SKU range while preserving zero and ignoring stale summary prices', () => {
    expect(formatProductPriceRange(product({ skus: [
      sku({ defaultSalePrice: 12.5 }), sku({ defaultSalePrice: 0 }),
      sku({ defaultSalePrice: 999, status: 'disabled' }),
      sku({ defaultSalePrice: Number.NaN }), sku({ defaultSalePrice: Number.POSITIVE_INFINITY })
    ] }))).toBe('¥0.00 – ¥12.50');
    expect(formatProductPriceRange(product({ skus: [sku(), sku()] }))).toBe('¥12.50');
  });

  it('falls back to a finite product sale price only when no enabled SKU price exists', () => {
    expect(formatProductPriceRange(product({ defaultSalePrice: 0, skus: [
      sku({ defaultSalePrice: 12, status: 'disabled' }), sku({ defaultSalePrice: Number.NaN })
    ] }))).toBe('¥0.00');
    expect(formatProductPriceRange(product({ defaultSalePrice: Number.POSITIVE_INFINITY, skus: [] }))).toBe('--');
    expect(formatProductPriceRange(product({ defaultSalePrice: null, skus: [] }))).toBe('--');
  });

  it('handles large SKU collections without exceeding JavaScript argument limits', () => {
    const skus = Array<ProductSku>(200_000).fill(sku());
    skus[0] = sku({ defaultSalePrice: 0 });
    skus[skus.length - 1] = sku({ defaultSalePrice: 25 });
    expect(formatProductPriceRange(product({ skus }))).toBe('¥0.00 – ¥25.00');
  });

  it('formats finite quantities in Chinese locale with at most three decimals', () => {
    expect(formatProductNumber(12345.6789)).toBe('12,345.679');
    expect(formatProductNumber(0)).toBe('0');
    expect(formatProductNumber(-2.5)).toBe('-2.5');
    for (const value of [null, undefined, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(formatProductNumber(value)).toBe('--');
    }
  });
});

describe('product CSV', () => {
  it('exports each SKU mouth diameter separately from length and width', () => {
    const rows = csvRecords(buildProductCsv([product({ skus: [
      sku({ productMouthDiameterCm: 8.2, productLengthCm: null, productWidthCm: null }),
      sku({ id: 12, productMouthDiameterCm: 0 }),
      sku({ id: 13, productMouthDiameterCm: Number.POSITIVE_INFINITY })
    ] })]));
    expect(rows[0]).toMatchObject({ '口径(cm)': '8.2', '产品长(cm)': '--', '产品宽(cm)': '--' });
    expect(rows[1]['口径(cm)']).toBe('0');
    expect(rows[2]['口径(cm)']).toBe('--');
  });

  it('exports each real SKU with its own prices, default quote and packing while retaining server totals', () => {
    const csv = buildProductCsv([product({ skus: [
      sku({ supplierQuotes: [quote()] }),
      sku({ id: 12, skuCode: 'CUP-600', specText: null, specificationValues: ['蓝色', '600ml'],
        defaultSku: false, status: 'disabled', defaultSalePrice: 0, stockQuantity: 0,
        capacityMl: 600, packageLengthCm: 56, supplierQuotes: [quote({ status: 'disabled' })] })
    ] })]);
    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv.replaceAll('\r\n', '')).not.toMatch(/[\r\n]/);
    const rows = csvRecords(csv);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      商品ID: '1', 商品编码: 'P001', 货号: 'CUP', 商品名称: '玻璃杯', 分类: '水杯',
      品牌: 'BeBeFish', 商品状态: '在售', SKU编码: 'CUP-450', SKU名称: '450ml 透明',
      规格: '透明 / 450ml', 条码: '6900000000001', 销售单位: '只', SKU状态: '启用',
      '销售价(元)': '12.5', '标准成本(元)': '6', SKU库存: '23', SKU安全库存: '5',
      商品总库存: '900', 商品总安全库存: '50', 默认供应商: '真实供应商',
      供应商货号: 'SUP-CUP', '采购价(元)': '5.25', 最小起订量: '48',
      '产品长(cm)': '8', '产品宽(cm)': '8', '产品高(cm)': '12', '容量(ml)': '450',
      '外箱长(cm)': '48', '外箱宽(cm)': '32', '外箱高(cm)': '28', '外箱体积(cm³)': '43008',
      '内盒长(cm)': '9', '内盒宽(cm)': '9', '内盒高(cm)': '13', '内盒重量(kg)': '0.05',
      装箱数量: '24', 包装方式: '彩盒', '毛重(kg)': '8.6', '净重(kg)': '7.2', '克重(g)': '300'
    });
    expect(rows[1]).toMatchObject({
      SKU编码: 'CUP-600', 规格: '蓝色 / 600ml', SKU状态: '停用', '销售价(元)': '0',
      SKU库存: '0', 商品总库存: '900', 默认供应商: '--', '采购价(元)': '--',
      '容量(ml)': '600', '外箱长(cm)': '56'
    });
  });

  it('retains a product with no SKUs without inventing SKU, stock or supplier values', () => {
    const rows = csvRecords(buildProductCsv([product({
      skus: [], defaultSalePrice: 0, totalStock: 0, totalSafetyStock: Number.NaN
    })]));
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      商品编码: 'P001', SKU编码: '--', '销售价(元)': '0', SKU库存: '--',
      商品总库存: '0', 商品总安全库存: '--', 默认供应商: '--', '采购价(元)': '--'
    });
  });

  it('escapes commas, quotes and line breaks without losing the original text', () => {
    const csv = buildProductCsv([product({ productName: '测试, "原杯"\r\n产品' })]);
    expect(csv).toContain('"测试, ""原杯""\r\n产品"');
  });

  it.each(['=1+1', '+SUM(A1)', '-10+20', '@SUM(A1)', '  =1+1', '\t+SUM(A1)', '\r@SUM(A1)'])(
    'neutralizes spreadsheet formulas including whitespace prefixes: %j', (value) => {
      const csv = buildProductCsv([product({ productName: value, itemNo: value,
        skus: [sku({ skuCode: value, supplierQuotes: [quote({ supplierName: value })] })] })]);
      const escaped = `'${value}`;
      expect(csv).toContain(value.includes('\r') ? `"${escaped}"` : escaped);
      expect(csv.match(/'/g)).toHaveLength(4);
    }
  );

  it('omits non-finite numeric data while preserving exact numeric precision in the export', () => {
    const [row] = csvRecords(buildProductCsv([product({ skus: [sku({
      defaultSalePrice: Number.NaN, stockQuantity: Number.POSITIVE_INFINITY,
      grossWeightKg: 1.234567, netWeightKg: 0, packageLengthCm: Number.NEGATIVE_INFINITY
    })] })]));
    expect(row).toMatchObject({
      '销售价(元)': '--', SKU库存: '--', '毛重(kg)': '1.234567', '净重(kg)': '0', '外箱长(cm)': '--'
    });
  });

  it('downloads a UTF-8 CSV with the requested filename and releases its temporary browser resources', async () => {
    vi.useFakeTimers();
    const blobs: Blob[] = [];
    const revoked: string[] = [];
    const originalUrl = URL;
    class DownloadUrl extends originalUrl {
      static createObjectURL(blob: Blob) { blobs.push(blob); return 'blob:product-export'; }
      static revokeObjectURL(url: string) { revoked.push(url); }
    }
    vi.stubGlobal('URL', DownloadUrl);
    let clickedAnchor: HTMLAnchorElement | undefined;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedAnchor = this;
    });
    downloadProductCsv([product()], '商品当前页.csv');
    expect(clickedAnchor?.download).toBe('商品当前页.csv');
    expect(clickedAnchor?.href).toBe('blob:product-export');
    expect(blobs[0].type).toBe('text/csv;charset=utf-8');
    expect(blobs[0].size).toBeGreaterThan(0);
    expect(document.querySelector('a[download]')).toBeNull();
    await vi.runAllTimersAsync();
    expect(revoked).toEqual(['blob:product-export']);
  });
});
