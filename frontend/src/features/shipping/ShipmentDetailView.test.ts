import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ShipmentDetailView from './ShipmentDetailView.vue';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { clearMessages, messages } from '../../components/feedback/message';

const service = vi.hoisted(() => ({ get: vi.fn(), getLogisticsOrder: vi.fn(), cancelLogisticsOrder: vi.fn(), updatePreparation: vi.fn() }));
enableAutoUnmount(afterEach);
afterEach(clearMessages);
vi.mock('./shippingService', () => ({ shippingService: service }));

const shipment = {
  id: 8, shipmentNo: 'FH20260924-8', version: 3, createdBy: 'employee:1', updatedBy: 'employee:2',
  createdAt: '2026-09-24T09:00:00', updatedAt: '2026-09-24T10:00:00',
  content: { shipmentDate: '2026-09-24', platform: '淘宝', shopName: '贝贝鱼淘宝旗舰店', preparers: ['小周', '阿杰'],
    senderName: '本单发货人', senderPhone: '13900000000', senderProvince: '浙江省', senderCity: '金华市',
    senderCounty: '东阳市', senderDetailAddress: '测试发货路2号',
    recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省', recipientCity: '杭州市',
    recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室', preparationContent: '水族箱 × 2\n滤材 × 6',
    remark: '外箱加固', estimatedFreight: 58, orderDraft: { cargoName: '水族用品', packType: '纸箱', weight: 18.5,
      volume: 0.12, pieceAmount: 2, productTypeId: 524, goodsType: 180, payType: 104, logisticsRemark: '轻放' },
    status: 'partially_shipped', orderer: '李主管', logisticsCompany: '安能物流', trackingNo: 'ANE001' }
};

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/shipping/list', name: 'shipping-list', component: { template: '<div>list</div>' } },
    { path: '/shipping/:id', name: 'shipping-detail', component: ShipmentDetailView }
  ] });
  await router.push('/shipping/8'); await router.isReady();
  const wrapper = mount(ShipmentDetailView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

beforeEach(() => {
  clearMessages();
  clearCurrentUser();
  saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '测试用户', roles: [],
    permissions: ['shipping:view', 'shipping:cancel'], loginMethod: 'feishu' });
  service.cancelLogisticsOrder.mockReset();
  service.get.mockReset().mockResolvedValue(shipment);
  service.getLogisticsOrder.mockReset().mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'succeeded',
    trackingNo: 'ANE001', childTrackingNos: '', message: '下单成功', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
});

