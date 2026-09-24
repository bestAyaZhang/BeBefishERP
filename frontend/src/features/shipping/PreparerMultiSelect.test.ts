import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PreparerMultiSelect from './PreparerMultiSelect.vue';

const options = [
  { employeeId: 2, employeeName: '小周' },
  { employeeId: 5, employeeName: '阿杰' }
];

describe('PreparerMultiSelect', () => {
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
