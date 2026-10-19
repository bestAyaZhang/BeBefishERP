import { request } from '../../services/http';
import { createService } from '../../services/serviceFactory';
import { getMockSalesOrder, listMockSalesOrders } from '../sales/mockSalesOrderStore';
import type { Customer } from './types';
import { mockMasterdataService } from './mockMasterdataService';

export interface CustomerPlatform { id: number; name: string; status: 'enabled' | 'disabled' }
export interface CustomerPrice {
  skuId: number; itemNo: string; productName: string; skuCode: string; skuName: string;
  salesUnit: string; status: string; defaultSalePrice: number; unitPrice: number;
}
export interface CustomerBill {
  id: number; orderNo: string; orderDate: string; totalAmount: number;
  receivedAmount: number; outstandingAmount: number;
}
export interface BillQuery { page: number; size: number; dateFrom?: string; dateTo?: string }
export interface CustomerBills {
  records: CustomerBill[]; page: number; pageSize: number; total: number;
  summary: { orderCount: number; totalAmount: number; receivedAmount: number; outstandingAmount: number };
}
export interface CustomerAccountService {
  getCustomer(id: number): Promise<Customer>;
  listPlatforms(): Promise<CustomerPlatform[]>;
  listPrices(id: number): Promise<CustomerPrice[]>;
  savePrice(id: number, skuId: number, unitPrice: number): Promise<CustomerPrice[]>;
  deletePrice(id: number, skuId: number): Promise<void>;
  listBills(id: number, query: BillQuery): Promise<CustomerBills>;
}
export const httpCustomerAccountService: CustomerAccountService = {
  getCustomer: id => request(`/api/customers/${id}`),
  listPlatforms: () => request('/api/customers/platform-options'),
  listPrices: id => request(`/api/customers/${id}/prices`),
  savePrice: (id, skuId, unitPrice) => request(`/api/customers/${id}/prices/${skuId}`, {
    method: 'PUT', body: JSON.stringify({ unitPrice })
  }),
  deletePrice: (id, skuId) => request(`/api/customers/${id}/prices/${skuId}`, { method: 'DELETE' }),
  listBills: (id, query) => {
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    if (query.dateFrom) params.set('dateFrom', query.dateFrom);
    if (query.dateTo) params.set('dateTo', query.dateTo);
    return request(`/api/customers/${id}/bills?${params}`);
  }
};

export function createMockCustomerAccountService(): CustomerAccountService {
  const prices = new Map<number, CustomerPrice[]>();
  return {
    async getCustomer(id) {
      return mockMasterdataService.getCustomer!(id);
    },
    async listPlatforms() { return [
      { id: 1, name: '拼多多', status: 'enabled' }, { id: 2, name: '1688', status: 'enabled' },
      { id: 3, name: '散户', status: 'enabled' }
    ]; },
    async listPrices(id) { return (prices.get(id) ?? []).map(p => ({ ...p })); },
    async savePrice(id, skuId, unitPrice) {
      if (!Number.isFinite(unitPrice) || unitPrice < 0 || Math.abs(unitPrice * 100 - Math.round(unitPrice * 100)) > 1e-7) {
        throw new Error('单价必须为非负金额且最多两位小数');
      }
      const customer = await mockMasterdataService.getCustomer!(id);
      if (customer.status !== 'enabled') throw new Error('客户不存在或已停用');
      const [{ mockProductService }, { toProductOptions }] = await Promise.all([
        import('../product/mockProductService'), import('../sales/salesCatalogService')
      ]);
      const products = [];
      for (let page = 1; ; page++) {
        const result = await mockProductService.listProducts({ page, size: 100, status: 'enabled' });
        products.push(...result.records);
        if (products.length >= result.total || !result.records.length) break;
      }
      const product = toProductOptions(products).find(p => p.skuId === skuId);
      if (!product) throw new Error('货品不存在或已停用');
      prices.set(id, [...(prices.get(id) ?? []).filter(p => p.skuId !== skuId), { ...product, status: 'enabled', unitPrice }]);
      return this.listPrices(id);
    },
    async deletePrice(id, skuId) { prices.set(id, (prices.get(id) ?? []).filter(p => p.skuId !== skuId)); },
    async listBills(id, query) {
      if (query.dateFrom && query.dateTo && query.dateFrom > query.dateTo) throw new Error('开始日期不能晚于结束日期');
      const all = listMockSalesOrders().filter(o => o.status === 'confirmed' && getMockSalesOrder(o.id)?.customerId === id
        && (!query.dateFrom || o.orderDate >= query.dateFrom) && (!query.dateTo || o.orderDate <= query.dateTo))
        .sort((a, b) => b.orderDate.localeCompare(a.orderDate) || b.id - a.id)
        .map(o => ({ ...o, outstandingAmount: Number((o.totalAmount - o.receivedAmount).toFixed(2)) }));
      const sum = (key: 'totalAmount' | 'receivedAmount' | 'outstandingAmount') => Number(all.reduce((s, o) => s + Math.round(o[key] * 100), 0) / 100);
      return { records: all.slice((query.page - 1) * query.size, query.page * query.size), page: query.page,
        pageSize: query.size, total: all.length,
        summary: { orderCount: all.length, totalAmount: sum('totalAmount'), receivedAmount: sum('receivedAmount'), outstandingAmount: sum('outstandingAmount') } };
    }
  };
}
export const customerAccountService = createService(createMockCustomerAccountService, () => httpCustomerAccountService);
