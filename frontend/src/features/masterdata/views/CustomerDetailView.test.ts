import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../../services/authSession';
import { clearMessages } from '../../../components/feedback/message';
import type { CustomerAccountService } from '../customerAccountService';
import type { Customer } from '../types';
import { printCustomerStatement } from '../customerPrint';
import CustomerDetailView from './CustomerDetailView.vue';

vi.mock('../customerPrint', () => ({ printCustomerStatement: vi.fn() }));
vi.mock('../../sales/salesCatalogService', () => ({ loadSaleProducts: async () => [{ skuId: 20, itemNo: 'SKU20', productName: '玻璃杯', skuName: '透明', defaultSalePrice: 12, salesUnit: '只' }] }));
const customer: Customer = { id: 5, customerNo: 'C5', customerName: '客户甲', contactPerson: '张三', mobile: '13800138000', telephone: '', province: '浙江省', city: '杭州市', district: '西湖区', detailAddress: '文三路88号', transportMethod: 'delivery', settlementCycle: 'monthly', platformId: 2, system: false, status: 'enabled', remark: '' };
const allBills = Array.from({ length: 101 }, (_, i) => ({ id: i + 1, orderNo: `SO${i + 1}`, orderDate: '2026-10-14', totalAmount: 10, receivedAmount: 2, outstandingAmount: 8 }));

function fakeService(): CustomerAccountService {
  return { getCustomer: vi.fn().mockResolvedValue(customer), listPlatforms: vi.fn().mockResolvedValue([{ id: 2, name: '1688', status: 'enabled' }]),
    listPrices: vi.fn().mockResolvedValue([]), savePrice: vi.fn().mockResolvedValue([]), deletePrice: vi.fn().mockResolvedValue(undefined),
    listBills: vi.fn().mockImplementation(async (_id, q) => ({ records: allBills.slice((q.page - 1) * q.size, q.page * q.size), page: q.page, pageSize: q.size, total: 101,
      summary: { orderCount: 101, totalAmount: 1010, receivedAmount: 202, outstandingAmount: 808 } })) };
}

async function mountView(service: CustomerAccountService, permissions = ['customer:view', 'customer:edit', 'sales:view', 'sales:create']) {
  saveCurrentUser({ accessToken: 'test', mobile: '13800138000', roles: [], permissions, loginMethod: 'password' });
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/customers/:id', name: 'customer-detail', component: CustomerDetailView },
    { path: '/customers', name: 'customers', component: { template: '<div />' } },
    { path: '/customers/:id/edit', name: 'customer-edit', component: { template: '<div />' } },
    { path: '/sales/create', name: 'sales-create', component: { template: '<div />' } },
    { path: '/sales/:id', name: 'sales-order-detail', component: { template: '<div />' } }
  ] });
  await router.push('/customers/5');
  const wrapper = mount(CustomerDetailView, { global: { plugins: [router], provide: { customerAccountService: service } } });
  await flushPromises();
  return wrapper;
}

afterEach(() => { clearCurrentUser(); clearMessages(); vi.clearAllMocks(); });
describe('customer detail', () => {
  it('shows the platform and saves zero as a dedicated customer price', async () => {
    const service = fakeService(); const wrapper = await mountView(service);
    expect(wrapper.text()).toContain('1688'); expect(wrapper.text()).toContain('文三路88号');
    expect(wrapper.get('[data-testid="customer-create-order"]').attributes('href')).toContain('customerId=5');
    await wrapper.get('[data-testid="customer-price-sku"]').setValue('20');
    await wrapper.get('[data-testid="customer-price-value"]').setValue('0');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(service.savePrice).toHaveBeenCalledWith(5, 20, 0);
    wrapper.unmount();
  });

  it('shows whole-range totals and prints every page of the selected statement', async () => {
    const service = fakeService(); const wrapper = await mountView(service);
    await wrapper.get('[data-testid="customer-bills-tab"]').trigger('click'); await flushPromises();
    expect(wrapper.text()).toContain('¥1010.00'); expect(wrapper.text()).toContain('共 101 单');
    expect(wrapper.findAll('tbody tr')).toHaveLength(20);
    await wrapper.get('[data-testid="customer-bills-print"]').trigger('click'); await flushPromises();
    expect(service.listBills).toHaveBeenCalledWith(5, expect.objectContaining({ page: 2, size: 100 }));
    expect(vi.mocked(printCustomerStatement).mock.calls[0][1].records).toHaveLength(101);
    wrapper.unmount();
  });

  it('hides bill and edit operations when permissions are absent', async () => {
    const wrapper = await mountView(fakeService(), ['customer:view']);
    expect(wrapper.find('[data-testid="customer-bills-tab"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="customer-price-save"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="customer-create-order"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
