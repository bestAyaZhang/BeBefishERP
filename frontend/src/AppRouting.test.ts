import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import router from './router';
import { getCurrentUser } from './services/auth';
import { ACCESS_TOKEN_STORAGE_KEY } from './types/auth';

const dashboardMocks = vi.hoisted(() => ({
  getOverview: vi.fn()
}));

vi.mock('./services/auth', () => ({
  getCurrentUser: vi.fn()
}));

vi.mock('./features/dashboard/dashboardService', () => ({
  dashboardService: {
    getOverview: dashboardMocks.getOverview
  }
}));

describe('application routes', () => {
  let wrapper: ReturnType<typeof mount> | null = null;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test-token');
    dashboardMocks.getOverview.mockReset();
    dashboardMocks.getOverview.mockResolvedValue({
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
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it('renders the category view for an authenticated user', async () => {
    await router.push('/categories');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('分类管理');
    expect(wrapper.get('[data-testid="erp-shell"]').classes()).not.toContain('lg:px-8');
    expect(wrapper.get('[data-testid="erp-layout-grid"]').classes()).toContain('lg:grid-cols-[244px_minmax(0,1fr)]');
    expect(wrapper.find('[data-testid="erp-sidebar"]').exists()).toBe(true);
  });

  it('renders the live workbench inside the shared ERP layout', async () => {
    await router.push('/workbench');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-shell"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workspace-prototype-content"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('商品与供应链');
    expect(wrapper.text()).toContain('销售与订单趋势');
  });

  it('renders the product route with the live product master-data page', async () => {
    await router.push('/products');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-shell"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-list-page"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-keyword"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('Master Data');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('Products');
  });

  it('renders the sales order create route with the live order form', async () => {
    await router.push('/sales/create');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-shell"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="sales-create-page"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('Sales');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('Create Order');
  });

  it('renders the sales order list and opens the create form from its add action', async () => {
    await router.push('/sales/orders');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="sales-orders-page"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('Orders');

    await wrapper.get('[data-testid="add-sales-order"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('sales-create');
    expect(wrapper.find('[data-testid="sales-create-page"]').exists()).toBe(true);
  });

  it('updates the shared workspace content when navigating from dashboard to products', async () => {
    await router.push('/workbench');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('商品与供应链');

    await router.push('/products');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('products');
    expect(wrapper.text()).toContain('商品列表');
    expect(wrapper.text()).not.toContain('商品与供应链');
  });

  it('opens the shared mobile navigation from the workbench header', async () => {
    await router.push('/workbench');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.get('button[aria-label="打开菜单"]').trigger('click');

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(true);
  });

  it('restores menu permissions from the current-user API for a legacy token session', async () => {
    localStorage.removeItem('bebefish_current_user');
    vi.mocked(getCurrentUser).mockResolvedValue({
      accessToken: 'test-token',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view', 'product:view', 'masterdata:view', 'inventory:view', 'inventory:adjust', 'sales:create', 'sales:view', 'finance:view', 'finance:receipt'],
      loginMethod: 'password'
    });

    await router.push('/categories');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(getCurrentUser).toHaveBeenCalledWith('test-token');
    expect(wrapper.text()).toContain('产品资料');
    expect(wrapper.text()).toContain('财务管理');
  });

  it('uses the breadcrumb as route navigation for inventory pages', async () => {
    await router.push('/inventory/ledger');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('Inventory');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('Ledger');
    expect(wrapper.get('[data-testid="breadcrumb-group"]').attributes('href')).toBe('/inventory/balances');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').attributes('href')).toBe('/inventory/ledger');

    await wrapper.get('[data-testid="breadcrumb-group"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('inventory-balances');
  });

  it('opens the customer full-page form for a data-heavy new record', async () => {
    await router.push('/customers');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.get('[data-testid="add-customer"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('customer-new');
    expect(wrapper.find('[data-testid="customer-form-page"]').exists()).toBe(true);
  });
});
