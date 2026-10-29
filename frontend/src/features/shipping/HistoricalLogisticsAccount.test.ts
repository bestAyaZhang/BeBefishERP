import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HistoricalLogisticsAccount from './HistoricalLogisticsAccount.vue';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { clearMessages, messages } from '../../components/feedback/message';
import type { AccountView, Company, Page } from '../logistics/types';

const service=vi.hoisted(()=>({listCompanies:vi.fn(),listAccounts:vi.fn(),confirmLogisticsAccount:vi.fn()}));
vi.mock('../logistics/logisticsService',()=>({logisticsService:service}));
enableAutoUnmount(afterEach);

const company=(id=11,provider:'ANE'|'MANUAL'='ANE',status='enabled'):Company=>({
  id,code:`C-${id}`,name:`合作公司 ${id}`,provider,status,contactName:'',contactPhone:'',sortOrder:id,remark:'',version:1,createdAt:'',updatedAt:''
});
const account=(id=71,environment='test',companyId=11):AccountView=>({
  id,companyId,environment,version:2,accountVersionId:3,code:`ACCOUNT-${id}`,customerCode:`CUSTOMER-${id}`,dictId:'D',orderUrl:'',
  sender:{name:'',phone:'',province:'',city:'',county:'',address:''},appKeyConfigured:true,customerPassConfigured:true,ready:true,message:''
});
const page=<T>(records:T[],pageNumber=1,total=records.length):Page<T>=>({records,page:pageNumber,pageSize:100,total});
function signIn(roles=['SUPER_ADMIN'],permissions=['logistics:view','logistics:configure']){
  saveCurrentUser({accessToken:'test-only',mobile:null,roles,permissions,loginMethod:'password'});
}
const render=(testEnvironment=true)=>mount(HistoricalLogisticsAccount,{props:{shipmentId:987,version:23,testEnvironment}});
async function open(wrapper:ReturnType<typeof render>){
  await wrapper.get('[data-testid="logistics-account-confirm-open"]').trigger('click');await flushPromises();
}
async function selectAccount(wrapper:ReturnType<typeof render>){
  await open(wrapper);
  await wrapper.get('[data-testid="historical-company"]').setValue('11');await flushPromises();
  await wrapper.get('[data-testid="historical-account"]').setValue('71');
  await wrapper.get('[data-testid="historical-account-confirmed"]').setValue(true);
}

beforeEach(()=>{
  clearCurrentUser();clearMessages();
  service.listCompanies.mockReset().mockResolvedValue(page([company()]));
  service.listAccounts.mockReset().mockResolvedValue(page([account()]));
  service.confirmLogisticsAccount.mockReset().mockResolvedValue(null);
});
afterEach(()=>{clearCurrentUser();clearMessages();});

