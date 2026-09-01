import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DashboardOverview } from '../features/dashboard/types';
import WorkbenchView from './WorkbenchView.vue';

const dashboardMocks = vi.hoisted(() => ({
  getOverview: vi.fn()
}));

vi.mock('../features/dashboard/dashboardService', () => ({
  dashboardService: {
    getOverview: dashboardMocks.getOverview
  }
}));

const overviewFixture: DashboardOverview = {
  summary: {
    productCount: 1234,
    enabledProductCount: 1200,
    skuCount: 3456,
    enabledSupplierCount: 87,
    zeroStockSkuCount: 12,
    lowStockSkuCount: 34,
    orderCount: 56,
    salesAmount: 2434.23,
    outstandingAmount: 456.7,
    draftOrderCount: 8
  },
  salesTrend: [
    { date: '2026-08-31', month: null, salesAmount: 1234.23, orderCount: 20 },
    { date: '2026-09-01', month: null, salesAmount: 1200, orderCount: 36 }
  ],
  stockAlerts: [{
    productId: 11,
    productName: '高脚玻璃杯',
    skuId: 101,
    skuCode: 'SKU-101',
    skuName: '透明款',
    stockQuantity: 4,
    safetyStockQuantity: 12,
    shortageQuantity: 8
  }],
  recentOrders: [{
    orderNo: 'SO202609010001',
    customer: '星海贸易',
    amount: 299,
    status: 'confirmed',
    businessDate: '2026-09-01'
  }]
};

