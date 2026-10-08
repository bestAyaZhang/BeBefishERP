import { request } from '../../services/http';
import type { PageResult } from '../masterdata/types';
import type { Shipment, ShippingService, LogisticsAvailability, LogisticsOrder, ShipmentSummary, ShippingFormOptions } from './types';

export const httpShippingService: ShippingService = {
  list(query) {
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    for (const key of ['keyword', 'status', 'dateFrom', 'dateTo', 'platform'] as const) {
      if (query[key]?.trim()) params.set(key, query[key]!.trim());
    }
    if (query.incompleteOnly) params.set('incompleteOnly', 'true');
    if (query.platformId != null) params.set('platformId', String(query.platformId));
    if (query.shopId != null) params.set('shopId', String(query.shopId));
    if (query.unlinkedOnly) params.set('unlinkedOnly', 'true');
    return request<PageResult<Shipment>>(`/api/shipments?${params}`);
  },
  summary: date => request<ShipmentSummary>(`/api/shipments/summary?date=${encodeURIComponent(date)}`),
  formOptions: () => request<ShippingFormOptions>('/api/shipments/form-options'),
  getFilterOptions: () => request('/api/shipments/filter-options'),
  get: id => request<Shipment>(`/api/shipments/${id}`),
  create: ({ preparerEmployeeIds, ...form }) => request<Shipment>('/api/shipments', { method: 'POST', body: JSON.stringify({ form, preparerEmployeeIds }) }),
  update: (id, { preparerEmployeeIds, ...form }, status, version) => request<Shipment>(`/api/shipments/${id}`, { method: 'PUT', body: JSON.stringify({ form, preparerEmployeeIds, status, version }) }),
  updatePreparation: (id, payload) => request<Shipment>(`/api/shipments/${id}/preparation`, { method: 'PATCH', body: JSON.stringify(payload) }),
  logisticsAvailability: () => request<LogisticsAvailability>('/api/shipments/logistics/availability'),
  getLogisticsOrder: id => request<LogisticsOrder | null>(`/api/shipments/${id}/logistics-order`),
  placeLogisticsOrder: (id, payload) => request<LogisticsOrder>(`/api/shipments/${id}/logistics-order`, { method: 'POST', body: JSON.stringify(payload) }),
  cancelLogisticsOrder: (id, payload) => request<LogisticsOrder>(`/api/shipments/${id}/logistics-order/cancel`, { method: 'POST', body: JSON.stringify(payload) })
};
