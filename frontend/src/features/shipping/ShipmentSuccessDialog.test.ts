import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ShipmentSuccessDialog from './ShipmentSuccessDialog.vue';

describe('ShipmentSuccessDialog', () => {
  it('shows the returned tracking number and only closes through the two actions', async () => {
    const wrapper = mount(ShipmentSuccessDialog, { attachTo: document.body, props: { open: true, trackingNo: 'ANE202609180018', orderNo: 'BF018' } });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[role="dialog"]').text()).toContain('ANE202609180018');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="success-back-list"]').element);
    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' });
    expect(wrapper.emitted('back-list')).toBeUndefined();
    await wrapper.get('[data-testid="success-continue"]').trigger('click');
    expect(wrapper.emitted('continue')).toHaveLength(1);
    wrapper.unmount();
  });

  it('marks a test order as unusable for real shipping', () => {
    const wrapper = mount(ShipmentSuccessDialog, {
      props: { open: true, trackingNo: '123456789012', orderNo: 'BF019', testEnvironment: true }
    });
    const dialog = wrapper.get('[role="dialog"]').text();
    expect(dialog).toContain('测试环境');
    expect(dialog).toContain('不可用于实际走货');
    expect(dialog).toContain('测试运单号');
    expect(dialog).toContain('123456789012');
    expect(dialog).not.toContain('请按此单号安排后续发货');
    wrapper.unmount();
  });
});
