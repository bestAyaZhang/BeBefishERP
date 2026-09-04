import { PERMISSION_MODULES } from './permissionCatalog';
import { widestDataScope } from './permissionRules';
import type { OrganizationService } from '../organization/organizationService';
import type { Department, Employee, Position } from '../organization/types';
import { collectDepartmentSubtreeIds } from '../organization/organizationTree';
import type {
  CreateRolePayload,
  MemberMutationResult,
  MemberRemovalResult,
  PermissionRole,
  PermissionRoleStatus,
  PermissionRoleSummary,
  RoleMember,
  RoleMemberPage,
  RoleMemberQuery,
  SaveRoleConfigurationPayload,
  UpdateRolePayload
} from './types';
import type { PermissionService } from './permissionService';

const ALL_PERMISSION_CODES = PERMISSION_MODULES.flatMap((module) =>
  module.supportedActions.map((action) => `${module.key}:${action}`)
);

const SEED_ROLES: Array<Omit<PermissionRole, 'memberIds'> & { memberIds: number[] }> = [
  {
    id: 1,
    code: 'SUPER_ADMIN',
    name: '超级管理员',
    description: '拥有系统全部权限',
    kind: 'system',
    immutable: true,
    status: 'enabled',
    dataScope: 'company',
    permissionCodes: ALL_PERMISSION_CODES,
    memberIds: [1],
    updatedBy: '系统',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 2,
    code: 'ORG_ADMIN',
    name: '组织管理员',
    description: '维护组织架构和员工信息',
    kind: 'system',
    immutable: true,
    status: 'enabled',
    dataScope: 'company',
    permissionCodes: [
      'dashboard:view',
      ...PERMISSION_MODULES.find((module) => module.key === 'organization')!.supportedActions
        .map((action) => `organization:${action}`)
    ],
    memberIds: [2],
    updatedBy: '系统',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 3,
    code: 'FINANCE_ADMIN',
    name: '财务管理员',
    description: '管理财务相关业务',
    kind: 'system',
    immutable: true,
    status: 'enabled',
    dataScope: 'company',
    permissionCodes: ['dashboard:view', ...moduleCodes('finance')],
    memberIds: [7],
    updatedBy: '系统',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 4,
    code: 'PRODUCT_OPERATOR',
    name: '商品运营',
    description: '维护商品和分类资料',
    kind: 'custom',
    immutable: false,
    status: 'enabled',
    dataScope: 'department-and-descendants',
    permissionCodes: ['dashboard:view', ...moduleCodes('product'), ...moduleCodes('category')],
    memberIds: [1, 3, 4, 5, 6, 19],
    updatedBy: '陈立',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 5,
    code: 'WAREHOUSE_MANAGER',
    name: '仓库管理员',
    description: '管理仓库和库存业务',
    kind: 'custom',
    immutable: false,
    status: 'enabled',
    dataScope: 'department-and-descendants',
    permissionCodes: ['dashboard:view', ...moduleCodes('warehouse'), ...moduleCodes('inventory')],
    memberIds: [10, 12],
    updatedBy: '陈立',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 6,
    code: 'PURCHASE_SPECIALIST',
    name: '采购专员',
    description: '管理供应商和采购业务',
    kind: 'custom',
    immutable: false,
    status: 'enabled',
    dataScope: 'department',
    permissionCodes: ['dashboard:view', ...moduleCodes('supplier')],
    memberIds: [11],
    updatedBy: '陈立',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 7,
    code: 'SALES_SPECIALIST',
    name: '销售专员',
    description: '管理客户和销售业务',
    kind: 'custom',
    immutable: false,
    status: 'enabled',
    dataScope: 'department-and-descendants',
    permissionCodes: ['dashboard:view', ...moduleCodes('customer'), ...moduleCodes('sales')],
    memberIds: [8, 9],
    updatedBy: '陈立',
    updatedAt: '2026-09-04 09:00:00'
  },
  {
    id: 8,
    code: 'TEMP_VISITOR',
    name: '临时访客',
    description: '仅可查看工作台',
    kind: 'custom',
    immutable: false,
    status: 'enabled',
    dataScope: 'self',
    permissionCodes: ['dashboard:view'],
    memberIds: [13],
    updatedBy: '陈立',
    updatedAt: '2026-09-04 09:00:00'
  }
];

function moduleCodes(key: string): string[] {
  const module = PERMISSION_MODULES.find((item) => item.key === key);
  return module?.supportedActions.map((action) => `${key}:${action}`) ?? [];
}

function cloneRole(role: PermissionRole): PermissionRole {
  return { ...role, permissionCodes: [...role.permissionCodes], memberIds: [...role.memberIds] };
}

