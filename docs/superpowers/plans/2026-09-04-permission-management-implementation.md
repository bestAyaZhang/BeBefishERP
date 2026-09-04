# BeBefish ERP Permission Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved permission-management Vue experience and a reusable backend RBAC resolver that supplies effective login roles and permissions.

**Architecture:** Add a `features/permission` vertical slice whose Vue page depends on a `PermissionService` and whose first implementation is a mutable in-memory service backed by the existing organization service. Add a backend `authorization` domain that resolves enabled role assignments into permission unions and the widest data scope, then make `AuthService` issue tokens from that resolved authorization instead of credential records containing final claims.

**Tech Stack:** Vue 3, TypeScript 5.7, Vue Router 4, Tailwind CSS 3, Vitest, Vue Test Utils, Java 21, Spring Boot 3.3, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-04-permission-management-implementation-design.md`

## Global Constraints

- Match the approved four-frame Figma prototype in `BeBefish ERP — Master Data UI`, permission section `538:10667`; no Figma mutation is required in this plan.
- Keep the route at `/organization/permissions`; view access requires `system:role:view`, and mutation controls require `system:role:manage`.
- Keep permission-management data behind `PermissionService`; this iteration uses an in-memory implementation and does not add MySQL tables, Flyway migrations, or permission REST endpoints.
- Use RBAC only: permissions are assigned to roles, enabled roles combine by union, and data scope resolves in the order `SELF < DEPARTMENT < DEPARTMENT_AND_DESCENDANTS < COMPANY`.
- `SUPER_ADMIN` is a read-only, non-disableable system role with the complete backend permission catalog.
- Selecting a non-view action selects view; clearing view clears the module's other actions.
- Copying a role copies permissions and data scope but never members; duplicate member additions are skipped and reported.
- Do not edit, stage, revert, or rely on these user-owned files:
  - `frontend/src/features/organization/DepartmentPositionManagement.test.ts`
  - `frontend/src/features/organization/EmployeeManagementView.test.ts`
  - `frontend/src/features/organization/views/DepartmentManagementView.vue`
  - `frontend/src/features/organization/views/EmployeeManagementView.vue`
  - `frontend/src/features/organization/views/PositionManagementView.vue`
- Follow red-green-refactor for every production behavior. Run the named failing test before writing implementation code.
- Do not merge or push automatically.

---

### Task 1: Frontend Permission Domain And Rules

**Files:**
- Create: `frontend/src/features/permission/types.ts`
- Create: `frontend/src/features/permission/permissionCatalog.ts`
- Create: `frontend/src/features/permission/permissionRules.ts`
- Test: `frontend/src/features/permission/permissionRules.test.ts`

**Interfaces:**
- Produces: `PermissionAction`, `PermissionDataScope`, `PermissionModule`, `PermissionRole`, `PermissionRoleSummary`, `RoleMember`, `RoleMemberPage`, `RoleMemberQuery`, `CreateRolePayload`, `UpdateRolePayload`, `SaveRoleConfigurationPayload`, `MemberMutationResult`, and `MemberRemovalResult`.
- Produces: `PERMISSION_MODULES`, `toggleModulePermission()`, `moduleSelectionState()`, and `widestDataScope()`.

- [ ] **Step 1: Write failing rule tests**

```ts
import { describe, expect, it } from 'vitest';
import { PERMISSION_MODULES } from './permissionCatalog';
import { moduleSelectionState, toggleModulePermission, widestDataScope } from './permissionRules';

describe('permissionRules', () => {
  const product = PERMISSION_MODULES.find((item) => item.key === 'product')!;

  it('selects view with a dependent action and removes dependants when view is cleared', () => {
    const withCreate = toggleModulePermission(product, [], 'create', true);
    expect(withCreate).toEqual(expect.arrayContaining(['product:view', 'product:create']));
    expect(toggleModulePermission(product, withCreate, 'view', false)).toEqual([]);
  });

  it('reports checked, mixed and unchecked module states', () => {
    expect(moduleSelectionState(product, [])).toBe('unchecked');
    expect(moduleSelectionState(product, ['product:view'])).toBe('mixed');
    expect(moduleSelectionState(product, [
      'product:view', 'product:create', 'product:edit', 'product:delete', 'product:export'
    ])).toBe('checked');
  });

  it('uses the widest active data scope', () => {
    expect(widestDataScope(['self', 'department', 'department-and-descendants'])).toBe('department-and-descendants');
    expect(widestDataScope([])).toBeNull();
  });
});
```

- [ ] **Step 2: Verify the tests fail because the permission domain does not exist**

Run: `cd frontend && npm run test:run -- src/features/permission/permissionRules.test.ts`

Expected: FAIL with unresolved `permissionCatalog` or `permissionRules` modules.

- [ ] **Step 3: Define immutable UI contracts and the exact catalog**

```ts
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

export type PermissionRoleSummary = Pick<PermissionRole,
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

