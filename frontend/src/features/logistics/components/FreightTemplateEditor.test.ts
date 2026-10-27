import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import FreightTemplateEditor from './FreightTemplateEditor.vue';
import { newTemplate } from '../templateRules';
const provinces=[{code:'330000',name:'浙江省',group:'华东'},{code:'320000',name:'江苏省',group:'华东'}];
describe('contract rate editor',()=>{
  it('offers only Yimi service products and submits their matching names',async()=>{
    const wrapper=mount(FreightTemplateEditor,{props:{template:null,provinces:[],provider:'YIMI',busy:false,error:''}});
    const product=wrapper.get('[data-testid="yimi-template-product"]');
    expect(product.findAll('option').map(o=>o.text())).toEqual(['请选择运输产品','港到门','港到港']);
    await wrapper.get('[name="template-name"]').setValue('壹米合同');
    await product.setValue('2');await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({productCode:'2',productName:'港到港'});
    wrapper.unmount();
  });
  it('starts with blank contract fees and switches piece/volume/default settings',async()=>{
    const wrapper=mount(FreightTemplateEditor,{props:{template:null,provinces:[],busy:false,error:''}});
    expect(wrapper.get('[data-testid="default-firstFee-0"]').element).toHaveProperty('value','');
    await wrapper.get('[data-testid="volumetric-toggle"]').setValue(true);
    expect(wrapper.find('[name="volumeDivisor"]').exists()).toBe(true);
    await wrapper.get('[name="charge-mode"]').setValue('piece');
    expect(wrapper.text()).toContain('首件');expect(wrapper.find('[name="volumeDivisor"]').exists()).toBe(false);
    await wrapper.get('[data-testid="default-toggle"]').setValue(false);
    expect(wrapper.find('[data-testid="default-firstFee-0"]').exists()).toBe(false);
    await wrapper.get('[name="template-name"]').setValue('草稿合同');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({name:'草稿合同',status:'disabled',rules:{mode:'piece',volumetric:false,defaultBands:[]}});
  });
  it('shows a backend province code as the catalog name without an unknown warning when editing',async()=>{
    const template={...newTemplate(),name:'已保存合同',originProvince:'330000'};
    const wrapper=mount(FreightTemplateEditor,{props:{template,provinces,busy:false,error:''}});
    const province=wrapper.findAll('select')[0].element as HTMLSelectElement;
    expect(province.value).toBe('330000');expect(province.selectedOptions[0].textContent).toBe('浙江省');
    expect(wrapper.text()).not.toContain('330000（请确认）');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({originProvince:'330000'});
    wrapper.unmount();
  });
  it('normalizes the sender province name to a selectable catalog code and submits code values',async()=>{
    const sender={name:'寄件人',phone:'',province:'浙江省',city:'金华市',county:'义乌市',address:''};
    const wrapper=mount(FreightTemplateEditor,{props:{template:null,provinces,sender,busy:false,error:''}});
    const province=wrapper.findAll('select')[0];
    expect(province.element).toHaveProperty('value','330000');
    await wrapper.get('[name="template-name"]').setValue('新合同');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({originProvince:'330000',originCity:'金华市',originCounty:'义乌市'});
    await province.setValue('320000');await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({originProvince:'320000'});
    wrapper.unmount();
  });
  it('preserves an unknown origin value so the operator can correct it explicitly',async()=>{
    const template={...newTemplate(),name:'待修正合同',originProvince:'未知省'};
    const wrapper=mount(FreightTemplateEditor,{props:{template,provinces,busy:false,error:''}});
    expect(wrapper.findAll('select')[0].element).toHaveProperty('value','未知省');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({originProvince:'未知省'});
    wrapper.unmount();
  });
});
