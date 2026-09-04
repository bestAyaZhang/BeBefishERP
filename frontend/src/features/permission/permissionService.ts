import { organizationService, type OrganizationService } from '../organization/organizationService';
import { createMockPermissionService } from './mockPermissionService';
import type {
  CreateRolePayload,
  MemberMutationResult,
  MemberRemovalResult,
  PermissionRole,
  PermissionRoleStatus,
  PermissionRoleSummary,
  RoleMemberPage,
  RoleMemberQuery,
  SaveRoleConfigurationPayload,
  UpdateRolePayload
} from './types';

export interface PermissionService {
  listRoles(keyword?: string): Promise<PermissionRoleSummary[]>;
  getRole(id: number): Promise<PermissionRole>;
  createRole(payload: CreateRolePayload): Promise<PermissionRole>;
  updateRole(id: number, payload: UpdateRolePayload): Promise<PermissionRole>;
  saveConfiguration(id: number, payload: SaveRoleConfigurationPayload): Promise<PermissionRole>;
  changeRoleStatus(id: number, status: PermissionRoleStatus): Promise<PermissionRole>;
  listMembers(roleId: number, query: RoleMemberQuery): Promise<RoleMemberPage>;
  listCandidates(roleId: number, query: RoleMemberQuery): Promise<RoleMemberPage>;
  addMembers(roleId: number, employeeIds: number[]): Promise<MemberMutationResult>;
  removeMembers(roleId: number, employeeIds: number[]): Promise<MemberRemovalResult>;
}

export const permissionService: PermissionService = createMockPermissionService(organizationService);

export type { OrganizationService };
