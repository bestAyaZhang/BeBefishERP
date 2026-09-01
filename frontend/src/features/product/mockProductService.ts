import type { PageResult, RecordStatus } from '../masterdata/types';
import type {
  Product,
  ProductFormPayload,
  ProductQuery,
  ProductService,
  ProductSku,
  SaveSupplierQuotePayload,
  SupplierQuote,
  UploadedImage
} from './types';

let nextProductId = 100;
let nextSkuId = 1000;
let quotes: SupplierQuote[] = [];

function asProductSku(productId: number, sku: ProductFormPayload['skus'][number], index: number): ProductSku {
  const id = sku.id ?? nextSkuId++;
  return {
    ...sku,
    id,
    skuCode: sku.skuCode || `PRD-${String(productId).padStart(6, '0')}-${String(index + 1).padStart(3, '0')}`,
    specText: sku.specificationValues.join(' / '),
    safetyStockQuantity: sku.safetyStockQuantity ?? null,
    stockQuantity: sku.stockQuantity ?? 0,
    innerPackageLengthCm: sku.innerPackageLengthCm ?? null,
    innerPackageWidthCm: sku.innerPackageWidthCm ?? null,
    innerPackageHeightCm: sku.innerPackageHeightCm ?? null,
    innerPackageWeightKg: sku.innerPackageWeightKg ?? null,
    skuImageUrl: sku.skuImageUrl ?? null,
    packageImageUrl: sku.packageImageUrl ?? null,
    cartonImageUrl: sku.cartonImageUrl ?? null,
    supplierQuotes: (sku.supplierQuotes ?? []).map((quote, quoteIndex) => ({
      ...quote,
      id: quote.id ?? id * 100 + quoteIndex + 1,
      skuId: id
    })),
    defaultSku: sku.defaultSku ?? index === 0,
    status: sku.status ?? 'enabled'
  };
}

function asProduct(
  id: number,
  payload: ProductFormPayload,
  status: RecordStatus = 'enabled',
  productCode = `PRD-${String(id).padStart(6, '0')}`,
  createdAt = new Date().toISOString()
): Product {
  const skus = payload.skus.map((sku, index) => asProductSku(id, sku, index));
  const defaultSku = skus.find((sku) => sku.defaultSku) ?? skus[0];
  return {
    ...payload,
    id,
    productCode,
    categoryId: payload.categoryId ?? 0,
    mainImageUrl: null,
    defaultSupplierName: null,
    totalStock: skus.reduce((total, sku) => total + sku.stockQuantity, 0),
    totalSafetyStock: skus.reduce((total, sku) => total + (sku.safetyStockQuantity ?? 0), 0),
    defaultSalePrice: defaultSku?.defaultSalePrice ?? null,
    completenessPercent: 0,
    completenessStatus: 'incomplete',
    missingGroups: ['基本信息', 'SKU 信息', '采购信息', '包装重量', '图片资料'],
    status,
    skus,
    createdAt,
    updatedAt: new Date().toISOString()
  };
}

let products: Product[] = [asProduct(1, {
  itemNo: 'GB-001',
  productName: '高硼硅玻璃杯',
  categoryId: 1,
  brand: 'BeBefish',
  productType: 'variant',
  mainImageFileId: null,
  remark: '',
  specifications: [{ name: '颜色', values: ['透明', '烟灰'] }],
  skus: []
}, 'enabled', 'GLASS-001')];

function page(query: ProductQuery): PageResult<Product> {
  const keyword = query.keyword?.trim().toLowerCase() ?? '';
  const filtered = products.filter((product) =>
    (!query.status || product.status === query.status)
    && (!query.categoryId || product.categoryId === query.categoryId)
    && (!keyword || [product.productCode, product.itemNo, product.productName]
      .some((value) => value.toLowerCase().includes(keyword)))
  );
  return {
    records: filtered.slice((query.page - 1) * query.size, query.page * query.size),
    page: query.page,
    pageSize: query.size,
    total: filtered.length
  };
}

export const mockProductService: ProductService = {
  listProducts: async (query) => page(query),
  getCategoryCounts: async () => products.reduce<Record<number, number>>((counts, product) => {
    counts[product.categoryId] = (counts[product.categoryId] ?? 0) + 1;
    return counts;
  }, {}),
  getProduct: async (id) => {
    const product = products.find((value) => value.id === id);
    if (!product) throw new Error('产品不存在');
    return product;
  },
  createProduct: async (payload) => {
    const product = asProduct(nextProductId++, payload);
    products = [...products, product];
    return product;
  },
  updateProduct: async (id, payload) => {
    const current = products.find((value) => value.id === id);
    if (!current) throw new Error('产品不存在');
    const product = asProduct(id, payload, current.status, current.productCode, current.createdAt);
    products = products.map((value) => value.id === id ? product : value);
    return product;
  },
  changeProductStatus: async (id, status) => {
    const product = products.find((value) => value.id === id);
    if (!product) throw new Error('产品不存在');
    product.status = status;
    product.updatedAt = new Date().toISOString();
    return product;
  },
  listSupplierQuotes: async (skuId) => quotes.filter((quote) => quote.skuId === skuId),
  saveSupplierQuote: async (skuId, payload: SaveSupplierQuotePayload, quoteId?) => {
    const quote: SupplierQuote = {
      id: quoteId ?? Date.now(),
      skuId,
      supplierId: payload.supplierId,
      supplierItemNo: payload.supplierItemNo,
      purchasePrice: payload.purchasePrice,
      minPurchaseQuantity: payload.minPurchaseQuantity,
      defaultQuote: payload.defaultQuote,
      status: 'enabled'
    };
    quotes = quoteId
      ? quotes.map((value) => value.id === quoteId ? quote : value)
      : [...quotes, quote];
    return quote;
  },
  setDefaultSupplierQuote: async (skuId, quoteId) => {
    quotes = quotes.map((quote) => quote.skuId === skuId
      ? { ...quote, defaultQuote: quote.id === quoteId }
      : quote);
    const quote = quotes.find((value) => value.id === quoteId);
    if (!quote) throw new Error('报价不存在');
    return quote;
  },
  uploadImage: async (file): Promise<UploadedImage> => ({
    id: Date.now(),
    url: URL.createObjectURL(file),
    originalFileName: file.name
  })
};
