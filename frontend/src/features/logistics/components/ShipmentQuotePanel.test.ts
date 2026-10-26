import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ShipmentQuotePanel from './ShipmentQuotePanel.vue';
import { logisticsService } from '../logisticsService';
import type { Quote, QuoteBatch, QuoteInput } from '../types';
vi.mock('../logisticsService', () => ({ logisticsService: { getQuotes: vi.fn() } }));
const input:QuoteInput = { senderProvince:'浙江省',senderCity:'金华市',senderCounty:'义乌市',recipientProvince:'江苏省',recipientCity:'南京市',recipientCounty:'鼓楼区',weight:10,volume:null,pieceAmount:1,goodsType:1,payType:1 };
const quote = (id:string, recommended=false):Quote => ({quoteId:id,companyId:id==='cheap'?2:1,companyName:id,provider:'MANUAL',accountId:null,accountVersionId:null,templateId:1,templateVersion:1,productCode:'A',productName:'普运',amount:20,billedQuantity:10,chargeMode:'weight',matchedRegion:'默认',lowerExclusive:0,upperInclusive:null,firstQuantity:1,firstFee:2,additionalQuantity:1,additionalFee:2,renewalCount:9,available:true,reason:'',apiReady:false,apiMessage:'需线下下单',recommended,expiresAt:'2099-01-01T00:00:00Z'});
const batch = (...quotes:Quote[]):QuoteBatch => ({quotes,legacyOrderingAllowed:false,generatedAt:''});
describe('shipment quote selection', () => {
  beforeEach(() => {vi.useFakeTimers(); vi.mocked(logisticsService.getQuotes).mockReset();});
  afterEach(() => vi.useRealTimers());
  it('ignores an obsolete response and disables old rows immediately while recalculating', async () => {
    let resolveOld!:(value:QuoteBatch)=>void;
    vi.mocked(logisticsService.getQuotes).mockResolvedValueOnce(batch(quote('initial',true)))
      .mockImplementationOnce(() => new Promise(resolve => {resolveOld=resolve;}))
      .mockResolvedValueOnce(batch(quote('latest',true)));
    const wrapper=mount(ShipmentQuotePanel,{props:{input,modelValue:quote('initial')}});
    await vi.runAllTimersAsync(); await flushPromises();
    await wrapper.setProps({input:{...input,weight:11}});
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([null]);
    expect(wrapper.get('[data-testid="quote-select-initial"]').attributes('disabled')).toBeDefined();
    await vi.advanceTimersByTimeAsync(300);
    await wrapper.setProps({input:{...input,weight:12}});
    await vi.advanceTimersByTimeAsync(300); await flushPromises();
    resolveOld(batch(quote('obsolete'))); await flushPromises();
    expect(wrapper.text()).toContain('latest'); expect(wrapper.text()).not.toContain('obsolete');
    wrapper.unmount();
  });
  it('retains a manual product selection when same-input refresh returns a recommendation', async () => {
    vi.mocked(logisticsService.getQuotes).mockResolvedValue(batch(quote('cheap',true),quote('manual')));
    const wrapper=mount(ShipmentQuotePanel,{props:{input,modelValue:quote('manual')}});
    await vi.runAllTimersAsync(); await flushPromises();
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    await wrapper.get('[data-testid="quote-refresh"]').trigger('click'); await flushPromises();
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    expect(wrapper.get('[data-testid="quote-select-manual"]').text()).toContain('已选择');
    wrapper.unmount();
  });
  it('shows unavailable prices as reasons and allows saving without selection', async () => {
    vi.mocked(logisticsService.getQuotes).mockResolvedValue(batch({...quote('unknown'),available:false,amount:null,reason:'收件省份无法识别'}));
    const wrapper=mount(ShipmentQuotePanel,{props:{input,modelValue:null}});
    await vi.runAllTimersAsync(); await flushPromises();
    expect(wrapper.text()).toContain('收件省份无法识别'); expect(wrapper.text()).not.toContain('¥0.00');
    expect(wrapper.get('[data-testid="quote-select-unknown"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
});

