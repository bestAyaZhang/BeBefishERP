import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DataScopePanel from './components/DataScopePanel.vue';
import FeishuRoleMappingPanel from './components/FeishuRoleMappingPanel.vue';
import MemberSelectionDrawer from './components/MemberSelectionDrawer.vue';
import PermissionMatrix from './components/PermissionMatrix.vue';
import RoleFormDrawer from './components/RoleFormDrawer.vue';
import RoleListPanel from './components/RoleListPanel.vue';
import RoleMembersPanel from './components/RoleMembersPanel.vue';
import { PERMISSION_MODULES } from './permissionCatalog';
import type { PermissionRoleSummary, RoleMemberPage } from './types';

describe('FeishuRoleMappingPanel', () => {
  it('never presents sensitive roles as mapping targets and emits a save payload', async () => {
    const wrapper = mount(FeishuRoleMappingPanel, {
      props: {
        mappings: [{ feishuRoleId: 'fs-product', feishuRoleName: '飞书商品运营', erpRoleId: 4, erpRoleName: '商品运营', enabled: true, memberCount: 8, lastSyncedAt: '2026-09-04T09:00:00Z', lastError: null }],
        feishuRoles: [{ id: 'fs-product', name: '飞书商品运营' }],
        candidateRoles: [
          { id: 1, code: 'SUPER_ADMIN', name: '超级管理员', sensitive: true },
          { id: 4, code: 'PRODUCT_OPERATOR', name: '商品运营', sensitive: false }
        ],
        canManage: true,
        loading: false,
        saving: false,
        error: ''
      }
    });

    await wrapper.get('[data-testid="edit-feishu-mapping-fs-product"]').trigger('click');
    expect(wrapper.text()).toContain('商品运营');
    expect(wrapper.text()).not.toContain('超级管理员');
    await wrapper.get('[data-testid="save-feishu-mapping-fs-product"]').trigger('click');
    expect(wrapper.emitted('save')?.[0]).toEqual(['fs-product', {
      feishuRoleName: '飞书商品运营', erpRoleId: 4, enabled: true
    }]);
  });

  it('shows empty, degraded, and read-only states', () => {
    const wrapper = mount(FeishuRoleMappingPanel, {
      props: {
        mappings: [{ feishuRoleId: 'fs-warehouse', feishuRoleName: '仓库主管', erpRoleId: 5, erpRoleName: '仓库管理员', enabled: false, memberCount: 3, lastSyncedAt: null, lastError: 'FEISHU_ROLE_DIRECTORY_UNAVAILABLE' }],
        feishuRoles: [], candidateRoles: [], canManage: false, loading: false, saving: false, error: ''
      }
    });

    expect(wrapper.text()).toContain('已停用');
    expect(wrapper.text()).toContain('3 名成员');
    expect(wrapper.text()).toContain('FEISHU_ROLE_DIRECTORY_UNAVAILABLE');
    expect(wrapper.find('[data-testid="edit-feishu-mapping-fs-warehouse"]').exists()).toBe(false);
  });
});

const roles: PermissionRoleSummary[] = [
  { id: 1, code: 'SUPER_ADMIN', name: '超级管理员', kind: 'system', immutable: true, status: 'enabled', memberCount: 2, updatedBy: '系统内置', updatedAt: '' },
  { id: 4, code: 'PRODUCT_OPERATOR', name: '商品运营', kind: 'custom', immutable: false, status: 'enabled', memberCount: 6, updatedBy: '张振亚', updatedAt: '2026-09-03' }
];

describe('RoleListPanel', () => {
  it('keeps the selected role context when search filters it out', async () => {
    const wrapper = mount(RoleListPanel, { props: { roles, selectedRoleId: 1, disabled: false } });
    await wrapper.get('[data-testid="role-search"]').setValue('商品');

    expect(wrapper.get('[data-testid="selected-role-filtered-notice"]').text()).toContain('当前角色不在筛选结果中');
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('emits a selected role only while switching is enabled', async () => {
    const wrapper = mount(RoleListPanel, { props: { roles, selectedRoleId: 1, disabled: false } });
    await wrapper.get('[data-testid="role-item-4"]').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[4]]);

    await wrapper.setProps({ disabled: true });
    await wrapper.get('[data-testid="role-item-1"]').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[4]]);
  });
});

