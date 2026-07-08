import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import WorkspacePrototype from './WorkspacePrototype.vue';

describe('WorkspacePrototype product catalog', () => {
  function findButtonByText(wrapper: ReturnType<typeof mount>, text: string) {
    const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
    expect(button, `button containing "${text}"`).toBeTruthy();
    return button!;
  }

  it('opens product master data from the sidebar and filters SKU rows', async () => {
    const wrapper = mount(WorkspacePrototype);

    await findButtonByText(wrapper, '产品资料').trigger('click');

    expect(wrapper.text()).toContain('SKU 主数据');
    expect(wrapper.text()).toContain('BBF-FEED-001');
    expect(wrapper.text()).toContain('BBF-TANK-009');

    await wrapper.get('input[placeholder="Search here"]').setValue('鱼缸');

    expect(wrapper.text()).toContain('智能生态鱼缸 Mini');
    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).not.toContain('热带鱼粮组合装');
  });

  it('opens the selected product detail in a right drawer from the SKU list', async () => {
    const wrapper = mount(WorkspacePrototype);

    await findButtonByText(wrapper, '产品资料').trigger('click');

    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);

    await findButtonByText(wrapper, '智能生态鱼缸 Mini').trigger('click');

    const drawer = wrapper.get('[data-testid="product-detail-drawer"]');
    const drawerText = drawer.text();
    expect(drawer.classes()).toContain('w-[50vw]');
    expect(drawerText).toContain('商品详情');
    expect(drawerText).toContain('BBF-TANK-009');
    expect(drawerText).toContain('待补主图');
    expect(drawerText).toContain('产品图片');
    expect(drawerText).toContain('货号');
    expect(drawerText).toContain('规格');
    expect(drawerText).toContain('外箱尺寸');
    expect(drawerText).toContain('单价');
    expect(drawerText).toContain('内盒包装');
    expect(drawerText).toContain('单杯条码');
    expect(drawerText).toContain('渠道商');
    expect(drawerText).toContain('内盒尺寸');
    expect(drawerText).toContain('内盒重量');
    expect(drawerText).toContain('外箱图片');
    expect(drawerText).toContain('内盒包装图');
    expect(drawerText).toContain('克重');
    expect(drawerText).toContain('净重');
    expect(drawerText).toContain('毛重');

    await wrapper.get('[data-testid="close-product-detail-drawer"]').trigger('click');

    expect(wrapper.find('[data-testid="product-detail-drawer"]').exists()).toBe(false);
  });
});
