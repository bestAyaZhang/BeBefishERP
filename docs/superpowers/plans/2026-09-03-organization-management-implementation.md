# Organization Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 BeBefish ERP 中交付可操作的员工、部门和岗位管理前端，并通过可注入 Mock Service 固定未来后端接口契约。

**Architecture:** 新建 `features/organization` 垂直模块，纯函数负责部门树，Mock Service 负责查询、分页和业务校验，Vue 页面只编排筛选、抽屉和列表状态。路由、导航和演示账号权限沿用现有项目机制；真实飞书 OAuth、组织数据库和 HTTP Service 留到后续迭代。

**Tech Stack:** Vue 3、TypeScript、Vue Router、Tailwind CSS、Lucide Vue、Vitest、Vue Test Utils、Java 21、Spring Boot 3。

**Spec:** `docs/superpowers/specs/2026-09-03-organization-management-design.md`

## Global Constraints

- 桌面基准为 `1440 × 1024`，本轮不增加移动端专用布局。
- 使用 Noto Sans SC 正文字体、Inter 数字字体和现有项目颜色令牌。
- 表格只使用横向分隔线，不增加竖向表格线；操作使用文字按钮。
- 员工只归属一个主部门和一个岗位；岗位选项随部门联动。
- 正式员工支持飞书绑定状态和可选密码登录；临时员工必须启用手机号和密码。
- 密码仅存在于保存载荷，不进入员工返回模型、列表、日志或 `localStorage`。
- Mock 数据在路由切换期间保留，浏览器刷新后重置。
- 本轮不实现组织数据库迁移、真实飞书 OAuth、短信服务、角色权限编辑或员工历史轨迹。

---

### Task 1: Organization Domain Types And Department Tree

**Files:**
- Create: `frontend/src/features/organization/types.ts`
- Create: `frontend/src/features/organization/organizationTree.ts`
- Test: `frontend/src/features/organization/organizationTree.test.ts`

**Interfaces:**
- Produces: `Department`, `Position`, `Employee`, `EmployeeQuery`, `PositionQuery`, `PageResult<T>`, `OrganizationSummary`, save payloads, `buildDepartmentTree(departments)`, `collectDepartmentSubtreeIds(departments, rootId)` and `filterDepartmentTree(nodes, keyword)`.
- Consumes: no organization module dependencies.

- [ ] **Step 1: Write failing department tree tests**

```ts
import { describe, expect, it } from 'vitest';
import { buildDepartmentTree, collectDepartmentSubtreeIds, filterDepartmentTree } from './organizationTree';
import type { Department } from './types';

const departments: Department[] = [
  { id: 1, departmentCode: 'HQ', departmentName: '总部', parentId: null, managerEmployeeId: 1, managerName: 'Aya Zhang', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PD', departmentName: '产品中心', parentId: 1, managerEmployeeId: 2, managerName: '张敏', sortOrder: 1, status: 'enabled' },
  { id: 3, departmentCode: 'DS', departmentName: '设计组', parentId: 2, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' }
];

describe('organizationTree', () => {
  it('builds ordered nested nodes without mutating source records', () => {
    const tree = buildDepartmentTree(departments);
    expect(tree[0].children[0].children[0].departmentName).toBe('设计组');
    expect(departments[0]).not.toHaveProperty('children');
  });

  it('collects the selected department and every descendant', () => {
    expect([...collectDepartmentSubtreeIds(departments, 2)]).toEqual([2, 3]);
  });

  it('keeps ancestors when a descendant matches search', () => {
    const filtered = filterDepartmentTree(buildDepartmentTree(departments), '设计');
    expect(filtered[0].children[0].children[0].departmentName).toBe('设计组');
  });
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd frontend && npm run test:run -- src/features/organization/organizationTree.test.ts`

Expected: FAIL because `types.ts` and `organizationTree.ts` do not exist.

- [ ] **Step 3: Add exact domain contracts and pure tree helpers**