describe('ShipmentDetailView', () => {
  it('updates preparation feedback and operator details without changing the carrier status', async () => {
    saveCurrentUser({ accessToken: 'test', employeeId: 42, mobile: null, displayName: '小周', roles: [],
      permissions: ['shipping:view', 'shipping:prepare'], loginMethod: 'feishu' });
    service.get.mockResolvedValue({ ...shipment, preparerEmployeeIds: [42] });
    service.updatePreparation.mockResolvedValue({ ...shipment, preparerEmployeeIds: [42], version: 4,
      content: { ...shipment.content, status: 'completed' },
      preparation: { actualWeight: 19.235, updatedBy: '小周', updatedAt: '2026-10-08T11:00:00' } });
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-detail-prepare"]').trigger('click'); await flushPromises();
    await wrapper.get('[data-testid="preparation-status"]').setValue('completed');
    await wrapper.get('[data-testid="preparation-actual-weight"]').setValue('19.235');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-detail-actual-weight"]').text()).toBe('19.235 kg');
    expect(wrapper.get('[data-testid="shipment-detail-preparation-status"]').text()).toContain('已完成');
    expect(wrapper.get('[data-testid="shipment-detail-logistics-status"]').text()).toContain('下单成功');
    expect(wrapper.text()).toContain('小周');
    expect(messages.value).toEqual([expect.objectContaining({ type: 'success', text: '备货状态与实际重量已保存' })]);
    expect(wrapper.text()).not.toContain('备货状态与实际重量已保存');
    wrapper.unmount();
  });
  it('disables duplicate confirmations while the cancellation request is pending', async () => {
    let resolve!: (value: unknown) => void;
    service.cancelLogisticsOrder.mockReturnValue(new Promise(done => { resolve = done; }));
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-cancel-order"]').trigger('click');
    await wrapper.get('[data-testid="cancel-order-confirm"]').trigger('click');
    expect(wrapper.get('[data-testid="cancel-order-confirm"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="cancel-order-confirm"]').trigger('click');
    expect(service.cancelLogisticsOrder).toHaveBeenCalledTimes(1);
    resolve({ shipmentId: 8, orderNo: 'BF008', state: 'cancel_unknown', trackingNo: 'ANE001',
      childTrackingNos: '', message: '取消待核实', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' });
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'cancel_unknown', trackingNo: 'ANE001',
      childTrackingNos: '', message: '取消待核实', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' });
    await flushPromises();
    expect(wrapper.get('[data-testid="shipment-detail-logistics-status"]').text()).toContain('取消待核实');
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(false);
    expect(messages.value).toEqual([expect.objectContaining({ type: 'warning', text: '取消待核实' })]);
    wrapper.unmount();
  });
  it('confirms before cancellation then shows the cancelled waybill as history without changing preparation', async () => {
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-cancel-order"]').trigger('click');
    expect(wrapper.get('[role="dialog"]').text()).toContain('揽收');
    expect(wrapper.get('[role="dialog"]').text()).toContain('ANE001');
    expect(service.cancelLogisticsOrder).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="cancel-order-dismiss"]').trigger('click');
    expect(service.cancelLogisticsOrder).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="shipment-cancel-order"]').trigger('click');
    service.cancelLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'cancelled', trackingNo: 'ANE001',
      childTrackingNos: '', message: '订单已取消', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' });
    service.get.mockResolvedValue({ ...shipment, version: 5, content: { ...shipment.content, trackingNo: '' } });
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'cancelled', trackingNo: 'ANE001',
      childTrackingNos: '', message: '订单已取消', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' });
    await wrapper.get('[data-testid="cancel-order-confirm"]').trigger('click'); await flushPromises();
    expect(service.cancelLogisticsOrder).toHaveBeenCalledWith(8, { version: 3 });
    expect(wrapper.get('[data-testid="shipment-detail-logistics-status"]').text()).toContain('已取消');
    expect(wrapper.get('[data-testid="shipment-real-tracking"]').text()).toContain('已作废');
    expect(wrapper.get('[data-testid="shipment-detail-preparation-status"]').text()).toContain('部分发货');
    expect(messages.value).toEqual([expect.objectContaining({ type: 'success', text: '订单已取消' })]);
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it.each(['cancel_processing', 'cancel_unknown', 'cancelled', 'unknown', 'processing'])('hides cancellation for %s', async state => {
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state, trackingNo: 'ANE001',
      childTrackingNos: '', message: '当前订单需核实', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' });
    const { wrapper } = await render();
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('requires cancel permission even when the user can place orders', async () => {
    saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '测试用户', roles: [],
      permissions: ['shipping:view', 'shipping:order'], loginMethod: 'feishu' });
    const { wrapper } = await render();
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('blocks another submission after transport failure until the saved state has been refreshed', async () => {
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-cancel-order"]').trigger('click');
    service.cancelLogisticsOrder.mockRejectedValue(new Error('连接中断'));
    service.getLogisticsOrder.mockRejectedValue(new Error('连接中断'));
    await wrapper.get('[data-testid="cancel-order-confirm"]').trigger('click'); await flushPromises();
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(false);
    expect(messages.value).toEqual([expect.objectContaining({ type: 'warning', text: expect.stringContaining('核实') })]);
    expect(wrapper.text()).toContain('物流状态尚未核实');
    wrapper.unmount();
  });

  it('keeps a rejected cancellation active and displays its reason', async () => {
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-cancel-order"]').trigger('click');
    const rejected = { shipmentId: 8, orderNo: 'BF008', state: 'cancel_rejected', trackingNo: 'ANE001',
      childTrackingNos: '', message: '已揽收，无法取消', testEnvironment: false, updatedAt: '2026-09-30T10:00:00' };
    service.cancelLogisticsOrder.mockResolvedValue(rejected);
    service.getLogisticsOrder.mockResolvedValue(rejected);
    await wrapper.get('[data-testid="cancel-order-confirm"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-real-tracking"]').text()).toBe('ANE001');
    expect(wrapper.get('[data-testid="shipment-order-message"]').text()).toContain('已揽收');
    expect(messages.value).toEqual([expect.objectContaining({ type: 'error', text: '已揽收，无法取消' })]);
    expect(wrapper.find('[data-testid="shipment-cancel-order"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows recipient, preparation, record and logistics information', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.get('[data-testid="shipment-detail-page"]').text()).toContain('浙江省杭州市余杭区示例路18号2栋101室');
    expect(wrapper.get('[data-testid="shipment-detail-sender"]').text()).toContain('本单发货人');
    expect(wrapper.get('[data-testid="shipment-detail-sender"]').text()).toContain('浙江省金华市东阳市测试发货路2号');
    expect(wrapper.text()).toContain('水族箱 × 2\n滤材 × 6');
    expect(wrapper.text()).toContain('贝贝鱼淘宝旗舰店');
    expect(wrapper.text()).toContain('小周、阿杰');
    expect(wrapper.text()).toContain('ANE001');
    expect(wrapper.get('[data-testid="shipment-detail-logistics-status"]').text()).toContain('下单成功');
    expect(wrapper.get('[data-testid="shipment-detail-preparation-status"]').text()).toContain('部分发货');
    await wrapper.get('[data-testid="shipment-back-list"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('shipping-list');
    wrapper.unmount();
  });

  it('keeps a test waybill separate from the real tracking number', async () => {
    service.get.mockResolvedValue({ ...shipment, content: { ...shipment.content, trackingNo: '' } });
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'succeeded',
      trackingNo: '123456789012', childTrackingNos: '', message: '测试下单成功',
      updatedAt: '2026-09-24T10:00:00', testEnvironment: true });
    const { wrapper } = await render();
    expect(wrapper.get('[data-testid="shipment-real-tracking"]').text()).toBe('暂无');
    expect(wrapper.get('[data-testid="shipment-test-order"]').text()).toContain('测试运单号');
    expect(wrapper.get('[data-testid="shipment-test-order"]').text()).toContain('123456789012');
    expect(wrapper.get('[data-testid="shipment-test-order"]').text()).toContain('不可用于实际走货');
    wrapper.unmount();
  });

  it('shows the reconciliation instruction when an order result is unknown', async () => {
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'unknown',
      trackingNo: '', childTrackingNos: '', message: '下单结果待核实，请联系安能网点核对订单号，勿重复下单',
      updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
    const { wrapper } = await render();
    expect(wrapper.text()).toContain('结果待核实');
    expect(wrapper.get('[data-testid="shipment-order-message"]').text()).toContain('请联系安能网点核对订单号，勿重复下单');
    expect(wrapper.text()).toContain('BF008');
    wrapper.unmount();
  });

  it('shows a retry action when the record cannot be loaded', async () => {
    service.get.mockRejectedValueOnce(new Error('发货单不存在')).mockResolvedValueOnce(shipment);
    const { wrapper } = await render();
    expect(wrapper.get('[data-testid="shipment-detail-error"]').text()).toContain('发货单不存在');
    await wrapper.get('[data-testid="shipment-detail-retry"]').trigger('click'); await flushPromises();
    expect(wrapper.find('[data-testid="shipment-detail-error"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
