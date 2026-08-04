import { beforeEach, describe, expect, it } from 'vitest';
import router from './index';

describe('ERP router', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('redirects unauthenticated users to login', async () => {
    await router.push('/products');
    await router.isReady();

    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.redirect).toBe('/products');
  });
});
