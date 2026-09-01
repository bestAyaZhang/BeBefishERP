import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { clearMessages, messages } from '../../../components/feedback/message';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category, MasterdataService, Supplier } from '../../masterdata/types';
import { productFixture } from '../productTestFixtures';
import { productService } from '../productService';
import type { Product, ProductService } from '../types';
import ProductEditorView from './ProductEditorView.vue';

vi.mock('../productService', () => ({
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
    listCategories: vi.fn(),
    listSuppliers: vi.fn()
  } satisfies Pick<MasterdataService, 'listCategories' | 'listSuppliers'>
}));

enableAutoUnmount(afterEach);

const enabledCategory: Category = {
  id: 8,
  categoryCode: 'BOTTLE',
  categoryName: '按压瓶',
  parentId: null,
  level: 1,
  sortOrder: 1,
  status: 'enabled',
  remark: ''
};

const disabledCategory: Category = {
  ...enabledCategory,
  id: 9,
  categoryCode: 'LEGACY',
  categoryName: '历史杯具',
  status: 'disabled'
};

const enabledSupplier: Supplier = {
  id: 4,
  supplierNo: 'SUP-004',
  supplierName: '义乌玻璃厂',
  contactPerson: '陈经理',
  mobile: '',
  telephone: '',
  address: '义乌',
  status: 'enabled',
  remark: ''
};

const disabledSupplier: Supplier = {
  ...enabledSupplier,
  id: 5,
  supplierNo: 'SUP-005',
  supplierName: '历史供应商',
  status: 'disabled'
};

function completeProduct(overrides: Partial<Product> = {}): Product {
  return productFixture({
    id: 42,
    productCode: 'PRD-000042',
    itemNo: 'BBF-042',
    productName: '玻璃按压瓶',
    categoryId: 9,
    categoryName: '历史杯具',
    brand: 'BeBefish',
    productType: 'simple',
    mainImageFileId: 100,
    mainImageUrl: '/uploads/main.png',
    remark: '编辑备注',
    specifications: [{ name: '颜色', values: ['透明'] }],
    skus: [{
      id: 420,
      skuCode: 'BBF-042-001',
      barcode: '6970000000420',
      skuName: '透明款',
      specificationValues: ['透明'],
      salesUnit: '只',
      defaultSalePrice: 19.9,
      standardCost: 8.6,
      safetyStockQuantity: 12,
      stockQuantity: 24,
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
      skuImageFileId: 101,
      skuImageUrl: '/uploads/sku.png',
      packageImageFileId: 102,
      packageImageUrl: '/uploads/package.png',
      cartonImageFileId: 103,
      cartonImageUrl: '/uploads/carton.png',
      supplierQuotes: [{
        id: 301,
        skuId: 420,
        supplierId: 5,
        supplierName: '历史供应商',
        supplierItemNo: 'LEGACY-42',
        purchasePrice: 8.6,
        minPurchaseQuantity: 24,
        defaultQuote: true,
        status: 'enabled'
      }],
      defaultSku: true,
      status: 'enabled'
    }],
    status: 'enabled',
    ...overrides
  });
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

function page<T>(records: T[], pageNumber = 1, total = records.length) {
  return { records, page: pageNumber, pageSize: 100, total };
}

async function mountEditor(path = '/products/new') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/products', name: 'products', component: { template: '<div data-testid="products-page" />' } },
      { path: '/products/new', name: 'product-new', component: ProductEditorView },
      { path: '/products/:id/edit', name: 'product-edit', component: ProductEditorView },
      { path: '/products/:id', name: 'product-detail', component: { template: '<div data-testid="product-detail-page" />' } }
    ]
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(
    { template: '<router-view />' },
    { attachTo: document.body, global: { plugins: [router] } }
  );
  await flushPromises();
  return { router, wrapper };
}

async function fillBasic(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="product-item-no"]').setValue('BBF-021');
  await wrapper.get('[data-testid="product-name"]').setValue('按压瓶');
  await wrapper.get('[data-testid="product-category"]').setValue('8');
  await wrapper.get('[data-testid="product-brand"]').setValue('BeBefish');
}

