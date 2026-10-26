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
