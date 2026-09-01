import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../../router';
import ProductListView from './views/ProductListView.vue';
import ProductDetailView from './views/ProductDetailView.vue';
import type { Product, ProductService, ProductSupplierQuote } from './types';
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

enableAutoUnmount(afterEach);

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

const supplierQuote: ProductSupplierQuote = {
  id: 301,
  skuId: 21,
  supplierId: 6,
  supplierName: '义乌玻璃制品厂',
  supplierItemNo: 'YW-021',
  purchasePrice: 8.6,
  minPurchaseQuantity: 120,
  defaultQuote: true,
  status: 'enabled'
};

const detailProduct = Object.assign(productFixture({
  ...product,
  mainImageFileId: 100,
  mainImageUrl: '/uploads/product-main.png',
  completenessPercent: 80,
  completenessStatus: 'incomplete',
  missingGroups: ['采购信息'],
  createdAt: '2026-08-31T08:30:00.000Z',
  updatedAt: '2026-09-01T10:45:00.000Z',
  remark: '季度主推款',
  specifications: [{ name: '颜色', values: ['透明', '烟灰'] }],
  skus: [
    {
      id: 21,
      skuCode: 'BBF-PUMP-021-WH',
      barcode: '6970000000210',
      skuName: '透明款',
      specText: '颜色：透明',
      specificationValues: ['透明'],
      salesUnit: '个',
      defaultSalePrice: 19.9,
      standardCost: 8.6,
      safetyStockQuantity: 20,
      stockQuantity: 136,
      packageLengthCm: 42,
      packageWidthCm: 28,
      packageHeightCm: 24,
      packageVolumeCm3: 28224,
      innerPackageLengthCm: 10,
      innerPackageWidthCm: 10,
      innerPackageHeightCm: 12,
      netWeightKg: 8.4,
      grossWeightKg: 9.1,
      gramWeightG: 210,
      innerPackageWeightKg: 0.23,
      packagingMethod: '彩盒',
      cartonQuantity: 40,
      skuImageFileId: 201,
      skuImageUrl: '/uploads/sku-21.png',
      packageImageFileId: 202,
      packageImageUrl: '/uploads/package-21.png',
      cartonImageFileId: 203,
      cartonImageUrl: '/uploads/carton-21.png',
      supplierQuotes: [supplierQuote],
      defaultSku: true,
      status: 'enabled'
    },
    {
      id: 22,
      skuCode: 'BBF-PUMP-022-GY',
      barcode: null,
      skuName: '烟灰款',
      specText: '颜色：烟灰',
      specificationValues: ['烟灰'],
      salesUnit: '个',
      defaultSalePrice: 21,
      standardCost: 9,
      safetyStockQuantity: 12,
      stockQuantity: 64,
      packageLengthCm: 42,
      packageWidthCm: 28,
      packageHeightCm: 24,
      packageVolumeCm3: 28224,
      innerPackageLengthCm: 10,
      innerPackageWidthCm: 10,
      innerPackageHeightCm: 12,
      netWeightKg: 8.4,
      grossWeightKg: 9.1,
      gramWeightG: 210,
      innerPackageWeightKg: 0.23,
      packagingMethod: '彩盒',
      cartonQuantity: 40,
      skuImageFileId: null,
      skuImageUrl: null,
      packageImageFileId: 202,
      packageImageUrl: '/temporary/package-22.png',
      cartonImageFileId: 203,
      cartonImageUrl: '/temporary/carton-22.png',
      supplierQuotes: [],
      defaultSku: false,
      status: 'disabled'
    }
  ],
  status: 'enabled'
}), { categoryName: '杯具 / 玻璃杯' }) as Product;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe('product detail navigation', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    vi.mocked(productService.getProduct).mockReset();
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

  it('renders a standalone read-only detail page with all six sections', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-overview-section"]').text()).toContain('PRD-000042');
    expect(wrapper.get('[data-testid="product-sku-section"]').text()).toContain('SKU 货号');
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('义乌玻璃制品厂');
    expect(wrapper.get('[data-testid="product-packaging-section"]').text()).toContain('统一包装');
    expect(wrapper.find('[data-testid="product-images-section"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-audit-section"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="sku-image-21"]').attributes('src')).toContain('sku-21.png');
    expect(wrapper.get('[data-testid="sku-item-number-21"]').text()).toBe('BBF-PUMP-021-WH');
    expect(wrapper.find('input, select, textarea').exists()).toBe(false);

    for (const excludedLabel of ['附件', '合规资料', '商品描述', '销售属性', '渠道售价']) {
      expect(wrapper.text()).not.toContain(excludedLabel);
    }
  });

  it('uses -- for absent values and invalid audit dates without inventing data', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      brand: null,
      createdAt: 'not-a-date',
      updatedAt: ''
    });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-brand"]').text()).toBe('--');
    expect(wrapper.get('[data-testid="product-created-at"]').text()).toBe('--');
    expect(wrapper.get('[data-testid="product-updated-at"]').text()).toBe('--');
    expect(wrapper.get('[data-testid="procurement-empty-22"]').text()).toContain('暂无供应商报价');
  });

  it('replaces a failed image with a fixed ImageOff fallback', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.get('[data-testid="sku-image-21"]').trigger('error');

    expect(wrapper.find('[data-testid="sku-image-21"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="sku-image-21-fallback"]').exists()).toBe(true);
  });

  it('preserves the original route id when navigating to edit and returns to products', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(detailProduct);
    await router.push('/products/0042');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined);

    await wrapper.get('[data-testid="edit-product"]').trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'product-edit', params: { id: '0042' } });

    await wrapper.get('[data-testid="product-detail-back"]').trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'products' });
  });

  it('shows an initial skeleton and validates positive integer route ids', async () => {
    const pendingProduct = deferred<Product>();
    vi.mocked(productService.getProduct).mockReturnValueOnce(pendingProduct.promise);
    await router.push('/products/42');
    const loadingWrapper = mount(ProductDetailView, { global: { plugins: [router] } });

    expect(loadingWrapper.find('[data-testid="product-detail-skeleton"]').exists()).toBe(true);
    loadingWrapper.unmount();
    pendingProduct.resolve(detailProduct);
    await flushPromises();

    vi.mocked(productService.getProduct).mockClear();
    await router.push('/products/0');
    const invalidWrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(productService.getProduct).not.toHaveBeenCalled();
    expect(invalidWrapper.get('[data-testid="product-detail-error"]').text()).toContain('商品编号无效');
  });

  it('retries a failed detail request in place', async () => {
    vi.mocked(productService.getProduct)
      .mockRejectedValueOnce(new Error('详情接口暂不可用'))
      .mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-detail-error"]').text()).toContain('详情接口暂不可用');
    await wrapper.get('[data-testid="retry-product-detail"]').trigger('click');
    await flushPromises();

    expect(productService.getProduct).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="product-detail-view"]').text()).toContain('高硼硅玻璃杯');
  });

  it('keeps the newest product when route requests resolve out of order', async () => {
    const first = deferred<Product>();
    const second = deferred<Product>();
    const newerProduct = { ...detailProduct, id: 43, productName: '新路由商品', itemNo: 'NEW-043' };
    vi.mocked(productService.getProduct).mockImplementation((id) => id === 42 ? first.promise : second.promise);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await router.push('/products/43');

    second.resolve(newerProduct);
    await flushPromises();
    first.resolve(detailProduct);
    await flushPromises();

    expect(productService.getProduct).toHaveBeenNthCalledWith(1, 42);
    expect(productService.getProduct).toHaveBeenNthCalledWith(2, 43);
    expect(wrapper.text()).toContain('新路由商品');
    expect(wrapper.text()).not.toContain('高硼硅玻璃杯');
  });
});
