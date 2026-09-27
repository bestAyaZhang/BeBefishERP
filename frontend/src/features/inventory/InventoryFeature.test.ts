import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, routeLocationKey } from 'vue-router';
import InventoryBalanceView from './views/InventoryBalanceView.vue';
import InventoryLedgerView from './views/InventoryLedgerView.vue';
import StockAdjustmentView from './views/StockAdjustmentView.vue';
import { productFixture } from '../product/productTestFixtures';
import type { ProductService } from '../product/types';
import type {
  InventoryAdjustment,
  InventoryBalance,
  InventoryLedger,
  InventoryService,
  PageResult,
  SaveInventoryAdjustmentPayload
} from './types';
import type { Warehouse } from '../masterdata/types';
import { createWarehouseStructure } from './warehouseCanvas/warehouseStructure';
import { warehousePlannerScene } from './warehouseCanvas/warehousePlannerScene';

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
  it('shows inventory balances as the default active warehouse layout and switches active warehouses', async () => {
    const structure = createWarehouseStructure();
    const pallet = warehousePlannerScene.palletGroups[0];
    const disabledWarehouse: Warehouse = {
      id: 77, warehouseNo: 'WH-077', warehouseName: '停用仓库',
      address: '宁波', defaultWarehouse: false, status: 'disabled', remark: ''
    };
    const layoutLoad = vi.fn().mockResolvedValue({
      revision: 2,
      document: { schemaVersion: 1, structure, palletGroups: [pallet], completed: true }
    });
    const inventoryLoad = vi.fn((warehouseId: number) => Promise.resolve({
      warehouseId, totalUnits: 12, skuCount: 1, placedUnits: 12, unallocatedUnits: 0, updatedAt: null,
      allocations: [{ zoneId: null, palletId: pallet.id, skuId: 10, skuCode: 'PRD-001-001', productName: '玻璃杯', skuName: '透明款', specification: '透明 / 竖纹', unitsPerCase: 6, units: 12 }]
    }));
    const wrapper = mount(InventoryBalanceView, {
      global: { provide: {
        [routeLocationKey as symbol]: { query: {} },
        masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue([...activeWarehouses, disabledWarehouse]) },
        warehouseLayoutService: { load: layoutLoad, save: vi.fn() },
        warehouseInventoryService: { load: inventoryLoad, allocateToPile: vi.fn() }
      } }
    });

    await flushPromises();
    expect(wrapper.get('[data-testid="planner-title"]').text()).toBe('杭州主仓 · 库存查看');
    const warehouseSelect = wrapper.get('[data-testid="warehouse-layout-selector"]');
    expect(warehouseSelect.element.closest('.planner-header')).not.toBeNull();
    expect(warehouseSelect.attributes('role')).toBe('combobox');
    expect(warehouseSelect.attributes('aria-expanded')).toBe('false');
    await warehouseSelect.trigger('click');
    expect(wrapper.get('[data-testid="warehouse-layout-menu"]').text()).toContain('义乌备货仓');
    expect(wrapper.get('[data-testid="warehouse-layout-menu"]').text()).not.toContain('停用仓库');
    expect(wrapper.find('[data-testid="planner-complete"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="planner-tool-structure"]').exists()).toBe(false);
    expect(wrapper.find('.inventory-sidebar').exists()).toBe(true);
    expect(wrapper.find('[aria-label="货物堆库存概览"]').exists()).toBe(false);
    expect(inventoryLoad).toHaveBeenCalledWith(88);

    await wrapper.get('[data-testid="warehouse-layout-option-99"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="planner-title"]').text()).toBe('义乌备货仓 · 库存查看');
    expect(layoutLoad).toHaveBeenLastCalledWith(99);
    expect(inventoryLoad).toHaveBeenLastCalledWith(99);
  });

  it('blocks inventory layout viewing until the warehouse has been planned', async () => {
    const inventoryLoad = vi.fn();
    const wrapper = mount(InventoryBalanceView, {
      global: { provide: {
        [routeLocationKey as symbol]: { query: {} },
        masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) },
        warehouseLayoutService: { load: vi.fn().mockResolvedValue({ revision: 0, document: null }), save: vi.fn() },
        warehouseInventoryService: { load: inventoryLoad, allocateToPile: vi.fn() }
      } }
    });

    await flushPromises();
    expect(wrapper.get('[data-testid="warehouse-inventory-unplanned"]').text()).toContain('该仓库尚未规划');
    expect(wrapper.get('[data-testid="inventory-plan-warehouse"]').attributes('href')).toBe('/inventory/warehouse-canvas?warehouseId=88');
    expect(wrapper.find('[data-testid="warehouse-blueprint-scene"]').exists()).toBe(false);
    expect(inventoryLoad).not.toHaveBeenCalled();
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
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/inventory/adjustments', name: 'inventory-adjustments', component: StockAdjustmentView }]
    });
    await router.push({ name: 'inventory-adjustments' });
    await router.isReady();
    const wrapper = mount(
      { template: '<router-view />' },
      { global: { plugins: [router], provide: { inventoryService: service, masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) } } } }
    );
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

  it('prefills a new product opening-stock adjustment and submits only positive SKU quantities', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/inventory/adjustments', name: 'inventory-adjustments', component: StockAdjustmentView }]
    });
    await router.push({ name: 'inventory-adjustments', query: { mode: 'opening', productId: '77' } });
    await router.isReady();
    const service = fakeService();
    const product = productFixture({
      id: 77,
      productCode: 'PRD-000077',
      itemNo: 'BBF-077',
      productName: '新建玻璃杯',
      categoryId: 8,
      brand: 'BeBefish',
      productType: 'variant',
      mainImageFileId: null,
      remark: '',
      specifications: [{ name: '颜色', values: ['白色', '蓝色'] }],
      skus: [
        {
          id: 771, skuCode: 'BBF-077-WH', barcode: '6970000000771', skuName: '白色款', specificationValues: ['白色'],
          salesUnit: '只', defaultSalePrice: 19.9, standardCost: 8.6, packageLengthCm: null, packageWidthCm: null,
          packageHeightCm: null, packageVolumeCm3: null, netWeightKg: null, grossWeightKg: null, gramWeightG: null,
          packagingMethod: null, cartonQuantity: null, skuImageFileId: null, packageImageFileId: null, cartonImageFileId: null,
          defaultSku: true, status: 'enabled'
        },
        {
          id: 772, skuCode: 'BBF-077-BL', barcode: '6970000000772', skuName: '蓝色款', specificationValues: ['蓝色'],
          salesUnit: '只', defaultSalePrice: 21.9, standardCost: 9.2, packageLengthCm: null, packageWidthCm: null,
          packageHeightCm: null, packageVolumeCm3: null, netWeightKg: null, grossWeightKg: null, gramWeightG: null,
          packagingMethod: null, cartonQuantity: null, skuImageFileId: null, packageImageFileId: null, cartonImageFileId: null,
          defaultSku: false, status: 'enabled'
        }
      ],
      status: 'enabled'
    });
    const productLookup = { getProduct: vi.fn().mockResolvedValue(product) } satisfies Pick<ProductService, 'getProduct'>;
    const wrapper = mount(
      { template: '<router-view />' },
      {
        global: {
          plugins: [router],
          provide: {
            inventoryService: service,
            masterdataService: { listActiveWarehouses: vi.fn().mockResolvedValue(activeWarehouses) },
            productService: productLookup
          }
        }
      }
    );
    await flushPromises();

    expect(productLookup.getProduct).toHaveBeenCalledWith(77);
    expect(wrapper.get('[data-testid="opening-stock-context"]').text()).toContain('新建玻璃杯');
    expect(wrapper.get('[data-testid="opening-stock-context"]').text()).toContain('BBF-077');
    expect(wrapper.get<HTMLInputElement>('[data-testid="adjustment-reason"]').element.value).toBe('期初库存');
    expect(wrapper.get('[data-testid="opening-stock-sku-771"]').text()).toContain('BBF-077-WH');
    expect(wrapper.get('[data-testid="opening-stock-sku-772"]').text()).toContain('蓝色款');

    await wrapper.get('[data-testid="adjustment-warehouse"]').setValue('88');
    await wrapper.get('[data-testid="quantity-delta-0"]').setValue('12');
    await wrapper.get('[data-testid="save-adjustment"]').trigger('click');

    expect(service.createAdjustment).toHaveBeenCalledWith({
      warehouseId: 88,
      reason: '期初库存',
      remark: '',
      items: [{ skuId: 771, quantityDelta: 12 }]
    });
  });
});
