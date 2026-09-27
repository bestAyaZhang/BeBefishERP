/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_DATA_SOURCE?: 'mock' | 'real' | 'auto';
  readonly VITE_RUNTIME_ENV?: 'local' | 'test' | 'prod';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
