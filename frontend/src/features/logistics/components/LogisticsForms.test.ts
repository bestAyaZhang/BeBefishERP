import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import CompanyForm from './CompanyForm.vue';
import AccountForm from './AccountForm.vue';
import ProvinceGroupDialog from './ProvinceGroupDialog.vue';
import type { AccountView } from '../types';
const account:AccountView={id:1,companyId:1,environment:'test',version:3,accountVersionId:4,code:'a',customerCode:'c',dictId:'d',orderUrl:'https://example.test',sender:{name:'寄件人',phone:'13800000000',province:'浙江省',city:'金华市',county:'义乌市',address:'某路'},appKeyConfigured:true,customerPassConfigured:true,ready:false,message:'缺少配置'};
describe('logistics configuration forms',()=>{
  it('creates a company and preserves edited values when server rejects save',async()=>{
    const wrapper=mount(CompanyForm,{props:{company:null,busy:false,error:''}});
    await wrapper.get('[name="company-name"]').setValue('合作物流');await wrapper.get('[name="company-code"]').setValue('PARTNER');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({name:'合作物流',code:'PARTNER',provider:'MANUAL',version:0});
    await wrapper.setProps({error:'编码重复'});expect(wrapper.get('[name="company-name"]').element).toHaveProperty('value','合作物流');expect(wrapper.text()).toContain('编码重复');
  });
  it('renders blank password inputs and omits unchanged secrets from the submitted payload',async()=>{
    const wrapper=mount(AccountForm,{props:{account,busy:false,error:''}});
    expect(wrapper.get('[name="appKey"]').element).toHaveProperty('value','');
    expect(wrapper.get('[name="customerPass"]').attributes('type')).toBe('password');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({version:3,code:'a'});
    expect(wrapper.emitted('save')?.[0]?.[0]).not.toHaveProperty('appKey');
    await wrapper.get('[name="account-code"]').setValue('new');
    expect(wrapper.text()).toContain('账号身份已变更');
  });
  it('requires explicit move confirmation before transferring an assigned province',async()=>{
    const wrapper=mount(ProvinceGroupDialog,{props:{groups:[{name:'原组',provinces:['330000'],bands:[]},{name:'新组',provinces:[],bands:[]}],groupIndex:1,provinces:[{code:'330000',name:'浙江省',group:'华东'}]}});
    await wrapper.get('[data-testid="province-330000"]').setValue(true);
    await wrapper.get('[data-testid="province-save"]').trigger('click');await flushPromises();
    expect(wrapper.emitted('save')).toBeUndefined();expect(wrapper.text()).toContain('原组');
    await wrapper.get('[data-testid="province-move-confirm"]').trigger('click');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject([{name:'原组',provinces:[]},{name:'新组',provinces:['330000']}]);
    wrapper.unmount();
  });
});
