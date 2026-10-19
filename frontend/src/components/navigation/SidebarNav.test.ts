import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../../router';
import SidebarNav from './SidebarNav.vue';
import { clearCurrentUser, restoreCurrentUser, saveCurrentUser } from '../../services/authSession';

const CURRENT_USER_STORAGE_KEY = 'bebefish_current_user';
const ADMIN_PERMISSIONS = [
  'dashboard:view',
  'masterdata:view',
  'product:view',
  'shipping:view',
  'platform:view',
  'warehouse:view',
  'inventory:view',
  'inventory:adjust',
  'sales:view',
  'sales:create',
  'finance:view',
  'finance:receipt',
  'organization:view',
  'system:role:view'
];

function storeCurrentUser(permissions = ADMIN_PERMISSIONS, roles = ['ADMIN']) {
  localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify({
    accessToken: 'token', mobile: '13800138000', roles, permissions, loginMethod: 'password'
  }));
}

async function openGroup(wrapper: VueWrapper, label: string) {
  await wrapper.findAll('button').find(button => button.text() === label)!.trigger('click');
}

describe('SidebarNav', () => {
  beforeEach(() => {
    clearCurrentUser();
    localStorage.clear();
    storeCurrentUser();
  });

  afterEach(() => {
    clearCurrentUser();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('starts with every group closed and opens only the selected group', async () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    expect(wrapper.findAll('button[aria-expanded="true"]')).toHaveLength(0);
    expect(wrapper.find('a[href="/products"]').exists()).toBe(false);
    await openGroup(wrapper, '商品管理');
    expect(wrapper.find('a[href="/products"]').exists()).toBe(true);
    await openGroup(wrapper, '库存管理');
    expect(wrapper.find('a[href="/products"]').exists()).toBe(false);
    expect(wrapper.find('a[href="/inventory/balances"]').exists()).toBe(true);
    expect(wrapper.findAll('button[aria-expanded="true"]')).toHaveLength(1);
    wrapper.unmount();
  });

  it('places the calculator under software tools and preserves product navigation', async () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '商品管理');
    expect(wrapper.text()).not.toContain('拼多多成本计算');
    expect(wrapper.get('a[href="/products"]').text()).toBe('商品资料');
    await openGroup(wrapper, '软件工具');
    expect(wrapper.get('a[href="/tools/pdd-cost"]').text()).toBe('拼多多成本计算');
    wrapper.unmount();
  });

  it('restores only the last expanded group after logout and a new login', async () => {
    let wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '商品管理');
    await openGroup(wrapper, '库存管理');
    wrapper.unmount();
    clearCurrentUser();
    storeCurrentUser();
    wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    expect(wrapper.findAll('button[aria-expanded="true"]').map(button => button.text())).toEqual(['库存管理']);
    wrapper.unmount();
  });

  it('remembers all groups closed when the last open group is collapsed', async () => {
    let wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '商品管理');
    await openGroup(wrapper, '商品管理');
    wrapper.unmount();
    clearCurrentUser();
    storeCurrentUser();
    wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    expect(wrapper.findAll('button[aria-expanded="true"]')).toHaveLength(0);
    wrapper.unmount();
  });

  it('isolates accounts and synchronizes desktop and mobile sidebars', async () => {
    const desktop = mount(SidebarNav, { global: { plugins: [router] } });
    const mobile = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(desktop, '商品管理');
    expect(mobile.findAll('button[aria-expanded="true"]').map(button => button.text())).toEqual(['商品管理']);
    saveCurrentUser({accessToken:'other-token',mobile:'13900139000',roles:['ADMIN'],permissions:ADMIN_PERMISSIONS,loginMethod:'password'});
    await mobile.vm.$nextTick();
    expect(mobile.findAll('button[aria-expanded="true"]')).toHaveLength(0);
    await openGroup(mobile, '财务管理');
    storeCurrentUser();restoreCurrentUser();await desktop.vm.$nextTick();
    expect(desktop.findAll('button[aria-expanded="true"]').map(button => button.text())).toEqual(['商品管理']);
    desktop.unmount();mobile.unmount();
  });

  it('ignores invalid saved preferences and never opens a group without permission', async () => {
    const key='bebefish_sidebar_group_v1:password:mobile:13800138000';
    localStorage.setItem(key,'invalid json');
    let wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    expect(wrapper.findAll('button[aria-expanded="true"]')).toHaveLength(0);
    await openGroup(wrapper, '库存管理');wrapper.unmount();clearCurrentUser();
    storeCurrentUser(['product:view']);
    wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    expect(wrapper.findAll('button[aria-expanded="true"]')).toHaveLength(0);
    expect(wrapper.text()).not.toContain('库存管理');wrapper.unmount();
  });

  it('still toggles groups if preference storage is unavailable', async () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('storage unavailable');});
    await openGroup(wrapper, '商品管理');
    expect(wrapper.find('a[href="/products"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows each approved first-level menu item', () => {
    const wrapper = mount(SidebarNav, {
      global: {
        plugins: [router]
      }
    });

    for (const label of [
      '工作台',
      '商品管理',
      '软件工具',
      '分类管理',
      '客户管理',
      '供应商管理',
      '平台管理',
      '发货管理',
      '库存管理',
      '销售单据',
      '财务管理',
      '组织架构',
      '权限管理'
    ]) {
      expect(wrapper.text()).toContain(label);
    }
    expect(wrapper.text()).not.toContain('销售开单');
    expect(wrapper.get('a[href="/platforms"]').text()).toBe('平台管理');
  });

  it('shows every configured menu item to a super administrator with an incomplete permission snapshot', async () => {
    storeCurrentUser([], ['SUPER_ADMIN']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    for (const group of ['商品管理', '软件工具', '库存管理', '组织架构', '财务管理']) {
      await openGroup(wrapper, group);
      const expected = group === '商品管理' ? ['商品资料']
        : group === '软件工具' ? ['拼多多成本计算']
        : group === '库存管理' ? ['仓库管理', '库存余额', '库存流水', '库存调整']
        : group === '组织架构' ? ['员工管理', '部门管理', '岗位管理'] : ['收款记录', '欠款应收'];
      for (const label of expected) expect(wrapper.text()).toContain(label);
    }

    for (const label of [
      '工作台',
      '商品管理',
      '软件工具',
      '分类管理',
      '客户管理',
      '供应商管理',
      '库存管理',
      '组织架构',
      '权限管理',
      '销售单据',
      '财务管理',
      '收款记录',
      '欠款应收'
    ]) {
      expect(wrapper.text()).toContain(label);
    }
  });

  it('shows organization children only with organization permission', async () => {
    storeCurrentUser(['organization:view', 'system:role:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '组织架构');

    expect(wrapper.text()).toContain('组织架构');
    expect(wrapper.text()).toContain('员工管理');
    expect(wrapper.text()).toContain('部门管理');
    expect(wrapper.text()).toContain('岗位管理');
    expect(wrapper.text()).toContain('权限管理');
    expect(wrapper.get('a[href="/organization/permissions"]').classes()).not.toContain('ml-6');

    wrapper.unmount();
    storeCurrentUser(['product:view']);
    const hiddenWrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(hiddenWrapper.text()).not.toContain('组织架构');
    expect(hiddenWrapper.text()).not.toContain('员工管理');
    expect(hiddenWrapper.text()).not.toContain('部门管理');
    expect(hiddenWrapper.text()).not.toContain('岗位管理');
  });

  it('keeps organization navigation visible without exposing permissions to view-only organization users', async () => {
    storeCurrentUser(['organization:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '组织架构');

    expect(wrapper.text()).toContain('组织架构');
    expect(wrapper.text()).toContain('员工管理');
    expect(wrapper.text()).not.toContain('权限管理');
  });

  it('does not expose platform management to shipping-only viewers', () => {
    storeCurrentUser(['shipping:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(wrapper.find('a[href="/platforms"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('发货管理');
  });

  it('shows the permission entry when system role viewing is the only granted navigation permission', () => {
    storeCurrentUser(['system:role:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(wrapper.text()).not.toContain('组织架构');
    expect(wrapper.text()).toContain('权限管理');
    expect(wrapper.text()).not.toContain('员工管理');
    expect(wrapper.get('a[href="/organization/permissions"]').classes()).not.toContain('ml-6');
  });

  it('opens warehouses from inventory management without a standalone canvas menu', async () => {
    storeCurrentUser(['warehouse:view', 'inventory:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '库存管理');

    expect(wrapper.findAll('a').map((link) => link.text())).toEqual([
      '仓库管理',
      '库存余额',
      '库存流水',
      '库存盘点'
    ]);
    expect(wrapper.get('a[href="/warehouses"]').text()).toBe('仓库管理');
    expect(wrapper.text()).not.toContain('仓库画布');
  });

  it('lets warehouse viewers open warehouse management without inventory viewing permission', async () => {
    storeCurrentUser(['warehouse:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '库存管理');

    expect(wrapper.get('a[href="/warehouses"]').text()).toBe('仓库管理');
    expect(wrapper.text()).not.toContain('库存余额');
  });

  it('shows inventory stocktake for users allowed to view inventory', async () => {
    storeCurrentUser(['inventory:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
    await openGroup(wrapper, '库存管理');

    expect(wrapper.get('a[href="/inventory/stocktakes"]').text()).toBe('库存盘点');
  });

  it('uses the full-height ERP navigation structure without a card shell', () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    const sidebar = wrapper.get('[data-testid="erp-sidebar"]');
    expect(sidebar.classes()).not.toContain('rounded-[24px]');
    expect(sidebar.classes().some((name) => name.startsWith('shadow-'))).toBe(false);
    expect(wrapper.text()).toContain('Menu');
    expect(wrapper.text()).not.toContain('Topics');
    expect(wrapper.text()).not.toContain('库存协同');
    expect(wrapper.find('[data-testid="erp-sidebar-user"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="erp-sidebar-brand"]').classes()).toContain('h-[88px]');
    expect(wrapper.get('[data-testid="erp-sidebar-logo"]').classes()).toEqual(expect.arrayContaining([
      'h-10',
      'w-10',
      'rounded-lg'
    ]));
    expect(wrapper.get('[data-testid="erp-sidebar-menu"]').classes()).toContain('px-4');
  });

  it('renders menu items from the authenticated API permission data', () => {
    storeCurrentUser(['product:view']);
    const wrapper = mount(SidebarNav, {
      props: {
        navigationItems: [
          {
            id: 'custom-product',
            label: '自定义产品菜单',
            icon: 'product',
            routeName: 'products',
            permission: 'product:view'
          }
        ]
      },
      global: { plugins: [router] }
    });

    expect(wrapper.text()).toContain('自定义产品菜单');
    expect(wrapper.findAll('a').some((link) => link.text().includes('商品管理'))).toBe(false);
    expect(wrapper.text()).not.toContain('分类管理');
    expect(wrapper.text()).not.toContain('销售开单');
    expect(wrapper.text()).not.toContain('财务管理');
    expect(wrapper.text()).toContain('13800138000');
  });
});
