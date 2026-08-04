export type RecordStatus = 'enabled' | 'disabled';

export interface MasterdataRow {
  id: number;
  [key: string]: unknown;
}

export interface PageResult<T> {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ListQuery {
  page: number;
  size: number;
  keyword?: string;
  status?: RecordStatus;
}

export interface Category extends MasterdataRow {
  categoryCode: string;
  categoryName: string;
  parentId: number | null;
  level: number;
  sortOrder: number;
  status: RecordStatus;
  remark: string;
}

export type SaveCategoryPayload = Pick<Category, 'categoryName' | 'sortOrder' | 'remark'>;

export interface Customer extends MasterdataRow {
  customerNo: string;
  customerName: string;
  contactPerson: string;
  mobile: string;
  telephone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  transportMethod: string;
  settlementCycle: string;
  system: boolean;
  status: RecordStatus;
  remark: string;
}

export interface SaveCustomerPayload {
  customerName: string;
  contactPerson: string;
  mobile: string;
  telephone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  transportMethod: string;
  settlementCycle: string;
  remark: string;
}

export interface Supplier extends MasterdataRow {
  supplierNo: string;
  supplierName: string;
  contactPerson: string;
  mobile: string;
  telephone: string;
  address: string;
  status: RecordStatus;
  remark: string;
}

export interface SaveSupplierPayload {
  supplierName: string;
  contactPerson: string;
  mobile: string;
  telephone: string;
  address: string;
  remark: string;
}

export interface Warehouse extends MasterdataRow {
  warehouseNo: string;
  warehouseName: string;
  address: string;
  defaultWarehouse: boolean;
  status: RecordStatus;
  remark: string;
}

export interface SaveWarehousePayload {
  warehouseName: string;
  address: string;
  defaultWarehouse: boolean;
  remark: string;
}

export interface MasterdataService {
  listCategories(query: ListQuery): Promise<PageResult<Category>>;
  createCategory(payload: SaveCategoryPayload): Promise<Category>;
  updateCategory(id: number, payload: SaveCategoryPayload): Promise<Category>;
  changeCategoryStatus(id: number, status: RecordStatus): Promise<Category>;
  listCustomers(query: ListQuery): Promise<PageResult<Customer>>;
  createCustomer(payload: SaveCustomerPayload): Promise<Customer>;
  updateCustomer(id: number, payload: SaveCustomerPayload): Promise<Customer>;
  changeCustomerStatus(id: number, status: RecordStatus): Promise<Customer>;
  listSuppliers(query: ListQuery): Promise<PageResult<Supplier>>;
  createSupplier(payload: SaveSupplierPayload): Promise<Supplier>;
  updateSupplier(id: number, payload: SaveSupplierPayload): Promise<Supplier>;
  changeSupplierStatus(id: number, status: RecordStatus): Promise<Supplier>;
  listWarehouses(query: ListQuery): Promise<PageResult<Warehouse>>;
  createWarehouse(payload: SaveWarehousePayload): Promise<Warehouse>;
  updateWarehouse(id: number, payload: SaveWarehousePayload): Promise<Warehouse>;
  changeWarehouseStatus(id: number, status: RecordStatus): Promise<Warehouse>;
  setDefaultWarehouse(id: number): Promise<Warehouse>;
  listActiveCustomers(): Promise<Customer[]>;
  listActiveWarehouses(): Promise<Warehouse[]>;
}
