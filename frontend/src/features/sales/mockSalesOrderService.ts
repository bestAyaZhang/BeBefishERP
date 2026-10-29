import type { ConfirmSalesOrderPayload, SalesOrderListQuery, SalesOrderService } from './types';
import { addMockSalesOrder, confirmMockSalesOrder, deleteMockSalesOrder, getMockSalesOrder, listMockSalesOrders, voidMockSalesOrder } from './mockSalesOrderStore';
import { mockMasterdataService } from '../masterdata/mockMasterdataService';
import { mockProductService } from '../product/mockProductService';
import { toProductOptions } from './salesCatalogService';

async function mockCatalog() {
  const [customers, warehouses, products] = await Promise.all([
    mockMasterdataService.listActiveCustomers(), mockMasterdataService.listActiveWarehouses(),
    mockProductService.listProducts({ page: 1, size: 100, status: 'enabled' })
  ]);
  return { customers, warehouses, products: toProductOptions(products.records) };
}

let sequence = 3;

export const mockSalesOrderService: SalesOrderService = {
  async listOrders(query: SalesOrderListQuery) {
    const records = listMockSalesOrders().filter((order) => {
      const keyword = query.keyword?.trim().toLowerCase();
      const matchesKeyword = !keyword || [order.orderNo, order.customerName, order.warehouseName]
        .some((value) => value.toLowerCase().includes(keyword));
      return !query.status || order.status === query.status ? matchesKeyword : false;
    });
    return { records, page: 1, pageSize: records.length || query.size, total: records.length };
  },
  async saveDraft(payload: ConfirmSalesOrderPayload) {
    const currentSequence = sequence++;
    const orderNo = `SO-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${String(currentSequence).padStart(3, '0')}`;
    addMockSalesOrder(currentSequence, orderNo, payload, 'draft', await mockCatalog());
    return { orderNo, status: 'draft' };
  },
  async confirmOrder(payload: ConfirmSalesOrderPayload) {
    const currentSequence = sequence++;
    const orderNo = `SO-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${String(currentSequence).padStart(3, '0')}`;
    addMockSalesOrder(currentSequence, orderNo, payload, 'confirmed', await mockCatalog());
    return { orderNo, status: 'confirmed' };
  },
  async getOrder(id) {
    const order = getMockSalesOrder(id);
    if (!order) throw new Error('销售单不存在');
    return order;
  },
  async confirmSavedOrder(id) { return confirmMockSalesOrder(id); },
  async deleteOrder(id) {
    const order = getMockSalesOrder(id);
    if (!order) throw new Error('销售单不存在');
    if (order.status !== 'draft') throw new Error('已确认销售单不能删除');
    deleteMockSalesOrder(id);
  },
  async voidOrder(id, reason) {
    const order = getMockSalesOrder(id);
    if (!order) throw new Error('销售单不存在');
    if (order.status !== 'confirmed') throw new Error('只有已确认销售单才能作废');
    voidMockSalesOrder(id);
    return { ...order, status: 'void', remark: reason };
  }
};