export interface MemberMutationResult { added: number; skipped: number; role: PermissionRole }
export interface MemberRemovalResult { removed: number; skippedLocked: number; role: PermissionRole }
```

Create `PERMISSION_MODULES` with these exact keys, labels, and action sets from the approved matrix:

- `dashboard/工作台`: `view`.
- `product/商品`, `category/分类`, `customer/客户`, `supplier/供应商`, `warehouse/仓库`: `view,create,edit,delete,export`.
- `inventory/库存`: `view,create,edit,approve,export`.
- `sales/销售`: `view,create,edit,delete,approve,export`.
- `finance/财务`: `view,create,edit,approve,export`.
- `organization/组织架构`: `view,create,edit,delete,export`.

- [ ] **Step 4: Implement the minimal pure rules**

```ts
const SCOPE_RANK: Record<PermissionDataScope, number> = {
  self: 0,
  department: 1,
  'department-and-descendants': 2,
  company: 3
};

export function toggleModulePermission(
  module: PermissionModule,
  selected: string[],
  action: PermissionAction,
  checked: boolean
): string[] {
  if (!module.supportedActions.includes(action)) return [...selected];
  const codes = new Set(selected);
  const code = `${module.key}:${action}`;
  if (checked) {
    codes.add(code);
    if (action !== 'view') codes.add(`${module.key}:view`);
  } else if (action === 'view') {
    module.supportedActions.forEach((item) => codes.delete(`${module.key}:${item}`));
  } else {
    codes.delete(code);
  }
  return [...codes].sort();
}
```

- [ ] **Step 5: Verify rule tests pass and commit**

Run: `cd frontend && npm run test:run -- src/features/permission/permissionRules.test.ts`

Expected: PASS with 3 tests.

```powershell
git add -- frontend/src/features/permission/types.ts frontend/src/features/permission/permissionCatalog.ts frontend/src/features/permission/permissionRules.ts frontend/src/features/permission/permissionRules.test.ts
git commit -m "feat: add permission domain rules"
```

---

### Task 2: Mutable Permission Service

**Files:**
- Create: `frontend/src/features/permission/permissionService.ts`
- Create: `frontend/src/features/permission/mockPermissionService.ts`
- Test: `frontend/src/features/permission/mockPermissionService.test.ts`

**Interfaces:**
- Consumes: organization methods `listAllEmployees()`, `listAllDepartments()`, and `listAllPositions()`.
- Produces: `PermissionService` with `listRoles`, `getRole`, `createRole`, `updateRole`, `saveConfiguration`, `changeRoleStatus`, `listMembers`, `listCandidates`, `addMembers`, and `removeMembers`.

- [ ] **Step 1: Write failing service tests for role rules**

```ts
it('creates an uppercase unique custom role and copies configuration without members', async () => {
  const source = await service.getRole(4);
  const created = await service.createRole({
    name: '渠道运营', code: 'channel_operator', description: '渠道数据维护', copyFromRoleId: 4
  });
  expect(created.code).toBe('CHANNEL_OPERATOR');
  expect(created.permissionCodes).toEqual(source.permissionCodes);
  expect(created.dataScope).toBe(source.dataScope);
  expect(created.memberIds).toEqual([]);
  await expect(service.createRole({ name: '重复', code: 'CHANNEL_OPERATOR', description: '', copyFromRoleId: null }))
    .rejects.toThrow('角色编码已存在');
});

it('protects system roles and rejects disabling the super administrator', async () => {
  await expect(service.saveConfiguration(1, { permissionCodes: [], dataScope: 'self' }))
    .rejects.toThrow('系统角色不可编辑');
  await expect(service.changeRoleStatus(1, 'disabled'))
    .rejects.toThrow('超级管理员不可停用');
});
```

- [ ] **Step 2: Write failing member tests**

```ts
it('skips duplicate and locked members, computes widest scope and reconciles pagination', async () => {
  const result = await service.addMembers(4, [1, 2, 2]);
  expect(result).toMatchObject({ added: 1, skipped: 2 });

  const members = await service.listMembers(4, { page: 9, size: 2, keyword: '', departmentId: null });
  expect(members.page).toBe(Math.max(1, Math.ceil(members.total / 2)));
  expect(members.records.every((item) => item.finalDataScope)).toBe(true);

  const removal = await service.removeMembers(4, [1, 2]);
  expect(removal.skippedLocked).toBeGreaterThanOrEqual(1);
});

