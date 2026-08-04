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
  'finance:receipt'
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
      '产品资料',
      '分类管理',
      '客户管理',
      '供应商管理',
      '仓库管理',
      '库存管理',
      '销售单据',
      '财务管理'
    ]) {
      expect(wrapper.text()).toContain(label);
    }
    expect(wrapper.text()).not.toContain('销售开单');
  });

  it('uses the approved prototype navigation structure', () => {
    const wrapper = mount(SidebarNav, { global: { plugins: [router] } });

    expect(wrapper.find('[data-testid="prototype-sidebar-nav"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('Menu');
    expect(wrapper.text()).toContain('Topics');
    expect(wrapper.find('[data-testid="prototype-sidebar-user"]').exists()).toBe(true);
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
    expect(wrapper.text()).not.toContain('产品资料');
    expect(wrapper.text()).not.toContain('分类管理');
    expect(wrapper.text()).not.toContain('销售开单');
    expect(wrapper.text()).not.toContain('财务管理');
    expect(wrapper.text()).toContain('13800138000');
  });
});
