import { request } from '../../services/http';
import type { OrganizationService } from './organizationService';

function queryString(query: object) {
  return new URLSearchParams(Object.entries(query).filter(([, value]) => value !== undefined && value !== '').map(([key, value]) => [key, String(value)])).toString();
}
function json(method: string, body: unknown): RequestInit { return { method, body: JSON.stringify(body) }; }

export const httpOrganizationService: OrganizationService = {
  listDepartmentPage: (query) => request(`/api/organization/departments?${queryString(query)}`),
  listAllDepartments: () => request('/api/organization/departments/all'),
  createDepartment: (payload) => request('/api/organization/departments', json('POST', payload)),
  updateDepartment: (id, payload) => request(`/api/organization/departments/${id}`, json('PUT', payload)),
  changeDepartmentStatus: (id, status) => request(`/api/organization/departments/${id}/status`, json('PATCH', { status })),
  listPositions: (query) => request(`/api/organization/positions?${queryString(query)}`),
  listAllPositions: () => request('/api/organization/positions/all'),
  createPosition: (payload) => request('/api/organization/positions', json('POST', payload)),
  updatePosition: (id, payload) => request(`/api/organization/positions/${id}`, json('PUT', payload)),
  changePositionStatus: (id, status) => request(`/api/organization/positions/${id}/status`, json('PATCH', { status })),
  listEmployees: (query) => request(`/api/organization/employees?${queryString(query)}`),
  listAllEmployees: () => request('/api/organization/employees/all'),
  createEmployee: (payload) => request('/api/organization/employees', json('POST', payload)),
  updateEmployee: (id, payload) => request(`/api/organization/employees/${id}`, json('PUT', payload)),
  changeEmployeeStatus: (id, status) => request(`/api/organization/employees/${id}/status`, json('PATCH', { status })),
  getDepartmentEmployeeCounts: () => request('/api/organization/departments/employee-counts'),
  getSummary: () => request('/api/organization/employees/summary'),
  getEmployee: (id) => request(`/api/organization/employees/${id}`),
  startFeishuSync: () => request('/api/organization/feishu-syncs', { method: 'POST' }),
  getLatestFeishuSync: () => request('/api/organization/feishu-syncs/latest'),
  getFeishuSync: (id) => request(`/api/organization/feishu-syncs/${id}`)
};