function cloneEmployee(employee: Employee): Employee {
  return { ...employee };
}

function normalizeKeyword(keyword?: string): string {
  return keyword?.trim().toLowerCase() ?? '';
}

function page(records: RoleMember[], query: RoleMemberQuery): RoleMemberPage {
  const pageSize = Math.max(1, Math.floor(query.size) || 1);
  const maxPage = Math.max(1, Math.ceil(records.length / pageSize));
  const requestedPage = Math.floor(query.page) || 1;
  const currentPage = Math.min(maxPage, Math.max(1, requestedPage));
  return {
    records: records.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    page: currentPage,
    pageSize,
    total: records.length
  };
}

export function createMockPermissionService(organization: OrganizationService): PermissionService {
  let roles = SEED_ROLES.map((role) => cloneRole(role));
  let employees: Employee[] = [];
  let departments: Department[] = [];
  let positions: Position[] = [];
  let nextRoleId = Math.max(...roles.map((role) => role.id)) + 1;
  let initialization: Promise<void> | null = null;

  const ensureInitialized = async () => {
    if (!initialization) {
      initialization = Promise.all([
        organization.listAllEmployees(),
        organization.listAllDepartments(),
        organization.listAllPositions()
      ]).then(([loadedEmployees, loadedDepartments, loadedPositions]) => {
        employees = loadedEmployees.map(cloneEmployee);
        departments = loadedDepartments.map((department) => ({ ...department }));
        positions = loadedPositions.map((position) => ({ ...position }));
        const employeeIds = new Set(employees.map((employee) => employee.id));
        roles = roles.map((role) => ({
          ...role,
          memberIds: role.memberIds.filter((employeeId) => employeeIds.has(employeeId))
        }));
      });
    }
    await initialization;
  };

  const findRole = (id: number) => {
    const role = roles.find((item) => item.id === id);
    if (!role) throw new Error('角色不存在');
    return role;
  };

  const assertMutable = (role: PermissionRole) => {
    if (role.immutable) throw new Error('系统角色不可编辑');
  };

  const employeeHasSuperAdmin = (employeeId: number) =>
    roles.find((role) => role.code === 'SUPER_ADMIN')?.memberIds.includes(employeeId) ?? false;

  const materializeMember = (role: PermissionRole, employee: Employee, includeCurrentRole = true): RoleMember => {
    const department = departments.find((item) => item.id === employee.departmentId);
    const position = positions.find((item) => item.id === employee.positionId);
    const otherRoles = roles.filter((candidate) => candidate.id !== role.id
      && candidate.status === 'enabled' && candidate.memberIds.includes(employee.id));
    const scopes = otherRoles.map((candidate) => candidate.dataScope);
    if (includeCurrentRole && role.status === 'enabled') scopes.push(role.dataScope);
    return {
      employeeId: employee.id,
      employeeNo: employee.employeeNo,
      employeeName: employee.employeeName,
      mobile: employee.mobile,
      departmentId: employee.departmentId,
      departmentName: department?.departmentName ?? '',
      positionName: position?.positionName ?? '',
      employmentType: employee.employmentType,
      otherRoleNames: otherRoles.map((candidate) => candidate.name),
      finalDataScope: widestDataScope(scopes),
      lockedReason: employeeHasSuperAdmin(employee.id) ? '超级管理员不可移除' : null
    };
  };

  const filterMembers = (role: PermissionRole, query: RoleMemberQuery, candidates: boolean) => {
    const memberIds = new Set(role.memberIds);
    const keyword = normalizeKeyword(query.keyword);
    const departmentIds = query.departmentId == null
      ? null
      : collectDepartmentSubtreeIds(departments, query.departmentId);
    return employees
      .filter((employee) => (candidates ? !memberIds.has(employee.id) : memberIds.has(employee.id)))
      .filter((employee) => !candidates || employee.status === 'active')
      .map((employee) => materializeMember(role, employee, !candidates))
      .filter((member) => departmentIds === null || departmentIds.has(member.departmentId))
      .filter((member) => !keyword || [member.employeeNo, member.employeeName, member.mobile, member.departmentName]
        .some((value) => value.toLowerCase().includes(keyword)));
  };

  return {
    async listRoles(keyword?: string) {
      await ensureInitialized();
      const normalized = normalizeKeyword(keyword);
      return roles
        .filter((role) => !normalized || [role.code, role.name].some((value) => value.toLowerCase().includes(normalized)))
        .map((role): PermissionRoleSummary => ({
          id: role.id,
          code: role.code,
          name: role.name,
          kind: role.kind,
          immutable: role.immutable,
          status: role.status,
          updatedBy: role.updatedBy,
          updatedAt: role.updatedAt,
          memberCount: role.memberIds.length
        }));
    },

    async getRole(id: number) {
      await ensureInitialized();
      return cloneRole(findRole(id));
    },

    async createRole(payload: CreateRolePayload) {
      await ensureInitialized();
      const name = payload.name.trim();
      if (!name) throw new Error('角色名称不能为空');
      const code = payload.code.trim().toUpperCase();
      if (!/^[A-Z0-9_]+$/.test(code)) throw new Error('角色编码只能包含大写字母、数字和下划线');
      if (roles.some((role) => role.code === code)) throw new Error('角色编码已存在');
      const source = payload.copyFromRoleId === null ? null : findRole(payload.copyFromRoleId);
      const role: PermissionRole = {
        id: nextRoleId++,
        code,
        name,
        description: payload.description.trim(),
        kind: 'custom',
        immutable: false,
        status: 'enabled',
        dataScope: source?.dataScope ?? 'self',
        permissionCodes: source ? [...source.permissionCodes] : [],
        memberIds: [],
        updatedBy: '当前用户',
        updatedAt: '2026-09-04 09:00:00'
      };
      roles = [...roles, role];
      return cloneRole(role);
    },

    async updateRole(id: number, payload: UpdateRolePayload) {
      await ensureInitialized();
      const role = findRole(id);
      assertMutable(role);
      const name = payload.name.trim();
      if (!name) throw new Error('角色名称不能为空');
      const updated = { ...role, name, description: payload.description.trim(), updatedBy: '当前用户' };
      roles = roles.map((item) => item.id === id ? updated : item);
      return cloneRole(updated);
    },

    async saveConfiguration(id: number, payload: SaveRoleConfigurationPayload) {
      await ensureInitialized();
      const role = findRole(id);
      assertMutable(role);
      const updated = {
        ...role,
        permissionCodes: [...new Set(payload.permissionCodes)],
        dataScope: payload.dataScope,
        updatedBy: '当前用户'
      };
      roles = roles.map((item) => item.id === id ? updated : item);
      return cloneRole(updated);
    },

    async changeRoleStatus(id: number, status: PermissionRoleStatus) {
      await ensureInitialized();
      const role = findRole(id);
      if (role.code === 'SUPER_ADMIN' && status === 'disabled') throw new Error('超级管理员不可停用');
      if (role.immutable) throw new Error('系统角色不可停用');
      const updated = { ...role, status, updatedBy: '当前用户' };
      roles = roles.map((item) => item.id === id ? updated : item);
      return cloneRole(updated);
    },

    async listMembers(roleId: number, query: RoleMemberQuery) {
      await ensureInitialized();
      return page(filterMembers(findRole(roleId), query, false), query);
    },

    async listCandidates(roleId: number, query: RoleMemberQuery) {
      await ensureInitialized();
      return page(filterMembers(findRole(roleId), query, true), query);
    },

    async addMembers(roleId: number, employeeIds: number[]): Promise<MemberMutationResult> {
      await ensureInitialized();
      const role = findRole(roleId);
      assertMutable(role);
      const knownEmployeeIds = new Set(employees.map((employee) => employee.id));
      const members = new Set(role.memberIds);
      let added = 0;
      let skipped = 0;
      for (const employeeId of employeeIds) {
        if (!knownEmployeeIds.has(employeeId) || members.has(employeeId) || employeeHasSuperAdmin(employeeId)) {
          skipped += 1;
          continue;
        }
        members.add(employeeId);
        added += 1;
      }
      const updated = { ...role, memberIds: [...members], updatedBy: '当前用户' };
      roles = roles.map((item) => item.id === roleId ? updated : item);
      return { added, skipped, role: cloneRole(updated) };
    },

    async removeMembers(roleId: number, employeeIds: number[]): Promise<MemberRemovalResult> {
      await ensureInitialized();
      const role = findRole(roleId);
      assertMutable(role);
      const members = new Set(role.memberIds);
      let removed = 0;
      let skippedLocked = 0;
      for (const employeeId of employeeIds) {
        if (!members.has(employeeId)) continue;
        if (employeeHasSuperAdmin(employeeId)) {
          skippedLocked += 1;
          continue;
        }
        members.delete(employeeId);
        removed += 1;
      }
      const updated = { ...role, memberIds: [...members], updatedBy: '当前用户' };
      roles = roles.map((item) => item.id === roleId ? updated : item);
      return { removed, skippedLocked, role: cloneRole(updated) };
    }
  };
}
