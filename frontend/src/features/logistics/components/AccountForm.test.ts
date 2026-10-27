import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AccountForm from './AccountForm.vue';
import type { AccountView } from '../types';

enableAutoUnmount(afterEach);
afterEach(()=>vi.unstubAllEnvs());
const savedAccount=(environment:string):AccountView=>({
  id:1,companyId:1,environment,version:1,accountVersionId:1,code:'A',customerCode:'C',dictId:'D',orderUrl:'',
  sender:{name:'',phone:'',province:'',city:'',county:'',address:''},appKeyConfigured:false,customerPassConfigured:false,ready:false,message:''
});

describe('account environment label',()=>{
  it('shows Yimi-only credentials blank and preserves missing IDs when saving a draft account',async()=>{
    const account={...savedAccount('test'),appKeyConfigured:true,appSecretConfigured:true,yimi:{sellerId:'seller',subscriptionId:''}};
    const wrapper=mount(AccountForm,{props:{account,provider:'YIMI',busy:false,error:''}});
    expect(wrapper.find('[name="customerPass"]').exists()).toBe(false);
    expect(wrapper.get('[name="appSecret"]').element).toHaveProperty('value','');
    expect(wrapper.get('[name="appSecret"]').attributes('type')).toBe('password');
    expect(wrapper.text()).toContain('已配置，留空保留');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({yimi:{sellerId:'seller',subscriptionId:''}});
    expect(wrapper.emitted('save')?.[0]?.[0]).not.toHaveProperty('appSecret');
    await wrapper.get('[name="sellerId"]').setValue('new-seller');
    expect(wrapper.text()).toContain('账号身份已变更');
    await wrapper.get('[name="appKey"]').setValue('replacement');
    await wrapper.get('[name="appSecret"]').setValue('new-secret');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({appKey:'replacement',appSecret:'new-secret',yimi:{sellerId:'new-seller'}});
  });
  it('defaults a new account to production when a production build omits VITE_RUNTIME_ENV',()=>{
    vi.stubEnv('PROD',true);vi.stubEnv('VITE_RUNTIME_ENV',undefined);
    const wrapper=mount(AccountForm,{props:{account:null,busy:false,error:''}});
    expect(wrapper.get('strong').text()).toBe('正式环境');
  });

  it('honors an explicit test environment even in a production build',()=>{
    vi.stubEnv('PROD',true);vi.stubEnv('VITE_RUNTIME_ENV','test');
    const wrapper=mount(AccountForm,{props:{account:null,busy:false,error:''}});
    expect(wrapper.get('strong').text()).toBe('测试环境');
  });

  it.each([
    {savedEnvironment:'test',runtimeEnvironment:'prod',expected:'测试环境'},
    {savedEnvironment:'production',runtimeEnvironment:'test',expected:'正式环境'}
  ])('prefers saved $savedEnvironment account metadata over runtime $runtimeEnvironment',({savedEnvironment,runtimeEnvironment,expected})=>{
    vi.stubEnv('PROD',true);vi.stubEnv('VITE_RUNTIME_ENV',runtimeEnvironment);
    const wrapper=mount(AccountForm,{props:{account:savedAccount(savedEnvironment),busy:false,error:''}});
    expect(wrapper.get('strong').text()).toBe(expected);
  });
});
