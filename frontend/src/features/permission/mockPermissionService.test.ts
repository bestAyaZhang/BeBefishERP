import { describe, expect, it } from 'vitest';
import { createMockOrganizationService } from '../organization/mockOrganizationService';
const organizationService = createMockOrganizationService();
import { createMockPermissionService } from './mockPermissionService';
import { PERMISSION_MODULES } from './permissionCatalog';

describe('mockPermissionService', () => {
  it('creates an uppercase unique custom role and copies configuration without members', async () => {
    const service = createMockPermissionService(organizationService);
    const source = await service.getRole(4);
    const created = await service.createRole({
      name: '渠道运营', code: 'channel_operator', description: '渠道数据维护', copyFromRoleId: 4
    });

    expect(created.code).toBe('CHANNEL_OPERATOR');
    expect(created.permissionCodes).toEqual(source.permissionCodes);
    expect(created.dataScope).toBe(source.dataScope);
    expect(created.memberIds).toEqual([]);
    await expect(service.createRole({
      name: '重复', code: 'CHANNEL_OPERATOR', description: '', copyFromRoleId: null
    })).rejects.toThrow('角色编码已存在');
  });

  it('protects system roles and rejects disabling the super administrator', async () => {
    const service = createMockPermissionService(organizationService);
    await expect(service.saveConfiguration(1, { permissionCodes: [], dataScope: 'self' }))
      .rejects.toThrow('系统角色不可编辑');
    await expect(service.changeRoleStatus(1, 'disabled'))
      .rejects.toThrow('超级管理员不可停用');
  });

  it('skips duplicate and locked members, computes widest scope and reconciles pagination', async () => {
    const service = createMockPermissionService(organizationService);
    const result = await service.addMembers(4, [1, 2, 2]);
    expect(result).toMatchObject({ added: 1, skipped: 2 });

    const members = await service.listMembers(4, { page: 9, size: 2, keyword: '', departmentId: null });
    expect(members.page).toBe(Math.max(1, Math.ceil(members.total / 2)));
    expect(members.records.every((item) => item.finalDataScope)).toBe(true);

    const removal = await service.removeMembers(4, [1, 2]);
    expect(removal.skippedLocked).toBeGreaterThanOrEqual(1);
  });

  it('removes a disabled role from each member effective data scope', async () => {
    const service = createMockPermissionService(organizationService);
    await service.changeRoleStatus(4, 'disabled');
    const page = await service.listMembers(4, { page: 1, size: 20, keyword: '', departmentId: null });
    const productOnlyMember = page.records.find((member) => member.otherRoleNames.length === 0)!;
    expect(productOnlyMember.finalDataScope).toBeNull();
  });

  it('excludes locked employees from candidates for a new custom role', async () => {
    const service = createMockPermissionService(organizationService);
    const role = await service.createRole({
      name: '新角色', code: 'NEW_ROLE', description: '', copyFromRoleId: null
    });

    const candidates = await service.listCandidates(role.id, { page: 1, size: 100 });

    expect(candidates.records.map((member) => member.employeeId)).not.toContain(1);
    expect(candidates.records.every((member) => member.lockedReason === null)).toBe(true);
  });

  it('rejects invalid runtime configuration and status values without mutating the role', async () => {
    const service = createMockPermissionService(organizationService);
    const before = await service.getRole(4);

    await expect(service.saveConfiguration(4, {
      permissionCodes: ['product:view', 'unknown:grant'],
      dataScope: 'department'
    })).rejects.toThrow('权限编码无效');
    await expect(service.saveConfiguration(4, {
      permissionCodes: ['product:view'],
      dataScope: 'region-wide'
    } as never)).rejects.toThrow('数据范围无效');
    await expect(service.changeRoleStatus(4, 'archived' as never)).rejects.toThrow('角色状态无效');

    await expect(service.getRole(4)).resolves.toEqual(before);
  });

  it('rejects every non-view action without its module view and permits complete module removal', async () => {
    const service = createMockPermissionService(organizationService);
    const nonViewPermissions = PERMISSION_MODULES.flatMap((module) =>
      module.supportedActions
        .filter((action) => action !== 'view')
        .map((action) => `${module.key}:${action}`)
    );

    for (const permissionCode of nonViewPermissions) {
      await expect(service.saveConfiguration(4, {
        permissionCodes: [permissionCode],
        dataScope: 'department'
      })).rejects.toThrow('非查看权限必须同时包含查看权限');
    }

    const before = await service.getRole(4);
    const withoutProduct = before.permissionCodes.filter((code) => !code.startsWith('product:'));
    const saved = await service.saveConfiguration(4, {
      permissionCodes: withoutProduct,
      dataScope: before.dataScope
    });
    expect(saved.permissionCodes.filter((code) => code.startsWith('product:'))).toEqual([]);
  });

  it('exposes page context and treats disabled custom roles as copy-only until re-enabled', async () => {
    const service = createMockPermissionService(organizationService);
    await expect(service.getPermissionContext()).resolves.toMatchObject({
      departments: expect.arrayContaining([expect.objectContaining({ id: 2, name: '产品中心' })])
    });
    await service.changeRoleStatus(4, 'disabled');
    await expect(service.updateRole(4, { name: '不可编辑', description: '' })).rejects.toThrow('已停用角色不可编辑');
    await expect(service.saveConfiguration(4, { permissionCodes: ['product:view'], dataScope: 'self' })).rejects.toThrow('已停用角色不可编辑');
    await expect(service.addMembers(4, [2])).rejects.toThrow('已停用角色不可编辑');
    await expect(service.removeMembers(4, [3])).rejects.toThrow('已停用角色不可编辑');
    await expect(service.createRole({ name: '副本', code: 'COPY_DISABLED', description: '', copyFromRoleId: 4 })).resolves.toMatchObject({ code: 'COPY_DISABLED' });
    await expect(service.changeRoleStatus(4, 'enabled')).resolves.toMatchObject({ status: 'enabled' });
  });
});