it('removes a disabled role from each member effective data scope', async () => {
  await service.changeRoleStatus(4, 'disabled');
  const page = await service.listMembers(4, { page: 1, size: 20, keyword: '', departmentId: null });
  const productOnlyMember = page.records.find((member) => member.otherRoleNames.length === 0)!;
  expect(productOnlyMember.finalDataScope).toBeNull();
});
```

- [ ] **Step 3: Verify the service tests fail**

Run: `cd frontend && npm run test:run -- src/features/permission/mockPermissionService.test.ts`

Expected: FAIL because `createMockPermissionService` and `PermissionService` do not exist.

- [ ] **Step 4: Implement the service contract and seeded roles**

```ts
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
```

Seed roles in the Figma order: `SUPER_ADMIN`, `ORG_ADMIN`, `FINANCE_ADMIN`, `PRODUCT_OPERATOR`, `WAREHOUSE_MANAGER`, `PURCHASE_SPECIALIST`, `SALES_SPECIALIST`, `TEMP_VISITOR`. Seed role member IDs only from employees returned by the injected organization dependency. `SUPER_ADMIN` contains employee `1`; `PRODUCT_OPERATOR` contains employees `1,3,4,5,6,19`, so its initial count is six, employee `1` is both duplicate and locked, and employee `2` is an available candidate. Give employee `3` no other enabled role so disabling `PRODUCT_OPERATOR` produces a visible `null` final scope in its member test. Clone every returned array and object so tests cannot mutate service state without a service call.

Export `permissionService = createMockPermissionService(organizationService)` from `permissionService.ts`. The page accepts a service prop for tests and defaults to this singleton in the application.

Implement role-code normalization with `/^[A-Z0-9_]+$/`, reject empty names, preserve immutable system roles, skip duplicate member IDs, skip employees holding `SUPER_ADMIN` during removal, calculate other enabled roles, and resolve `finalDataScope` with `widestDataScope`. Clamp requested member pages to the last valid page before slicing.

- [ ] **Step 5: Verify service tests and commit**

Run: `cd frontend && npm run test:run -- src/features/permission/mockPermissionService.test.ts src/features/permission/permissionRules.test.ts`

Expected: PASS.

```powershell
git add -- frontend/src/features/permission/permissionService.ts frontend/src/features/permission/mockPermissionService.ts frontend/src/features/permission/mockPermissionService.test.ts
git commit -m "feat: add permission management service"
```

---

### Task 3: Reusable Permission UI Components

**Files:**
- Create: `frontend/src/features/permission/components/RoleListPanel.vue`
- Create: `frontend/src/features/permission/components/PermissionMatrix.vue`
- Create: `frontend/src/features/permission/components/DataScopePanel.vue`
- Create: `frontend/src/features/permission/components/RoleMembersPanel.vue`
- Create: `frontend/src/features/permission/components/RoleFormDrawer.vue`
- Create: `frontend/src/features/permission/components/MemberSelectionDrawer.vue`
- Test: `frontend/src/features/permission/PermissionComponents.test.ts`

**Interfaces:**
- Consumes: Task 1 types and pure rules plus the existing `OrganizationPagination` and `AccessibleDialog` components.
- Produces: controlled presentational components with typed props and emits; no component imports the permission service singleton.

- [ ] **Step 1: Write failing role-list and matrix tests**

```ts
const roles: PermissionRoleSummary[] = [
  { id: 1, code: 'SUPER_ADMIN', name: '超级管理员', kind: 'system', immutable: true, status: 'enabled', memberCount: 2, updatedBy: '系统内置', updatedAt: '' },
  { id: 4, code: 'PRODUCT_OPERATOR', name: '商品运营', kind: 'custom', immutable: false, status: 'enabled', memberCount: 6, updatedBy: '张振亚', updatedAt: '2026-09-03' }
];

it('keeps the selected role context when search filters it out', async () => {
  const wrapper = mount(RoleListPanel, { props: { roles, selectedRoleId: 1, disabled: false } });
  await wrapper.get('[data-testid="role-search"]').setValue('商品');
  expect(wrapper.get('[data-testid="selected-role-filtered-notice"]').text()).toContain('当前角色不在筛选结果中');
  expect(wrapper.emitted('select')).toBeUndefined();
});

