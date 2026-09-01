import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../../router';
import ProductDetailDrawer from './components/ProductDetailDrawer.vue';
import ProductListView from './views/ProductListView.vue';
import ProductDetailView from './views/ProductDetailView.vue';
import type { Product, ProductService } from './types';
import { productService } from './productService';
import { productFixture, productPage } from './productTestFixtures';
import { masterdataService } from '../masterdata/masterdataService';
import type { Category } from '../masterdata/types';

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

vi.mock('../masterdata/masterdataService', () => ({
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
    vi.clearAllMocks();
    localStorage.setItem('bebefish_access_token', 'test-token');
    vi.mocked(productService.listProducts).mockResolvedValue(productPage([product]));
    vi.mocked(productService.getCategoryCounts).mockResolvedValue({});
    vi.mocked(masterdataService.listCategories).mockResolvedValue({ records: [], page: 1, pageSize: 100, total: 0 });
    await router.push('/products');
    await router.isReady();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('opens a standalone product detail route instead of a right-side drawer', async () => {
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push');

    await wrapper.get('[data-testid="product-row-42"]').trigger('click');
    await flushPromises();
    await push.mock.results.at(-1)?.value;

    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith({ name: 'product-detail', params: { id: 42 } });
    expect(router.currentRoute.value.name).toBe('product-detail');
    expect(router.currentRoute.value.params.id).toBe('42');
    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
  });

  it('opens product detail exactly once from row Enter with link semantics', async () => {
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push');
    const row = wrapper.get('[data-testid="product-row-42"]');

    expect(row.attributes('role')).toBe('link');
    expect(row.attributes('aria-label')).toContain('高硼硅玻璃杯');
    await row.trigger('keyup.enter');
    await flushPromises();

    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith({ name: 'product-detail', params: { id: 42 } });
  });

  it('opens product detail exactly once when the detail button receives Enter', async () => {
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push');

    await wrapper.get('[data-testid="product-detail-42"]').trigger('keydown.enter');
    await flushPromises();

    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith({ name: 'product-detail', params: { id: 42 } });
  });

  it('opens the standalone product creation route from the list toolbar', async () => {
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push');

    await wrapper.get('[data-testid="add-product"]').trigger('click');
    await flushPromises();
    await push.mock.results.at(-1)?.value;

    expect(push).toHaveBeenCalledWith({ name: 'product-new' });
    expect(router.currentRoute.value.name).toBe('product-new');
  });

  it('keeps the product list usable when category counts fail and retries only the tree area', async () => {
    vi.mocked(productService.getCategoryCounts)
      .mockRejectedValueOnce(new Error('分类数量暂不可用'))
      .mockResolvedValueOnce({ 1: 1 });
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="product-row-42"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="category-tree-error"]').text()).toContain('商品数量加载失败');

    await wrapper.get('[data-testid="category-tree-error"] button').trigger('click');
    await flushPromises();

    expect(productService.getCategoryCounts).toHaveBeenCalledTimes(2);
    expect(vi.mocked(productService.listProducts).mock.calls.filter(([query]) => query.size === 1)).toHaveLength(1);
    expect(wrapper.find('[data-testid="category-tree-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-row-42"]').exists()).toBe(true);
  });

  it('loads every category page without a status filter and resolves disabled category names', async () => {
    const firstPageCategories: Category[] = Array.from({ length: 100 }, (_, index) => ({
      id: index + 1,
      categoryCode: `CAT-${index + 1}`,
      categoryName: `分类 ${index + 1}`,
      parentId: null,
      level: 1,
      sortOrder: index + 1,
      status: 'enabled',
      remark: ''
    }));
    const disabledCategory: Category = {
      id: 101,
      categoryCode: 'LEGACY',
      categoryName: '已停用历史分类',
      parentId: null,
      level: 1,
      sortOrder: 101,
      status: 'disabled',
      remark: ''
    };
    const disabledCategoryProduct: Product = { ...product, categoryId: 101 };
    vi.mocked(masterdataService.listCategories).mockImplementation((query) => Promise.resolve({
      records: query.page === 1 ? firstPageCategories : [disabledCategory],
      page: query.page,
      pageSize: 100,
      total: 101
    }));
    vi.mocked(productService.listProducts).mockImplementation((query) => Promise.resolve(
      productPage(query.size === 1 ? [] : [disabledCategoryProduct], 137, query.page, query.size)
    ));

    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();

    expect(masterdataService.listCategories).toHaveBeenNthCalledWith(1, { page: 1, size: 100 });
    expect(masterdataService.listCategories).toHaveBeenNthCalledWith(2, { page: 2, size: 100 });
    expect(wrapper.get('[data-testid="product-category-42"]').text()).toContain('已停用历史分类');
    expect(wrapper.get('[data-testid="category-node-all"]').text()).toContain('137');
    expect(vi.mocked(productService.listProducts).mock.calls.filter(([query]) => query.size === 1)).toHaveLength(1);
  });

  it('continues to the response-derived last page when duplicate rows satisfy the raw total early', async () => {
    const category = (id: number): Category => ({
      id,
      categoryCode: `CAT-${id}`,
      categoryName: `分类 ${id}`,
      parentId: null,
      level: 1,
      sortOrder: id,
      status: 'enabled',
      remark: ''
    });
    vi.mocked(masterdataService.listCategories).mockImplementation(({ page }) => Promise.resolve({
      records: page === 1 ? [category(1), category(2)] : page === 2 ? [category(1)] : [category(3)],
      page,
      pageSize: 1,
      total: 3
    }));
    vi.mocked(productService.listProducts).mockImplementation((query) => Promise.resolve(
      productPage(query.size === 1 ? [] : [{ ...product, categoryId: 3 }], 1, query.page, query.size)
    ));

    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();

    expect(masterdataService.listCategories).toHaveBeenCalledTimes(3);
    expect(masterdataService.listCategories).toHaveBeenNthCalledWith(3, { page: 3, size: 100 });
    expect(wrapper.get('[data-testid="product-category-42"]').text()).toContain('分类 3');
    expect(wrapper.find('[data-testid="category-tree-error"]').exists()).toBe(false);
  });

  it('rejects an incomplete unique category lookup after all declared pages while keeping products usable', async () => {
    const repeatedCategory: Category = {
      id: 1,
      categoryCode: 'REPEATED',
      categoryName: '重复分类',
      parentId: null,
      level: 1,
      sortOrder: 1,
      status: 'enabled',
      remark: ''
    };
    vi.mocked(masterdataService.listCategories).mockImplementation(({ page }) => Promise.resolve({
      records: [repeatedCategory],
      page,
      pageSize: 1,
      total: 3
    }));
    vi.mocked(productService.listProducts).mockImplementation((query) => Promise.resolve(
      productPage(query.size === 1 ? [] : [{ ...product, categoryId: 3 }], 1, query.page, query.size)
    ));

    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();

    expect(masterdataService.listCategories).toHaveBeenCalledTimes(3);
    expect(wrapper.find('[data-testid="product-row-42"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="category-tree-error"]').text()).toContain('分类加载失败');
    const categoryCell = wrapper.get('[data-testid="product-category-42"]');
    expect(categoryCell.text()).toContain('--');
    expect(categoryCell.text()).toContain('分类加载失败');
  });

  it('marks category lookup as failed while preserving the product table', async () => {
    vi.mocked(masterdataService.listCategories).mockRejectedValueOnce(new Error('分类接口不可用'));
    const wrapper = mount(ProductListView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="product-row-42"]').exists()).toBe(true);
    const categoryCell = wrapper.get('[data-testid="product-category-42"]');
    expect(categoryCell.text()).toContain('--');
    expect(categoryCell.text()).toContain('分类加载失败');
    expect(categoryCell.text()).not.toContain('未分类');
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
