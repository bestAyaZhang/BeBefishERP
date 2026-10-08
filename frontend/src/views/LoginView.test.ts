import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../router';
import LoginView from './LoginView.vue';
import { beginFeishuLogin, getFeishuStatus, loginWithPassword } from '../services/auth';

vi.mock('../services/auth', () => ({
  loginWithPassword: vi.fn(),
  getFeishuStatus: vi.fn(),
  beginFeishuLogin: vi.fn(),
  logout: vi.fn()
}));

describe('LoginView', () => {
  let activeWrapper: ReturnType<typeof mount> | null = null;

  function mountLoginView() {
    activeWrapper = mount(LoginView, {
      global: {
        plugins: [router]
      }
    });
    return activeWrapper;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(getFeishuStatus).mockResolvedValue({ available: true, errorCode: null, message: '飞书登录可用' });
  });

  afterEach(() => {
    activeWrapper?.unmount();
    activeWrapper = null;
    vi.useRealTimers();
  });

  it('submits mobile and password login and stores the access token', async () => {
    vi.mocked(loginWithPassword).mockResolvedValue({
      accessToken: 'token-1',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view'],
      loginMethod: 'password'
    });
    const wrapper = mountLoginView();

    await wrapper.get('[data-testid="mobile-input"]').setValue('13800138000');
    await wrapper.get('[data-testid="password-input"]').setValue('Admin@123456');
    await wrapper.get('form').trigger('submit.prevent');
    await flushPromises();
    await router.isReady();

    expect(loginWithPassword).toHaveBeenCalledWith({
      mobile: '13800138000',
      password: 'Admin@123456'
    });
    expect(localStorage.getItem('bebefish_access_token')).toBe('token-1');
    expect(JSON.parse(localStorage.getItem('bebefish_current_user') ?? '{}')).toMatchObject({
      mobile: '13800138000',
      permissions: ['dashboard:view']
    });
    expect(wrapper.text()).toContain('13800138000');
    expect(router.currentRoute.value.name).toBe('workbench');
  });

  it('shows feishu as the primary entry and removes sms controls', async () => {
    const wrapper = mountLoginView();
    await flushPromises();

    expect(wrapper.text()).not.toContain('Admin@123456');
    expect(wrapper.get('[data-testid="feishu-login"]').text()).toContain('飞书扫码登录');
    expect(wrapper.find('[data-testid="sms-code-input"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="send-code-button"]').exists()).toBe(false);
  });

  it('shows the ICP registration as a safe external link outside the login form', () => {
    const wrapper = mountLoginView();
    const footer = wrapper.get('[data-testid="icp-footer"]');
    const link = footer.get('a');

    expect(link.text()).toBe('浙ICP备2026075333号-1');
    expect(link.attributes('href')).toBe('https://beian.miit.gov.cn/');
    expect(link.attributes('target')).toBe('_blank');
    expect(link.attributes('rel')?.split(' ')).toEqual(expect.arrayContaining(['noopener', 'noreferrer']));
    expect(wrapper.get('[data-testid="login-form-panel"]').get('[data-testid="icp-footer"]').element).toBe(footer.element);
    expect(wrapper.get('[data-testid="temporary-login-form"]').find('[data-testid="icp-footer"]').exists()).toBe(false);
    expect(footer.classes()).toContain('print:hidden');
    expect(footer.classes()).not.toContain('fixed');
    expect(footer.classes()).not.toContain('absolute');
  });

  it('prevents duplicate feishu redirects', async () => {
    const wrapper = mountLoginView();
    await flushPromises();

    await wrapper.get('[data-testid="feishu-login"]').trigger('click');
    await wrapper.get('[data-testid="feishu-login"]').trigger('click');

    expect(beginFeishuLogin).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="feishu-login"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="feishu-login"]').text()).toContain('正在前往飞书');
  });

  it('renders the animated characters login template structure', () => {
    const wrapper = mountLoginView();

    expect(wrapper.find('[data-testid="template-login-shell"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="animated-characters-panel"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="login-form-panel"]').text()).toContain('欢迎回来');
    expect(wrapper.get('[data-testid="animated-character-purple"]').classes()).toContain('login-character-float');
    expect(wrapper.get('[data-testid="animated-character-purple"]').classes()).toContain('login-character-blink');
    expect(wrapper.find('[data-testid="animated-character-black"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="animated-character-orange"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="animated-character-yellow"]').exists()).toBe(true);
  });

  it('uses a black and white background palette for the login page', () => {
    const wrapper = mountLoginView();

    expect(wrapper.get('[data-testid="template-login-shell"]').attributes('data-color-scheme')).toBe('black-white');
    expect(wrapper.get('[data-testid="animated-characters-panel"]').classes()).toContain('bg-black');
    expect(wrapper.get('[data-testid="login-form-panel"]').classes()).toContain('bg-white');
  });

  it('keeps the illustration panel free of auxiliary overlay text', () => {
    const wrapper = mountLoginView();
    const stage = wrapper.get('[data-testid="login-character-stage"]');
    const panel = wrapper.get('[data-testid="animated-characters-panel"]');

    expect(stage.text()).not.toContain('等待输入');
    expect(wrapper.find('[data-testid="login-status-indicator"]').exists()).toBe(false);
    expect(panel.text()).not.toContain('等待输入');
    expect(panel.text()).not.toContain('库存');
    expect(panel.text()).not.toContain('线下开单');
    expect(panel.text()).not.toContain('财务看板');
    expect(panel.text()).not.toContain('平台数据');
    expect(wrapper.get('[data-testid="login-stage-ground"]').classes()).toContain('z-0');
    expect(wrapper.get('[data-testid="login-stage-ground"]').classes()).not.toContain('z-50');
  });

  it('keeps temporary employee login as a secondary expandable form', async () => {
    const wrapper = mountLoginView();
    await flushPromises();

    expect(wrapper.get('[data-testid="temporary-login-form"]').attributes('style')).toContain('display: none');
    await wrapper.get('[data-testid="temporary-login-toggle"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="temporary-login-form"]').attributes('style')).not.toContain('display: none');
    expect(wrapper.get('[data-testid="temporary-login-toggle"]').text()).toContain('收起');
  });

  it('uses uppercase English labels for mobile and password fields', () => {
    const wrapper = mountLoginView();

    expect(wrapper.get('[data-testid="login-mobile-label"]').text()).toBe('MOBILE');
    expect(wrapper.get('[data-testid="login-password-label"]').text()).toBe('PASSWORD');
  });

  it('toggles password visibility and updates the character scene state', async () => {
    const wrapper = mountLoginView();
    const passwordInput = wrapper.get('[data-testid="password-input"]');

    expect(passwordInput.attributes('type')).toBe('password');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-visible')).toBe('false');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-state')).toBe('empty-hidden');

    await passwordInput.setValue('Admin@123456');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-state')).toBe('filled-hidden');
    await wrapper.get('[data-testid="password-visibility-toggle"]').trigger('click');

    expect(passwordInput.attributes('type')).toBe('text');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-visible')).toBe('true');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-state')).toBe('filled-visible');
    expect(wrapper.get('[data-testid="animated-characters-panel"]').text()).not.toContain('密码可见');
  });

  it('marks the character scene with the active input field', async () => {
    const wrapper = mountLoginView();
    const stage = wrapper.get('[data-testid="login-character-stage"]');

    expect(stage.attributes('data-active-field')).toBe('none');

    await wrapper.get('[data-testid="mobile-input"]').trigger('focus');
    expect(stage.attributes('data-active-field')).toBe('mobile');

    await wrapper.get('[data-testid="password-input"]').trigger('focus');
    expect(stage.attributes('data-active-field')).toBe('password');

    await wrapper.get('[data-testid="password-input"]').trigger('blur');
    expect(stage.attributes('data-active-field')).toBe('none');
  });

  it('starts character reactions from input events even when focus is missed', async () => {
    vi.useFakeTimers();
    const wrapper = mountLoginView();
    const stage = wrapper.get('[data-testid="login-character-stage"]');

    await wrapper.get('[data-testid="mobile-input"]').setValue('13800138000');

    expect(stage.attributes('data-active-field')).toBe('mobile');
    expect(stage.attributes('data-looking-at-each-other')).toBe('true');

    await vi.advanceTimersByTimeAsync(900);
    expect(stage.attributes('data-looking-at-each-other')).toBe('false');

    await wrapper.get('[data-testid="password-input"]').setValue('Admin@123456');

    expect(stage.attributes('data-active-field')).toBe('password');
    expect(stage.attributes('data-looking-at-each-other')).toBe('true');
  });

  it('moves character eyes in response to pointer movement', async () => {
    const wrapper = mountLoginView();
    const stage = wrapper.get('[data-testid="login-character-stage"]');

    expect(stage.attributes('data-look-x')).toBe('0');
    expect(stage.attributes('data-look-y')).toBe('0');

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 1000, clientY: 120 }));
    await flushPromises();

    expect(stage.attributes('data-look-x')).not.toBe('0');
    expect(stage.attributes('data-look-y')).not.toBe('0');
  });

  it('runs looking and peeking interaction states while typing password', async () => {
    vi.useFakeTimers();
    const wrapper = mountLoginView();
    const stage = wrapper.get('[data-testid="login-character-stage"]');
    const passwordInput = wrapper.get('[data-testid="password-input"]');

    expect(stage.attributes('data-looking-at-each-other')).toBe('false');
    expect(stage.attributes('data-peeking')).toBe('false');

    await passwordInput.trigger('focus');
    await passwordInput.setValue('Admin@123456');

    expect(stage.attributes('data-looking-at-each-other')).toBe('true');

    await vi.advanceTimersByTimeAsync(900);
    expect(stage.attributes('data-looking-at-each-other')).toBe('false');

    await wrapper.get('[data-testid="password-visibility-toggle"]').trigger('click');
    expect(stage.attributes('data-peeking')).toBe('true');
    expect(wrapper.get('[data-testid="animated-character-purple"]').classes()).toContain('login-character-peek');
  });
});