```ts
export type OrganizationRecordStatus = 'enabled' | 'disabled';
export type EmployeeStatus = 'active' | 'disabled' | 'resigned';
export type EmploymentType = 'formal' | 'temporary';
export type FeishuBindingStatus = 'bound' | 'pending' | 'unbound';

export interface Department {
  id: number;
  departmentCode: string;
  departmentName: string;
  parentId: number | null;
  managerEmployeeId: number | null;
  managerName: string;
  sortOrder: number;
  status: OrganizationRecordStatus;
}

export interface DepartmentTreeNode extends Department {
  children: DepartmentTreeNode[];
}

export interface Position {
  id: number;
  positionCode: string;
  positionName: string;
  departmentId: number;
  responsibilities: string;
  status: OrganizationRecordStatus;
  employeeCount: number;
}

export interface DepartmentListItem extends Department {
  employeeCount: number;
  childCount: number;
  statusActionDisabled: boolean;
}

export interface Employee {
  id: number;
  employeeNo: string;
  employeeName: string;
  mobile: string;
  departmentId: number;
  positionId: number;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  hireDate: string;
  feishuBindingStatus: FeishuBindingStatus;
  feishuDisplayName: string;
  passwordLoginEnabled: boolean;
}

export interface PageResult<T> {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface EmployeeQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: number;
  employmentType?: EmploymentType;
  status?: EmployeeStatus;
}

export interface PositionQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: number;
  status?: OrganizationRecordStatus;
}

export interface DepartmentQuery {
  page: number;
  size: number;
  keyword?: string;
  status?: OrganizationRecordStatus;
}

export interface OrganizationSummary {
  formalEmployees: number;
  temporaryEmployees: number;
  pendingFeishuBindings: number;
  disabledAccounts: number;
}

export interface SaveEmployeePayload extends Omit<Employee, 'id' | 'feishuDisplayName' | 'feishuBindingStatus'> {
  password?: string;
}

export type SaveDepartmentPayload = Omit<Department, 'id' | 'managerName'>;
export type SavePositionPayload = Omit<Position, 'id' | 'employeeCount'>;
```

Implement tree building with a `Map<number, DepartmentTreeNode>`, order siblings by `sortOrder` then `departmentName`, collect descendants depth-first without duplicates, and filter recursively while preserving matching ancestors.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `cd frontend && npm run test:run -- src/features/organization/organizationTree.test.ts`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit the domain foundation**

```bash
git add frontend/src/features/organization/types.ts frontend/src/features/organization/organizationTree.ts frontend/src/features/organization/organizationTree.test.ts
git commit -m "feat: add organization domain model"
```

### Task 2: Mutable Mock Organization Service

**Files:**
- Create: `frontend/src/features/organization/organizationService.ts`
- Create: `frontend/src/features/organization/mockOrganizationService.ts`
- Test: `frontend/src/features/organization/mockOrganizationService.test.ts`

**Interfaces:**
- Consumes: all Task 1 types and `collectDepartmentSubtreeIds`.
- Produces: `OrganizationService`, `createMockOrganizationService()` and singleton `organizationService`.

- [ ] **Step 1: Write failing service behavior tests**

```ts
import { beforeEach, describe, expect, it } from 'vitest';
import { createMockOrganizationService } from './mockOrganizationService';

describe('mockOrganizationService', () => {
  let service: ReturnType<typeof createMockOrganizationService>;
  beforeEach(() => { service = createMockOrganizationService(); });

  it('filters employees by a department subtree, keyword, type and status', async () => {
    const departments = await service.listAllDepartments();
    const productCenter = departments.find((item) => item.departmentName === '产品中心')!;
    const page = await service.listEmployees({ page: 1, size: 20, departmentId: productCenter.id, keyword: '张', employmentType: 'formal', status: 'active' });
    expect(page.records.length).toBeGreaterThan(0);
    expect(page.records.every((employee) => employee.employmentType === 'formal' && employee.status === 'active')).toBe(true);
  });

  it('requires password login and a password for a new temporary employee', async () => {
    await expect(service.createEmployee({
      employeeNo: 'TMP-009', employeeName: '临时员工', mobile: '13900000009', departmentId: 2, positionId: 3,
      employmentType: 'temporary', status: 'active', hireDate: '2026-09-03', passwordLoginEnabled: false
    })).rejects.toThrow('临时员工必须启用手机号和密码登录');
  });

  it('never returns an employee password after creation', async () => {
    const created = await service.createEmployee({
      employeeNo: 'TMP-010', employeeName: '临时员工', mobile: '13900000010', departmentId: 2, positionId: 3,
      employmentType: 'temporary', status: 'active', hireDate: '2026-09-03', passwordLoginEnabled: true, password: 'Temp@123456'
    });
    expect(created).not.toHaveProperty('password');
  });

  it('rejects a position outside the selected department', async () => {
    await expect(service.createEmployee({
      employeeNo: 'E-099', employeeName: '错配员工', mobile: '13900000011', departmentId: 2, positionId: 8,
      employmentType: 'formal', status: 'active', hireDate: '2026-09-03', passwordLoginEnabled: false
    })).rejects.toThrow('岗位不属于所选部门');
  });
});
```

