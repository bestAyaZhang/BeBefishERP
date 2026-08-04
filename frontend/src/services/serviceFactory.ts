export type DataSource = 'mock' | 'real';

export function selectService<T>(dataSource: DataSource, mockService: T, realService: T): T {
  return dataSource === 'mock' ? mockService : realService;
}

function validateDataSource(dataSource: DataSource, isProduction: boolean) {
  if (isProduction && dataSource === 'mock') {
    throw new Error('生产构建不允许使用 Mock 数据源');
  }
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
  const dataSource = usesConfiguredSource ? (import.meta.env.VITE_DATA_SOURCE ?? 'real') : dataSourceOrMockFactory;
  const mockFactory = usesConfiguredSource ? dataSourceOrMockFactory : mockFactoryOrRealFactory;
  const realFactory = usesConfiguredSource ? mockFactoryOrRealFactory : maybeRealFactory;

  if (dataSource !== 'mock' && dataSource !== 'real') {
    throw new Error('VITE_DATA_SOURCE 只能设置为 mock 或 real');
  }
  validateDataSource(dataSource, isProduction);
  if (!realFactory) {
    throw new Error('缺少真实服务实现');
  }
  return dataSource === 'mock' ? mockFactory() : realFactory();
}
