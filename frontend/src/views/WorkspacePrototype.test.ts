import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import WorkspacePrototype from './WorkspacePrototype.vue';

describe('WorkspacePrototype product catalog', () => {
  const currentUserStorageKey = 'bebefish_current_user';

  beforeEach(() => {
    localStorage.setItem(currentUserStorageKey, JSON.stringify({
      accessToken: 'token',
      mobile: '13800138000',
      roles: ['ADMIN'],
      permissions: ['dashboard:view', 'product:view', 'inventory:view', 'sales:create', 'sales:view'],
      loginMethod: 'password'
    }));
  });

  afterEach(() => {
    localStorage.clear();
  });

  function findButtonByText(wrapper: ReturnType<typeof mount>, text: string) {
    const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
    expect(button, `button containing "${text}"`).toBeTruthy();
    return button!;
  }

  it('renders page content inside the shared prototype shell', () => {
    const wrapper = mount(WorkspacePrototype);

    expect(wrapper.find('[data-testid="workspace-prototype-content"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="erp-sidebar"]').exists()).toBe(false);
  });

  it('opens product master data from the sidebar and filters SKU rows', async () => {
    const wrapper = mount(WorkspacePrototype, { props: { initialSection: 'products' } });

    expect(wrapper.text()).toContain('SKU 主数据');
    expect(wrapper.text()).toContain('BBF-FEED-001');
    expect(wrapper.text()).toContain('BBF-TANK-009');

    await wrapper.get('input[placeholder="Search here"]').setValue('鱼缸');

    expect(wrapper.text()).toContain('智能生态鱼缸 Mini');
    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).not.toContain('热带鱼粮组合装');
  });

  it('filters product rows by article number, brand, supplier, and category', async () => {
    const wrapper = mount(WorkspacePrototype);

    const articleFilter = wrapper.get('[data-testid="product-filter-article"]');

    await articleFilter.setValue('AQ-TANK-MINI-09');

    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).not.toContain('BBF-FEED-001');

    await articleFilter.setValue('');
    await wrapper.get('[data-testid="product-filter-brand-button"]').trigger('click');
    expect(wrapper.find('[data-testid="product-filter-brand-menu"]').exists()).toBe(true);
    await wrapper.get('[data-testid="product-filter-brand-option-AquaLab"]').trigger('click');

    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).toContain('BBF-PUMP-021');
    expect(wrapper.text()).not.toContain('BBF-FEED-001');

    await wrapper.get('[data-testid="product-filter-brand-button"]').trigger('click');
    await wrapper.get('[data-testid="product-filter-brand-option-all"]').trigger('click');
    await wrapper.get('[data-testid="product-filter-supplier-button"]').trigger('click');
    const supplierOptions = wrapper.findAll('[data-testid="product-filter-supplier-option"]');
    await supplierOptions[3].trigger('click');

    expect(wrapper.text()).toContain('BBF-PUMP-021');
    expect(wrapper.text()).not.toContain('BBF-TANK-009');

    await wrapper.get('[data-testid="product-filter-supplier-button"]').trigger('click');
    await wrapper.get('[data-testid="product-filter-supplier-option-all"]').trigger('click');
    await wrapper.get('[data-testid="product-filter-category-button"]').trigger('click');
    const categoryOptions = wrapper.findAll('[data-testid="product-filter-category-option"]');
    await categoryOptions[1].trigger('click');

    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).not.toContain('BBF-FEED-001');
  });
  it('paginates the product catalog list and supports changing page size', async () => {
    const wrapper = mount(WorkspacePrototype);

    const pagination = wrapper.get('[data-testid="product-pagination"]');
    expect(pagination.text()).toContain('5');

    await wrapper.get('[data-testid="product-page-size"]').setValue('2');

    expect(wrapper.text()).toContain('BBF-FEED-001');
    expect(wrapper.text()).toContain('BBF-TANK-009');
    expect(wrapper.text()).not.toContain('BBF-CLEAN-017');

    await wrapper.get('[data-testid="product-page-next"]').trigger('click');

    expect(wrapper.text()).toContain('BBF-CLEAN-017');
    expect(wrapper.text()).toContain('BBF-PUMP-021');
    expect(wrapper.text()).not.toContain('BBF-FEED-001');
  });
  it('opens a create product form with the required product information fields', async () => {
    const wrapper = mount(WorkspacePrototype);

    expect(wrapper.find('[data-testid="product-create-form"]').exists()).toBe(false);

    await wrapper.get('[data-testid="add-product-button"]').trigger('click');

    const form = wrapper.get('[data-testid="product-create-form"]');
    const requiredFields = [
      'create-product-image',
      'create-product-name',
      'create-product-sku',
      'create-product-article-no',
      'create-product-spec',
      'create-product-outer-length',
      'create-product-outer-width',
      'create-product-outer-height',
      'create-product-unit-price',
      'create-product-inner-packaging',
      'create-product-cup-barcode',
      'create-product-distributor',
      'create-product-inner-length',
      'create-product-inner-width',
      'create-product-inner-height',
      'create-product-inner-weight',
      'create-product-outer-box-image',
      'create-product-inner-packaging-image',
      'create-product-gram-weight',
      'create-product-net-weight',
      'create-product-gross-weight'
    ];

    requiredFields.forEach((testId) => {
      expect(form.find(`[data-testid="${testId}"]`).exists(), testId).toBe(true);
    });

    ['create-product-image', 'create-product-outer-box-image', 'create-product-inner-packaging-image'].forEach((testId) => {
      const upload = form.get(`[data-testid="${testId}"]`);
      expect(upload.element.tagName).toBe('INPUT');
      expect(upload.attributes('type')).toBe('file');
      expect(upload.attributes('accept')).toBe('image/*');
    });

    ['create-product-brand', 'create-product-category', 'create-product-supplier', 'create-product-distributor', 'create-product-inner-packaging'].forEach((testId) => {
      expect(form.get(`[data-testid="${testId}"]`).element.tagName).toBe('BUTTON');
      expect(form.find(`[data-testid="${testId}-menu"]`).exists()).toBe(false);
    });

    await form.get('[data-testid="create-product-brand"]').trigger('click');

    expect(form.find('[data-testid="create-product-brand-menu"]').exists()).toBe(true);
    const brandOptions = form.findAll('[data-testid="create-product-brand-option"]');
    expect(brandOptions.length).toBeGreaterThan(0);

    await brandOptions[1].trigger('click');

    expect(form.get('[data-testid="create-product-brand"]').text()).toContain('AquaLab');
    expect(form.find('[data-testid="create-product-brand-menu"]').exists()).toBe(false);

    await wrapper.get('[data-testid="back-to-product-list"]').trigger('click');

    expect(wrapper.find('[data-testid="product-create-form"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('BBF-FEED-001');
  });
  it('opens the selected product detail in a right drawer from the SKU list', async () => {
    const wrapper = mount(WorkspacePrototype, { props: { initialSection: 'products' } });

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