- [ ] **Step 2: Run the service test and verify it fails**

Run: `cd frontend && npm run test:run -- src/features/organization/mockOrganizationService.test.ts`

Expected: FAIL because the service files do not exist.

- [ ] **Step 3: Define the service interface**

```ts
export interface OrganizationService {
  listDepartmentPage(query: DepartmentQuery): Promise<PageResult<DepartmentListItem>>;
  listAllDepartments(): Promise<Department[]>;
  getDepartmentEmployeeCounts(): Promise<Record<number, number>>;
  listPositions(query: PositionQuery): Promise<PageResult<Position>>;
  listAllPositions(): Promise<Position[]>;
  listEmployees(query: EmployeeQuery): Promise<PageResult<Employee>>;
  getSummary(): Promise<OrganizationSummary>;
  getEmployee(id: number): Promise<Employee>;
  createEmployee(payload: SaveEmployeePayload): Promise<Employee>;
  updateEmployee(id: number, payload: SaveEmployeePayload): Promise<Employee>;
  changeEmployeeStatus(id: number, status: EmployeeStatus): Promise<Employee>;
  createDepartment(payload: SaveDepartmentPayload): Promise<Department>;
  updateDepartment(id: number, payload: SaveDepartmentPayload): Promise<Department>;
  changeDepartmentStatus(id: number, status: OrganizationRecordStatus): Promise<Department>;
  createPosition(payload: SavePositionPayload): Promise<Position>;
  updatePosition(id: number, payload: SavePositionPayload): Promise<Position>;
  changePositionStatus(id: number, status: OrganizationRecordStatus): Promise<Position>;
}
```

Use `listDepartmentPage(query)` only for department management pagination and `listAllDepartments()` for trees and selects.

- [ ] **Step 4: Implement deterministic seed data and validation**

Create a company root plus at least eight nested departments, at least ten department-bound positions and at least twenty employees. Include formal/temporary, bound/pending/unbound, active/disabled/resigned records so every filter and badge has data.

Business rules implemented in the service:

```ts
function validateEmployee(payload: SaveEmployeePayload, positions: Position[], employees: Employee[], editingId?: number) {
  if (!payload.employeeNo.trim() || !payload.employeeName.trim() || !payload.mobile.trim()) throw new Error('请完整填写员工基本资料');
  if (!/^1\d{10}$/.test(payload.mobile)) throw new Error('请输入正确的手机号');
  if (employees.some((item) => item.id !== editingId && item.employeeNo === payload.employeeNo.trim())) throw new Error('员工工号已存在');
  if (employees.some((item) => item.id !== editingId && item.mobile === payload.mobile.trim())) throw new Error('手机号已绑定其他员工');
  if (!positions.some((item) => item.id === payload.positionId && item.departmentId === payload.departmentId && item.status === 'enabled')) throw new Error('岗位不属于所选部门');
  if (payload.employmentType === 'temporary' && !payload.passwordLoginEnabled) throw new Error('临时员工必须启用手机号和密码登录');
  if (payload.employmentType === 'temporary' && !editingId && !payload.password?.trim()) throw new Error('请设置临时员工登录密码');
}
```

Also validate department code/name uniqueness, prevent a department from becoming its own descendant, validate position uniqueness within one department, prevent disabling a department that has enabled children or active employees, and prevent disabling a position assigned to active employees.

- [ ] **Step 5: Run focused service tests**

Run: `cd frontend && npm run test:run -- src/features/organization/mockOrganizationService.test.ts`

Expected: PASS for query, pagination, create/update, account rules and status validation.

