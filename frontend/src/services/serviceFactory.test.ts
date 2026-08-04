import { describe, expect, it } from 'vitest';
import { createService, selectService } from './serviceFactory';

describe('service factory', () => {
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
});
