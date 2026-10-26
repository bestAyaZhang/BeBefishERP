import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { clearMessages } from '../../components/feedback/message';
import ShipmentFormView from './ShipmentFormView.vue';
import type { Shipment } from './types';
import type { Quote, QuoteBatch } from '../logistics/types';

const service = vi.hoisted(() => ({ formOptions: vi.fn(), logisticsAvailability: vi.fn(), get: vi.fn(),
  getLogisticsOrder: vi.fn(), update: vi.fn(), placeLogisticsOrder: vi.fn() }));
const freight = vi.hoisted(() => ({ getFreightSelection: vi.fn(), getQuotes: vi.fn() }));
vi.mock('./shippingService', () => ({ shippingService: service }));
vi.mock('../logistics/logisticsService', () => ({ logisticsService: freight }));
// Keep ShipmentQuotePanel real: its automatic requests and emissions are part of these regressions.
enableAutoUnmount(afterEach);

const historical: Quote = {
  quoteId: 'historical-quote', companyId: 7, companyName: '原合作物流', provider: 'ANE', accountId: 8,
  accountVersionId: 9, templateId: 10, templateVersion: 1, productCode: '24', productName: '精准零担',
  amount: 26.5, billedQuantity: 18.5, chargeMode: 'weight', matchedRegion: '浙江省', lowerExclusive: 0,
  upperInclusive: null, firstQuantity: 1, firstFee: 9.5, additionalQuantity: 1, additionalFee: 1,
  renewalCount: 17, available: true, reason: '', apiReady: true, apiMessage: '', recommended: true,
  expiresAt: '2026-10-25T10:10:00Z'
};
const current: Quote = { ...historical, quoteId: 'current-quote', amount: 39.5, templateVersion: 2,
  expiresAt: '2026-10-26T10:10:00Z' };
const batch = (): QuoteBatch => ({ quotes: [{ ...current }], legacyOrderingAllowed: false,
  generatedAt: '2026-10-26T10:00:00Z' });
const record = (): Shipment => ({
  id: 18, shipmentNo: 'FH20261025-18', version: 3, creatorEmployeeId: 1, preparerEmployeeIds: [2],
  createdBy: 'employee:1', updatedBy: 'employee:1', logisticsOrderState: null,
  createdAt: '2026-10-25T09:00:00', updatedAt: '2026-10-25T09:00:00',
  content: { shipmentDate: '2026-10-25', platform: '淘宝', shopName: '原店铺', preparers: ['小周'],
    senderName: '原寄件人', senderPhone: '13800000000', senderProvince: '浙江省', senderCity: '杭州市',
    senderCounty: '余杭区', senderDetailAddress: '原仓库1号', recipientName: '林女士', recipientPhone: '13800006028',
    recipientProvince: '浙江省', recipientCity: '杭州市', recipientCounty: '余杭区', recipientDetailAddress: '收件路18号',
    preparationContent: '水族箱 × 2', remark: '原备注', estimatedFreight: historical.amount,
    orderDraft: { cargoName: '水族用品', packType: '纸箱', weight: 18.5, volume: 0.12, pieceAmount: 2,
      productTypeId: 24, goodsType: 180, payType: 104, logisticsRemark: '' },
    status: 'unfinished', orderer: '李主管', logisticsCompany: historical.companyName, trackingNo: '' }
});

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/shipping/:id/edit', name: 'shipping-edit', component: ShipmentFormView },
    { path: '/shipping/:id', name: 'shipping-detail', component: { template: '<div>detail</div>' } },
    { path: '/shipping', name: 'shipping-list', component: { template: '<div>list</div>' } }
  ] });
  await router.push('/shipping/18/edit'); await router.isReady();
  const wrapper = mount(ShipmentFormView, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}
async function settleQuote() { await vi.advanceTimersByTimeAsync(300); await flushPromises(); }

beforeEach(() => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-26T10:00:00Z'));
  clearCurrentUser(); clearMessages();
  saveCurrentUser({ accessToken: 'test-only', employeeId: 1, mobile: null, roles: [],
    permissions: ['shipping:view', 'shipping:edit', 'shipping:order'], loginMethod: 'password' });
  service.formOptions.mockReset().mockResolvedValue({ shopNames: [], platforms: [], shops: [],
    preparers: [{ employeeId: 2, employeeName: '小周' }] });
  service.logisticsAvailability.mockReset().mockResolvedValue({ available: true, testEnvironment: true,
    managedTemplatesEnabled: true, message: '合作报价已启用', sender: null });
  service.get.mockReset().mockResolvedValue(record()); service.getLogisticsOrder.mockReset().mockResolvedValue(null);
  service.update.mockReset().mockResolvedValue({ ...record(), version: 4 });
  service.placeLogisticsOrder.mockReset();
  freight.getFreightSelection.mockReset().mockResolvedValue({ ...historical });
  freight.getQuotes.mockReset().mockResolvedValue(batch());
});
afterEach(() => { clearCurrentUser(); clearMessages(); vi.useRealTimers(); });

describe('editing a saved shipment with its real quote panel', () => {
  it('keeps an expired saved quote and amount when only remark changes without automatically requesting quotes', async () => {
    const wrapper = await render(); await settleQuote();
    expect.soft(freight.getQuotes).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="shipment-remark"]').setValue('仅补充包装备注');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect.soft(service.update).toHaveBeenCalledWith(18, expect.objectContaining({
      remark: '仅补充包装备注', estimatedFreight: historical.amount
    }), 'unfinished', 3);
    expect(service.update.mock.calls[0][1].logisticsChoice).toBeUndefined();
    expect(service.placeLogisticsOrder).not.toHaveBeenCalled();
  });

  it('invalidates the saved quote immediately after a price input changes and requests current conditions', async () => {
    freight.getQuotes.mockImplementation(() => new Promise<QuoteBatch>(() => {}));
    const wrapper = await render();
    await wrapper.get('[data-testid="ane-weight"]').setValue('22'); await settleQuote();
    expect(freight.getQuotes).toHaveBeenCalledWith(expect.objectContaining({ weight: 22 }));
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ estimatedFreight: null,
      logisticsChoice: { quoteId: null, preferredCompanyId: historical.companyId, clear: true }
    }), 'unfinished', 3);
  });

  it('allows an explicit recalculation to replace the saved quote with the current selected quotation', async () => {
    const wrapper = await render();
    await wrapper.get('[data-testid="shipment-requote"]').trigger('click'); await settleQuote();
    expect(freight.getQuotes).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="quote-select-current-quote"]').trigger('click'); await flushPromises();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ estimatedFreight: current.amount,
      logisticsChoice: { quoteId: current.quoteId, preferredCompanyId: current.companyId, clear: false }
    }), 'unfinished', 3);
  });

  it('retains the saved company and product after a price change even when another carrier is recommended', async () => {
    freight.getQuotes.mockResolvedValue({ ...batch(), quotes: [
      { ...current, quoteId: 'cheapest-quote', companyId: 99, companyName: '另一家最低价', amount: 10, recommended: true },
      { ...current, recommended: false }
    ] });
    const wrapper = await render();
    await wrapper.get('[data-testid="ane-weight"]').setValue('22'); await settleQuote();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ estimatedFreight: current.amount,
      logisticsChoice: { quoteId: current.quoteId, preferredCompanyId: historical.companyId, clear: false }
    }), 'unfinished', 3);
  });
});