- [ ] **Step 6: Commit the service layer**

```bash
git add frontend/src/features/organization/organizationService.ts frontend/src/features/organization/mockOrganizationService.ts frontend/src/features/organization/mockOrganizationService.test.ts
git commit -m "feat: add organization mock service"
```

### Task 3: Routes, Navigation And Auth Permission

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/index.test.ts`
- Modify: `frontend/src/features/navigation/types.ts`
- Modify: `frontend/src/features/navigation/mockNavigationService.ts`
- Modify: `frontend/src/components/navigation/SidebarNav.vue`
- Modify: `frontend/src/components/navigation/SidebarNav.test.ts`
- Modify: `frontend/src/layouts/ErpLayout.vue`
- Modify: `frontend/src/layouts/ErpLayout.test.ts`
- Modify: `frontend/src/AppRouting.test.ts`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryUserAccountRepository.java`
- Modify: `backend/src/test/java/com/bebefish/erp/auth/application/AuthServiceTest.java`

**Interfaces:**
- Consumes: view modules added by Tasks 5 and 6 through lazy imports.
- Produces: route names `organization-employees`, `organization-departments`, `organization-positions`; sidebar icon `organization`; permissions `organization:view` and `organization:manage`.

- [ ] **Step 1: Add failing route, navigation and header tests**

```ts
it('registers organization management routes', () => {
  expect(router.resolve({ name: 'organization-employees' }).path).toBe('/organization/employees');
  expect(router.resolve({ name: 'organization-departments' }).path).toBe('/organization/departments');
  expect(router.resolve({ name: 'organization-positions' }).path).toBe('/organization/positions');
});

it('shows organization children only with organization permission', () => {
  storeCurrentUser(['organization:view']);
  const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
  expect(wrapper.text()).toContain('组织架构');
  expect(wrapper.text()).toContain('员工管理');
  expect(wrapper.text()).toContain('部门管理');
  expect(wrapper.text()).toContain('岗位管理');
});
```

Add an `ErpLayout` assertion that organization routes display `组织架构 / 员工管理` and the placeholder `搜索员工、手机号或岗位`.

- [ ] **Step 2: Run the focused routing tests and verify failure**

Run: `cd frontend && npm run test:run -- src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts`

Expected: FAIL because organization routes and navigation do not exist.

- [ ] **Step 3: Register lazy routes, navigation and page headers**

```ts
{ path: 'organization/employees', name: 'organization-employees', component: () => import('../features/organization/views/EmployeeManagementView.vue') },
{ path: 'organization/departments', name: 'organization-departments', component: () => import('../features/organization/views/DepartmentManagementView.vue') },
{ path: 'organization/positions', name: 'organization-positions', component: () => import('../features/organization/views/PositionManagementView.vue') }
```

Add one sidebar group with `UsersRound` from Lucide, label `组织架构`, and three children using `organization:view`. Add page headers with Chinese group/title text. Compute the organization search placeholder when `String(route.name).startsWith('organization-')`.

- [ ] **Step 4: Grant demo administrator permissions and test them**

Add `organization:view` and `organization:manage` to the in-memory administrator permission list. Update `AuthServiceTest` so the expected permission response includes both exact strings.

- [ ] **Step 5: Run frontend routing tests and backend auth test**

Run: `cd frontend && npm run test:run -- src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts src/AppRouting.test.ts`

Expected: PASS.

Run with dedicated test DB environment only if already configured: `cd backend && mvn -Dtest=AuthServiceTest test`

Expected: PASS without database access because this test uses in-memory auth collaborators.

- [ ] **Step 6: Commit navigation integration**

```bash
git add frontend/src/router frontend/src/features/navigation frontend/src/components/navigation/SidebarNav.vue frontend/src/components/navigation/SidebarNav.test.ts frontend/src/layouts frontend/src/AppRouting.test.ts backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryUserAccountRepository.java backend/src/test/java/com/bebefish/erp/auth/application/AuthServiceTest.java
git commit -m "feat: add organization navigation"
```

### Task 4: Shared Organization Components

