import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { clearCurrentUser, saveCurrentUser } from '../services/authSession';
import ErpLayout from './ErpLayout.vue';

const TestPage = { template: '<div data-testid="test-page">Page</div>' };

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workbench', name: 'workbench', component: TestPage },
      { path: '/products', name: 'products', component: TestPage },
      { path: '/products/new', name: 'product-new', component: TestPage },
      { path: '/products/:id/edit', name: 'product-edit', component: TestPage },
      { path: '/products/:id', name: 'product-detail', component: TestPage }
    ]
  });
}

async function mountLayout(path = '/workbench') {
  const router = createTestRouter();
  await router.push(path);
  await router.isReady();

  const wrapper = mount(ErpLayout, { global: { plugins: [router] } });
  await flushPromises();
  return { router, wrapper };
}

async function openMobileNavigation(wrapper: VueWrapper) {
  await wrapper.get('button[aria-label="打开菜单"]').trigger('click');
  expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(true);
}

describe('ErpLayout', () => {
  let wrapper: VueWrapper | null = null;
  let router: Router | null = null;

  beforeEach(() => {
    clearCurrentUser();
    saveCurrentUser({
      accessToken: 'token',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view', 'product:view'],
      loginMethod: 'password'
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    router = null;
    clearCurrentUser();
    localStorage.clear();
  });

  it('fills the viewport with a fixed desktop sidebar and stable content frame', async () => {
    ({ router, wrapper } = await mountLayout());

    expect(wrapper.get('[data-testid="erp-shell"]').classes()).toContain('min-h-screen');
    expect(wrapper.get('[data-testid="erp-shell"]').classes()).not.toContain('px-4');
    expect(wrapper.get('[data-testid="erp-layout-grid"]').classes().join(' ')).toContain('lg:grid-cols-[244px_minmax(0,1fr)]');
    expect(wrapper.findAll('[class]').some((node) => node.classes().includes('max-w-[1440px]'))).toBe(false);
    expect(wrapper.get('[data-testid="erp-sidebar"]').classes()).not.toContain('rounded-[24px]');
    expect(wrapper.get('[data-testid="erp-sidebar"]').classes().some((name) => name.startsWith('shadow-'))).toBe(false);
    expect(wrapper.get('[data-testid="erp-main"]').classes()).toContain('min-w-0');
    expect(wrapper.get('[data-testid="erp-topbar"]').classes()).toEqual(expect.arrayContaining(['h-16', 'border-b']));
    expect(wrapper.get('[data-testid="erp-page-content"]').classes()).toEqual(expect.arrayContaining(['p-4', 'lg:p-6']));
  });

  it.each([
    ['/workbench', 'Dashboards', 'Analytics', '/workbench'],
    ['/products/new', 'Master Data', 'New Product', '/products/new'],
    ['/products/42', 'Master Data', 'Product Detail', '/products'],
    ['/products/42/edit', 'Master Data', 'Edit Product', '/products']
  ])('maps %s to its breadcrumb and return target', async (path, group, title, currentHref) => {
    ({ router, wrapper } = await mountLayout(path));

    expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe(group);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe(title);
    expect(wrapper.get('[data-testid="breadcrumb-current"]').attributes('href')).toBe(currentHref);
  });

  it('keeps the drawer open for inside clicks and closes it from the backdrop', async () => {
    ({ router, wrapper } = await mountLayout());
    await openMobileNavigation(wrapper);

    await wrapper.get('[data-testid="erp-mobile-drawer"]').trigger('click');
    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(true);

    await wrapper.get('[data-testid="erp-mobile-navigation"]').trigger('click');
    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
  });

  it('closes the mobile navigation on Escape', async () => {
    ({ router, wrapper } = await mountLayout());
    await openMobileNavigation(wrapper);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
  });

  it('closes the mobile navigation after a route change', async () => {
    ({ router, wrapper } = await mountLayout());
    await openMobileNavigation(wrapper);

    await router.push('/products');
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
  });
});
