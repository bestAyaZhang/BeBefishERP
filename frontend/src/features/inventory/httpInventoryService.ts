import { request } from '../../services/http';
import type { PageResult } from '../masterdata/types';
import type {
  InventoryAdjustment,
  InventoryBalance,
  InventoryBalanceQuery,
  InventoryLedger,
  InventoryLedgerQuery,
  InventoryService,
  SaveInventoryAdjustmentPayload
} from './types';

function params(input: object) {
  const result = new URLSearchParams();
  Object.entries(input as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== '') result.set(key, String(value));
  });
  return result.toString();
}

export const httpInventoryService: InventoryService = {
  listBalances: (query: InventoryBalanceQuery) => request<PageResult<InventoryBalance>>(`/api/inventory/balances?${params(query)}`),
  listLedger: (query: InventoryLedgerQuery) => request<PageResult<InventoryLedger>>(`/api/inventory/ledger?${params(query)}`),
  createAdjustment: (payload: SaveInventoryAdjustmentPayload) => request<InventoryAdjustment>('/api/inventory/adjustments', { method: 'POST', body: JSON.stringify(payload) }),
  updateAdjustment: (id, payload) => request<InventoryAdjustment>(`/api/inventory/adjustments/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  confirmAdjustment: (id) => request<InventoryAdjustment>(`/api/inventory/adjustments/${id}/confirm`, { method: 'POST' }),
  voidAdjustment: (id, reason) => request<InventoryAdjustment>(`/api/inventory/adjustments/${id}/void`, { method: 'POST', body: JSON.stringify({ reason }) })
};
