import { enableAutoUnmount, mount, flushPromises } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { saveCurrentUser, clearCurrentUser } from '../../services/authSession';
import { clearMessages, messages } from '../../components/feedback/message';
import { emptyShipmentForm, type Shipment } from './types';
import ShipmentPreparationDialog from './ShipmentPreparationDialog.vue';

const service = vi.hoisted(() => ({ get: vi.fn(), updatePreparation: vi.fn() }));
enableAutoUnmount(afterEach);
vi.mock('./shippingService', () => ({ shippingService: service }));
function record(version = 3): Shipment {
  return { id: 8, shipmentNo: 'FH-8', version, preparerEmployeeIds: [42], logisticsOrderState: 'succeeded',
    content: { ...emptyShipmentForm(), preparers: ['小周'], preparationContent: '商品2件', shipmentDate: '2026-10-08',
      status: 'unfinished', orderer: '录入人', logisticsCompany: '安能物流', trackingNo: '710001' },
    createdBy: 'test', updatedBy: 'test', createdAt: '2026-10-08T10:00:00', updatedAt: '2026-10-08T10:00:00' };
}
beforeEach(() => {
  clearMessages();
  saveCurrentUser({ accessToken: 'test', employeeId: 42, mobile: null, roles: [], permissions: ['shipping:view', 'shipping:prepare'], loginMethod: 'feishu' });
  service.get.mockReset().mockResolvedValue(record()); service.updatePreparation.mockReset();
});
afterEach(() => { clearCurrentUser(); clearMessages(); });
async function render() {
  const wrapper = mount(ShipmentPreparationDialog, { attachTo: document.body, props: { shipmentId: 8 } });
  await flushPromises(); return wrapper;
}
describe('preparation feedback dialog', () => {
  it('retains input on a conflict and requires loading the current version before saving again', async () => {
    service.updatePreparation.mockRejectedValue(Object.assign(new Error('发货单已更新'), { code: 'SHIPMENT_VERSION_CONFLICT' }));
    const wrapper = await render();
    await wrapper.get('[data-testid="preparation-status"]').setValue('completed');
    await wrapper.get('[data-testid="preparation-actual-weight"]').setValue('12.345');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="preparation-actual-weight"]').element).toHaveProperty('value', '12.345');
    expect(wrapper.get('[data-testid="preparation-status"]').element).toHaveProperty('value', 'completed');
    expect(wrapper.get('[data-testid="preparation-save"]').attributes('disabled')).toBeDefined();
    expect(wrapper.emitted('saved')).toBeUndefined();
    expect(messages.value).toEqual([expect.objectContaining({ type: 'warning', text: '发货单已更新' })]);
    service.get.mockResolvedValue(record(4));
    service.updatePreparation.mockResolvedValue(record(5));
    await wrapper.get('[data-testid="preparation-reload"]').trigger('click'); await flushPromises();
    await wrapper.get('[data-testid="preparation-status"]').setValue('out_of_stock');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click'); await flushPromises();
    expect(service.updatePreparation).toHaveBeenLastCalledWith(8, { status: 'out_of_stock', actualWeight: null, version: 4 });
    wrapper.unmount();
  });
  it('rejects invalid weight and prevents duplicate saving or dismissal during a request', async () => {
    const wrapper = await render();
    for (const invalid of ['0', '-1', '1.2345']) {
      await wrapper.get('[data-testid="preparation-actual-weight"]').setValue(invalid);
      await wrapper.get('[data-testid="preparation-save"]').trigger('click');
      expect(service.updatePreparation).not.toHaveBeenCalled();
      expect(messages.value.at(-1)).toMatchObject({ type: 'warning', text: expect.stringContaining('实际重量') });
    }
    let resolve!: (shipment: Shipment) => void;
    service.updatePreparation.mockReturnValue(new Promise<Shipment>(done => { resolve = done; }));
    await wrapper.get('[data-testid="preparation-actual-weight"]').setValue('1.235');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click');
    await wrapper.get('[data-testid="preparation-close"]').trigger('click');
    expect(service.updatePreparation).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted('close')).toBeUndefined();
    resolve(record(4)); await flushPromises();
    expect(wrapper.emitted('saved')).toHaveLength(1);
    wrapper.unmount();
  });
  it('checks the freshly loaded assignment before offering a save', async () => {
    service.get.mockResolvedValue({ ...record(), preparerEmployeeIds: [43] });
    const wrapper = await render();
    expect(wrapper.text()).toContain('未分配给当前账号');
    expect(wrapper.get('[data-testid="preparation-save"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
  it.each([['SERVICE_UNAVAILABLE', 'error'], ['VALIDATION_FAILED', 'warning']])('shows %s with the appropriate message while retaining the entered weight', async (code, type) => {
    service.updatePreparation.mockRejectedValue(Object.assign(new Error('服务拒绝本次保存'), { code }));
    const wrapper = await render();
    await wrapper.get('[data-testid="preparation-actual-weight"]').setValue('12.345');
    await wrapper.get('[data-testid="preparation-save"]').trigger('click'); await flushPromises();
    expect(messages.value).toEqual([expect.objectContaining({ type, text: '服务拒绝本次保存' })]);
    expect(wrapper.get('[data-testid="preparation-actual-weight"]').element).toHaveProperty('value', '12.345');
    expect(wrapper.get('[data-testid="preparation-save"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });
});
