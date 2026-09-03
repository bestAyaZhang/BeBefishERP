import { beforeEach, describe, expect, it } from 'vitest';
import { createMockOrganizationService } from './mockOrganizationService';
import type { Employee, SaveDepartmentPayload, SaveEmployeePayload, SavePositionPayload } from './types';

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

  it('requires a password whenever password login is enabled for a new employee', async () => {
    await expect(service.createEmployee(validEmployee({ passwordLoginEnabled: true })))
      .rejects.toThrow('启用手机号和密码登录时必须设置密码');

    const created = await service.createEmployee(validEmployee({
      passwordLoginEnabled: true,
      password: 'Formal@123456'
    }));

    expect(created.passwordLoginEnabled).toBe(true);
    expect(created).not.toHaveProperty('password');
  });

  it('requires a new password only when an edit establishes password login', async () => {
    const formalEmployee = (await service.listAllEmployees()).find((item) => (
      item.employmentType === 'formal' && !item.passwordLoginEnabled
    ))!;

    await expect(service.updateEmployee(formalEmployee.id, employeePayload(formalEmployee, {
      passwordLoginEnabled: true
    }))).rejects.toThrow('启用手机号和密码登录时必须设置密码');

    const enabled = await service.updateEmployee(formalEmployee.id, employeePayload(formalEmployee, {
      passwordLoginEnabled: true,
      password: 'Formal@123456'
    }));
    const renamed = await service.updateEmployee(formalEmployee.id, employeePayload(enabled, {
      employeeName: '保留密码账号'
    }));

    expect(renamed).toMatchObject({ employeeName: '保留密码账号', passwordLoginEnabled: true });
    expect(renamed).not.toHaveProperty('password');
  });

  it('requires a password when transitioning to temporary unless the temporary account is established', async () => {
    const formalEmployee = (await service.listAllEmployees()).find((item) => item.id === 20)!;

    await expect(service.updateEmployee(formalEmployee.id, employeePayload(formalEmployee, {
      employmentType: 'temporary',
      passwordLoginEnabled: true
    }))).rejects.toThrow('转为临时员工时必须设置密码');

    const temporaryEmployee = (await service.listAllEmployees()).find((item) => (
      item.employmentType === 'temporary' && item.passwordLoginEnabled
    ))!;
    const updated = await service.updateEmployee(temporaryEmployee.id, employeePayload(temporaryEmployee, {
      employeeName: '临时员工免重置'
    }));

    expect(updated).toMatchObject({
      employeeName: '临时员工免重置',
      employmentType: 'temporary',
      passwordLoginEnabled: true
    });
  });

  it('rejects a position outside the selected department', async () => {
    await expect(service.createEmployee({
      employeeNo: 'E-099', employeeName: '错配员工', mobile: '13900000011', departmentId: 2, positionId: 8,
      employmentType: 'formal', status: 'active', hireDate: '2026-09-03', passwordLoginEnabled: false
    })).rejects.toThrow('岗位不属于所选部门');
  });

  it('requires enabled departments and positions when creating or updating employees', async () => {
    await expect(service.createEmployee(validEmployee({
      departmentId: 11,
      positionId: 13
    }))).rejects.toThrow('所属部门未启用');

    await service.changeDepartmentStatus(11, 'enabled');
    await expect(service.createEmployee(validEmployee({
      departmentId: 11,
      positionId: 13
    }))).rejects.toThrow('所属岗位未启用');

    const existing = (await service.listAllEmployees())[0];
    await expect(service.updateEmployee(existing.id, employeePayload(existing, {
      departmentId: 11,
      positionId: 13
    }))).rejects.toThrow('所属岗位未启用');
  });

  it('revalidates assignment hierarchy before reactivating a disabled employee', async () => {
    const department = await service.createDepartment(validDepartment({
      departmentCode: 'REACTIVATE',
      departmentName: '重新启用测试部'
    }));
    const position = await service.createPosition(validPosition({
      positionCode: 'REACTIVATE-ROLE',
      positionName: '重新启用测试岗',
      departmentId: department.id
    }));
    const created = await service.createEmployee(validEmployee({
      employeeNo: 'E-REACTIVATE',
      mobile: '13900000119',
      departmentId: department.id,
      positionId: position.id
    }));
    await service.changeEmployeeStatus(created.id, 'disabled');
    await service.changePositionStatus(position.id, 'disabled');
    await service.changeDepartmentStatus(department.id, 'disabled');

    await expect(service.changeEmployeeStatus(created.id, 'active'))
      .rejects.toThrow('所属部门未启用');

    await service.changeDepartmentStatus(department.id, 'enabled');
    await expect(service.changeEmployeeStatus(created.id, 'active'))
      .rejects.toThrow('所属岗位未启用');
  });

  it('paginates department and position management queries', async () => {
    const departments = await service.listDepartmentPage({ page: 2, size: 3, status: 'enabled' });
    const positions = await service.listPositions({ page: 2, size: 4, status: 'enabled' });

    expect(departments).toMatchObject({ page: 2, pageSize: 3 });
    expect(departments.records).toHaveLength(3);
    expect(departments.total).toBeGreaterThan(8);
    expect(positions).toMatchObject({ page: 2, pageSize: 4 });
    expect(positions.records).toHaveLength(4);
    expect(positions.total).toBeGreaterThanOrEqual(10);
  });

  it('filters positions by the complete selected department subtree', async () => {
    const page = await service.listPositions({ page: 1, size: 20, departmentId: 2 });
    const departmentIds = new Set(page.records.map((item) => item.departmentId));

    expect(departmentIds.has(2)).toBe(true);
    expect(departmentIds.has(3)).toBe(true);
    expect(departmentIds.has(4)).toBe(true);
    expect(page.records.some((item) => item.positionName === '后端工程师')).toBe(true);
    expect(page.records.some((item) => item.positionName === '产品设计师')).toBe(true);
    expect(page.records.every((item) => [2, 3, 4, 11].includes(item.departmentId))).toBe(true);
  });

  it('provides stable unpaged employee options and isolates service instances', async () => {
    const other = createMockOrganizationService();
    const initialOptions = await service.listAllEmployees();

    await service.createEmployee(validEmployee({ employeeNo: 'E-101', mobile: '13900000101' }));
    initialOptions[0].employeeName = '外部修改';

    expect(await service.listAllEmployees()).toHaveLength(initialOptions.length + 1);
    expect((await service.getEmployee(initialOptions[0].id)).employeeName).not.toBe('外部修改');
    expect(await other.listAllEmployees()).toHaveLength(initialOptions.length);
  });

  it('validates employee basics, uniqueness and temporary passwords', async () => {
    const existing = (await service.listAllEmployees())[0];

    await expect(service.createEmployee(validEmployee({ employeeName: ' ' })))
      .rejects.toThrow('请完整填写员工基本资料');
    await expect(service.createEmployee(validEmployee({ mobile: '123' })))
      .rejects.toThrow('请输入正确的手机号');
    await expect(service.createEmployee(validEmployee({ employeeNo: existing.employeeNo })))
      .rejects.toThrow('员工工号已存在');
    await expect(service.createEmployee(validEmployee({ mobile: existing.mobile })))
      .rejects.toThrow('手机号已绑定其他员工');
    await expect(service.createEmployee(validEmployee({
      employeeNo: 'TMP-011',
      mobile: '13900000012',
      employmentType: 'temporary',
      passwordLoginEnabled: true
    }))).rejects.toThrow('请设置临时员工登录密码');
  });

  it('updates an employee without requiring a replacement password and resets Feishu binding when made formal', async () => {
    const created = await service.createEmployee(validEmployee({
      employeeNo: 'TMP-012',
      mobile: '13900000013',
      employmentType: 'temporary',
      passwordLoginEnabled: true,
      password: 'Temp@123456'
    }));

    const updated = await service.updateEmployee(created.id, {
      employeeNo: created.employeeNo,
      employeeName: '转正员工',
      mobile: created.mobile,
      departmentId: created.departmentId,
      positionId: created.positionId,
      employmentType: 'formal',
      status: created.status,
      hireDate: created.hireDate,
      passwordLoginEnabled: false
    });

    expect(updated).toMatchObject({
      id: created.id,
      employeeName: '转正员工',
      employmentType: 'formal',
      feishuBindingStatus: 'pending',
      feishuDisplayName: ''
    });
    expect(updated).not.toHaveProperty('password');
  });

  it('derives summary, employee counts, manager names and position counts from current employees', async () => {
    const beforeEmployees = await service.listAllEmployees();
    const beforeSummary = await service.getSummary();
    const beforeDepartmentCounts = await service.getDepartmentEmployeeCounts();
    const beforePosition = (await service.listAllPositions()).find((item) => item.id === 3)!;

    const created = await service.createEmployee(validEmployee({ employeeNo: 'E-102', mobile: '13900000102' }));
    const department = await service.createDepartment(validDepartment({
      departmentCode: 'MGR',
      departmentName: '负责人测试部',
      managerEmployeeId: created.id
    }));

    expect(await service.getSummary()).toEqual({
      ...beforeSummary,
      formalEmployees: beforeSummary.formalEmployees + 1,
      pendingFeishuBindings: beforeSummary.pendingFeishuBindings + 1
    });
    expect((await service.getDepartmentEmployeeCounts())[2]).toBe((beforeDepartmentCounts[2] ?? 0) + 1);
    expect((await service.listAllPositions()).find((item) => item.id === 3)!.employeeCount)
      .toBe(beforePosition.employeeCount + 1);
    expect((await service.listAllDepartments()).find((item) => item.id === department.id)!.managerName)
      .toBe(created.employeeName);
    expect(beforeSummary.formalEmployees + beforeSummary.temporaryEmployees).toBe(beforeEmployees.length);
  });

  it('rejects duplicate departments and descendant parent assignments', async () => {
    await expect(service.createDepartment(validDepartment({ departmentCode: 'PD', departmentName: '新部门' })))
      .rejects.toThrow('部门编码已存在');
    await expect(service.createDepartment(validDepartment({ departmentCode: 'NEW', departmentName: '产品中心' })))
      .rejects.toThrow('部门名称已存在');

    const productCenter = (await service.listAllDepartments()).find((item) => item.id === 2)!;
    await expect(service.updateDepartment(2, {
      departmentCode: productCenter.departmentCode,
      departmentName: productCenter.departmentName,
      parentId: 3,
      managerEmployeeId: productCenter.managerEmployeeId,
      sortOrder: productCenter.sortOrder,
      status: productCenter.status
    })).rejects.toThrow('上级部门不能选择当前部门或其下级部门');
  });

  it('enforces position uniqueness within a department but permits the same name elsewhere', async () => {
    await expect(service.createPosition(validPosition({ positionCode: 'PM', positionName: '新岗位' })))
      .rejects.toThrow('岗位编码已存在');
    await expect(service.createPosition(validPosition({ positionCode: 'NEW-PM', positionName: '产品经理' })))
      .rejects.toThrow('同一部门内岗位名称已存在');

    const created = await service.createPosition(validPosition({
      positionCode: 'PM',
      positionName: '产品经理',
      departmentId: 5
    }));
    expect(created).toMatchObject({ positionCode: 'PM', positionName: '产品经理', departmentId: 5 });
  });

  it('requires an enabled department when saving or enabling an enabled position', async () => {
    await expect(service.createPosition(validPosition({
      positionCode: 'ARCHIVE-ENABLED',
      positionName: '归档启用岗位',
      departmentId: 11
    }))).rejects.toThrow('启用岗位必须属于启用部门');

    const disabledPosition = await service.createPosition(validPosition({
      positionCode: 'ARCHIVE-DISABLED',
      positionName: '归档停用岗位',
      departmentId: 11,
      status: 'disabled'
    }));
    await expect(service.updatePosition(disabledPosition.id, {
      positionCode: disabledPosition.positionCode,
      positionName: disabledPosition.positionName,
      departmentId: disabledPosition.departmentId,
      responsibilities: disabledPosition.responsibilities,
      status: 'enabled'
    })).rejects.toThrow('启用岗位必须属于启用部门');
    await expect(service.changePositionStatus(13, 'enabled'))
      .rejects.toThrow('启用岗位必须属于启用部门');
  });

  it('requires an enabled parent when saving or enabling an enabled child department', async () => {
    await expect(service.createDepartment(validDepartment({
      departmentCode: 'ARCHIVE-CHILD',
      departmentName: '归档子部门',
      parentId: 11
    }))).rejects.toThrow('启用部门的上级部门必须已启用');

    const disabledChild = await service.createDepartment(validDepartment({
      departmentCode: 'ARCHIVE-DISABLED-CHILD',
      departmentName: '归档停用子部门',
      parentId: 11,
      status: 'disabled'
    }));
    await expect(service.updateDepartment(disabledChild.id, {
      departmentCode: disabledChild.departmentCode,
      departmentName: disabledChild.departmentName,
      parentId: disabledChild.parentId,
      managerEmployeeId: disabledChild.managerEmployeeId,
      sortOrder: disabledChild.sortOrder,
      status: 'enabled'
    })).rejects.toThrow('启用部门的上级部门必须已启用');
    await expect(service.changeDepartmentStatus(disabledChild.id, 'enabled'))
      .rejects.toThrow('启用部门的上级部门必须已启用');
  });

  it('prevents disabling departments with enabled children or active employees', async () => {
    await expect(service.changeDepartmentStatus(1, 'disabled'))
      .rejects.toThrow('存在启用中的子部门，无法停用');
    await expect(service.changeDepartmentStatus(4, 'disabled'))
      .rejects.toThrow('部门下存在在职员工，无法停用');
  });

  it('prevents disabling a department whose subtree contains an enabled position', async () => {
    const department = await service.createDepartment(validDepartment({
      departmentCode: 'POSITION-HOLDER',
      departmentName: '岗位占用部门'
    }));
    await service.createPosition(validPosition({
      positionCode: 'POSITION-HOLDER-ROLE',
      positionName: '岗位占用角色',
      departmentId: department.id
    }));

    await expect(service.changeDepartmentStatus(department.id, 'disabled'))
      .rejects.toThrow('部门或下级部门存在启用岗位，无法停用');
    await expect(service.updateDepartment(department.id, {
      departmentCode: department.departmentCode,
      departmentName: department.departmentName,
      parentId: department.parentId,
      managerEmployeeId: department.managerEmployeeId,
      sortOrder: department.sortOrder,
      status: 'disabled'
    })).rejects.toThrow('部门或下级部门存在启用岗位，无法停用');
  });

  it('prevents disabling a position assigned to active employees', async () => {
    await expect(service.changePositionStatus(3, 'disabled'))
      .rejects.toThrow('岗位已分配给在职员工，无法停用');
  });

  it('applies department disable guards during a full update', async () => {
    const root = (await service.listAllDepartments()).find((item) => item.id === 1)!;

    await expect(service.updateDepartment(root.id, {
      departmentCode: root.departmentCode,
      departmentName: root.departmentName,
      parentId: root.parentId,
      managerEmployeeId: root.managerEmployeeId,
      sortOrder: root.sortOrder,
      status: 'disabled'
    })).rejects.toThrow('存在启用中的子部门，无法停用');
  });

  it('applies position disable guards during a full update', async () => {
    const productManager = (await service.listAllPositions()).find((item) => item.id === 3)!;

    await expect(service.updatePosition(productManager.id, {
      positionCode: productManager.positionCode,
      positionName: productManager.positionName,
      departmentId: productManager.departmentId,
      responsibilities: productManager.responsibilities,
      status: 'disabled'
    })).rejects.toThrow('岗位已分配给在职员工，无法停用');
  });

  it('rejects moving an occupied position and preserves its department', async () => {
    const productManager = (await service.listAllPositions()).find((item) => item.id === 3)!;

    await expect(service.updatePosition(productManager.id, {
      positionCode: productManager.positionCode,
      positionName: productManager.positionName,
      departmentId: 5,
      responsibilities: productManager.responsibilities,
      status: productManager.status
    })).rejects.toThrow('该岗位已有员工，不能调整所属部门');
    expect((await service.listAllPositions()).find((item) => item.id === productManager.id))
      .toEqual(productManager);
  });
});

