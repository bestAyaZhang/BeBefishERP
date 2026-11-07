import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type LocationQueryRaw } from 'vue-router';
import type { Category } from '../masterdata/types';
import { httpProductService } from './httpProductService';
import { createMockProductService } from './mockProductService';
import { productFixture, productPage } from './productTestFixtures';
import type { Product, ProductFormPayload, ProductService } from './types';
import ProductList from './components/ProductList.vue';
import ProductTable from './components/ProductTable.vue';
import { deduplicateCategories } from './productCategoryTree';
import { PRODUCT_CATALOG_STORAGE_KEY } from './productCatalogColumns';
import type { ProductCatalogColumnId } from './productCatalogColumns';

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
    status: 'enabled',
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
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450,
      netWeightKg: 8.5,
      grossWeightKg: 9.2,
      gramWeightG: 350,
      innerPackageWeightKg: 1.1,
      packagingMethod: '彩盒',
      cartonQuantity: 12,
      skuImageFileId: null,
      packageImageFileId: null,
      cartonImageFileId: null,
      supplierQuotes,
      defaultSku: true,
      status: 'enabled'
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
      productLengthCm: 12.5,
      productWidthCm: 8.25,
      productHeightCm: 20,
      capacityMl: 450,
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

function catalogSku(
  id: number,
  overrides: Partial<Product['skus'][number]> = {}
): Product['skus'][number] {
  return {
    id,
    skuCode: `SKU-${id}`,
    barcode: null,
    skuName: `SKU ${id}`,
    specText: null,
    specificationValues: [],
    salesUnit: '只',
    defaultSalePrice: 19.9,
    standardCost: 8,
    safetyStockQuantity: 12,
    stockQuantity: 36,
    packageLengthCm: 42,
    packageWidthCm: 31,
    packageHeightCm: 28,
    packageVolumeCm3: 36456,
    innerPackageLengthCm: 36,
    innerPackageWidthCm: 25,
    innerPackageHeightCm: 22,
    productLengthCm: 12.5,
    productWidthCm: 8.25,
    productHeightCm: 20,
    capacityMl: 450,
    netWeightKg: 8.5,
    grossWeightKg: 9.2,
    gramWeightG: 350,
    innerPackageWeightKg: 1.1,
    packagingMethod: '彩盒',
    cartonQuantity: 12,
    skuImageFileId: null,
    skuImageUrl: null,
    packageImageFileId: null,
    packageImageUrl: null,
    cartonImageFileId: null,
    cartonImageUrl: null,
    supplierQuotes: [],
    defaultSku: true,
    status: 'enabled',
    ...overrides
  };
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
  attachTo?: Element;
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
    global: { plugins: [router] },
    attachTo: options.attachTo
  });
  await flushPromises();
  return { replace, router, service, wrapper };
}

