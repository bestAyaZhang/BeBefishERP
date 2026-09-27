import vue from '@vitejs/plugin-vue';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const runtimeEnvironment = env.VITE_RUNTIME_ENV || (mode === 'production' ? 'prod' : 'local');
  const dataSource = env.VITE_DATA_SOURCE || (mode === 'production' ? 'real' : 'auto');
  if (!['local', 'test', 'prod'].includes(runtimeEnvironment)) {
    throw new Error('VITE_RUNTIME_ENV 只能设置为 local、test 或 prod');
  }
  if (!['real', 'mock', 'auto'].includes(dataSource)) {
    throw new Error('VITE_DATA_SOURCE 只能设置为 real、mock 或 auto');
  }
  if (mode === 'production' && runtimeEnvironment === 'local') {
    throw new Error('生产构建必须指定 test 或 prod 运行环境');
  }
  if (dataSource !== 'real' && (mode === 'production' || runtimeEnvironment !== 'local')) {
    throw new Error('生产构建及测试/线上环境不允许使用 Mock 数据源或自动降级');
  }

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': 'http://localhost:8080',
        '/uploads': 'http://localhost:8080'
      }
    },
    test: {
      environment: 'jsdom',
      globals: true
    }
  };
});
