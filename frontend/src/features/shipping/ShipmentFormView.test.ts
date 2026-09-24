import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import ShipmentFormView from './ShipmentFormView.vue';
import type { Shipment } from './types';

const service = vi.hoisted(() => ({ formOptions: vi.fn(), logisticsAvailability: vi.fn(), get: vi.fn(),
  getLogisticsOrder: vi.fn(), create: vi.fn(), update: vi.fn(), placeLogisticsOrder: vi.fn() }));
vi.mock('./shippingService', () => ({ shippingService: service }));

function shipment(overrides: Partial<Shipment> = {}): Shipment {
  const base: Shipment = {
    id: 18, shipmentNo: 'FH20260924-18', version: 0, createdBy: 'employee:1', updatedBy: 'employee:1',
    createdAt: '2026-09-24T09:00:00', updatedAt: '2026-09-24T09:00:00',
    content: { shipmentDate: '2026-09-24', platform: '淘宝', shopName: '贝贝鱼淘宝旗舰店', preparers: ['小周', '阿杰'],
      recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省', recipientCity: '杭州市',
      recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室', preparationContent: '水族箱 × 2\n滤材 × 6',
      remark: '', estimatedFreight: null, orderDraft: { cargoName: '水族用品', packType: '纸箱', weight: 18.5,
        volume: 0.12, pieceAmount: 2, productTypeId: 524, goodsType: 180, payType: 104, logisticsRemark: '' },
      status: 'unfinished', orderer: '李主管', logisticsCompany: '', trackingNo: '' }
  };
  return { ...base, ...overrides };
}

async function render(path = '/shipping/new') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/shipping/list', name: 'shipping-list', component: { template: '<div>list</div>' } },
    { path: '/shipping/new', name: 'shipping-new', component: ShipmentFormView },
    { path: '/shipping/:id/edit', name: 'shipping-edit', component: ShipmentFormView },
    { path: '/shipping/:id', name: 'shipping-detail', component: { template: '<div>detail</div>' } }
  ] });
  await router.push(path); await router.isReady();
  const wrapper = mount(ShipmentFormView, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

async function fillRequired(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('[data-testid="shipment-platform"]').setValue('淘宝');
  await wrapper.get('[data-testid="preparer-toggle"]').trigger('click');
  await wrapper.get('[data-testid="preparer-option-1"]').trigger('click');
  await wrapper.get('[data-testid="preparer-option-2"]').trigger('click');
  await wrapper.get('[data-testid="recipient-raw"]').setValue('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室');
  await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');
  await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('水族箱 × 2\n滤材 × 6');
}

beforeEach(() => {
  localStorage.clear();
  clearCurrentUser();
  saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '李主管', roles: [],
    permissions: ['shipping:view', 'shipping:create', 'shipping:edit', 'shipping:order'], loginMethod: 'feishu' });
  service.formOptions.mockReset().mockResolvedValue({ shopNames: ['贝贝鱼淘宝旗舰店'], preparers: [
    { employeeId: 1, employeeName: '小周' }, { employeeId: 2, employeeName: '阿杰' }] });
  service.logisticsAvailability.mockReset().mockResolvedValue({ available: true, testEnvironment: false, message: '安能物流下单服务已配置' });
  service.get.mockReset().mockResolvedValue(shipment());
  service.getLogisticsOrder.mockReset().mockResolvedValue(null);
  service.create.mockReset().mockResolvedValue(shipment());
  service.update.mockReset().mockResolvedValue(shipment({ version: 1 }));
  service.placeLogisticsOrder.mockReset().mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'succeeded',
    trackingNo: 'ANE202609180018', childTrackingNos: '', message: '成功', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
});

afterEach(clearCurrentUser);

describe('ShipmentFormView', () => {
  it('keeps server fields hidden, recognizes inline and saves the full-width form', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.find('[data-testid="shipment-date"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-orderer"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-status"]').exists()).toBe(false);
    expect((wrapper.get('[data-testid="shipment-shop"]').element as HTMLSelectElement).value).toBe('贝贝鱼淘宝旗舰店');
    await fillRequired(wrapper);
    expect((wrapper.get('[data-testid="recipient-county"]').element as HTMLInputElement).value).toBe('余杭区');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ shopName: '贝贝鱼淘宝旗舰店',
      preparers: ['小周', '阿杰'], preparationContent: '水族箱 × 2\n滤材 × 6' }));
    expect(router.currentRoute.value.name).toBe('shipping-detail');
    wrapper.unmount();
  });

  it('creates once, submits the returned version and opens the result dialog', async () => {
    const { wrapper } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledTimes(1);
    expect(service.placeLogisticsOrder).toHaveBeenCalledWith(18, { version: 0 });
    expect(wrapper.get('[role="dialog"]').text()).toContain('ANE202609180018');
    wrapper.unmount();
  });

  it('hides the logistics order action without shipping:order permission', async () => {
    saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '录入人', roles: [],
      permissions: ['shipping:view', 'shipping:create'], loginMethod: 'feishu' });
    const { wrapper } = await render();
    expect(wrapper.find('[data-testid="shipment-save-and-order"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-save-only"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keeps the saved shipment and routes to edit when ordering fails', async () => {
    service.placeLogisticsOrder.mockRejectedValue(new Error('安能暂时不可用'));
    const { wrapper, router } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value).toMatchObject({ name: 'shipping-edit', params: { id: '18' } });
    expect(wrapper.get('[role="alert"]').text()).toContain('发货单已保存');
    wrapper.unmount();
  });

  it('disables save actions while a request is active', async () => {
    let release!: (value: Shipment) => void;
    service.create.mockReturnValue(new Promise<Shipment>(resolve => { release = resolve; }));
    const { wrapper } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    release(shipment()); await flushPromises();
    wrapper.unmount();
  });

  it('locks order fields after submission but updates preparation, remark and status with the loaded values', async () => {
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'processing', trackingNo: '',
      childTrackingNos: '', message: '提交中', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
    const { wrapper } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-platform"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="recipient-name"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('已发第一批');
    await wrapper.get('[data-testid="shipment-remark"]').setValue('剩余待补');
    await wrapper.get('[data-testid="shipment-status"]').setValue('partially_shipped');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ platform: '淘宝', recipientName: '林女士',
      preparationContent: '已发第一批', remark: '剩余待补' }), 'partially_shipped', 0);
    wrapper.unmount();
  });
});
