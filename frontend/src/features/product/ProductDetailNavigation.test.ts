import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../../router';
import ProductDetailDrawer from './components/ProductDetailDrawer.vue';
import ProductListView from './views/ProductListView.vue';
import ProductDetailView from './views/ProductDetailView.vue';
import type { Product, ProductService } from './types';
import { productService } from './productService';
import { productFixture, productPage } from './productTestFixtures';

vi.mock('./productService', () => ({
  productService: {
    listProducts: vi.fn(),
    getCategoryCounts: vi.fn(),
    getProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    changeProductStatus: vi.fn(),
    listSupplierQuotes: vi.fn(),
    saveSupplierQuote: vi.fn(),
    setDefaultSupplierQuote: vi.fn(),
    uploadImage: vi.fn()
  } satisfies ProductService
}));

vi.mock('../../masterdata/masterdataService', () => ({
  masterdataService: {
    listCategories: vi.fn().mockResolvedValue({ records: [], page: 1, pageSize: 100, total: 0 })
  }
}));

const product = productFixture({
  id: 42,
  productCode: 'PRD-000042',
  itemNo: 'GLASS-042',
  productName: '高硼硅玻璃杯',
  categoryId: 1,
  brand: '共典',
  productType: 'simple',
  mainImageFileId: null,
  remark: '',
  specifications: [{ name: '容量', values: ['210ml'] }],
  skus: [],
  status: 'enabled'
});

describe('product detail navigation', () => {
  beforeEach(async () => {
    localStorage.setItem('bebefish_access_token', 'test-token');
    vi.mocked(productService.listProducts).mockResolvedValue(productPage([product]));
    await router.push('/products');
    await router.isReady();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('opens a standalone product detail route instead of a right-side drawer', async () => {
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push');

    await wrapper.get('[data-testid="product-row-42"]').trigger('click');
    await flushPromises();
    await push.mock.results.at(-1)?.value;

    expect(push).toHaveBeenCalledWith({ name: 'product-detail', params: { id: 42 } });
    expect(router.currentRoute.value.name).toBe('product-detail');
    expect(router.currentRoute.value.params.id).toBe('42');
    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
  });

  it('renders product detail as a page without drawer positioning or overlay', () => {
    const wrapper = mount(ProductDetailDrawer, { props: { product, presentation: 'page' }, global: { plugins: [router] } });

    expect(wrapper.find('[data-testid="product-detail-page"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="关闭商品详情抽屉"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('商品详情');
  });

  it('opens and closes a large image preview from the detail page', async () => {
    const productWithImage: Product = {
      ...product,
      mainImageFileId: 100,
      mainImageUrl: '/uploads/detail-main.png'
    };
    const wrapper = mount(ProductDetailDrawer, { props: { product: productWithImage, presentation: 'page' }, global: { plugins: [router] } });

    await wrapper.get('[data-testid="product-detail-main-image"]').trigger('click');

    const lightbox = document.body.querySelector('[data-testid="product-image-lightbox"]');
    expect(lightbox).not.toBeNull();
    expect(lightbox?.querySelector('img')?.getAttribute('src')).toBe('/uploads/detail-main.png');

    const closeButton = document.body.querySelector('[data-testid="product-image-lightbox-close"]');
    expect(closeButton).not.toBeNull();
    (closeButton as HTMLButtonElement).click();
    await wrapper.vm.$nextTick();
    expect(document.body.querySelector('[data-testid="product-image-lightbox"]')).toBeNull();
  });

  it('falls back to the product list data when the detail request fails', async () => {
    vi.mocked(productService.getProduct).mockRejectedValueOnce(new Error('详情接口暂不可用'));
    await router.push('/products/42');
    await router.isReady();

    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(productService.getProduct).toHaveBeenCalledWith(42);
    expect(wrapper.text()).toContain('高硼硅玻璃杯');
    expect(productService.listProducts).toHaveBeenCalledWith({ page: 1, size: 100 });
  });
});
