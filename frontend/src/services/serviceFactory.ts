export type DataSource = 'mock' | 'real' | 'auto';

export function selectService<T>(dataSource: Exclude<DataSource, 'auto'>, mockService: T, realService: T): T {
  return dataSource === 'mock' ? mockService : realService;
}

function validateDataSource(dataSource: DataSource, isProduction: boolean) {
  const runtimeEnvironment = import.meta.env.VITE_RUNTIME_ENV ?? (isProduction ? 'prod' : 'local');
  if (!['local', 'test', 'prod'].includes(runtimeEnvironment)) {
    throw new Error('VITE_RUNTIME_ENV 只能设置为 local、test 或 prod');
  }
  if (dataSource !== 'real' && isProduction) {
    throw new Error('生产构建不允许使用 Mock 数据源或自动降级');
  }
  if (dataSource !== 'real' && runtimeEnvironment !== 'local') {
    throw new Error('测试/线上环境不允许使用 Mock 数据源或自动降级');
  }
}

function isReadOperation(name: string) {
  return /^(get|list|search|find|fetch)/.test(name) || name === 'summary' || name === 'formOptions'
    || name === 'logisticsAvailability';
}

function canUseMock(error: unknown) {
  if (!(error instanceof Error)) return false;
  const failure = error as Error & { status?: number; code?: string };
  return failure.code === 'API_UNAVAILABLE'
    || failure.status === 501
    || (failure.status === 404 && failure.code === 'NOT_FOUND' && failure.message === '接口不存在');
}

function withLocalFallback<T>(mockService: T, realService: T): T {
  if (typeof realService !== 'object' || realService === null
      || typeof mockService !== 'object' || mockService === null) return realService;
  const warnedMethods = new Set<string>();
  return new Proxy(realService, {
    get(target, property, receiver) {
      const realMethod = Reflect.get(target, property, receiver);
      if (typeof property !== 'string' || !isReadOperation(property)) return realMethod;
      const mockMethod = Reflect.get(mockService, property);
      if (typeof mockMethod !== 'function' || (realMethod !== undefined && typeof realMethod !== 'function')) return realMethod;
      return (...args: unknown[]) => Promise.resolve()
        .then(() => typeof realMethod === 'function'
          ? realMethod.apply(target, args)
          : Promise.reject(Object.assign(new Error('接口不存在'), { status: 501, code: 'NOT_IMPLEMENTED' })))
        .catch((error: unknown) => {
          if (!canUseMock(error)) throw error;
          if (!warnedMethods.has(property)) {
            console.warn(`本地接口不可用，${property} 暂时显示 Mock 数据`);
            warnedMethods.add(property);
          }
          if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('erp:mock-fallback', { detail: { method: property } }));
          }
          return mockMethod.apply(mockService, args);
        });
    }
  }) as T;
}

export function createService<T>(mockFactory: () => T, realFactory: () => T): T;
export function createService<T>(dataSource: DataSource, mockFactory: () => T, realFactory: () => T, isProduction?: boolean): T;
export function createService<T>(
  dataSourceOrMockFactory: DataSource | (() => T),
  mockFactoryOrRealFactory: (() => T),
  maybeRealFactory?: () => T,
  isProduction = import.meta.env.PROD
): T {
  const usesConfiguredSource = typeof dataSourceOrMockFactory === 'function';
  const dataSource = usesConfiguredSource ? (import.meta.env.VITE_DATA_SOURCE ?? (import.meta.env.DEV ? 'auto' : 'real')) : dataSourceOrMockFactory;
  const mockFactory = usesConfiguredSource ? dataSourceOrMockFactory : mockFactoryOrRealFactory;
  const realFactory = usesConfiguredSource ? mockFactoryOrRealFactory : maybeRealFactory;

  if (dataSource !== 'mock' && dataSource !== 'real' && dataSource !== 'auto') {
    throw new Error('VITE_DATA_SOURCE 只能设置为 mock、real 或 auto');
  }
  validateDataSource(dataSource, isProduction);
  if (!realFactory) {
    throw new Error('缺少真实服务实现');
  }
  if (dataSource === 'mock') return mockFactory();
  const realService = realFactory();
  return dataSource === 'auto' ? withLocalFallback(mockFactory(), realService) : realService;
}
