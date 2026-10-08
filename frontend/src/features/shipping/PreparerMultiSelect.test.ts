import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PreparerMultiSelect from './PreparerMultiSelect.vue';

const options = [
  { employeeId: 2, employeeName: '小周' },
  { employeeId: 5, employeeName: '阿杰' }
];

describe('PreparerMultiSelect', () => {
  it('allows explicit removal of an unavailable employee account without guessing name identity', async () => {
    const wrapper = mount(PreparerMultiSelect, { props: { modelValue: ['停用员工'], employeeIds: [99], options } });
    expect(wrapper.text()).toContain('不可用账号 #99');
    await wrapper.setProps({ modelValue: [] });
    expect(wrapper.text()).toContain('不可用账号 #99');
    await wrapper.get('[data-testid="preparer-remove-99"]').trigger('click');
    expect(wrapper.emitted('update:employeeIds')).toEqual([[[]]]);
    wrapper.unmount();
  });
  it('keeps the original name snapshot order while confirming historical accounts', async () => {
    const wrapper = mount(PreparerMultiSelect, { props: { modelValue: ['阿杰', '小周'], employeeIds: [], options } });
    await wrapper.get('[data-testid="preparer-toggle"]').trigger('click');
    await wrapper.get('[data-testid="preparer-option-2"]').trigger('click');
    await wrapper.setProps({ employeeIds: [2], modelValue: ['阿杰', '小周'] });
    await wrapper.get('[data-testid="preparer-option-5"]').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['阿杰', '小周']]);
    wrapper.unmount();
  });
  it('distinguishes employee accounts with identical names', async () => {
    const wrapper = mount(PreparerMultiSelect, { props: { modelValue: ['小周'], employeeIds: [2],
      options: [{ employeeId: 2, employeeName: '小周' }, { employeeId: 9, employeeName: '小周' }] } });
    await wrapper.get('[data-testid="preparer-toggle"]').trigger('click');
    expect(wrapper.get('[data-testid="preparer-option-2"]').attributes('aria-selected')).toBe('true');
    expect(wrapper.get('[data-testid="preparer-option-9"]').attributes('aria-selected')).toBe('false');
    await wrapper.get('[data-testid="preparer-option-9"]').trigger('click');
    expect(wrapper.emitted('update:employeeIds')).toEqual([[[2, 9]]]);
    wrapper.unmount();
  });
  it('toggles choices without duplicates and keeps legacy selected names', async () => {
    const wrapper = mount(PreparerMultiSelect, { props: { modelValue: ['离职员工'], options } });
    expect(wrapper.text()).toContain('离职员工');
    await wrapper.get('[data-testid="preparer-toggle"]').trigger('click');
    await wrapper.get('[data-testid="preparer-option-2"]').trigger('click');
    await wrapper.get('[data-testid="preparer-option-2"]').trigger('click');

    expect(wrapper.emitted('update:modelValue')).toEqual([[['离职员工', '小周']], [['离职员工', '小周']]]);
  });

  it('closes on Escape and outside pointerdown', async () => {
    const wrapper = mount(PreparerMultiSelect, { attachTo: document.body, props: { modelValue: [], options } });
    const toggle = wrapper.get('[data-testid="preparer-toggle"]');
    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await toggle.trigger('click');
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(toggle.attributes('aria-expanded')).toBe('false');
    wrapper.unmount();
  });
});
