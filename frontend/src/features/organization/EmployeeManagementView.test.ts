import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmployeeFormDrawer from './components/EmployeeFormDrawer.vue';
import { createMockOrganizationService } from './mockOrganizationService';
import type { OrganizationService } from './organizationService';
import type { Department, Employee, Position, SaveEmployeePayload } from './types';
import EmployeeManagementView from './views/EmployeeManagementView.vue';

const departments: Department[] = [
  { id: 1, departmentCode: 'HQ', departmentName: '总部', parentId: null, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PRODUCT', departmentName: '产品中心', parentId: 1, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' },
  { id: 3, departmentCode: 'RD', departmentName: '研发组', parentId: 2, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' }
];

const positions: Position[] = [
  { id: 1, positionCode: 'CEO', positionName: '总经理', departmentId: 1, responsibilities: '', status: 'enabled', employeeCount: 1 },
  { id: 2, positionCode: 'PM', positionName: '产品经理', departmentId: 2, responsibilities: '', status: 'enabled', employeeCount: 1 },
  { id: 3, positionCode: 'FE', positionName: '前端工程师', departmentId: 3, responsibilities: '', status: 'enabled', employeeCount: 1 },
  { id: 4, positionCode: 'OLD', positionName: '停用岗位', departmentId: 3, responsibilities: '', status: 'disabled', employeeCount: 0 }
];

const employee: Employee = {
  id: 7,
  employeeNo: 'EMP0007',
  employeeName: '张敏',
  mobile: '13800138000',
  departmentId: 2,
  positionId: 2,
  employmentType: 'formal',
  status: 'active',
  hireDate: '2024-07-08',
  feishuBindingStatus: 'bound',
  feishuDisplayName: '张敏',
  passwordLoginEnabled: false
};

let activeWrapper: VueWrapper | undefined;

afterEach(() => {
  activeWrapper?.unmount();
  activeWrapper = undefined;
});

async function mountPage(service: OrganizationService = createMockOrganizationService()) {
  const wrapper = mount(EmployeeManagementView, {
    attachTo: document.body,
    global: { provide: { organizationService: service } }
  });
  activeWrapper = wrapper;
  await flushPromises();
  return wrapper;
}

function mountDrawer(overrides: Partial<InstanceType<typeof EmployeeFormDrawer>['$props']> = {}) {
  const wrapper = mount(EmployeeFormDrawer, {
    attachTo: document.body,
    props: {
      mode: 'create',
      employee: null,
      departments,
      positions,
      saving: false,
      error: '',
      ...overrides
    }
  });
  activeWrapper = wrapper;
  return wrapper;
}

describe('EmployeeManagementView', () => {
  it('loads summary cards and filters by the selected department subtree', async () => {
    const service = createMockOrganizationService();
    const listEmployees = vi.spyOn(service, 'listEmployees');
    const wrapper = await mountPage(service);

    expect(wrapper.get('[data-testid="formal-employee-count"]').text()).toMatch(/\d+/);
    expect(wrapper.get('[data-testid="temporary-employee-count"]').text()).toMatch(/\d+/);
    await wrapper.get('[data-testid="department-node-2"]').trigger('click');
    await flushPromises();

    expect(listEmployees).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: 2, page: 1 }));
    expect(wrapper.get('[data-testid="employee-list-title"]').text()).toContain('产品中心');
  });

  it('resets paging when filters change and renders an empty result', async () => {
    const service = createMockOrganizationService();
    const listEmployees = vi.spyOn(service, 'listEmployees');
    const wrapper = await mountPage(service);

    await wrapper.get('[data-testid="page-2"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="employee-keyword"]').setValue('不存在的员工');
    await wrapper.get('[data-testid="employee-search"]').trigger('click');
    await flushPromises();

    expect(listEmployees).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '不存在的员工', page: 1 }));
    expect(wrapper.get('[data-testid="employee-empty"]').text()).toContain('暂无匹配员工');
  });

  it('shows loading and a readable service error', async () => {
    let rejectRequest: ((error: Error) => void) | undefined;
    const pending = new Promise<never>((_resolve, reject) => { rejectRequest = reject; });
    const service = createMockOrganizationService();
    vi.spyOn(service, 'listEmployees').mockReturnValue(pending);
    const wrapper = mount(EmployeeManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;

    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(true);
    rejectRequest?.(new Error('员工数据加载失败'));
    await flushPromises();

    expect(wrapper.get('[data-testid="employee-error"]').text()).toContain('员工数据加载失败');
  });

  it('creates, edits, views and cancels through the employee drawer', async () => {
    const service = createMockOrganizationService();
    const createEmployee = vi.spyOn(service, 'createEmployee');
    const updateEmployee = vi.spyOn(service, 'updateEmployee');
    const wrapper = await mountPage(service);

    await wrapper.get('[data-testid="add-employee"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('create');
    await wrapper.get('[data-testid="employee-name"]').setValue('测试员工');
    await wrapper.get('[data-testid="employee-mobile"]').setValue('13900139000');
    await wrapper.get('[data-testid="employee-number"]').setValue('EMP0999');
    await wrapper.get('[data-testid="employee-department"]').setValue('2');
    await wrapper.get('[data-testid="employee-position"]').setValue('2');
    await wrapper.get('[data-testid="employee-hire-date"]').setValue('2026-09-03');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    await flushPromises();
    expect(createEmployee).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-testid="employee-drawer"]').exists()).toBe(false);

    await wrapper.get('[data-testid^="edit-employee-"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('edit');
    expect(wrapper.find('[data-testid="employee-password"]').exists()).toBe(false);
    await wrapper.get('[data-testid="password-login-enabled"]').setValue(true);
    expect((wrapper.get('[data-testid="employee-password"]').element as HTMLInputElement).value).toBe('');
    await wrapper.get('[data-testid="employee-name"]').setValue('编辑后的员工');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    await flushPromises();
    expect(updateEmployee).toHaveBeenCalledOnce();

    await wrapper.get('[data-testid^="view-employee-"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('view');
    expect(wrapper.find('[data-testid="employee-password"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="employee-name"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="edit-from-view"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('edit');
    await wrapper.get('[data-testid="cancel-employee"]').trigger('click');
    expect(wrapper.find('[data-testid="employee-drawer"]').exists()).toBe(false);
  });

  it('keeps form values visible when saving fails', async () => {
    const service = createMockOrganizationService();
    vi.spyOn(service, 'createEmployee').mockRejectedValue(new Error('手机号已绑定其他员工'));
    const wrapper = await mountPage(service);

    await wrapper.get('[data-testid="add-employee"]').trigger('click');
    await wrapper.get('[data-testid="employee-name"]').setValue('重复手机号员工');
    await wrapper.get('[data-testid="employee-mobile"]').setValue('13900139000');
    await wrapper.get('[data-testid="employee-number"]').setValue('EMP0998');
    await wrapper.get('[data-testid="employee-department"]').setValue('2');
    await wrapper.get('[data-testid="employee-position"]').setValue('2');
    await wrapper.get('[data-testid="employee-hire-date"]').setValue('2026-09-03');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('手机号已绑定其他员工');
    expect((wrapper.get('[data-testid="employee-name"]').element as HTMLInputElement).value).toBe('重复手机号员工');
  });
});

describe('EmployeeFormDrawer', () => {
  it('validates temporary account rules before emitting save', async () => {
    const wrapper = mountDrawer();

    await wrapper.get('[data-testid="employment-temporary"]').setValue(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.text()).toContain('临时员工必须启用手机号和密码登录');
    expect(wrapper.emitted('save')).toBeUndefined();
  });

  it('filters enabled positions when department changes', async () => {
    const wrapper = mountDrawer();

    await wrapper.get('[data-testid="employee-department"]').setValue('3');
    const options = wrapper.get('[data-testid="employee-position"]').findAll('option').filter((option) => option.attributes('value'));

    expect(options).toHaveLength(1);
    expect(options.every((option) => option.attributes('data-department-id') === '3')).toBe(true);
    expect(options[0].text()).toBe('前端工程师');
  });

  it('emits a password only from a create or reset submission', async () => {
    const wrapper = mountDrawer();
    await wrapper.get('[data-testid="employee-name"]').setValue('临时员工');
    await wrapper.get('[data-testid="employee-mobile"]').setValue('13900139001');
    await wrapper.get('[data-testid="employee-number"]').setValue('TMP0001');
    await wrapper.get('[data-testid="employee-department"]').setValue('3');
    await wrapper.get('[data-testid="employee-position"]').setValue('3');
    await wrapper.get('[data-testid="employee-hire-date"]').setValue('2026-09-03');
    await wrapper.get('[data-testid="employment-temporary"]').setValue(true);
    await wrapper.get('[data-testid="employee-password"]').setValue('Secret123');
    await wrapper.get('[data-testid="employee-password-confirm"]').setValue('Secret123');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    const payload = wrapper.emitted<SaveEmployeePayload[]>('save')?.[0]?.[0];
    expect(payload?.password).toBe('Secret123');
    expect(payload?.passwordLoginEnabled).toBe(true);
    expect(payload).not.toHaveProperty('feishuDisplayName');
  });
});
