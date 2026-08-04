import { request } from '../../services/http';
import type {
  ConfirmSalesOrderPayload,
  ConfirmedSalesOrder,
  SavedSalesOrder,
  SalesOrderDetail,
  SalesOrderListItem,
  SalesOrderListQuery,
  SalesOrderService
} from './types';

function queryString(query: SalesOrderListQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  return params.toString();
}

export const httpSalesOrderService: SalesOrderService = {
  saveDraft: (payload: ConfirmSalesOrderPayload) => request<SavedSalesOrder>('/api/sales-orders', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  confirmOrder: async (payload: ConfirmSalesOrderPayload) => {
    const draft = await request<SavedSalesOrder>('/api/sales-orders', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (!draft.id) throw new Error('销售草稿已保存，但缺少单据编号，无法确认');
    const confirmed = await request<ConfirmedSalesOrder>(`/api/sales-orders/${draft.id}/confirm`, {
      method: 'POST'
    });
    return { orderNo: confirmed.orderNo, status: 'confirmed' as const };
  },
  listOrders: (query) => request<import('../masterdata/types').PageResult<SalesOrderListItem>>(`/api/sales-orders?${queryString(query)}`),
  getOrder: (id) => request<SalesOrderDetail>(`/api/sales-orders/${id}`),
  deleteOrder: (id) => request<void>(`/api/sales-orders/${id}`, { method: 'DELETE' }),
  voidOrder: (id, reason) => request<SalesOrderDetail>(`/api/sales-orders/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
};
