import type { ApiResponse, LoginResult, PasswordLoginPayload, SmsLoginPayload } from '../types/auth';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    }
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || payload.code !== 'SUCCESS') {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

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
