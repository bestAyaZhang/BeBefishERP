import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginView from './LoginView.vue';
import { loginWithPassword, loginWithSms, sendSmsCode } from '../services/auth';

vi.mock('../services/auth', () => ({
  loginWithPassword: vi.fn(),
  loginWithSms: vi.fn(),
  sendSmsCode: vi.fn(),
  logout: vi.fn()
}));

describe('LoginView', () => {
  let activeWrapper: ReturnType<typeof mount> | null = null;

  function mountLoginView() {
    activeWrapper = mount(LoginView);
    return activeWrapper;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
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

    expect(loginWithPassword).toHaveBeenCalledWith({
      mobile: '13800138000',
      password: 'Admin@123456'
    });
    expect(localStorage.getItem('bebefish_access_token')).toBe('token-1');
    expect(wrapper.text()).toContain('13800138000');
  });

  it('sends sms code and submits sms login', async () => {
    vi.mocked(sendSmsCode).mockResolvedValue();
    vi.mocked(loginWithSms).mockResolvedValue({
      accessToken: 'token-2',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view'],
      loginMethod: 'sms'
    });
    const wrapper = mountLoginView();

    await wrapper.get('[data-testid="mobile-input"]').setValue('13800138000');
    await wrapper.findAll('button').find((button) => button.text().includes('验证码'))!.trigger('click');
    await wrapper.get('[data-testid="send-code-button"]').trigger('click');
    await wrapper.get('[data-testid="sms-code-input"]').setValue('123456');
    await wrapper.get('form').trigger('submit.prevent');
    await flushPromises();

    expect(sendSmsCode).toHaveBeenCalledWith('13800138000');
    expect(loginWithSms).toHaveBeenCalledWith({
      mobile: '13800138000',
      smsCode: '123456'
    });
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
    expect(wrapper.get('[data-testid="login-character-stage"]').text()).toContain('密码可见');
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
