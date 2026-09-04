export type PermissionAction = 'view' | 'create' | 'edit' | 'delete' | 'approve' | 'export';
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
  departmentId: number;
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
