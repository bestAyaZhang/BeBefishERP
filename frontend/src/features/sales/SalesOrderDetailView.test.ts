import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearMessages, messages } from '../../components/feedback/message';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import router from '../../router';
import SalesOrderDetailView from './SalesOrderDetailView.vue';
import type { SalesOrderDetail, SalesOrderService } from './types';

const detail: SalesOrderDetail = {
  id: 99,
  orderNo: 'SO202607190001',
  customerId: 1,
  customerName: '杭州酒店用品店',
  warehouseId: 10,
  warehouseName: '杭州主仓',
  orderDate: '2026-07-19',
  salespersonMobile: '13800138000',
  status: 'draft',
  transportMethod: 'delivery',
  settlementCycle: 'monthly',
  paymentMethod: null,
  deliveryAddress: '浙江省杭州市西湖区文三路88号',
  logisticsCompany: null,
  trackingNo: null,
  packageNote: null,
  invoiceRequired: false,
  invoiceStatus: 'not_required',
  goodsAmount: 25,
  discountAmount: 0,
  freight: 10,
  totalAmount: 35,
  receivedAmount: 15,
  outstandingAmount: 20,
  remark: '送货前联系',
  createdAt: '2026-07-19T10:00:00',
  updatedAt: '2026-07-19T10:00:00',
  items: [{
    id: 1,
    skuId: 20,
    quantity: 2,
    defaultUnitPrice: 12.5,
    unitPrice: 12.5,
    discountRate: 0,
    amount: 25,
    standardCostSnapshot: 3,
    itemNoSnapshot: 'EW43245',
    productNameSnapshot: '高硼硅玻璃杯',
    skuCodeSnapshot: 'PRD-000001-001',
    skuNameSnapshot: '透明款',
    specificationSnapshot: '透明',
    packagingSnapshot: '彩盒',
    cartonQuantitySnapshot: 48,
    barcodeSnapshot: 'BAR-001',
    salesUnitSnapshot: '只'
  }]
};

function createService(order: SalesOrderDetail = detail): SalesOrderService & { getOrder: NonNullable<SalesOrderService['getOrder']>; deleteOrder: NonNullable<SalesOrderService['deleteOrder']>; voidOrder: NonNullable<SalesOrderService['voidOrder']>; confirmSavedOrder: NonNullable<SalesOrderService['confirmSavedOrder']> } {
  return {
    saveDraft: vi.fn(),
    confirmOrder: vi.fn(),
    confirmSavedOrder: vi.fn().mockResolvedValue({ ...order, status: 'confirmed' }),
    getOrder: vi.fn().mockResolvedValue(order),
    deleteOrder: vi.fn().mockResolvedValue(undefined),
    voidOrder: vi.fn().mockResolvedValue({ ...order, status: 'void' })
  };
}

