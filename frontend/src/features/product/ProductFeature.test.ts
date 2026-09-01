import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type LocationQueryRaw } from 'vue-router';
import { clearMessages, messages } from '../../components/feedback/message';
import type { Category } from '../masterdata/types';
import { httpProductService } from './httpProductService';
import { productFixture, productPage } from './productTestFixtures';
import type { Product, ProductFormPayload, ProductService } from './types';
import ProductForm from './components/ProductForm.vue';
import ProductList from './components/ProductList.vue';
import { deduplicateCategories } from './productCategoryTree';

function mockFetchApi(data: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response(
    JSON.stringify({ code: 'SUCCESS', message: '操作成功', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  ))));
}

function productPayload(supplierQuotes?: ProductFormPayload['skus'][number]['supplierQuotes']): ProductFormPayload {
  return {
    itemNo: 'EW43249',
    productName: '高脚玻璃杯',
    categoryId: 1,
    brand: '共典',
    productType: 'simple',
    mainImageFileId: null,
    remark: '',
    specifications: [],
    skus: [{
      skuCode: 'SKU-EW43249',
      barcode: 'EW43249',
      skuName: '默认规格',
      specificationValues: [],
      salesUnit: '只',
      defaultSalePrice: 19.9,
      standardCost: 8,
      safetyStockQuantity: 12,
      packageLengthCm: 42,
      packageWidthCm: 31,
      packageHeightCm: 28,
      packageVolumeCm3: 36456,
      innerPackageLengthCm: 36,
      innerPackageWidthCm: 25,
      innerPackageHeightCm: 22,
      netWeightKg: 8.5,
      grossWeightKg: 9.2,
      gramWeightG: 350,
      innerPackageWeightKg: 1.1,
      packagingMethod: '彩盒',
      cartonQuantity: 12,
      skuImageFileId: null,
      packageImageFileId: null,
      cartonImageFileId: null,
      supplierQuotes
    }]
  };
}

describe('http product service contracts', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('serializes SKU purchasing and packaging fields', async () => {
    mockFetchApi({});
    const payload = productPayload([{
      supplierId: 3,
      supplierItemNo: 'SUP-EW43249',
      purchasePrice: 6.8,
      minPurchaseQuantity: 48,
      defaultQuote: true,
      status: 'enabled'
    }]);

    await httpProductService.createProduct(payload);

    const body = JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body));
    expect(body.skus[0]).toEqual(expect.objectContaining({
      safetyStockQuantity: 12,
      innerPackageLengthCm: 36,
      innerPackageWidthCm: 25,
      innerPackageHeightCm: 22,
      innerPackageWeightKg: 1.1
    }));
    expect(body.skus[0].supplierQuotes[0]).toEqual(expect.objectContaining({
      supplierId: 3,
      supplierItemNo: 'SUP-EW43249'
    }));
  });

  it('distinguishes omitted supplier quotes from explicitly clearing them', async () => {
    mockFetchApi({});

    await httpProductService.createProduct(productPayload(undefined));
    await httpProductService.createProduct(productPayload([]));

    const omittedBody = JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body));
    const clearedBody = JSON.parse(String(vi.mocked(fetch).mock.calls[1][1]?.body));
    expect(omittedBody.skus[0]).not.toHaveProperty('supplierQuotes');
    expect(clearedBody.skus[0].supplierQuotes).toEqual([]);
  });

  it('returns numeric category counts from the API envelope', async () => {
    mockFetchApi({ 1: 4, 8: 0 });

    const counts = await httpProductService.getCategoryCounts();

    expect(counts).toEqual({ 1: 4, 8: 0 });
    expect(fetch).toHaveBeenCalledWith('/api/products/category-counts', expect.any(Object));
  });
});

const fakeProductService: ProductService = {
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
};

function mockListProducts(result: ReturnType<typeof productPage>) {
  return vi.fn<ProductService['listProducts']>().mockResolvedValue(result);
}

const catalogCategories: Category[] = [
  { id: 12, categoryCode: 'DRINKWARE', categoryName: '杯具', parentId: null, level: 1, sortOrder: 1, status: 'enabled', remark: '' },
  { id: 13, categoryCode: 'GLASS', categoryName: '玻璃杯', parentId: 12, level: 2, sortOrder: 1, status: 'enabled', remark: '' },
  { id: 14, categoryCode: 'MUG', categoryName: '马克杯', parentId: 12, level: 2, sortOrder: 2, status: 'enabled', remark: '' },
  { id: 20, categoryCode: 'DINNERWARE', categoryName: '餐具', parentId: null, level: 1, sortOrder: 2, status: 'enabled', remark: '' }
];

function catalogProduct(id: number, productName: string, overrides: Partial<Product> = {}): Product {
  return productFixture({
    id,
    productCode: `PRD-${String(id).padStart(6, '0')}`,
    itemNo: `ITEM-${id}`,
    productName,
    categoryId: 13,
    brand: '共典',
    productType: 'simple',
    mainImageFileId: null,
    defaultSupplierName: '义乌玻璃厂',
    totalStock: 36,
    defaultSalePrice: 19.9,
    completenessPercent: 80,
    completenessStatus: 'incomplete',
    remark: '',
    specifications: [],
    skus: [],
    status: 'enabled',
    ...overrides
  });
}

