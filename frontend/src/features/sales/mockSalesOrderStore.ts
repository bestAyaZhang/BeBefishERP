import type { ConfirmSalesOrderPayload, SalesCatalog, SalesOrderDetail, SalesOrderListItem, SalesOrderStatus } from './types';

const details = new Map<number, Partial<SalesOrderDetail>>();

let orders: SalesOrderListItem[] = [
  { id: 1, orderNo: 'SO-20260713-001', orderDate: '2026-07-13', customerName: '杭州酒店用品店', warehouseName: '杭州主仓', totalAmount: 2850, receivedAmount: 2850, status: 'confirmed' },
  { id: 2, orderNo: 'SO-20260713-002', orderDate: '2026-07-13', customerName: '自动编号客户', warehouseName: '杭州主仓', totalAmount: 1280, receivedAmount: 500, status: 'draft' }
];

export function listMockSalesOrders() {
  return orders.map((order) => ({ ...order }));
}

export function addMockSalesOrder(id: number, orderNo: string, payload: ConfirmSalesOrderPayload, status: SalesOrderStatus = 'confirmed', catalog?: SalesCatalog) {
  const customer = catalog?.customers.find(c => c.id === payload.customerId);
  details.set(id, {
    customerId: payload.customerId, warehouseId: payload.warehouseId,
    transportMethod: payload.transportMethod, settlementCycle: payload.settlementCycle,
    deliveryAddress: customer ? [customer.province, customer.city, customer.district, customer.detailAddress].filter(Boolean).join('') : '',
    freight: payload.freight, goodsAmount: payload.totalAmount - payload.freight,
    outstandingAmount: payload.receivableAmount, remark: payload.remark,
    items: payload.lines.map(line => {
      const p = catalog?.products.find(p => p.skuId === line.skuId);
      return { id: null, skuId: line.skuId, quantity: line.quantity, unitPrice: line.unitPrice,
        defaultUnitPrice: p?.defaultSalePrice ?? line.unitPrice, amount: line.amount, discountRate: 0,
        standardCostSnapshot: null, itemNoSnapshot: p?.itemNo ?? '', productNameSnapshot: p?.productName ?? '',
        skuCodeSnapshot: p?.skuCode ?? '', skuNameSnapshot: p?.skuName ?? '', specificationSnapshot: p?.specification ?? '',
        packagingSnapshot: p?.packagingMethod ?? '', cartonQuantitySnapshot: p?.cartonQuantity ?? null,
        barcodeSnapshot: p?.barcode ?? '', salesUnitSnapshot: p?.salesUnit ?? '件' };
    })
  });
  orders = [{
    id,
    orderNo,
    orderDate: payload.orderDate,
    customerName: payload.customerName,
    warehouseName: payload.warehouseName,
    totalAmount: payload.totalAmount,
    receivedAmount: payload.receivedAmount,
    status
  }, ...orders];
}

export function getMockSalesOrder(id: number): SalesOrderDetail | undefined {
  const order = orders.find((item) => item.id === id);
  if (!order) return undefined;
  return {
    ...order,
    customerId: 5,
    warehouseId: 8,
    salespersonMobile: '13800138000',
    transportMethod: 'delivery',
    settlementCycle: 'monthly',
    paymentMethod: null,
    deliveryAddress: '浙江省杭州市西湖区文三路88号',
    logisticsCompany: null,
    trackingNo: null,
    packageNote: null,
    invoiceRequired: false,
    invoiceStatus: 'not_required',
    goodsAmount: order.totalAmount,
    discountAmount: 0,
    freight: 0,
    outstandingAmount: Math.max(order.totalAmount - order.receivedAmount, 0),
    remark: null,
    createdAt: `${order.orderDate}T09:00:00`,
    updatedAt: `${order.orderDate}T09:00:00`,
    items: [],
    ...details.get(id)
  };
}

export function deleteMockSalesOrder(id: number) {
  details.delete(id);
  orders = orders.filter((order) => order.id !== id);
}

export function voidMockSalesOrder(id: number) {
  orders = orders.map((order) => order.id === id ? { ...order, status: 'void' as const } : order);
}

export function confirmMockSalesOrder(id: number) {
  const order = orders.find(order => order.id === id);
  if (!order) throw new Error('销售单不存在');
  if (order.status !== 'draft') throw new Error('销售单已确认，请勿重复操作');
  orders = orders.map(order => order.id === id ? { ...order, status: 'confirmed' as const } : order);
  return getMockSalesOrder(id)!;
}