describe('SalesOrderDetailView', () => {
  afterEach(() => {
    clearMessages();
    clearCurrentUser();
    vi.restoreAllMocks();
  });

  function allowConfirmation() {
    saveCurrentUser({ accessToken: 'test', mobile: '13800138000', roles: [], permissions: ['sales:create'], loginMethod: 'password' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  }

  it('confirms a saved draft and updates the stock status without permitting repeated clicks', async () => {
    allowConfirmation();
    const service = createService();
    let finishConfirmation!: (order: SalesOrderDetail) => void;
    vi.mocked(service.confirmSavedOrder).mockImplementation(() => new Promise(resolve => { finishConfirmation = resolve; }));
    const wrapper = mount(SalesOrderDetailView, { props: { service, orderId: 99 }, global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.get('[data-testid="sales-order-stock-status"]').text()).toContain('草稿暂未扣库存');
    await wrapper.get('[data-testid="sales-order-detail-confirm"]').trigger('click');
    await wrapper.get('[data-testid="sales-order-detail-confirm"]').trigger('click');
    finishConfirmation({ ...detail, status: 'confirmed' });
    await flushPromises();
    expect(service.confirmSavedOrder).toHaveBeenCalledTimes(1);
    expect(service.confirmSavedOrder).toHaveBeenCalledWith(99);
    expect(wrapper.find('[data-testid="sales-order-detail-confirm"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="sales-order-stock-status"]').text()).toContain('库存已从所选仓库扣减');
    expect(wrapper.find('[data-testid="sales-order-detail-print"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keeps an insufficient-stock order as a draft and shows the reason', async () => {
    allowConfirmation();
    const service = createService();
    vi.mocked(service.confirmSavedOrder).mockRejectedValue(new Error('库存不足'));
    const wrapper = mount(SalesOrderDetailView, { props: { service, orderId: 99 }, global: { plugins: [router] } });
    await flushPromises();
    await wrapper.get('[data-testid="sales-order-detail-confirm"]').trigger('click');
    await flushPromises();
    expect(messages.value.at(-1)?.text).toBe('库存不足');
    expect(wrapper.get('[data-testid="sales-order-stock-status"]').text()).toContain('草稿暂未扣库存');
    expect(wrapper.get('[data-testid="sales-order-detail-confirm"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('does not show confirmation without sales creation permission', async () => {
    const wrapper = mount(SalesOrderDetailView, { props: { service: createService(), orderId: 99 }, global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.find('[data-testid="sales-order-detail-confirm"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('loads and renders a sales order detail as a standalone page', async () => {
    const service = createService();
    const wrapper = mount(SalesOrderDetailView, {
      props: { service, orderId: 99 },
      global: { plugins: [router] }
    });
    await flushPromises();

    expect(service.getOrder).toHaveBeenCalledWith(99);
    expect(wrapper.get('[data-testid="sales-order-detail-page"]').text()).toContain('SO202607190001');
    expect(wrapper.get('[data-testid="sales-order-detail-page"]').text()).toContain('杭州酒店用品店');
    expect(wrapper.get('[data-testid="sales-order-detail-items"]').text()).toContain('EW43245');
    expect(wrapper.get('[data-testid="sales-order-detail-total"]').text()).toBe('¥35.00');
  });

  it('deletes a draft and returns to the sales order list', async () => {
    const service = createService();
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined as never);
    const wrapper = mount(SalesOrderDetailView, {
      props: { service, orderId: 99 },
      global: { plugins: [router] }
    });
    await flushPromises();

    await wrapper.get('[data-testid="sales-order-detail-delete"]').trigger('click');
    await flushPromises();

    expect(service.deleteOrder).toHaveBeenCalledWith(99);
    expect(messages.value.at(-1)?.text).toContain('销售单已删除');
    expect(push).toHaveBeenCalledWith({ name: 'sales-orders', query: { deleted: 'SO202607190001' } });
  });

  it('voids a confirmed order with a required reason and refreshes its status', async () => {
    const confirmed = { ...detail, status: 'confirmed' as const };
    const service = createService(confirmed);
    const wrapper = mount(SalesOrderDetailView, {
      props: { service, orderId: 99 },
      global: { plugins: [router] }
    });
    await flushPromises();

    await wrapper.get('[data-testid="sales-order-detail-void"]').trigger('click');
    expect(document.querySelector('[data-testid="sales-order-detail-void-dialog"]')?.textContent).toContain('作废原因');

    const reasonInput = document.querySelector<HTMLTextAreaElement>('[data-testid="sales-order-detail-void-reason"]');
    if (!reasonInput) throw new Error('作废原因输入框未渲染');
    reasonInput.value = '客户取消订单';
    reasonInput.dispatchEvent(new Event('input'));
    document.querySelector<HTMLButtonElement>('[data-testid="sales-order-detail-void-submit"]')?.click();
    await flushPromises();

    expect(service.voidOrder).toHaveBeenCalledWith(99, '客户取消订单');
    expect(messages.value.at(-1)?.text).toContain('销售单已作废');
    expect(wrapper.get('[data-testid="sales-order-detail-page"]').text()).toContain('已作废');
  });
});
