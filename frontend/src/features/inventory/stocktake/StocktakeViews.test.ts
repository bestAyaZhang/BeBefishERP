import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentUser } from '../../../services/authSession';
import StocktakeExecutionView from '../views/StocktakeExecutionView.vue';
import StocktakeTaskListView from '../views/StocktakeTaskListView.vue';
import type { StocktakeService, StocktakeTaskDetails, StocktakeTaskListResult } from './types';

const listResult: StocktakeTaskListResult = {
  summary: {
    inProgress: 3,
    awaitingRecount: 2,
    awaitingApproval: 1,
    completedThisMonth: 12,
    discrepancyItems: 18,
    accuracyRate: 98.6
  },
  tasks: [
    {
      id: 1,
      taskNo: 'PD20260917001',
      warehouseId: 88,
      warehouseName: '杭州主仓',
      scopeLabel: '全仓 · 8区23堆',
      assigneeName: '张敏',
      countedItems: 68,
      totalItems: 96,
      differenceItems: 0,
      status: 'in_progress',
      createdAt: '2026-09-17T09:20:00'
    },
    {
      id: 2,
      taskNo: 'PD20260916003',
      warehouseId: 99,
      warehouseName: '义乌备货仓',
      scopeLabel: 'A区 · 6个货物堆',
      assigneeName: '李娜',
      countedItems: 42,
      totalItems: 42,
      differenceItems: 6,
      status: 'awaiting_recount',
      createdAt: '2026-09-16T14:35:00'
    }
  ]
};

function service(overrides: Partial<StocktakeService> = {}): StocktakeService {
  return {
    listTasks: vi.fn().mockResolvedValue(listResult),
    getTask: vi.fn(),
    createTask: vi.fn(),
    saveDraft: vi.fn(),
    submitInitial: vi.fn(),
    submitRecount: vi.fn(),
    approve: vi.fn(),
    ...overrides
  };
}

