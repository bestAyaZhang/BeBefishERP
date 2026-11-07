import type { Product, ProductCatalogSupplierQuote, ProductSku } from './types';
import {
  EMPTY_VALUE,
  formatMoney,
  formatProductStatus,
  formatProductType,
  formatRecordStatus
} from './productDetailFormatting';

const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 });

export function getDefaultSupplierQuote(sku?: ProductSku): ProductCatalogSupplierQuote | undefined {
  return sku?.supplierQuotes.find((quote) => quote.status === 'enabled' && quote.defaultQuote);
}

export function formatProductPriceRange(product: Product): string {
  let minimum: number | undefined;
  let maximum: number | undefined;
  for (const sku of product.skus) {
    const price = sku.defaultSalePrice;
    if (sku.status !== 'enabled' || !Number.isFinite(price)) continue;
    minimum = minimum === undefined ? price : Math.min(minimum, price);
    maximum = maximum === undefined ? price : Math.max(maximum, price);
  }
  if (minimum === undefined) return formatMoney(product.defaultSalePrice);
  return minimum === maximum ? formatMoney(minimum) : `${formatMoney(minimum)} – ${formatMoney(maximum)}`;
}

export function formatProductNumber(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value) ? EMPTY_VALUE : numberFormatter.format(value);
}

type CsvValue = string | number | null | undefined;

interface CsvRow {
  product: Product;
  sku?: ProductSku;
  quote?: ProductCatalogSupplierQuote;
}

const csvColumns: { label: string; value: (row: CsvRow) => CsvValue }[] = [
  { label: '商品ID', value: ({ product }) => product.id },
  { label: '商品编码', value: ({ product }) => product.productCode },
  { label: '货号', value: ({ product }) => product.itemNo },
  { label: '商品名称', value: ({ product }) => product.productName },
  { label: '分类', value: ({ product }) => product.categoryName },
  { label: '品牌', value: ({ product }) => product.brand },
  { label: '商品类型', value: ({ product }) => formatProductType(product.productType) },
  { label: '商品状态', value: ({ product }) => formatProductStatus(product.status) },
  { label: 'SKU ID', value: ({ sku }) => sku?.id },
  { label: 'SKU编码', value: ({ sku }) => sku?.skuCode },
  { label: 'SKU名称', value: ({ sku }) => sku?.skuName },
  { label: '规格', value: ({ sku }) => sku?.specText?.trim() ? sku.specText : sku?.specificationValues.join(' / ') },
  { label: '条码', value: ({ sku }) => sku?.barcode },
  { label: '销售单位', value: ({ sku }) => sku?.salesUnit },
  { label: 'SKU状态', value: ({ sku }) => sku ? formatRecordStatus(sku.status) : undefined },
  { label: '默认SKU', value: ({ sku }) => sku ? (sku.defaultSku ? '是' : '否') : undefined },
  { label: '销售价(元)', value: ({ product, sku }) => sku ? sku.defaultSalePrice : product.defaultSalePrice },
  { label: '标准成本(元)', value: ({ sku }) => sku?.standardCost },
  { label: 'SKU库存', value: ({ sku }) => sku?.stockQuantity },
  { label: 'SKU安全库存', value: ({ sku }) => sku?.safetyStockQuantity },
  { label: '商品总库存', value: ({ product }) => product.totalStock },
  { label: '商品总安全库存', value: ({ product }) => product.totalSafetyStock },
  { label: '默认供应商', value: ({ quote }) => quote?.supplierName },
  { label: '供应商货号', value: ({ quote }) => quote?.supplierItemNo },
  { label: '采购价(元)', value: ({ quote }) => quote?.purchasePrice },
  { label: '最小起订量', value: ({ quote }) => quote?.minPurchaseQuantity },
  { label: '默认报价状态', value: ({ quote }) => quote ? formatRecordStatus(quote.status) : undefined },
  { label: '产品长(cm)', value: ({ sku }) => sku?.productLengthCm },
  { label: '产品宽(cm)', value: ({ sku }) => sku?.productWidthCm },
  { label: '产品高(cm)', value: ({ sku }) => sku?.productHeightCm },
  { label: '口径(cm)', value: ({ sku }) => sku?.productMouthDiameterCm },
  { label: '容量(ml)', value: ({ sku }) => sku?.capacityMl },
  { label: '外箱长(cm)', value: ({ sku }) => sku?.packageLengthCm },
  { label: '外箱宽(cm)', value: ({ sku }) => sku?.packageWidthCm },
  { label: '外箱高(cm)', value: ({ sku }) => sku?.packageHeightCm },
  { label: '外箱体积(cm³)', value: ({ sku }) => sku?.packageVolumeCm3 },
  { label: '内盒长(cm)', value: ({ sku }) => sku?.innerPackageLengthCm },
  { label: '内盒宽(cm)', value: ({ sku }) => sku?.innerPackageWidthCm },
  { label: '内盒高(cm)', value: ({ sku }) => sku?.innerPackageHeightCm },
  { label: '内盒重量(kg)', value: ({ sku }) => sku?.innerPackageWeightKg },
  { label: '装箱数量', value: ({ sku }) => sku?.cartonQuantity },
  { label: '包装方式', value: ({ sku }) => sku?.packagingMethod },
  { label: '毛重(kg)', value: ({ sku }) => sku?.grossWeightKg },
  { label: '净重(kg)', value: ({ sku }) => sku?.netWeightKg },
  { label: '克重(g)', value: ({ sku }) => sku?.gramWeightG },
  { label: '商品图片', value: ({ product }) => product.mainImageUrl },
  { label: 'SKU图片', value: ({ sku }) => sku?.skuImageUrl },
  { label: '包装图片', value: ({ sku }) => sku?.packageImageUrl },
  { label: '外箱图片', value: ({ sku }) => sku?.cartonImageUrl },
  { label: '备注', value: ({ product }) => product.remark },
  { label: '创建时间', value: ({ product }) => product.createdAt },
  { label: '更新时间', value: ({ product }) => product.updatedAt }
];

function escapeCsvCell(value: CsvValue): string {
  if (value == null || (typeof value === 'number' && !Number.isFinite(value))) return EMPTY_VALUE;
  if (typeof value === 'string' && !value.trim()) return EMPTY_VALUE;
  // Prefix before any whitespace so spreadsheet import cannot expose a formula after trimming.
  const text = typeof value === 'string' && /^\s*[=+\-@]/u.test(value) ? `'${value}` : String(value);
  return /[",\r\n]/u.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function buildProductCsv(products: Product[]): string {
  const rows = [csvColumns.map(({ label }) => escapeCsvCell(label)).join(',')];
  for (const product of products) {
    const skus: (ProductSku | undefined)[] = product.skus.length ? product.skus : [undefined];
    for (const sku of skus) {
      const row: CsvRow = { product, sku, quote: getDefaultSupplierQuote(sku) };
      rows.push(csvColumns.map(({ value }) => escapeCsvCell(value(row))).join(','));
    }
  }
  return `\uFEFF${rows.join('\r\n')}\r\n`;
}

export function downloadProductCsv(products: Product[], filename: string): void {
  const blob = new Blob([buildProductCsv(products)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  try {
    anchor.click();
  } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
