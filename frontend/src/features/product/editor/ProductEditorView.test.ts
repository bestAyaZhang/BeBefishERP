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
    productType: 'variant',
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
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('基本资料');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('SKU 信息');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('采购与渠道');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('包装与重量');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('图片资料');
    expect(wrapper.get('[data-testid="editor-step-nav"]').text()).toContain('确认提交');

    await fillBasic(wrapper);
    await next(wrapper);
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    await addSku(wrapper, '白色款', '6970000000210');
    await next(wrapper);

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('采购与渠道');
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
      productType: 'simple',
      status: 'enabled',
      mainImageFileId: 201,
      skus: [expect.objectContaining({
        defaultSku: true,
        status: 'enabled',
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

  it('renders the edit route with the six-step Figma desktop wizard layout', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');

    const wizard = wrapper.get('[data-testid="product-editor-wizard"]');
    expect(wizard.classes()).toContain('h-[904px]');
    expect(wizard.get('[data-testid="product-editor-header"]').classes()).toContain('h-16');
    expect(wizard.get('h1').text()).toBe('编辑商品');
    expect(wizard.get('[data-testid="product-editor-subtitle"]').text()).toContain('第 1 步，共 6 步');

    const stepper = wizard.get('[data-testid="editor-step-nav"]');
    expect(stepper.classes()).toContain('h-[92px]');
    expect(stepper.findAll('button')).toHaveLength(6);
    expect(stepper.findAll('button').map((button) => button.text())).toEqual(expect.arrayContaining([
      expect.stringContaining('基本资料'),
      expect.stringContaining('SKU 信息'),
      expect.stringContaining('采购与渠道'),
      expect.stringContaining('包装与重量'),
      expect.stringContaining('图片资料'),
      expect.stringContaining('确认提交')
    ]));

    const stage = wizard.get('[data-testid="product-editor-stage"]');
    expect(stage.classes()).toContain('h-[620px]');
    expect(stage.get('[data-testid="editor-step-title"]').text()).toBe('基本资料');

    const footer = wizard.get('[data-testid="product-editor-footer"]');
    expect(footer.classes()).toContain('h-20');
    expect(footer.get('[data-testid="cancel-product"]').text()).toBe('取消');
    expect(footer.get('[data-testid="save-product-draft"]').text()).toBe('保存草稿');
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-name"]').element.value).toBe('玻璃按压瓶');
  });

  it('saves explicit product type and product status controls', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await wrapper.get('[data-testid="product-type"]').setValue('variant');
    await wrapper.get('[data-testid="product-status"]').setValue('disabled');
    await next(wrapper);
    await addSku(wrapper, '透明款', '6970001');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productType: 'variant',
      status: 'disabled'
    }));
  });

  it('keeps SKU item number and barcode distinct through create and edit', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    await wrapper.get('[data-testid="sku-dialog-name"]').setValue('透明款');
    await wrapper.get('[data-testid="sku-dialog-sku-code"]').setValue('SKU-ITEM-021');
    await wrapper.get('[data-testid="sku-dialog-barcode"]').setValue('6970000000210');
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');

    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('SKU-ITEM-021');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('6970000000210');
    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="sku-dialog-sku-code"]').element.value).toBe('SKU-ITEM-021');
    expect(wrapper.get<HTMLInputElement>('[data-testid="sku-dialog-barcode"]').element.value).toBe('6970000000210');
    await wrapper.get('[data-testid="sku-dialog-sku-code"]').setValue('SKU-ITEM-021-B');
    await wrapper.get('[data-testid="sku-dialog-barcode"]').setValue('6970000000211');
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      skus: [expect.objectContaining({
        skuCode: 'SKU-ITEM-021-B',
        barcode: '6970000000211'
      })]
    }));
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
    await wrapper.get('[data-testid="product-status"]').setValue('disabled');

    for (let index = 0; index < 5; index += 1) await next(wrapper);
    expect(wrapper.text()).toContain('历史供应商');
    expect(wrapper.get('[data-testid="product-main-image-preview"]').attributes('src')).toBe('/uploads/main.png');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.updateProduct).toHaveBeenCalledWith(42, expect.objectContaining({
      productName: '更新后的玻璃按压瓶',
      productType: 'variant',
      status: 'disabled',
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

  it('lets a loaded variant enter SKU cleanup after switching to simple and blocks submission until reconciled', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="product-type"]').setValue('simple');
    await next(wrapper);
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');

    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    expect(wrapper.text()).toContain('请删除多余 SKU，并编辑保留的 SKU 清空 SKU 规格值；若仍有 SKU 规格定义，请返回基础信息删除。');
    expect(productService.createProduct).not.toHaveBeenCalled();
    expect(productService.updateProduct).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    await wrapper.get('[data-testid="sku-dialog-specification"]').setValue('');
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
    await wrapper.get('[data-testid="step-basic"]').trigger('click');
    await wrapper.get('button[aria-label="删除规格 1"]').trigger('click');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.updateProduct).toHaveBeenCalledWith(42, expect.objectContaining({
      productType: 'simple',
      specifications: [],
      skus: [expect.objectContaining({ specificationValues: [] })]
    }));
  });

  it('renders an SKU error and keeps exponent-form numeric input away from save APIs', async () => {
    const product = completeProduct();
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct({
      skus: [{ ...product.skus[0], defaultSalePrice: 1e21 }]
    }));
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="step-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    await wrapper.get('[data-testid="sku-dialog-sale-price"]').setValue('1e21');
    await wrapper.get('[data-testid="sku-dialog-cancel"]').trigger('click');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    if (wrapper.find('[data-testid="submit-product"]').exists()) {
      await wrapper.get('[data-testid="submit-product"]').trigger('click');
      await flushPromises();
    }

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('默认售价最多允许 15 位整数和 4 位小数');
    expect(productService.createProduct).not.toHaveBeenCalled();
    expect(productService.updateProduct).not.toHaveBeenCalled();
  });

  it('traps modal focus, closes on Escape, and restores the SKU opener focus', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    const opener = wrapper.get('[data-testid="add-sku"]');
    (opener.element as HTMLButtonElement).focus();

    await opener.trigger('click');
    await flushPromises();
    const dialog = wrapper.get('[data-testid="sku-editor-dialog"]');
    const close = dialog.get('[data-testid="sku-dialog-close"]');
    const save = dialog.get('[data-testid="sku-dialog-save"]');
    expect(document.activeElement).toBe(close.element);

    await close.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(save.element);
    await save.trigger('keydown', { key: 'Tab' });
    expect(document.activeElement).toBe(close.element);

    await dialog.trigger('keydown', { key: 'Escape' });
    await flushPromises();
    expect(wrapper.find('[data-testid="sku-editor-dialog"]').exists()).toBe(false);
    expect(document.activeElement).toBe(opener.element);
  });

  it('renders persisted SKU default/status and reconciles default selection after disable and delete', async () => {
    const base = completeProduct();
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct({
      skus: [
        { ...base.skus[0], id: 420, skuName: '启用款', defaultSku: true, status: 'enabled' },
        { ...base.skus[0], id: 421, skuCode: 'BBF-042-002', skuName: '停用款', defaultSku: false, status: 'disabled' }
      ]
    }));
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-sku"]').trigger('click');

    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('默认 SKU');
    expect(wrapper.get('[data-testid="sku-row-1"]').text()).toContain('已停用');
    await wrapper.get('[data-testid="edit-sku-1"]').trigger('click');
    expect(wrapper.get<HTMLSelectElement>('[data-testid="sku-dialog-status"]').element.value).toBe('disabled');
    await wrapper.get('[data-testid="sku-dialog-status"]').setValue('enabled');
    await wrapper.get('[data-testid="sku-dialog-default"]').setValue(true);
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-row-1"]').text()).toContain('默认 SKU');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).not.toContain('默认 SKU');

    await wrapper.get('[data-testid="remove-sku-1"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-row-0"]').text()).toContain('默认 SKU');
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

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('采购与渠道');
    expect(wrapper.get('[data-testid="quote-supplier-error-0-0"]').text()).toContain('请选择供应商');
  });

  it('copies unified packaging independently and preserves per-SKU dialog differences', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await wrapper.get('[data-testid="product-type"]').setValue('variant');
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
    expect(dialog.get('[data-testid="packaging-dialog-carton-quantity-error"]').text()).toContain('装箱数必须为 1 到 2147483647 之间的整数');
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
    expect(wrapper.get('[data-testid="packaging-unify-confirm-dialog"]').text()).toContain('覆盖');
    await wrapper.get('[data-testid="packaging-unify-cancel"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-row-0"]').text()).toContain('42');
    expect(wrapper.get('[data-testid="packaging-row-1"]').text()).toContain('44');

    await wrapper.get('[data-testid="packaging-mode-unified"]').trigger('click');
    await wrapper.get('[data-testid="packaging-unify-confirm"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-row-0"]').text()).toContain('42');
    expect(wrapper.get('[data-testid="packaging-row-1"]').text()).toContain('42');
  });

  it('edits product dimensions and capacity in unified and per-SKU packaging modes', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await wrapper.get('[data-testid="product-type"]').setValue('variant');
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await addSku(wrapper, '烟灰款', 'A-2');
    await next(wrapper);
    await next(wrapper);

    expect(wrapper.text()).toContain('产品尺寸与容量');
    expect(wrapper.get('[data-testid="packaging-product-length-unit"]').text()).toBe('cm');
    expect(wrapper.get('[data-testid="packaging-capacity-unit"]').text()).toBe('ml');
    expect(wrapper.findAll('[data-testid="packaging-product-dimension-separator"]')).toHaveLength(2);
    for (const testId of ['packaging-product-length', 'packaging-product-width', 'packaging-product-height', 'packaging-capacity']) {
      expect(wrapper.get(`[data-testid="${testId}"]`).attributes()).toMatchObject({ min: '0', max: '999999999.999', step: '0.001' });
    }

    await wrapper.get('[data-testid="packaging-product-length"]').setValue('12.5');
    await wrapper.get('[data-testid="packaging-product-width"]').setValue('8.25');
    await wrapper.get('[data-testid="packaging-product-height"]').setValue('20');
    await wrapper.get('[data-testid="packaging-capacity"]').setValue('450');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');

    await wrapper.get('[data-testid="edit-packaging-1"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-length"]').element.value).toBe('12.5');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-width"]').element.value).toBe('8.25');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-height"]').element.value).toBe('20');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-capacity"]').element.value).toBe('450');
    expect(wrapper.get('[data-testid="packaging-dialog-product-length-unit"]').text()).toBe('cm');
    expect(wrapper.get('[data-testid="packaging-dialog-capacity-unit"]').text()).toBe('ml');
    expect(wrapper.findAll('[data-testid="packaging-dialog-product-dimension-separator"]')).toHaveLength(2);
    for (const testId of ['packaging-dialog-product-length', 'packaging-dialog-product-width', 'packaging-dialog-product-height', 'packaging-dialog-capacity']) {
      expect(wrapper.get(`[data-testid="${testId}"]`).attributes()).toMatchObject({ min: '0', max: '999999999.999', step: '0.001' });
    }

    await wrapper.get('[data-testid="packaging-dialog-product-length"]').setValue('13.5');
    await wrapper.get('[data-testid="packaging-dialog-product-width"]').setValue('9.25');
    await wrapper.get('[data-testid="packaging-dialog-product-height"]').setValue('21');
    await wrapper.get('[data-testid="packaging-dialog-capacity"]').setValue('500');
    await wrapper.get('[data-testid="packaging-dialog-save"]').trigger('click');

    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-length"]').element.value).toBe('12.5');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-width"]').element.value).toBe('8.25');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-height"]').element.value).toBe('20');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-capacity"]').element.value).toBe('450');
    await wrapper.get('[data-testid="packaging-dialog-cancel"]').trigger('click');

    await wrapper.get('[data-testid="edit-packaging-1"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-length"]').element.value).toBe('13.5');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-width"]').element.value).toBe('9.25');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-product-height"]').element.value).toBe('21');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-capacity"]').element.value).toBe('500');
  });

  it('normalizes a cleared product physical input to null', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await next(wrapper);
    await next(wrapper);

    await wrapper.get('[data-testid="packaging-capacity"]').setValue('450');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-capacity"]').element.value).toBe('450');
    await wrapper.get('[data-testid="packaging-dialog-cancel"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-unified"]').trigger('click');
    await wrapper.get('[data-testid="packaging-capacity"]').setValue('');

    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    await flushPromises();

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      skus: [expect.objectContaining({ capacityMl: null })]
    }));
  });

  it('keeps invalid product physical values in the dialog with visible field errors', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');

    await wrapper.get('[data-testid="packaging-dialog-product-length"]').setValue('-1');
    await wrapper.get('[data-testid="packaging-dialog-product-width"]').setValue('-1');
    await wrapper.get('[data-testid="packaging-dialog-product-height"]').setValue('-1');
    await wrapper.get('[data-testid="packaging-dialog-capacity"]').setValue('-1');
    await wrapper.get('[data-testid="packaging-dialog-save"]').trigger('click');

    expect(wrapper.get('[data-testid="packaging-dialog-product-length-error"]').text()).toContain('产品长不能小于 0');
    expect(wrapper.get('[data-testid="packaging-dialog-product-width-error"]').text()).toContain('产品宽不能小于 0');
    expect(wrapper.get('[data-testid="packaging-dialog-product-height-error"]').text()).toContain('产品高不能小于 0');
    expect(wrapper.get('[data-testid="packaging-dialog-capacity-error"]').text()).toContain('容量不能小于 0');
    expect(wrapper.find('[data-testid="packaging-editor-dialog"]').exists()).toBe(true);
  });

  it('sets Java integer constraints on the unified carton quantity input', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');

    const input = wrapper.get('[data-testid="unified-carton-quantity"]');
    expect(input.attributes()).toMatchObject({ min: '1', step: '1', max: '2147483647' });
  });

  it('shows a unit suffix after every unit-bearing editor input', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="step-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-sku-0"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-dialog-sale-price-unit"]').text()).toBe('元');
    await wrapper.get('[data-testid="sku-dialog-cancel"]').trigger('click');

    await wrapper.get('[data-testid="step-procurement"]').trigger('click');
    expect(wrapper.get('[data-testid="primary-quote-price-unit"]').text()).toBe('元');
    expect(wrapper.get('[data-testid="primary-quote-min-quantity-unit"]').text()).toBe('只');
    expect(wrapper.get('[data-testid="quote-price-unit-0-0"]').text()).toBe('元');
    expect(wrapper.get('[data-testid="quote-min-quantity-unit-0-0"]').text()).toBe('只');

    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    expect(wrapper.get('[data-testid="unified-package-length-unit"]').text()).toBe('cm');
    expect(wrapper.get('[data-testid="unified-package-volume-unit"]').text()).toBe('cm³');
    expect(wrapper.get('[data-testid="unified-carton-quantity-unit"]').text()).toBe('只');
    expect(wrapper.get('[data-testid="unified-gram-weight-unit"]').text()).toBe('g');
    expect(wrapper.get('[data-testid="unified-net-weight-unit"]').text()).toBe('kg');

    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');
    expect(wrapper.get('[data-testid="packaging-dialog-package-length-unit"]').text()).toBe('cm');
    expect(wrapper.get('[data-testid="packaging-dialog-carton-quantity-unit"]').text()).toBe('只');
    expect(wrapper.get('[data-testid="packaging-dialog-gram-weight-unit"]').text()).toBe('g');
    expect(wrapper.get('[data-testid="packaging-dialog-net-weight-unit"]').text()).toBe('kg');
  });

  it('keeps length width and height identifiable with multiplication separators', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');

    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    const outerDimensions = wrapper.get('[data-testid="unified-package-dimensions"]');
    expect(outerDimensions.findAll('[data-dimension-label]').map((label) => label.text())).toEqual(['长', '宽', '高']);
    expect(outerDimensions.findAll('[data-dimension-separator]').map((separator) => separator.text())).toEqual(['×', '×']);

    const innerDimensions = wrapper.get('[data-testid="unified-inner-package-dimensions"]');
    expect(innerDimensions.findAll('[data-dimension-label]').map((label) => label.text())).toEqual(['长', '宽', '高']);
    expect(innerDimensions.findAll('[data-dimension-separator]').map((separator) => separator.text())).toEqual(['×', '×']);

    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');
    const dialogDimensions = wrapper.get('[data-testid="packaging-dialog-package-dimensions"]');
    expect(dialogDimensions.findAll('[data-dimension-label]').map((label) => label.text())).toEqual(['长', '宽', '高']);
    expect(dialogDimensions.findAll('[data-dimension-separator]').map((separator) => separator.text())).toEqual(['×', '×']);
  });

  it('sets schema and safe-precision constraints on editor decimal inputs', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await wrapper.get('[data-testid="add-sku"]').trigger('click');
    expect(wrapper.get('[data-testid="sku-dialog-sale-price"]').attributes()).toMatchObject({
      step: '0.0001',
      max: '900719925474.0991'
    });
    await wrapper.get('[data-testid="sku-dialog-name"]').setValue('透明款');
    await wrapper.get('[data-testid="sku-dialog-save"]').trigger('click');
    await next(wrapper);
    await wrapper.get('[data-testid="add-quote-0"]').trigger('click');
    expect(wrapper.get('[data-testid="quote-price-0-0"]').attributes()).toMatchObject({
      step: '0.0001',
      max: '900719925474.0991'
    });
    expect(wrapper.get('[data-testid="quote-min-quantity-0-0"]').attributes()).toMatchObject({
      step: '0.0001',
      max: '900719925474.0991'
    });
    await wrapper.get('[data-testid="quote-supplier-0-0"]').setValue('4');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    expect(wrapper.get('[data-testid="unified-package-length"]').attributes()).toMatchObject({
      step: '0.001',
      max: '999999999.999'
    });
    expect(wrapper.get('[data-testid="unified-package-volume"]').attributes()).toMatchObject({
      step: '0.001',
      max: '9007199254740.991'
    });
  });

  it('sets Java integer constraints on the per-SKU carton quantity input', async () => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');

    const input = wrapper.get('[data-testid="packaging-dialog-carton-quantity"]');
    expect(input.attributes()).toMatchObject({ min: '1', step: '1', max: '2147483647' });
  });

  it.each(['1.5', '2147483648'])('rejects per-SKU carton quantity %s without committing the dialog', async (cartonQuantity) => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');

    await wrapper.get('[data-testid="packaging-dialog-carton-quantity"]').setValue(cartonQuantity);
    await wrapper.get('[data-testid="packaging-dialog-save"]').trigger('click');

    expect(wrapper.get('[data-testid="packaging-dialog-carton-quantity-error"]').text()).toContain('装箱数必须为 1 到 2147483647 之间的整数');
    expect(wrapper.find('[data-testid="packaging-editor-dialog"]').exists()).toBe(true);
  });

  it.each(['1', '2147483647'])('commits valid per-SKU carton quantity %s', async (cartonQuantity) => {
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="packaging-mode-per-sku"]').trigger('click');
    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');

    await wrapper.get('[data-testid="packaging-dialog-carton-quantity"]').setValue(cartonQuantity);
    await wrapper.get('[data-testid="packaging-dialog-save"]').trigger('click');
    expect(wrapper.find('[data-testid="packaging-editor-dialog"]').exists()).toBe(false);

    await wrapper.get('[data-testid="edit-packaging-0"]').trigger('click');
    expect(wrapper.get<HTMLInputElement>('[data-testid="packaging-dialog-carton-quantity"]').element.value).toBe(cartonQuantity);
  });

  it.each([
    { path: '/products/new', cartonQuantity: '1.5', operation: 'create' },
    { path: '/products/42/edit', cartonQuantity: '2147483648', operation: 'update' }
  ])('keeps invalid carton quantity $cartonQuantity away from the $operation API', async ({ path, cartonQuantity }) => {
    if (path.includes('/edit')) vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    const { wrapper } = await mountEditor(path);
    if (path === '/products/new') {
      await fillBasic(wrapper);
      await next(wrapper);
      await addSku(wrapper, '透明款', 'A-1');
    }
    await wrapper.get('[data-testid="step-packaging"]').trigger('click');
    await wrapper.get('[data-testid="unified-carton-quantity"]').setValue(cartonQuantity);
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    if (wrapper.find('[data-testid="submit-product"]').exists()) {
      await wrapper.get('[data-testid="submit-product"]').trigger('click');
      await flushPromises();
    }

    expect(productService.createProduct).not.toHaveBeenCalled();
    expect(productService.updateProduct).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('包装与重量');
    expect(wrapper.text()).toContain('装箱数必须为 1 到 2147483647 之间的整数');
  });

  it('inherits unified packaging data and matching previews when a SKU is added later', async () => {
    const { wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await wrapper.get('[data-testid="product-type"]').setValue('variant');
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

  it('blocks every step, route, and browser exit while an upload is active', async () => {
    const pending = deferred<{ id: number; url: string; originalFileName: string }>();
    vi.mocked(productService.uploadImage).mockReturnValueOnce(pending.promise);
    const { router, wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await wrapper.get('[data-testid="step-images"]').trigger('click');
    const input = wrapper.get('[data-testid="product-main-image"]');
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'pending-main.png', { type: 'image/png' })]
    });
    await input.trigger('change');

    await wrapper.get('[data-testid="step-basic"]').trigger('click');
    await wrapper.get('[data-testid="previous-step"]').trigger('click');
    await router.push({ name: 'products' });
    const unloadEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unloadEvent);

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('图片资料');
    expect(router.currentRoute.value.name).toBe('product-new');
    expect(unloadEvent.defaultPrevented).toBe(true);
    expect(messages.value.at(-1)?.text).toContain('图片正在上传');

    pending.resolve({ id: 201, url: '/uploads/main.png', originalFileName: 'pending-main.png' });
    await flushPromises();
  });

  it('blocks route and browser exits while saving, then permits its own successful detail navigation', async () => {
    const pending = deferred<Product>();
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    vi.mocked(productService.updateProduct).mockReturnValueOnce(pending.promise);
    const { router, wrapper } = await mountEditor('/products/42/edit');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');

    await router.push({ name: 'products' });
    const unloadEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unloadEvent);
    expect(router.currentRoute.value.name).toBe('product-edit');
    expect(unloadEvent.defaultPrevented).toBe(true);
    expect(messages.value.at(-1)?.text).toContain('商品正在保存');

    pending.resolve(completeProduct());
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('product-detail');
  });

  it('ignores late save success and failure callbacks after unmount', async () => {
    const success = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(success.promise);
    const first = await mountEditor();
    await fillBasic(first.wrapper);
    await next(first.wrapper);
    await addSku(first.wrapper, '透明款', 'A-1');
    await first.wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await first.wrapper.get('[data-testid="submit-product"]').trigger('click');
    const firstPush = vi.spyOn(first.router, 'push');
    first.wrapper.unmount();
    firstPush.mockClear();
    clearMessages();
    success.resolve(completeProduct({ id: 77 }));
    await flushPromises();
    expect(firstPush).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);

    const failure = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(failure.promise);
    const second = await mountEditor();
    await fillBasic(second.wrapper);
    await next(second.wrapper);
    await addSku(second.wrapper, '透明款', 'A-2');
    await second.wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await second.wrapper.get('[data-testid="submit-product"]').trigger('click');
    const secondPush = vi.spyOn(second.router, 'push');
    second.wrapper.unmount();
    secondPush.mockClear();
    clearMessages();
    failure.reject(new Error('迟到的失败'));
    await flushPromises();
    expect(secondPush).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);
  });

  it('ignores late save success and failure after an external route identity change', async () => {
    const success = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(success.promise);
    const first = await mountEditor();
    await fillBasic(first.wrapper);
    await next(first.wrapper);
    await addSku(first.wrapper, '透明款', 'A-1');
    await first.wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await first.wrapper.get('[data-testid="submit-product"]').trigger('click');
    const firstPush = vi.spyOn(first.router, 'push');
    const firstRoute = first.router.currentRoute as unknown as { value: typeof first.router.currentRoute.value };
    firstRoute.value = {
      ...firstRoute.value,
      fullPath: '/products/new?revision=2',
      query: { revision: '2' }
    };
    await flushPromises();
    firstPush.mockClear();
    clearMessages();
    success.resolve(completeProduct({ id: 77 }));
    await flushPromises();
    expect(firstPush).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);

    const failure = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(failure.promise);
    const second = await mountEditor();
    await fillBasic(second.wrapper);
    await next(second.wrapper);
    await addSku(second.wrapper, '透明款', 'A-2');
    await second.wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await second.wrapper.get('[data-testid="submit-product"]').trigger('click');
    const secondPush = vi.spyOn(second.router, 'push');
    const secondRoute = second.router.currentRoute as unknown as { value: typeof second.router.currentRoute.value };
    secondRoute.value = {
      ...secondRoute.value,
      fullPath: '/products/new?revision=3',
      query: { revision: '3' }
    };
    await flushPromises();
    secondPush.mockClear();
    clearMessages();
    failure.reject(new Error('迟到的路由失败'));
    await flushPromises();
    expect(secondPush).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);
  });

  it('permanently invalidates a pending save after an A-to-B-to-A route cycle', async () => {
    const pending = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(pending.promise);
    const { router, wrapper } = await mountEditor();
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    const push = vi.spyOn(router, 'push');
    const routeRef = router.currentRoute as unknown as { value: typeof router.currentRoute.value };

    routeRef.value = {
      ...routeRef.value,
      fullPath: '/products/new?revision=2',
      query: { revision: '2' }
    };
    routeRef.value = {
      ...routeRef.value,
      fullPath: '/products/new',
      query: {}
    };
    await flushPromises();
    push.mockClear();
    clearMessages();

    pending.resolve(completeProduct({ id: 77 }));
    await flushPromises();

    expect(push).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);
  });

  it('releases stale saving state on route identity change and keeps the late callback silent', async () => {
    const pending = deferred<Product>();
    vi.mocked(productService.createProduct).mockReturnValueOnce(pending.promise);
    const { router, wrapper } = await mountEditor();
    const confirm = vi.mocked(window.confirm);
    await fillBasic(wrapper);
    await next(wrapper);
    await addSku(wrapper, '透明款', 'A-1');
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    const push = vi.spyOn(router, 'push');
    const routeRef = router.currentRoute as unknown as { value: typeof router.currentRoute.value };

    routeRef.value = {
      ...routeRef.value,
      fullPath: '/products/new?revision=2',
      query: { revision: '2' }
    };
    await flushPromises();
    push.mockClear();
    clearMessages();

    pending.resolve(completeProduct({ id: 77 }));
    await flushPromises();

    expect(push).not.toHaveBeenCalled();
    expect(messages.value).toHaveLength(0);
    expect(confirm).not.toHaveBeenCalled();

    await fillBasic(wrapper);
    clearMessages();
    await wrapper.get('[data-testid="step-sku"]').trigger('click');

    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
    expect(messages.value.map((entry) => entry.text)).not.toContain('商品正在保存，请等待完成后再离开');
  });

  it('does not let an old navigation finally clear a newer save authorization', async () => {
    const firstSave = deferred<Product>();
    const secondSave = deferred<Product>();
    const firstNavigationStarted = deferred<void>();
    const secondNavigationStarted = deferred<void>();
    const releaseFirstNavigation = deferred<void>();
    const releaseSecondNavigation = deferred<void>();
    vi.mocked(productService.getProduct).mockImplementation(async (id) => completeProduct({ id }));
    vi.mocked(productService.updateProduct).mockImplementation((id) => (
      id === 42 ? firstSave.promise : secondSave.promise
    ));
    const { router, wrapper } = await mountEditor('/products/42/edit');
    const confirm = vi.mocked(window.confirm);
    const originalPush = router.push.bind(router);
    vi.spyOn(router, 'push').mockImplementation(async (to) => {
      const target = router.resolve(to);
      if (target.name === 'product-detail' && target.params.id === '42') {
        firstNavigationStarted.resolve(undefined);
        await releaseFirstNavigation.promise;
        return;
      }
      if (target.name === 'product-detail' && target.params.id === '43') {
        secondNavigationStarted.resolve(undefined);
        await releaseSecondNavigation.promise;
      }
      return originalPush(to);
    });

    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    firstSave.resolve(completeProduct({ id: 42 }));
    await firstNavigationStarted.promise;

    const routeRef = router.currentRoute as unknown as { value: typeof router.currentRoute.value };
    routeRef.value = {
      ...routeRef.value,
      fullPath: '/products/43/edit',
      params: { id: '43' }
    };
    await flushPromises();
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');
    secondSave.resolve(completeProduct({ id: 43 }));
    await secondNavigationStarted.promise;

    releaseFirstNavigation.resolve(undefined);
    await flushPromises();
    releaseSecondNavigation.resolve(undefined);
    await flushPromises();

    expect(confirm).not.toHaveBeenCalled();
    expect(router.currentRoute.value).toMatchObject({ name: 'product-detail', params: { id: '43' } });
  });

  it('authorizes only the exact saved detail target while save navigation is pending', async () => {
    const pendingSave = deferred<Product>();
    const savedNavigationStarted = deferred<void>();
    const releaseSavedNavigation = deferred<void>();
    vi.mocked(productService.getProduct).mockResolvedValue(completeProduct());
    vi.mocked(productService.updateProduct).mockReturnValueOnce(pendingSave.promise);
    const { router, wrapper } = await mountEditor('/products/42/edit');
    const confirm = vi.mocked(window.confirm);
    const originalPush = router.push.bind(router);
    vi.spyOn(router, 'push').mockImplementation(async (to) => {
      if (router.resolve(to).name === 'product-detail') {
        savedNavigationStarted.resolve(undefined);
        await releaseSavedNavigation.promise;
      }
      return originalPush(to);
    });
    await wrapper.get('[data-testid="step-confirm"]').trigger('click');
    await wrapper.get('[data-testid="submit-product"]').trigger('click');

    pendingSave.resolve(completeProduct({ id: 77 }));
    await savedNavigationStarted.promise;
    await originalPush({ name: 'product-detail', params: { id: 999 } });

    expect(router.currentRoute.value.name).toBe('product-edit');
    expect(router.currentRoute.value.params.id).toBe('42');
    expect(messages.value.at(-1)?.text).toContain('商品正在保存');

    releaseSavedNavigation.resolve(undefined);
    await flushPromises();

    expect(confirm).not.toHaveBeenCalled();
    expect(router.currentRoute.value).toMatchObject({ name: 'product-detail', params: { id: '77' } });
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
    expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('基本资料');
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
