import { request } from '../../../services/http';
import type { StocktakeService, StocktakeTaskQuery } from './types';

function queryString(query: StocktakeTaskQuery) {
  const params = new URLSearchParams();
  if (query.warehouseId !== undefined) params.set('warehouseId', String(query.warehouseId));
  if (query.status) params.set('status', query.status);
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  const value = params.toString();
  return value ? `?${value}` : '';
}

export const httpStocktakeService: StocktakeService = {
  listTasks: (query) => request(`/api/inventory/stocktakes${queryString(query)}`),
  getTask: (id) => request(`/api/inventory/stocktakes/${id}`),
  createTask: (input) => request('/api/inventory/stocktakes', { method: 'POST', body: JSON.stringify(input) }),
  saveDraft: (id, counts) => request(`/api/inventory/stocktakes/${id}/draft`, {
    method: 'PUT', body: JSON.stringify({ counts })
  }),
  submitInitial: (id, counts) => request(`/api/inventory/stocktakes/${id}/submit-initial`, {
    method: 'POST', body: JSON.stringify({ counts })
  }),
  submitRecount: (id, counts) => request(`/api/inventory/stocktakes/${id}/submit-recount`, {
    method: 'POST', body: JSON.stringify({ counts })
  }),
  approve: (id) => request(`/api/inventory/stocktakes/${id}/approve`, { method: 'POST' })
};
