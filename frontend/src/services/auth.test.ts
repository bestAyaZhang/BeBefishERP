import { beforeEach, describe, expect, it, vi } from 'vitest';
import { exchangeFeishuTicket, getFeishuStatus, loginWithPassword } from './auth';

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

  it('loads feishu availability and exchanges a one-time ticket', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'SUCCESS', message: '操作成功', data: { available: true, errorCode: null, message: '飞书登录可用' }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'SUCCESS', message: '操作成功', data: {
          accessToken: 'token-feishu', employeeId: 7, mobile: null, displayName: '飞书员工', avatarUrl: null,
          roles: ['BASIC_EMPLOYEE'], permissions: ['dashboard:view'], loginMethod: 'feishu', warnings: []
        }
      }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getFeishuStatus()).resolves.toMatchObject({ available: true });
    await expect(exchangeFeishuTicket('ticket-a')).resolves.toMatchObject({ accessToken: 'token-feishu' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/feishu/exchange');
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', body: '{"ticket":"ticket-a"}' });
  });
});
