import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import InventoryBalanceView from './views/InventoryBalanceView.vue';
import InventoryLedgerView from './views/InventoryLedgerView.vue';
import StockAdjustmentView from './views/StockAdjustmentView.vue';
import type {
  InventoryAdjustment,
  InventoryBalance,
  InventoryLedger,
  InventoryService,
  PageResult,
  SaveInventoryAdjustmentPayload
} from './types';
import type { Warehouse } from '../masterdata/types';

const page = <T,>(records: T[], total = records.length): PageResult<T> => ({ records, page: 1, pageSize: 20, total });

const activeWarehouses: Warehouse[] = [
  { id: 88, warehouseNo: 'WH-088', warehouseName: '杭州主仓', address: '杭州', defaultWarehouse: true, status: 'enabled', remark: '' },
  { id: 99, warehouseNo: 'WH-099', warehouseName: '义乌备货仓', address: '义乌', defaultWarehouse: false, status: 'enabled', remark: '' }
];

function fakeService(overrides: Partial<InventoryService> = {}): InventoryService {
  return {
    listBalances: vi.fn().mockResolvedValue(page<InventoryBalance>([])),
    listLedger: vi.fn().mockResolvedValue(page<InventoryLedger>([])),
    createAdjustment: vi.fn().mockResolvedValue({
      id: 1, adjustmentNo: 'ADJ-001', warehouseId: 1, reason: '期初盘点', status: 'draft', remark: '', items: []
    } as InventoryAdjustment),
    updateAdjustment: vi.fn(),
    confirmAdjustment: vi.fn().mockResolvedValue({ status: 'confirmed' } as InventoryAdjustment),
    voidAdjustment: vi.fn(),
    ...overrides
  };
}

describe('inventory pages', () => {
  it('shows balance fields and loads by filter', async () => {
    const service = fakeService({
      listBalances: vi.fn().mockResolvedValue(page([
        {
          warehouseId: 1, skuId: 10, skuCode: 'PRD-001-001', barcode: '690001', itemNo: 'EW43245',
          productName: '玻璃杯', skuName: '透明款', specification: '透明 / 竖纹', quantity: 12
        }
      ]))
    });
    const wrapper = mount(InventoryBalanceView, { global: { provide: { inventoryService: service, masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) } } } });
    await vi.waitFor(() => expect(wrapper.text()).toContain('EW43245'));
    expect(wrapper.get('[data-testid="inventory-balance-warehouse"]').text()).toContain('杭州主仓');
    expect(wrapper.text()).toContain('12');
    await wrapper.get('[data-testid="inventory-balance-search"]').trigger('click');
    expect(service.listBalances).toHaveBeenCalled();
  });

  it('shows ledger before and after quantities', async () => {
    const service = fakeService({
      listLedger: vi.fn().mockResolvedValue(page([
        {
          id: 1, warehouseId: 1, skuId: 10, direction: 'increase', quantity: 12,
          beforeQuantity: 0, afterQuantity: 12, sourceType: 'purchase', sourceId: 2,
          sourceNo: 'PI-001', occurredAt: '2026-07-19T10:00:00', operatorMobile: '13800138000'
        }
      ]))
    });
    const wrapper = mount(InventoryLedgerView, { global: { provide: { inventoryService: service, masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) } } } });
    await vi.waitFor(() => expect(wrapper.text()).toContain('PI-001'));
    expect(wrapper.get('[data-testid="inventory-ledger-warehouse"]').text()).toContain('义乌备货仓');
    expect(wrapper.text()).toContain('0');
    expect(wrapper.text()).toContain('12');
  });

  it('saves a draft and confirms an inventory adjustment', async () => {
    const payload: SaveInventoryAdjustmentPayload = {
      warehouseId: 88, reason: '期初盘点', remark: '', items: [{ skuId: 10, quantityDelta: 12 }]
    };
    const service = fakeService({
      createAdjustment: vi.fn().mockResolvedValue({ id: 7, adjustmentNo: 'ADJ-007', ...payload, status: 'draft' } as InventoryAdjustment)
    });
    const wrapper = mount(StockAdjustmentView, { global: { provide: { inventoryService: service, masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) } } } });
    await vi.waitFor(() => expect(wrapper.get('[data-testid="adjustment-warehouse"]').text()).toContain('杭州主仓'));
    await wrapper.get('[data-testid="adjustment-warehouse"]').setValue('88');
    await wrapper.get('[data-testid="adjustment-reason"]').setValue('期初盘点');
    await wrapper.get('[data-testid="add-adjustment-item"]').trigger('click');
    await wrapper.get('[data-testid="adjustment-sku-0"]').setValue('10');
    await wrapper.get('[data-testid="quantity-delta-0"]').setValue('12');
    await wrapper.get('[data-testid="save-adjustment"]').trigger('click');
    expect(service.createAdjustment).toHaveBeenCalledWith(payload);
    await wrapper.get('[data-testid="confirm-adjustment"]').trigger('click');
    expect(service.confirmAdjustment).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('已确认');
  });
});
