import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearMessages, messages } from '../../components/feedback/message';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import router from '../../router';
import SalesCreateView from './SalesCreateView.vue';
import type { CustomerAccountService } from '../masterdata/customerAccountService';

const { printSalesDocumentMock } = vi.hoisted(() => ({ printSalesDocumentMock: vi.fn() }));

vi.mock('./salesPrint', () => ({ printSalesDocument: printSalesDocumentMock }));
vi.mock('../masterdata/customerAccountService', () => ({ customerAccountService: { listPrices: async () => [] } }));

const catalog = {
  customers: [{
    id: 5,
    customerNo: 'CUS-HZ-001',
    customerName: '杭州酒店用品店',
    contactPerson: '张三',
    mobile: '13800000000',
    telephone: '',
    province: '浙江省',
    city: '杭州市',
    district: '西湖区',
    detailAddress: '文三路 88 号',
    transportMethod: 'delivery',
    settlementCycle: 'monthly'
  }],
  warehouses: [{ id: 8, warehouseNo: 'WH-HZ-MAIN', warehouseName: '杭州主仓', address: '杭州市拱墅区', defaultWarehouse: true }],
  products: [{
    skuId: 2001,
    skuCode: 'PRD-000101-001',
    itemNo: 'GB-101',
    barcode: 'GB-101-CLEAR',
    productName: '高硼硅玻璃杯',
    skuName: '透明款',
    specification: '透明',
    packagingMethod: '彩盒',
    cartonQuantity: 48,
    salesUnit: '只',
    defaultSalePrice: 12
  }]
};

function createService() {
  return { loadCatalog: vi.fn().mockResolvedValue(catalog) };
}

function createOrderService() {
  return {
    confirmOrder: vi.fn().mockResolvedValue({ orderNo: 'SO-20260713-003', status: 'confirmed' }),
    saveDraft: vi.fn().mockResolvedValue({ orderNo: 'SO-20260713-004', status: 'draft' })
  };
}

function mountView(service: ReturnType<typeof createService>, orderService = createOrderService()) {
  return mount(SalesCreateView, { props: { service, orderService }, global: { plugins: [router] } });
}

async function selectCustomer(wrapper: VueWrapper, id: number) {
  await wrapper.get('[data-testid="sales-customer"]').trigger('focus');
  await wrapper.get(`[data-testid="sales-customer-option-${id}"]`).trigger('click');
}

function getPrintDocument() {
  const element = globalThis.document.body.querySelector<HTMLElement>('[data-testid="sales-print-document"]');
  if (!element) throw new Error('打印文档未渲染');
  return element;
}

