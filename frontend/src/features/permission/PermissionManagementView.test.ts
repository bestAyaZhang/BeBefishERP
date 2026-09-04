import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { organizationService } from '../organization/organizationService';
import { createMockPermissionService } from './mockPermissionService';
import type { PermissionService } from './permissionService';
import PermissionManagementView from './views/PermissionManagementView.vue';

function mountPermissionView(service: PermissionService, permissions: string[]) {
  saveCurrentUser({
    accessToken: 'token', mobile: '13800138000', roles: ['SUPER_ADMIN'], permissions, loginMethod: 'password'
  });
  return mount(PermissionManagementView, { props: { service } });
}

describe('PermissionManagementView', () => {
  afterEach(() => {
    clearCurrentUser();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads super administrator read-only and exposes all three tabs', async () => {
    const service = createMockPermissionService(organizationService);
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();

    expect(wrapper.get('[data-testid="selected-role-name"]').text()).toBe('超级管理员');
    expect(wrapper.get('[data-testid="permission-workspace"]').attributes('data-readonly')).toBe('true');
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
  });

  it('preserves dirty edits on save failure and confirms before role switch', async () => {
    const service = createMockPermissionService(organizationService);
    service.saveConfiguration = vi.fn().mockRejectedValue(new Error('保存失败，请重试'));
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="permission-customer-create"]').setValue(true);
    await wrapper.get('[data-testid="save-role-configuration"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="permission-save-error"]').text()).toContain('保存失败，请重试');
    expect(wrapper.get('[data-testid="save-role-configuration"]').attributes('disabled')).toBeUndefined();

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await wrapper.get('[data-testid="role-item-5"]').trigger('click');
    await flushPromises();
    expect(confirm).toHaveBeenCalledWith('当前角色存在未保存修改，是否放弃？');
    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('PRODUCT_OPERATOR');
  });

  it('selects a created role and refreshes its member count after member changes', async () => {
    const service = createMockPermissionService(organizationService);
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="create-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('渠道运营');
    await wrapper.get('[data-testid="role-code"]').setValue('CHANNEL_OPERATOR');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('CHANNEL_OPERATOR');
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('权限配置');

    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-add"]').trigger('click');
    await wrapper.get('[data-testid="candidate-select-2"]').setValue(true);
    await wrapper.get('[data-testid="member-selection-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="selected-role-member-count"]').text()).toContain('1');
  });

  it('edits, copies, and toggles a custom role through the injected service', async () => {
    const service = createMockPermissionService(organizationService);
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();

    await wrapper.get('[data-testid="edit-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('商品营运');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="selected-role-name"]').text()).toBe('商品营运');

    await wrapper.get('[data-testid="copy-role"]').trigger('click');
    expect((wrapper.get('[data-testid="role-copy-source"]').element as HTMLSelectElement).value).toBe('4');
    await wrapper.get('[data-testid="role-name"]').setValue('商品运营副本');
    await wrapper.get('[data-testid="role-code"]').setValue('PRODUCT_OPERATOR_COPY');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('PRODUCT_OPERATOR_COPY');

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await wrapper.get('[data-testid="role-status"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="selected-role-status"]').text()).toContain('已停用');
    expect(wrapper.get('[data-testid="role-status"]').text()).toContain('启用');
  });

  it('keeps the current member page when a successful removal refreshes the role', async () => {
    const service = createMockPermissionService(organizationService);
    const candidates = await service.listCandidates(4, { page: 1, size: 50 });
    await service.addMembers(4, candidates.records.slice(0, 10).map((member) => member.employeeId));
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="page-size"]').setValue('10');
    await flushPromises();
    await wrapper.get('[data-testid="member-next-page"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="page-2"]').attributes('aria-current')).toBe('page');

    await wrapper.get('tbody input[type="checkbox"]:not([disabled])').setValue(true);
    await wrapper.get('[data-testid="member-remove-selected"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="page-2"]').attributes('aria-current')).toBe('page');
  });
});
