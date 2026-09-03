import { beforeEach, describe, expect, it } from 'vitest';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';
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

  it('resolves static create and dynamic product routes in the required order', () => {
    expect(router.resolve('/products/new').name).toBe('product-new');
    expect(router.resolve('/products/12/edit').name).toBe('product-edit');
    expect(router.resolve('/products/12').name).toBe('product-detail');
    expect(router.resolve('/products/0012').params.id).toBe('0012');
  });

  it('keeps workbench inside the shared ERP header', () => {
    const resolved = router.resolve('/workbench');

    expect(resolved.name).toBe('workbench');
    expect(resolved.meta).not.toHaveProperty('ownsPrototypeHeader');
  });

  it('registers organization management routes under the authenticated ERP layout', () => {
    expect(router.resolve({ name: 'organization-employees' }).path).toBe('/organization/employees');
    expect(router.resolve({ name: 'organization-departments' }).path).toBe('/organization/departments');
    expect(router.resolve({ name: 'organization-positions' }).path).toBe('/organization/positions');

    for (const path of ['/organization/employees', '/organization/departments', '/organization/positions']) {
      expect(router.resolve(path).meta.requiresAuth).toBe(true);
    }
  });

  it('keeps the existing token-only direct-route authorization behavior', async () => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test-token');

    await router.push('/organization/employees');

    expect(router.currentRoute.value.name).toBe('organization-employees');
  });
});
