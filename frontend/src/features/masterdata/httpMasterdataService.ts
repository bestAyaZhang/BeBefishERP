import { request } from '../../services/http';
import type {
  Category,
  Customer,
  ListQuery,
  MasterdataService,
  PageResult,
  RecordStatus,
  SaveCategoryPayload,
  SaveCustomerPayload,
  SaveSupplierPayload,
  SaveWarehousePayload,
  Supplier,
  Warehouse
} from './types';

function queryString(query: ListQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  return params.toString();
}

function list<T>(path: string, query: ListQuery): Promise<PageResult<T>> {
  return request<PageResult<T>>(`${path}?${queryString(query)}`);
}

function save<T>(path: string, payload: unknown, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  return request<T>(path, { method, body: JSON.stringify(payload) });
}

function changeStatus<T>(path: string, status: RecordStatus): Promise<T> {
  return save<T>(path, { status });
}

export const httpMasterdataService: MasterdataService = {
  listCategories: (query) => list<Category>('/api/categories', query),
  createCategory: (payload: SaveCategoryPayload) => save<Category>('/api/categories', payload),
  updateCategory: (id, payload) => save<Category>(`/api/categories/${id}`, payload, 'PUT'),
  changeCategoryStatus: (id, status) => changeStatus<Category>(`/api/categories/${id}/status`, status),
  listCustomers: (query) => list<Customer>('/api/customers', query),
  createCustomer: (payload: SaveCustomerPayload) => save<Customer>('/api/customers', payload),
  updateCustomer: (id, payload) => save<Customer>(`/api/customers/${id}`, payload, 'PUT'),
  changeCustomerStatus: (id, status) => changeStatus<Customer>(`/api/customers/${id}/status`, status),
  listSuppliers: (query) => list<Supplier>('/api/suppliers', query),
  createSupplier: (payload: SaveSupplierPayload) => save<Supplier>('/api/suppliers', payload),
  updateSupplier: (id, payload) => save<Supplier>(`/api/suppliers/${id}`, payload, 'PUT'),
  changeSupplierStatus: (id, status) => changeStatus<Supplier>(`/api/suppliers/${id}/status`, status),
  listWarehouses: (query) => list<Warehouse>('/api/warehouses', query),
  createWarehouse: (payload: SaveWarehousePayload) => save<Warehouse>('/api/warehouses', payload),
  updateWarehouse: (id, payload) => save<Warehouse>(`/api/warehouses/${id}`, payload, 'PUT'),
  changeWarehouseStatus: (id, status) => changeStatus<Warehouse>(`/api/warehouses/${id}/status`, status),
  setDefaultWarehouse: (id) => save<Warehouse>(`/api/warehouses/${id}/default`, {}),
  async listActiveCustomers() {
    return (await list<Customer>('/api/customers', { page: 1, size: 100, status: 'enabled' })).records;
  },
  async listActiveWarehouses() {
    return (await list<Warehouse>('/api/warehouses', { page: 1, size: 100, status: 'enabled' })).records;
  }
};
