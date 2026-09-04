import type { FeishuLoginStatus, LoginResult, PasswordLoginPayload } from '../types/auth';
import { request } from './http';

export function loginWithPassword(payload: PasswordLoginPayload): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/login/password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getFeishuStatus(): Promise<FeishuLoginStatus> {
  return request<FeishuLoginStatus>('/api/auth/feishu/status');
}

export function beginFeishuLogin(): void {
  window.location.assign('/api/auth/feishu/authorize');
}

export function exchangeFeishuTicket(ticket: string): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/feishu/exchange', {
    method: 'POST',
    body: JSON.stringify({ ticket })
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
