import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { exchangeFeishuTicket } from '../services/auth';
import FeishuLoginResultView from './FeishuLoginResultView.vue';

vi.mock('../services/auth', () => ({ exchangeFeishuTicket: vi.fn() }));

function testRouter(query = '?ticket=ticket-a') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/feishu/result', name: 'result', component: FeishuLoginResultView },
      { path: '/workbench', name: 'workbench', component: { template: '<div>workbench</div>' } },
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } }
    ]
  });
  return router.push('/auth/feishu/result' + query).then(() => router);
}

describe('FeishuLoginResultView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('exchanges ticket, stores session, and shows degraded warning', async () => {
    vi.mocked(exchangeFeishuTicket).mockResolvedValue({
      accessToken: 'token-a', employeeId: 8, mobile: null, displayName: '飞书员工', avatarUrl: null,
      roles: ['BASIC_EMPLOYEE'], permissions: ['dashboard:view'], loginMethod: 'feishu',
      warnings: ['FEISHU_ROLE_SYNC_DEGRADED']
    });
    const router = await testRouter();
    const wrapper = mount(FeishuLoginResultView, { global: { plugins: [router] } });

    await flushPromises();

    expect(exchangeFeishuTicket).toHaveBeenCalledWith('ticket-a');
    expect(localStorage.getItem('bebefish_access_token')).toBe('token-a');
    expect(wrapper.text()).toContain('业务角色同步暂时不可用');
    expect(wrapper.text()).toContain('登录成功');
  });

  it('shows an actionable error when ticket is missing', async () => {
    const router = await testRouter('');
    const wrapper = mount(FeishuLoginResultView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('登录结果不存在或已过期');
    expect(wrapper.text()).toContain('重新扫码');
    expect(wrapper.text()).toContain('返回临时员工登录');
  });
});
