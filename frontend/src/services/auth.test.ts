import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loginWithPassword } from './auth';

describe('auth service', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a clear message when backend returns an empty non-json error response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('', {
          status: 500,
          statusText: 'Internal Server Error',
          headers: {
            'Content-Type': 'text/plain'
          }
        })
      )
    );

    await expect(
      loginWithPassword({
        mobile: '13800138000',
        password: 'Admin@123456'
      })
    ).rejects.toThrow('接口服务暂不可用，请确认后端已启动');
  });
});