const emptyOverview: DashboardOverview = {
  summary: {
    productCount: 0,
    enabledProductCount: 0,
    skuCount: 0,
    enabledSupplierCount: 0,
    zeroStockSkuCount: 0,
    lowStockSkuCount: 0,
    orderCount: 0,
    salesAmount: 0,
    outstandingAmount: 0,
    draftOrderCount: 0
  },
  salesTrend: [],
  stockAlerts: [],
  recentOrders: []
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function mountWorkbench() {
  return mount(WorkbenchView);
}

describe('WorkbenchView', () => {
  beforeEach(() => {
    dashboardMocks.getOverview.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders every api summary metric and the dense operation lists', async () => {
    dashboardMocks.getOverview.mockResolvedValue(overviewFixture);

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-productCount"]').text()).toContain('1,234');
    expect(wrapper.get('[data-testid="summary-enabledProductCount"]').text()).toContain('1,200');
    expect(wrapper.get('[data-testid="summary-skuCount"]').text()).toContain('3,456');
    expect(wrapper.get('[data-testid="summary-enabledSupplierCount"]').text()).toContain('87');
    expect(wrapper.get('[data-testid="summary-zeroStockSkuCount"]').text()).toContain('12');
    expect(wrapper.get('[data-testid="summary-lowStockSkuCount"]').text()).toContain('34');
    expect(wrapper.get('[data-testid="summary-orderCount"]').text()).toContain('56');
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');
    expect(wrapper.get('[data-testid="summary-outstandingAmount"]').text()).toContain('456.70');
    expect(wrapper.get('[data-testid="summary-draftOrderCount"]').text()).toContain('8');
    expect(wrapper.get('[data-testid="stock-alert-list"]').text()).toContain('SKU-101');
    expect(wrapper.get('[data-testid="stock-alert-list"]').text()).toContain('缺口 8');
    expect(wrapper.get('[data-testid="recent-order-list"]').text()).toContain('SO202609010001');
    expect(wrapper.get('[data-testid="recent-order-list"]').text()).toContain('¥299.00');
    expect(wrapper.get('[data-testid="recent-order-list"]').text()).toContain('已确认');
  });

  it('loads week by default and reloads the selected period with accessible controls', async () => {
    dashboardMocks.getOverview.mockResolvedValue(overviewFixture);
    const wrapper = mountWorkbench();
    await flushPromises();

    const weekButton = wrapper.get('[data-testid="period-week"]');
    const yearButton = wrapper.get('[data-testid="period-year"]');
    expect(weekButton.element.tagName).toBe('BUTTON');
    expect(weekButton.attributes('aria-pressed')).toBe('true');
    expect(yearButton.attributes('aria-pressed')).toBe('false');
    expect(dashboardMocks.getOverview).toHaveBeenCalledWith('week');

    await yearButton.trigger('click');
    await flushPromises();

    expect(dashboardMocks.getOverview).toHaveBeenLastCalledWith('year');
    expect(yearButton.attributes('aria-pressed')).toBe('true');
    expect(weekButton.attributes('aria-pressed')).toBe('false');
  });

  it('does not request the already selected period again', async () => {
    dashboardMocks.getOverview.mockResolvedValue(overviewFixture);
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-week"]').trigger('click');
    await flushPromises();

    expect(dashboardMocks.getOverview).toHaveBeenCalledTimes(1);
    expect(dashboardMocks.getOverview).toHaveBeenCalledWith('week');
  });

  it('uses one stable chart area for sales bars and the order line', async () => {
    dashboardMocks.getOverview.mockResolvedValue(overviewFixture);

    const wrapper = mountWorkbench();
    await flushPromises();

    const chart = wrapper.get('[data-testid="sales-trend-chart"]');
    expect(chart.findAll('[data-testid="sales-bar"]')).toHaveLength(2);
    expect(chart.find('[data-testid="order-line"]').exists()).toBe(true);
    expect(chart.classes()).toContain('aspect-[16/7]');
  });

  it('does not draw fake marks when trend values are all zero', async () => {
    dashboardMocks.getOverview.mockResolvedValue({
      ...emptyOverview,
      salesTrend: [{ date: '2026-09-01', month: null, salesAmount: 0, orderCount: 0 }]
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.find('[data-testid="sales-trend-chart"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="sales-bar"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="order-line"]').exists()).toBe(false);
  });

  it('keeps the current overview visible while a new period is loading', async () => {
    const monthRequest = deferred<DashboardOverview>();
    dashboardMocks.getOverview
      .mockResolvedValueOnce(overviewFixture)
      .mockReturnValueOnce(monthRequest.promise);
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-month"]').trigger('click');

    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');
    expect(wrapper.get('[data-testid="dashboard-content"]').attributes('aria-busy')).toBe('true');
    expect(wrapper.find('[data-testid="dashboard-loading"]').exists()).toBe(true);

    monthRequest.resolve(overviewFixture);
    await flushPromises();
  });

  it('prevents a slow earlier request from replacing the latest period', async () => {
    const monthRequest = deferred<DashboardOverview>();
    const yearRequest = deferred<DashboardOverview>();
    const yearOverview: DashboardOverview = {
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 9876.54 }
    };
    dashboardMocks.getOverview
      .mockResolvedValueOnce(overviewFixture)
      .mockReturnValueOnce(monthRequest.promise)
      .mockReturnValueOnce(yearRequest.promise);
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-month"]').trigger('click');
    await wrapper.get('[data-testid="period-year"]').trigger('click');
    yearRequest.resolve(yearOverview);
    await flushPromises();
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('9,876.54');

    monthRequest.resolve({
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 111.11 }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('9,876.54');
    expect(wrapper.get('[data-testid="period-year"]').attributes('aria-pressed')).toBe('true');
  });

  it('keeps loading active when an older request settles before the latest request', async () => {
    const monthRequest = deferred<DashboardOverview>();
    const yearRequest = deferred<DashboardOverview>();
    dashboardMocks.getOverview
      .mockResolvedValueOnce(overviewFixture)
      .mockReturnValueOnce(monthRequest.promise)
      .mockReturnValueOnce(yearRequest.promise);
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-month"]').trigger('click');
    await wrapper.get('[data-testid="period-year"]').trigger('click');
    monthRequest.resolve({
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 111.11 }
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="dashboard-content"]').attributes('aria-busy')).toBe('true');
    expect(wrapper.find('[data-testid="dashboard-loading"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');

    yearRequest.resolve({
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 9999 }
    });
    await flushPromises();
    expect(wrapper.get('[data-testid="dashboard-content"]').attributes('aria-busy')).toBe('false');
  });

  it('does not update component state or warn when a request settles after unmount', async () => {
    const request = deferred<DashboardOverview>();
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    dashboardMocks.getOverview.mockReturnValue(request.promise);
    const wrapper = mountWorkbench();
    const setupState = (wrapper.vm.$ as unknown as {
      setupState: { overview: DashboardOverview | null };
    }).setupState;

    wrapper.unmount();
    request.resolve(overviewFixture);
    await flushPromises();

    expect(setupState.overview).toBeNull();
    expect(warn).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  it('ignores an older response that settles after a failed request is retried', async () => {
    const monthRequest = deferred<DashboardOverview>();
    const retriedOverview: DashboardOverview = {
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 7777.77 }
    };
    dashboardMocks.getOverview
      .mockResolvedValueOnce(overviewFixture)
      .mockReturnValueOnce(monthRequest.promise)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(retriedOverview);
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-month"]').trigger('click');
    await wrapper.get('[data-testid="period-year"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="dashboard-error"]').text()).toContain('已保留上次数据');

    await wrapper.get('[data-testid="dashboard-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('7,777.77');

    monthRequest.resolve({
      ...overviewFixture,
      summary: { ...overviewFixture.summary, salesAmount: 222.22 }
    });
    await flushPromises();
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('7,777.77');
    expect(wrapper.get('[data-testid="period-year"]').attributes('aria-pressed')).toBe('true');
  });

  it('keeps previous data and offers retry when a period switch fails', async () => {
    dashboardMocks.getOverview
      .mockResolvedValueOnce(overviewFixture)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({
        ...overviewFixture,
        summary: { ...overviewFixture.summary, salesAmount: 5555 }
      });
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.get('[data-testid="period-month"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');
    expect(wrapper.get('[data-testid="dashboard-error"]').text()).toContain('已保留上次数据');

    await wrapper.get('[data-testid="dashboard-retry"]').trigger('click');
    await flushPromises();
    expect(dashboardMocks.getOverview).toHaveBeenLastCalledWith('month');
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('5,555.00');
  });

  it('shows an inline initial error and recovers through retry', async () => {
    dashboardMocks.getOverview
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(overviewFixture);
    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="dashboard-error"]').attributes('role')).toBe('alert');
    expect(wrapper.get('[data-testid="dashboard-error"]').text()).toContain('工作台数据加载失败');
    expect(wrapper.find('[data-testid="dashboard-content"]').exists()).toBe(false);

    await wrapper.get('[data-testid="dashboard-retry"]').trigger('click');
    await flushPromises();

    expect(dashboardMocks.getOverview).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');
  });

  it('shows real zero summary values and independent empty panels without demo values', async () => {
    dashboardMocks.getOverview.mockResolvedValue(emptyOverview);
    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-productCount"]').text()).toContain('0');
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('0.00');
    expect(wrapper.text()).toContain('暂无销售趋势');
    expect(wrapper.text()).toContain('暂无库存预警');
    expect(wrapper.text()).toContain('暂无最近订单');
    expect(wrapper.text()).not.toContain('639');
    expect(wrapper.text()).not.toContain('待发货');
  });

  it('formats missing business values safely instead of leaking invalid text', async () => {
    dashboardMocks.getOverview.mockResolvedValue({
      summary: {
        ...emptyOverview.summary,
        productCount: null,
        salesAmount: null
      },
      salesTrend: null,
      stockAlerts: null,
      recentOrders: null
    } as unknown as DashboardOverview);
    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="summary-productCount"]').text()).toContain('--');
    expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('--');
    expect(wrapper.text()).not.toContain('NaN');
    expect(wrapper.text()).not.toContain('null');
    expect(wrapper.text()).toContain('暂无销售趋势');
    expect(wrapper.text()).toContain('暂无库存预警');
    expect(wrapper.text()).toContain('暂无最近订单');
  });
});
