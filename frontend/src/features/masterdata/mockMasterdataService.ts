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
import { generateCustomerNumber } from './customerNumber';
import { generateWarehouseNumber } from './warehouseNumber';

let nextId = 10;
let categories: Category[] = [
  { id: 1, categoryCode: 'GLASS', categoryName: '玻璃杯', parentId: null, level: 1, sortOrder: 10, status: 'enabled', remark: '主力产品分类' },
  { id: 2, categoryCode: 'BEER', categoryName: '啤酒杯', parentId: null, level: 1, sortOrder: 20, status: 'enabled', remark: '' },
  { id: 3, categoryCode: 'WINE', categoryName: '酒杯', parentId: null, level: 1, sortOrder: 30, status: 'enabled', remark: '' }
];
let customers: Customer[] = [
  { id: 4, customerNo: 'WALK_IN', customerName: '散客', contactPerson: '', mobile: '', telephone: '', province: '', city: '', district: '', detailAddress: '', transportMethod: 'pickup', settlementCycle: 'cash', system: true, status: 'enabled', remark: '系统预置客户' },
  { id: 5, customerNo: 'CUS-HZ-001', customerName: '杭州酒店用品店', contactPerson: '周经理', mobile: '13800138000', telephone: '', province: '浙江', city: '杭州', district: '余杭', detailAddress: '良渚街道', transportMethod: 'delivery', settlementCycle: 'monthly', system: false, status: 'enabled', remark: '' }
];
let suppliers: Supplier[] = [
  { id: 6, supplierNo: 'SUP-YW-001', supplierName: '义乌玻璃制品厂', contactPerson: '陈经理', mobile: '13900139000', telephone: '', address: '浙江省义乌市', status: 'enabled', remark: '' },
  { id: 7, supplierNo: 'SUP-SH-001', supplierName: '上海包装材料厂', contactPerson: '王经理', mobile: '', telephone: '021-66668888', address: '上海市嘉定区', status: 'enabled', remark: '' }
];
let warehouses: Warehouse[] = [
  { id: 8, warehouseNo: 'WH-HZ-MAIN', warehouseName: '杭州主仓', address: '浙江省杭州市余杭区', defaultWarehouse: true, status: 'enabled', remark: '' },
  { id: 9, warehouseNo: 'WH-YW-SPARE', warehouseName: '义乌备货仓', address: '浙江省义乌市', defaultWarehouse: false, status: 'enabled', remark: '' }
];

function page<T extends Record<string, unknown>>(records: T[], query: ListQuery, searchable: string[]): PageResult<T> {
  const keyword = query.keyword?.trim().toLowerCase() ?? '';
  const filtered = records.filter((record) => {
    const matchesKeyword = !keyword || searchable.some((key) => String(record[key] ?? '').toLowerCase().includes(keyword));
    const matchesStatus = !query.status || record.status === query.status;
    return matchesKeyword && matchesStatus;
  });
  const start = (query.page - 1) * query.size;
  return { records: filtered.slice(start, start + query.size), page: query.page, pageSize: query.size, total: filtered.length };
}

function updateStatus<T extends { id: number; status: RecordStatus }>(records: T[], id: number, status: RecordStatus) {
  const record = records.find((candidate) => candidate.id === id);
  if (!record) throw new Error('记录不存在');
  record.status = status;
  return record;
}

export const mockMasterdataService: MasterdataService = {
  listCategories: (query) => Promise.resolve(page(categories, query, ['categoryCode', 'categoryName'])),
  createCategory: (payload: SaveCategoryPayload) => {
    const id = nextId++;
    const value: Category = { id, categoryCode: `CAT-${String(id).padStart(3, '0')}`, ...payload, parentId: null, level: 1, status: 'enabled' };
    categories = [...categories, value];
    return Promise.resolve(value);
  },
  updateCategory: (id, payload) => {
    const record = categories.find((candidate) => candidate.id === id);
    if (!record) return Promise.reject(new Error('分类不存在'));
    Object.assign(record, payload);
    return Promise.resolve(record);
  },
  changeCategoryStatus: (id, status) => Promise.resolve(updateStatus(categories, id, status)),
  listCustomers: (query) => Promise.resolve(page(customers, query, ['customerNo', 'customerName'])),
  createCustomer: (payload: SaveCustomerPayload) => {
    const value: Customer = { id: nextId++, customerNo: generateCustomerNumber(), ...payload, system: false, status: 'enabled' };
    customers = [...customers, value];
    return Promise.resolve(value);
  },
  updateCustomer: (id, payload) => {
    const record = customers.find((candidate) => candidate.id === id);
    if (!record) return Promise.reject(new Error('客户不存在'));
    Object.assign(record, payload);
    return Promise.resolve(record);
  },
  changeCustomerStatus: (id, status) => Promise.resolve(updateStatus(customers, id, status)),
  listSuppliers: (query) => Promise.resolve(page(suppliers, query, ['supplierNo', 'supplierName'])),
  createSupplier: (payload: SaveSupplierPayload) => {
    const id = nextId++;
    const value: Supplier = { id, supplierNo: `SUP-${String(id).padStart(3, '0')}`, ...payload, status: 'enabled' };
    suppliers = [...suppliers, value];
    return Promise.resolve(value);
  },
  updateSupplier: (id, payload) => {
    const record = suppliers.find((candidate) => candidate.id === id);
    if (!record) return Promise.reject(new Error('供应商不存在'));
    Object.assign(record, payload);
    return Promise.resolve(record);
  },
  changeSupplierStatus: (id, status) => Promise.resolve(updateStatus(suppliers, id, status)),
  listWarehouses: (query) => Promise.resolve(page(warehouses, query, ['warehouseNo', 'warehouseName'])),
  createWarehouse: (payload: SaveWarehousePayload) => {
    const value: Warehouse = { id: nextId++, warehouseNo: generateWarehouseNumber(), ...payload, status: 'enabled' };
    if (value.defaultWarehouse) warehouses.forEach((record) => (record.defaultWarehouse = false));
    warehouses = [...warehouses, value];
    return Promise.resolve(value);
  },
  updateWarehouse: (id, payload) => {
    const record = warehouses.find((candidate) => candidate.id === id);
    if (!record) return Promise.reject(new Error('仓库不存在'));
    if (payload.defaultWarehouse) warehouses.forEach((candidate) => (candidate.defaultWarehouse = false));
    Object.assign(record, payload);
    return Promise.resolve(record);
  },
  changeWarehouseStatus: (id, status) => Promise.resolve(updateStatus(warehouses, id, status)),
  setDefaultWarehouse: (id) => {
    const record = warehouses.find((candidate) => candidate.id === id);
    if (!record) return Promise.reject(new Error('仓库不存在'));
    warehouses.forEach((candidate) => (candidate.defaultWarehouse = candidate.id === id));
    return Promise.resolve(record);
  },
  async listActiveCustomers() {
    return customers.filter((record) => record.status === 'enabled');
  },
  async listActiveWarehouses() {
    return warehouses.filter((record) => record.status === 'enabled');
  }
};
