import type { Customer, PageResult, Warehouse } from '../masterdata/types';

export interface SaleProductOption {
  skuId: number;
  skuCode: string;
  itemNo: string;
  barcode: string;
  productName: string;
  skuName: string;
  specification: string;
  packagingMethod: string;
  cartonQuantity: number | null;
  salesUnit: string;
  defaultSalePrice: number;
}

export interface SalesCatalog {
  customers: Customer[];
  warehouses: Warehouse[];
  products: SaleProductOption[];
}

export interface SalesCatalogService {
  loadCatalog(): Promise<SalesCatalog>;
}

export interface SalesOrderLinePayload {
  skuId: number;
  quantity: number;
  unitPrice: number;
  amount: number;
}

export interface ConfirmSalesOrderPayload {
  customerId: number;
  customerName: string;
  warehouseId: number;
  warehouseName: string;
  orderDate: string;
  transportMethod: string;
  settlementCycle: string;
  freight: number;
  totalAmount: number;
  receivedAmount: number;
  receivableAmount: number;
  remark: string;
  lines: SalesOrderLinePayload[];
}

export interface ConfirmedSalesOrder {
  orderNo: string;
  status: 'confirmed';
}

export interface SavedSalesOrder {
  id?: number;
  orderNo: string;
  status: 'draft';
}

export interface SalesOrderService {
  saveDraft(payload: ConfirmSalesOrderPayload): Promise<SavedSalesOrder>;
  confirmOrder(payload: ConfirmSalesOrderPayload): Promise<ConfirmedSalesOrder>;
  listOrders?(query: SalesOrderListQuery): Promise<PageResult<SalesOrderListItem>>;
  getOrder?(id: number): Promise<SalesOrderDetail>;
  deleteOrder?(id: number): Promise<void>;
  voidOrder?(id: number, reason: string): Promise<SalesOrderDetail>;
}

export interface SalesOrderListQuery {
  page: number;
  size: number;
  keyword?: string;
  status?: SalesOrderStatus;
}

export type SalesOrderStatus = 'draft' | 'confirmed' | 'void';

export interface SalesOrderListItem {
  id: number;
  orderNo: string;
  orderDate: string;
  customerName: string;
  warehouseName: string;
  totalAmount: number;
  receivedAmount: number;
  status: SalesOrderStatus;
}

export interface SalesOrderItemDetail {
  id: number | null;
  skuId: number;
  quantity: number;
  defaultUnitPrice: number;
  unitPrice: number;
  discountRate: number;
  amount: number;
  standardCostSnapshot: number | null;
  itemNoSnapshot: string | null;
  productNameSnapshot: string;
  skuCodeSnapshot: string;
  skuNameSnapshot: string | null;
  specificationSnapshot: string | null;
  packagingSnapshot: string | null;
  cartonQuantitySnapshot: number | null;
  barcodeSnapshot: string | null;
  salesUnitSnapshot: string;
}

export interface SalesOrderDetail extends SalesOrderListItem {
  customerId: number;
  warehouseId: number;
  salespersonMobile: string;
  transportMethod: string;
  settlementCycle: string;
  paymentMethod: string | null;
  deliveryAddress: string | null;
  logisticsCompany: string | null;
  trackingNo: string | null;
  packageNote: string | null;
  invoiceRequired: boolean;
  invoiceStatus: string;
  goodsAmount: number;
  discountAmount: number;
  freight: number;
  outstandingAmount: number;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
  items: SalesOrderItemDetail[];
}