it('enforces view dependency and stays read-only for immutable roles', async () => {
  const editable = mount(PermissionMatrix, { props: { modules: PERMISSION_MODULES, modelValue: [], readonly: false } });
  await editable.get('[data-testid="permission-product-create"]').setValue(true);
  expect(editable.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(expect.arrayContaining(['product:view', 'product:create']));

  const readonly = mount(PermissionMatrix, { props: { modules: PERMISSION_MODULES, modelValue: ['product:view'], readonly: true } });
  expect(readonly.get('[data-testid="permission-product-view"]').attributes('disabled')).toBeDefined();
});
```

- [ ] **Step 2: Write failing data, member and drawer tests**

```ts
it('emits the selected data scope and member page changes', async () => {
  const scope = mount(DataScopePanel, { props: { modelValue: 'self', readonly: false, organizationSummary: '产品中心及 3 个下级组织' } });
  await scope.get('[data-testid="scope-department-and-descendants"]').setValue(true);
  expect(scope.emitted('update:modelValue')?.[0]).toEqual(['department-and-descendants']);

  const memberPage: RoleMemberPage = { records: [], page: 1, pageSize: 20, total: 40 };
  const members = mount(RoleMembersPanel, { props: { page: memberPage, canManage: true, selectedIds: [] } });
  await members.get('[data-testid="member-next-page"]').trigger('click');
  expect(members.emitted('page')?.[0]).toEqual([2]);
});

it('normalizes role codes and validates uniqueness before submit', async () => {
  const wrapper = mount(RoleFormDrawer, { props: { open: true, mode: 'create', role: null, copySources: roles, existingCodes: roles.map((role) => role.code), saving: false, error: '' } });
  await wrapper.get('[data-testid="role-name"]').setValue('渠道运营');
  await wrapper.get('[data-testid="role-code"]').setValue('channel_operator');
  await wrapper.get('[data-testid="role-submit"]').trigger('click');
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ name: '渠道运营', code: 'CHANNEL_OPERATOR' });
});
```

- [ ] **Step 3: Verify component tests fail**

Run: `cd frontend && npm run test:run -- src/features/permission/PermissionComponents.test.ts`

Expected: FAIL because the six components do not exist.

- [ ] **Step 4: Implement the approved component contracts**

Use the Figma dimensions and behavior directly:

- `RoleListPanel`: `280px` width, search input, system/custom groups, selected and dirty states, disabled switching while saving.
- `PermissionMatrix`: columns `模块/权限说明/查看/新增/编辑/删除/审核/导出`, horizontal separators only, disabled dash for unsupported actions, group checkbox with `aria-checked="mixed"`.
- `DataScopePanel`: the four exact scope labels and explanations from the approved spec, plus a read-only current-range summary.
- `RoleMembersPanel`: keyword and department filters, text actions, selectable unlocked rows, pinned `OrganizationPagination`, empty-state clear action.
- `RoleFormDrawer`: reuse `AccessibleDialog`, right-aligned `min(720px,100vw)` drawer, uppercase code, uniqueness feedback, copy-source select.
- `MemberSelectionDrawer`: reuse `AccessibleDialog`, organization filters, checkboxes, selected count, and one bulk-add submit event.

Every user action must emit a domain value; components must not mutate prop arrays in place.

- [ ] **Step 5: Verify component tests and commit**

Run: `cd frontend && npm run test:run -- src/features/permission/PermissionComponents.test.ts`

Expected: PASS with no Vue warnings.

```powershell
git add -- frontend/src/features/permission/components frontend/src/features/permission/PermissionComponents.test.ts
git commit -m "feat: add permission management components"
```

---

### Task 4: Permission Page, Route, Navigation And Access Control

**Files:**
- Create: `frontend/src/features/permission/views/PermissionManagementView.vue`
- Test: `frontend/src/features/permission/PermissionManagementView.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/index.test.ts`
- Modify: `frontend/src/features/navigation/mockNavigationService.ts`
- Modify: `frontend/src/components/navigation/SidebarNav.test.ts`
- Modify: `frontend/src/layouts/ErpLayout.vue`
- Modify: `frontend/src/layouts/ErpLayout.test.ts`
- Modify: `frontend/src/AppRouting.test.ts`

**Interfaces:**
- Consumes: `PermissionService`, six controlled components, `currentUser`, and the approved route permission codes.
- Produces: `/organization/permissions`, route name `organization-permissions`, breadcrumb `组织架构 / 权限管理`, and a lazy-loaded production page.

- [ ] **Step 1: Write failing page orchestration tests**

```ts
function mountPermissionView(service: PermissionService, permissions: string[]) {
  saveCurrentUser({
    accessToken: 'token', mobile: '13800138000', roles: ['SUPER_ADMIN'], permissions, loginMethod: 'password'
  });
  return mount(PermissionManagementView, { props: { service } });
}

it('loads super administrator read-only and exposes all three tabs', async () => {
  const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
  await flushPromises();
  expect(wrapper.get('[data-testid="selected-role-name"]').text()).toBe('超级管理员');
  expect(wrapper.get('[data-testid="permission-workspace"]').attributes('data-readonly')).toBe('true');
  expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
});