describe('Task 8 product catalog list', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('expands real SKU records inline without opening the product on click or keyboard activation', async () => {
    const product = catalogProduct(42, '多规格测试杯', {
      skus: [catalogSku(421), catalogSku(422, {
        skuName: '500 ml 大杯', barcode: '6901234567001', safetyStockQuantity: 19,
        defaultSku: false, status: 'disabled'
      })]
    });
    const wrapper = mount(ProductTable, { props: { products: [product] } });
    const toggle = wrapper.get('[data-testid="product-sku-toggle-42"]');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('[data-testid="product-sku-panel-42"]').text()).toContain('500 ml 大杯');
    expect(wrapper.get('[data-testid="product-sku-panel-42"]').text()).toContain('SKU-422');
    expect(wrapper.get('[data-testid="product-sku-panel-42"]').text()).toContain('6901234567001');
    expect(wrapper.get('[data-testid="product-sku-panel-42"]').text()).toContain('安全 19 只');
    expect(wrapper.get('[data-testid="product-sku-panel-42"]').text()).toContain('已停用');
    expect(wrapper.emitted('open-product')).toBeUndefined();
    await toggle.trigger('keydown', { key: 'Enter' });
    await toggle.trigger('keyup', { key: 'Enter' });
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('[data-testid="product-sku-panel-42"]').exists()).toBe(false);
    expect(wrapper.emitted('open-product')).toBeUndefined();
    await toggle.trigger('keydown', { key: ' ' });
    expect(toggle.attributes('aria-expanded')).toBe('true');
    await wrapper.get('[data-testid="product-row-42"]').trigger('keyup.enter');
    expect(wrapper.emitted('open-product')).toEqual([[product]]);
    wrapper.unmount();
  });

  it('shows physical product specifications separately from packaging and carton metrics', async () => {
    const product = catalogProduct(42, '口径测试杯', {
      skus: [catalogSku(421, {
        productMouthDiameterCm: 8.2, productHeightCm: 8.9,
        specText: '透明 / 金边', capacityMl: 450
      })]
    });
    const wrapper = mount(ProductTable, { props: { products: [product] } });
    const specification = wrapper.get('[data-testid="product-cell-specification-42"]');
    expect(specification.findAll('p')[0].text()).toBe('口径 8.2 × 高 8.9 cm');
    expect(specification.text()).toContain('450 ml');
    expect(specification.text()).toContain('透明 / 金边');
    const packaging = wrapper.get('[data-testid="product-cell-packaging-42"]');
    expect(packaging.text()).toContain('彩盒');
    expect(packaging.text()).toContain('12 只/箱');
    expect(packaging.text()).toContain('350 g');
    expect(packaging.text()).not.toContain('口径');
    await wrapper.get('[data-testid="product-sku-toggle-42"]').trigger('click');
    const panel = wrapper.get('[data-testid="product-sku-panel-42"]');
    expect(panel.get('[data-label="产品规格"]').text()).toContain('口径 8.2 × 高 8.9 cm');
    expect(panel.get('[data-label="箱规 / 体积 / 重量"]').text()).toContain('42 × 31 × 28 cm');
    expect(panel.find('[data-label="采购来源"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('previews SKU photos, falls back to the main image, and recovers when a failed SKU image changes', async () => {
    const product = catalogProduct(42, '图片测试杯', {
      mainImageUrl: '/uploads/main.png',
      skus: [catalogSku(421, { skuImageUrl: '/uploads/sku-broken.png' }), catalogSku(422, { defaultSku: false })]
    });
    const wrapper = mount(ProductTable, { props: { products: [product] }, attachTo: document.body });
    await wrapper.get('[data-testid="product-sku-toggle-42"]').trigger('click');
    expect(wrapper.get('[data-testid="catalog-sku-image-42-421"]').attributes('src')).toBe('/uploads/sku-broken.png');
    expect(wrapper.get('[data-testid="catalog-sku-image-42-422"]').attributes('src')).toBe('/uploads/main.png');
    const trigger = wrapper.get('[data-testid="catalog-sku-image-42-421-preview-trigger"]');
    expect(trigger.classes()).toEqual(expect.arrayContaining(['h-10', 'w-10']));
    await trigger.trigger('click');
    expect(document.querySelector('[data-testid="catalog-sku-image-42-421-preview-dialog"]')).not.toBeNull();
    expect(wrapper.emitted('open-product')).toBeUndefined();
    await wrapper.get('[data-testid="catalog-sku-image-42-421"]').trigger('error');
    expect(wrapper.find('[data-testid="catalog-sku-image-42-421-fallback"]').exists()).toBe(true);
    await wrapper.setProps({ products: [{ ...product, skus: [
      { ...product.skus[0], skuImageUrl: '/uploads/sku-recovered.png' }, product.skus[1]
    ] }] });
    expect(wrapper.get('[data-testid="catalog-sku-image-42-421"]').attributes('src')).toBe('/uploads/sku-recovered.png');
    expect(wrapper.find('[data-testid="catalog-sku-image-42-421-fallback"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('uses a neutral placeholder when neither the SKU nor the product has an image', async () => {
    const wrapper = mount(ProductTable, {
      props: { products: [catalogProduct(42, '无图商品', { skus: [catalogSku(421)] })] }
    });
    await wrapper.get('[data-testid="product-sku-toggle-42"]').trigger('click');
    expect(wrapper.find('[data-testid="catalog-sku-image-42-421"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="catalog-sku-image-42-421-fallback"]').attributes('aria-label')).toContain('不可用');
    expect(wrapper.emitted('open-product')).toBeUndefined();
    wrapper.unmount();
  });

  it('replaces legacy supplier-only table props with carton metrics without adding unwanted columns', () => {
    const product = catalogProduct(42, '箱规测试杯', { skus: [catalogSku(421)] });
    const wrapper = mount(ProductTable, { props: { products: [product], columnIds: ['supplierSource'] } });
    expect(wrapper.findAll('thead th').map((header) => header.text())).toEqual(['箱规 / 体积 / 重量', '操作']);
    const metrics = wrapper.get('[data-testid="product-cell-cartonMetrics-42"]');
    expect(metrics.text()).toContain('42 × 31 × 28 cm');
    expect(metrics.text()).toContain('0.036456 m³');
    expect(metrics.text()).toContain('毛重 9.2 kg');
    expect(metrics.text()).toContain('净重 8.5 kg');
    expect(metrics.text()).not.toContain('义乌玻璃厂');
    expect(metrics.findAll('p').map((line) => line.attributes('title'))).toContain('36456 cm³ = 0.036456 m³');
    wrapper.unmount();
  });

  it('preserves zero carton measurements and calculates missing volume only from complete finite carton dimensions', () => {
    const products = [
      catalogProduct(1, '零箱规', { skus: [catalogSku(11, {
        packageLengthCm: 0, packageWidthCm: 2, packageHeightCm: 3,
        packageVolumeCm3: 0, grossWeightKg: 0, netWeightKg: 0
      })] }),
      catalogProduct(2, '自动体积', { skus: [catalogSku(21, {
        packageLengthCm: 50, packageWidthCm: 40, packageHeightCm: 30, packageVolumeCm3: null
      })] }),
      catalogProduct(3, '缺失箱规', { skus: [catalogSku(31, {
        packageLengthCm: null, packageVolumeCm3: null, grossWeightKg: null, netWeightKg: null
      })] }),
      catalogProduct(4, '异常箱规', { skus: [catalogSku(41, {
        packageLengthCm: Number.NaN, packageVolumeCm3: Number.POSITIVE_INFINITY,
        grossWeightKg: Number.NaN, netWeightKg: Number.NEGATIVE_INFINITY
      })] }),
      catalogProduct(5, '权威体积', { skus: [catalogSku(51, {
        packageLengthCm: 50, packageWidthCm: 40, packageHeightCm: 30, packageVolumeCm3: 1000
      })] })
    ];
    const wrapper = mount(ProductTable, { props: { products, columnIds: ['cartonMetrics'] } });
    expect(wrapper.get('[data-testid="product-cell-cartonMetrics-1"]').text()).toContain('0 × 2 × 3 cm');
    expect(wrapper.get('[data-testid="product-cell-cartonMetrics-1"]').text()).toContain('0 m³');
    expect(wrapper.get('[data-testid="product-cell-cartonMetrics-1"]').text()).toContain('毛重 0 kg · 净重 0 kg');
    expect(wrapper.get('[data-testid="product-cell-cartonMetrics-2"]').text()).toContain('0.06 m³');
    for (const id of [3, 4]) {
      const text = wrapper.get(`[data-testid="product-cell-cartonMetrics-${id}"]`).text();
      expect(text).toBe('--体积 --毛重 -- · 净重 --');
      expect(text).not.toMatch(/NaN|Infinity/);
    }
    expect(wrapper.get('[data-testid="product-cell-cartonMetrics-5"]').text()).toContain('0.001 m³');
    wrapper.unmount();
  });

  it('migrates supplier-source preferences on refresh and removes the retired column from customization', async () => {
    window.localStorage.setItem(PRODUCT_CATALOG_STORAGE_KEY, JSON.stringify([
      'inventory', 'supplierSource', 'productInfo', 'specification', 'packaging', 'pricing', 'lifecycle'
    ]));
    const { wrapper } = await mountCatalog();
    expect(wrapper.findAll('thead th').map((header) => header.text())).toEqual([
      '可用库存', '箱规 / 体积 / 重量', '商品档案', '产品规格', '包装信息默认 SKU', '销售价 / 采购价', '状态 / 更新', '操作'
    ]);
    await wrapper.get('[data-testid="product-column-trigger"]').trigger('click');
    expect(wrapper.find('[data-testid="product-column-checkbox-supplierSource"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-column-checkbox-cartonMetrics"]').element).toBeTruthy();
    wrapper.unmount();
  });

  it('opens category filters as a focused popover and dismisses it on Escape, outside pointerdown or selection', async () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const { wrapper, router } = await mountCatalog({ attachTo: host });
    const toggle = wrapper.get<HTMLButtonElement>('[data-testid="product-category-trigger"]');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('[data-testid="product-category-popover"]').isVisible()).toBe(false);
    await toggle.trigger('click');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="category-search"]').element);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushPromises();
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle.element);
    await toggle.trigger('click');
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await toggle.trigger('click');
    await wrapper.get('[data-testid="category-node-13"]').trigger('click');
    await flushPromises();
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(router.currentRoute.value.query.categoryId).toBe('13');
    expect(toggle.text()).toContain('玻璃杯');
    wrapper.unmount();
    host.remove();
  });

  it('uses authoritative stock totals, enabled SKU sale ranges and only explicit enabled default purchasing quotes', async () => {
    const quote = {
      id: 1, skuId: 421, supplierId: 1, supplierName: '已停用默认供应商', supplierItemNo: 'OLD-42',
      purchasePrice: 88, minPurchaseQuantity: 120, status: 'disabled' as const, defaultQuote: true
    };
    const product = catalogProduct(42, '真实资料杯', {
      totalStock: 0, totalSafetyStock: 9, skus: [
        catalogSku(421, { defaultSalePrice: 0, stockQuantity: 600, supplierQuotes: [quote] }),
        catalogSku(422, { defaultSku: false, defaultSalePrice: 20, salesUnit: '箱' }),
        catalogSku(423, { defaultSku: false, status: 'disabled', defaultSalePrice: 900 })
      ]
    });
    const wrapper = mount(ProductTable, { props: { products: [product] } });
    expect(wrapper.get('[data-testid="product-price-42"]').text()).toContain('¥0.00 – ¥20.00');
    expect(wrapper.get('[data-testid="product-price-42"]').text()).toContain('采购 --');
    expect(wrapper.get('[data-testid="product-stock-42"]').text()).toContain('安全 9');
    expect(wrapper.get('[data-testid="product-stock-42"]').text()).toContain('缺口 9');
    expect(wrapper.get('[data-testid="product-stock-42"] > div > span').text()).toBe('0汇总');
    expect(wrapper.get('[data-testid="product-stock-42"]').text()).not.toMatch(/只|箱/);
    expect(wrapper.text()).not.toContain('已停用默认供应商');
    await wrapper.setProps({ products: [{ ...product, skus: [{ ...product.skus[0], supplierQuotes: [{
      ...quote, status: 'enabled', supplierName: '当前默认供应商', supplierItemNo: 'CURRENT-42', purchasePrice: 0
    }] }] }] });
    expect(wrapper.text()).not.toContain('当前默认供应商');
    expect(wrapper.text()).not.toContain('CURRENT-42');
    expect(wrapper.get('[data-testid="product-price-42"]').text()).toContain('采购 ¥0.00');
    wrapper.unmount();
  });

  it('exports only the currently loaded page with an explicit scope and disables export during a failed page load', async () => {
    const createObjectURL = vi.fn().mockReturnValue('blob:current-page');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const downloads: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download);
    });
    try {
      const listProducts = vi.fn<ProductService['listProducts']>()
        .mockResolvedValueOnce(productPage([catalogProduct(42, '当前页商品')], 80, 2, 20))
        .mockRejectedValueOnce(new Error('下一页加载失败'));
      const { wrapper, router } = await mountCatalog({ query: { page: '2', size: '20' }, service: catalogService(listProducts) });
      const button = wrapper.get('[data-testid="product-export-current-page"]');
      expect(button.text()).toContain('当前页');
      expect(button.attributes('aria-label')).toBe('导出当前页商品及 SKU');
      await button.trigger('click');
      expect(downloads).toEqual(['商品资料-当前页-2.csv']);
      const blob = createObjectURL.mock.calls[0][0] as Blob;
      const csv = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = reject;
        reader.readAsText(blob);
      });
      expect(csv).toContain('当前页商品');
      expect(csv).toContain('ITEM-42');
      expect(listProducts).toHaveBeenCalledTimes(1);
      await router.push({ query: { page: '3', size: '20' } });
      await flushPromises();
      expect(button.attributes('disabled')).toBeDefined();
      wrapper.unmount();
    } finally {
      click.mockRestore();
      vi.unstubAllGlobals();
    }
  });

  it('customizes and reorders catalog fields, persists them, enforces the minimum, and resets defaults', async () => {
    // Existing saved columns stay usable in their original order after the redesign.
    window.localStorage.setItem(PRODUCT_CATALOG_STORAGE_KEY, JSON.stringify([
      'productInfo', 'category', 'brandSupplier', 'stock', 'price', 'completenessStatus'
    ]));
    const product = catalogProduct(42, '容量测试杯', {
      skus: [catalogSku(421)]
    });
    const { wrapper } = await mountCatalog({
      service: catalogService(mockListProducts(productPage([product])))
    });

    expect(wrapper.findAll('thead th')).toHaveLength(7);
    const trigger = wrapper.get('[data-testid="product-column-trigger"]');
    expect(trigger.attributes('aria-expanded')).toBe('false');

    await trigger.trigger('click');
    expect(wrapper.get('[data-testid="product-column-panel"]').isVisible()).toBe(true);
    expect(wrapper.get('[data-testid="product-column-options"]').classes()).toContain(
      'max-h-[clamp(220px,calc(100vh-440px),430px)]'
    );
    expect(wrapper.get('[data-testid="product-column-count"]').text()).toContain('已选 6');
    expect(wrapper.get('[data-testid="product-column-help"]').text()).toContain('至少保留 6 个业务字段，操作列固定显示。');

    await wrapper.get<HTMLInputElement>('[data-testid="product-column-checkbox-productDimensions"]').setValue(true);
    await wrapper.get<HTMLInputElement>('[data-testid="product-column-checkbox-capacity"]').setValue(true);

    expect(wrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品档案', '分类', '品牌 / 供应商', '库存', '价格', '资料状态', '产品尺寸', '容量', '操作'
    ]);
    expect(wrapper.get('[data-testid="product-cell-productDimensions-42"]').text()).toBe('12.5 × 8.25 × 20 cm');
    expect(wrapper.get('[data-testid="product-cell-capacity-42"]').text()).toBe('450 ml');

    await wrapper.get('[data-testid="product-column-drag-capacity"]').trigger('dragstart');
    await wrapper.get('[data-testid="product-column-row-category"]').trigger('drop', { clientY: 0 });

    expect(wrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品档案', '容量', '分类', '品牌 / 供应商', '库存', '价格', '资料状态', '产品尺寸', '操作'
    ]);
    expect(window.localStorage.getItem(PRODUCT_CATALOG_STORAGE_KEY)).toBe(JSON.stringify([
      'productInfo',
      'capacity',
      'category',
      'brandSupplier',
      'stock',
      'price',
      'completenessStatus',
      'productDimensions'
    ]));

    const { wrapper: restoredWrapper } = await mountCatalog({
      service: catalogService(mockListProducts(productPage([product])))
    });
    expect(restoredWrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品档案', '容量', '分类', '品牌 / 供应商', '库存', '价格', '资料状态', '产品尺寸', '操作'
    ]);
    restoredWrapper.unmount();

    await wrapper.get<HTMLInputElement>('[data-testid="product-column-checkbox-category"]').setValue(false);
    await wrapper.get<HTMLInputElement>('[data-testid="product-column-checkbox-brandSupplier"]').setValue(false);
    const selectedCheckboxes = wrapper.findAll<HTMLInputElement>('[data-testid^="product-column-checkbox-"]')
      .filter((checkbox) => checkbox.element.checked);
    expect(selectedCheckboxes).toHaveLength(6);
    expect(selectedCheckboxes.every((checkbox) => checkbox.element.disabled)).toBe(true);

    await wrapper.get('[data-testid="product-column-reset"]').trigger('click');
    expect(wrapper.get('[data-testid="product-column-count"]').text()).toContain('已选 7');
    expect(window.localStorage.getItem(PRODUCT_CATALOG_STORAGE_KEY)).toBeNull();
    expect(wrapper.get('thead tr').findAll('th').map((header) => header.text())).toEqual([
      '商品档案', '产品规格', '包装信息默认 SKU', '箱规 / 体积 / 重量', '销售价 / 采购价', '可用库存', '状态 / 更新', '操作'
    ]);
    wrapper.unmount();
  });

  it('dismisses the column panel with Escape or outside pointerdown and cleans document listeners', async () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const removeEventListener = vi.spyOn(document, 'removeEventListener');
    const { wrapper } = await mountCatalog({ attachTo: host });
    const trigger = wrapper.get<HTMLButtonElement>('[data-testid="product-column-trigger"]');

    await trigger.trigger('click');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(trigger.attributes('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger.element);

    await trigger.trigger('click');
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(trigger.attributes('aria-expanded')).toBe('false');

    await trigger.trigger('click');
    removeEventListener.mockClear();
    wrapper.unmount();
    expect(removeEventListener).toHaveBeenCalledWith('keydown', expect.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('pointerdown', expect.any(Function));
    removeEventListener.mockRestore();
    host.remove();
  });

  it('aligns dynamic table states and resolves physical fields through the documented SKU fallback', () => {
    const physicalColumnIds: ProductCatalogColumnId[] = [
      'productInfo',
      'productDimensions',
      'capacity',
      'packageVolume',
      'cartonQuantity',
      'packagingMethod',
      'grossWeight',
      'netWeight',
      'gramWeight'
    ];
    const products = [
      catalogProduct(1, '默认启用 SKU', {
        skus: [
          catalogSku(10, { defaultSku: false, productLengthCm: 1 }),
          catalogSku(11, { defaultSku: true, productLengthCm: 11, productWidthCm: 12, productHeightCm: 13, capacityMl: 410 })
        ]
      }),
      catalogProduct(2, '首个启用 SKU', {
        skus: [
          catalogSku(20, { status: 'disabled', defaultSku: true, productLengthCm: 2 }),
          catalogSku(21, {
            defaultSku: false,
            productLengthCm: 21,
            productWidthCm: 22,
            productHeightCm: 23,
            capacityMl: 420,
            packageVolumeCm3: 42000,
            cartonQuantity: 24,
            packagingMethod: '纸盒彩盒',
            grossWeightKg: 10.2,
            netWeightKg: 9.4,
            gramWeightG: 380
          })
        ]
      }),
      catalogProduct(3, '首个 SKU 兜底', {
        skus: [
          catalogSku(30, {
            status: 'disabled',
            defaultSku: false,
            productLengthCm: 31,
            productWidthCm: 32,
            productHeightCm: 33,
            capacityMl: 430
          }),
          catalogSku(31, { status: 'disabled', defaultSku: false, productLengthCm: 3 })
        ]
      })
    ];
    const wrapper = mount(ProductTable, {
      props: { products, columnIds: physicalColumnIds }
    });

    expect(wrapper.findAll('thead th')).toHaveLength(10);
    expect(wrapper.get('[data-testid="product-row-1"]').text()).toContain('11 × 12 × 13 cm');
    expect(wrapper.get('[data-testid="product-row-1"]').text()).toContain('410 ml');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('21 × 22 × 23 cm');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('42000 cm³');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('24 只/箱');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('纸盒彩盒');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('10.2 kg');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('9.4 kg');
    expect(wrapper.get('[data-testid="product-row-2"]').text()).toContain('380 g');
    expect(wrapper.get('[data-testid="product-row-3"]').text()).toContain('31 × 32 × 33 cm');
    expect(wrapper.get('[data-testid="product-row-3"]').text()).toContain('430 ml');

    const loading = mount(ProductTable, {
      props: { products: [], loading: true, columnIds: physicalColumnIds }
    });
    expect(loading.get('[data-testid="product-table-loading"]').attributes('colspan')).toBe('10');

    const empty = mount(ProductTable, {
      props: { products: [], showEmpty: true, columnIds: physicalColumnIds }
    });
    expect(empty.get('[data-testid="product-table-empty"]').attributes('colspan')).toBe('10');
  });

  it('renders every optional nonphysical registry field through the dynamic table', () => {
    const columnIds: ProductCatalogColumnId[] = [
      'productInfo',
      'productCode',
      'category',
      'productType',
      'brandSupplier',
      'stock',
      'safetyStock',
      'price',
      'skuCount',
      'completenessStatus',
      'completenessPercent',
      'recordStatus',
      'updatedAt',
      'remark'
    ];
    const product = catalogProduct(55, '扩展字段商品', {
      totalStock: 72,
      totalSafetyStock: 18,
      completenessPercent: 88,
      updatedAt: '2026-09-02T13:45:00',
      remark: '重点维护',
      skus: [catalogSku(551), catalogSku(552, { defaultSku: false })]
    });
    const wrapper = mount(ProductTable, {
      props: { products: [product], categories: catalogCategories, columnIds }
    });

    expect(wrapper.findAll('thead th')).toHaveLength(15);
    expect(wrapper.get('[data-testid="product-cell-productCode-55"]').text()).toBe('PRD-000055');
    expect(wrapper.get('[data-testid="product-cell-productType-55"]').text()).toBe('单品');
    expect(wrapper.get('[data-testid="product-cell-safetyStock-55"]').text()).toBe('18');
    expect(wrapper.get('[data-testid="product-cell-skuCount-55"]').text()).toBe('2');
    expect(wrapper.get('[data-testid="product-cell-completenessPercent-55"]').text()).toBe('88%');
    expect(wrapper.get('[data-testid="product-cell-recordStatus-55"]').text()).toBe('启用');
    expect(wrapper.get('[data-testid="product-cell-updatedAt-55"]').text()).toBe('2026-09-02 13:45');
    expect(wrapper.get('[data-testid="product-cell-remark-55"]').text()).toBe('重点维护');
    expect(wrapper.get('[data-testid="product-detail-55"]').text()).toBe('查看详情');
  });

  it('prefers the response category name when the local category lookup is stale', () => {
    const product = catalogProduct(31, '分类响应优先', { categoryName: '服务端分类名' });
    const wrapper = mount(ProductTable, {
      props: {
        products: [product],
        categories: [{ ...catalogCategories[1], categoryName: '过期分类名' }]
      }
    });

    expect(wrapper.get('[data-testid="product-category-31"]').text()).toContain('服务端分类名');
    expect(wrapper.get('[data-testid="product-category-31"]').text()).not.toContain('过期分类名');
  });

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
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12, status: 'published' });
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

  it('separates formal products from drafts and restores the draft filter through navigation', async () => {
    const service = createMockProductService([
      catalogProduct(41, '正式启用商品'),
      catalogProduct(42, '正式停用商品', { status: 'disabled' }),
      catalogProduct(43, '未提交草稿', { status: 'draft', completenessStatus: 'complete' })
    ]);
    const { router, wrapper } = await mountCatalog({ service });

    expect(wrapper.text()).toContain('正式启用商品');
    expect(wrapper.text()).toContain('正式停用商品');
    expect(wrapper.text()).not.toContain('未提交草稿');
    await wrapper.get('[data-testid="product-filter-draft"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query.status).toBe('draft');
    expect(wrapper.text()).toContain('未提交草稿');
    expect(wrapper.text()).not.toContain('正式启用商品');
    expect(wrapper.text()).not.toContain('正式停用商品');
    expect(wrapper.get('[data-testid="product-cell-completenessStatus-43"]').text()).toBe('草稿');

    await wrapper.get('[data-testid="product-filter-all"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('正式启用商品');
    expect(wrapper.text()).toContain('正式停用商品');
    expect(wrapper.text()).toContain('未提交草稿');
    router.back();
    await flushPromises();
    expect(wrapper.get('[data-testid="product-filter-draft"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.text()).not.toContain('正式启用商品');
  });

  it('resets pagination and retains category and keyword when switching the publication filter', async () => {
    const service = catalogService(vi.fn().mockImplementation((query) => (
      Promise.resolve(productPage([], 200, query.page, query.size))
    )));
    const { router, wrapper } = await mountCatalog({
      query: { categoryId: '12', keyword: 'SKU', page: '4', size: '50' }, service
    });
    await wrapper.get('[data-testid="product-filter-draft"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.query).toEqual({ categoryId: '12', keyword: 'SKU', page: '1', size: '50', status: 'draft' });
    expect(service.listProducts).toHaveBeenLastCalledWith({ categoryId: 12, keyword: 'SKU', page: 1, size: 50, status: 'draft' });
  });

  it('searches item number or SKU alongside the publication filter', async () => {
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
    expect(wrapper.find('[data-testid="product-status-tabs"]').exists()).toBe(true);
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
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12, keyword: 'SKU-42', status: 'published' });

    await wrapper.get('[data-testid="product-reset"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({ categoryId: '12', page: '1', size: '20' });
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, categoryId: 12, status: 'published' });
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
    expect(listProducts).toHaveBeenCalledWith({ page: 1, size: 20, status: 'published' });
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
    expect(listProducts).toHaveBeenLastCalledWith({ page: 3, size: 50, categoryId: 13, keyword: 'GLASS', status: 'published' });

    await router.push({
      name: 'products',
      query: { categoryId: ['13', '14'], keyword: ['A', 'B'], page: '-7', size: '999' }
    });
    await flushPromises();
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-keyword"]').element.value).toBe('');
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 20, status: 'published' });

    router.back();
    await flushPromises();
    expect(wrapper.get<HTMLInputElement>('[data-testid="product-keyword"]').element.value).toBe('GLASS');
    expect(listProducts).toHaveBeenLastCalledWith({ page: 3, size: 50, categoryId: 13, keyword: 'GLASS', status: 'published' });
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
    expect(listProducts).toHaveBeenLastCalledWith({ page: 1, size: 50, categoryId: 12, keyword: 'CUP', status: 'published' });
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
      '商品档案', '产品规格', '包装信息默认 SKU', '箱规 / 体积 / 重量', '销售价 / 采购价', '可用库存', '状态 / 更新', '操作'
    ]);
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('批量');
    expect(wrapper.text()).not.toContain('编辑');
    expect(wrapper.text()).not.toContain('删除');
    expect(wrapper.get('[data-testid="product-detail-42"]').text()).toBe('查看详情');
    expect(wrapper.get('[data-testid="product-thumb-42"]').classes()).toContain('h-14');
    expect(wrapper.get('[data-testid="product-thumb-42"]').classes()).toContain('w-[52px]');
    expect(wrapper.get('[data-testid="product-table-scroll"]').classes()).toContain('overflow-auto');
    expect(wrapper.get('[data-testid="product-table"]').attributes('style')).toContain('min-width: 1108px');
    expect(wrapper.get('[data-testid="product-pagination"]').element.parentElement?.getAttribute('data-testid')).not.toBe('product-table-scroll');

    await wrapper.get('[data-testid="product-detail-42"]').trigger('click');
    expect(wrapper.emitted('open-product')?.[0]).toEqual([product]);
    await wrapper.get('[data-testid="add-product"]').trigger('click');
    expect(wrapper.emitted('create-product')).toHaveLength(1);
  });

  it('uses a full-width product workspace with category filters in a popover', async () => {
    const product = catalogProduct(42, '高硼硅玻璃杯');
    const { wrapper } = await mountCatalog({
      allProductTotal: 5,
      service: catalogService(mockListProducts(productPage([product], 5)))
    });

    expect(wrapper.classes()).toEqual(expect.arrayContaining([
      'flex',
      'h-full',
      'min-h-0',
      'flex-col',
      'gap-4'
    ]));
    expect(wrapper.get('[data-testid="product-page-header"]').classes()).toEqual(expect.arrayContaining([
      'min-h-[72px]',
      'shrink-0'
    ]));
    expect(wrapper.get('[data-testid="product-page-title"]').text()).toBe('商品管理');
    expect(wrapper.get('[data-testid="product-page-title"]').classes()).toContain('text-page-title');
    expect(wrapper.get('[data-testid="product-page-count"]').text()).toBe('5');
    expect(wrapper.get('[data-testid="product-page-subtitle"]').text()).toContain('包装与采购资料');
    expect(wrapper.get('[data-testid="add-product"]').element.closest('[data-testid="product-page-header"]')).not.toBeNull();

    expect(wrapper.get('[data-testid="product-workspace"]').classes()).toEqual(expect.arrayContaining([
      'flex-1',
      'flex',
      'min-h-0',
      'min-w-0'
    ]));
    expect(wrapper.get('[data-testid="product-category-panel"]').classes()).toEqual(expect.arrayContaining([
      'rounded-lg',
      'border-slate-200',
      'bg-white'
    ]));
    expect(wrapper.get('[data-testid="product-category-popover"]').isVisible()).toBe(false);
    expect(wrapper.get('[data-testid="product-list-panel"]').classes()).toEqual(expect.arrayContaining([
      'rounded-lg',
      'border-slate-200',
      'bg-white'
    ]));
    expect(wrapper.get('[data-testid="product-list-heading"]').text()).toContain('商品列表');
    expect(wrapper.get('[data-testid="product-list-heading"]').classes()).toContain('h-12');
    expect(wrapper.get('[data-testid="product-list-heading"]').get('h2').classes()).toContain('text-card-title');
    expect(wrapper.get('[data-testid="product-list-total"]').text()).toBe('共 5 件商品');
    expect(wrapper.get('[data-testid="product-filter-bar"]').classes()).toContain('min-h-[60px]');
    expect(wrapper.get('[data-testid="product-filter-bar"]').find('[data-testid="add-product"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="product-table"] thead').classes()).toEqual(expect.arrayContaining([
      'sticky',
      'top-0'
    ]));
    expect(wrapper.get('[data-testid="product-row-42"]').classes()).toContain('h-[88px]');
    expect(wrapper.get('[data-testid="product-pagination"]').classes()).toContain('lg:h-14');
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
    expect(wrapper.get('[data-testid="product-thumb-42"]').classes()).toEqual(expect.arrayContaining([
      'bg-slate-50',
      'text-slate-400'
    ]));

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
      expect(cell.get('span').classes()).toContain('font-numeric');
      expect(cell.get('span').classes()).toContain('text-table-number');
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
      page: 1, size: 50, categoryId: 12, keyword: 'CUP', status: 'published'
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