describe('PermissionMatrix', () => {
  it('enforces view dependency and stays read-only for immutable roles', async () => {
    const editable = mount(PermissionMatrix, { props: { modules: PERMISSION_MODULES, modelValue: [], readonly: false } });
    await editable.get('[data-testid="permission-product-create"]').setValue(true);
    expect(editable.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(expect.arrayContaining(['product:view', 'product:create']));

    const readonly = mount(PermissionMatrix, { props: { modules: PERMISSION_MODULES, modelValue: ['product:view'], readonly: true } });
    expect(readonly.get('[data-testid="permission-product-view"]').attributes('disabled')).toBeDefined();
  });

  it('reports a mixed module selection and renders unsupported actions as dashes', () => {
    const wrapper = mount(PermissionMatrix, { props: { modules: PERMISSION_MODULES, modelValue: ['product:view'], readonly: false } });
    expect(wrapper.get('[data-testid="permission-group-product"]').attributes('aria-checked')).toBe('mixed');
    expect(wrapper.get('[data-testid="permission-dashboard-create"]').text()).toBe('—');
  });
});

describe('DataScopePanel and RoleMembersPanel', () => {
  it('emits the selected data scope and member page changes', async () => {
    const scope = mount(DataScopePanel, { props: { modelValue: 'self', readonly: false, organizationSummary: '产品中心及 3 个下级组织' } });
    expect(scope.text()).toContain('全公司：可访问公司内所有部门和员工数据；示例：总部管理角色。');
    expect(scope.text()).toContain('本部门及下级：可访问所在部门及全部下级部门；示例：业务中心负责人。');
    expect(scope.text()).toContain('本部门：仅可访问所在部门；示例：部门主管。');
    expect(scope.text()).toContain('仅本人：仅可访问本人创建或负责的数据；示例：一线业务员工。');
    await scope.get('[data-testid="scope-department-and-descendants"]').setValue(true);
    expect(scope.emitted('update:modelValue')?.[0]).toEqual(['department-and-descendants']);

    const memberPage: RoleMemberPage = { records: [], page: 1, pageSize: 20, total: 40 };
    const members = mount(RoleMembersPanel, { props: { page: memberPage, canManage: true, selectedIds: [] } });
    await members.get('[data-testid="member-next-page"]').trigger('click');
    expect(members.emitted('page')?.[0]).toEqual([2]);
  });

  it('only allows unlocked member rows to be selected', async () => {
    const page: RoleMemberPage = {
      records: [{ employeeId: 9, employeeNo: 'E009', employeeName: '张瑜', mobile: '13800000009', departmentId: 1, departmentName: '总部', positionName: '管理员', employmentType: 'formal', otherRoleNames: [], finalDataScope: 'company', lockedReason: '系统角色成员' }],
      page: 1,
      pageSize: 20,
      total: 1
    };
    const wrapper = mount(RoleMembersPanel, { props: { page, canManage: true, selectedIds: [] } });
    expect(wrapper.get('[data-testid="member-select-9"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="member-keyword"]').attributes('aria-label')).toBe('搜索成员');
    expect(wrapper.get('[data-testid="member-department"]').attributes('aria-label')).toBe('筛选部门');
    expect(wrapper.get('[data-testid="member-select-9"]').attributes('aria-label')).toBe('选择成员 张瑜');
    expect(wrapper.get('[data-testid="member-scope-help"]').attributes('aria-label')).toBe('最终数据范围按最宽的启用角色范围生效。');
  });
});

describe('RoleFormDrawer', () => {
  it('normalizes role codes and validates uniqueness before submit', async () => {
    const wrapper = mount(RoleFormDrawer, { props: { open: true, mode: 'create', role: null, copySources: roles, existingCodes: roles.map((role) => role.code), saving: false, error: '' } });
    await wrapper.get('[data-testid="role-name"]').setValue('渠道运营');
    await wrapper.get('[data-testid="role-code"]').setValue('channel_operator');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ name: '渠道运营', code: 'CHANNEL_OPERATOR' });
  });

  it('prevents duplicate normalized codes from being submitted', async () => {
    const wrapper = mount(RoleFormDrawer, { props: { open: true, mode: 'create', role: null, copySources: roles, existingCodes: roles.map((role) => role.code), saving: false, error: '' } });
    await wrapper.get('[data-testid="role-name"]').setValue('重复角色');
    await wrapper.get('[data-testid="role-code"]').setValue('product_operator');
    await wrapper.get('[data-testid="role-submit"]').trigger('click');
    expect(wrapper.emitted('submit')).toBeUndefined();
    expect(wrapper.get('[data-testid="role-code-error"]').text()).toContain('角色编码已存在');
  });
});

describe('MemberSelectionDrawer', () => {
  it('emits the selected employee ids once for bulk addition', async () => {
    const page: RoleMemberPage = {
      records: [{ employeeId: 7, employeeNo: 'E007', employeeName: '陈雯', mobile: '13800000007', departmentId: 2, departmentName: '产品中心', positionName: '产品经理', employmentType: 'formal', otherRoleNames: [], finalDataScope: null, lockedReason: null }],
      page: 1,
      pageSize: 20,
      total: 1
    };
    const wrapper = mount(MemberSelectionDrawer, { props: { open: true, page, selectedIds: [], saving: false } });
    expect(wrapper.get('[data-testid="candidate-keyword"]').attributes('aria-label')).toBe('搜索可添加成员');
    expect(wrapper.get('[data-testid="candidate-department"]').attributes('aria-label')).toBe('筛选候选成员部门');
    expect(wrapper.get('[data-testid="candidate-select-7"]').attributes('aria-label')).toBe('选择成员 陈雯');
    await wrapper.get('[data-testid="candidate-select-7"]').setValue(true);
    await wrapper.get('[data-testid="member-selection-submit"]').trigger('click');
    expect(wrapper.emitted('submit')).toEqual([[[7]]]);
  });

  it('disables a defensively supplied locked candidate', async () => {
    const page: RoleMemberPage = {
      records: [{ employeeId: 1, employeeNo: 'E001', employeeName: '超级管理员', mobile: '13800000001', departmentId: 1, departmentName: '总部', positionName: '管理员', employmentType: 'formal', otherRoleNames: ['超级管理员'], finalDataScope: 'company', lockedReason: '超级管理员不可移除' }],
      page: 1,
      pageSize: 20,
      total: 1
    };
    const wrapper = mount(MemberSelectionDrawer, {
      props: { open: true, page, selectedIds: [], saving: false }
    });

    expect(wrapper.get('[data-testid="candidate-select-1"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="candidate-select-1"]').setValue(true);
    expect(wrapper.emitted('update:selectedIds')).toBeUndefined();
  });
});
it('makes organization sync independently administrable', async () => {
 const wrapper = mount(PermissionMatrix, {props: {modules: PERMISSION_MODULES, modelValue: ['organization:view'], readonly: false}});
 await wrapper.get('input[data-testid="permission-organization-sync"]').setValue(true);
 expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual(['organization:sync','organization:view']);
});
