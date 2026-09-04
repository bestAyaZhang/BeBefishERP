import { request } from '../../services/http';
import type { PermissionService } from './permissionService';
import type { RoleMemberQuery } from './types';

function queryString(query: RoleMemberQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.departmentId != null) params.set('departmentId', String(query.departmentId));
  return params.toString();
}

function json(method: string, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) };
}

export const httpPermissionService: PermissionService = {
  getPermissionContext: () => request('/api/permissions/context'),
  listRoles: (keyword) => request(`/api/permissions/roles${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`),
  getRole: (id) => request(`/api/permissions/roles/${id}`),
  createRole: (payload) => request('/api/permissions/roles', json('POST', payload)),
  updateRole: (id, payload) => request(`/api/permissions/roles/${id}`, json('PUT', payload)),
  saveConfiguration: (id, payload) => request(`/api/permissions/roles/${id}/configuration`, json('PUT', payload)),
  changeRoleStatus: (id, status) => request(`/api/permissions/roles/${id}/status`, json('PATCH', { status })),
  listMembers: (id, query) => request(`/api/permissions/roles/${id}/members?${queryString(query)}`),
  listCandidates: (id, query) => request(`/api/permissions/roles/${id}/candidates?${queryString(query)}`),
  addMembers: (id, employeeIds) => request(`/api/permissions/roles/${id}/members`, json('POST', { employeeIds })),
  removeMembers: (id, employeeIds) => request(`/api/permissions/roles/${id}/members`, json('DELETE', { employeeIds })),
  listFeishuRoles: () => request('/api/permissions/feishu-roles'),
  listFeishuRoleMappings: () => request('/api/permissions/feishu-role-mappings'),
  listFeishuRoleMappingCandidates: () => request('/api/permissions/feishu-role-mappings/candidate-roles'),
  saveFeishuRoleMapping: (id, payload) => request(`/api/permissions/feishu-role-mappings/${encodeURIComponent(id)}`, json('PUT', payload)),
  deleteFeishuRoleMapping: (id) => request(`/api/permissions/feishu-role-mappings/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  syncFeishuRoleMappings: () => request('/api/permissions/feishu-role-mappings/sync', { method: 'POST' })
};
