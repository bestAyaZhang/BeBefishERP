import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import router from '../../router';
import SidebarNav from './SidebarNav.vue';

const CURRENT_USER_STORAGE_KEY = 'bebefish_current_user';
const ADMIN_PERMISSIONS = [
  'dashboard:view',
  'masterdata:view',
  'product:view',
  'inventory:view',
  'inventory:adjust',
  'sales:view',
  'sales:create',
  'finance:view',
  'finance:receipt',
  'organization:view'
];

function storeCurrentUser(permissions = ADMIN_PERMISSIONS) {
  localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify({
    accessToken: 'token', mobile: '13800138000', roles: ['ADMIN'], permissions, loginMethod: 'password'
  }));
}

describe('SidebarNav', () => {
  beforeEach(() => {
    storeCurrentUser();
  });

  afterEach(() => {
    localStorage.clear();
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
      '分类管理',
      '客户管理',
      '供应商管理',
      '仓库管理',
      '库存管理',
      '销售单据',
      '财务管理',
      '组织架构'
    ]) {
      expect(wrapper.text()).toContain(label);
    }
    expect(wrapper.text()).not.toContain('销售开单');
  });

  it('shows organization children only with organization permission', () => {
    storeCurrentUser(['organization:view', 'system:role:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(wrapper.text()).toContain('组织架构');
    expect(wrapper.text()).toContain('员工管理');
    expect(wrapper.text()).toContain('部门管理');
    expect(wrapper.text()).toContain('岗位管理');
    expect(wrapper.text()).toContain('权限管理');

    wrapper.unmount();
    storeCurrentUser(['product:view']);
    const hiddenWrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(hiddenWrapper.text()).not.toContain('组织架构');
    expect(hiddenWrapper.text()).not.toContain('员工管理');
    expect(hiddenWrapper.text()).not.toContain('部门管理');
    expect(hiddenWrapper.text()).not.toContain('岗位管理');
  });

  it('keeps organization navigation visible without exposing permissions to view-only organization users', () => {
    storeCurrentUser(['organization:view']);
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(wrapper.text()).toContain('组织架构');
    expect(wrapper.text()).toContain('员工管理');
    expect(wrapper.text()).not.toContain('权限管理');
  });

  it('uses the full-height ERP navigation structure without a card shell', () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    const sidebar = wrapper.get('[data-testid="erp-sidebar"]');
    expect(sidebar.classes()).not.toContain('rounded-[24px]');
    expect(sidebar.classes().some((name) => name.startsWith('shadow-'))).toBe(false);
    expect(wrapper.text()).toContain('Menu');
    expect(wrapper.text()).toContain('Topics');
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
