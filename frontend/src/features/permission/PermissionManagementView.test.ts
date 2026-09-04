import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { organizationService } from '../organization/organizationService';
import { createMockPermissionService } from './mockPermissionService';
import type { PermissionService } from './permissionService';
import type { PermissionRole, RoleMemberPage } from './types';
import PermissionManagementView from './views/PermissionManagementView.vue';

function mountPermissionView(service: PermissionService, permissions: string[]) {
  saveCurrentUser({
    accessToken: 'token', mobile: '13800138000', roles: ['SUPER_ADMIN'], permissions, loginMethod: 'password'
  });
  return mount(PermissionManagementView, { props: { service } });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function memberPage(employeeId: number, employeeName: string): RoleMemberPage {
  return {
    records: [{ employeeId, employeeNo: `E-${employeeId}`, employeeName, mobile: '13800000000', departmentId: 2, departmentName: '产品中心', positionName: '产品经理', employmentType: 'formal', otherRoleNames: [], finalDataScope: 'department', lockedReason: null }],
    page: 1,
    pageSize: 20,
    total: 1
  };
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
    expect(wrapper.get('[data-testid="selected-role-status"]').text()).toContain('停用');
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

  it('keeps the latest role selection when an earlier selection resolves late', async () => {
    const base = createMockPermissionService(organizationService);
    const role4 = await base.getRole(4);
    const role5 = await base.getRole(5);
    const first = deferred<PermissionRole>();
    const second = deferred<PermissionRole>();
    const service: PermissionService = {
      ...base,
      getRole: vi.fn((id) => id === 4 ? first.promise : id === 5 ? second.promise : base.getRole(id))
    };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();

    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await wrapper.get('[data-testid="role-item-5"]').trigger('click');
    second.resolve(role5);
    await flushPromises();
    first.resolve(role4);
    await flushPromises();

    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('WAREHOUSE_MANAGER');
  });

  it('keeps the latest member and candidate filter response when earlier requests resolve late', async () => {
    const base = createMockPermissionService(organizationService);
    const staleMembers = deferred<RoleMemberPage>();
    const latestMembers = deferred<RoleMemberPage>();
    const staleCandidates = deferred<RoleMemberPage>();
    const latestCandidates = deferred<RoleMemberPage>();
    const service: PermissionService = {
      ...base,
      listMembers: vi.fn((roleId, query) => query.keyword === '旧' ? staleMembers.promise : query.keyword === '新' ? latestMembers.promise : base.listMembers(roleId, query)),
      listCandidates: vi.fn((roleId, query) => query.keyword === '旧' ? staleCandidates.promise : query.keyword === '新' ? latestCandidates.promise : base.listCandidates(roleId, query))
    };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-keyword"]').setValue('旧');
    await wrapper.get('[data-testid="member-keyword"]').setValue('新');
    latestMembers.resolve(memberPage(92, '最新成员'));
    await flushPromises();
    staleMembers.resolve(memberPage(91, '过期成员'));
    await flushPromises();
    expect(wrapper.text()).toContain('最新成员');
    expect(wrapper.text()).not.toContain('过期成员');

    await wrapper.get('[data-testid="member-add"]').trigger('click');
    await wrapper.get('[data-testid="candidate-keyword"]').setValue('旧');
    await wrapper.get('[data-testid="candidate-keyword"]').setValue('新');
    latestCandidates.resolve(memberPage(82, '最新候选人'));
    await flushPromises();
    staleCandidates.resolve(memberPage(81, '过期候选人'));
    await flushPromises();
    expect(wrapper.text()).toContain('最新候选人');
    expect(wrapper.text()).not.toContain('过期候选人');
  });

  it('snapshots a configuration save and prevents changes while the save is pending', async () => {
    const base = createMockPermissionService(organizationService);
    const saved = deferred<PermissionRole>();
    const service: PermissionService = {
      ...base,
      saveConfiguration: vi.fn(() => saved.promise)
    };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="permission-customer-create"]').setValue(true);
    await wrapper.get('[data-testid="save-role-configuration"]').trigger('click');

    expect(wrapper.get('[data-testid="permission-customer-create"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="permission-product-create"]').attributes('disabled')).toBeDefined();
    const captured = vi.mocked(service.saveConfiguration).mock.calls[0][1];
    await wrapper.get('[data-testid="permission-product-create"]').setValue(false);
    expect(captured.permissionCodes).toContain('customer:create');
    expect(captured.permissionCodes).toContain('product:create');
    saved.resolve(await base.getRole(4));
    await flushPromises();
  });

  it('keeps configuration, role-form, and member-selection drafts independent when cancelling', async () => {
    const service = createMockPermissionService(organizationService);
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="permission-customer-create"]').setValue(true);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await wrapper.get('[data-testid="cancel-role-configuration"]').trigger('click');
    expect(confirm).toHaveBeenCalledWith('当前角色存在未保存修改，是否放弃？');
    expect(wrapper.get('[data-testid="permission-customer-create"]').element).toHaveProperty('checked', true);

    await wrapper.get('[data-testid="create-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('未保存角色');
    await wrapper.get('[data-testid="role-cancel"]').trigger('click');
    expect(wrapper.find('[data-testid="role-form-drawer"]').exists()).toBe(true);

    confirm.mockReturnValue(true);
    await wrapper.get('[data-testid="role-cancel"]').trigger('click');
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-add"]').trigger('click');
    await wrapper.get('[data-testid="candidate-select-2"]').setValue(true);
    await wrapper.get('[data-testid="close-member-selection"]').trigger('click');
    expect(wrapper.find('[data-testid="member-selection-drawer"]').exists()).toBe(false);
    await wrapper.get('[role="tab"][aria-selected="false"]').trigger('click');
    expect(wrapper.get('[data-testid="permission-customer-create"]').element).toHaveProperty('checked', true);
  });

  it('renders retryable error and permission-gated empty states from the injected service context', async () => {
    const base = createMockPermissionService(organizationService);
    const service = {
      ...base,
      getPermissionContext: vi.fn().mockResolvedValue({ departments: [{ id: 2, name: '产品中心' }], organizationSummary: '注入的组织摘要' }),
      listRoles: vi.fn().mockRejectedValueOnce(new Error('角色加载失败')).mockResolvedValue([])
    } as PermissionService & { getPermissionContext: ReturnType<typeof vi.fn> };
    const wrapper = mountPermissionView(service, ['system:role:view']);
    await flushPromises();
    expect(wrapper.get('[data-testid="permission-load-error"]').text()).toContain('角色加载失败');
    await wrapper.get('[data-testid="retry-permission-load"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="permission-empty"]').text()).toContain('暂无角色');
    expect(wrapper.get('[data-testid="empty-create-role"]').attributes('disabled')).toBeDefined();
    expect(service.getPermissionContext).toHaveBeenCalled();
  });

  it('renders disabled custom roles read-only except for copying and re-enabling', async () => {
    const service = createMockPermissionService(organizationService);
    await service.changeRoleStatus(4, 'disabled');
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="permission-workspace"]').attributes('data-readonly')).toBe('true');
    expect(wrapper.find('[data-testid="edit-role"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="copy-role"]').text()).toBe('复制角色');
    expect(wrapper.get('[data-testid="role-status"]').text()).toBe('启用');
  });

  it('uses the approved title, summary copy, system notice, actions, and sticky configuration footer', async () => {
    const service = createMockPermissionService(organizationService);
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    expect(wrapper.get('[data-testid="permission-page-title"]').text()).toBe('权限管理');
    expect(wrapper.get('[data-testid="permission-page-subtitle"]').text()).toBe('按角色维护功能权限、数据范围与授权成员');
    expect(wrapper.get('[data-testid="permission-role-count"]').text()).toBe('8 个角色');
    expect(wrapper.get('[data-testid="system-role-readonly-notice"]').text()).toBe('系统角色不可编辑');

    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="role-updated-at"]').text()).toContain('最近更新：');
    expect(wrapper.get('[data-testid="edit-role"]').text()).toBe('编辑角色');
    expect(wrapper.get('[data-testid="copy-role"]').text()).toBe('复制角色');
    expect(wrapper.get('[data-testid="role-status"]').text()).toBe('停用');
    expect(wrapper.get('[data-testid="configuration-footer"]').classes()).toContain('sticky');
    expect(wrapper.get('[data-testid="cancel-role-configuration"]').text()).toBe('取消修改');
    expect(wrapper.get('[data-testid="save-role-configuration"]').text()).toBe('保存配置');

    await wrapper.get('[data-testid="create-role"]').trigger('click');
    expect(wrapper.get('[data-testid="role-form-drawer"]').text()).toContain('创建角色后继续配置权限和成员');
  });

  it('keeps a user selection over a late initial role load and keeps the newest member page', async () => {
    const base = createMockPermissionService(organizationService);
    const initial = deferred<PermissionRole>();
    const pageTwo = deferred<RoleMemberPage>();
    const service: PermissionService = {
      ...base,
      getRole: vi.fn((id) => id === 1 ? initial.promise : base.getRole(id)),
      listMembers: vi.fn((roleId, query) => query.page === 2 ? pageTwo.promise : base.listMembers(roleId, query))
    };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    initial.resolve(await base.getRole(1));
    await flushPromises();
    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('PRODUCT_OPERATOR');

    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="page-size"]').setValue('10');
    await flushPromises();
    await wrapper.get('[data-testid="member-next-page"]').trigger('click');
    await wrapper.get('[data-testid="member-previous-page"]').trigger('click');
    pageTwo.resolve(memberPage(91, '过期第 2 页'));
    await flushPromises();
    expect(wrapper.text()).not.toContain('过期第 2 页');
  });

  it('controls filters, keeps add failure inside the drawer, and blocks edits for a view-only user', async () => {
    const base = createMockPermissionService(organizationService);
    const service: PermissionService = { ...base, addMembers: vi.fn().mockRejectedValue(new Error('添加失败')) };
    const wrapper = mountPermissionView(service, ['system:role:view']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="edit-role"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="create-role"]').attributes('disabled')).toBeDefined();
    clearCurrentUser();
    saveCurrentUser({ accessToken: 'token', mobile: '13800138000', roles: ['SUPER_ADMIN'], permissions: ['system:role:view', 'system:role:manage'], loginMethod: 'password' });
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-keyword"]').setValue('不存在');
    await flushPromises();
    await wrapper.get('[data-testid="member-clear-filters"]').trigger('click');
    await flushPromises();
    expect((wrapper.get('[data-testid="member-keyword"]').element as HTMLInputElement).value).toBe('');
    await wrapper.get('[data-testid="member-add"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="candidate-select-2"]').setValue(true);
    await wrapper.get('[data-testid="member-selection-submit"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="candidate-error"]').text()).toContain('添加失败');
    expect(wrapper.find('[data-testid="member-selection-drawer"]').exists()).toBe(true);
  });

  it('confirms dirty configuration before a create or copy can replace the selected role, while edit keeps all local context', async () => {
    const base = createMockPermissionService(organizationService);
    const service: PermissionService = { ...base, createRole: vi.fn(base.createRole), updateRole: vi.fn(base.updateRole) };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="permission-customer-create"]').setValue(true);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await wrapper.get('[data-testid="create-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('不应创建');
    await wrapper.get('[data-testid="role-code"]').setValue('DECLINED_CREATE');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    expect(confirm).toHaveBeenCalledWith('当前角色存在未保存修改，是否放弃？');
    expect(service.createRole).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="role-form-drawer"]').exists()).toBe(true);

    confirm.mockReturnValue(true);
    await wrapper.get('[data-testid="role-cancel"]').trigger('click');
    await wrapper.get('[data-testid="copy-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('不应复制');
    await wrapper.get('[data-testid="role-code"]').setValue('DECLINED_COPY');
    confirm.mockReturnValue(false);
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    expect(service.createRole).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    await wrapper.get('[data-testid="role-cancel"]').trigger('click');
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-keyword"]').setValue('张');
    await flushPromises();
    await wrapper.get('[data-testid="edit-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('商品运营新版');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('成员管理');
    expect((wrapper.get('[data-testid="member-keyword"]').element as HTMLInputElement).value).toBe('张');
    await wrapper.get('[role="tab"][aria-selected="false"]').trigger('click');
    expect(wrapper.get('[data-testid="selected-role-name"]').text()).toBe('商品运营新版');
    expect(wrapper.get('[data-testid="permission-customer-create"]').element).toHaveProperty('checked', true);
  });

  it('commits a created role before a refresh failure and never reports the committed create as failed', async () => {
    const base = createMockPermissionService(organizationService);
    const service: PermissionService = {
      ...base,
      listRoles: vi.fn().mockResolvedValueOnce(await base.listRoles()).mockRejectedValue(new Error('刷新失败'))
    };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="create-role"]').trigger('click');
    await wrapper.get('[data-testid="role-name"]').setValue('刷新失败后仍创建');
    await wrapper.get('[data-testid="role-code"]').setValue('REFRESH_FAILURE');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="role-form-drawer"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('REFRESH_FAILURE');
    expect(wrapper.get('[data-testid="permission-feedback"]').text()).toContain('角色已创建');
    expect(wrapper.get('[data-testid="permission-feedback"]').text()).toContain('角色列表刷新失败');
    expect(wrapper.find('[data-testid="role-form-error"]').exists()).toBe(false);
  });

  it('refreshes the preserved member query after configuration and status mutations, and reports initial selection failure', async () => {
    const base = createMockPermissionService(organizationService);
    const service: PermissionService = { ...base, listMembers: vi.fn(base.listMembers) };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="tab-members"]').trigger('click');
    await wrapper.get('[data-testid="member-keyword"]').setValue('张');
    await flushPromises();
    const beforeConfiguration = vi.mocked(service.listMembers).mock.calls.length;
    await wrapper.get('[role="tab"][aria-selected="false"]').trigger('click');
    await wrapper.get('[data-testid="permission-customer-create"]').setValue(true);
    await wrapper.get('[data-testid="save-role-configuration"]').trigger('click');
    await flushPromises();
    expect(vi.mocked(service.listMembers).mock.calls.length).toBeGreaterThan(beforeConfiguration);
    expect(vi.mocked(service.listMembers).mock.calls.at(-1)?.[1]).toMatchObject({ keyword: '张' });
    const beforeStatus = vi.mocked(service.listMembers).mock.calls.length;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await wrapper.get('[data-testid="role-status"]').trigger('click');
    await flushPromises();
    expect(vi.mocked(service.listMembers).mock.calls.length).toBeGreaterThan(beforeStatus);

    const rejected: PermissionService = { ...base, getRole: vi.fn().mockRejectedValue(new Error('默认角色加载失败')) };
    const rejectedWrapper = mountPermissionView(rejected, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    expect(rejectedWrapper.get('[data-testid="permission-role-load-error"]').text()).toContain('默认角色加载失败');
  });

  it('uses the remaining approved role visuals and keeps action controls disabled during selection loading', async () => {
    const base = createMockPermissionService(organizationService);
    const pending = deferred<PermissionRole>();
    const service: PermissionService = { ...base, getRole: vi.fn((id) => id === 5 ? pending.promise : base.getRole(id)) };
    const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
    await flushPromises();
    expect(wrapper.get('[data-testid="permission-role-count"]').classes()).toContain('rounded');
    expect(wrapper.get('[data-testid="role-item-8"]').text()).toContain('已停用');
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="selected-role-status"]').text()).toBe('启用');
    await wrapper.get('[data-testid="edit-role"]').trigger('click');
    expect(wrapper.get('[data-testid="role-form-drawer"]').text()).not.toContain('创建角色后继续配置权限和成员');
    await wrapper.get('[data-testid="role-cancel"]').trigger('click');
    await wrapper.get('[data-testid="role-item-5"]').trigger('click');
    expect(wrapper.get('[data-testid="create-role"]').attributes('disabled')).toBeDefined();
    pending.resolve(await base.getRole(5));
    await flushPromises();
  });
});
