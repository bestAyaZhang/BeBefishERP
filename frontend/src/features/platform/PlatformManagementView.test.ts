import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { saveCurrentUser, clearCurrentUser } from '../../services/authSession';
import PlatformManagementView from './PlatformManagementView.vue';
const service = vi.hoisted(() => ({ listPlatforms: vi.fn(), listShops: vi.fn(), createPlatform: vi.fn(), createShop: vi.fn(), updatePlatform: vi.fn(), updateShop: vi.fn(), changePlatformStatus: vi.fn(), changeShopStatus: vi.fn() }));
vi.mock('./platformService', () => ({ platformService: service }));
const platform = { id: 1, code: 'TAOBAO', name: '淘宝', status: 'enabled', sortOrder: 0, remark: '', version: 0, shopCount: 2, updatedAt: '2026-09-29T09:00:00' };
beforeEach(() => {
  localStorage.clear(); clearCurrentUser(); vi.clearAllMocks();
  saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '运营', roles: [], permissions: ['platform:view', 'platform:create', 'platform:edit'], loginMethod: 'feishu' });
  service.listPlatforms.mockResolvedValue({ records: [platform], page: 1, pageSize: 20, total: 1 });
  service.listShops.mockResolvedValue({ records: [], page: 1, pageSize: 20, total: 0 });
});
async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/platforms', name: 'platforms', component: PlatformManagementView }] });
  await router.push('/platforms'); await router.isReady();
  const wrapper = mount(PlatformManagementView, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises(); return { wrapper, router };
}
describe('Platform management', () => {
  it('opens shop context from a platform and keeps the filter in the URL', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.text()).toContain('淘宝');
    await wrapper.get('[data-testid="platform-shops-1"]').trigger('click'); await flushPromises();
    expect(router.currentRoute.value.query.platformId).toBe('1');
    expect(wrapper.text()).toContain('暂无店铺'); wrapper.unmount();
  });
  it('retains the drawer and input when a duplicate create is rejected', async () => {
    service.createPlatform.mockRejectedValue(new Error('名称已存在'));
    const { wrapper } = await render();
    await wrapper.get('[data-testid="catalog-add"]').trigger('click');
    await wrapper.get('[data-testid="catalog-name"]').setValue('重复平台');
    await wrapper.get('[data-testid="catalog-form"]').trigger('submit'); await flushPromises();
    expect(wrapper.text()).toContain('名称已存在');
    expect((wrapper.get('[data-testid="catalog-name"]').element as HTMLInputElement).value).toBe('重复平台'); wrapper.unmount();
  });
  it('hides mutation controls for a read-only user', async () => {
    saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '查看人', roles: [], permissions: ['platform:view'], loginMethod: 'feishu' });
    const { wrapper } = await render(); expect(wrapper.find('[data-testid="catalog-add"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="catalog-edit-1"]').exists()).toBe(false); wrapper.unmount();
  });
  it('creates a shop with Feishu fields without asking for a code', async () => {
    service.createShop.mockResolvedValue({ id: 2 });
    const { wrapper } = await render();
    await wrapper.get('[data-testid="platform-shops-1"]').trigger('click'); await flushPromises();
    await wrapper.get('[data-testid="catalog-add"]').trigger('click');
    expect(wrapper.find('[data-testid="catalog-code"]').exists()).toBe(false);
    await wrapper.get('[data-testid="catalog-name"]').setValue('笨笨的生活商铺');
    await wrapper.get('[data-testid="catalog-channel-type"]').setValue('ecommerce');
    await wrapper.get('[data-testid="catalog-owner-name"]').setValue('韩晓兵');
    expect(wrapper.get('[data-testid="catalog-option-label"]').text()).toBe('电商_淘宝_笨笨的生活商铺');
    await wrapper.get('[data-testid="catalog-form"]').trigger('submit'); await flushPromises();
    expect(service.createShop).toHaveBeenCalledWith(expect.objectContaining({ name: '笨笨的生活商铺',
      channelType: 'ecommerce', ownerName: '韩晓兵' }));
    expect(service.createShop.mock.calls[0][0].code).toBeUndefined();
    expect(service.createShop.mock.calls[0][0].optionLabel).toBeUndefined();
    wrapper.unmount();
  });
  it('keeps a private exception label visible while editing its shop name', async () => {
    service.listShops.mockResolvedValue({ records: [{ id: 2, platformId: 1, platformName: '淘宝', code: 'S2', name: '代发1',
      channelType: 'private', ownerName: '安正鹏', optionLabel: '私域_代发_方钉', status: 'enabled', sortOrder: 0,
      remark: '', version: 0, updatedAt: '2026-09-29T09:00:00' }], page: 1, pageSize: 20, total: 1 });
    const { wrapper } = await render();
    await wrapper.get('[data-testid="platform-shops-1"]').trigger('click'); await flushPromises();
    await wrapper.get('[data-testid="catalog-edit-2"]').trigger('click');
    await wrapper.get('[data-testid="catalog-name"]').setValue('代发一号');
    expect(wrapper.get('[data-testid="catalog-option-label"]').text()).toBe('私域_代发_方钉');
    wrapper.unmount();
  });
});
