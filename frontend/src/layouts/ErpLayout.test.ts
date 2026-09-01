import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  const wrapper = mount(ErpLayout, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises();
  return { router, wrapper };
}

async function openMobileNavigation(wrapper: VueWrapper) {
  const trigger = wrapper.get('button[aria-label="打开菜单"]');
  await trigger.trigger('click');
  await flushPromises();
  expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(true);
  return trigger;
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
    document.body.innerHTML = '';
    clearCurrentUser();
    localStorage.clear();
    vi.restoreAllMocks();
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

  it('keeps the 320px topbar shrink-safe while preserving full desktop actions', async () => {
    ({ router, wrapper } = await mountLayout('/products/42/edit'));

    expect(wrapper.get('[data-testid="erp-topbar"]').classes()).toEqual(expect.arrayContaining(['gap-2', 'sm:gap-4']));
    expect(wrapper.get('[data-testid="erp-topbar-leading"]').classes()).toEqual(expect.arrayContaining(['min-w-0', 'flex-1', 'overflow-hidden']));
    expect(wrapper.get('[data-testid="erp-breadcrumb"]').classes()).toEqual(expect.arrayContaining(['min-w-0', 'overflow-hidden']));
    expect(wrapper.get('[data-testid="breadcrumb-group"]').classes()).toEqual(expect.arrayContaining(['hidden', 'sm:block']));
    expect(wrapper.get('[data-testid="breadcrumb-separator"]').classes()).toEqual(expect.arrayContaining(['hidden', 'sm:inline']));
    expect(wrapper.get('[data-testid="breadcrumb-current"]').classes()).toEqual(expect.arrayContaining(['min-w-0', 'truncate']));
    expect(wrapper.get('[data-testid="erp-topbar-actions"]').classes()).toContain('shrink-0');

    for (const testId of ['erp-mobile-trigger', 'erp-account-action', 'erp-settings-action', 'erp-logout-action']) {
      expect(wrapper.get(`[data-testid="${testId}"]`).classes()).toContain('shrink-0');
    }
    for (const testId of ['erp-account-action', 'erp-settings-action']) {
      expect(wrapper.get(`[data-testid="${testId}"]`).classes()).toEqual(expect.arrayContaining(['hidden', 'sm:flex']));
    }
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
    const trigger = await openMobileNavigation(wrapper);

    await wrapper.get('[data-testid="erp-mobile-drawer"]').trigger('click');
    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(true);

    await wrapper.get('[data-testid="erp-mobile-navigation"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it('focuses the drawer close button and isolates the background when opened', async () => {
    ({ router, wrapper } = await mountLayout());
    await openMobileNavigation(wrapper);

    expect(document.activeElement).toBe(wrapper.get('[data-testid="erp-mobile-close"]').element);
    expect(wrapper.get('[data-testid="erp-layout-grid"]').attributes('inert')).toBeDefined();
    expect(wrapper.get('[data-testid="erp-layout-grid"]').attributes('aria-hidden')).toBe('true');
  });

  it('cycles Tab and Shift+Tab within the mobile drawer', async () => {
    ({ router, wrapper } = await mountLayout());
    await openMobileNavigation(wrapper);

    const drawer = wrapper.get('[data-testid="erp-mobile-drawer"]');
    const focusable = drawer.findAll('a[href], button:not([disabled])');
    const first = focusable[0].element as HTMLElement;
    const last = focusable[focusable.length - 1].element as HTMLElement;

    last.focus();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(first);

    first.focus();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(last);
  });

  it('closes on Escape, restores the trigger focus, and releases the background', async () => {
    ({ router, wrapper } = await mountLayout());
    const trigger = await openMobileNavigation(wrapper);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
    expect(wrapper.get('[data-testid="erp-layout-grid"]').attributes('inert')).toBeUndefined();
    expect(wrapper.get('[data-testid="erp-layout-grid"]').attributes('aria-hidden')).toBeUndefined();
  });

  it('closes the mobile navigation after a route change and restores focus', async () => {
    ({ router, wrapper } = await mountLayout());
    const trigger = await openMobileNavigation(wrapper);

    await router.push('/products');
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it('closes from an allowed navigation item and restores focus', async () => {
    ({ router, wrapper } = await mountLayout());
    const trigger = await openMobileNavigation(wrapper);

    await wrapper.get('[data-testid="erp-mobile-drawer"] a[href="/products"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="erp-mobile-navigation"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it('removes the same global keydown listener that it registers', async () => {
    const addEventListener = vi.spyOn(window, 'addEventListener');
    const removeEventListener = vi.spyOn(window, 'removeEventListener');
    ({ router, wrapper } = await mountLayout());

    const keydownRegistration = addEventListener.mock.calls.find(([eventName]) => eventName === 'keydown');
    expect(keydownRegistration).toBeDefined();

    wrapper.unmount();
    wrapper = null;

    expect(removeEventListener).toHaveBeenCalledWith('keydown', keydownRegistration?.[1]);
  });
});
