import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import CompanyForm from './CompanyForm.vue';
import AccountForm from './AccountForm.vue';
import ProvinceGroupDialog from './ProvinceGroupDialog.vue';
import type { AccountView } from '../types';
const account:AccountView={id:1,companyId:1,environment:'test',version:3,accountVersionId:4,code:'a',customerCode:'c',dictId:'d',orderUrl:'https://example.test',sender:{name:'寄件人',phone:'13800000000',province:'浙江省',city:'金华市',county:'义乌市',address:'某路'},appKeyConfigured:true,customerPassConfigured:true,ready:false,message:'缺少配置'};
describe('logistics configuration forms',()=>{
  it('allows selecting the Yimi adapter for a new company',async()=>{
    const wrapper=mount(CompanyForm,{props:{company:null,busy:false,error:''}});
    const provider=wrapper.findAll('select')[0];
    expect(provider.find('option[value="YIMI"]').text()).toContain('壹米滴答');
    await provider.setValue('YIMI');await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({provider:'YIMI'});
    wrapper.unmount();
  });
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
  it('defaults existing Yimi accounts to ordinary pickup and submits empty pickup metadata',async()=>{
    const wrapper=mount(AccountForm,{props:{account:{...account,yimi:{sellerId:'seller',subscriptionId:'subscription'}},provider:'YIMI',busy:false,error:''}});
    expect(wrapper.get('[name="designatedPickup"]').element).toHaveProperty('checked',false);
    expect(wrapper.find('[name="salesmanCode"]').exists()).toBe(false);
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({yimi:{sellerId:'seller',subscriptionId:'subscription',designatedPickup:false,salesmanCode:'',salesmanName:''}});
    wrapper.unmount();
  });
  it('allows saving incomplete designated pickup with a clear API-ordering warning',async()=>{
    const wrapper=mount(AccountForm,{props:{account:null,provider:'YIMI',busy:false,error:''}});
    expect(wrapper.text()).toContain('指定揽收业务员（汇森/汇霖）');
    await wrapper.get('[name="designatedPickup"]').setValue(true);
    for(const name of ['salesmanCode','salesmanName']){
      const input=wrapper.get(`[name="${name}"]`);
      expect(input.attributes('maxlength')).toBe('50');
      expect(input.attributes('aria-required')).toBe('true');
      expect(input.attributes('required')).toBeUndefined();
      expect(input.element.closest('label')?.textContent).toContain('必填');
    }
    expect(wrapper.text()).toContain('可保存配置');
    expect(wrapper.text()).toContain('工号和姓名补全后才能接口下单');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({yimi:{designatedPickup:true,salesmanCode:'',salesmanName:''}});
    wrapper.unmount();
  });
  it('submits pickup strings with leading zeros and preserves blank-secret replacement semantics',async()=>{
    const wrapper=mount(AccountForm,{props:{account:{...account,appSecretConfigured:true,yimi:{sellerId:'seller',subscriptionId:'subscription'}},provider:'YIMI',busy:false,error:''}});
    await wrapper.get('[name="designatedPickup"]').setValue(true);
    await wrapper.get('[name="salesmanCode"]').setValue(' 00123 ');
    await wrapper.get('[name="salesmanName"]').setValue(' 揽收员甲 ');
    expect(wrapper.text()).not.toContain('账号身份已变更');
    expect(wrapper.get('[name="appKey"]').element).toHaveProperty('value','');
    expect(wrapper.get('[name="appSecret"]').element).toHaveProperty('value','');
    await wrapper.get('form').trigger('submit');
    const input=wrapper.emitted('save')?.[0]?.[0];
    expect(input).toMatchObject({yimi:{sellerId:'seller',subscriptionId:'subscription',designatedPickup:true,salesmanCode:'00123',salesmanName:'揽收员甲'}});
    expect(input).not.toHaveProperty('appKey');expect(input).not.toHaveProperty('appSecret');expect(input).not.toHaveProperty('customerPass');
    await wrapper.get('[name="designatedPickup"]').setValue(false);
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[1]?.[0]).toMatchObject({yimi:{designatedPickup:false,salesmanCode:'',salesmanName:''}});
    expect(wrapper.text()).not.toContain('账号身份已变更');
    wrapper.unmount();
  });
  it.each([
    {salesmanCode:'00123',salesmanName:''},
    {salesmanCode:'',salesmanName:'揽收员甲'},
    {designatedPickup:false,salesmanCode:'00123',salesmanName:'揽收员甲'}
  ])('enables loaded pickup metadata even without an enabled flag: %o',async pickup=>{
    const wrapper=mount(AccountForm,{props:{account:{...account,yimi:{sellerId:'seller',subscriptionId:'subscription',...pickup}},provider:'YIMI',busy:false,error:''}});
    expect(wrapper.get('[name="designatedPickup"]').element).toHaveProperty('checked',true);
    expect(wrapper.get('[name="salesmanCode"]').element).toHaveProperty('value',pickup.salesmanCode);
    expect(wrapper.get('[name="salesmanName"]').element).toHaveProperty('value',pickup.salesmanName);
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({yimi:{...pickup,designatedPickup:true}});
    expect(wrapper.text()).not.toContain('账号身份已变更');
    wrapper.unmount();
  });
  it.each(['appKey','sellerId','subscriptionId'])('still warns about a changed Yimi identity when %s changes',async field=>{
    const wrapper=mount(AccountForm,{props:{account:{...account,yimi:{sellerId:'seller',subscriptionId:'subscription',designatedPickup:true,salesmanCode:'00123',salesmanName:'揽收员甲'}},provider:'YIMI',busy:false,error:''}});
    expect(wrapper.text()).not.toContain('账号身份已变更');
    await wrapper.get(`[name="${field}"]`).setValue('changed');
    expect(wrapper.text()).toContain('账号身份已变更');
    wrapper.unmount();
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
