import { flushPromises,mount,type VueWrapper } from '@vue/test-utils';
import { beforeEach,afterEach,describe,it,expect,vi } from 'vitest';
import { saveCurrentUser,clearCurrentUser } from '../../../services/authSession';
import { clearMessages } from '../../../components/feedback/message';
import { createMockPddCostService,type PddCostService } from './pddCostService';
import PddCostView from './PddCostView.vue';
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}));

async function fill(wrapper:VueWrapper){
  await wrapper.get('[data-testid="cost-name"]').setValue('玻璃杯日常方案');
  await wrapper.get('[data-testid="cost-spec-0"]').setValue('透明 / 350ml');
  await wrapper.get('[data-testid="cost-unitCost-0"]').setValue('2.1');
  await wrapper.get('[data-testid="cost-shippingCost-0"]').setValue('2.05');
  await wrapper.get('[data-testid="cost-salePrice-0"]').setValue('5.5');
}
describe('PDD SKU cost workspace',()=>{
  let wrapper:VueWrapper;
  let service:PddCostService;
  beforeEach(()=>{
    localStorage.clear();saveCurrentUser({accessToken:'test',mobile:'13800138000',roles:[],permissions:['product:view','product:edit'],loginMethod:'password'});
    service=createMockPddCostService();vi.spyOn(window,'confirm').mockReturnValue(true);
  });
  afterEach(()=>{wrapper?.unmount();clearMessages();clearCurrentUser();localStorage.clear();vi.restoreAllMocks();});
  it('edits inline, saves, reopens and updates a persistent plan',async()=>{
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await fill(wrapper);
    await wrapper.get('[data-testid="cost-refundRate-0"]').setValue('13');
    await wrapper.get('[data-testid="cost-adMode-0"]').setValue('roi');
    await wrapper.get('[data-testid="cost-roi-0"]').setValue('5');
    expect(wrapper.get('[data-testid="cost-total-0"]').text()).toBe('4.15');
    expect(wrapper.get('[data-testid="cost-net-0"]').text()).toBe('0.07');
    await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    expect(wrapper.get('[data-testid="cost-plan-1"]').text()).toContain('玻璃杯日常方案');
    wrapper.unmount();wrapper=mount(PddCostView,{props:{service:createMockPddCostService()}});await flushPromises();
    await wrapper.get('[data-testid="cost-plan-1"]').trigger('click');await flushPromises();
    expect((wrapper.get('[data-testid="cost-unitCost-0"]').element as HTMLInputElement).value).toBe('2.1');
    await wrapper.get('[data-testid="cost-salePrice-0"]').setValue('10');await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    expect((await service.get(1)).version).toBe(2);expect((await service.get(1)).lines[0].input.salePrice).toBe(10);
  });
  it('preserves edits after failed saves and rejects incomplete or invalid numbers',async()=>{
    vi.spyOn(service,'save').mockRejectedValue(new Error('服务暂不可用'));
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await fill(wrapper);
    await wrapper.get('[data-testid="cost-unitCost-0"]').setValue('');await wrapper.get('[data-testid="cost-save"]').trigger('click');
    expect(service.save).not.toHaveBeenCalled();expect(wrapper.get('[data-testid="cost-error"]').text()).toContain('货品成本');
    await wrapper.get('[data-testid="cost-unitCost-0"]').setValue('2.1');await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    expect(wrapper.get('[data-testid="cost-error"]').text()).toBe('服务暂不可用');expect((wrapper.get('[data-testid="cost-name"]').element as HTMLInputElement).value).toContain('玻璃杯');
  });
  it('adds an existing SKU with its actual cost, copies rows and reverse-prices',async()=>{
    vi.spyOn(service,'skuOptions').mockResolvedValue([{skuId:20,skuCode:'SKU20',productName:'玻璃杯',specification:'透明',unitCost:4,salePrice:10}]);
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await wrapper.get('[data-testid="cost-sku-search"]').trigger('focus');await flushPromises();
    await wrapper.get('[data-testid="cost-sku-20"]').trigger('click');expect(wrapper.findAll('[data-testid^="cost-line-"]')).toHaveLength(1);
    expect((wrapper.get('[data-testid="cost-unitCost-0"]').element as HTMLInputElement).value).toBe('4');
    await wrapper.get('[data-testid="cost-target-margin"]').setValue('30');await wrapper.get('[data-testid="cost-target-0"]').trigger('click');
    expect((wrapper.get('[data-testid="cost-salePrice-0"]').element as HTMLInputElement).value).toBe('5.72');
    await wrapper.get('[data-testid="cost-copy-line-0"]').trigger('click');expect(wrapper.findAll('[data-testid^="cost-line-"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="cost-line-1"]').exists()).toBe(true);
    await wrapper.get('[data-testid="cost-remove-line-1"]').trigger('click');expect(wrapper.findAll('[data-testid^="cost-line-"]')).toHaveLength(1);
  });
  it('copies a plan and archives and restores it without losing its saved inputs',async()=>{
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await fill(wrapper);await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    await wrapper.get('[data-testid="cost-archive"]').trigger('click');await flushPromises();expect((await service.get(1)).archived).toBe(true);
    expect(wrapper.find('[data-testid="cost-save"]').exists()).toBe(false);
    await wrapper.get('[data-testid="cost-archive"]').trigger('click');await flushPromises();expect((await service.get(1)).archived).toBe(false);
    await wrapper.get('[data-testid="cost-copy-plan"]').trigger('click');await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    expect((await service.get(2)).name).toContain('副本');expect((await service.get(1)).name).toBe('玻璃杯日常方案');
  });
  it('does not discard unsaved changes when the user cancels switching',async()=>{
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await fill(wrapper);vi.mocked(window.confirm).mockReturnValue(false);
    await wrapper.get('[data-testid="cost-new"]').trigger('click');expect((wrapper.get('[data-testid="cost-name"]').element as HTMLInputElement).value).toBe('玻璃杯日常方案');
  });
  it('opens automatically, keeps edits when closed and reopens from the workspace',async()=>{
    wrapper=mount(PddCostView,{props:{service},attachTo:document.body});await flushPromises();
    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true');
    await fill(wrapper);
    await wrapper.get('[data-testid="cost-calculator-close"]').trigger('click');await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    await wrapper.get('[data-testid="cost-open-calculator"]').trigger('click');await flushPromises();
    expect((wrapper.get('[data-testid="cost-spec-0"]').element as HTMLInputElement).value).toBe('透明 / 350ml');
    await wrapper.get('[role="dialog"]').trigger('keydown',{key:'Escape'});
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    window.dispatchEvent(new Event('erp:open-pdd-cost'));await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it('pages many specifications without losing values or saving only the current page',async()=>{
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();await fill(wrapper);
    for(let index=0;index<5;index++) await wrapper.get(`[data-testid="cost-copy-line-${index}"]`).trigger('click');
    expect(wrapper.findAll('tbody tr[data-testid]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="cost-rows-pagination"]').text()).toContain('6 / 6');
    await wrapper.get('[data-testid="cost-unitCost-5"]').setValue('8');
    await wrapper.get('[data-testid="cost-rows-prev"]').trigger('click');
    expect((wrapper.get('[data-testid="cost-unitCost-4"]').element as HTMLInputElement).value).toBe('2.1');
    await wrapper.get('[data-testid="cost-save"]').trigger('click');await flushPromises();
    const plan=await service.get(1);expect(plan.lines).toHaveLength(6);expect(plan.lines[5].input.unitCost).toBe(8);
    expect(wrapper.get('[data-testid="cost-calculator-body"]').classes().join(' ')).not.toContain('overflow-y-auto');
    expect(wrapper.find('[class*="min-w-[2100px]"]').exists()).toBe(false);
  });
  it('hides write actions for a viewer',async()=>{
    saveCurrentUser({accessToken:'test',mobile:null,roles:[],permissions:['product:view'],loginMethod:'password'});
    wrapper=mount(PddCostView,{props:{service}});await flushPromises();expect(wrapper.find('[data-testid="cost-save"]').exists()).toBe(false);expect(wrapper.find('[data-testid="cost-add-line"]').exists()).toBe(false);
  });
});