**Files:**
- Create: `frontend/src/features/organization/components/DepartmentTree.vue`
- Create: `frontend/src/features/organization/components/OrganizationPagination.vue`
- Create: `frontend/src/features/organization/components/OrganizationStatusBadge.vue`
- Test: `frontend/src/features/organization/OrganizationComponents.test.ts`

**Interfaces:**
- Consumes: `DepartmentTreeNode`, status union types and `buildDepartmentTree` output.
- Produces: `DepartmentTree` events `select(id: number | null)` and `toggle(id: number)`; `OrganizationPagination` event `page(page: number)`; shared status presentation.

- [ ] **Step 1: Write failing component interaction tests**

```ts
it('selects all company and a nested department', async () => {
  const wrapper = mount(DepartmentTree, { props: { nodes: tree, selectedId: null, expandedIds: [1, 2] } });
  await wrapper.get('[data-testid="department-all"]').trigger('click');
  expect(wrapper.emitted('select')?.[0]).toEqual([null]);
  await wrapper.get('[data-testid="department-node-3"]').trigger('click');
  expect(wrapper.emitted('select')?.[1]).toEqual([3]);
});

it('emits a valid page and disables boundary controls', async () => {
  const wrapper = mount(OrganizationPagination, { props: { page: 1, pageSize: 10, total: 83 } });
  expect(wrapper.get('[data-testid="previous-page"]').attributes('disabled')).toBeDefined();
  await wrapper.get('[data-testid="page-2"]').trigger('click');
  expect(wrapper.emitted('page')?.[0]).toEqual([2]);
});
```

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: FAIL because components do not exist.

- [ ] **Step 3: Implement the department tree**

Use `ChevronRight`, `Building2`, `Search` and stable dimensions. The left panel contains title `组织架构`, one search input, `全公司`, nested buttons with indentation by depth, and employee count badges supplied as `employeeCounts: Record<number, number>`. Do not show edit/delete actions in the tree.

```vue
<button
  :data-testid="`department-node-${node.id}`"
  :class="selectedId === node.id ? 'bg-[#eef2ff] text-[#536dff]' : 'text-[#475569] hover:bg-slate-50'"
  @click="$emit('select', node.id)"
>
  <span class="min-w-0 flex-1 truncate">{{ node.departmentName }}</span>
  <span class="font-number text-xs">{{ employeeCounts[node.id] ?? 0 }}</span>
</button>
```

- [ ] **Step 4: Implement pagination and badges**

Pagination must show total count, previous/next, compact page numbers with ellipsis, and a page-size select for 10/20/50 rows. Badge labels and colors:

```ts
const statusPresentation = {
  active: ['在职', 'bg-emerald-50 text-emerald-700'],
  disabled: ['停用', 'bg-slate-100 text-slate-600'],
  resigned: ['离职', 'bg-rose-50 text-rose-600'],
  enabled: ['启用', 'bg-emerald-50 text-emerald-700'],
  bound: ['已绑定', 'bg-blue-50 text-blue-700'],
  pending: ['待绑定', 'bg-amber-50 text-amber-700'],
  unbound: ['未绑定', 'bg-slate-100 text-slate-600']
} as const;
```

- [ ] **Step 5: Run component tests and commit**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: PASS.

```bash
git add frontend/src/features/organization/components frontend/src/features/organization/OrganizationComponents.test.ts
git commit -m "feat: add organization shared components"
```

### Task 5: Employee Management And Employee Drawer

**Files:**
- Create: `frontend/src/features/organization/views/EmployeeManagementView.vue`
- Create: `frontend/src/features/organization/components/EmployeeFormDrawer.vue`
- Test: `frontend/src/features/organization/EmployeeManagementView.test.ts`

**Interfaces:**
- Consumes: `OrganizationService` through `inject('organizationService', organizationService)`, shared tree/pagination/badge components and Task 1 models.
- Produces: complete employee list, read-only view, create and edit flow.

- [ ] **Step 1: Write failing employee workflow tests**

