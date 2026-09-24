export type PermissionAction = 'view' | 'create' | 'edit' | 'delete' | 'approve' | 'export' | 'sync' | 'order';
export type PermissionDataScope = 'company' | 'department-and-descendants' | 'department' | 'self';
export type PermissionRoleStatus = 'enabled' | 'disabled';
export type PermissionRoleKind = 'system' | 'custom';

export interface PermissionModule {
  key: string;
  label: string;
  description: string;
  supportedActions: PermissionAction[];
}

export interface PermissionRole {
  id: number;
  code: string;
  name: string;
  description: string;
  kind: PermissionRoleKind;
  immutable: boolean;
  sensitive?: boolean;
  status: PermissionRoleStatus;
  dataScope: PermissionDataScope;
  permissionCodes: string[];
  memberIds: number[];
  updatedBy: string;
  updatedAt: string;
}

export type PermissionRoleSummary = Pick<
  PermissionRole,
  'id' | 'code' | 'name' | 'kind' | 'immutable' | 'status' | 'updatedBy' | 'updatedAt'
> & { memberCount: number };

export interface RoleMember {
  employeeId: number;
  employeeNo: string;
  employeeName: string;
  mobile: string;
  departmentId: number | null;
  departmentName: string;
  positionName: string;
  employmentType: 'formal' | 'temporary';
  otherRoleNames: string[];
  finalDataScope: PermissionDataScope | null;
  lockedReason: string | null;
}

export interface RoleMemberQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: number | null;
}

export interface RoleMemberPage {
  records: RoleMember[];
  page: number;
  pageSize: number;
  total: number;
}

/** Dependencies the permission page needs from the organization boundary. */
export interface PermissionPageContext {
  departments: Array<{ id: number; name: string }>;
  organizationSummary: string;
}

export interface CreateRolePayload {
  name: string;
  code: string;
  description: string;
  copyFromRoleId: number | null;
}

export interface UpdateRolePayload {
  name: string;
  description: string;
}

export interface SaveRoleConfigurationPayload {
  permissionCodes: string[];
  dataScope: PermissionDataScope;
}

export interface MemberMutationResult {
  added: number;
  skipped: number;
  role: PermissionRole;
}

export interface MemberRemovalResult {
  removed: number;
  skippedLocked: number;
  role: PermissionRole;
}

export interface FeishuRoleOption {
  id: string;
  name: string;
}

export interface FeishuRoleMapping {
  feishuRoleId: string;
  feishuRoleName: string;
  erpRoleId: number;
  erpRoleName: string;
  enabled: boolean;
  memberCount: number;
  lastSyncedAt: string | null;
  lastError: string | null;
}

export interface FeishuRoleMappingCandidate {
  id: number;
  code: string;
  name: string;
  /** Defensive client-side guard; the server also excludes sensitive roles. */
  sensitive?: boolean;
}

export interface SaveFeishuRoleMappingPayload {
  feishuRoleName: string;
  erpRoleId: number;
  enabled: boolean;
}