describe('stocktake views', () => {
  afterEach(() => {
    currentUser.value = null;
  });

  it('renders the Figma task summary and opens the selected stocktake', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: (route) => ({ taskId: route.params.id }) }
      ]
    });
    await router.push('/inventory/stocktakes');
    await router.isReady();
    const stocktakeService = service();
    const wrapper = mount({ template: '<router-view />' }, {
      global: { plugins: [router], provide: { stocktakeService } }
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="stocktake-task-list"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('库存盘点');
    expect(wrapper.get('[data-testid="stocktake-summary-in-progress"]').text()).toContain('3');
    expect(wrapper.get('[data-testid="stocktake-summary-recount"]').text()).toContain('2');
    expect(wrapper.get('[data-testid="stocktake-summary-approval"]').text()).toContain('1');
    expect(wrapper.text()).toContain('PD20260917001');
    expect(wrapper.text()).toContain('待复盘');

    await wrapper.get('[data-testid="open-stocktake-1"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('inventory-stocktake-execution');
    expect(router.currentRoute.value.params.id).toBe('1');
  });

  it('creates a blind-count task for an enabled warehouse', async () => {
    currentUser.value = {
      accessToken: 'token-current-user', employeeId: 9, mobile: '13800000009', displayName: '当前盘点员',
      roles: ['仓库管理员'], permissions: ['inventory:view', 'inventory:edit', 'warehouse:view'], loginMethod: 'password'
    };
    const stocktakeService = service({
      createTask: vi.fn().mockResolvedValue({ ...listResult.tasks[0], blindCount: true, items: [] })
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: (route) => ({ taskId: route.params.id }) }
      ]
    });
    await router.push('/inventory/stocktakes');
    await router.isReady();
    const wrapper = mount(StocktakeTaskListView, {
      global: {
        plugins: [router],
        provide: {
          stocktakeService,
          masterdataService: {
            listActiveWarehouses: vi.fn().mockResolvedValue([
              { id: 88, warehouseNo: 'WH-088', warehouseName: '杭州主仓', status: 'enabled' }
            ])
          }
        }
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="create-stocktake"]').trigger('click');
    expect(wrapper.find('[data-testid="stocktake-create-dialog"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stocktake-create-assignee"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="stocktake-create-owner"]').text()).toContain('当前盘点员');
    await wrapper.get('[data-testid="stocktake-create-warehouse"]').setValue('88');
    await flushPromises();
    await wrapper.get('[data-testid="stocktake-create-submit"]').trigger('click');
    await flushPromises();

    expect(stocktakeService.createTask).toHaveBeenCalledWith({ warehouseId: 88, blindCount: true });
    expect(router.currentRoute.value.name).toBe('inventory-stocktake-execution');
  });

  it('does not offer task creation when the user cannot view warehouses', async () => {
    currentUser.value = {
      accessToken: 'inventory-editor', mobile: '13800000009', roles: [],
      permissions: ['inventory:view', 'inventory:edit'], loginMethod: 'password'
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView }]
    });
    await router.push('/inventory/stocktakes');
    await router.isReady();
    const wrapper = mount(StocktakeTaskListView, {
      global: { plugins: [router], provide: { stocktakeService: service() } }
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="create-stocktake"]').exists()).toBe(false);
  });

  it('creates a stocktake for one or more selected piles', async () => {
    currentUser.value = {
      accessToken: 'token-current-user', employeeId: 9, mobile: '13800000009', displayName: '当前盘点员',
      roles: ['仓库管理员'], permissions: ['inventory:view', 'inventory:edit', 'warehouse:view'], loginMethod: 'password'
    };
    const stocktakeService = service({
      createTask: vi.fn().mockResolvedValue({ ...listResult.tasks[0], blindCount: true, items: [] })
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: true }
      ]
    });
    await router.push('/inventory/stocktakes');
    await router.isReady();
    const wrapper = mount(StocktakeTaskListView, {
      global: {
        plugins: [router],
        provide: {
          stocktakeService,
          masterdataService: {
            listActiveWarehouses: vi.fn().mockResolvedValue([
              { id: 88, warehouseNo: 'WH-088', warehouseName: '杭州主仓', status: 'enabled' }
            ])
          },
          warehouseLayoutService: {
            load: vi.fn().mockResolvedValue({
              revision: 1,
              document: {
                schemaVersion: 1,
                completed: true,
                structure: { outline: { id: 'outline', closed: true, nodes: [] }, partitions: [], doors: [], elevators: [], columns: [], zones: [] },
                palletGroups: [
                  { id: 'pile-a01', code: 'P001', name: '地面货堆 P001', left: 1, top: 1, width: 4, height: 4, columns: 2, rows: 2, xMeters: 1, yMeters: 1, lengthMeters: 2, widthMeters: 2, rotation: 0, contents: [] },
                  { id: 'pile-a02', code: 'P002', name: '地面货堆 P002', left: 6, top: 1, width: 4, height: 4, columns: 2, rows: 2, xMeters: 3, yMeters: 1, lengthMeters: 2, widthMeters: 2, rotation: 0, contents: [] }
                ]
              }
            }),
            save: vi.fn()
          },
          warehouseInventoryService: {
            load: vi.fn().mockResolvedValue({
              warehouseId: 88, totalUnits: 20, skuCount: 2, placedUnits: 20, unallocatedUnits: 0, updatedAt: null,
              allocations: [
                { zoneId: null, palletId: 'pile-a01', skuId: 1, skuCode: 'SKU-1', productName: '商品1', skuName: '规格1', specification: null, unitsPerCase: 12, units: 12 },
                { zoneId: null, palletId: 'pile-a02', skuId: 2, skuCode: 'SKU-2', productName: '商品2', skuName: '规格2', specification: null, unitsPerCase: 12, units: 8 }
              ]
            }),
            allocateToPile: vi.fn()
          }
        }
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="create-stocktake"]').trigger('click');
    await wrapper.get('[data-testid="stocktake-create-warehouse"]').setValue('88');
    await flushPromises();
    await wrapper.get('[data-testid="stocktake-scope-selected"]').setValue(true);
    expect(wrapper.text()).toContain('P001');
    expect(wrapper.text()).toContain('P002');

    await wrapper.get('[data-testid="stocktake-create-pile-pile-a01"]').setValue(true);
    await wrapper.get('[data-testid="stocktake-create-pile-pile-a02"]').setValue(true);
    await wrapper.get('[data-testid="stocktake-create-submit"]').trigger('click');
    await flushPromises();

    expect(stocktakeService.createTask).toHaveBeenCalledWith({
      warehouseId: 88,
      blindCount: true,
      palletIds: ['pile-a01', 'pile-a02']
    });
  });

  it('keeps book quantities hidden during blind count and submits only after every row is counted', async () => {
    const details: StocktakeTaskDetails = {
      ...listResult.tasks[0],
      blindCount: true,
      items: [
        {
          id: 101, zoneName: 'A区', palletId: 'ST-001', palletLabel: 'ST-001', skuId: 501,
          skuCode: 'SKU-FISH-500ML-红', productName: '玻璃杯', skuName: '红色款', specification: '红色 / 500ml',
          unitsPerCase: 24, bookQuantity: null, firstCountQuantity: null, recountQuantity: null,
          difference: null, status: 'uncounted'
        },
        {
          id: 102, zoneName: 'A区', palletId: 'ST-002', palletLabel: 'ST-002', skuId: 502,
          skuCode: 'SKU-CUP-12OZ', productName: '随行杯', skuName: '透明款', specification: '透明 / 12oz',
          unitsPerCase: 12, bookQuantity: null, firstCountQuantity: 120, recountQuantity: null,
          difference: null, status: 'counted'
        }
      ]
    };
    const stocktakeService = service({
      getTask: vi.fn().mockResolvedValue(details),
      saveDraft: vi.fn().mockResolvedValue(details),
      submitInitial: vi.fn().mockResolvedValue({ ...details, status: 'awaiting_approval' })
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: true }
      ]
    });
    await router.push('/inventory/stocktakes/1');
    await router.isReady();
    const wrapper = mount(StocktakeExecutionView, {
      props: { taskId: 1 },
      global: { plugins: [router], provide: { stocktakeService } }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="stocktake-mode"]').text()).toContain('盲盘模式');
    expect(wrapper.text()).not.toContain('288');
    expect(wrapper.get('[data-testid="submit-initial"]').attributes()).toHaveProperty('disabled');

    await wrapper.get('[data-testid="count-input-101"]').setValue('286');
    expect(wrapper.get('[data-testid="submit-initial"]').attributes()).not.toHaveProperty('disabled');
    await wrapper.get('[data-testid="save-stocktake-draft"]').trigger('click');
    expect(stocktakeService.saveDraft).toHaveBeenCalledWith(1, [
      { itemId: 101, quantity: 286 },
      { itemId: 102, quantity: 120 }
    ]);

    await wrapper.get('[data-testid="submit-initial"]').trigger('click');
    expect(stocktakeService.submitInitial).toHaveBeenCalledWith(1, [
      { itemId: 101, quantity: 286 },
      { itemId: 102, quantity: 120 }
    ]);
  });

  it('uses the Figma area filter without covering the stocktake details', async () => {
    const details: StocktakeTaskDetails = {
      ...listResult.tasks[0],
      blindCount: true,
      items: [
        {
          id: 301, zoneName: '全仓', palletId: 'pallet-internal-id', palletLabel: 'P001', skuId: 701,
          skuCode: 'MOCK-SKU-001', productName: '星耀高脚红酒杯', skuName: '默认SKU', specification: '透明 / 小号',
          unitsPerCase: 12, bookQuantity: 132, firstCountQuantity: null, recountQuantity: null,
          difference: null, status: 'uncounted'
        },
        {
          id: 302, zoneName: '全仓', palletId: 'pile-a02', palletLabel: 'P002', skuId: 702,
          skuCode: 'MOCK-SKU-002', productName: '星耀水杯', skuName: '默认SKU', specification: '透明 / 中号',
          unitsPerCase: 12, bookQuantity: 96, firstCountQuantity: null, recountQuantity: null,
          difference: null, status: 'uncounted'
        }
      ]
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: true }
      ]
    });
    await router.push('/inventory/stocktakes/1');
    await router.isReady();
    const wrapper = mount(StocktakeExecutionView, {
      props: { taskId: 1 },
      global: { plugins: [router], provide: { stocktakeService: service({ getTask: vi.fn().mockResolvedValue(details) }) } }
    });
    await flushPromises();

    const trigger = wrapper.get('[data-testid="stocktake-area-filter-trigger"]');
    expect(trigger.text()).toContain('全部区域');
    await trigger.trigger('click');
    expect(wrapper.get('[data-testid="stocktake-area-filter-menu"]').isVisible()).toBe(true);
    expect(wrapper.text()).toContain('全仓');
    expect(wrapper.text()).toContain('P001');
    expect(wrapper.text()).not.toContain('pallet-internal-id');

    const pileTrigger = wrapper.get('[data-testid="stocktake-pile-filter-trigger"]');
    expect(pileTrigger.text()).toContain('全部货物堆');
    await pileTrigger.trigger('click');
    await wrapper.get('[data-testid="stocktake-pile-filter-option-pallet-internal-id"]').trigger('click');
    expect(wrapper.text()).toContain('P001');
    expect(wrapper.text()).not.toContain('P002');
  });

  it('submits recount quantities for difference rows and approves reviewed tasks', async () => {
    const differenceTask: StocktakeTaskDetails = {
      ...listResult.tasks[1],
      blindCount: true,
      items: [{
        id: 201, zoneName: 'A区', palletId: 'ST-008', palletLabel: 'ST-008', skuId: 601,
        skuCode: 'SKU-FISH-350ML-橙', productName: '玻璃杯', skuName: '橙色款', specification: '橙色 / 350ml',
        unitsPerCase: 24, bookQuantity: 576, firstCountQuantity: 568, recountQuantity: null,
        difference: -8, status: 'difference'
      }]
    };
    const stocktakeService = service({
      getTask: vi.fn().mockResolvedValue(differenceTask),
      submitRecount: vi.fn().mockResolvedValue({ ...differenceTask, status: 'awaiting_approval' })
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: StocktakeTaskListView },
        { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-execution', component: StocktakeExecutionView, props: (route) => ({ taskId: route.params.id }) }
      ]
    });
    await router.push('/inventory/stocktakes/2');
    await router.isReady();
    const wrapper = mount(StocktakeExecutionView, { props: { taskId: 2 }, global: { plugins: [router], provide: { stocktakeService } } });
    await flushPromises();

    expect(wrapper.text()).toContain('复盘数量');
    await wrapper.get('[data-testid="count-input-201"]').setValue('570');
    await wrapper.get('[data-testid="submit-recount"]').trigger('click');
    expect(stocktakeService.submitRecount).toHaveBeenCalledWith(2, [{ itemId: 201, quantity: 570 }]);

    const approvalTask = { ...differenceTask, status: 'awaiting_approval' as const, items: [{ ...differenceTask.items[0], recountQuantity: 570 }] };
    const approvalService = service({
      getTask: vi.fn().mockResolvedValue(approvalTask),
      approve: vi.fn().mockResolvedValue({ ...approvalTask, status: 'completed' })
    });
    currentUser.value = {
      accessToken: 'approval-token', mobile: '13800000009', roles: [],
      permissions: ['inventory:view', 'inventory:edit'], loginMethod: 'password'
    };
    const approvalWrapper = mount(StocktakeExecutionView, { props: { taskId: 2 }, global: { plugins: [router], provide: { stocktakeService: approvalService } } });
    await flushPromises();
    expect(approvalWrapper.find('[data-testid="approve-stocktake"]').exists()).toBe(false);
    currentUser.value = { ...currentUser.value, permissions: ['inventory:view', 'inventory:approve'] };
    await flushPromises();
    await approvalWrapper.get('[data-testid="approve-stocktake"]').trigger('click');
    expect(approvalService.approve).toHaveBeenCalledWith(2);
  });
});
