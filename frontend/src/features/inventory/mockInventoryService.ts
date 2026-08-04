import type { InventoryAdjustment, InventoryBalance, InventoryLedger, InventoryService, SaveInventoryAdjustmentPayload } from './types';
import type { PageResult } from '../masterdata/types';

let adjustmentSequence = 1;
const adjustments: InventoryAdjustment[] = [];
const balances: InventoryBalance[] = [
  {
    warehouseId: 1, skuId: 10, skuCode: 'PRD-000001-001', barcode: '690001', itemNo: 'EW43245',
    productName: '高脚玻璃杯', skuName: '透明款', specification: '透明 / 竖纹', quantity: 1250
  },
  {
    warehouseId: 1, skuId: 11, skuCode: 'PRD-000001-002', barcode: '690002', itemNo: 'EW43246',
    productName: '高脚玻璃杯', skuName: '烟灰款', specification: '烟灰 / 竖纹', quantity: 620
  }
];
const ledger: InventoryLedger[] = [
  {
    id: 1, warehouseId: 1, skuId: 10, direction: 'increase', quantity: 1250, beforeQuantity: 0,
    afterQuantity: 1250, sourceType: 'purchase', sourceId: 1, sourceNo: 'PI-20260719-001',
    occurredAt: '2026-07-19T10:00:00', operatorMobile: '13800138000'
  }
];

function page<T>(records: T[], pageNumber = 1, pageSize = 20): PageResult<T> {
  return { records, page: pageNumber, pageSize, total: records.length };
}

export const mockInventoryService: InventoryService = {
  async listBalances(query) {
    const keyword = query.keyword?.trim().toLowerCase();
    const records = balances.filter((record) => {
      const warehouseMatch = query.warehouseId === undefined || record.warehouseId === query.warehouseId;
      const keywordMatch = !keyword || [record.skuCode, record.itemNo, record.productName, record.skuName]
        .some((value) => value?.toLowerCase().includes(keyword));
      return warehouseMatch && keywordMatch;
    });
    return page(records, query.page, query.size);
  },
  async listLedger(query) {
    const records = ledger.filter((record) => {
      if (query.warehouseId !== undefined && record.warehouseId !== query.warehouseId) return false;
      if (query.skuId !== undefined && record.skuId !== query.skuId) return false;
      if (query.direction && record.direction !== query.direction) return false;
      return !query.sourceType || record.sourceType === query.sourceType;
    });
    return page(records, query.page, query.size);
  },
  async createAdjustment(payload) {
    const adjustment: InventoryAdjustment = {
      ...payload,
      id: adjustmentSequence,
      adjustmentNo: `ADJ-20260719-${String(adjustmentSequence).padStart(3, '0')}`,
      status: 'draft'
    };
    adjustmentSequence += 1;
    adjustments.push(adjustment);
    return adjustment;
  },
  async updateAdjustment(id, payload) {
    const current = adjustments.find((item) => item.id === id);
    if (!current) throw new Error('库存调整单不存在');
    if (current.status !== 'draft') throw new Error('已确认的库存调整单不可修改');
    Object.assign(current, payload);
    return current;
  },
  async confirmAdjustment(id) {
    const current = adjustments.find((item) => item.id === id);
    if (!current) throw new Error('库存调整单不存在');
    if (current.status !== 'draft') throw new Error('库存调整单已确认，请勿重复操作');
    current.status = 'confirmed';
    return current;
  },
  async voidAdjustment(id, reason) {
    const current = adjustments.find((item) => item.id === id);
    if (!current) throw new Error('库存调整单不存在');
    current.status = 'voided';
    current.remark = reason;
    return current;
  }
};
