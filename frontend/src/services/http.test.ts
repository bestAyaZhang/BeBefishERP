import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';
import { request, setUnauthorizedHandler } from './http';

describe('HTTP service', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    setUnauthorizedHandler(() => undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('attaches the current access token and returns ApiResponse data', async () => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'access-token');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SUCCESS', message: '操作成功', data: { id: 1 } }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );

    await expect(request<{ id: number }>('/api/products')).resolves.toEqual({ id: 1 });
    expect(fetch).toHaveBeenCalledWith(
      '/api/products',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-token',
          'Content-Type': 'application/json'
        })
      })
    );
  });

  it('clears the access token and redirects to login for a 401 response', async () => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'expired-token');
    const redirectToLogin = vi.fn();
    setUnauthorizedHandler(redirectToLogin);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: '登录已过期', data: null }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );

    await expect(request('/api/products')).rejects.toThrow('登录已过期');

    expect(localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull();
    expect(redirectToLogin).toHaveBeenCalledOnce();
  });

  it('keeps the backend error code and status so only a missing endpoint can use mock data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 'NOT_FOUND', message: '接口不存在', data: null }),
      { status: 404, headers: { 'Content-Type': 'application/json' } }
    )));

    await expect(request('/api/not-implemented')).rejects.toMatchObject({
      code: 'NOT_FOUND', status: 404, message: '接口不存在'
    });
  });

  it('marks network failures as unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(request('/api/products')).rejects.toMatchObject({ code: 'API_UNAVAILABLE', status: 0 });
  });
});
