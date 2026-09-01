import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { clearMessages, messages } from '../../components/feedback/message';
import { httpProductService } from './httpProductService';
import { productFixture, productPage } from './productTestFixtures';
import type { Product, ProductFormPayload, ProductService } from './types';
import ProductDetailDrawer from './components/ProductDetailDrawer.vue';
import ProductForm from './components/ProductForm.vue';
import ProductList from './components/ProductList.vue';

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

  it('loads one SPU row and opens product creation from the list', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
        id: 8, productCode: 'GLASS-001', itemNo: 'GB-001', productName: '玻璃杯', categoryId: 1,
        brand: 'BeBefish', productType: 'simple', mainImageFileId: null, remark: '', specifications: [], skus: [], status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.text()).toContain('玻璃杯');
    await wrapper.get('[data-testid="add-product"]').trigger('click');

    expect(wrapper.emitted('create')).toHaveLength(1);
  });

  it('shows the approved product overview labels and opens a selected product', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
        id: 8, productCode: 'GLASS-001', itemNo: 'GB-001', productName: '玻璃杯', categoryId: 1,
        brand: 'BeBefish', productType: 'simple', mainImageFileId: null, remark: '', specifications: [], skus: [], status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.text()).toContain('SKU 主数据');
    await wrapper.get('[data-testid="product-row-8"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]).toEqual([expect.objectContaining({ id: 8 })]);
  });

  it('shows product image, item number, specification, price, weight, brand and supplier', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
          id: 9,
          productCode: 'GLASS-009',
          itemNo: 'GB-009',
          productName: '复古玻璃杯',
          categoryId: 1,
          brand: '共典',
          productType: 'variant',
          mainImageFileId: 19,
          mainImageUrl: '/uploads/glass-009.png',
          defaultSupplierName: '义乌玻璃厂',
          remark: '',
          specifications: [{ name: '颜色', values: ['透明'] }],
          skus: [{
            id: 90,
            skuCode: 'GLASS-009-CLEAR',
            barcode: '',
            skuName: '透明',
            specificationValues: ['透明'],
            salesUnit: '只',
            defaultSalePrice: 19.9,
            standardCost: 8,
            packageLengthCm: null,
            packageWidthCm: null,
            packageHeightCm: null,
            packageVolumeCm3: null,
            netWeightKg: 0.3,
            grossWeightKg: 0.42,
            gramWeightG: 300,
            packagingMethod: '彩盒',
            cartonQuantity: 12,
            skuImageFileId: null,
            packageImageFileId: null,
            cartonImageFileId: null,
            defaultSku: true,
            status: 'enabled'
          }],
          status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-image-9"]').attributes('src')).toBe('/uploads/glass-009.png');
    expect(wrapper.text()).toContain('GB-009');
    expect(wrapper.text()).toContain('透明');
    expect(wrapper.text()).toContain('¥19.90');
    expect(wrapper.text()).toContain('0.42 kg');
    expect(wrapper.text()).toContain('共典');
    expect(wrapper.text()).toContain('义乌玻璃厂');
    expect(wrapper.get('[data-testid="product-packaging-9"]').classes()).toContain('whitespace-nowrap');
    expect(wrapper.get('[data-testid="product-supplier-9"]').classes()).toContain('whitespace-nowrap');
  });

  it('previews a product image while the pointer is over the thumbnail', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
          id: 11,
          productCode: 'GLASS-011',
          itemNo: 'GB-011',
          productName: '预览玻璃杯',
          categoryId: 1,
          brand: '共典',
          productType: 'simple',
          mainImageFileId: 21,
          mainImageUrl: '/uploads/glass-011.png',
          remark: '',
          specifications: [],
          skus: [],
          status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    const image = wrapper.get('[data-testid="product-image-11"]');
    await image.trigger('mouseenter');
    expect(document.body.querySelector('[data-testid="image-hover-preview"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="image-hover-preview"] img')?.getAttribute('src')).toBe('/uploads/glass-011.png');

    await image.trigger('mouseleave');
    expect(document.body.querySelector('[data-testid="image-hover-preview"]')).toBeNull();
  });

  it('groups product information and formats specifications, packaging and carton size', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
          id: 10,
          productCode: 'GLASS-010',
          itemNo: 'GB-010',
          productName: '高脚玻璃杯',
          categoryId: 1,
          brand: '共典',
          productType: 'simple',
          mainImageFileId: null,
          remark: '',
          specifications: [
            { name: '口径', values: ['70±1'] },
            { name: '高度', values: ['83.5±1'] },
            { name: '容量', values: ['210'] },
            { name: '重量', values: ['200±12'] }
          ],
          skus: [{
            id: 100,
            skuCode: 'GLASS-010-DEFAULT',
            barcode: '',
            skuName: '默认规格',
            specificationValues: [],
            salesUnit: '只',
            defaultSalePrice: 20,
            standardCost: 8,
            packageLengthCm: 46.7,
            packageWidthCm: 30.7,
            packageHeightCm: 22.4,
            packageVolumeCm3: 32000,
            netWeightKg: 0.2,
            grossWeightKg: 0.42,
            gramWeightG: 200,
            packagingMethod: '普盒/1*12*4/48*',
            cartonQuantity: 48,
            skuImageFileId: null,
            packageImageFileId: null,
            cartonImageFileId: null,
            defaultSku: true,
            status: 'enabled'
          }],
          status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.text()).toContain('商品信息');
    expect(wrapper.text()).toContain('产品名称');
    expect(wrapper.text()).not.toContain('产品编码');
    expect(wrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品信息', '产品名称', '规格', '售价', '毛重', '包装', '外箱尺寸', '供应商', '状态', '操作'
    ]);
    expect(wrapper.get('[data-testid="product-name-10"]').text()).toBe('高脚玻璃杯');
    expect(wrapper.get('[data-testid="product-info-10"]').text()).not.toContain('高脚玻璃杯');
    expect(wrapper.get('[data-testid="product-info-10"]').text()).toContain('GB-010');
    expect(wrapper.get('[data-testid="product-info-10"]').text()).toContain('共典');
    expect(wrapper.get('[data-testid="product-info-content-10"]').classes()).toContain('items-center');
    expect(wrapper.get('[data-testid="product-info-thumb-10"]').classes()).toContain('shrink-0');
    expect(wrapper.get('[data-testid="product-info-10"]').text()).not.toContain('货号：');
    expect(wrapper.get('[data-testid="product-info-10"]').text()).not.toContain('品牌：');
    expect(wrapper.get('[data-testid="product-specifications-10"]').text()).toContain('口径：70±1mm');
    expect(wrapper.get('[data-testid="product-specifications-10"]').text()).toContain('高度：83.5±1mm');
    expect(wrapper.get('[data-testid="product-specifications-10"]').text()).toContain('容量：210ml');
    expect(wrapper.get('[data-testid="product-specifications-10"]').text()).toContain('重量：200±12g');
    expect(wrapper.get('[data-testid="product-packaging-10"]').text()).toContain('普盒/1*12*4/48*');
    const cartonSizeLines = wrapper.get('[data-testid="product-carton-size-10"]').findAll('p');
    expect(cartonSizeLines).toHaveLength(2);
    expect(cartonSizeLines[0].text()).toBe('46.7*30.7*22.4CM');
    expect(cartonSizeLines[1].text()).toBe('0.032立方');
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

  it('shows category names from masterdata in the product list filter', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
          id: 21,
          productCode: 'PRD-000021',
          itemNo: 'ITEM-021',
          productName: '啤酒杯',
          categoryId: 2,
          brand: '共典',
          productType: 'simple',
          mainImageFileId: null,
          remark: '',
          specifications: [],
          skus: [],
          status: 'enabled'
      })]))
    };
    const wrapper = mount(ProductList, {
      props: {
        service,
        categories: [{ id: 2, categoryName: '啤酒杯' }]
      }
    });
    await flushPromises();

    await wrapper.get('[data-testid="product-filter-category-button"]').trigger('click');

    const categoryMenu = wrapper.get('[data-testid="product-filter-category-menu"]');
    expect(categoryMenu.text()).toContain('啤酒杯');
    expect(categoryMenu.text()).not.toContain('分类 2');
  });

  it('uses the prototype list table styling for the product master data table', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-table"]').classes()).toContain('rounded-[22px]');
    expect(wrapper.get('[data-testid="product-table"]').classes()).toContain('shadow-[0_14px_40px_rgba(31,45,74,0.04)]');
    expect(wrapper.get('table').classes()).toContain('min-w-[1200px]');
    expect(wrapper.get('table').classes()).toContain('border-collapse');
    expect(wrapper.get('thead').classes()).toContain('font-black');
    expect(wrapper.get('thead th').classes()).toContain('whitespace-nowrap');
    expect(wrapper.get('thead tr').findAll('th')[8].classes()).toContain('w-20');
    expect(wrapper.get('thead tr').findAll('th')[9].classes()).toContain('w-24');
    expect(wrapper.get('tbody').classes()).toContain('divide-y');
    expect(wrapper.get('tbody').classes()).toContain('text-slate-700');
    expect(wrapper.get('[data-testid="product-table-pagination"]').classes()).toContain('min-w-[900px]');
    expect(wrapper.get('[data-testid="product-table-pagination"]').classes()).toContain('flex-wrap');
    expect(wrapper.get('[data-testid="product-table-pagination"]').classes()).toContain('justify-end');
    expect(wrapper.get('[data-testid="product-table-pagination"]').classes()).toContain('rounded-2xl');
    expect(wrapper.get('[data-testid="product-page-number"]').text()).toBe('1');
  });

  it('keeps SKU details in the product detail drawer instead of expanding list rows', async () => {
    const product = productFixture({
      id: 101,
      productCode: 'PRD-000101',
      itemNo: 'GB-101',
      productName: '高硼硅玻璃杯',
      categoryId: 1,
      brand: '共典',
      productType: 'variant',
      mainImageFileId: null,
      remark: '',
      specifications: [{ name: '容量', values: ['210ml'] }],
      skus: [{
        id: 1001,
        skuCode: 'PRD-000101-001',
        barcode: 'GB-101-CLEAR',
        skuName: '透明款',
        specificationValues: ['透明'],
        salesUnit: '只',
        defaultSalePrice: 12,
        standardCost: 5,
        packageLengthCm: null,
        packageWidthCm: null,
        packageHeightCm: null,
        packageVolumeCm3: null,
        netWeightKg: null,
        grossWeightKg: null,
        gramWeightG: null,
        packagingMethod: '',
        cartonQuantity: null,
        skuImageFileId: 2001,
        packageImageFileId: null,
        cartonImageFileId: null,
        defaultSku: true,
        status: 'enabled'
      }],
      status: 'enabled'
    });
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([product]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    const row = wrapper.get('[data-testid="product-row-101"]');
    expect(row.find('button[aria-label="展开 SKU"]').exists()).toBe(false);
    expect(row.find('button[aria-label="收起 SKU"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="product-sku-list-101"]').exists()).toBe(false);

    await row.trigger('click');
    expect(wrapper.emitted('select')).toHaveLength(1);
  });

  it('closes an open product filter menu after clicking outside the filter bar', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    const categoryButton = wrapper.get('[data-testid="product-filter-category-button"]');
    categoryButton.element.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await nextTick();
    expect(wrapper.find('[data-testid="product-filter-category-menu"]').exists()).toBe(true);

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await nextTick();

    expect(wrapper.find('[data-testid="product-filter-category-menu"]').exists()).toBe(false);
  });

  it('uses the prototype SKU master header and filter layout', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([productFixture({
        id: 8, productCode: 'GLASS-001', itemNo: 'GB-001', productName: '玻璃杯', categoryId: 1,
        brand: 'BeBefish', productType: 'simple', mainImageFileId: null, remark: '', specifications: [], skus: [], status: 'enabled'
      })], 5))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.get('[data-testid="product-table-header"]').classes()).toContain('px-6');
    expect(wrapper.get('[data-testid="product-table-title"]').text()).toBe('SKU 主数据');
    expect(wrapper.get('[data-testid="product-table-subtitle"]').text()).toContain('5 个商品资料');
    expect(wrapper.get('[data-testid="product-filter-toggle"]').text()).toContain('筛选');
    expect(wrapper.get('[data-testid="add-product"]').text()).toContain('新增商品');
    expect(wrapper.get('[data-testid="add-product"]').classes()).toContain('bg-[#536dff]');
    expect(wrapper.get('[data-testid="product-filter-bar"]').classes()).toContain('bg-slate-50/45');
    expect(wrapper.get('[data-testid="product-filter-article"]').attributes('placeholder')).toBe('输入货号 / SKU');
    expect(wrapper.get('[data-testid="product-filter-search"]').text()).toBe('搜索');
    expect(wrapper.get('[data-testid="product-filter-brand-button"]').text()).toContain('全部品牌');
    expect(wrapper.get('[data-testid="product-filter-supplier-button"]').text()).toContain('全部供应商');
    expect(wrapper.get('[data-testid="product-filter-category-button"]').text()).toContain('全部分类');
    expect(wrapper.get('[data-testid="product-filter-reset"]').text()).toBe('重置');

    await wrapper.get('[data-testid="product-filter-toggle"]').trigger('click');
    expect(wrapper.get('[data-testid="product-status-filter-panel"]').text()).toContain('在售');
  });

  it('searches products only after clicking the search button', async () => {
    const listProducts = mockListProducts(productPage([]));
    const service: ProductService = { ...fakeProductService, listProducts };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(listProducts).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="product-filter-article"]').setValue('TS0721');
    expect(listProducts).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-testid="product-filter-search"]').trigger('click');
    await flushPromises();

    expect(listProducts).toHaveBeenCalledTimes(2);
    expect(listProducts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: 'TS0721' }));
  });

  it('applies dropdown filters only after clicking the search button', async () => {
    const makeProduct = (id: number, brand: string): Product => productFixture({
      id,
      productCode: `PRD-${id}`,
      itemNo: `ITEM-${id}`,
      productName: `产品${id}`,
      categoryId: 1,
      brand,
      productType: 'simple',
      mainImageFileId: null,
      remark: '',
      specifications: [],
      skus: [],
      status: 'enabled'
    });
    const records = [makeProduct(21, '品牌A'), makeProduct(22, '品牌B')];
    const listProducts = mockListProducts(productPage(records));
    const service: ProductService = { ...fakeProductService, listProducts };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    expect(wrapper.findAll('[data-testid^="product-row-"]')).toHaveLength(2);
    await wrapper.get('[data-testid="product-filter-brand-button"]').trigger('click');
    const brandOption = wrapper.get('[data-testid="product-filter-brand-menu"]').findAll('button').find((option) => option.text() === '品牌B');
    expect(brandOption).toBeDefined();
    await brandOption!.trigger('click');

    expect(wrapper.findAll('[data-testid^="product-row-"]')).toHaveLength(2);
    expect(listProducts).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-testid="product-filter-search"]').trigger('click');
    await flushPromises();

    expect(wrapper.findAll('[data-testid^="product-row-"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="product-row-22"]').exists()).toBe(true);
    expect(listProducts).toHaveBeenCalledTimes(2);
  });

  it('uses the approved prototype layout for product overview stats', async () => {
    const service: ProductService = {
      ...fakeProductService,
      listProducts: mockListProducts(productPage([]))
    };
    const wrapper = mount(ProductList, { props: { service } });
    await flushPromises();

    const stats = wrapper.get('[data-testid="product-overview-stats"]');
    expect(stats.classes()).toContain('min-[1180px]:grid-cols-4');
    expect(stats.classes()).toContain('gap-4');
    const firstCard = stats.findAll('article')[0];
    expect(firstCard.classes()).toContain('rounded-[18px]');
    expect(firstCard.classes()).toContain('border-slate-200');
    expect(firstCard.classes()).toContain('p-5');
    expect(firstCard.classes()).toContain('shadow-[0_14px_40px_rgba(31,45,74,0.04)]');
    expect(firstCard.find('[data-testid="product-overview-stat-icon"]').classes()).toContain('h-11');
    expect(firstCard.find('[data-testid="product-overview-stat-icon"]').classes()).toContain('w-11');
    expect(firstCard.find('[data-testid="product-overview-stat-icon"]').classes()).toContain('rounded-full');
    expect(firstCard.find('[data-testid="product-overview-stat-badge"]').classes()).toContain('rounded-full');
    expect(firstCard.find('[data-testid="product-overview-stat-badge"]').classes()).toContain('text-[11px]');
    expect(firstCard.findAll('p')[0].classes()).toContain('text-sm');
    expect(firstCard.findAll('p')[0].classes()).toContain('text-slate-400');
    expect(firstCard.findAll('p')[1].classes()).toContain('mt-2');
    expect(firstCard.findAll('p')[1].classes()).toContain('text-3xl');
    expect(firstCard.findAll('p')[1].classes()).toContain('leading-none');
    expect(firstCard.findAll('p')[1].classes()).toContain('tracking-tight');
    expect(stats.findAll('article')).toHaveLength(4);
  });

  it('uses the approved prototype layout for the product detail drawer', async () => {
    const product = productFixture({
      id: 18,
      productCode: 'PRD-000018',
      itemNo: 'EW43249',
      productName: '高脚玻璃杯',
      categoryId: 1,
      brand: '共典',
      productType: 'simple',
      mainImageFileId: 88,
      mainImageUrl: '/uploads/glass.png',
      remark: '',
      specifications: [{ name: '容量', values: ['210'] }],
      skus: [{
        id: 180,
        skuCode: 'PRD-000018-001',
        barcode: 'EW43249',
        skuName: '默认规格',
        specificationValues: [],
        salesUnit: '只',
        defaultSalePrice: 19.9,
        standardCost: 8,
        packageLengthCm: 46.7,
        packageWidthCm: 30.7,
        packageHeightCm: 22.4,
        packageVolumeCm3: 32000,
        netWeightKg: 0.2,
        grossWeightKg: 0.42,
        gramWeightG: 200,
        packagingMethod: '普盒/1*12*4/48*',
        cartonQuantity: 48,
        skuImageFileId: null,
        packageImageFileId: null,
        cartonImageFileId: null,
        defaultSku: true,
        status: 'enabled'
      }],
      defaultSupplierName: '义乌玻璃厂',
      status: 'enabled'
    });
    const wrapper = mount(ProductDetailDrawer, { props: { product } });
    const drawer = wrapper.get('[data-testid="product-detail-drawer"]');

    expect(drawer.classes()).toContain('w-[50vw]');
    expect(drawer.classes()).toContain('min-w-[620px]');
    expect(drawer.classes()).toContain('max-w-[840px]');
    expect(drawer.classes()).toContain('border-l');
    expect(drawer.classes()).toContain('shadow-[0_24px_80px_rgba(31,45,74,0.24)]');
    expect(drawer.text()).toContain('商品详情');
    expect(drawer.text()).toContain('包装尺寸');
    expect(drawer.text()).toContain('重量与条码');
    expect(drawer.text()).toContain('图片资料');
    expect(drawer.text()).toContain('资料完整度');
    expect(wrapper.get('[data-testid="product-detail-drawer-sku"]').text()).toContain('SKU 信息');
    expect(wrapper.get('[data-testid="product-detail-drawer-sku"]').text()).toContain('SKU 图片');
    expect(wrapper.get('[data-testid="product-detail-drawer-sku"]').text()).toContain('货号');
    expect(wrapper.get('[data-testid="product-detail-drawer-sku"]').text()).toContain('SKU 名称');
    expect(wrapper.get('[data-testid="product-detail-drawer-sku"]').text()).toContain('容量：210ml');
    expect(wrapper.findAll('[data-testid="product-detail-sku-card"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="product-detail-sku-card"]').classes()).toContain('p-3');
    expect(wrapper.get('[data-testid="product-detail-sku-fields"]').classes()).toContain('sm:grid-cols-2');
    expect(wrapper.get('[data-testid="product-detail-sku-image"]').find('img').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-detail-drawer-header"]').classes()).toContain('mb-6');
    expect(wrapper.get('[data-testid="product-detail-drawer-summary"]').classes()).toContain('min-[1180px]:grid-cols-[260px_minmax(0,1fr)]');
    expect(wrapper.get('[data-testid="edit-product-detail"]').text()).toContain('编辑资料');

    await wrapper.get('[data-testid="close-product-detail-drawer"]').trigger('click');
    expect(wrapper.emitted('close')).toHaveLength(1);
  });
});
