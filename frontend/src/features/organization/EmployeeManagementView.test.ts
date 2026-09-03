import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EmployeeFormDrawer from './components/EmployeeFormDrawer.vue';
import { createMockOrganizationService } from './mockOrganizationService';
import type { OrganizationService } from './organizationService';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import type { Department, Employee, PageResult, Position, SaveEmployeePayload } from './types';
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

function setPermissions(permissions: string[]) {
  saveCurrentUser({
    accessToken: 'organization-test-token',
    mobile: '13800138000',
    roles: ['ADMIN'],
    permissions,
    loginMethod: 'password'
  });
}

beforeEach(() => {
  setPermissions(['organization:view', 'organization:manage']);
});

afterEach(() => {
  activeWrapper?.unmount();
  activeWrapper = undefined;
  clearCurrentUser();
  vi.useRealTimers();
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

  it('applies employment and status filters and resets each query to page one', async () => {
    const service = createMockOrganizationService();
    const listEmployees = vi.spyOn(service, 'listEmployees');
    const wrapper = await mountPage(service);

    await wrapper.get('[data-testid="page-2"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="employee-employment-filter"]').setValue('temporary');
    await flushPromises();
    expect(listEmployees).toHaveBeenLastCalledWith(expect.objectContaining({ employmentType: 'temporary', page: 1 }));

    await wrapper.get('[data-testid="employee-employment-filter"]').setValue('');
    await flushPromises();
    await wrapper.get('[data-testid="page-2"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="employee-status-filter"]').setValue('disabled');
    await flushPromises();
    expect(listEmployees).toHaveBeenLastCalledWith(expect.objectContaining({ employmentType: undefined, status: 'disabled', page: 1 }));
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

  it('keeps the newest employee request authoritative while stale requests finish around it', async () => {
    const service = createMockOrganizationService();
    const initialRequest = deferred<PageResult<Employee>>();
    const productRequest = deferred<PageResult<Employee>>();
    const researchRequest = deferred<PageResult<Employee>>();
    vi.spyOn(service, 'listEmployees')
      .mockReturnValueOnce(initialRequest.promise)
      .mockReturnValueOnce(productRequest.promise)
      .mockReturnValueOnce(researchRequest.promise);
    const wrapper = mount(EmployeeManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;
    await flushPromises();

    await wrapper.get('[data-testid="department-node-2"]').trigger('click');
    await wrapper.get('[data-testid="department-node-3"]').trigger('click');
    productRequest.resolve(employeePage({ ...employee, id: 201, employeeName: '过期产品员工' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(true);

    researchRequest.resolve(employeePage({ ...employee, id: 202, departmentId: 3, positionId: 4, employeeName: '最新研发员工' }));
    await flushPromises();
    expect(wrapper.text()).toContain('最新研发员工');
    expect(wrapper.text()).not.toContain('过期产品员工');

    initialRequest.resolve(employeePage({ ...employee, id: 203, employeeName: '最旧全公司员工' }));
    await flushPromises();
    expect(wrapper.text()).toContain('最新研发员工');
    expect(wrapper.text()).not.toContain('最旧全公司员工');
  });

  it('ignores stale initial-reference success while a newer employee query is pending', async () => {
    const service = createMockOrganizationService();
    const departmentItems = await service.listAllDepartments();
    const initialReferences = deferred<Department[]>();
    const latestRequest = deferred<PageResult<Employee>>();
    const stalePage = employeePage({ ...employee, id: 211, employeeName: '过期初始化员工' });
    const latestPage = employeePage({ ...employee, id: 212, employeeName: '最新查询员工' });
    vi.spyOn(service, 'listAllDepartments').mockReturnValueOnce(initialReferences.promise);
    const listEmployees = vi.spyOn(service, 'listEmployees')
      .mockReturnValueOnce(latestRequest.promise)
      .mockResolvedValueOnce(stalePage);
    const wrapper = mount(EmployeeManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;
    await nextTick();

    await wrapper.get('[data-testid="employee-keyword"]').setValue('最新查询员工');
    await wrapper.get('[data-testid="employee-search"]').trigger('click');
    initialReferences.resolve(departmentItems);
    await flushPromises();

    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('过期初始化员工');
    expect(listEmployees).toHaveBeenCalledOnce();

    latestRequest.resolve(latestPage);
    await flushPromises();
    expect(wrapper.text()).toContain('最新查询员工');
    expect(wrapper.find('[data-testid="employee-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(false);
  });

  it('ignores stale initial-reference failure while a newer employee query is pending', async () => {
    const service = createMockOrganizationService();
    const initialReferences = deferred<Department[]>();
    const latestRequest = deferred<PageResult<Employee>>();
    const latestPage = employeePage({ ...employee, id: 213, employeeName: '失败后最新员工' });
    vi.spyOn(service, 'listAllDepartments').mockReturnValueOnce(initialReferences.promise);
    vi.spyOn(service, 'listEmployees').mockReturnValueOnce(latestRequest.promise);
    const wrapper = mount(EmployeeManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;
    await nextTick();

    await wrapper.get('[data-testid="employee-keyword"]').setValue('失败后最新员工');
    await wrapper.get('[data-testid="employee-search"]').trigger('click');
    initialReferences.reject(new Error('过期初始化失败'));
    await flushPromises();

    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="employee-error"]').exists()).toBe(false);

    latestRequest.resolve(latestPage);
    await flushPromises();
    expect(wrapper.text()).toContain('失败后最新员工');
    expect(wrapper.find('[data-testid="employee-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="employee-loading"]').exists()).toBe(false);
  });

  it('supports row and select-all employee selection and clears stale selections on new records', async () => {
    const wrapper = await mountPage();
    const selectAll = wrapper.get('[data-testid="select-all-employees"]');
    const firstRow = wrapper.findAll('[data-testid^="select-employee-"]')[0];

    await firstRow.setValue(true);
    expect((firstRow.element as HTMLInputElement).checked).toBe(true);
    expect((selectAll.element as HTMLInputElement).indeterminate).toBe(true);

    await selectAll.setValue(true);
    expect(wrapper.findAll('[data-testid^="select-employee-"]')
      .every((item) => (item.element as HTMLInputElement).checked)).toBe(true);
    await selectAll.setValue(false);
    await firstRow.setValue(true);

    await wrapper.get('[data-testid="employee-keyword"]').setValue('张敏');
    await wrapper.get('[data-testid="employee-search"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-testid^="select-employee-"]')
      .every((item) => !(item.element as HTMLInputElement).checked)).toBe(true);
    expect((wrapper.get('[data-testid="select-all-employees"]').element as HTMLInputElement).indeterminate).toBe(false);
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
    await wrapper.get('[data-testid="employee-password"]').setValue('Formal@123456');
    await wrapper.get('[data-testid="employee-password-confirm"]').setValue('Formal@123456');
    await wrapper.get('[data-testid="employee-name"]').setValue('编辑后的员工');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    await flushPromises();
    expect(updateEmployee).toHaveBeenCalledOnce();

    await wrapper.get('[data-testid^="view-employee-"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('view');
    expect(wrapper.find('[data-testid="employee-password"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="employee-name"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="edit-from-view"]').trigger('click');
    await nextTick();
    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('edit');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="employee-name"]').element);
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

  it('closes the drawer and reports that save succeeded when the following refresh fails', async () => {
    const service = createMockOrganizationService();
    const initialResult = await service.listEmployees({ page: 1, size: 20 });
    const listEmployees = vi.spyOn(service, 'listEmployees');
    listEmployees.mockResolvedValueOnce(initialResult);
    listEmployees.mockRejectedValueOnce(new Error('列表服务暂不可用'));
    const wrapper = await mountPage(service);

    await wrapper.get('[data-testid="add-employee"]').trigger('click');
    await wrapper.get('[data-testid="employee-name"]').setValue('刷新失败员工');
    await wrapper.get('[data-testid="employee-mobile"]').setValue('13900139002');
    await wrapper.get('[data-testid="employee-number"]').setValue('EMP0997');
    await wrapper.get('[data-testid="employee-department"]').setValue('2');
    await wrapper.get('[data-testid="employee-position"]').setValue('2');
    await wrapper.get('[data-testid="employee-hire-date"]').setValue('2026-09-03');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="employee-drawer"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="employee-page-notice"]').text()).toContain('员工保存成功，但列表刷新失败');
    expect(wrapper.get('[data-testid="employee-page-notice"]').text()).toContain('列表服务暂不可用');
  });

  it('focuses the drawer, traps focus, closes on Escape and restores the trigger', async () => {
    const wrapper = await mountPage();
    const trigger = wrapper.get('[data-testid="add-employee"]');
    (trigger.element as HTMLButtonElement).focus();
    await trigger.trigger('click');
    await nextTick();

    const nameInput = wrapper.get('[data-testid="employee-name"]');
    expect(document.activeElement).toBe(nameInput.element);

    const saveButton = wrapper.get('[data-testid="save-employee"]');
    (saveButton.element as HTMLButtonElement).focus();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(wrapper.get('[data-testid="close-employee-drawer"]').element);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }));
    expect(document.activeElement).toBe(saveButton.element);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await nextTick();
    expect(wrapper.find('[data-testid="employee-drawer"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it('uses the Figma desktop height and typography hierarchy', async () => {
    const wrapper = await mountPage();

    expect(wrapper.get('[data-testid="employee-workspace"]').classes()).toContain('h-[804px]');
    expect(wrapper.get('[data-testid="employee-table-content"]').classes()).toContain('h-[572px]');
    expect(wrapper.get('h1').classes()).toContain('font-bold');
    expect(wrapper.get('[data-testid="employee-list-title"]').classes()).toContain('font-medium');
  });

  it('renders exact login availability labels for every employee account state', async () => {
    const service = createMockOrganizationService();
    const cases: Array<{ employee: Employee; label: string }> = [
      { employee: { ...employee, id: 101, status: 'disabled', passwordLoginEnabled: true }, label: '不可登录' },
      { employee: { ...employee, id: 102, status: 'resigned', employmentType: 'temporary', passwordLoginEnabled: true }, label: '不可登录' },
      { employee: { ...employee, id: 103, passwordLoginEnabled: true }, label: '飞书 + 手机号' },
      { employee: { ...employee, id: 104, passwordLoginEnabled: false }, label: '飞书已绑定' },
      { employee: { ...employee, id: 105, feishuBindingStatus: 'unbound', feishuDisplayName: '', passwordLoginEnabled: true }, label: '手机号账号' },
      { employee: { ...employee, id: 106, feishuBindingStatus: 'pending', feishuDisplayName: '', passwordLoginEnabled: false }, label: '待绑定飞书' },
      { employee: { ...employee, id: 107, employmentType: 'temporary', feishuBindingStatus: 'unbound', feishuDisplayName: '', passwordLoginEnabled: true }, label: '手机号账号' },
      { employee: { ...employee, id: 108, employmentType: 'temporary', feishuBindingStatus: 'unbound', feishuDisplayName: '', passwordLoginEnabled: false }, label: '不可登录' },
      { employee: { ...employee, id: 109, feishuBindingStatus: 'unbound', feishuDisplayName: '', passwordLoginEnabled: false }, label: '飞书未绑定' }
    ];
    vi.spyOn(service, 'listEmployees').mockResolvedValue({
      records: cases.map(({ employee: item }) => item),
      page: 1,
      pageSize: 20,
      total: cases.length
    });

    const wrapper = await mountPage(service);

    for (const { employee: item, label } of cases) {
      expect(wrapper.get(`[data-testid="employee-login-method-${item.id}"]`).text()).toBe(label);
    }
  });

  it('keeps view access while hiding every employee mutation control without manage permission', async () => {
    setPermissions(['organization:view']);
    const wrapper = await mountPage();

    expect(wrapper.find('[data-testid="add-employee"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="edit-employee-"]').exists()).toBe(false);
    const viewAction = wrapper.get('[data-testid^="view-employee-"]');
    await viewAction.trigger('click');

    expect(wrapper.get('[data-testid="employee-drawer"]').attributes('data-mode')).toBe('view');
    expect(wrapper.find('[data-testid="edit-from-view"]').exists()).toBe(false);
  });
});

describe('EmployeeFormDrawer', () => {
  it('uses the Figma medium weight for the drawer title', () => {
    const wrapper = mountDrawer();
    const title = wrapper.get('[data-testid="employee-drawer-title"]');

    expect(title.classes()).toContain('font-medium');
    expect(title.classes()).not.toContain('font-semibold');
  });

  it('places an accessible employment mode fieldset inside the login section', () => {
    const wrapper = mountDrawer();
    const loginSection = wrapper.get('[data-testid="employee-login-section"]');

    expect(loginSection.get('[data-testid="employment-type-fieldset"]').element.tagName).toBe('FIELDSET');
    expect(wrapper.find('label label').exists()).toBe(false);
  });

  it('validates temporary account rules before emitting save', async () => {
    const wrapper = mountDrawer();

    await wrapper.get('[data-testid="employment-temporary"]').setValue(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.text()).toContain('临时员工必须启用手机号和密码登录');
    expect(wrapper.emitted('save')).toBeUndefined();
  });

  it('requires a password and matching confirmation when creating any password account', async () => {
    const wrapper = mountDrawer();
    await fillValidDrawer(wrapper);
    await wrapper.get('[data-testid="password-login-enabled"]').setValue(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('启用手机号和密码登录时必须设置密码');
    expect(wrapper.emitted('save')).toBeUndefined();

    await wrapper.get('[data-testid="employee-password"]').setValue('Formal@123456');
    await wrapper.get('[data-testid="save-employee"]').trigger('click');
    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('两次输入的密码不一致');
    expect(wrapper.emitted('save')).toBeUndefined();
  });

  it('requires a password when an edit enables password login', async () => {
    const wrapper = mountDrawer({ mode: 'edit', employee });

    await wrapper.get('[data-testid="password-login-enabled"]').setValue(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('启用手机号和密码登录时必须设置密码');
    expect(wrapper.emitted('save')).toBeUndefined();
  });

  it('allows an established password account to be edited without replacing its password', async () => {
    const wrapper = mountDrawer({
      mode: 'edit',
      employee: { ...employee, passwordLoginEnabled: true }
    });

    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    const payload = wrapper.emitted<SaveEmployeePayload[]>('save')?.[0]?.[0];
    expect(payload?.passwordLoginEnabled).toBe(true);
    expect(payload).not.toHaveProperty('password');
  });

  it('clears a typed password and omits it after password login is turned off', async () => {
    const wrapper = mountDrawer();
    await fillValidDrawer(wrapper);
    await wrapper.get('[data-testid="password-login-enabled"]').setValue(true);
    await wrapper.get('[data-testid="employee-password"]').setValue('Stale@123456');
    await wrapper.get('[data-testid="employee-password-confirm"]').setValue('Stale@123456');
    await wrapper.get('[data-testid="password-login-enabled"]').setValue(false);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    const payload = wrapper.emitted<SaveEmployeePayload[]>('save')?.[0]?.[0];
    expect(payload?.passwordLoginEnabled).toBe(false);
    expect(payload).not.toHaveProperty('password');

    await wrapper.get('[data-testid="password-login-enabled"]').setValue(true);
    expect((wrapper.get('[data-testid="employee-password"]').element as HTMLInputElement).value).toBe('');
    expect((wrapper.get('[data-testid="employee-password-confirm"]').element as HTMLInputElement).value).toBe('');
  });

  it('uses the browser-local calendar date for a new employee', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 3, 0, 30));

    const wrapper = mountDrawer();

    expect((wrapper.get('[data-testid="employee-hire-date"]').element as HTMLInputElement).value).toBe('2026-09-03');
  });

  it('marks the Feishu QR action as unavailable until it is connected', () => {
    const wrapper = mountDrawer();
    const qrAction = wrapper.get('[data-testid="generate-feishu-qr"]');

    expect(qrAction.attributes('disabled')).toBeDefined();
    expect(qrAction.attributes('title')).toBe('暂未接入飞书二维码');
    expect(qrAction.text()).toContain('暂未接入');
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

  it('requires a new password when converting a formal employee to temporary', async () => {
    const wrapper = mountDrawer({ mode: 'edit', employee });

    await wrapper.get('[data-testid="employment-temporary"]').setValue(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('临时员工必须启用手机号和密码登录');
    expect(wrapper.emitted('save')).toBeUndefined();
  });

  it('does not force password replacement for an established temporary password account', async () => {
    const temporaryEmployee: Employee = {
      ...employee,
      employeeNo: 'TMP0007',
      employmentType: 'temporary',
      feishuBindingStatus: 'unbound',
      feishuDisplayName: '',
      passwordLoginEnabled: true
    };
    const wrapper = mountDrawer({ mode: 'edit', employee: temporaryEmployee });

    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    const payload = wrapper.emitted<SaveEmployeePayload[]>('save')?.[0]?.[0];
    expect(payload?.employmentType).toBe('temporary');
    expect(payload).not.toHaveProperty('password');
  });

  it('requires a password for a legacy temporary employee without password login', async () => {
    const legacyTemporaryEmployee: Employee = {
      ...employee,
      employmentType: 'temporary',
      feishuBindingStatus: 'unbound',
      feishuDisplayName: '',
      passwordLoginEnabled: false
    };
    const wrapper = mountDrawer({ mode: 'edit', employee: legacyTemporaryEmployee });

    expect(wrapper.find('[data-testid="employee-password"]').exists()).toBe(true);
    await wrapper.get('[data-testid="save-employee"]').trigger('click');

    expect(wrapper.get('[data-testid="employee-drawer-error"]').text()).toContain('临时员工必须启用手机号和密码登录');
    expect(wrapper.emitted('save')).toBeUndefined();
  });
});

async function fillValidDrawer(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="employee-name"]').setValue('测试员工');
  await wrapper.get('[data-testid="employee-mobile"]').setValue('13900139009');
  await wrapper.get('[data-testid="employee-number"]').setValue('EMP0009');
  await wrapper.get('[data-testid="employee-department"]').setValue('2');
  await wrapper.get('[data-testid="employee-position"]').setValue('2');
  await wrapper.get('[data-testid="employee-hire-date"]').setValue('2026-09-03');
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function employeePage(item: Employee): PageResult<Employee> {
  return { records: [item], page: 1, pageSize: 20, total: 1 };
}
