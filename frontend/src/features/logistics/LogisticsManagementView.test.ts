import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import LogisticsManagementView from './LogisticsManagementView.vue';
import { createMockLogisticsService } from './mockLogisticsService';
import { currentUser } from '../../services/authSession';
import type {LoginResult} from '../../types/auth';
import { clearMessages,messages } from '../../components/feedback/message';
const permissions=(codes:string[])=>{currentUser.value={permissions:codes} as LoginResult;};
afterEach(()=>{currentUser.value=null;clearMessages();});
describe('logistics management',()=>{
  it('lists companies and separates account/template writes from company access',async()=>{
    permissions(['logistics:view']);const service=createMockLogisticsService();
    await service.createCompany({code:'A',name:'合作甲',provider:'MANUAL',status:'disabled',contactName:'',contactPhone:'',sortOrder:0,remark:'',version:0});
    const wrapper=mount(LogisticsManagementView,{props:{service}});await flushPromises();
    expect(wrapper.text()).toContain('合作甲');expect(wrapper.find('[data-testid="company-new"]').exists()).toBe(false);
    await wrapper.get('[data-testid="company-configure-1"]').trigger('click');await flushPromises();
    expect(wrapper.find('[data-testid="account-tab"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="company-form"]').exists()).toBe(false);
    wrapper.unmount();
  });
  it('creates, edits and changes cooperation status with Message feedback',async()=>{
    permissions(['logistics:view','logistics:edit']);const service=createMockLogisticsService();
    const wrapper=mount(LogisticsManagementView,{props:{service}});await flushPromises();
    await wrapper.get('[data-testid="company-new"]').trigger('click');
    await wrapper.get('[name="company-name"]').setValue('合作甲');await wrapper.get('[name="company-code"]').setValue('A');
    await wrapper.get('[data-testid="company-form"]').trigger('submit');await flushPromises();
    expect(messages.value.some(m=>m.text==='公司资料已保存')).toBe(true);
    await wrapper.get('[data-testid="company-back"]').trigger('click');await flushPromises();
    await wrapper.get('[data-testid="company-status-1"]').trigger('click');
    await wrapper.get('[data-testid="status-confirm"]').trigger('click');await flushPromises();
    expect((await service.getCompany(1)).status).toBe('enabled');
    expect(messages.value.some(m=>m.text==='合作状态已更新')).toBe(true);
    wrapper.unmount();
  });
});