```ts
it('loads summary cards and filters by the selected department subtree', async () => {
  const wrapper = mount(EmployeeManagementView, { global: { provide: { organizationService: service } } });
  await flushPromises();
  expect(wrapper.get('[data-testid="formal-employee-count"]').text()).toMatch(/\d+/);
  await wrapper.get('[data-testid="department-node-2"]').trigger('click');
  await flushPromises();
  expect(service.listEmployees).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: 2, page: 1 }));
});

it('validates temporary account rules and saves a new employee', async () => {
  const wrapper = mount(EmployeeManagementView, { global: { provide: { organizationService: service } } });
  await flushPromises();
  await wrapper.get('[data-testid="add-employee"]').trigger('click');
  await wrapper.get('[data-testid="employment-temporary"]').setValue(true);
  await wrapper.get('[data-testid="save-employee"]').trigger('click');
  expect(wrapper.text()).toContain('临时员工必须启用手机号和密码登录');
});

it('filters positions when department changes', async () => {
  const wrapper = mount(EmployeeFormDrawer, { props: drawerProps });
  await wrapper.get('[data-testid="employee-department"]').setValue('3');
  expect(wrapper.findAll('[data-testid="employee-position"] option').every((option) => option.attributes('data-department-id') === '3')).toBe(true);
});
```

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm run test:run -- src/features/organization/EmployeeManagementView.test.ts`

Expected: FAIL because the employee view and drawer do not exist.

- [ ] **Step 3: Implement the employee page layout**

Build one full-width page band containing a `280px` department tree and a `minmax(0, 1fr)` list area. The right header contains title, summary, query fields and primary `新增员工` button. Table columns: employee, employee number, department/position, employment type, login method, status, hire date, action. Use text actions `查看` and `编辑`.

Required states: skeleton/loading, red inline error, empty result, page reset on filters, no horizontal overlap at 1440px. Selecting a parent department uses service subtree filtering.

- [ ] **Step 4: Implement the 50% employee drawer**

Use a fixed right overlay with `width: min(50vw, 720px)` and modes `create | edit | view`. Sections are `基本资料`, `部门与岗位`, `登录账号`. Use native/select-styled controls consistent with product editor selects.

```ts
const visiblePositions = computed(() => props.positions.filter((item) => item.departmentId === form.value.departmentId && item.status === 'enabled'));
const temporaryAccountInvalid = computed(() => form.value.employmentType === 'temporary' && (!form.value.passwordLoginEnabled || (!props.employee && !form.value.password.trim())));
```

Formal employees show Feishu binding status and an optional password-login toggle. Temporary employees hide Feishu controls, force password login, and require a password on create. View mode disables all controls, omits password fields and shows one top-right `编辑` command.

- [ ] **Step 5: Run employee tests and commit**

Run: `cd frontend && npm run test:run -- src/features/organization/EmployeeManagementView.test.ts`

Expected: PASS for loading, filters, subtree selection, drawer validation, linked positions, create, edit, cancel and read-only mode.

```bash
git add frontend/src/features/organization/views/EmployeeManagementView.vue frontend/src/features/organization/components/EmployeeFormDrawer.vue frontend/src/features/organization/EmployeeManagementView.test.ts
git commit -m "feat: add employee management"
```

### Task 6: Department And Position Management

**Files:**
- Create: `frontend/src/features/organization/views/DepartmentManagementView.vue`
- Create: `frontend/src/features/organization/views/PositionManagementView.vue`
- Create: `frontend/src/features/organization/components/DepartmentFormDrawer.vue`
- Create: `frontend/src/features/organization/components/PositionFormDrawer.vue`
- Test: `frontend/src/features/organization/DepartmentPositionManagement.test.ts`

**Interfaces:**
- Consumes: `OrganizationService`, department tree, pagination, status badge and Task 1 payloads.
- Produces: department and position query/create/edit/status workflows.

- [ ] **Step 1: Write failing management tests**

```ts
it('creates a child department from the selected tree node', async () => {
  const wrapper = mount(DepartmentManagementView, { global: { provide: { organizationService: service } } });
  await flushPromises();
  await wrapper.get('[data-testid="department-node-2"]').trigger('click');
  await wrapper.get('[data-testid="add-department"]').trigger('click');
  expect((wrapper.get('[data-testid="department-parent"]').element as HTMLSelectElement).value).toBe('2');
});

