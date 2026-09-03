import { collectDepartmentSubtreeIds } from './organizationTree';
import type { OrganizationService } from './organizationService';
import type {
  Department,
  DepartmentListItem,
  DepartmentQuery,
  Employee,
  EmployeeQuery,
  OrganizationSummary,
  PageResult,
  Position,
  PositionQuery,
  SaveDepartmentPayload,
  SaveEmployeePayload,
  SavePositionPayload
} from './types';

const defaultDepartments: Department[] = [
  { id: 1, departmentCode: 'BBF', departmentName: '贝贝鱼科技有限公司', parentId: null, managerEmployeeId: 1, managerName: '陈立', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PD', departmentName: '产品中心', parentId: 1, managerEmployeeId: 2, managerName: '李娜', sortOrder: 10, status: 'enabled' },
  { id: 3, departmentCode: 'RD', departmentName: '产品研发部', parentId: 2, managerEmployeeId: 4, managerName: '张伟', sortOrder: 10, status: 'enabled' },
  { id: 4, departmentCode: 'DESIGN', departmentName: '产品设计部', parentId: 2, managerEmployeeId: 6, managerName: '赵倩', sortOrder: 20, status: 'enabled' },
  { id: 5, departmentCode: 'SALES', departmentName: '销售中心', parentId: 1, managerEmployeeId: 7, managerName: '孙强', sortOrder: 20, status: 'enabled' },
  { id: 6, departmentCode: 'DOM-SALES', departmentName: '国内销售部', parentId: 5, managerEmployeeId: 8, managerName: '周婷', sortOrder: 10, status: 'enabled' },
  { id: 7, departmentCode: 'INT-SALES', departmentName: '海外销售部', parentId: 5, managerEmployeeId: 9, managerName: '吴晨', sortOrder: 20, status: 'enabled' },
  { id: 8, departmentCode: 'SUPPLY', departmentName: '供应链中心', parentId: 1, managerEmployeeId: 10, managerName: '郑凯', sortOrder: 30, status: 'enabled' },
  { id: 9, departmentCode: 'PROCUREMENT', departmentName: '采购部', parentId: 8, managerEmployeeId: 11, managerName: '刘芳', sortOrder: 10, status: 'enabled' },
  { id: 10, departmentCode: 'WAREHOUSE', departmentName: '仓储物流部', parentId: 8, managerEmployeeId: 12, managerName: '何军', sortOrder: 20, status: 'enabled' },
  { id: 11, departmentCode: 'ARCHIVE', departmentName: '归档项目组', parentId: 2, managerEmployeeId: null, managerName: '', sortOrder: 90, status: 'disabled' }
];

const defaultPositions: Position[] = [
  { id: 1, positionCode: 'CEO', positionName: '总经理', departmentId: 1, responsibilities: '负责公司经营管理', status: 'enabled', employeeCount: 0 },
  { id: 2, positionCode: 'PD-DIR', positionName: '产品总监', departmentId: 2, responsibilities: '负责产品中心管理', status: 'enabled', employeeCount: 0 },
  { id: 3, positionCode: 'PM', positionName: '产品经理', departmentId: 2, responsibilities: '负责产品规划与交付', status: 'enabled', employeeCount: 0 },
  { id: 4, positionCode: 'BE', positionName: '后端工程师', departmentId: 3, responsibilities: '负责服务端研发', status: 'enabled', employeeCount: 0 },
  { id: 5, positionCode: 'FE', positionName: '前端工程师', departmentId: 3, responsibilities: '负责前端研发', status: 'enabled', employeeCount: 0 },
  { id: 6, positionCode: 'DESIGNER', positionName: '产品设计师', departmentId: 4, responsibilities: '负责产品体验设计', status: 'enabled', employeeCount: 0 },
  { id: 7, positionCode: 'DOM-SALES-MGR', positionName: '国内销售经理', departmentId: 6, responsibilities: '负责国内销售业务', status: 'enabled', employeeCount: 0 },
  { id: 8, positionCode: 'SALES-DIR', positionName: '销售总监', departmentId: 5, responsibilities: '负责销售中心管理', status: 'enabled', employeeCount: 0 },
  { id: 9, positionCode: 'INT-SALES-MGR', positionName: '海外销售经理', departmentId: 7, responsibilities: '负责海外销售业务', status: 'enabled', employeeCount: 0 },
  { id: 10, positionCode: 'SUPPLY-DIR', positionName: '供应链总监', departmentId: 8, responsibilities: '负责供应链管理', status: 'enabled', employeeCount: 0 },
  { id: 11, positionCode: 'BUYER', positionName: '采购专员', departmentId: 9, responsibilities: '负责采购执行', status: 'enabled', employeeCount: 0 },
  { id: 12, positionCode: 'WH-MGR', positionName: '仓储主管', departmentId: 10, responsibilities: '负责仓储物流管理', status: 'enabled', employeeCount: 0 },
  { id: 13, positionCode: 'ARCHIVE-PM', positionName: '归档项目经理', departmentId: 11, responsibilities: '负责归档项目', status: 'disabled', employeeCount: 0 }
];

function seedEmployee(
  id: number,
  employeeName: string,
  departmentId: number,
  positionId: number,
  options: Partial<Pick<Employee,
    'employmentType' | 'status' | 'feishuBindingStatus' | 'passwordLoginEnabled' | 'hireDate'>> = {}
): Employee {
  const feishuBindingStatus = options.feishuBindingStatus ?? 'bound';
  return {
    id,
    employeeNo: `E-${String(id).padStart(3, '0')}`,
    employeeName,
    mobile: `138000000${String(id).padStart(2, '0')}`,
    departmentId,
    positionId,
    employmentType: options.employmentType ?? 'formal',
    status: options.status ?? 'active',
    hireDate: options.hireDate ?? '2025-01-06',
    feishuBindingStatus,
    feishuDisplayName: feishuBindingStatus === 'bound' ? employeeName : '',
    passwordLoginEnabled: options.passwordLoginEnabled ?? false
  };
}

const defaultEmployees: Employee[] = [
  seedEmployee(1, '陈立', 1, 1),
  seedEmployee(2, '李娜', 2, 2),
  seedEmployee(3, '张敏', 2, 3, { feishuBindingStatus: 'pending' }),
  seedEmployee(4, '张伟', 3, 4),
  seedEmployee(5, '王磊', 3, 5, { feishuBindingStatus: 'pending' }),
  seedEmployee(6, '赵倩', 4, 6),
  seedEmployee(7, '孙强', 5, 8),
  seedEmployee(8, '周婷', 6, 7),
  seedEmployee(9, '吴晨', 7, 9, { feishuBindingStatus: 'pending' }),
  seedEmployee(10, '郑凯', 8, 10),
  seedEmployee(11, '刘芳', 9, 11),
  seedEmployee(12, '何军', 10, 12),
  seedEmployee(13, '钱露', 3, 5, { employmentType: 'temporary', feishuBindingStatus: 'unbound', passwordLoginEnabled: true }),
  seedEmployee(14, '冯博', 4, 6, { employmentType: 'temporary', status: 'disabled', feishuBindingStatus: 'unbound', passwordLoginEnabled: true }),
  seedEmployee(15, '蒋欣', 6, 7, { status: 'disabled', feishuBindingStatus: 'pending' }),
  seedEmployee(16, '韩梅', 7, 9, { status: 'resigned' }),
  seedEmployee(17, '曹宇', 9, 11, { employmentType: 'temporary', feishuBindingStatus: 'unbound', passwordLoginEnabled: true }),
  seedEmployee(18, '许宁', 10, 12, { employmentType: 'temporary', status: 'resigned', feishuBindingStatus: 'unbound', passwordLoginEnabled: true }),
  seedEmployee(19, '彭越', 2, 3),
  seedEmployee(20, '唐静', 5, 8, { feishuBindingStatus: 'pending', passwordLoginEnabled: true }),
  seedEmployee(21, '马骏', 6, 7, { employmentType: 'temporary', feishuBindingStatus: 'unbound', passwordLoginEnabled: true }),
  seedEmployee(22, '罗兰', 7, 9),
  seedEmployee(23, '谢菲', 3, 4, { status: 'disabled', feishuBindingStatus: 'pending' }),
  seedEmployee(24, '宋阳', 8, 10, { employmentType: 'temporary', feishuBindingStatus: 'unbound', passwordLoginEnabled: true })
];

function page<T>(records: T[], pageNumber: number, pageSize: number): PageResult<T> {
  return {
    records: records.slice((pageNumber - 1) * pageSize, pageNumber * pageSize),
    page: pageNumber,
    pageSize,
    total: records.length
  };
}

function includesKeyword(values: string[], keyword?: string) {
  const normalizedKeyword = keyword?.trim().toLowerCase() ?? '';
  return !normalizedKeyword || values.some((value) => value.toLowerCase().includes(normalizedKeyword));
}

function maxId(records: Array<{ id: number }>) {
  return Math.max(0, ...records.map((item) => item.id)) + 1;
}

function sameNormalized(left: string, right: string) {
  return left.trim().toLowerCase() === right.trim().toLowerCase();
}

function validateEmployee(payload: SaveEmployeePayload, positions: Position[], employees: Employee[], editingId?: number) {
  if (!payload.employeeNo.trim() || !payload.employeeName.trim() || !payload.mobile.trim()) throw new Error('请完整填写员工基本资料');
  if (!/^1\d{10}$/.test(payload.mobile)) throw new Error('请输入正确的手机号');
  if (employees.some((item) => item.id !== editingId && item.employeeNo === payload.employeeNo.trim())) throw new Error('员工工号已存在');
  if (employees.some((item) => item.id !== editingId && item.mobile === payload.mobile.trim())) throw new Error('手机号已绑定其他员工');
  if (!positions.some((item) => item.id === payload.positionId && item.departmentId === payload.departmentId && item.status === 'enabled')) throw new Error('岗位不属于所选部门');
  if (payload.employmentType === 'temporary' && !payload.passwordLoginEnabled) throw new Error('临时员工必须启用手机号和密码登录');
  if (payload.employmentType === 'temporary' && !editingId && !payload.password?.trim()) throw new Error('请设置临时员工登录密码');
}

function validateDepartment(
  payload: SaveDepartmentPayload,
  departments: Department[],
  editingId?: number
) {
  if (!payload.departmentCode.trim() || !payload.departmentName.trim()) throw new Error('请完整填写部门资料');
  if (departments.some((item) => item.id !== editingId && sameNormalized(item.departmentCode, payload.departmentCode))) {
    throw new Error('部门编码已存在');
  }
  if (departments.some((item) => item.id !== editingId && sameNormalized(item.departmentName, payload.departmentName))) {
    throw new Error('部门名称已存在');
  }
  if (payload.parentId !== null && !departments.some((item) => item.id === payload.parentId)) {
    throw new Error('上级部门不存在');
  }
  if (editingId !== undefined && payload.parentId !== null
    && collectDepartmentSubtreeIds(departments, editingId).has(payload.parentId)) {
    throw new Error('上级部门不能选择当前部门或其下级部门');
  }
}

function validatePosition(
  payload: SavePositionPayload,
  departments: Department[],
  positions: Position[],
  editingId?: number
) {
  if (!payload.positionCode.trim() || !payload.positionName.trim()) throw new Error('请完整填写岗位资料');
  if (!departments.some((item) => item.id === payload.departmentId)) throw new Error('所属部门不存在');
  if (positions.some((item) => item.id !== editingId && item.departmentId === payload.departmentId
    && sameNormalized(item.positionCode, payload.positionCode))) {
    throw new Error('岗位编码已存在');
  }
  if (positions.some((item) => item.id !== editingId && item.departmentId === payload.departmentId
    && sameNormalized(item.positionName, payload.positionName))) {
    throw new Error('同一部门内岗位名称已存在');
  }
}

function validateDepartmentCanDisable(id: number, departments: Department[], employees: Employee[]) {
  if (departments.some((item) => item.parentId === id && item.status === 'enabled')) {
    throw new Error('存在启用中的子部门，无法停用');
  }
  if (employees.some((item) => item.departmentId === id && item.status === 'active')) {
    throw new Error('部门下存在在职员工，无法停用');
  }
}

function validatePositionCanDisable(id: number, employees: Employee[]) {
  if (employees.some((item) => item.positionId === id && item.status === 'active')) {
    throw new Error('岗位已分配给在职员工，无法停用');
  }
}

function employeeFromPayload(id: number, payload: SaveEmployeePayload, current?: Employee): Employee {
  const becameFormal = current?.employmentType === 'temporary' && payload.employmentType === 'formal';
  const formalBindingStatus = becameFormal ? 'pending' : current?.feishuBindingStatus ?? 'pending';
  const feishuBindingStatus = payload.employmentType === 'temporary' ? 'unbound' : formalBindingStatus;
  return {
    id,
    employeeNo: payload.employeeNo.trim(),
    employeeName: payload.employeeName.trim(),
    mobile: payload.mobile.trim(),
    departmentId: payload.departmentId,
    positionId: payload.positionId,
    employmentType: payload.employmentType,
    status: payload.status,
    hireDate: payload.hireDate,
    feishuBindingStatus,
    feishuDisplayName: feishuBindingStatus === 'bound' ? current?.feishuDisplayName ?? '' : '',
    passwordLoginEnabled: payload.passwordLoginEnabled
  };
}

export function createMockOrganizationService(): OrganizationService {
  let departments = defaultDepartments.map((item) => ({ ...item }));
  let positions = defaultPositions.map((item) => ({ ...item }));
  let employees = defaultEmployees.map((item) => ({ ...item }));
  let nextDepartmentId = maxId(departments);
  let nextPositionId = maxId(positions);
  let nextEmployeeId = maxId(employees);

  const cloneEmployee = (employee: Employee): Employee => ({ ...employee });

  const departmentEmployeeCounts = () => employees.reduce<Record<number, number>>((counts, employee) => {
    counts[employee.departmentId] = (counts[employee.departmentId] ?? 0) + 1;
    return counts;
  }, {});

  const materializeDepartment = (department: Department): Department => ({
    ...department,
    managerName: department.managerEmployeeId === null
      ? ''
      : employees.find((employee) => employee.id === department.managerEmployeeId)?.employeeName ?? ''
  });

  const materializePosition = (position: Position): Position => ({
    ...position,
    employeeCount: employees.filter((employee) => employee.positionId === position.id).length
  });

  const departmentStatusActionDisabled = (department: Department) => department.status === 'enabled'
    && (departments.some((item) => item.parentId === department.id && item.status === 'enabled')
      || employees.some((item) => item.departmentId === department.id && item.status === 'active'));

  const materializeDepartmentListItem = (department: Department): DepartmentListItem => ({
    ...materializeDepartment(department),
    employeeCount: departmentEmployeeCounts()[department.id] ?? 0,
    childCount: departments.filter((item) => item.parentId === department.id).length,
    statusActionDisabled: departmentStatusActionDisabled(department)
  });

  return {
    async listDepartmentPage(query: DepartmentQuery) {
      const records = departments
        .filter((department) => (!query.status || department.status === query.status)
          && includesKeyword([department.departmentCode, department.departmentName], query.keyword))
        .map(materializeDepartmentListItem);
      return page(records, query.page, query.size);
    },

    async listAllDepartments() {
      return departments.map(materializeDepartment);
    },

    async getDepartmentEmployeeCounts() {
      return { ...departmentEmployeeCounts() };
    },

    async listPositions(query: PositionQuery) {
      const departmentIds = query.departmentId === undefined
        ? null
        : collectDepartmentSubtreeIds(departments, query.departmentId);
      const records = positions
        .filter((position) => (!query.status || position.status === query.status)
          && (departmentIds === null || departmentIds.has(position.departmentId))
          && includesKeyword([position.positionCode, position.positionName, position.responsibilities], query.keyword))
        .map(materializePosition);
      return page(records, query.page, query.size);
    },

    async listAllPositions() {
      return positions.map(materializePosition);
    },

    async listEmployees(query: EmployeeQuery) {
      const departmentIds = query.departmentId === undefined
        ? null
        : collectDepartmentSubtreeIds(departments, query.departmentId);
      const records = employees
        .filter((employee) => (departmentIds === null || departmentIds.has(employee.departmentId))
          && (!query.employmentType || employee.employmentType === query.employmentType)
          && (!query.status || employee.status === query.status)
          && includesKeyword([employee.employeeNo, employee.employeeName, employee.mobile], query.keyword))
        .map(cloneEmployee);
      return page(records, query.page, query.size);
    },

    async listAllEmployees() {
      return employees.map(cloneEmployee);
    },

    async getSummary(): Promise<OrganizationSummary> {
      return {
        formalEmployees: employees.filter((employee) => employee.employmentType === 'formal').length,
        temporaryEmployees: employees.filter((employee) => employee.employmentType === 'temporary').length,
        pendingFeishuBindings: employees.filter((employee) => employee.feishuBindingStatus === 'pending').length,
        disabledAccounts: employees.filter((employee) => employee.status === 'disabled').length
      };
    },

    async getEmployee(id: number) {
      const employee = employees.find((item) => item.id === id);
      if (!employee) throw new Error('员工不存在');
      return cloneEmployee(employee);
    },

    async createEmployee(payload: SaveEmployeePayload) {
      validateEmployee(payload, positions, employees);
      const employee = employeeFromPayload(nextEmployeeId++, payload);
      employees = [...employees, employee];
      return cloneEmployee(employee);
    },

    async updateEmployee(id: number, payload: SaveEmployeePayload) {
      const current = employees.find((item) => item.id === id);
      if (!current) throw new Error('员工不存在');
      validateEmployee(payload, positions, employees, id);
      const employee = employeeFromPayload(id, payload, current);
      employees = employees.map((item) => item.id === id ? employee : item);
      return cloneEmployee(employee);
    },

    async changeEmployeeStatus(id, status) {
      const current = employees.find((item) => item.id === id);
      if (!current) throw new Error('员工不存在');
      const employee = { ...current, status };
      employees = employees.map((item) => item.id === id ? employee : item);
      return cloneEmployee(employee);
    },

    async createDepartment(payload: SaveDepartmentPayload) {
      validateDepartment(payload, departments);
      const department: Department = {
        ...payload,
        id: nextDepartmentId++,
        departmentCode: payload.departmentCode.trim(),
        departmentName: payload.departmentName.trim(),
        managerName: ''
      };
      departments = [...departments, department];
      return materializeDepartment(department);
    },

    async updateDepartment(id: number, payload: SaveDepartmentPayload) {
      const current = departments.find((item) => item.id === id);
      if (!current) throw new Error('部门不存在');
      validateDepartment(payload, departments, id);
      if (payload.status === 'disabled') validateDepartmentCanDisable(id, departments, employees);
      const department: Department = {
        ...payload,
        id,
        departmentCode: payload.departmentCode.trim(),
        departmentName: payload.departmentName.trim(),
        managerName: ''
      };
      departments = departments.map((item) => item.id === id ? department : item);
      return materializeDepartment(department);
    },

    async changeDepartmentStatus(id, status) {
      const current = departments.find((item) => item.id === id);
      if (!current) throw new Error('部门不存在');
      if (status === 'disabled') validateDepartmentCanDisable(id, departments, employees);
      const department = { ...current, status };
      departments = departments.map((item) => item.id === id ? department : item);
      return materializeDepartment(department);
    },

    async createPosition(payload: SavePositionPayload) {
      validatePosition(payload, departments, positions);
      const position: Position = {
        ...payload,
        id: nextPositionId++,
        positionCode: payload.positionCode.trim(),
        positionName: payload.positionName.trim(),
        responsibilities: payload.responsibilities.trim(),
        employeeCount: 0
      };
      positions = [...positions, position];
      return materializePosition(position);
    },

    async updatePosition(id: number, payload: SavePositionPayload) {
      const current = positions.find((item) => item.id === id);
      if (!current) throw new Error('岗位不存在');
      validatePosition(payload, departments, positions, id);
      if (payload.departmentId !== current.departmentId
        && employees.some((item) => item.positionId === id)) {
        throw new Error('该岗位已有员工，不能调整所属部门');
      }
      if (payload.status === 'disabled') validatePositionCanDisable(id, employees);
      const position: Position = {
        ...payload,
        id,
        positionCode: payload.positionCode.trim(),
        positionName: payload.positionName.trim(),
        responsibilities: payload.responsibilities.trim(),
        employeeCount: 0
      };
      positions = positions.map((item) => item.id === id ? position : item);
      return materializePosition(position);
    },

    async changePositionStatus(id, status) {
      const current = positions.find((item) => item.id === id);
      if (!current) throw new Error('岗位不存在');
      if (status === 'disabled') validatePositionCanDisable(id, employees);
      const position = { ...current, status };
      positions = positions.map((item) => item.id === id ? position : item);
      return materializePosition(position);
    }
  };
}
