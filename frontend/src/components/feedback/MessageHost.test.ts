import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MessageHost from './MessageHost.vue';
import { clearMessages, message } from './message';

describe('MessageHost', () => {
  afterEach(() => {
    clearMessages();
    vi.useRealTimers();
  });

  it('renders a success message at the top and allows it to be closed', async () => {
    const wrapper = mount(MessageHost);
    const id = message.success('保存成功', 0);
    await nextTick();

    const item = wrapper.get(`[data-testid="message-${id}"]`);
    expect(item.attributes('role')).toBe('alert');
    expect(item.text()).toContain('保存成功');
    expect(wrapper.get('[data-testid="message-host"]').classes()).toContain('fixed');

    await wrapper.get(`[data-testid="message-close-${id}"]`).trigger('click');
    await nextTick();
    expect(wrapper.find(`[data-testid="message-${id}"]`).exists()).toBe(false);
  });

  it('automatically removes a failed message after its duration', async () => {
    vi.useFakeTimers();
    const wrapper = mount(MessageHost);
    const id = message.error('保存失败', 1000);
    await nextTick();

    expect(wrapper.get(`[data-testid="message-${id}"]`).text()).toContain('保存失败');
    vi.advanceTimersByTime(1000);
    await nextTick();

    expect(wrapper.find(`[data-testid="message-${id}"]`).exists()).toBe(false);
  });
});
