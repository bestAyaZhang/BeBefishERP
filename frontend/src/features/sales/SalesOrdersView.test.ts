import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import { clearMessages, messages } from '../../components/feedback/message';
import router from '../../router';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../types/auth';
import { mockSalesOrderService } from './mockSalesOrderService';
import SalesOrdersView from './SalesOrdersView.vue';

describe('SalesOrdersView', () => {
  afterEach(() => {
    clearMessages();
  });

  it('shows sales orders and routes the add action to the order form', async () => {
    await router.push('/sales/orders');
    await router.isReady();
    const wrapper = mount(SalesOrdersView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="sales-orders-page"]').text()).toContain('销售单据');
    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).toContain('SO-20260713-001');
    expect(wrapper.get('[data-testid="add-sales-order"]').attributes('href')).toBe('/sales/create');
  });

  it('shows a success tip for a newly confirmed order', async () => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test-token');
    await router.push({ name: 'sales-orders', query: { created: 'SO-20260713-003' } });
    const wrapper = mount(SalesOrdersView, { global: { plugins: [router] } });
    await flushPromises();

    expect(messages.value.at(-1)?.type).toBe('success');
    expect(messages.value.at(-1)?.text).toContain('SO-20260713-003');
  });

  it('shows a saved draft in the draft tab and filters other statuses out', async () => {
    const result = await mockSalesOrderService.saveDraft({
      customerId: 5,
      customerName: '杭州酒店用品店',
      warehouseId: 8,
      warehouseName: '杭州主仓',
      orderDate: '2026-07-13',
      transportMethod: 'delivery',
      settlementCycle: 'cash',
      freight: 0,
      totalAmount: 12,
      receivedAmount: 0,
      receivableAmount: 12,
      remark: '待确认',
      lines: [{ skuId: 2001, quantity: 1, unitPrice: 12, amount: 12 }]
    });
    await router.push('/sales/orders');
    const wrapper = mount(SalesOrdersView, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.get('[data-testid="sales-status-tab-draft"]').trigger('click');

    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).toContain(result.orderNo);
    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).toContain('草稿');
    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).not.toContain('SO-20260713-001');
    expect(wrapper.get('[data-testid="sales-orders-footer-count"]').text()).toContain('共 2 条');
  });

  it('shows an order created by the mock confirmation service', async () => {
    const result = await mockSalesOrderService.confirmOrder({
      customerId: 5,
      customerName: '杭州酒店用品店',
      warehouseId: 8,
      warehouseName: '杭州主仓',
      orderDate: '2026-07-13',
      transportMethod: 'delivery',
      settlementCycle: 'cash',
      freight: 0,
      totalAmount: 66,
      receivedAmount: 66,
      receivableAmount: 0,
      remark: '',
      lines: [{ skuId: 2001, quantity: 2, unitPrice: 33, amount: 66 }]
    });
    await router.push('/sales/orders');
    const wrapper = mount(SalesOrdersView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).toContain(result.orderNo);
    expect(wrapper.get('[data-testid="sales-orders-table"]').text()).toContain('¥66.00');
  });
});
