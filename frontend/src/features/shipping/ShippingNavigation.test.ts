import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import router from '../../router';
import SidebarNav from '../../components/navigation/SidebarNav.vue';
import ErpLayout from '../../layouts/ErpLayout.vue';
import { getCurrentUser } from '../../services/auth';
import { saveCurrentUser, clearCurrentUser } from '../../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../types/auth';
import type { LoginResult } from '../../types/auth';
vi.mock('../../services/auth', () => ({ getCurrentUser: vi.fn() }));
beforeEach(() => { localStorage.clear(); clearCurrentUser(); vi.mocked(getCurrentUser).mockReset(); });
afterEach(clearCurrentUser);
it('protects the shipping route and exposes its menu only with view permission', async () => {
  localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY,'test');
  saveCurrentUser({accessToken:'test',mobile:null,roles:[],permissions:['dashboard:view'],loginMethod:'feishu'});
  await router.push('/shipping/list');
  expect(router.currentRoute.value.name).toBe('workbench');
  const hidden = mount(SidebarNav,{global:{plugins:[router]}});
  expect(hidden.text()).not.toContain('发货管理');
  hidden.unmount();
  saveCurrentUser({accessToken:'test',mobile:null,roles:[],permissions:['shipping:view'],loginMethod:'feishu'});
  await router.push('/shipping/list');
  expect(router.currentRoute.value.name).toBe('shipping-list');
  const visible = mount(SidebarNav,{global:{plugins:[router]}});
  expect(visible.text()).toContain('发货管理');
  expect(visible.find('a[href="/shipping/list"]').exists()).toBe(true);
  visible.unmount();
  const layout = mount(ErpLayout,{global:{plugins:[router],stubs:{RouterView:true}}});
  await router.push('/shipping/list');
  expect(layout.get('[data-testid="breadcrumb-current"]').text()).toBe('发货列表');
  layout.unmount();
});

it('refreshes cached permissions so a newly granted shipping menu appears without logging in again', async () => {
  localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test');
  saveCurrentUser({ accessToken: 'test', mobile: null, roles: ['SUPER_ADMIN'],
    permissions: ['dashboard:view'], loginMethod: 'feishu' });
  vi.mocked(getCurrentUser).mockResolvedValue({ accessToken: 'test', mobile: null,
    roles: ['SUPER_ADMIN'], permissions: ['dashboard:view', 'shipping:view'], loginMethod: 'feishu' });
  await router.push('/workbench');
  const layout = mount(ErpLayout, { global: { plugins: [router], stubs: { RouterView: true } } });
  await flushPromises();
  expect(getCurrentUser).toHaveBeenCalledWith('test');
  expect(layout.get('a[href="/shipping/list"]').text()).toBe('发货列表');
  layout.unmount();
});

it('does not restore a shipping menu from a permission refresh that finishes after logout', async () => {
  localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test');
  saveCurrentUser({ accessToken: 'test', mobile: null, roles: [],
    permissions: ['dashboard:view'], loginMethod: 'feishu' });
  let resolveUser!: (user: LoginResult) => void;
  vi.mocked(getCurrentUser).mockReturnValue(new Promise(resolve => { resolveUser = resolve; }));
  await router.push('/workbench');
  const layout = mount(ErpLayout, { global: { plugins: [router], stubs: { RouterView: true } } });
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  clearCurrentUser();
  resolveUser({ accessToken: 'test', mobile: null, roles: [],
    permissions: ['dashboard:view', 'shipping:view'], loginMethod: 'feishu' });
  await flushPromises();
  expect(layout.find('a[href="/shipping/list"]').exists()).toBe(false);
  layout.unmount();
});
