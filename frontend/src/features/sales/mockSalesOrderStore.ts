import type { ConfirmSalesOrderPayload, SalesOrderDetail, SalesOrderListItem, SalesOrderStatus } from './types';

let orders: SalesOrderListItem[] = [
  { id: 1, orderNo: 'SO-20260713-001', orderDate: '2026-07-13', customerName: '杭州酒店用品店', warehouseName: '杭州主仓', totalAmount: 2850, receivedAmount: 2850, status: 'confirmed' },
  { id: 2, orderNo: 'SO-20260713-002', orderDate: '2026-07-13', customerName: '自动编号客户', warehouseName: '杭州主仓', totalAmount: 1280, receivedAmount: 500, status: 'draft' }
];

export function listMockSalesOrders() {
  return orders.map((order) => ({ ...order }));
}

export function addMockSalesOrder(id: number, orderNo: string, payload: ConfirmSalesOrderPayload, status: SalesOrderStatus = 'confirmed') {
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
    customerId: 1,
    warehouseId: 1,
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
    items: []
  };
}

export function deleteMockSalesOrder(id: number) {
  orders = orders.filter((order) => order.id !== id);
}

export function voidMockSalesOrder(id: number) {
  orders = orders.map((order) => order.id === id ? { ...order, status: 'void' as const } : order);
}
