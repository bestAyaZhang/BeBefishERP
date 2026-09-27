import { afterEach, describe, expect, it, vi } from 'vitest';
import { createService, selectService } from './serviceFactory';

describe('service factory', () => {
  afterEach(() => vi.unstubAllEnvs());
  it('uses mock service only when VITE_DATA_SOURCE is mock', () => {
    const mockService = { source: 'mock' };
    const realService = { source: 'real' };

    expect(selectService('mock', mockService, realService)).toBe(mockService);
    expect(selectService('real', mockService, realService)).toBe(realService);
  });

  it('creates only the selected service implementation', () => {
    const service = createService(
      'real',
      () => ({ source: 'mock' }),
      () => ({ source: 'real' })
    );

    expect(service).toEqual({ source: 'real' });
  });

  it('rejects mock data source for production builds', () => {
    expect(() => createService('mock', () => ({ source: 'mock' }), () => ({ source: 'real' }), true)).toThrow(
      '生产构建不允许使用 Mock 数据源'
    );
  });

  it('uses real data first in auto mode and keeps a successful empty result', async () => {
    const mockList = vi.fn().mockResolvedValue(['demo']);
    const service = createService<{ list(): Promise<string[]> }>('auto', () => ({ list: mockList }), () => ({ list: async () => [] }), false);

    await expect(service.list()).resolves.toEqual([]);
    expect(mockList).not.toHaveBeenCalled();
  });

  it('falls back only for an unavailable read endpoint in auto mode', async () => {
    const missingEndpoint = Object.assign(new Error('接口不存在'), { status: 404, code: 'NOT_FOUND' });
    const service = createService('auto', () => ({ list: async () => ['demo'] }), () => ({ list: async (): Promise<string[]> => { throw missingEndpoint; } }), false);
    const fallbackNotice = vi.fn();
    window.addEventListener('erp:mock-fallback', fallbackNotice);

    await expect(service.list()).resolves.toEqual(['demo']);
    expect(fallbackNotice).toHaveBeenCalledOnce();
    window.removeEventListener('erp:mock-fallback', fallbackNotice);
  });

  it('does not hide unauthorized, missing record, or server errors with mock data', async () => {
    for (const error of [
      Object.assign(new Error('未登录'), { status: 401, code: 'UNAUTHORIZED' }),
      Object.assign(new Error('发货单不存在'), { status: 404, code: 'SHIPMENT_NOT_FOUND' }),
      Object.assign(new Error('服务故障'), { status: 500, code: 'INTERNAL_ERROR' })
    ]) {
      const service = createService('auto', () => ({ get: async () => 'demo' }), () => ({ get: async (): Promise<string> => { throw error; } }), false);
      await expect(service.get()).rejects.toBe(error);
    }
  });

  it('never falls back for a write operation', async () => {
    const unavailable = Object.assign(new Error('接口不存在'), { status: 404, code: 'NOT_FOUND' });
    const mockCreate = vi.fn().mockResolvedValue('demo');
    const service = createService<{ create(): Promise<string> }>('auto', () => ({ create: mockCreate }), () => ({ create: async (): Promise<string> => { throw unavailable; } }), false);

    await expect(service.create()).rejects.toBe(unavailable);
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it('rejects auto mode for production builds', () => {
    expect(() => createService('auto', () => ({}), () => ({}), true)).toThrow('生产构建不允许');
  });

  it('rejects all mock modes in the local test and online runtime environments', () => {
    for (const environment of ['test', 'prod']) {
      vi.stubEnv('VITE_RUNTIME_ENV', environment);
      for (const source of ['mock', 'auto'] as const) {
        expect(() => createService(source, () => ({}), () => ({}), false)).toThrow('测试/线上环境不允许');
      }
      expect(createService('real', () => ({}), () => ({ source: 'real' }), false)).toEqual({ source: 'real' });
    }
  });
});
