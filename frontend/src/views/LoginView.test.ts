import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginView from './LoginView.vue';
import { loginWithPassword, loginWithSms, sendSmsCode } from '../services/auth';

vi.mock('../services/auth', () => ({
  loginWithPassword: vi.fn(),
  loginWithSms: vi.fn(),
  sendSmsCode: vi.fn(),
  logout: vi.fn()
}));

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('submits mobile and password login and stores the access token', async () => {
    vi.mocked(loginWithPassword).mockResolvedValue({
      accessToken: 'token-1',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view'],
      loginMethod: 'password'
    });
    const wrapper = mount(LoginView);

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
    const wrapper = mount(LoginView);

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

  it('renders the illustrated ERP login scene with operational signals', () => {
    const wrapper = mount(LoginView);

    expect(wrapper.get('[data-testid="login-character-stage"]').text()).toContain('库存');
    expect(wrapper.text()).toContain('平台汇总');
    expect(wrapper.text()).toContain('财务');
  });

  it('toggles password visibility and updates the character scene state', async () => {
    const wrapper = mount(LoginView);
    const passwordInput = wrapper.get('[data-testid="password-input"]');

    expect(passwordInput.attributes('type')).toBe('password');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-visible')).toBe('false');

    await wrapper.get('[data-testid="password-visibility-toggle"]').trigger('click');

    expect(passwordInput.attributes('type')).toBe('text');
    expect(wrapper.get('[data-testid="login-character-stage"]').attributes('data-password-visible')).toBe('true');
  });
});
