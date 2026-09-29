import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import ShipmentListView from './ShipmentListView.vue';
import type { Shipment } from './types';

const service = vi.hoisted(() => ({ list: vi.fn(), summary: vi.fn() }));
vi.mock('./shippingService', () => ({ shippingService: service }));

function record(id = 8): Shipment {
  return {
    id, shipmentNo: `FH20260924-${id}`, version: 3, createdBy: 'employee:1', updatedBy: 'employee:1',
    logisticsOrderState: 'succeeded',
    createdAt: '2026-09-24T09:00:00', updatedAt: '2026-09-24T10:00:00',
    content: {
      shipmentDate: '2026-09-24', platform: '淘宝', shopName: '贝贝鱼淘宝旗舰店', preparers: ['小周', '阿杰'],
      senderName: '测试发货人', senderPhone: '13800000000', senderProvince: '浙江省', senderCity: '杭州市',
      senderCounty: '余杭区', senderDetailAddress: '测试路1号',
      recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省', recipientCity: '杭州市',
      recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室', preparationContent: '水族箱 × 2\n滤材 × 6',
      remark: '外箱加固', estimatedFreight: 58, orderDraft: { cargoName: '水族用品', packType: '纸箱', weight: 18.5,
        volume: 0.12, pieceAmount: 2, productTypeId: 524, goodsType: 180, payType: 104, logisticsRemark: '' },
      status: 'unfinished', orderer: '李主管', logisticsCompany: '安能物流', trackingNo: 'ANE001'
    }
  };
}

async function render(permissions = ['shipping:view', 'shipping:create', 'shipping:edit']) {
  saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '李主管', roles: [], permissions, loginMethod: 'feishu' });
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/shipping/list', name: 'shipping-list', component: ShipmentListView },
    { path: '/shipping/:id', name: 'shipping-detail', component: { template: '<div>detail</div>' } }
  ] });
  await router.push('/shipping/list'); await router.isReady();
  const wrapper = mount(ShipmentListView, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

beforeEach(() => {
  clearCurrentUser();
  service.summary.mockReset().mockResolvedValue({ todayCount: 7, unfinishedCount: 3, completedCount: 2, outOfStockCount: 1, partiallyShippedCount: 1 });
  service.list.mockReset().mockResolvedValue({ records: [record()], page: 1, pageSize: 20, total: 1 });
});

describe('ShipmentListView', () => {
  it('shows the Figma summary, masked recipient and routes to detail', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.get('[data-testid="shipment-summary-today"]').text()).toContain('7 单');
    expect(wrapper.text()).toContain('138****6028');
    expect(wrapper.text()).toContain('水族箱 × 2\n滤材 × 6');
    await wrapper.get('[data-testid="shipment-detail-8"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value).toMatchObject({ name: 'shipping-detail', params: { id: '8' } });
    wrapper.unmount();
  });

  it('shows ANE order status separately from preparation status', async () => {
    const item = record();
    item.content.status = 'out_of_stock';
    item.logisticsOrderState = 'succeeded';
    service.list.mockResolvedValue({ records: [item], page: 1, pageSize: 20, total: 1 });

    const { wrapper } = await render();
    expect(wrapper.get('[data-testid="shipment-logistics-status-8"]').text()).toContain('下单成功');
    expect(wrapper.get('[data-testid="shipment-preparation-status-8"]').text()).toContain('缺货');
    expect(wrapper.get('[data-testid="shipment-filter-status-label"]').text()).toContain('备货状态');
    wrapper.unmount();
  });

  it('keeps dense rows compact while preserving full shipment and preparation details', async () => {
    const item = record();
    item.shipmentNo = 'FH20260928-0DA07473BC63479F97353F0C33BCA6B4';
    item.content.preparationContent = '商品一 × 5\n商品二 × 8\n商品三 × 3\n商品四 × 12\n商品五 × 6';
    item.content.remark = '这是一段较长的备货备注信息，需要在列表中保持单行展示';
    service.list.mockResolvedValue({ records: [item], page: 1, pageSize: 20, total: 1 });

    const { wrapper } = await render();
    const shipmentNo = wrapper.get('[data-testid="shipment-number-8"]');
    expect(shipmentNo.text()).toBe('FH20260928-0DA074…');
    expect(shipmentNo.attributes('title')).toBe(item.shipmentNo);
    expect(wrapper.get('[data-testid="shipment-preparation-8"]').classes()).toContain('line-clamp-3');
    expect(wrapper.get('[data-testid="shipment-preparation-8"]').attributes('title')).toBe(item.content.preparationContent);
    expect(wrapper.get('[data-testid="shipment-remark-8"]').classes()).toContain('line-clamp-1');
    expect(wrapper.get('[data-testid="shipment-remark-8"]').attributes('title')).toBe(`备注：${item.content.remark}`);
    wrapper.unmount();
  });

  it('supports incomplete, explicit filters, pagination and reset-to-today', async () => {
    service.list.mockResolvedValue({ records: [record()], page: 1, pageSize: 20, total: 45 });
    const { wrapper } = await render();
    await wrapper.get('[data-testid="shipment-scope-incomplete"]').trigger('click'); await flushPromises();
    expect(service.list).toHaveBeenLastCalledWith(expect.objectContaining({ incompleteOnly: true, dateFrom: '', dateTo: '' }));
    await wrapper.get('[data-testid="shipment-date-from"]').setValue('2026-09-01');
    await wrapper.get('[data-testid="shipment-filter-status"]').setValue('out_of_stock');
    await wrapper.get('[data-testid="shipment-platform"]').setValue('淘宝');
    await wrapper.get('[data-testid="shipment-filter-form"]').trigger('submit'); await flushPromises();
    expect(service.list).toHaveBeenLastCalledWith(expect.objectContaining({ dateFrom: '2026-09-01', status: 'out_of_stock', platform: '淘宝' }));
    await wrapper.get('[data-testid="shipment-next-page"]').trigger('click'); await flushPromises();
    expect(service.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
    await wrapper.get('[data-testid="shipment-reset"]').trigger('click'); await flushPromises();
    expect(service.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, incompleteOnly: false }));
    wrapper.unmount();
  });

  it('shows independent list failures with retry and hides create without permission', async () => {
    service.list.mockRejectedValueOnce(new Error('接口服务暂不可用')).mockResolvedValueOnce({ records: [], page: 1, pageSize: 20, total: 0 });
    const { wrapper } = await render(['shipping:view']);
    expect(wrapper.find('[data-testid="shipment-add"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="shipment-load-error"]').text()).toContain('接口服务暂不可用');
    await wrapper.get('[data-testid="shipment-retry"]').trigger('click'); await flushPromises();
    expect(wrapper.text()).toContain('当前条件下暂无发货记录');
    wrapper.unmount();
  });
});
