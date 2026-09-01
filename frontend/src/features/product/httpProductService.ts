import { request } from '../../services/http';
import type { PageResult, RecordStatus } from '../masterdata/types';
import type { Product, ProductFormPayload, ProductQuery, ProductService, ProductSupplierQuote, SaveSupplierQuotePayload, UploadedImage } from './types';

function listPath(query: ProductQuery) { const params = new URLSearchParams({ page: String(query.page), size: String(query.size) }); if (query.keyword?.trim()) params.set('keyword', query.keyword.trim()); if (query.status) params.set('status', query.status); if (query.categoryId) params.set('categoryId', String(query.categoryId)); if (query.supplierId) params.set('supplierId', String(query.supplierId)); return `/api/products?${params}`; }

export const httpProductService: ProductService = {
  listProducts: (query) => request<PageResult<Product>>(listPath(query)),
  getCategoryCounts: async () => {
    const counts = await request<Record<string, number>>('/api/products/category-counts');
    return Object.fromEntries(
      Object.entries(counts).map(([categoryId, count]) => [Number(categoryId), count])
    ) as Record<number, number>;
  },
  getProduct: (id) => request<Product>(`/api/products/${id}`),
  createProduct: (payload: ProductFormPayload) => request<Product>('/api/products', { method: 'POST', body: JSON.stringify(payload) }),
  updateProduct: (id, payload) => request<Product>(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  changeProductStatus: (id, status: RecordStatus) => request<Product>(`/api/products/${id}/status`, { method: 'POST', body: JSON.stringify({ status }) }),
  listSupplierQuotes: (skuId) => request<ProductSupplierQuote[]>(`/api/skus/${skuId}/supplier-quotes`),
  saveSupplierQuote: (skuId, payload: SaveSupplierQuotePayload, quoteId?) => request<ProductSupplierQuote>(`/api/skus/${skuId}/supplier-quotes${quoteId ? `/${quoteId}` : ''}`, { method: quoteId ? 'PUT' : 'POST', body: JSON.stringify(payload) }),
  setDefaultSupplierQuote: (skuId, quoteId, syncStandardCost) => request<ProductSupplierQuote>(`/api/skus/${skuId}/supplier-quotes/${quoteId}/default`, { method: 'POST', body: JSON.stringify({ syncStandardCost }) }),
  uploadImage: (file) => { const formData = new FormData(); formData.append('file', file); return request<UploadedImage>('/api/files/images', { method: 'POST', body: formData }); }
};
