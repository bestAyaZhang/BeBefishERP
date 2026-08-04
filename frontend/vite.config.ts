import vue from '@vitejs/plugin-vue';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  if (mode === 'production' && env.VITE_DATA_SOURCE === 'mock') {
    throw new Error('生产构建不允许使用 Mock 数据源');
  }

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': 'http://localhost:8080'
      }
    },
    test: {
      environment: 'jsdom',
      globals: true
    }
  };
});
