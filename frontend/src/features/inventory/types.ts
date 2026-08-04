import type { PageResult } from '../masterdata/types';

export type { PageResult } from '../masterdata/types';

export type InventoryAdjustmentStatus = 'draft' | 'confirmed' | 'voided';

export interface InventoryBalance {
  warehouseId: number;
  skuId: number;
  skuCode: string | null;
  barcode: string | null;
  itemNo: string | null;
  productName: string | null;
  skuName: string | null;
  specification: string | null;
  quantity: number;
}

export interface InventoryLedger {
  id: number;
  warehouseId: number;
  skuId: number;
  direction: 'increase' | 'decrease';
  quantity: number;
  beforeQuantity: number;
  afterQuantity: number;
  sourceType: string;
  sourceId: number;
  sourceNo: string;
  occurredAt: string;
  operatorMobile: string;
}

export interface InventoryAdjustmentItem {
  id?: number;
  skuId: number;
  quantityDelta: number;
}

export interface SaveInventoryAdjustmentPayload {
  warehouseId: number;
  reason: string;
  remark: string;
  items: InventoryAdjustmentItem[];
}

export interface InventoryAdjustment extends SaveInventoryAdjustmentPayload {
  id: number;
  adjustmentNo: string;
  status: InventoryAdjustmentStatus;
}

export interface InventoryBalanceQuery {
  page: number;
  size: number;
  warehouseId?: number;
  keyword?: string;
}

export interface InventoryLedgerQuery {
  page: number;
  size: number;
  warehouseId?: number;
  skuId?: number;
  direction?: 'increase' | 'decrease';
  sourceType?: string;
}

export interface InventoryService {
  listBalances(query: InventoryBalanceQuery): Promise<PageResult<InventoryBalance>>;
  listLedger(query: InventoryLedgerQuery): Promise<PageResult<InventoryLedger>>;
  createAdjustment(payload: SaveInventoryAdjustmentPayload): Promise<InventoryAdjustment>;
  updateAdjustment(id: number, payload: SaveInventoryAdjustmentPayload): Promise<InventoryAdjustment>;
  confirmAdjustment(id: number): Promise<InventoryAdjustment>;
  voidAdjustment(id: number, reason: string): Promise<InventoryAdjustment>;
}
