import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import router from '../router';
import WorkbenchView from './WorkbenchView.vue';

describe('WorkbenchView', () => {
  it('renders the approved realtime transaction workbench structure', () => {
    const wrapper = mount(WorkbenchView, { global: { plugins: [router] } });

    expect(wrapper.get('h1').text()).toBe('工作台');
    expect(wrapper.text()).toContain('实时交易');
    expect(wrapper.text()).toContain('快捷入口');
    expect(wrapper.find('aside').exists()).toBe(true);
  });
});
