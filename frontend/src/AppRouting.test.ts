import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import router from './router';
import { getCurrentUser } from './services/auth';
import { clearCurrentUser, saveCurrentUser } from './services/authSession';
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

vi.mock('./features/permission/permissionService', async () => {
  const { createMockPermissionService } = await import('./features/permission/mockPermissionService');
  const { createMockOrganizationService } = await import('./features/organization/mockOrganizationService');
  const organizationService = createMockOrganizationService();
  return { permissionService: createMockPermissionService(organizationService) };
});

describe('application routes', () => {
  let wrapper: ReturnType<typeof mount> | null = null;

  beforeEach(() => {
    localStorage.clear();
    clearCurrentUser();
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
    expect(wrapper.get('[data-testid="product-list-page"]').classes()).toEqual(expect.arrayContaining([
      'h-full',
      'min-h-0'
    ]));
    expect(wrapper.find('[data-testid="product-keyword"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('主数据');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('商品资料');
  });

  it.each([
    ['/organization/employees', '员工管理', 'employee-workspace'],
    ['/organization/departments', '部门管理', 'department-workspace'],
    ['/organization/positions', '岗位管理', 'position-workspace']
  ])('renders the organization route %s inside the shared ERP layout', async (path, title, testId) => {
    await router.push(path);
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-shell"]').exists()).toBe(true);
    expect(wrapper.find(`[data-testid="${testId}"]`).exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('组织架构');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe(title);
  });

  it('renders permission management as an independent navigation area', async () => {
    saveCurrentUser({
      accessToken: 'test-token', mobile: '13800138000', roles: ['ADMIN'], permissions: ['system:role:view'], loginMethod: 'password'
    });
    await router.push('/organization/permissions');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-shell"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="permission-workspace"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('权限管理');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('权限管理');
  });

  it('renders the final product create, detail, and edit route surfaces', async () => {
    await router.push('/products/new');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('product-new');
    expect(wrapper.find('[data-testid="product-editor-view"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('商品资料');

    await router.push('/products/1');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('product-detail');
    expect(wrapper.find('[data-testid="product-detail-view"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('商品资料');

    await router.push('/products/1/edit');
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('product-edit');
    expect(wrapper.find('[data-testid="product-editor-view"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('商品资料');
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
    expect(wrapper.text()).toContain('商品管理');
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

  it('renders the warehouse canvas with its inventory breadcrumb and route-only flush content shell', async () => {
    await router.push('/inventory/warehouse-canvas');
    await router.isReady();
    wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('仓库画布');
    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('库存管理');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('仓库画布');
    expect(wrapper.get('[data-testid="erp-page-content"]').classes()).toContain('p-0');
    expect(wrapper.get('[data-testid="erp-page-content"]').classes()).not.toContain('p-4');

    await router.push('/inventory/balances');
    await flushPromises();

    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('库存管理');
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('库存余额');
    expect(wrapper.get('[data-testid="erp-page-content"]').classes()).toContain('p-0');
    expect(wrapper.get('[data-testid="erp-page-content"]').classes()).not.toContain('p-4');
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