it('preserves dirty edits on save failure and confirms before role switch', async () => {
  service.saveConfiguration = vi.fn().mockRejectedValue(new Error('保存失败，请重试'));
  const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
  await flushPromises();
  await wrapper.get('[data-testid="role-item-4"]').trigger('click');
  await wrapper.get('[data-testid="permission-product-create"]').setValue(true);
  await wrapper.get('[data-testid="save-role-configuration"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('[data-testid="permission-save-error"]').text()).toContain('保存失败，请重试');
  expect(wrapper.get('[data-testid="save-role-configuration"]').attributes('disabled')).toBeUndefined();
});

it('selects a created role and refreshes its member count after member changes', async () => {
  const wrapper = mountPermissionView(service, ['system:role:view', 'system:role:manage']);
  await flushPromises();
  await wrapper.get('[data-testid="create-role"]').trigger('click');
  await wrapper.get('[data-testid="role-name"]').setValue('渠道运营');
  await wrapper.get('[data-testid="role-code"]').setValue('CHANNEL_OPERATOR');
  await wrapper.get('[data-testid="role-submit"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('[data-testid="selected-role-code"]').text()).toBe('CHANNEL_OPERATOR');
  expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('权限配置');

  await wrapper.get('[data-testid="tab-members"]').trigger('click');
  await wrapper.get('[data-testid="add-member"]').trigger('click');
  await wrapper.get('[data-testid="candidate-member-2"]').setValue(true);
  await wrapper.get('[data-testid="member-selection-submit"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('[data-testid="selected-role-member-count"]').text()).toContain('1');
});
```

- [ ] **Step 2: Write failing route and navigation tests**

```ts
expect(router.resolve('/organization/permissions').name).toBe('organization-permissions');
expect(router.resolve('/organization/permissions').meta.requiredPermission).toBe('system:role:view');

storeCurrentUser(['organization:view', 'system:role:view']);
expect(mount(SidebarNav, { global: { plugins: [router] } }).text()).toContain('权限管理');
```

Add a router test that stores an authenticated current user without `system:role:view`, navigates to `/organization/permissions`, and expects a redirect to `workbench`. Add the positive case with the permission present.

- [ ] **Step 3: Verify page, router and navigation tests fail**

Run: `cd frontend && npm run test:run -- src/features/permission/PermissionManagementView.test.ts src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts src/AppRouting.test.ts`

Expected: FAIL because the page and route are absent.

- [ ] **Step 4: Implement the page state machine**

On mount, load roles and select `SUPER_ADMIN`. Keep a saved role snapshot and editable draft. Derive `dirty` from the snapshot and draft, and derive `canManage` from `system:role:manage` plus role mutability. Disable role switching and tabs while saving. On failed save, retain the draft and show the exact service message. On successful create, refresh roles, select the created role, and activate `permissions`.

Use one `confirmDiscardChanges()` function for role changes, tab changes that discard local edits, and drawer cancellation. Use `window.confirm('当前角色存在未保存修改，是否放弃？')` until the project has a shared confirmation dialog component.

Wire every approved mutation through the service: edit updates role name/description, copy opens the create drawer with `copyFromRoleId`, deactivate confirms the affected member count before calling `changeRoleStatus`, add members reports added/skipped counts, and remove/batch-remove excludes rows with `lockedReason`. Refresh the selected role summary and current member page after every successful mutation.

- [ ] **Step 5: Register route, permission guard, navigation and breadcrumb**

```ts
{
  path: 'organization/permissions',
  name: 'organization-permissions',
  component: () => import('../features/permission/views/PermissionManagementView.vue'),
  meta: { requiredPermission: 'system:role:view' }
}
```

In `router.beforeEach`, call `restoreCurrentUser()` only when `to.meta.requiredPermission` is a string; redirect authenticated users missing it to `{ name: 'workbench' }`. Add `{ label: '权限管理', routeName: 'organization-permissions', permission: 'system:role:view' }` under the organization navigation group. Add the permission page to the existing breadcrumb and organization-search mappings.

- [ ] **Step 6: Verify the focused frontend slice and commit**

Run: `cd frontend && npm run test:run -- src/features/permission src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts src/AppRouting.test.ts`

Expected: all focused tests PASS with no unhandled promise rejection or Vue warning.

```powershell
git add -- frontend/src/features/permission frontend/src/router/index.ts frontend/src/router/index.test.ts frontend/src/features/navigation/mockNavigationService.ts frontend/src/components/navigation/SidebarNav.test.ts frontend/src/layouts/ErpLayout.vue frontend/src/layouts/ErpLayout.test.ts frontend/src/AppRouting.test.ts
git commit -m "feat: add permission management page"
```

---

### Task 5: Backend RBAC Domain And Resolver

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/authorization/domain/DataScope.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/domain/PermissionDefinition.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/domain/Role.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/domain/RoleRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/application/ResolvedAuthorization.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/application/AuthorizationResolver.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/infrastructure/InMemoryRoleRepository.java`
- Test: `backend/src/test/java/com/bebefish/erp/authorization/application/AuthorizationResolverTest.java`

**Interfaces:**
- Produces: `ResolvedAuthorization(List<String> roles, List<String> permissions, Optional<DataScope> dataScope)`.
- Produces: `AuthorizationResolver.resolve(String memberKey)` for Task 6 login integration.

- [ ] **Step 1: Write failing resolver tests**

```java
private final FakeRoleRepository repository = new FakeRoleRepository(
        Set.of("product:view", "product:edit", "sales:view", "finance:view")
);
private final AuthorizationResolver resolver = new AuthorizationResolver(repository);

private static Role role(
        String code,
        boolean enabled,
        DataScope scope,
        Set<String> permissions,
        Set<String> members
) {
    return new Role(code, code, false, false, enabled, scope, permissions, members);
}

private Role superAdmin(Set<String> members) {
    return new Role("SUPER_ADMIN", "超级管理员", true, true, true,
            DataScope.COMPANY, Set.of(), members);
}

private static final class FakeRoleRepository implements RoleRepository {
    private final List<Role> roles = new ArrayList<>();
    private final Set<String> catalog;

    private FakeRoleRepository(Set<String> catalog) {
        this.catalog = Set.copyOf(catalog);
    }

    void save(Role role) {
        roles.add(role);
    }

    @Override
    public List<Role> findEnabledByMemberKey(String memberKey) {
        return roles.stream()
                .filter(Role::enabled)
                .filter(role -> role.memberKeys().contains(memberKey))
                .toList();
    }

    @Override
    public Set<String> findAllPermissionCodes() {
        return catalog;
    }
}

@Test
void combinesEnabledRolesAndUsesTheWidestScope() {
    repository.save(role("PRODUCT_OPERATOR", true, DataScope.DEPARTMENT,
            Set.of("product:view", "product:edit"), Set.of("13800138000")));
    repository.save(role("SALES_VIEWER", true, DataScope.COMPANY,
            Set.of("sales:view"), Set.of("13800138000")));

    var result = resolver.resolve("13800138000");

    assertThat(result.roles()).containsExactly("PRODUCT_OPERATOR", "SALES_VIEWER");
    assertThat(result.permissions()).containsExactly("product:edit", "product:view", "sales:view");
    assertThat(result.dataScope()).contains(DataScope.COMPANY);
}

@Test
void ignoresDisabledRolesAndReturnsEmptyAuthorizationForUnknownMember() {
    repository.save(role("DISABLED", false, DataScope.COMPANY,
            Set.of("finance:view"), Set.of("13800138000")));
    assertThat(resolver.resolve("13800138000").permissions()).isEmpty();
    assertThat(resolver.resolve("unknown").dataScope()).isEmpty();
}

@Test
void superAdministratorReceivesTheCompleteCatalog() {
    repository.save(superAdmin(Set.of("13800138000")));
    assertThat(resolver.resolve("13800138000").permissions())
            .containsExactlyElementsOf(repository.findAllPermissionCodes().stream().sorted().toList());
}
```

- [ ] **Step 2: Verify resolver tests fail**

Run: `cd backend && mvn -Dtest=AuthorizationResolverTest test`

Expected: FAIL because the authorization package does not exist.

- [ ] **Step 3: Implement immutable role and scope types**

```java
public enum DataScope {
    SELF, DEPARTMENT, DEPARTMENT_AND_DESCENDANTS, COMPANY;

    public static Optional<DataScope> widest(Collection<DataScope> scopes) {
        return scopes.stream().max(Comparator.comparingInt(Enum::ordinal));
    }
}

public record Role(
        String code,
        String name,
        boolean system,
        boolean superAdministrator,
        boolean enabled,
        DataScope dataScope,
        Set<String> permissionCodes,
        Set<String> memberKeys
) {
    public Role {
        permissionCodes = Set.copyOf(permissionCodes);
        memberKeys = Set.copyOf(memberKeys);
    }
}
```

`RoleRepository` must expose `List<Role> findEnabledByMemberKey(String memberKey)` and `Set<String> findAllPermissionCodes()`. `AuthorizationResolver` sorts role codes and permission codes for deterministic token claims, replaces a super administrator's permissions with the complete catalog, and calculates scope from enabled roles only.

- [ ] **Step 4: Seed the in-memory repository**

Seed `SUPER_ADMIN` with member key `13800138000`, `COMPANY`, and the complete catalog. The catalog must contain all currently enforced application permissions plus `system:role:manage`: `system:user:view`, `system:role:view`, `system:role:manage`, dashboard, masterdata, product, organization, inventory, sales, and finance permissions already listed in the existing demo account.

- [ ] **Step 5: Verify resolver tests and commit**

Run: `cd backend && mvn -Dtest=AuthorizationResolverTest test`

Expected: PASS with 3 tests.

```powershell
git add -- backend/src/main/java/com/bebefish/erp/authorization backend/src/test/java/com/bebefish/erp/authorization
git commit -m "feat: add RBAC authorization resolver"
```

---

### Task 6: Resolve Login Claims Through RBAC

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/auth/domain/AuthenticatedUser.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/UserAccount.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/TokenIssuer.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/application/AuthService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryUserAccountRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryTokenIssuer.java`
- Modify: `backend/src/test/java/com/bebefish/erp/auth/application/AuthServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/infrastructure/InMemoryTokenIssuerTest.java`
- Modify: every backend controller/security test helper returned by `rg -l "new UserAccount\(" backend/src/test`

**Interfaces:**
- Consumes: `AuthorizationResolver.resolve(mobile)`.
- Produces: `AuthenticatedUser(String mobile, List<String> roles, List<String> permissions)` and `TokenIssuer.issue(AuthenticatedUser, String)`.

- [ ] **Step 1: Change the auth service test first**

Update the test fixture so `UserAccount` contains only `mobile`, `passwordHash`, `enabled`, and `employeeActive`. Back it with a fake `RoleRepository` containing an enabled role for the test mobile.

```java
@Test
void passwordLoginReturnsClaimsResolvedFromActiveRoles() {
    users.save(activeUser("13800138000"));
    roles.save(role("ORG_ADMIN", Set.of("organization:view", "organization:manage"), "13800138000"));

    var result = authService.loginWithPassword(new PasswordLoginCommand("13800138000", "secret"));

    assertThat(result.roles()).containsExactly("ORG_ADMIN");
    assertThat(result.permissions()).containsExactly("organization:manage", "organization:view");
}
```

Retain wrong-password, disabled-user, SMS-login, and SMS-code tests unchanged in meaning.

Replace the existing development-administrator assertion with a repository-level integration check:

```java
var authorization = new AuthorizationResolver(new InMemoryRoleRepository()).resolve("13800138000");
assertThat(authorization.roles()).containsExactly("SUPER_ADMIN");
assertThat(authorization.permissions())
        .contains("system:role:view", "system:role:manage", "organization:view", "organization:manage");
```

- [ ] **Step 2: Verify the auth test fails for the expected constructor and resolver gap**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`

Expected: FAIL because `AuthService` does not accept `AuthorizationResolver` and `UserAccount` still owns claims.

- [ ] **Step 3: Separate credentials from authenticated claims**

```java
public record UserAccount(
        String mobile,
        String passwordHash,
        boolean enabled,
        boolean employeeActive
) {}

public record AuthenticatedUser(
        String mobile,
        List<String> roles,
        List<String> permissions
) {
    public AuthenticatedUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
```

Inject `AuthorizationResolver` into `AuthService`. After credential validation, resolve the user's mobile, construct `AuthenticatedUser`, and call `tokenIssuer.issue(authenticatedUser, loginMethod)`. Keep `LoginResult` and `ErpPrincipal` wire contracts unchanged.

- [ ] **Step 4: Update token issuers and test token helpers**

Change `TokenIssuer.issue` and `InMemoryTokenIssuer.issue` to accept `AuthenticatedUser`. Mechanically update controller/security test helpers to construct `AuthenticatedUser` with their existing explicit roles and permissions; do not change their authorization assertions.

- [ ] **Step 5: Add a focused token-claim test and verify database-independent auth tests**

```java
@Test
void storesResolvedRolesAndPermissionsInTheSessionSnapshot() {
    var issued = issuer.issue(new AuthenticatedUser(
            "13800138000",
            List.of("SUPER_ADMIN"),
            List.of("system:role:view", "system:role:manage")
    ), "password");

    assertThat(issuer.resolve(issued.accessToken()).roles()).containsExactly("SUPER_ADMIN");
    assertThat(issuer.resolve(issued.accessToken()).permissions())
            .containsExactly("system:role:view", "system:role:manage");
}
```

Run: `cd backend && mvn -Dtest=AuthorizationResolverTest,AuthServiceTest,InMemoryTokenIssuerTest test`

Expected: PASS without database variables. `AuthServiceTest` proves the development administrator resolves `SUPER_ADMIN`, `system:role:view`, and `system:role:manage`; `InMemoryTokenIssuerTest` proves those claims survive issue and resolve.

When all three test-database variables are configured, also run the controller/security test list from the existing suite to prove protected endpoints retain their previous pass/forbid behavior.

- [ ] **Step 6: Commit auth integration**

```powershell
git add -- backend/src/main/java/com/bebefish/erp/auth backend/src/test/java/com/bebefish/erp/auth backend/src/test/java/com/bebefish/erp/common/security backend/src/test/java/com/bebefish/erp/dashboard backend/src/test/java/com/bebefish/erp/file backend/src/test/java/com/bebefish/erp/inventory backend/src/test/java/com/bebefish/erp/masterdata backend/src/test/java/com/bebefish/erp/product backend/src/test/java/com/bebefish/erp/sales
git diff --cached --check
git commit -m "feat: resolve login claims from RBAC"
```

---

### Task 7: Whole-Slice Review And Clean Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-09-04-permission-management-implementation.md` for checked steps and exact evidence only.
- Review: all changes from `c7a98a3` through the implementation head.

**Interfaces:**
- Consumes: completed frontend and backend tasks.
- Produces: independent review verdict, clean test/build evidence, protected-file audit, and an integration choice without merge or push.

- [x] **Step 1: Run focused frontend verification**

Run: `cd frontend && npm run test:run -- src/features/permission src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts src/AppRouting.test.ts`

Expected: PASS with no warnings or unhandled errors.

- [x] **Step 2: Run complete frontend verification**

Run: `cd frontend && npm run test:run`

Expected: all test files and tests PASS.

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` and `vite build` both exit 0.

- [x] **Step 3: Run backend verification allowed by the environment**

Run: `cd backend && mvn -Dtest=AuthorizationResolverTest,AuthServiceTest,BearerTokenAuthenticationFilterTest test`

Expected: PASS without database variables.

If `ERP_TEST_DB_URL`, `ERP_TEST_DB_USERNAME`, and `ERP_TEST_DB_PASSWORD` are all non-empty, also run `cd backend && mvn test`. If any is empty, record that the complete backend suite remains blocked by the documented test-database requirement.

- [x] **Step 4: Request an independent review**

Use `superpowers:requesting-code-review` against the merge base for this implementation. The reviewer must check spec coverage, system-role protection, permission dependency, dirty-state safety, member locking, router access, backend claim resolution, protected-file scope, and test quality. Fix every Critical or Important finding with a new failing regression test before implementation changes; record Minor findings that remain outside the bounded scope.

- [x] **Step 5: Verify from a disposable clean worktree**

Create a disposable worktree at the implementation HEAD without moving `codex/organization-management`. Install or attach dependencies safely, then rerun the complete frontend suite, frontend build, and database-independent backend focused tests. Remove only the resolved disposable path after verifying it lies under the current Codex worktree directory.

- [x] **Step 6: Audit repository boundaries**

```powershell
$protected = @(
  'frontend/src/features/organization/DepartmentPositionManagement.test.ts',
  'frontend/src/features/organization/EmployeeManagementView.test.ts',
  'frontend/src/features/organization/views/DepartmentManagementView.vue',
  'frontend/src/features/organization/views/EmployeeManagementView.vue',
  'frontend/src/features/organization/views/PositionManagementView.vue'
)
$changed = @(git diff --name-only c7a98a3..HEAD)
$protected | Where-Object { $changed -contains $_ }
git status --short --branch
git diff --check c7a98a3..HEAD
```

Expected: the protected-file overlap prints nothing, diff check is clean, and only intended implementation-plan evidence remains before the final documentation commit.

- [x] **Step 7: Record evidence and commit the completed plan**

Check each completed step and append exact test counts, build output summary, reviewer verdict, environmental limitations, commit IDs, and protected-file result.

```powershell
git add -- docs/superpowers/plans/2026-09-04-permission-management-implementation.md
git diff --cached --check
git commit -m "docs: record permission management verification"
```

#### Completion evidence — 2026-09-04

- The current checkout began clean and detached at `fe5564a6a4824896c601c85ea0986a1ff1696886`; `git symbolic-ref --quiet HEAD` exited `1` and `git status --short --branch` reported `## HEAD (no branch)`.
- Focused frontend verification passed: 8 test files / 92 tests. Complete frontend verification passed: 41 test files / 483 tests. The frontend build passed `vue-tsc --noEmit`; Vite transformed 1,767 modules and built successfully.
- The database-independent backend command passed 18 tests: `AuthorizationResolverTest` 7, `AuthServiceTest` 6, and `BearerTokenAuthenticationFilterTest` 5. `InMemoryTokenIssuerTest` passed 1 test.
- The final independent review of `c7a98a3eb6c025138a2dd6134ad2767ddc2b3c54..fe5564a6a4824896c601c85ea0986a1ff1696886` returned 0 Critical / 0 Important / 0 Minor findings.
- All three `ERP_TEST_DB_*` values were empty (0/3); the complete backend suite was not run because it requires the documented test database.
- A detached clean worktree at `fe5564a6a4824896c601c85ea0986a1ff1696886` was created under `.superpowers/sdd/2026-09-04-permission-management-implementation/clean-verification-fe5564a6` after resolving and confirming the path was inside that directory. Its frontend dependencies were attached through a verified `node_modules` junction. There, the complete frontend suite passed 41 files / 483 tests, the build passed with 1,767 transformed modules, the three-class backend command passed 18 tests, and `InMemoryTokenIssuerTest` passed 1 test. After `git worktree remove` unregistered it and Windows reported `Filename too long`, recovery removed the junction without touching its source, renamed the unregistered residual inside the verified SDD parent, moved it to the verified ASCII path `C:\\codex-clean-fe5564a6`, and deleted that exact directory. Final checks: original residual absent, ASCII residual absent, source `node_modules` present, and disposable worktree registration absent.
- Protected-file overlap was 0/5; `git diff --check c7a98a3..HEAD` exited 0. No protected file was edited.
- Reviewed commits: `fb104fc4c6fa31e5f727ed66887a38a453ba1b5e`, `544ebac93358f73bb4d7a495814843c15b8a1ed0`, `964952ed089d61e8e7d44d4aa1e484e17d801f45`, `21c77d282c45e68dc15edda92a501947afa714f4`, `fa17796d3b7108bf7b813696cc4dee0922f37021`, `033770ffa430426d099b5b676fa8f1ab2428a725`, `ddf691549345af6aa65afc02ffa5bd2bde35119b`, `50cfd8027dbe40122e342a57c0235e84b4a78ec4`, `000ea7cfd660a5f60b49f99e26ff9246c1f6d880`, `513b9545de4ad2062dcf99b86006f9a83561bf44`, `9e4ce9ade93723c2daee61b6988e0080cc71772e`, `ff4513de9db9ded5a4f28895be156e1f063be943`, `61d678653ee517d6acd6adfa39fd4ce762add6f1`, `d2749a0380829053df56b3f47ee96fd17ea314aa`, and `fe5564a6a4824896c601c85ea0986a1ff1696886`.

- [x] **Step 8: Present integration choices without acting automatically**

Report the detached HEAD, implementation commits, review verdict, tests, build, backend limitation, and protected-file audit. Do not merge or push. Follow `superpowers:finishing-a-development-branch` for the integration menu appropriate to the current Codex-managed detached worktree.

Completion evidence: the detached-HEAD reduced two-option integration menu is prepared for the main agent's immediate user handoff. No merge, push, or branch-pointer move occurred.
