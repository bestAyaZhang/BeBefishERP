import type { LoginResult, PasswordLoginPayload, SmsLoginPayload } from '../types/auth';
import { request } from './http';

export function sendSmsCode(mobile: string): Promise<void> {
  return request<void>('/api/auth/sms-code', {
    method: 'POST',
    body: JSON.stringify({ mobile })
  });
}

export function loginWithPassword(payload: PasswordLoginPayload): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/login/password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function loginWithSms(payload: SmsLoginPayload): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/login/sms', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getCurrentUser(accessToken: string): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/me', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });
}

export function logout(accessToken: string): Promise<void> {
  return request<void>('/api/auth/logout', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });
}
