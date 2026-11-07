import { request } from '../../../services/http';
import type { ReplenishmentConfig, ReplenishmentPage, ReplenishmentQuery } from './replenishmentModel';

const replenishmentPath = '/api/inventory/replenishment';

export const replenishmentService = {
  list(query: ReplenishmentQuery) {
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
    if (query.warehouseId !== undefined) params.set('warehouseId', String(query.warehouseId));
    if (query.status) params.set('status', query.status);
    return request<ReplenishmentPage>(`${replenishmentPath}?${params}`);
  },

  save(skuId: number, warehouseId: number | null, config: ReplenishmentConfig) {
    const scope = warehouseId === null ? '' : `?warehouseId=${warehouseId}`;
    return request<void>(`${replenishmentPath}/${skuId}${scope}`, { method: 'PUT', body: JSON.stringify(config) });
  },

  saveBatch(targets: { skuId: number; warehouseId: number | null }[], config: ReplenishmentConfig) {
    return request<void>(`${replenishmentPath}/batch`, { method: 'PUT', body: JSON.stringify({ targets, config }) });
  }
};
