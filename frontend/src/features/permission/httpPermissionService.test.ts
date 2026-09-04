import { beforeEach, describe, expect, it, vi } from 'vitest';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../services/http', () => ({ request }));

import { httpPermissionService } from './httpPermissionService';

describe('httpPermissionService', () => {
  beforeEach(() => request.mockReset());

  it('maps the existing permission service contract to REST endpoints', async () => {
    request.mockResolvedValue(undefined);

    await httpPermissionService.getPermissionContext();
    await httpPermissionService.listRoles('运营');
    await httpPermissionService.getRole(4);
    await httpPermissionService.createRole({ name: '渠道', code: 'CHANNEL', description: '', copyFromRoleId: null });
    await httpPermissionService.updateRole(4, { name: '商品', description: '商品权限' });
    await httpPermissionService.saveConfiguration(4, { permissionCodes: ['product:view'], dataScope: 'department' });
    await httpPermissionService.changeRoleStatus(4, 'disabled');
    await httpPermissionService.listMembers(4, { page: 2, size: 10, keyword: '张', departmentId: 3 });
    await httpPermissionService.listCandidates(4, { page: 1, size: 20 });
    await httpPermissionService.addMembers(4, [2, 3]);
    await httpPermissionService.removeMembers(4, [3]);

    expect(request.mock.calls).toEqual([
      ['/api/permissions/context'],
      ['/api/permissions/roles?keyword=%E8%BF%90%E8%90%A5'],
      ['/api/permissions/roles/4'],
      ['/api/permissions/roles', { method: 'POST', body: JSON.stringify({ name: '渠道', code: 'CHANNEL', description: '', copyFromRoleId: null }) }],
      ['/api/permissions/roles/4', { method: 'PUT', body: JSON.stringify({ name: '商品', description: '商品权限' }) }],
      ['/api/permissions/roles/4/configuration', { method: 'PUT', body: JSON.stringify({ permissionCodes: ['product:view'], dataScope: 'department' }) }],
      ['/api/permissions/roles/4/status', { method: 'PATCH', body: JSON.stringify({ status: 'disabled' }) }],
      ['/api/permissions/roles/4/members?page=2&size=10&keyword=%E5%BC%A0&departmentId=3'],
      ['/api/permissions/roles/4/candidates?page=1&size=20'],
      ['/api/permissions/roles/4/members', { method: 'POST', body: JSON.stringify({ employeeIds: [2, 3] }) }],
      ['/api/permissions/roles/4/members', { method: 'DELETE', body: JSON.stringify({ employeeIds: [3] }) }]
    ]);
  });

  it('maps Feishu role mapping operations to REST endpoints', async () => {
    request.mockResolvedValue(undefined);
    const payload = { feishuRoleName: '飞书仓库主管', erpRoleId: 5, enabled: true };

    await httpPermissionService.listFeishuRoles();
    await httpPermissionService.listFeishuRoleMappings();
    await httpPermissionService.listFeishuRoleMappingCandidates();
    await httpPermissionService.saveFeishuRoleMapping('fs role/1', payload);
    await httpPermissionService.deleteFeishuRoleMapping('fs role/1');
    await httpPermissionService.syncFeishuRoleMappings();

    expect(request.mock.calls).toEqual([
      ['/api/permissions/feishu-roles'],
      ['/api/permissions/feishu-role-mappings'],
      ['/api/permissions/feishu-role-mappings/candidate-roles'],
      ['/api/permissions/feishu-role-mappings/fs%20role%2F1', { method: 'PUT', body: JSON.stringify(payload) }],
      ['/api/permissions/feishu-role-mappings/fs%20role%2F1', { method: 'DELETE' }],
      ['/api/permissions/feishu-role-mappings/sync', { method: 'POST' }]
    ]);
  });
});