function catalogService(listProducts: ProductService['listProducts']): ProductService {
  return {
    ...fakeProductService,
    listProducts,
    getCategoryCounts: vi.fn().mockResolvedValue({ 12: 5, 13: 3, 14: 2, 20: 4 })
  };
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

async function mountCatalog(options: {
  query?: LocationQueryRaw;
  service?: ProductService;
  categories?: Category[];
  categoryCounts?: Record<number, number>;
  allProductTotal?: number;
  categoryLookupFailed?: boolean;
} = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/products', name: 'products', component: { template: '<div />' } }]
  });
  await router.push({ name: 'products', query: options.query });
  await router.isReady();
  const replace = vi.spyOn(router, 'replace');
  const service = options.service ?? catalogService(vi.fn().mockResolvedValue(productPage([])));
  const wrapper = mount(ProductList, {
    props: {
      service,
      categories: options.categories ?? catalogCategories,
      categoryCounts: options.categoryCounts ?? { 12: 5, 13: 3, 14: 2, 20: 4 },
      allProductTotal: options.allProductTotal ?? 9,
      categoryLookupFailed: options.categoryLookupFailed ?? false
    } as never,
    global: { plugins: [router] }
  });
  await flushPromises();
  return { replace, router, service, wrapper };
}

describe('product feature', () => {
  afterEach(() => {
    clearMessages();
  });

  it('renders the product form with the approved prototype layout', () => {
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    expect(wrapper.get('[data-testid="product-form-shell"]').classes()).toContain('xl:grid-cols-[minmax(0,1fr)_320px]');
    expect(wrapper.get('[data-testid="product-form-header"]').text()).toContain('新增产品');
    expect(wrapper.get('[data-testid="product-form-header"]').text()).toContain('MASTER DATA / PRODUCTS');
    expect(wrapper.get('[data-testid="product-form-header"]').text()).toContain('产品信息较多，使用独立页面录入，便于完整维护档案。');
    expect(wrapper.get('[data-testid="product-form-header"]').classes()).not.toContain('rounded-[22px]');
    const header = wrapper.get('[data-testid="product-form-header"]');
    expect(wrapper.get('[data-testid="product-form-back"]').text()).toContain('返回产品列表');
    expect(wrapper.get('[data-testid="product-form-back"]').classes()).toContain('text-sm');
    expect(header.find('p').classes()).toContain('text-[11px]');
    expect(header.find('h1').classes()).toContain('text-2xl');
    expect(header.findAll('p')[1].classes()).toContain('text-sm');
    expect(wrapper.get('[data-testid="product-form-steps"]').text()).toContain('基础信息');
    expect(wrapper.get('[data-testid="product-form-steps"]').text()).toContain('渠道价格');
    expect(wrapper.get('[data-testid="product-form-steps"]').text()).toContain('规格包装');
    expect(wrapper.get('[data-testid="product-form-steps"]').text()).toContain('图片资料');
    expect(wrapper.find('[data-testid="product-form-main"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="product-form-sidebar"]').text()).toContain('资料预检');
    expect(wrapper.get('[data-testid="product-form-sidebar"]').text()).toContain('产品主图、SKU 图、彩盒图和外箱图');
  });

  it('renders product image, product dimensions, and SKU packaging image fields', () => {
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    expect(wrapper.get('[data-testid="product-main-image"]').attributes('type')).toBe('file');
    expect(wrapper.get('[data-testid="product-dimensions"]').text()).toContain('口径');
    expect(wrapper.get('[data-testid="product-dimensions"]').text()).toContain('高度');
    expect(wrapper.get('[data-testid="product-dimensions"]').text()).toContain('容量');
    expect(wrapper.get('[data-testid="product-dimensions"]').text()).toContain('重量');
    expect(wrapper.get('[data-testid="sku-image-0"]').attributes('type')).toBe('file');
    expect(wrapper.get('[data-testid="product-form-section-packaging"]').find('[data-testid="package-image"]').attributes('type')).toBe('file');
    expect(wrapper.get('[data-testid="product-form-section-packaging"]').find('[data-testid="carton-image"]').attributes('type')).toBe('file');
    expect(wrapper.get('[data-testid="product-form-section-packaging"]').text()).not.toContain('克重 (g)');
    expect(wrapper.get('[data-testid="product-form-section-pricing"]').text()).toContain('通过添加 SKU 维护货号、名称、规格、售价和 SKU 图');
    expect(wrapper.get('[data-testid="product-form-section-pricing"]').text()).not.toContain('彩盒图');
    expect(wrapper.get('[data-testid="product-form-section-pricing"]').text()).not.toContain('外箱图');
  });

  it('updates step cards and precheck status from entered product data', async () => {
    const wrapper = mount(ProductForm, {
      props: {
        service: fakeProductService,
        categories: [{ id: 1, categoryName: '啤酒杯' }]
      }
    });

    expect(wrapper.get('[data-testid="progress-step-basic"]').text()).toContain('当前填写');
    expect(wrapper.get('[data-testid="progress-summary-basic-status"]').text()).toBe('待完善');
    expect(wrapper.get('[data-testid="progress-step-packaging"]').attributes('aria-controls')).toBe('product-form-section-packaging');

    await wrapper.find('input[placeholder="例如：EW43249"]').setValue('EW43249');
    await wrapper.get('[data-testid="product-name"]').setValue('啤酒杯');
    await wrapper.get('[data-testid="product-category"]').setValue('1');
    await wrapper.find('input[placeholder="请输入品牌"]').setValue('共典');

    expect(wrapper.get('[data-testid="progress-step-basic"]').text()).toContain('已完成');
    expect(wrapper.get('[data-testid="progress-step-pricing"]').text()).toContain('当前填写');

    await wrapper.get('[data-testid="sku-sale-price-0"]').setValue('12.8');
    await wrapper.get('[data-testid="sku-standard-cost-0"]').setValue('6.4');

    expect(wrapper.get('[data-testid="progress-summary-pricing-status"]').text()).toBe('已完成');
    expect(wrapper.get('[data-testid="progress-summary-pricing-bar"]').attributes('style')).toContain('width: 100%');
  });

  it('uploads product and SKU images and saves their file ids with dimensions', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.uploadImage).mockImplementation(async (file) => ({
      id: file.name === 'product.png' ? 101 : file.name === 'sku.png' ? 102 : file.name === 'package.png' ? 103 : 104,
      url: `/uploads/${file.name}`,
      originalFileName: file.name
    }));
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    await wrapper.get('[data-testid="product-dimension-diameter"]').setValue('70±1mm');
    await wrapper.get('[data-testid="product-dimension-height"]').setValue('83.5±1mm');
    await wrapper.get('[data-testid="product-dimension-capacity"]').setValue('210ml');
    await wrapper.get('[data-testid="product-dimension-weight"]').setValue('200±12g');
    for (const [testId, fileName] of [['product-main-image', 'product.png'], ['sku-image-0', 'sku.png'], ['package-image', 'package.png'], ['carton-image', 'carton.png']] as const) {
      const input = wrapper.get(`[data-testid="${testId}"]`);
      Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['image'], fileName, { type: 'image/png' })] });
      await input.trigger('change');
      await flushPromises();
    }

    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(fakeProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      mainImageFileId: 101,
      specifications: expect.arrayContaining([
        { name: '口径', values: ['70±1mm'] },
        { name: '高度', values: ['83.5±1mm'] },
        { name: '容量', values: ['210ml'] },
        { name: '重量', values: ['200±12g'] }
      ]),
      skus: [expect.objectContaining({ skuImageFileId: 102, packageImageFileId: 103, cartonImageFileId: 104 })]
    }));
    expect(fakeProductService.uploadImage).toHaveBeenCalledTimes(4);
    expect(wrapper.get('[data-testid="progress-summary-images-status"]').text()).toBe('已完成');
  });

  it('shows upload success, previews uploaded images, and removes an image association', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.uploadImage).mockResolvedValue({
      id: 201,
      url: '/uploads/product-preview.png',
      originalFileName: 'product-preview.png'
    });
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
    const input = wrapper.get('[data-testid="product-main-image"]');
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'product-preview.png', { type: 'image/png' })]
    });

    await input.trigger('change');
    await flushPromises();

    expect(wrapper.get('[data-testid="product-main-image-preview"]').attributes('src')).toBe('/uploads/product-preview.png');
    expect(wrapper.get('[data-testid="product-main-image-status"]').text()).toContain('上传成功');

    await wrapper.get('[data-testid="product-main-image-delete"]').trigger('click');

    expect(wrapper.find('[data-testid="product-main-image-preview"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-main-image-status"]').text()).toContain('未上传');

    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();
    expect(fakeProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({ mainImageFileId: null }));
  });

  it('hydrates existing product and packaging images when editing', () => {
    const product = productFixture({
      id: 22,
      productCode: 'PRD-000022',
      itemNo: 'GLASS-022',
      productName: '编辑图片产品',
      categoryId: 1,
      brand: '共典',
      productType: 'simple',
      mainImageFileId: 301,
      mainImageUrl: '/uploads/main.png',
      remark: '',
      specifications: [],
      skus: [{
        id: 302,
        skuCode: 'PRD-000022-001',
        barcode: 'GLASS-022-1',
        skuName: '默认规格',
        specificationValues: [],
        salesUnit: '只',
        defaultSalePrice: 12,
        standardCost: 5,
        packageLengthCm: 10,
        packageWidthCm: 10,
        packageHeightCm: 10,
        packageVolumeCm3: 1000,
        netWeightKg: 1,
        grossWeightKg: 1.2,
        gramWeightG: 200,
        packagingMethod: '彩盒',
        cartonQuantity: 12,
        skuImageFileId: 303,
        skuImageUrl: '/uploads/sku.png',
        packageImageFileId: 304,
        packageImageUrl: '/uploads/package.png',
        cartonImageFileId: 305,
      cartonImageUrl: '/uploads/carton.png',
        defaultSku: true,
        status: 'enabled'
      }],
      status: 'enabled'
    });
    const wrapper = mount(ProductForm, { props: { service: fakeProductService, initialValue: product } });

    expect(wrapper.get('[data-testid="product-main-image-preview"]').attributes('src')).toBe('/uploads/main.png');
    expect(wrapper.get('[data-testid="sku-image-0-preview"]').attributes('src')).toBe('/uploads/sku.png');
    expect(wrapper.get('[data-testid="package-image-preview"]').attributes('src')).toBe('/uploads/package.png');
    expect(wrapper.get('[data-testid="carton-image-preview"]').attributes('src')).toBe('/uploads/carton.png');
  });

  it('adds SKU cards directly without a product type switch', async () => {
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    expect(wrapper.find('[data-testid="product-type-simple"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-type-variant"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-testid="sku-editor"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="default-sku-badge"]').text()).toContain('默认 SKU');
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    expect(wrapper.findAll('[data-testid="sku-editor"]')).toHaveLength(2);
    expect(wrapper.get('[data-testid="sku-item-no-0"]').attributes('placeholder')).toContain('货号');
    expect(wrapper.get('[data-testid="sku-name-0"]').attributes('placeholder')).toContain('名称');
  });

  it('keeps shared packaging data in one product-level editor', async () => {
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    expect(wrapper.findAll('[data-testid="packaging-editor"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="packaging-editor"]').classes()).not.toContain('rounded-2xl');
    expect(wrapper.get('[data-testid="packaging-editor"]').classes()).not.toContain('border');
    expect(wrapper.get('[data-testid="packaging-editor"]').text()).not.toContain('统一包装资料');
    expect(wrapper.get('[data-testid="packaging-editor"]').text()).not.toContain('此处信息由产品所有 SKU 共用');
    expect(wrapper.find('[data-testid="package-image"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="carton-image"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="package-image-1"]').exists()).toBe(false);
    await wrapper.get('[data-testid="add-sku"]').trigger('click');

    expect(wrapper.findAll('[data-testid="packaging-editor"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="package-image-1"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="carton-image-1"]').exists()).toBe(false);
  });

  it('saves shared packaging only on the default SKU record for API compatibility', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    await wrapper.find('[data-testid="sku-specification-0"]').setValue('透明');
    await wrapper.find('input[placeholder="长"]').setValue('46.7');
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    await wrapper.find('[data-testid="sku-specification-1"]').setValue('烟灰');
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(fakeProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      skus: [
        expect.objectContaining({ packageLengthCm: 46.7 }),
        expect.objectContaining({ packageLengthCm: null })
      ]
    }));
  });

  it('saves manually added SKU specification information as variant data', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
    await wrapper.get('[data-testid="sku-specification-0"]').setValue('透明 / 竖纹');
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    await wrapper.get('[data-testid="sku-specification-1"]').setValue('烟灰 / 樱花纹');
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(fakeProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productType: 'variant',
      specifications: [{ name: '规格', values: ['透明 / 竖纹', '烟灰 / 樱花纹'] }]
    }));
  });

  it('allows multiple manually added SKUs without formal specification values', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    await wrapper.get('[data-testid="sku-name-0"]').setValue('透明款');
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    await wrapper.get('[data-testid="sku-name-1"]').setValue('烟灰款');
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).not.toContain('请填写每个 SKU 的规格信息，且规格不能重复');
    expect(fakeProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productType: 'variant',
      specifications: [],
      skus: [
        expect.objectContaining({ skuName: '透明款', specificationValues: [] }),
        expect.objectContaining({ skuName: '烟灰款', specificationValues: [] })
      ]
    }));
  });

  it('shows validation when gross weight is below net weight', async () => {
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
    await wrapper.get('[data-testid="net-weight"]').setValue('9.2');
    await wrapper.get('[data-testid="gross-weight"]').setValue('8.5');
    await wrapper.get('[data-testid="save-product"]').trigger('click');

    expect(messages.value.at(-1)?.type).toBe('error');
    expect(messages.value.at(-1)?.text).toContain('毛重不能小于净重');
  });

  it('shows a top success message after saving a product', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(messages.value.at(-1)).toMatchObject({ type: 'success', text: '产品已保存' });
  });

  it('shows a top error message when saving a product fails', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockRejectedValue(new Error('保存产品失败'));
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(messages.value.at(-1)).toMatchObject({ type: 'error', text: '保存产品失败' });
  });

  it('does not ask users to enter a product code', async () => {
    vi.clearAllMocks();
    vi.mocked(fakeProductService.createProduct).mockResolvedValue({} as Product);
    const wrapper = mount(ProductForm, { props: { service: fakeProductService } });

    expect(wrapper.text()).not.toContain('产品编码');
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(fakeProductService.createProduct).toHaveBeenCalledWith(
      expect.not.objectContaining({ productCode: expect.anything() })
    );
  });

  it('updates an existing product instead of creating a duplicate', async () => {
    vi.clearAllMocks();
    const existing = productFixture({
      id: 18,
      productCode: 'GLASS-018',
      itemNo: 'GB-018',
      productName: '原产品名称',
      categoryId: 1,
      brand: '共典',
      productType: 'simple',
      mainImageFileId: 88,
      remark: '原备注',
      specifications: [],
      skus: [{
        id: 180,
        skuCode: 'GLASS-018-DEFAULT',
        barcode: '',
        skuName: '默认规格',
        specificationValues: [],
        salesUnit: '只',
        defaultSalePrice: 12,
        standardCost: 5,
        packageLengthCm: 10,
        packageWidthCm: 10,
        packageHeightCm: 10,
        packageVolumeCm3: 1000,
        netWeightKg: 0.5,
        grossWeightKg: 0.6,
        gramWeightG: 500,
        packagingMethod: '彩盒',
        cartonQuantity: 12,
        skuImageFileId: 90,
        packageImageFileId: 91,
        cartonImageFileId: 92,
        defaultSku: true,
        status: 'enabled'
      }],
      status: 'enabled'
    });
    vi.mocked(fakeProductService.updateProduct).mockResolvedValue(existing);

    const wrapper = mount(ProductForm, { props: { service: fakeProductService, initialValue: existing } });
    await wrapper.get('[data-testid="product-name"]').setValue('更新后的产品名称');
    await wrapper.get('[data-testid="save-product"]').trigger('click');
    await flushPromises();

    expect(fakeProductService.updateProduct).toHaveBeenCalledWith(18, expect.objectContaining({
      productName: '更新后的产品名称',
      mainImageFileId: 88
    }));
    expect(fakeProductService.createProduct).not.toHaveBeenCalled();
    expect(wrapper.emitted('saved')).toHaveLength(1);
  });

  it('uses a category selector when masterdata categories are provided', () => {
    const wrapper = mount(ProductForm, {
      props: {
        service: fakeProductService,
        categories: [{ id: 1, categoryName: '玻璃杯' }]
      }
    });

    expect(wrapper.get('[data-testid="product-category"]').text()).toContain('玻璃杯');
  });

});

