import { organizationService, type OrganizationService } from '../organization/organizationService';
import { createService } from '../../services/serviceFactory';
import { httpPermissionService } from './httpPermissionService';
import { createMockPermissionService } from './mockPermissionService';
import type {
  CreateRolePayload,
  FeishuRoleMapping,
  FeishuRoleMappingCandidate,
  FeishuRoleOption,
  MemberMutationResult,
  MemberRemovalResult,
  PermissionRole,
  PermissionRoleStatus,
  PermissionRoleSummary,
  PermissionPageContext,
  RoleMemberPage,
  RoleMemberQuery,
  SaveRoleConfigurationPayload,
  SaveFeishuRoleMappingPayload,
  UpdateRolePayload
} from './types';

export interface PermissionService {
  getPermissionContext(): Promise<PermissionPageContext>;
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
  listFeishuRoles(): Promise<FeishuRoleOption[]>;
  listFeishuRoleMappings(): Promise<FeishuRoleMapping[]>;
  listFeishuRoleMappingCandidates(): Promise<FeishuRoleMappingCandidate[]>;
  saveFeishuRoleMapping(feishuRoleId: string, payload: SaveFeishuRoleMappingPayload): Promise<FeishuRoleMapping>;
  deleteFeishuRoleMapping(feishuRoleId: string): Promise<void>;
  syncFeishuRoleMappings(): Promise<FeishuRoleMapping[]>;
}

export const permissionService: PermissionService = createService(
  () => createMockPermissionService(organizationService),
  () => httpPermissionService
);

export type { OrganizationService };
