import type { PageResult, RecordStatus } from '../masterdata/types';
import type { Product, ProductFormPayload, ProductQuery, ProductService, SaveSupplierQuotePayload, SupplierQuote, UploadedImage } from './types';

let nextProductId = 100;
let products: Product[] = [{ id: 1, productCode: 'GLASS-001', itemNo: 'GB-001', productName: '高硼硅玻璃杯', categoryId: 1, brand: 'BeBefish', productType: 'variant', mainImageFileId: null, remark: '', status: 'enabled', specifications: [{ name: '颜色', values: ['透明', '烟灰'] }, { name: '花纹', values: ['竖纹', '樱花纹'] }], skus: [] }];
let quotes: SupplierQuote[] = [];

function page(query: ProductQuery): PageResult<Product> {
  const keyword = query.keyword?.trim().toLowerCase() ?? '';
  const filtered = products.filter((product) => (!query.status || product.status === query.status) && (!keyword || [product.productCode, product.itemNo, product.productName].some((value) => value.toLowerCase().includes(keyword))));
  return { records: filtered.slice((query.page - 1) * query.size, query.page * query.size), page: query.page, pageSize: query.size, total: filtered.length };
}

function asProduct(id: number, payload: ProductFormPayload, status: RecordStatus = 'enabled', productCode = `PRD-${String(id).padStart(6, '0')}`): Product { return { id, productCode, ...payload, categoryId: payload.categoryId ?? 0, status }; }

export const mockProductService: ProductService = {
  listProducts: async (query) => page(query),
  getProduct: async (id) => { const product = products.find((value) => value.id === id); if (!product) throw new Error('产品不存在'); return product; },
  createProduct: async (payload) => { const product = asProduct(nextProductId++, payload); products = [...products, product]; return product; },
  updateProduct: async (id, payload) => { const current = products.find((value) => value.id === id); if (!current) throw new Error('产品不存在'); const product = asProduct(id, payload, current.status, current.productCode); products = products.map((value) => value.id === id ? product : value); return product; },
  changeProductStatus: async (id, status) => { const product = products.find((value) => value.id === id); if (!product) throw new Error('产品不存在'); product.status = status; return product; },
  listSupplierQuotes: async (skuId) => quotes.filter((quote) => quote.skuId === skuId),
  saveSupplierQuote: async (skuId, payload: SaveSupplierQuotePayload, quoteId?) => { const quote: SupplierQuote = { id: quoteId ?? Date.now(), skuId, supplierId: payload.supplierId, supplierItemNo: payload.supplierItemNo, purchasePrice: payload.purchasePrice, minPurchaseQuantity: payload.minPurchaseQuantity, defaultQuote: payload.defaultQuote, status: 'enabled' }; quotes = quoteId ? quotes.map((value) => value.id === quoteId ? quote : value) : [...quotes, quote]; return quote; },
  setDefaultSupplierQuote: async (skuId, quoteId) => { quotes = quotes.map((quote) => quote.skuId === skuId ? { ...quote, defaultQuote: quote.id === quoteId } : quote); const quote = quotes.find((value) => value.id === quoteId); if (!quote) throw new Error('报价不存在'); return quote; },
  uploadImage: async (file): Promise<UploadedImage> => ({ id: Date.now(), url: URL.createObjectURL(file), originalFileName: file.name })
};
