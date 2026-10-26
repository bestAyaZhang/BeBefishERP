import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import FreightTemplateEditor from './FreightTemplateEditor.vue';
describe('contract rate editor',()=>{
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
});
