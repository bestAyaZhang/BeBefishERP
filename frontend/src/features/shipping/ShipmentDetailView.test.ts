import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ShipmentDetailView from './ShipmentDetailView.vue';

const service = vi.hoisted(() => ({ get: vi.fn(), getLogisticsOrder: vi.fn() }));
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
  service.get.mockReset().mockResolvedValue(shipment);
  service.getLogisticsOrder.mockReset().mockResolvedValue({ shipmentId: 8, orderNo: 'BF008', state: 'succeeded',
    trackingNo: 'ANE001', childTrackingNos: '', message: '下单成功', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
});

describe('ShipmentDetailView', () => {
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