async function addSku(wrapper: VueWrapper, name: string, barcode: string) {
  await wrapper.get('[data-testid="add-sku"]').trigger('click');
  await wrapper.get('[data-testid="sku-dialog-name"]').setValue(name);
  await wrapper.get('[data-testid="sku-dialog-barcode"]').setValue(barcode);
  await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
}

async function next(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="next-step"]').trigger('click');
  await flushPromises();
}

async function upload(wrapper: VueWrapper, testId: string, fileName: string) {
  const input = wrapper.get(`[data-testid="${testId}"]`);
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [new File(['image'], fileName, { type: 'image/png' })]
  });
  await input.trigger('change');
  await flushPromises();
}

describe('Task 10 product editor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearMessages();
    vi.mocked(masterdataService.listCategories).mockResolvedValue(page([enabledCategory, disabledCategory]));
    vi.mocked(masterdataService.listSuppliers).mockResolvedValue(page([enabledSupplier, disabledSupplier]));
    vi.mocked(productService.uploadImage).mockImplementation(async (file) => ({
      id: file.name.includes('main') ? 201 : file.name.includes('sku') ? 202 : file.name.includes('package') ? 203 : 204,
      url: `/uploads/${file.name}`,
      originalFileName: file.name
    }));
    vi.mocked(productService.createProduct).mockResolvedValue(completeProduct({ id: 77 }));
    vi.mocked(productService.updateProduct).mockResolvedValue(completeProduct());
    vi.stubGlobal('confirm', vi.fn(() => true));
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn((file: File) => `blob:${file.name}`),
      revokeObjectURL: vi.fn()
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('moves through exactly six steps and submits a complete product', async () => {
    const { router, wrapper } = await mountEditor();

    expect(wrapper.get('[data-testid="editor-step-nav"]').findAll('button')).toHaveLength(6);
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('基础信息');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('SKU 信息');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('采购信息');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('包装/重量');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('图片资料');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('确认提交');

    await fillBasic(wrapper);
    await next(wrapper);
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    await addSku(wrapper, '白色款', '6970000000210');
    await next(wrapper);

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('采购信息');
    expect(wrapper.text()).not.toContain('渠道售价');
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    await wrapper.get('[data-testid="quote-supplier-0-0"]').setValue('4');
    await wrapper.get('[data-testid="quote-price-0-0"]').setValue('8.6');
    await wrapper.get('[data-testid="quote-min-quantity-0-0"]').setValue('24');
    await next(wrapper);

    await wrapper.get('[data-testid="unified-package-length"]').setValue('42');
    await wrapper.get('[data-testid="unified-inner-package-length"]').setValue('36');
    await wrapper.get('[data-testid="unified-net-weight"]').setValue('3.0');
    await wrapper.get('[data-testid="unified-gross-weight"]').setValue('3.1');
    await next(wrapper);

    await upload(wrapper, 'product-main-image', 'main.png');
    await upload(wrapper, 'sku-image-0', 'sku.png');
    await upload(wrapper, 'package-image-unified', 'package.png');
    await upload(wrapper, 'carton-image-unified', 'carton.png');
    await next(wrapper);

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('确认提交');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      itemNo: 'BBF-021',
      productName: '按压瓶',
      mainImageFileId: 201,
      skus: [expect.objectContaining({
        supplierQuotes: [expect.objectContaining({ supplierId: 4, defaultQuote: true })],
        packageLengthCm: 42,
        innerPackageLengthCm: 36,
        skuImageFileId: 202,
        packageImageFileId: 203,
        cartonImageFileId: 204
      })]
    }));
    expect(vi.mocked(productService.createProduct).mock.calls[0][0]).not.toHaveProperty('packagingMode');
    expect(router.currentRoute.value).toMatchObject({ name: 'product-detail', params: { id: '77' } });
  });

  it('loads every option page, preserves selected disabled options, and updates the loaded numeric id', async () => {
    vi.mocked(masterdataService.listCategories)
      .mockResolvedValueOnce(page([enabledCategory], 1, 2))
      .mockResolvedValueOnce(page([disabledCategory], 2, 2));
    vi.mocked(masterdataService.listSuppliers)
      .mockResolvedValueOnce(page([enabledSupplier], 1, 2))
      .mockResolvedValueOnce(page([disabledSupplier], 2, 2));
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { router, wrapper } = await mountEditor('/products/0042/edit');

    expect(masterdataService.listCategories).toHaveBeenNthCalledWith(1, { page: 1, size: 100 });
    expect(masterdataService.listCategories).toHaveBeenNthCalledWith(2, { page: 2, size: 100 });
    expect(masterdataService.listSuppliers).toHaveBeenNthCalledWith(1, { page: 1, size: 100 });
    expect(masterdataService.listSuppliers).toHaveBeenNthCalledWith(2, { page: 2, size: 100 });
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-name"]').element.value).toBe('玻璃按压瓶');
    expect(wrapper.get('[data-testid="product-category"]').text()).toContain('历史杯具（已停用）');
    await wrapper.get('[data-testid="product-name"]').setValue('更新后的玻璃按压瓶');

    for (let index = 0; index < 5; index += 1) await next(wrapper);
    expect(wrapper.text()).toContain('历史供应商');
    expect(wrapper.get('[data-testid="product-main-image-preview"]').attributes('src')).toBe('/uploads/main.png');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.updateProduct).toHaveBeenCalledWith(42, expect.objectContaining({
      productName: '更新后的玻璃按压瓶',
      mainImageFileId: 100,
      skus: [expect.objectContaining({
        id: 420,
        packageImageFileId: 102,
        supplierQuotes: [expect.objectContaining({ supplierId: 5 })]
      })]
    }));
    expect(productService.createProduct).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('product-detail');
  });

  it('commits and cancels SKU dialog drafts without leaking changes', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);

    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    const dialog = wrapper.get('[data-testid="sku-editor-dialog"]');
    const dialogBody = dialog.get('[data-testid="sku-dialog-body"]');
    const dialogFooter = dialog.get('[data-testid="sku-dialog-footer"]');
    const lastDialogInput = dialog.get('[data-testid="sku-dialog-safety-stock"]');
    expect(dialog.attributes('role')).toBe('dialog');
    expect(dialogBody.element.contains(dialogFooter.element)).toBe(false);
    expect(dialog.element.compareDocumentPosition(dialogFooter.element) & Node.DOCUMENT_POSITION_CONTAINED_BY).toBeTruthy();
    (lastDialogInput.element as HTMLInputElement).focus();
    expect(document.activeElement).toBe(lastDialogInput.element);
    await dialog.get('[data-testid="sku-dialog-sale-price"]').setValue('-1');
    await dialog.get('[data-testid="sku-dialog-name"]').setValue('数值无效');
    await dialog.get('[data-testid="sku-dialog-save"]').trigger('click');
    expect(dialog.get('[data-testid="sku-dialog-sale-price-error"]').text()).toContain('默认售价不能小于 0');
    expect(wrapper.find('[data-testid="sku-row-0"]').exists()).toBe(false);
    await dialog.get('[data-testid="sku-dialog-sale-price"]').setValue('0');
    await dialog.get('[data-testid="sku-dialog-name"]').setValue('不会保存');
    await dialog.get('[data-testid="sku-dialog-cancel"]').trigger('click');
    expect(wrapper.find('[data-testid="sku-row-0"]').exists()).toBe(false);

    await addSku(wrapper, '透明款', '6970001');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('透明款');
    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    await wrapper.get('[data-testid="sku-dialog-name"]').setValue('取消后的名称');
    await wrapper.get('[data-testid="sku-dialog-cancel"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('透明款');

    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    await wrapper.get('[data-testid="sku-dialog-name"]').setValue('烟灰款');
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('烟灰款');
  });

  it('groups quotes by SKU and keeps one default when adding, selecting, and removing quotes', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);

    expect(wrapper.get('[data-testid="procurement-sku-0"]').text()).toContain('透明款');
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    await wrapper.get('[data-testid="quote-supplier-0-0"]').setValue('4');
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-0"]').element.checked).toBe(true);
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    await wrapper.get('[data-testid="quote-supplier-0-1"]').setValue('4');
    await wrapper.get('[data-testid="quote-default-0-1"]').setValue(true);

    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-0"]').element.checked).toBe(false);
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-1"]').element.checked).toBe(true);
    await wrapper.get('[data-testid="remove-quote-0-1"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-0"]').element.checked).toBe(true);
  });

  it('starts quotes valid and reconciles defaults when quote status changes', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);

    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    await wrapper.get('[data-testid="quote-supplier-0-0"]').setValue('4');
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-price-0-0"]').element.value).toBe('0');
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-min-quantity-0-0"]').element.value).toBe('1');
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    await wrapper.get('[data-testid="quote-default-0-1"]').setValue(true);
    await wrapper.get('[data-testid="quote-status-0-1"]').setValue('disabled');

    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-0"]').element.checked).toBe(true);
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-1"]').element.checked).toBe(false);
    expect(wrapper.get<HTMLInputElement>('[data-testid="quote-default-0-1"]').element.disabled).toBe(true);
  });

  it('shows the invalid supplier field and stays on procurement', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');

    await next(wrapper);

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('采购信息');
    expect(wrapper.get('[data-testid="quote-supplier-error-0-0"]').text()).toContain('请选择供应商');
  });

  it('copies unified packaging independently and preserves per-SKU dialog differences', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await addSku(wrapper, '烟灰款', 'A-2');
    await next(wrapper);
    await next(wrapper);

    await wrapper.get('[data-testid="unified-package-length"]').setValue('42');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-1"]').trigger('click');
    const dialog = wrapper.get('[data-testid="packaging-editor-dialog"]');
    const dialogBody = dialog.get('[data-testid="packaging-dialog-body"]');
    const dialogFooter = dialog.get('[data-testid="packaging-dialog-footer"]');
    const lastDialogInput = dialog.get('[data-testid="packaging-dialog-method"]');
    expect(dialog.attributes('role')).toBe('dialog');
    expect(dialogBody.element.contains(dialogFooter.element)).toBe(false);
    expect(dialog.element.compareDocumentPosition(dialogFooter.element) & Node.DOCUMENT_POSITION_CONTAINED_BY).toBeTruthy();
    (lastDialogInput.element as HTMLInputElement).focus();
    expect(document.activeElement).toBe(lastDialogInput.element);
    await dialog.get('[data-testid="packaging-dialog-carton-quantity"]').setValue('0');
    await dialog.get('[data-testid="packaging-dialog-save"]').trigger('click');
    expect(dialog.get('[data-testid="packaging-dialog-carton-quantity-error"]').text()).toContain('装箱数必须大于 0');
    await dialog.get('[data-testid="packaging-dialog-carton-quantity"]').setValue('12');
    expect(dialog.get<HTMLInputElement>('[data-testid="packaging-dialog-package-length"]').element.value).toBe('42');
    await dialog.get('[data-testid="packaging-dialog-package-length"]').setValue('44');
    await dialog.get('[data-testid="packaging-dialog-cancel"]').trigger('click');

    await wrapper.get('[data-testid="edit-packaging-1"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-package-length"]').element.value).toBe('42');
    await wrapper.get('[data-testid="packaging-dialog-package-length"]').setValue('44');
    await wrapper.get('[data-testid="packaging-dialog-save"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-row-1"]').text()).toContain('44');
    expect(wrapper.get('[data-testid="packaging-row-0"]').text()).toContain('42');

    await wrapper.get('[data-testid="packaging-mode-unified"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-row-0"]').text()).toContain('42');
    expect(wrapper.get('[data-testid="packaging-row-1"]').text()).toContain('42');
  });

  it('inherits unified packaging data and matching previews when a SKU is added later', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);
    await next(wrapper);
    await wrapper.get('[data-testid="unified-package-length"]').setValue('42');
    await next(wrapper);
    await upload(wrapper, 'package-image-unified', 'package.png');

    await wrapper.get('[data-testid="step-sku"]').trigger('click');
    await addSku(wrapper, '烟灰款', 'A-2');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-row-1"]').text()).toContain('42');

    await wrapper.get('[data-testid="step-images"]').trigger('click');
    expect(wrapper.get('[data-testid="package-image-1-preview"]').attributes('src')).toBe('/uploads/package.png');
  });

  it('keeps previews through upload errors, removes associations, and revokes blob urls', async () => {
    vi.mocked(productService.uploadImage)
      .mockResolvedValueOnce({ id: 201, url: '/uploads/main.png', originalFileName: 'main.png' })
      .mockRejectedValueOnce(new Error('SKU 图片上传失败'));
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);
    await next(wrapper);
    await next(wrapper);

    await upload(wrapper, 'product-main-image', 'main.png');
    expect(wrapper.get('[data-testid="product-main-image-preview"]').attributes('src')).toBe('/uploads/main.png');
    expect(wrapper.get('[data-testid="product-main-image-status"]').text()).toContain('上传成功');
    await upload(wrapper, 'sku-image-0', 'sku-failed.png');
    expect(wrapper.get('[data-testid="sku-image-0-status"]').text()).toContain('上传失败');
    expect(wrapper.text()).toContain('SKU 图片上传失败');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:sku-failed.png');

    await wrapper.get('[data-testid="product-main-image-remove"]').trigger('click');
    expect(wrapper.find('[data-testid="product-main-image-preview"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-main-image-status"]').text()).toContain('未上传');
  });

  it('blocks advancing and submission while an image upload is pending', async () => {
    const pending = deferred<{ id: number; url: string; originalFileName: string }>();
    vi.mocked(productService.uploadImage).mockReturnValueOnce(pending.promise);
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);
    await next(wrapper);
    await next(wrapper);

    const input = wrapper.get('[data-testid="product-main-image"]');
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'pending-main.png', { type: 'image/png' })]
    });
    await input.trigger('change');

    expect(wrapper.get('[data-testid="image-upload-blocker"]').text()).toContain('图片正在上传');
    expect(wrapper.get<HTMLButtonElement>('[data-testid="next-step"]').element.disabled).toBe(true);
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('图片资料');
    expect(productService.createProduct).not.toHaveBeenCalled();
    expect(productService.updateProduct).not.toHaveBeenCalled();

    pending.resolve({ id: 201, url: '/uploads/main.png', originalFileName: 'pending-main.png' });
    await flushPromises();
    expect(wrapper.find('[data-testid="image-upload-blocker"]').exists()).toBe(false);
    expect(wrapper.get<HTMLButtonElement>('[data-testid="next-step"]').element.disabled).toBe(false);
  });

  it('guards only real unsaved changes for route and browser navigation', async () => {
    const { router, wrapper } = await mountEditor();
    const confirm = vi.mocked(window.confirm);

    await router.push({ name: 'products' });
    expect(confirm).not.toHaveBeenCalled();

    await router.push({ name: 'product-new' });
    await flushPromises();
    await wrapper.get('[data-testid="product-name"]').setValue('未保存商品');
    confirm.mockReturnValueOnce(false);
    await router.push({ name: 'products' });
    expect(router.currentRoute.value.name).toBe('product-new');
    expect(confirm).toHaveBeenCalledTimes(1);

    const unloadEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unloadEvent);
    expect(unloadEvent.defaultPrevented).toBe(true);

    confirm.mockReturnValueOnce(true);
    await wrapper.get('[data-testid="cancel-product"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('products');
  });

  it('does not dirty or prompt for a UI-only packaging mode change', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { router, wrapper } = await mountEditor('/products/42/edit');
    const confirm = vi.mocked(window.confirm);

    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');

    expect(wrapper.text()).not.toContain('有未保存更改');
    await router.push({ name: 'products' });
    expect(confirm).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('products');
  });

  it('does not prompt during successful save navigation', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { router, wrapper } = await mountEditor('/products/42/edit');
    const confirm = vi.mocked(window.confirm);
    await wrapper.get('[data-testid="product-name"]').setValue('已保存商品');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.updateProduct).toHaveBeenCalled();
    expect(confirm).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('product-detail');
  });

  it('loads a same-route product id change after the dirty guard is accepted', async () => {
    const nextProduct = deferred<Product>();
    vi.mocked(productService.getProduct).mockImplementation((id) => (
      id === 42 ? Promise.resolve(completeProduct()) : nextProduct.promise
    ));
    const { router, wrapper } = await mountEditor('/products/42/edit');
    const confirm = vi.mocked(window.confirm);
    confirm.mockReturnValue(true);
    await wrapper.get('[data-testid="product-name"]').setValue('未保存旧商品');

    await router.push('/products/43/edit');
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="product-editor-loading"]').exists()).toBe(true);
    nextProduct.resolve(completeProduct({ id: 43, productName: '新商品' }));
    await flushPromises();

    expect(wrapper.get<HTMLInputElement>('[data-testid="product-name"]').element.value).toBe('新商品');
    expect(router.currentRoute.value.params.id).toBe('43');
  });

  it('returns to the first invalid step and keeps the current step when saving fails', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    vi.mocked(productService.updateProduct).mockRejectedValue(new Error('保存接口暂不可用'));
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('确认提交');
    expect(messages.value.at(-1)).toMatchObject({ type: 'error', text: '保存接口暂不可用' });

    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await wrapper.get('[data-testid="product-name"]').setValue('');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('基础信息');
    expect(wrapper.text()).toContain('请输入商品名称');
  });

  it('returns final validation to the exact first invalid backend field without calling the API', async () => {
    const base = completeProduct();
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct({
      skus: [{
        ...base.skus[0],
        defaultSalePrice: -0.01,
        supplierQuotes: [{ ...base.skus[0].supplierQuotes[0], minPurchaseQuantity: 0 }]
      }]
    }));
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="step-confirm"]').trigger('click');

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('默认售价不能小于 0');
    expect(productService.updateProduct).not.toHaveBeenCalled();
    expect(productService.createProduct).not.toHaveBeenCalled();
  });

  it('shows safe loading, invalid-id, lookup-error, and newest-route states', async () => {
    const pending = deferred<Product>();
    vi.mocked(productService.getProduct).mockReturnValueOnce(pending.promise);
    const loading = await mountEditor('/products/42/edit');
    expect(loading.wrapper.find('[data-testid="product-editor-loading"]').exists()).toBe(true);
    loading.wrapper.unmount();
    pending.resolve(completeProduct());
    await flushPromises();

    vi.mocked(productService.getProduct).mockClear();
    const invalid = await mountEditor('/products/not-a-number/edit');
    expect(productService.getProduct).not.toHaveBeenCalled();
    expect(invalid.wrapper.get('[data-testid="product-editor-error"]').text()).toContain('商品编号无效');
    expect(invalid.wrapper.find('[data-testid="product-editor-back"]').exists()).toBe(true);
    invalid.wrapper.unmount();

    vi.mocked(masterdataService.listCategories).mockRejectedValueOnce(new Error('接口服务暂不可用'));
    vi.mocked(masterdataService.listSuppliers).mockRejectedValueOnce(new Error('接口服务暂不可用'));
    const lookupError = await mountEditor();
    const optionError = lookupError.wrapper.get('[data-testid="product-editor-option-error"]').text();
    expect(optionError).toContain('接口服务暂不可用');
    expect(optionError.match(/接口服务暂不可用/g)).toHaveLength(1);
    expect(lookupError.wrapper.find('[data-testid="retry-editor-options"]').exists()).toBe(true);
    lookupError.wrapper.unmount();

    const first = deferred<Product>();
    const second = deferred<Product>();
    vi.mocked(productService.getProduct).mockImplementation((id) => id === 42 ? first.promise : second.promise);
    const race = await mountEditor('/products/42/edit');
    await race.router.push('/products/43/edit');
    second.resolve(completeProduct({ id: 43, productName: '新路由商品' }));
    await flushPromises();
    first.resolve(completeProduct());
    await flushPromises();
    expect(race.wrapper.get<HTMLInputElement>('[data-testid="product-name"]').element.value).toBe('新路由商品');
  });
});