it('filters positions by selected department and opens a prefilled editor', async () => {
  const wrapper = mount(PositionManagementView, { global: { provide: { organizationService: service } } });
  await flushPromises();
  await wrapper.get('[data-testid="department-node-3"]').trigger('click');
  await flushPromises();
  expect(service.listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: 3 }));
  await wrapper.get('[data-testid^="edit-position-"]').trigger('click');
  expect(wrapper.get('[data-testid="position-drawer"]').exists()).toBe(true);
});
```

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm run test:run -- src/features/organization/DepartmentPositionManagement.test.ts`

Expected: FAIL because management views do not exist.

- [ ] **Step 3: Implement department management**

Use the shared tree on the left and a department list on the right. Query by name/code and status. Columns: code, name, parent, manager, employee count, status, action. Text actions: `编辑` and `停用/启用`; show `不可停用` when the service rule says active descendants or staff block the action. Drawer fields: department code, name, parent department, manager, sort order, status.

- [ ] **Step 4: Implement position management**

Use the same shared tree to scope the position query. Columns: code, name, department, employee count, responsibilities, status, action. Drawer fields: position code, name, department, responsibilities, status. Surface duplicate-name and occupied-position errors at the drawer top while preserving entered data.

- [ ] **Step 5: Run management tests and commit**

Run: `cd frontend && npm run test:run -- src/features/organization/DepartmentPositionManagement.test.ts`

Expected: PASS for keyword/status/department filtering, prefilled drawers, validation and status operations.

```bash
git add frontend/src/features/organization/views/DepartmentManagementView.vue frontend/src/features/organization/views/PositionManagementView.vue frontend/src/features/organization/components/DepartmentFormDrawer.vue frontend/src/features/organization/components/PositionFormDrawer.vue frontend/src/features/organization/DepartmentPositionManagement.test.ts
git commit -m "feat: add department and position management"
```

### Task 7: Integration, Visual Verification And Delivery

**Files:**
- Modify: `frontend/src/features/organization/*.test.ts` only when verification exposes a missing assertion.
- Create: `frontend/prototype-screenshots/organization/employee-management.png`
- Create: `frontend/prototype-screenshots/organization/employee-department-filter.png`
- Create: `frontend/prototype-screenshots/organization/department-management.png`
- Create: `frontend/prototype-screenshots/organization/position-management.png`
- Create: `frontend/prototype-screenshots/organization/employee-drawer.png`
- Modify: `frontend/prototype-screenshots/README.md`

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: verified desktop organization module and durable visual references.

- [ ] **Step 1: Run all organization tests**

Run: `cd frontend && npm run test:run -- src/features/organization src/router/index.test.ts src/components/navigation/SidebarNav.test.ts src/layouts/ErpLayout.test.ts src/AppRouting.test.ts`

Expected: PASS with no unhandled Vue warnings.

- [ ] **Step 2: Run the complete frontend suite and production build**

Run: `cd frontend && npm run test:run`

Expected: all test files pass.

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` and `vite build` both succeed.

- [ ] **Step 3: Run backend verification**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`

Expected: PASS.

When `ERP_TEST_DB_URL`, `ERP_TEST_DB_USERNAME` and `ERP_TEST_DB_PASSWORD` are available, run `cd backend && mvn test`. Otherwise run `cd backend && mvn -DskipTests package` and report the full-test environment limitation without claiming all backend tests passed.

- [ ] **Step 4: Verify interactions in the browser at 1440 × 1024**

Start the frontend with `VITE_DATA_SOURCE=real` only after confirming the organization service intentionally remains Mock-backed. Verify:

1. `组织架构` navigation opens employee management.
2. `全公司` and `产品中心` change records and counts.
3. Keyword/type/status filters reset to page 1.
4. Employee read-only drawer contains no editable password.
5. Creating a temporary employee requires password login.
6. Department and position drawers save and immediately refresh lists.
7. No text, select, table cell or drawer footer overlaps at 1440 × 1024.
8. Browser console contains no errors.

- [ ] **Step 5: Capture and index visual evidence**

Save the five named screenshots under `frontend/prototype-screenshots/organization/`. Update the screenshot README with one row per file and its state. Compare spacing, typography, card radii, table density, tree indentation and 50% drawer width with the `04 Organization` Figma frames.

- [ ] **Step 6: Commit verification artifacts**

```bash
git add frontend/prototype-screenshots/organization frontend/prototype-screenshots/README.md
git commit -m "test: verify organization management ui"
```