describe('SalesCreateView', () => {
  it('uses a zero customer price and reprices existing lines when switching customers', async () => {
    const service = { loadCatalog: vi.fn().mockResolvedValue({ ...catalog, customers: [...catalog.customers, { ...catalog.customers[0], id: 6, customerName: '另一客户' }] }) };
    const accountService = { listPrices: vi.fn().mockImplementation(async (id: number) => id === 5 ? [{ skuId: 2001, unitPrice: 0 }] : []) } as unknown as CustomerAccountService;
    const wrapper = mount(SalesCreateView, { props: { service, orderService: createOrderService(), accountService }, global: { plugins: [router] } });
    await flushPromises();
    await selectCustomer(wrapper, 5);
    await flushPromises();
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    expect(wrapper.get('[data-testid="sales-total"]').text()).toBe('¥0.00');
    await selectCustomer(wrapper, 6);
    await flushPromises();
    expect(wrapper.get('[data-testid="sales-total"]').text()).toBe('¥12.00');
    expect(accountService.listPrices).toHaveBeenLastCalledWith(6);
    wrapper.unmount();
  });

  it('ignores a stale price response after switching customer', async () => {
    let resolveFirst!: (prices: unknown[]) => void;
    const service = { loadCatalog: vi.fn().mockResolvedValue({ ...catalog, customers: [...catalog.customers, { ...catalog.customers[0], id: 6 }] }) };
    const accountService = { listPrices: vi.fn().mockImplementation((id: number) => id === 5 ? new Promise(resolve => { resolveFirst = resolve; }) : Promise.resolve([{ skuId: 2001, unitPrice: 7 }])) } as unknown as CustomerAccountService;
    const wrapper = mount(SalesCreateView, { props: { service, orderService: createOrderService(), accountService }, global: { plugins: [router] } });
    await flushPromises();
    await selectCustomer(wrapper, 5);
    await selectCustomer(wrapper, 6);
    await flushPromises();
    resolveFirst([{ skuId: 2001, unitPrice: 99 }]); await flushPromises();
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    expect(wrapper.get('[data-testid="sales-total"]').text()).toBe('¥7.00');
    wrapper.unmount();
  });

  afterEach(() => {
    clearMessages();
    clearCurrentUser();
    document.body.classList.remove('sales-order-printing');
    printSalesDocumentMock.mockReset();
    vi.restoreAllMocks();
  });

  it('loads customer, warehouse, and SKU choices for a sales order', async () => {
    const service = createService();
    const wrapper = mountView(service);
    await flushPromises();

    expect(wrapper.get('[data-testid="sales-create-page"]').text()).toContain('销售开单');
    await wrapper.get('[data-testid="sales-customer"]').trigger('focus');
    expect(wrapper.get('[data-testid="sales-customer-options"]').text()).toContain('杭州酒店用品店');
    expect(wrapper.get('[data-testid="sales-warehouse"]').text()).toContain('杭州主仓');
    expect((wrapper.get('[data-testid="sales-warehouse"]').element as HTMLSelectElement).value).toBe('8');
    expect(wrapper.get('[data-testid="sales-product-input"]').attributes('placeholder')).toContain('输入货号、SKU或商品名称');
  });

  it('searches customers by name, number, contact and phone and applies the chosen customer defaults', async () => {
    const service = { loadCatalog: vi.fn().mockResolvedValue({ ...catalog, customers: [...catalog.customers,
      { ...catalog.customers[0], id: 6, customerNo: 'CUS-SH-002', customerName: '上海日用百货', contactPerson: '李四', mobile: '13912345678', transportMethod: 'pickup', settlementCycle: 'daily' }
    ] }) };
    const accountService = { listPrices: vi.fn().mockResolvedValue([]) } as unknown as CustomerAccountService;
    const wrapper = mount(SalesCreateView, { props: { service, orderService: createOrderService(), accountService }, global: { plugins: [router] } });
    await flushPromises();
    const input = wrapper.get('[data-testid="sales-customer"]');
    for (const query of ['上海', 'cus-sh', '李四', '12345678']) {
      await input.setValue(query);
      expect(wrapper.find('[data-testid="sales-customer-option-5"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="sales-customer-option-6"]').text()).toContain('上海日用百货');
    }
    await wrapper.get('[data-testid="sales-customer-option-6"]').trigger('click');
    await flushPromises();
    expect((input.element as HTMLInputElement).value).toBe('CUS-SH-002 · 上海日用百货');
    expect(wrapper.find('[data-testid="sales-customer-options"]').exists()).toBe(false);
    expect((wrapper.get('[data-testid="sales-transport"]').element as HTMLSelectElement).value).toBe('pickup');
    expect((wrapper.get('[data-testid="sales-settlement"]').element as HTMLSelectElement).value).toBe('daily');
    expect(accountService.listPrices).toHaveBeenCalledWith(6);
    wrapper.unmount();
  });

  it('requires a new selection after editing or clearing the selected customer', async () => {
    const orderService = createOrderService();
    const wrapper = mountView(createService(), orderService);
    await flushPromises();
    await selectCustomer(wrapper, 5);
    const input = wrapper.get('[data-testid="sales-customer"]');
    await input.setValue('不存在的客户');
    expect(wrapper.get('[data-testid="sales-customer-no-results"]').text()).toContain('未找到匹配客户');
    await wrapper.get('[data-testid="sales-order-form"]').trigger('submit');
    await flushPromises();
    expect(orderService.saveDraft).not.toHaveBeenCalled();
    expect(messages.value.at(-1)?.text).toBe('请选择客户');
    await wrapper.get('[data-testid="sales-customer-clear"]').trigger('click');
    expect((input.element as HTMLInputElement).value).toBe('');
    expect(wrapper.get('[data-testid="sales-customer-option-5"]').text()).toContain('杭州酒店用品店');
    wrapper.unmount();
  });

  it('selects search results by keyboard without submitting the order and closes on Escape or blur', async () => {
    const orderService = createOrderService();
    const wrapper = mountView(createService(), orderService);
    await flushPromises();
    const input = wrapper.get('[data-testid="sales-customer"]');
    await input.setValue('杭州');
    await input.trigger('keydown', { key: 'ArrowDown' });
    expect(input.attributes('aria-activedescendant')).toBe('sales-customer-option-5');
    await input.trigger('keydown', { key: 'Enter' });
    await flushPromises();
    expect((input.element as HTMLInputElement).value).toContain('杭州酒店用品店');
    expect(orderService.saveDraft).not.toHaveBeenCalled();
    await input.trigger('focus');
    await input.trigger('keydown', { key: 'Escape' });
    expect(input.attributes('aria-expanded')).toBe('false');
    await input.trigger('focus');
    await input.trigger('blur');
    expect(wrapper.find('[data-testid="sales-customer-options"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('filters SKU choices by typing and adds the selected product', async () => {
    const wrapper = mountView(createService());
    await flushPromises();

    const input = wrapper.get('[data-testid="sales-product-input"]');
    await input.trigger('focus');
    await input.setValue('GB-101');
    expect(wrapper.get('[data-testid="sales-product-options"]').text()).toContain('高硼硅玻璃杯');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    expect((input.element as HTMLInputElement).value).toContain('GB-101');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-line-qty-2001"]').setValue('2');

    expect(wrapper.get('[data-testid="sales-line-2001"]').text()).toContain('透明款');
    expect(wrapper.get('[data-testid="sales-line-amount-2001"]').text()).toBe('¥24.00');
    expect(wrapper.get('[data-testid="sales-subtotal"]').text()).toBe('¥24.00');
    expect(wrapper.get('[data-testid="sales-total"]').text()).toBe('¥24.00');
  });

  it('calculates received and receivable amounts before confirming the order', async () => {
    const wrapper = mountView(createService());
    await flushPromises();

    await selectCustomer(wrapper, 5);
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-freight"]').setValue('10');
    await wrapper.get('[data-testid="sales-received"]').setValue('15');
    await wrapper.get('[data-testid="sales-confirm"]').trigger('click');

    expect(wrapper.get('[data-testid="sales-total"]').text()).toBe('¥22.00');
    expect(wrapper.get('[data-testid="sales-receivable"]').text()).toBe('¥7.00');
    expect(wrapper.get('[data-testid="sales-status"]').text()).toContain('已确认');
  });

  it('calls the order API and navigates to the list after confirmation succeeds', async () => {
    const orderService = createOrderService();
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined as never);
    const wrapper = mountView(createService(), orderService);
    await flushPromises();

    await selectCustomer(wrapper, 5);
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-confirm"]').trigger('click');
    await flushPromises();

    expect(orderService.confirmOrder).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 5,
      warehouseId: 8,
      lines: [expect.objectContaining({ skuId: 2001, quantity: 1, unitPrice: 12 })]
    }));
    expect(push).toHaveBeenCalledWith({ name: 'sales-orders', query: { created: 'SO-20260713-003' } });
  });

  it('saves a draft through the order API and navigates to the list', async () => {
    const orderService = createOrderService();
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined as never);
    const wrapper = mountView(createService(), orderService);
    await flushPromises();

    await selectCustomer(wrapper, 5);
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-order-form"]').trigger('submit');
    await flushPromises();

    expect(orderService.saveDraft).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 5,
      warehouseId: 8,
      lines: [expect.objectContaining({ skuId: 2001, quantity: 1, unitPrice: 12 })]
    }));
    expect(push).toHaveBeenCalledWith({ name: 'sales-orders', query: { created: 'SO-20260713-004', type: 'draft' } });
  });

  it('keeps the order form open when the confirmation API fails', async () => {
    const orderService = createOrderService();
    orderService.confirmOrder.mockRejectedValue(new Error('销售单接口失败'));
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined as never);
    const wrapper = mountView(createService(), orderService);
    await flushPromises();

    await selectCustomer(wrapper, 5);
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-confirm"]').trigger('click');
    await flushPromises();

    expect(messages.value.at(-1)?.type).toBe('error');
    expect(messages.value.at(-1)?.text).toContain('销售单接口失败');
    expect(push).not.toHaveBeenCalledWith({ name: 'sales-orders' });
  });

  it('requires customer and at least one SKU before confirming', async () => {
    const wrapper = mountView(createService());
    await flushPromises();

    await wrapper.get('[data-testid="sales-confirm"]').trigger('click');

    expect(messages.value.at(-1)?.type).toBe('error');
    expect(messages.value.at(-1)?.text).toContain('请选择客户');
  });

  it('enters print mode while opening the browser print dialog and restores the page afterwards', async () => {
    const wrapper = mountView(createService());
    await flushPromises();

    await wrapper.get('[data-testid="sales-print"]').trigger('click');
    await flushPromises();

    expect(printSalesDocumentMock).toHaveBeenCalledTimes(1);
    const printDocument = getPrintDocument();
    expect(printDocument.textContent).toContain('出库送货单');
    expect(wrapper.get('[data-testid="sales-screen"]').attributes('data-print-state')).toBe('excluded');
    expect(document.body.classList.contains('sales-order-printing')).toBe(true);

    const [, printOptions] = printSalesDocumentMock.mock.calls[0] as [HTMLElement, { onPrintDialogClose: () => void }];
    expect(printOptions.onPrintDialogClose).toEqual(expect.any(Function));
    printOptions.onPrintDialogClose();
    expect(document.body.classList.contains('sales-order-printing')).toBe(false);
  });

  it('renders the agreed B print layout with business sections and only actual detail rows', async () => {
    const wrapper = mountView(createService());
    await flushPromises();

    saveCurrentUser({
      accessToken: 'test-token',
      mobile: '13800138000',
      roles: ['sales'],
      permissions: ['sales:order:create'],
      loginMethod: 'password'
    });

    await selectCustomer(wrapper, 5);
    await wrapper.get('[data-testid="sales-transport"]').setValue('delivery');
    await wrapper.get('[data-testid="sales-settlement"]').setValue('monthly');
    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-freight"]').setValue('10');
    await wrapper.get('[data-testid="sales-received"]').setValue('15');
    await wrapper.get('[data-testid="sales-remark"]').setValue('客户要求送货上门');
    await wrapper.get('[data-testid="sales-print"]').trigger('click');
    await flushPromises();

    const printDocument = getPrintDocument();
    const printText = printDocument.textContent ?? '';
    expect(printSalesDocumentMock).toHaveBeenCalledTimes(1);
    expect(printText).toContain('出库送货单');
    expect(printText).toContain('BeBefish ERP');
    expect(printText).toContain('客户信息');
    expect(printText).toContain('发货信息');
    expect(printText).toContain('杭州酒店用品店');
    expect(printText).toContain('张三');
    expect(printText).toContain('浙江省杭州市西湖区文三路 88 号');
    expect(printText).toContain('杭州主仓');
    expect(printText).toContain('送货上门');
    expect(printText).toContain('结算周期');
    expect(printText).toContain('月结');
    expect(printText).toContain('收款状态');
    expect(printText).toContain('部分收款');
    expect(printText).toContain('开票状态');
    expect(printText).toContain('未开票');
    expect(printText).not.toContain('SKU 编码');
    expect(printText).toContain('包装');
    expect(printText).toContain('箱规');
    expect(printText).toContain('条形码');
    expect(printText).toContain('商品名称');
    expect(printText).toContain('GB-101');
    expect(printText).toContain('GB-101-CLEAR');
    expect(printText).toContain('彩盒');
    expect(printText).toContain('48 件/箱');
    expect(printText).toContain('商品金额');
    expect(printText).toContain('应收合计');
    expect(printText).not.toContain('本次已收');
    expect(printText).not.toContain('尚欠金额');
    expect(printText).toContain('金额大写');
    expect(printText).toContain('贰拾贰元整');
    expect(printText).toContain('包装与交付说明');
    expect(printText).toContain('库管员');
    expect(printText).toContain('业务代表');
    const businessRepresentative = printDocument.querySelector('.sales-print-business-representative');
    expect(businessRepresentative?.textContent).toContain('13800138000');
    expect(businessRepresentative?.querySelector('.sales-print-signature-line .sales-print-signature-name')?.textContent).toBe('13800138000');
    expect(printText).toContain('客户签字');
    expect(printDocument.querySelector('.sales-print-signatures')).not.toBeNull();
    expect(printText).toContain('客户要求送货上门');
    expect(printDocument.querySelectorAll('[data-testid="sales-print-detail-row"]')).toHaveLength(1);
    expect(printDocument.querySelectorAll('[data-testid="sales-print-summary-row"]')).toHaveLength(4);
    expect(printDocument.querySelector('.sales-print-summary-row--total')?.textContent).toContain('应收合计');
    expect(printDocument.querySelector('[data-testid="sales-print-total-amount"]')?.textContent).toBe('¥22.00');
    expect(printDocument.parentElement).toBe(document.body);
    expect(printDocument.querySelector('.sales-print-summary-row--chinese')?.classList.contains('sales-print-summary-row--inline-value')).toBe(true);

    const [, printOptions] = printSalesDocumentMock.mock.calls[0] as [HTMLElement, { onPrintDialogClose: () => void }];
    printOptions.onPrintDialogClose();
  });

  it('removes the editable sales screen from print flow when printing multiple products', async () => {
    const service = {
      loadCatalog: vi.fn().mockResolvedValue({
        ...catalog,
        products: [...catalog.products, {
          skuId: 2002,
          skuCode: 'PRD-000101-002',
          itemNo: 'GB-102',
          barcode: 'GB-102-SMOKE',
          productName: '高硼硅玻璃杯',
          skuName: '烟灰款',
          specification: '烟灰',
          packagingMethod: '彩盒',
          cartonQuantity: 48,
          salesUnit: '只',
          defaultSalePrice: 11
        }]
      })
    };
    const wrapper = mountView(service);
    await flushPromises();

    await wrapper.get('[data-testid="sales-product-input"]').trigger('focus');
    await wrapper.get('[data-testid="sales-product-option-2001"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-product-input"]').setValue('GB-102');
    await wrapper.get('[data-testid="sales-product-option-2002"]').trigger('click');
    await wrapper.get('[data-testid="sales-add-product"]').trigger('click');
    await wrapper.get('[data-testid="sales-print"]').trigger('click');
    await flushPromises();

    expect(printSalesDocumentMock).toHaveBeenCalledTimes(1);
    const printDocument = getPrintDocument();
    expect(printDocument.querySelectorAll('[data-testid="sales-print-detail-row"]')).toHaveLength(2);
    expect(wrapper.get('[data-testid="sales-screen"]').classes()).toContain('sales-screen--print-hidden');

    const [, printOptions] = printSalesDocumentMock.mock.calls[0] as [HTMLElement, { onPrintDialogClose: () => void }];
    printOptions.onPrintDialogClose();
  });
});
