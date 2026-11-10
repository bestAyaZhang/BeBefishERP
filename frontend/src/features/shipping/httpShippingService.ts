import { request } from '../../services/http';
import type { PageResult } from '../masterdata/types';
import type { Shipment, ShipmentFilters, ShippingService, LogisticsAvailability, LogisticsOrder, ShipmentSummary, ShippingFormOptions } from './types';

function shipmentFilterParams(filters: ShipmentFilters) {
  const params = new URLSearchParams();
  for (const key of ['keyword', 'status', 'dateFrom', 'dateTo', 'platform', 'logisticsCompany'] as const) {
    const value = filters[key]?.trim();
    if (value) params.set(key, value);
  }
  if (filters.incompleteOnly) params.set('incompleteOnly', 'true');
  if (filters.platformId != null) params.set('platformId', String(filters.platformId));
  if (filters.shopId != null) params.set('shopId', String(filters.shopId));
  if (filters.unlinkedOnly) params.set('unlinkedOnly', 'true');
  return params;
}

export const httpShippingService: ShippingService = {
  list(query) {
    const params = shipmentFilterParams(query);
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return request<PageResult<Shipment>>(`/api/shipments?${params}`);
  },
  listForExport(filters) {
    const params = shipmentFilterParams(filters).toString();
    return request<Shipment[]>(params ? `/api/shipments/export?${params}` : '/api/shipments/export');
  },
  summary: date => request<ShipmentSummary>(`/api/shipments/summary?date=${encodeURIComponent(date)}`),
  formOptions: () => request<ShippingFormOptions>('/api/shipments/form-options'),
  getFilterOptions: () => request('/api/shipments/filter-options'),
  get: id => request<Shipment>(`/api/shipments/${id}`),
  create: ({ preparerEmployeeIds, logisticsChoice, ...form }) => request<Shipment>('/api/shipments', { method: 'POST', body: JSON.stringify({ form, preparerEmployeeIds, ...(logisticsChoice === undefined ? {} : { logisticsChoice }) }) }),
  update: (id, { preparerEmployeeIds, logisticsChoice, ...form }, status, version) => request<Shipment>(`/api/shipments/${id}`, { method: 'PUT', body: JSON.stringify({ form, preparerEmployeeIds, status, version, ...(logisticsChoice === undefined ? {} : { logisticsChoice }) }) }),
  updatePreparation: (id, payload) => request<Shipment>(`/api/shipments/${id}/preparation`, { method: 'PATCH', body: JSON.stringify(payload) }),
  updatePreparationContent: (id, payload) => request<Shipment>(`/api/shipments/${id}/preparation-content`, { method: 'PATCH', body: JSON.stringify(payload) }),
  confirmCreator: (id, payload) => request<Shipment>(`/api/shipments/${id}/creator`, { method: 'PATCH', body: JSON.stringify(payload) }),
  logisticsAvailability: companyId => request<LogisticsAvailability>(`/api/shipments/logistics/availability${companyId==null?'':`?companyId=${companyId}`}`),
  getLogisticsAccountBinding: id => request(`/api/shipments/${id}/logistics-account`),
  getLogisticsTrace: id => request(`/api/shipments/${id}/logistics-trace`),
  getLogisticsOrder: id => request<LogisticsOrder | null>(`/api/shipments/${id}/logistics-order`),
  placeLogisticsOrder: (id, payload) => request<LogisticsOrder>(`/api/shipments/${id}/logistics-order`, { method: 'POST', body: JSON.stringify(payload) }),
  cancelLogisticsOrder: (id, payload) => request<LogisticsOrder>(`/api/shipments/${id}/logistics-order/cancel`, { method: 'POST', body: JSON.stringify(payload) })
};
