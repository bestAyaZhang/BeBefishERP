import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';
import { clearCurrentUser } from './authSession';
import type { ApiResponse } from '../types/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const SERVICE_UNAVAILABLE_MESSAGE = '接口服务暂不可用，请确认后端已启动';
const INVALID_RESPONSE_MESSAGE = '接口返回格式错误，请联系管理员';

let unauthorizedHandler: () => void | Promise<void> = () => {
  if (typeof window !== 'undefined') {
    window.location.assign('/login');
  }
};

export function setUnauthorizedHandler(handler: () => void | Promise<void>) {
  unauthorizedHandler = handler;
}

function buildHeaders(init?: RequestInit) {
  const headers = Object.fromEntries(new Headers(init?.headers).entries());
  const hasHeader = (headerName: string) => Object.keys(headers).some((key) => key.toLowerCase() === headerName.toLowerCase());
  const accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);

  if (accessToken && !hasHeader('authorization')) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (!(init?.body instanceof FormData) && !hasHeader('content-type')) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    if (!response.ok) {
      throw new Error(SERVICE_UNAVAILABLE_MESSAGE);
    }
    return { code: 'SUCCESS', message: '操作成功', data: undefined as T };
  }

  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new Error(response.ok ? INVALID_RESPONSE_MESSAGE : SERVICE_UNAVAILABLE_MESSAGE);
  }
}

function handleUnauthorized() {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  clearCurrentUser();
  void unauthorizedHandler();
}

async function unwrapResponse<T>(response: Response): Promise<T> {
  const payload = await parseApiResponse<T>(response);
  if (response.status === 401) {
    handleUnauthorized();
  }
  if (!response.ok || payload.code !== 'SUCCESS') {
    throw Object.assign(new Error(payload.message || '请求失败'), {
      status: response.status,
      code: payload.code
    });
  }
  return payload.data;
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: buildHeaders(init)
    });
  } catch {
    throw Object.assign(new Error(SERVICE_UNAVAILABLE_MESSAGE), { status: 0, code: 'API_UNAVAILABLE' });
  }

  return unwrapResponse<T>(response);
}

export function uploadRequest<T>(
  path: string,
  body: FormData,
  onProgress?: (progress: number) => void
): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE_URL}${path}`);
    for (const [name, value] of Object.entries(buildHeaders({ method: 'POST', body }))) {
      xhr.setRequestHeader(name, value);
    }
    xhr.upload.addEventListener('progress', (event) => {
      if (!event.lengthComputable || event.total <= 0) return;
      onProgress?.((event.loaded / event.total) * 100);
    });
    xhr.addEventListener('load', () => {
      const contentType = xhr.getResponseHeader('Content-Type');
      const response = new Response(xhr.responseText, {
        status: xhr.status,
        statusText: xhr.statusText,
        headers: contentType ? { 'Content-Type': contentType } : undefined
      });
      void unwrapResponse<T>(response).then(resolve, reject);
    });
    const rejectUnavailable = () => reject(new Error(SERVICE_UNAVAILABLE_MESSAGE));
    xhr.addEventListener('error', rejectUnavailable);
    xhr.addEventListener('abort', rejectUnavailable);
    xhr.addEventListener('timeout', rejectUnavailable);
    xhr.send(body);
  });
}