function validEmployee(overrides: Partial<SaveEmployeePayload> = {}): SaveEmployeePayload {
  return {
    employeeNo: 'E-100',
    employeeName: '测试员工',
    mobile: '13900000100',
    departmentId: 2,
    positionId: 3,
    employmentType: 'formal',
    status: 'active',
    hireDate: '2026-09-03',
    passwordLoginEnabled: false,
    ...overrides
  };
}

function employeePayload(employee: Employee, overrides: Partial<SaveEmployeePayload> = {}): SaveEmployeePayload {
  return {
    employeeNo: employee.employeeNo,
    employeeName: employee.employeeName,
    mobile: employee.mobile,
    departmentId: employee.departmentId,
    positionId: employee.positionId,
    employmentType: employee.employmentType,
    status: employee.status,
    hireDate: employee.hireDate,
    passwordLoginEnabled: employee.passwordLoginEnabled,
    ...overrides
  };
}

function validDepartment(overrides: Partial<SaveDepartmentPayload> = {}): SaveDepartmentPayload {
  return {
    departmentCode: 'NEW-DEPT',
    departmentName: '新部门',
    parentId: 1,
    managerEmployeeId: null,
    sortOrder: 99,
    status: 'enabled',
    ...overrides
  };
}

function validPosition(overrides: Partial<SavePositionPayload> = {}): SavePositionPayload {
  return {
    positionCode: 'NEW-POS',
    positionName: '新岗位',
    departmentId: 2,
    responsibilities: '负责测试岗位职责',
    status: 'enabled',
    ...overrides
  };
}