describe('historical logistics account verification',()=>{
  it.each([
    {label:'ordinary operator',roles:[],permissions:['logistics:view','logistics:configure']},
    {label:'administrator without view',roles:['SUPER_ADMIN'],permissions:['logistics:configure']},
    {label:'administrator without configure',roles:['SUPER_ADMIN'],permissions:['logistics:view']}
  ])('does not expose confirmation to $label',({roles,permissions})=>{
    signIn(roles,permissions);const wrapper=render();
    expect(wrapper.text()).toContain('核实原物流账号');
    expect(wrapper.find('[data-testid="logistics-account-confirm-open"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="historical-account-save"]').exists()).toBe(false);
    expect(service.listCompanies).not.toHaveBeenCalled();expect(service.confirmLogisticsAccount).not.toHaveBeenCalled();
  });

  it('requires a signed-in super administrator with both independent logistics permissions',async()=>{
    const anonymous=render();expect(anonymous.find('[data-testid="logistics-account-confirm-open"]').exists()).toBe(false);
    anonymous.unmount();
    signIn();const wrapper=render();
    expect(wrapper.find('[data-testid="logistics-account-confirm-open"]').exists()).toBe(true);
    expect(service.listCompanies).not.toHaveBeenCalled();
    await open(wrapper);expect(wrapper.find('[data-testid="historical-company"]').exists()).toBe(true);
  });

  it.each([{testEnvironment:true,expected:'test',excluded:'production'},{testEnvironment:false,expected:'production',excluded:'test'}])(
    'reads every company/account page and restricts $expected orders to matching historical accounts',async({testEnvironment,expected,excluded})=>{
      signIn();
      const firstCompanies=Array.from({length:100},(_,index)=>company(index+1,index===0?'MANUAL':'ANE'));
      service.listCompanies.mockResolvedValueOnce(page(firstCompanies,1,101)).mockResolvedValueOnce(page([company(101,'ANE','disabled')],2,101));
      const firstAccounts=Array.from({length:100},(_,index)=>account(index+1,excluded,101));
      service.listAccounts.mockResolvedValueOnce(page(firstAccounts,1,101)).mockResolvedValueOnce(page([account(501,expected,101)],2,101));
      const wrapper=render(testEnvironment);await open(wrapper);
      const companyOptions=wrapper.findAll('[data-testid="historical-company"] option').map(option=>option.attributes('value'));
      expect(companyOptions).not.toContain('1');expect(companyOptions).toContain('101');
      expect(wrapper.get('[data-testid="historical-company"]').text()).toContain('合作公司 101（已停用）');
      expect(service.listCompanies.mock.calls).toEqual([[{page:1,size:100}],[{page:2,size:100}]]);
      await wrapper.get('[data-testid="historical-company"]').setValue('101');await flushPromises();
      expect(service.listAccounts.mock.calls).toEqual([[101,{page:1,size:100}],[101,{page:2,size:100}]]);
      const accountOptions=wrapper.findAll('[data-testid="historical-account"] option');
      expect(accountOptions.map(option=>option.attributes('value'))).toEqual(['','501']);
      expect(accountOptions[1].text()).toContain('CUSTOMER-501');
      expect(accountOptions[1].text()).toContain(testEnvironment?'测试':'正式');
    }
  );

  it('requires an account and explicit verification before submitting the exact shipment/version linkage',async()=>{
    signIn();const wrapper=render();await open(wrapper);
    const save=()=>wrapper.get('[data-testid="historical-account-save"]');
    expect(save().attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="historical-company"]').setValue('11');await flushPromises();
    await wrapper.get('[data-testid="historical-account-confirmed"]').setValue(true);
    expect(save().attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="historical-account"]').setValue('71');
    await wrapper.get('[data-testid="historical-account-confirmed"]').setValue(false);
    expect(save().attributes('disabled')).toBeDefined();
    await save().trigger('click');expect(service.confirmLogisticsAccount).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="historical-account-confirmed"]').setValue(true);
    expect(save().attributes('disabled')).toBeUndefined();
    await save().trigger('click');await flushPromises();
    expect(service.confirmLogisticsAccount).toHaveBeenCalledWith(987,{accountId:71,version:23,confirmed:true});
    expect(wrapper.emitted('saved')).toEqual([[]]);
    expect(wrapper.find('[data-testid="historical-account-save"]').exists()).toBe(false);
    expect(messages.value).toEqual([expect.objectContaining({type:'success',text:'原物流账号已确认'})]);
  });

  it('preserves the selected account and verification on a rejected save and retries that write',async()=>{
    signIn();service.confirmLogisticsAccount.mockRejectedValueOnce(new Error('关联保存暂不可用')).mockResolvedValueOnce(null);
    const wrapper=render();await selectAccount(wrapper);
    await wrapper.get('[data-testid="historical-account-save"]').trigger('click');await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('关联保存暂不可用');
    expect(wrapper.get('[data-testid="historical-account"]').element).toHaveProperty('value','71');
    expect(wrapper.get('[data-testid="historical-account-confirmed"]').element).toHaveProperty('checked',true);
    expect(wrapper.emitted('saved')).toBeUndefined();
    await wrapper.get('[role="alert"] button').trigger('click');await flushPromises();
    expect(service.confirmLogisticsAccount.mock.calls).toEqual([
      [987,{accountId:71,version:23,confirmed:true}],
      [987,{accountId:71,version:23,confirmed:true}]
    ]);
    expect(wrapper.emitted('saved')).toEqual([[]]);
  });
});
