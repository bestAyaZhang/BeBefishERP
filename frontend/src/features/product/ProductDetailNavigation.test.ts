import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import router from '../../router';
import { message } from '../../components/feedback/message';
import ProductListView from './views/ProductListView.vue';
import ProductDetailView from './views/ProductDetailView.vue';
import type { Product, ProductCatalogSupplierQuote, ProductImagePack, ProductService } from './types';
import { productService } from './productService';
import { productFixture, productPage } from './productTestFixtures';
import * as productPresentation from './productPresentation';
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

const supplierQuote: ProductCatalogSupplierQuote = {
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

const disabledSupplierQuote: ProductCatalogSupplierQuote = {
  id: 302,
  skuId: 21,
  supplierId: 7,
  supplierName: '宁波包装制品厂',
  supplierItemNo: 'NB-021',
  purchasePrice: 8.9,
  minPurchaseQuantity: 0,
  defaultQuote: false,
  status: 'disabled'
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
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450,
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
      supplierQuotes: [supplierQuote, disabledSupplierQuote],
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
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450,
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

const detailImagePacks: ProductImagePack[] = [
  {
    name: '批发素材',
    main: [{ fileId: 401, name: '批发主图.png', url: '/uploads/wholesale-main.png' }],
    sku: [{ fileId: 402, name: '批发规格.png', url: '/uploads/wholesale-sku.png' }],
    detail: [{ fileId: 403, name: '批发详情.png', url: '/uploads/wholesale-detail.png' }],
    long: [
      { fileId: 404, name: '批发长图一.png', url: '/uploads/wholesale-long-1.png' },
      { fileId: 405, name: '批发长图二.png', url: '/uploads/wholesale-long-2.png' }
    ]
  },
  {
    name: '零售素材',
    main: [{ fileId: 406, name: '零售主图.png', url: '/uploads/retail-main.png' }],
    sku: [], detail: [], long: []
  }
];

async function mountDetailAlbums(imagePacks: Product['imagePacks']) {
  vi.mocked(productService.getProduct).mockResolvedValueOnce({ ...detailProduct, imagePacks });
  await router.push('/products/42');
  const wrapper = mount(ProductDetailView, { global: { plugins: [router], stubs: { teleport: true } } });
  await flushPromises();
  return wrapper;
}

function skuCellSelector(skuId: number, label: string) {
  return `tr:has([data-testid="select-sku-${skuId}"]) > td[data-label="${label}"]`;
}

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
    vi.unstubAllGlobals();
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

  it('renders a standalone read-only detail page with packaging for every SKU', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-overview-section"]').text()).toContain('PRD-000042');
    expect(wrapper.get('[data-testid="product-sku-section"]').text()).toContain('SKU货号');
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('义乌玻璃制品厂');
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('宁波包装制品厂');
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('停用');
    const skuList = wrapper.get('[data-testid="product-sku-section"]');
    for (const sku of detailProduct.skus) {
      expect(skuList.get(skuCellSelector(sku.id, '箱规体积')).text()).toBe('28224 cm³');
      expect(skuList.get(skuCellSelector(sku.id, '外箱尺寸')).text()).toBe('42 × 28 × 24 cm');
      expect(skuList.get(skuCellSelector(sku.id, '产品尺寸')).text()).toBe('12.5 × 8.25 × 20 cm');
      expect(skuList.get(skuCellSelector(sku.id, '毛重')).text()).toBe('9.1 kg');
      const fullPackaging = skuList.get(`[data-testid="sku-packaging-details-${sku.id}"]`);
      expect(fullPackaging.text()).toContain('10 × 10 × 12 cm');
      expect(fullPackaging.text()).toContain('容量');
      expect(fullPackaging.text()).toContain('450 ml');
    }
    expect(wrapper.get('[data-testid="packaging-package-image-21"]').attributes('src')).toContain('package-21.png');
    expect(wrapper.get('[data-testid="packaging-carton-image-21"]').attributes('src')).toContain('carton-21.png');
    expect(wrapper.find('[data-testid="product-images-section"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-audit-section"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="sku-image-21"]').attributes('src')).toContain('sku-21.png');
    expect(wrapper.get('[data-testid="sku-item-number-21"]').text()).toBe('BBF-PUMP-021-WH');
    expect(wrapper.find('input, select, textarea').exists()).toBe(false);

    for (const excludedLabel of ['附件', '合规资料', '商品描述', '销售属性', '渠道售价']) {
      expect(wrapper.text()).not.toContain(excludedLabel);
    }
  });

  it('links SKU selection to the overview picture, prices and quote while keeping both packaging records', async () => {
    const selectedSku = {
      ...detailProduct.skus[1]!,
      status: 'enabled' as const,
      skuImageUrl: '/uploads/sku-22.png',
      capacityMl: 650,
      packageLengthCm: 54,
      cartonQuantity: 24,
      supplierQuotes: [{ ...supplierQuote, id: 303, skuId: 22, supplierName: '烟灰杯供应商', purchasePrice: 11.2 }]
    };
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      totalStock: 987,
      totalSafetyStock: 123,
      skus: [detailProduct.skus[0]!, selectedSku]
    });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-overview-main-image"]').attributes('src')).toContain('sku-21.png');
    await wrapper.get('[data-testid="select-sku-22"]').trigger('click');

    const overview = wrapper.get('[data-testid="product-overview-section"]');
    expect(overview.get('[data-testid="product-overview-main-image"]').attributes('src')).toContain('sku-22.png');
    expect(overview.get('[data-testid="selected-sku-code"]').text()).toBe('BBF-PUMP-022-GY');
    expect(overview.get('[data-testid="selected-sku-sale-price"]').text()).toContain('21.00');
    expect(overview.get('[data-testid="selected-sku-purchase-price"]').text()).toContain('11.20');
    expect(overview.get('[data-testid="product-total-stock"]').text()).toContain('987');
    expect(overview.text()).toContain('123');
    expect(wrapper.get('[data-testid="selected-sku-quotes"]').text()).toContain('烟灰杯供应商');
    expect(wrapper.get('[data-testid="selected-sku-quotes"]').text()).not.toContain('义乌玻璃制品厂');
    expect(wrapper.get(skuCellSelector(21, '外箱尺寸')).text()).toBe('42 × 28 × 24 cm');
    expect(wrapper.get(skuCellSelector(22, '外箱尺寸')).text()).toBe('54 × 28 × 24 cm');
    expect(wrapper.get('[data-testid="select-sku-22"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-testid="product-sku-section"]').text()).toContain('BBF-PUMP-021-WH');
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('宁波包装制品厂');

    await overview.get('[data-testid="product-overview-main-image"]').trigger('error');
    expect(overview.find('[data-testid="product-overview-main-image-fallback"]').exists()).toBe(true);
    await wrapper.get('[data-testid="sku-thumbnail-21"]').trigger('click');
    expect(overview.get('[data-testid="product-overview-main-image"]').attributes('src')).toContain('sku-21.png');
    expect(wrapper.get('[data-testid="select-sku-21"]').attributes('aria-pressed')).toBe('true');
  });

  it('starts with an enabled default SKU and never promotes a disabled quote to default procurement', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => ({
        ...sku,
        status: sku.id === 21 ? 'disabled' as const : 'enabled' as const,
        supplierQuotes: sku.id === 22 ? [{ ...supplierQuote, skuId: 22, status: 'disabled' as const }] : sku.supplierQuotes
      }))
    });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="selected-sku-code"]').text()).toBe('BBF-PUMP-022-GY');
    expect(wrapper.get('[data-testid="selected-sku-purchase-price"]').text()).toBe('--');
    expect(wrapper.get('[data-testid="selected-sku-quotes"]').text()).toContain('停用');
    expect(wrapper.get('[data-testid="selected-sku-quotes"]').text()).not.toContain('默认');
  });

  it('exports the current loaded product with all SKUs even after selecting a different SKU', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    const download = vi.spyOn(productPresentation, 'downloadProductCsv').mockImplementation(() => undefined);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.get('[data-testid="select-sku-22"]').trigger('click');
    await wrapper.get('[data-testid="export-product-detail"]').trigger('click');

    expect(download).toHaveBeenCalledTimes(1);
    expect(download).toHaveBeenCalledWith([detailProduct], '商品资料-42.csv');
    expect(wrapper.get('[data-testid="product-detail-feedback"]').text()).toContain('当前商品全部 SKU');
    expect(productService.updateProduct).not.toHaveBeenCalled();
    expect(productService.setDefaultSupplierQuote).not.toHaveBeenCalled();
  });

  it('copies the loaded item number and reports clipboard failure truthfully', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('clipboard unavailable'));
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.get('[data-testid="copy-product-item-no"]').trigger('click');
    await flushPromises();

    expect(writeText).toHaveBeenCalledWith('GLASS-042');
    expect(wrapper.get('[data-testid="product-detail-feedback"]').text()).toBe('货号已复制');
    await wrapper.get('[data-testid="copy-product-item-no"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="product-detail-feedback"]').text()).toContain('复制失败');
  });

  it('copies the clicked SKU parameters without selecting that SKU or including commercial data', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      skus: [detailProduct.skus[0]!, {
        ...detailProduct.skus[1]!,
        capacityMl: 0,
        productMouthDiameterCm: 9,
        productHeightCm: 21,
        packageLengthCm: 54,
        cartonQuantity: 24
      }]
    });
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('clipboard unavailable'));
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const success = vi.spyOn(message, 'success').mockReturnValue(1);
    const error = vi.spyOn(message, 'error').mockReturnValue(2);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    const copy = wrapper.get('[data-testid="copy-sku-parameters-22"]');
    await copy.trigger('click');
    await flushPromises();

    expect(writeText).toHaveBeenCalledTimes(1);
    const [copiedText] = writeText.mock.calls[0]! as [string];
    expect(copiedText.split('\n')).toEqual(expect.arrayContaining([
      'SKU 货号：BBF-PUMP-022-GY',
      '规格：颜色：烟灰',
      '产品尺寸：口径 9 × 高 21 cm',
      '容量：0 ml',
      '外箱尺寸：54 × 28 × 24 cm',
      '装箱数量：24 个/箱',
      '箱规体积：28224 cm³',
      '毛重：9.1 kg'
    ]));
    expect(copiedText).not.toMatch(/条码|--|采购价|销售价|库存|供应商|BBF-PUMP-021-WH/);
    expect(wrapper.get('[data-testid="select-sku-21"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-testid="select-sku-22"]').attributes('aria-pressed')).toBe('false');
    expect(success).toHaveBeenCalledTimes(1);

    await copy.trigger('click');
    await flushPromises();
    expect(success).toHaveBeenCalledTimes(1);
    expect(error).toHaveBeenCalledTimes(1);
  });

  it('renders the approved overview, linked SKU workspace and local section navigation', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    const page = wrapper.get('[data-testid="product-detail-view"]');
    const body = wrapper.get('[data-testid="product-detail-body"]');
    const sections = body.findAll('section[id^="product-"]:not(#product-packaging)')
      .map((section) => section.attributes('data-testid'));

    expect(page.classes()).toContain('w-full');
    expect(wrapper.get('[data-testid="product-detail-title"]').text()).toBe(detailProduct.productName);
    expect(wrapper.find('.pd-work-grid [data-testid="product-sku-section"]').exists()).toBe(true);
    expect(wrapper.find('.pd-work-grid [data-testid="selected-sku-quotes"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="selected-sku-packaging"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-packaging-section"]').exists()).toBe(false);
    expect(wrapper.get('#product-skus td#product-packaging').attributes('data-label')).toBe('产品尺寸');
    expect(wrapper.get('[data-testid="export-product-detail"]').text()).toContain('当前商品');
    const sectionLinks = wrapper.get('[data-testid="product-detail-section-nav"]').findAll('a');
    expect(sectionLinks.map((link) => link.attributes('href'))).toEqual([
      '#product-skus', '#product-packaging', '#product-procurement', '#product-images', '#product-audit'
    ]);
    for (const link of sectionLinks) expect(wrapper.find(link.attributes('href')!).exists()).toBe(true);
    await sectionLinks[1]!.trigger('click');
    expect(sectionLinks[1]!.attributes('aria-current')).toBe('location');
    expect(sections).toEqual([
      'product-overview-section',
      'product-sku-section',
      'product-procurement-section',
      'product-images-section',
      'product-audit-section'
    ]);
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

  it('uses the loaded numeric product id for the canonical edit URL and returns to products', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(detailProduct);
    await router.push('/products/0042');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined);

    await wrapper.get('[data-testid="edit-product"]').trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'product-edit', params: { id: 42 } });
    expect(router.resolve({ name: 'product-edit', params: { id: detailProduct.id } }).fullPath).toBe('/products/42/edit');

    await wrapper.get('[data-testid="product-detail-back"]').trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'products' });
  });

  it('shows an initial skeleton and validates positive integer route ids', async () => {
    const pendingProduct = deferred<Product>();
    vi.mocked(productService.getProduct).mockReturnValueOnce(pendingProduct.promise);
    await router.push('/products/42');
    const loadingWrapper = mount(ProductDetailView, { global: { plugins: [router] } });

    const skeleton = loadingWrapper.get('[data-testid="product-detail-skeleton"]');
    expect(skeleton.findAll('.h-40')).toHaveLength(6);
    expect(skeleton.find('.h-16').exists()).toBe(false);
    expect(loadingWrapper.find('[data-testid="product-detail-back"]').exists()).toBe(true);
    loadingWrapper.unmount();
    pendingProduct.resolve(detailProduct);
    await flushPromises();

    vi.mocked(productService.getProduct).mockClear();
    await router.push('/products/0');
    const invalidWrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(productService.getProduct).not.toHaveBeenCalled();
    expect(invalidWrapper.get('[data-testid="product-detail-error"]').text()).toContain('商品编号无效');
    expect(invalidWrapper.find('[data-testid="product-detail-back"]').exists()).toBe(true);
  });

  it('retries a failed detail request in place', async () => {
    vi.mocked(productService.getProduct)
      .mockRejectedValueOnce(new Error('详情接口暂不可用'))
      .mockResolvedValueOnce(detailProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-detail-error"]').text()).toContain('详情接口暂不可用');
    expect(wrapper.find('[data-testid="product-detail-back"]').exists()).toBe(true);
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

  it('shows five packaging columns directly in each SKU row before its purchase price', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => sku.id === 22
        ? { ...sku, packageLengthCm: 54, cartonQuantity: 24 }
        : sku)
    });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    const skuList = wrapper.get('[data-testid="product-sku-section"]');
    const packagingHeaders = ['产品尺寸', '外箱尺寸', '装箱数量', '箱规体积', '毛重'];
    expect(skuList.findAll('thead th').map((header) => header.text())).toEqual([
      'SKU货号 / 规格', ...packagingHeaders, '采购价', '销售价', '库存', '安全库存', '状态'
    ]);
    for (const [id, cartonSize, quantity] of [
      [21, '42 × 28 × 24 cm', '40 个/箱'],
      [22, '54 × 28 × 24 cm', '24 个/箱']
    ] as const) {
      const skuButton = skuList.get(`[data-testid="select-sku-${id}"]`).element;
      const skuRow = skuButton.closest('tr')!;
      const cells = Array.from(skuRow.cells);
      expect(cells).toHaveLength(11);
      expect(cells[0]).toBe(skuButton.closest('td'));
      expect(cells.slice(1, 6).map((cell) => cell.dataset.label)).toEqual(packagingHeaders);
      expect(cells.slice(1, 6).map((cell) => cell.textContent?.trim())).toEqual([
        '12.5 × 8.25 × 20 cm', cartonSize, quantity, '28224 cm³', '9.1 kg'
      ]);
      expect(cells[6]?.dataset.label).toBe('采购价');
      expect(skuRow.classList.contains('pd-selected-sku')).toBe(id === 21);
      expect(skuRow.querySelector('dl, details, [data-testid^="sku-packaging-"]')).toBeNull();
      const fullPackaging = skuList.get(`details[data-testid="sku-packaging-details-${id}"]`);
      expect(fullPackaging.element.closest('tr')).toBe(skuRow.nextElementSibling);
      expect(fullPackaging.element.closest('td')?.colSpan).toBe(11);
      expect(fullPackaging.attributes('open')).toBeUndefined();
      expect(fullPackaging.get('summary').text()).toContain('查看完整包装参数');
    }
    const firstDetails = skuList.get('[data-testid="sku-packaging-details-21"]');
    const secondDetails = skuList.get('[data-testid="sku-packaging-details-22"]');
    await secondDetails.get('summary').trigger('click');
    expect(secondDetails.attributes('open')).toBeDefined();
    expect(firstDetails.attributes('open')).toBeUndefined();
    expect(skuList.get('[data-testid="select-sku-21"]').attributes('aria-pressed')).toBe('true');
    expect(skuList.get('[data-testid="select-sku-22"]').attributes('aria-pressed')).toBe('false');
  });

  it('keeps SKU-specific dimensions and optional fields in the collapsible packaging details', async () => {
    const perSkuProduct: Product = {
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => sku.id === 22
        ? {
            ...sku,
            productLengthCm: 14,
            productWidthCm: 9.5,
            productHeightCm: 21,
            capacityMl: null,
            packageImageFileId: 999
          }
        : sku)
    };
    vi.mocked(productService.getProduct).mockResolvedValueOnce(perSkuProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get(skuCellSelector(21, '产品尺寸')).text()).toBe('12.5 × 8.25 × 20 cm');
    expect(wrapper.get(skuCellSelector(22, '产品尺寸')).text()).toBe('14 × 9.5 × 21 cm');
    expect(wrapper.get('[data-testid="sku-packaging-details-21"]').text()).toContain('450 ml');
    const secondDetails = wrapper.get('[data-testid="sku-packaging-details-22"]');
    const secondCapacity = secondDetails.findAll('dl > div').find((fact) => fact.get('dt').text() === '容量');
    expect(secondCapacity?.get('dd').text()).toBe('--');
    expect(wrapper.get('[data-testid="product-images-section"] [data-testid="packaging-package-image-21"]').attributes('src')).toContain('package-21.png');
    expect(wrapper.get('[data-testid="product-images-section"] [data-testid="packaging-carton-image-21"]').attributes('src')).toContain('carton-21.png');
  });

  it('shows packaging and carton images for every SKU when the image files differ', async () => {
    const productWithPerSkuImages: Product = {
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => sku.id === 22
        ? {
            ...sku,
            packageImageFileId: 204,
            packageImageUrl: '/uploads/package-22.png',
            cartonImageFileId: 205,
            cartonImageUrl: '/uploads/carton-22.png'
          }
        : sku)
    };
    vi.mocked(productService.getProduct).mockResolvedValueOnce(productWithPerSkuImages);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    const images = wrapper.get('[data-testid="product-images-section"]');
    expect(images.get('[data-testid="packaging-package-image-21"]').attributes('src')).toContain('package-21.png');
    expect(images.get('[data-testid="packaging-carton-image-21"]').attributes('src')).toContain('carton-21.png');
    expect(images.get('[data-testid="packaging-package-image-22"]').attributes('src')).toContain('package-22.png');
    expect(images.get('[data-testid="packaging-carton-image-22"]').attributes('src')).toContain('carton-22.png');
    expect(images.text()).toContain('BBF-PUMP-021-WH');
    expect(images.text()).toContain('BBF-PUMP-022-GY');
  });

  it('groups stored images into four read-only albums per pack and previews full-size long images in order', async () => {
    const wrapper = await mountDetailAlbums(detailImagePacks);
    const wholesale = wrapper.get('[data-testid="detail-image-pack-0"]');
    const retail = wrapper.get('[data-testid="detail-image-pack-1"]');
    expect(wholesale.get('h3').text()).toBe('批发素材');
    expect(retail.get('h3').text()).toBe('零售素材');
    expect(wholesale.findAll('h4').map((heading) => heading.text())).toEqual([
      '主图 1 张', 'SKU 图 1 张', '详情图 1 张', '详情长图 2 张'
    ]);
    expect(wholesale.get('[data-testid="detail-image-album-0-main"] img').attributes('src')).toBe('/uploads/wholesale-main.png');
    expect(wholesale.get('[data-testid="detail-image-album-0-sku"] img').attributes('src')).toBe('/uploads/wholesale-sku.png');
    expect(wholesale.get('[data-testid="detail-image-album-0-detail"] img').attributes('src')).toBe('/uploads/wholesale-detail.png');
    expect(retail.findAll('h4')).toHaveLength(4);
    expect(retail.findAll('img').map((image) => image.attributes('src'))).toEqual(['/uploads/retail-main.png']);
    expect(wholesale.find('input[type="file"]').exists()).toBe(false);
    expect(wholesale.find('button[aria-label^="移除 "]').exists()).toBe(false);

    await wholesale.get('[aria-label="查看 批发长图一.png"]').trigger('click');
    const preview = wrapper.get('[data-testid="image-album-preview"]');
    expect(preview.get('[aria-label="可滚动查看详情长图"]').attributes('tabindex')).toBe('0');
    expect(preview.get('img').attributes('src')).toBe('/uploads/wholesale-long-1.png');
    await preview.get('[aria-label="下一张"]').trigger('click');
    await flushPromises();
    const advancedPreview = wrapper.get('[data-testid="image-album-preview"]');
    expect(advancedPreview.get('img').attributes('src')).toBe('/uploads/wholesale-long-2.png');
    await advancedPreview.trigger('keydown', { key: 'Escape' });
    expect(wrapper.find('[data-testid="image-album-preview"]').exists()).toBe(false);
  });

  it('downloads just the chosen detail pack as a ZIP', async () => {
    const wrapper = await mountDetailAlbums(detailImagePacks);
    const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aAxkAAAAASUVORK5CYII='), (char) => char.charCodeAt(0));
    const fetchImage = vi.fn(async () => new Response(png));
    const createObjectURL = vi.fn(() => 'blob:detail-pack');
    vi.stubGlobal('fetch', fetchImage);
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL: vi.fn() });
    const download = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      download(this.download, this.href);
    });
    vi.useFakeTimers();
    try {
      await wrapper.get('[aria-label="打包下载零售素材"]').trigger('click');
      await flushPromises();
      expect(fetchImage.mock.calls).toEqual([['/uploads/retail-main.png', { signal: expect.any(AbortSignal) }]]);
      expect(createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: 'application/zip' }));
      expect(download.mock.calls).toEqual([['零售素材.zip', 'blob:detail-pack']]);
      vi.advanceTimersByTime(30_000);
    } finally {
      vi.useRealTimers();
    }
  });

  it('groups legacy main and SKU images in the default pack', async () => {
    const wrapper = await mountDetailAlbums(null);
    const pack = wrapper.get('[data-testid="detail-image-pack-0"]');
    expect(pack.get('h3').text()).toBe('商品资料包');
    expect(pack.findAll('h4').map((heading) => heading.text())).toEqual([
      '主图 1 张', 'SKU 图 1 张', '详情图 0 张', '详情长图 0 张'
    ]);
    expect(pack.get('[data-testid="detail-image-album-0-main"] img').attributes('src')).toBe('/uploads/product-main.png');
    expect(pack.get('[data-testid="detail-image-album-0-sku"] img').attributes('src')).toBe('/uploads/sku-21.png');
  });

  it('respects a deliberately empty pack list even when canonical images still exist', async () => {
    const wrapper = await mountDetailAlbums([]);
    const images = wrapper.get('[data-testid="product-images-section"]');
    expect(images.text()).toContain('暂无素材包');
    expect(images.find('[data-testid="detail-image-pack-0"]').exists()).toBe(false);
  });

  it('keeps identical packaging separate when SKU sales units differ', async () => {
    vi.mocked(productService.getProduct).mockResolvedValueOnce({
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => ({ ...sku, salesUnit: sku.id === 21 ? '只' : '套' }))
    });
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get(skuCellSelector(21, '装箱数量')).text()).toBe('40 只/箱');
    expect(wrapper.get(skuCellSelector(22, '装箱数量')).text()).toBe('40 套/箱');
  });

  it('renders abnormal stock, quantity, dimensions and volume as -- while preserving zero', async () => {
    const abnormalProduct: Product = {
      ...detailProduct,
      skus: detailProduct.skus.map((sku) => sku.id === 21
        ? {
            ...sku,
            stockQuantity: Number.NaN,
            safetyStockQuantity: Number.POSITIVE_INFINITY,
            packageLengthCm: Number.NaN,
            packageVolumeCm3: Number.POSITIVE_INFINITY,
            supplierQuotes: sku.supplierQuotes.map((quote) => quote.id === 301
              ? { ...quote, minPurchaseQuantity: Number.POSITIVE_INFINITY }
              : quote)
          }
        : sku)
    };
    vi.mocked(productService.getProduct).mockResolvedValueOnce(abnormalProduct);
    await router.push('/products/42');
    const wrapper = mount(ProductDetailView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-sku-section"]').text()).not.toMatch(/NaN|Infinity/);
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).not.toMatch(/NaN|Infinity/);
    expect(wrapper.get('[data-testid="product-procurement-section"]').text()).toContain('0');
    expect(wrapper.get(skuCellSelector(21, '外箱尺寸')).text()).toBe('--');
    expect(wrapper.get(skuCellSelector(21, '箱规体积')).text()).toBe('--');
  });
});