describe('Task 8 product catalog list', () => {
  it('filters from a parent category and resets the route page', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([], 0, query.page, query.size))
    ));
    const { router, wrapper } = await mountCatalog({
      query: { page: '3', size: '20' },
      service: catalogService(listProducts)
    });
    const push = vi.spyOn(router, 'push');

    await wrapper.get('[data-testid="category-node-12"]').trigger('click');
    await flushPromises();

    expect(push).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({ categoryId: '12', page: '1', size: '20' })
    }));
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12 });
  });

  it('keeps matching category nodes and ancestors, then restores expansion after clearing search', async () => {
    const { wrapper } = await mountCatalog();

    expect(wrapper.get('[data-testid="category-node-12"]').text()).toContain('5');
    expect(wrapper.find('[data-testid="category-node-13"]').exists()).toBe(true);
    await wrapper.get('[data-testid="category-toggle-12"]').trigger('click');
    expect(wrapper.find('[data-testid="category-node-13"]').exists()).toBe(false);

    await wrapper.get('[data-testid="category-search"]').setValue('玻璃');
    expect(wrapper.find('[data-testid="category-node-12"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="category-node-13"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="category-node-14"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="category-node-20"]').exists()).toBe(false);

    await wrapper.get('[data-testid="category-search"]').setValue('');
    expect(wrapper.find('[data-testid="category-node-13"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="category-add"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="category-edit"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="category-delete"]').exists()).toBe(false);
  });

  it('uses the authoritative unfiltered total for all products', async () => {
    const { wrapper } = await mountCatalog({
      allProductTotal: 137,
      categoryCounts: { 12: 5, 13: 3, 14: 2, 20: 4 }
    });

    expect(wrapper.get('[data-testid="category-node-all"]').text()).toContain('137');
    expect(wrapper.get('[data-testid="category-node-all"]').text()).not.toContain('9');
  });

  it('deduplicates dirty categories and promotes orphaned or cyclic paths so every id appears once', async () => {
    const dirtyCategories: Category[] = [
      { id: 1, categoryCode: 'A', categoryName: '重复保留项', parentId: null, level: 1, sortOrder: 1, status: 'enabled', remark: '' },
      { id: 1, categoryCode: 'Z', categoryName: '重复后项', parentId: null, level: 1, sortOrder: 9, status: 'disabled', remark: '' },
      { id: 2, categoryCode: 'ORPHAN', categoryName: '孤儿分类', parentId: 999, level: 2, sortOrder: 2, status: 'enabled', remark: '' },
      { id: 3, categoryCode: 'SELF', categoryName: '自环分类', parentId: 3, level: 2, sortOrder: 3, status: 'enabled', remark: '' },
      { id: 4, categoryCode: 'CYCLE-A', categoryName: '双环 A', parentId: 5, level: 2, sortOrder: 4, status: 'enabled', remark: '' },
      { id: 5, categoryCode: 'CYCLE-B', categoryName: '双环 B', parentId: 4, level: 2, sortOrder: 5, status: 'enabled', remark: '' },
      { id: 6, categoryCode: 'CYCLE-CHILD', categoryName: '依附环节点', parentId: 4, level: 3, sortOrder: 6, status: 'enabled', remark: '' },
      { id: 7, categoryCode: 'ORPHAN-CHILD', categoryName: '孤儿子节点', parentId: 2, level: 3, sortOrder: 7, status: 'enabled', remark: '' }
    ];
    const { wrapper } = await mountCatalog({ categories: dirtyCategories, allProductTotal: 7 });

    for (const id of [1, 2, 3, 4, 5, 6, 7]) {
      expect(wrapper.findAll(`[data-testid="category-node-${id}"]`)).toHaveLength(1);
    }
    expect(wrapper.get('[data-testid="category-node-1"]').text()).toContain('重复保留项');
    expect(wrapper.text()).not.toContain('重复后项');

    await wrapper.get('[data-testid="category-search"]').setValue('双环');
    expect(wrapper.find('[data-testid="category-node-4"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="category-node-5"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="category-node-6"]').exists()).toBe(false);
  });

  it('uses level as a deterministic tie-breaker when duplicate categories otherwise match', () => {
    const lowerLevel: Category = {
      id: 88,
      categoryCode: 'LEVEL-TIE',
      categoryName: '层级冲突分类',
      parentId: null,
      level: 1,
      sortOrder: 1,
      status: 'enabled',
      remark: ''
    };
    const higherLevel = { ...lowerLevel, level: 2 };

    expect(deduplicateCategories([higherLevel, lowerLevel])).toEqual([lowerLevel]);
  });

  it('reconciles expansion state when categories refresh and expands newly reachable roots', async () => {
    const { wrapper } = await mountCatalog();

    await wrapper.get('[data-testid="category-toggle-12"]').trigger('click');
    expect(wrapper.find('[data-testid="category-node-13"]').exists()).toBe(false);

    await wrapper.setProps({
      categories: [
        { id: 30, categoryCode: 'NEW', categoryName: '新根分类', parentId: null, level: 1, sortOrder: 1, status: 'enabled', remark: '' },
        { id: 31, categoryCode: 'NEW-CHILD', categoryName: '新根子分类', parentId: 30, level: 2, sortOrder: 1, status: 'enabled', remark: '' }
      ]
    } as never);
    expect(wrapper.find('[data-testid="category-node-31"]').exists()).toBe(true);

    await wrapper.setProps({
      categories: [
        { id: 12, categoryCode: 'DRINKWARE', categoryName: '杯具', parentId: null, level: 1, sortOrder: 1, status: 'enabled', remark: '' },
        { id: 15, categoryCode: 'REATTACHED', categoryName: '重新接入子分类', parentId: 12, level: 2, sortOrder: 1, status: 'enabled', remark: '' }
      ]
    } as never);
    expect(wrapper.find('[data-testid="category-node-15"]').exists()).toBe(true);
  });

  it('shows an explicit lookup error instead of mislabeling a product category', async () => {
    const product = catalogProduct(42, '分类待恢复商品', { categoryId: 999 });
    const { wrapper } = await mountCatalog({
      categories: [],
      categoryLookupFailed: true,
      service: catalogService(mockListProducts(productPage([product])))
    });

    const categoryCell = wrapper.get('[data-testid="product-category-42"]');
    expect(categoryCell.text()).toContain('--');
    expect(categoryCell.text()).toContain('分类加载失败');
    expect(categoryCell.text()).not.toContain('未分类');
  });

  it('keeps item number or sku as the only right-side query field and searches on Enter', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([], 0, query.page, query.size))
    ));
    const { router, wrapper } = await mountCatalog({
      query: { page: '4', size: '20', categoryId: '12' },
      service: catalogService(listProducts)
    });

    expect(wrapper.find('[data-testid="product-keyword"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="product-filter-brand-button"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-filter-supplier-button"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-filter-category-button"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-status-tabs"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="current-category-strip"]').exists()).toBe(false);

    await wrapper.get('[data-testid="product-keyword"]').setValue(' SKU-42 ');
    await wrapper.get('[data-testid="product-keyword"]').trigger('keyup.enter');
    await flushPromises();

    expect(router.currentRoute.value.query).toEqual({
      categoryId: '12',
      keyword: 'SKU-42',
      page: '1',
      size: '20'
    });
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12, keyword: 'SKU-42' });

    await wrapper.get('[data-testid="product-reset"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({ categoryId: '12', page: '1', size: '20' });
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12 });
  });

  it('does not request while the keyword is only a draft', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockResolvedValue(productPage([]));
    const { wrapper } = await mountCatalog({
      query: { page: '1', size: '20' },
      service: catalogService(listProducts)
    });

    expect(listProducts).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="product-keyword"]').setValue('NOT-SUBMITTED');
    await flushPromises();
    expect(listProducts).toHaveBeenCalledTimes(1);
  });

  it('canonicalizes malformed query once before issuing a single request', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockResolvedValue(productPage([]));
    const { replace, router } = await mountCatalog({
      query: {
        categoryId: ['12', '13'],
        keyword: '   ',
        page: '-7',
        size: '999'
      },
      service: catalogService(listProducts)
    });
    await flushPromises();

    expect(replace).toHaveBeenCalledTimes(1);
    expect(replace).toHaveBeenCalledWith({ query: { page: '1', size: '20' } });
    expect(router.currentRoute.value.query).toEqual({ page: '1', size: '20' });
    expect(listProducts).toHaveBeenCalledTimes(1);
    expect(listProducts).toHaveBeenCalledWith({ page: 1, size: 20 });
  });

  it('restores valid route state and safely normalizes array or negative query values', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([], 200, query.page, query.size))
    ));
    const { router, wrapper } = await mountCatalog({
      query: { categoryId: '13', keyword: 'GLASS', page: '3', size: '50' },
      service: catalogService(listProducts)
    });

    expect(wrapper.get<HTMLInputElement>('[data-testid="product-keyword"]').element.value).toBe('GLASS');
    expect(wrapper.get('[data-testid="category-node-13"]').attributes('aria-pressed')).toBe('true');
    expect(listProducts).toHaveBeenLastCalledWith({ page: 3, size: 50, categoryId: 13, keyword: 'GLASS' });

    await router.push({
      name: 'products',
      query: { categoryId: ['13', '14'], keyword: ['A', 'B'], page: '-7', size: '999' }
    });
    await flushPromises();
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-keyword"]').element.value).toBe('');
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20 });

    router.back();
    await flushPromises();
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-keyword"]').element.value).toBe('GLASS');
    expect(listProducts).toHaveBeenLastCalledWith({ page: 3, size: 50, categoryId: 13, keyword: 'GLASS' });
  });

  it('paginates with ellipses, keeps filters, and resets the page when size changes', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([catalogProduct(query.page, `第 ${query.page} 页商品`)], 240, query.page, query.size))
    ));
    const { router, wrapper } = await mountCatalog({
      query: { categoryId: '12', keyword: 'CUP', page: '5', size: '20' },
      service: catalogService(listProducts)
    });

    expect(wrapper.findAll('[data-testid="product-page-ellipsis"]')).toHaveLength(2);
    await wrapper.get('[data-testid="product-page-next"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({ categoryId: '12', keyword: 'CUP', page: '6', size: '20' });

    await wrapper.get('[data-testid="product-page-size"]').setValue('50');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({ categoryId: '12', keyword: 'CUP', page: '1', size: '50' });
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 50, categoryId: 12, keyword: 'CUP' });
  });

  it('corrects an out-of-range page once when the result total shrinks', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([], 15, query.page, query.size))
    ));
    const { router } = await mountCatalog({
      query: { page: '9', size: '20' },
      service: catalogService(listProducts)
    });
    await flushPromises();

    expect(router.currentRoute.value.query.page).toBe('1');
    expect(listProducts.mock.calls.map(([query]) => query.page)).toEqual([9, 1]);
  });

  it('renders only approved table columns and text navigation actions', async () => {
    const product = catalogProduct(42, '高硼硅玻璃杯');
    const { wrapper } = await mountCatalog({
      service: catalogService(mockListProducts(productPage([product])))
    });

    expect(wrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品', '分类', '品牌 / 默认供应商', '库存', '价格', '资料状态', '业务状态', '操作'
    ]);
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('批量');
    expect(wrapper.text()).not.toContain('编辑');
    expect(wrapper.text()).not.toContain('删除');
    expect(wrapper.get('[data-testid="product-detail-42"]').text()).toBe('查看详情');
    expect(wrapper.get('[data-testid="product-thumb-42"]').classes()).toContain('h-11');
    expect(wrapper.get('[data-testid="product-thumb-42"]').classes()).toContain('w-11');
    expect(wrapper.get('[data-testid="product-table-scroll"]').classes()).toContain('overflow-x-auto');
    expect(wrapper.get('[data-testid="product-pagination"]').element.parentElement?.getAttribute('data-testid')).not.toBe('product-table-scroll');

    await wrapper.get('[data-testid="product-detail-42"]').trigger('click');
    expect(wrapper.emitted('open-product')?.[0]).toEqual([product]);
    await wrapper.get('[data-testid="add-product"]').trigger('click');
    expect(wrapper.emitted('create-product')).toHaveLength(1);
  });

  it('falls back after an image error and retries when the same product receives a new url', async () => {
    const brokenProduct = catalogProduct(42, '图片异常商品', {
      mainImageFileId: 1,
      mainImageUrl: '/uploads/broken.png'
    });
    const recoveredProduct = catalogProduct(42, '图片异常商品', {
      mainImageFileId: 2,
      mainImageUrl: '/uploads/recovered.png'
    });
    const listProducts = vi.fn<ProductService['listProducts']>()
      .mockResolvedValueOnce(productPage([brokenProduct]))
      .mockResolvedValueOnce(productPage([recoveredProduct]));
    const { router, wrapper } = await mountCatalog({
      query: { page: '1', size: '20' },
      service: catalogService(listProducts)
    });

    await wrapper.get('[data-testid="product-image-42"]').trigger('error');
    expect(wrapper.find('[data-testid="product-image-42"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-image-placeholder-42"]').exists()).toBe(true);

    await router.push({ query: { keyword: 'RECOVERED', page: '1', size: '20' } });
    await flushPromises();
    expect(wrapper.get('[data-testid="product-image-42"]').attributes('src')).toBe('/uploads/recovered.png');
    expect(wrapper.find('[data-testid="product-image-placeholder-42"]').exists()).toBe(false);
  });

  it('keeps large stock and price values inside fixed truncating numeric cells', async () => {
    const product = catalogProduct(42, '大数值商品', {
      totalStock: 9876543210123.125,
      defaultSalePrice: 9876543210.99
    });
    const { wrapper } = await mountCatalog({
      service: catalogService(mockListProducts(productPage([product])))
    });

    const stock = wrapper.get('[data-testid="product-stock-42"]');
    const price = wrapper.get('[data-testid="product-price-42"]');
    for (const cell of [stock, price]) {
      expect(cell.classes()).toContain('overflow-hidden');
      expect(cell.get('span').classes()).toContain('truncate');
      expect(cell.get('span').classes()).toContain('tabular-nums');
      expect(cell.get('span').attributes('title')).toBeTruthy();
    }
  });

  it('keeps the page-size selector accessible in compact pagination and preserves filters when it changes', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockImplementation((query) => (
      Promise.resolve(productPage([], 240, query.page, query.size))
    ));
    const { router, wrapper } = await mountCatalog({
      query: { categoryId: '12', keyword: 'CUP', page: '5', size: '20' },
      service: catalogService(listProducts)
    });
    const summary = wrapper.get('[data-testid="product-pagination-summary"]');
    const sizeControl = summary.get('[data-testid="product-page-size-control"]');

    expect(summary.text()).toContain('共 240 条');
    expect(sizeControl.classes()).toContain('flex');
    expect(sizeControl.classes()).not.toContain('hidden');
    expect(sizeControl.get('select').attributes('aria-label')).toBe('每页条数');

    await sizeControl.get('select').setValue('50');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({
      categoryId: '12', keyword: 'CUP', page: '1', size: '50'
    });
    expect(listProducts).toHaveBeenLastCalledWith({
      page: 1, size: 50, categoryId: 12, keyword: 'CUP'
    });
  });

  it('uses compact pagination through narrow desktop widths and restores full pages at lg', async () => {
    const { wrapper } = await mountCatalog({
      query: { page: '5', size: '20' },
      service: catalogService(vi.fn().mockResolvedValue(productPage([], 240, 5, 20)))
    });
    const pagination = wrapper.get('[data-testid="product-pagination"]');

    expect(pagination.classes()).toContain('flex-col');
    expect(pagination.classes()).toContain('lg:flex-row');
    expect(pagination.classes()).toContain('lg:flex-wrap');
    expect(wrapper.get('[data-testid="product-pagination-mobile"]').classes()).toContain('lg:hidden');
    expect(wrapper.get('[data-testid="product-pagination-desktop"]').classes()).toContain('hidden');
    expect(wrapper.get('[data-testid="product-pagination-desktop"]').classes()).toContain('lg:flex');
  });

  it('ignores stale product responses when route queries change quickly', async () => {
    const firstRequest = deferred<ReturnType<typeof productPage>>();
    const secondRequest = deferred<ReturnType<typeof productPage>>();
    const listProducts = vi.fn<ProductService['listProducts']>()
      .mockImplementationOnce(() => firstRequest.promise)
      .mockImplementationOnce(() => secondRequest.promise);
    const { router, wrapper } = await mountCatalog({
      query: { keyword: 'OLD', page: '1', size: '20' },
      service: catalogService(listProducts)
    });

    await router.push({ name: 'products', query: { keyword: 'NEW', page: '1', size: '20' } });
    await flushPromises();
    secondRequest.resolve(productPage([catalogProduct(2, '新结果')], 1));
    await flushPromises();
    expect(wrapper.text()).toContain('新结果');

    firstRequest.resolve(productPage([catalogProduct(1, '旧结果')], 1));
    await flushPromises();
    expect(wrapper.text()).toContain('新结果');
    expect(wrapper.text()).not.toContain('旧结果');
  });

  it('keeps the previous list on failure and retries without changing route filters', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>()
      .mockResolvedValueOnce(productPage([catalogProduct(1, '上次结果')], 1))
      .mockRejectedValueOnce(new Error('网络中断'))
      .mockResolvedValueOnce(productPage([catalogProduct(2, '重试结果')], 1));
    const { router, wrapper } = await mountCatalog({ service: catalogService(listProducts) });

    await router.push({ name: 'products', query: { keyword: 'RETRY', page: '1', size: '20' } });
    await flushPromises();
    expect(wrapper.text()).toContain('上次结果');
    expect(wrapper.get('[data-testid="product-list-error"]').text()).toContain('网络中断');

    await wrapper.get('[data-testid="product-list-retry"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({ keyword: 'RETRY', page: '1', size: '20' });
    expect(wrapper.text()).toContain('重试结果');
    expect(wrapper.find('[data-testid="product-list-error"]').exists()).toBe(false);
  });

  it('shows only the retry error when the initial product request fails', async () => {
    const listProducts = vi.fn<ProductService['listProducts']>().mockRejectedValue(new Error('首次加载失败'));
    const { wrapper } = await mountCatalog({
      query: { page: '1', size: '20' },
      service: catalogService(listProducts)
    });

    expect(wrapper.get('[data-testid="product-list-error"]').text()).toContain('首次加载失败');
    expect(wrapper.find('[data-testid="product-empty"]').exists()).toBe(false);
  });

  it('shows an explicit empty state without production sample rows', async () => {
    const { wrapper } = await mountCatalog({
      service: catalogService(mockListProducts(productPage([])))
    });

    expect(wrapper.get('[data-testid="product-empty"]').text()).toContain('暂无商品');
    expect(wrapper.findAll('[data-testid^="product-row-"]')).toHaveLength(0);
  });
});
