import type { ApiResponse, LoginResult, PasswordLoginPayload, SmsLoginPayload } from '../types/auth';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const SERVICE_UNAVAILABLE_MESSAGE = '接口服务暂不可用，请确认后端已启动';
const INVALID_RESPONSE_MESSAGE = '接口返回格式错误，请联系管理员';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {})
      }
    });
  } catch {
    throw new Error(SERVICE_UNAVAILABLE_MESSAGE);
  }

  const payload = await parseApiResponse<T>(response);
  if (!response.ok || payload.code !== 'SUCCESS') {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    if (!response.ok) {
      throw new Error(SERVICE_UNAVAILABLE_MESSAGE);
    }
    return {
      code: 'SUCCESS',
      message: '操作成功',
      data: undefined as T
    };
  }

  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new Error(response.ok ? INVALID_RESPONSE_MESSAGE : SERVICE_UNAVAILABLE_MESSAGE);
  }
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
