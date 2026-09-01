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
});
