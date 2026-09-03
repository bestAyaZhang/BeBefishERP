import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DepartmentFormDrawer from './components/DepartmentFormDrawer.vue';
import PositionFormDrawer from './components/PositionFormDrawer.vue';
import { createMockOrganizationService } from './mockOrganizationService';
import type { OrganizationService } from './organizationService';
import type { Department, Employee, Position } from './types';
import DepartmentManagementView from './views/DepartmentManagementView.vue';
import PositionManagementView from './views/PositionManagementView.vue';

const departments: Department[] = [
  { id: 1, departmentCode: 'BBF', departmentName: '贝贝鱼科技有限公司', parentId: null, managerEmployeeId: 1, managerName: '陈立', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PD', departmentName: '产品中心', parentId: 1, managerEmployeeId: 2, managerName: '李娜', sortOrder: 10, status: 'enabled' },
  { id: 3, departmentCode: 'RD', departmentName: '研发组', parentId: 2, managerEmployeeId: 3, managerName: '张伟', sortOrder: 10, status: 'enabled' }
];

const employees: Employee[] = [
  { id: 1, employeeNo: 'E-001', employeeName: '陈立', mobile: '13800000001', departmentId: 1, positionId: 1, employmentType: 'formal', status: 'active', hireDate: '2024-01-01', feishuBindingStatus: 'bound', feishuDisplayName: '陈立', passwordLoginEnabled: false },
  { id: 2, employeeNo: 'E-002', employeeName: '李娜', mobile: '13800000002', departmentId: 2, positionId: 2, employmentType: 'formal', status: 'active', hireDate: '2024-01-01', feishuBindingStatus: 'bound', feishuDisplayName: '李娜', passwordLoginEnabled: false }
];

const position: Position = {
  id: 2,
  positionCode: 'PM',
  positionName: '产品经理',
  departmentId: 2,
  responsibilities: '负责产品规划与交付',
  status: 'enabled',
  employeeCount: 2
};

let activeWrapper: VueWrapper | undefined;

afterEach(() => {
  activeWrapper?.unmount();
  activeWrapper = undefined;
});

async function mountDepartmentPage(service: OrganizationService = createMockOrganizationService()) {
  const wrapper = mount(DepartmentManagementView, {
    attachTo: document.body,
    global: { provide: { organizationService: service } }
  });
  activeWrapper = wrapper;
  await flushPromises();
  return wrapper;
}

async function mountPositionPage(service: OrganizationService = createMockOrganizationService()) {
  const wrapper = mount(PositionManagementView, {
    attachTo: document.body,
    global: { provide: { organizationService: service } }
  });
  activeWrapper = wrapper;
  await flushPromises();
  return wrapper;
}

describe('DepartmentManagementView', () => {
  it('creates a child department from the selected tree node and loads manager options', async () => {
    const service = createMockOrganizationService();
    const listAllEmployees = vi.spyOn(service, 'listAllEmployees');
    const wrapper = await mountDepartmentPage(service);

    await wrapper.get('[data-testid="department-node-2"]').trigger('click');
    await wrapper.get('[data-testid="add-department"]').trigger('click');

    expect(listAllEmployees).toHaveBeenCalledOnce();
    expect((wrapper.get('[data-testid="department-parent"]').element as HTMLSelectElement).value).toBe('2');
    expect(wrapper.get('[data-testid="department-manager"]').text()).toContain('李娜');
  });

  it('filters by keyword and status, resetting the query to page one', async () => {
    const service = createMockOrganizationService();
    const originalList = service.listDepartmentPage.bind(service);
    const listDepartmentPage = vi.spyOn(service, 'listDepartmentPage');
    listDepartmentPage.mockImplementation(async (query) => ({ ...await originalList(query), total: 45 }));
    const wrapper = await mountDepartmentPage(service);

    await wrapper.get('[data-testid="page-2"]').trigger('click');
    await flushPromises();
    expect(listDepartmentPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));

    await wrapper.get('[data-testid="department-keyword"]').setValue('产品');
    await wrapper.get('[data-testid="department-search-button"]').trigger('click');
    await flushPromises();
    expect(listDepartmentPage).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '产品', page: 1 }));

    await wrapper.get('[data-testid="department-status-filter"]').setValue('disabled');
    await flushPromises();
    expect(listDepartmentPage).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '产品', status: 'disabled', page: 1 }));
  });

  it('prefills edit data and explains a blocked disable action', async () => {
    const wrapper = await mountDepartmentPage();
    const blockedAction = wrapper.get('[data-testid="department-status-action-1"]');

    expect(blockedAction.text()).toBe('不可停用');
    expect(blockedAction.attributes('title')).toContain('子部门或在职员工');

    await wrapper.get('[data-testid="edit-department-2"]').trigger('click');
    expect((wrapper.get('[data-testid="department-name"]').element as HTMLInputElement).value).toBe('产品中心');
    expect((wrapper.get('[data-testid="department-code"]').element as HTMLInputElement).value).toBe('PD');
  });

  it('preserves department values when the service rejects a duplicate', async () => {
    const wrapper = await mountDepartmentPage();

    await wrapper.get('[data-testid="add-department"]').trigger('click');
    await wrapper.get('[data-testid="department-code"]').setValue('PD');
    await wrapper.get('[data-testid="department-name"]').setValue('重复编码部门');
    await wrapper.get('[data-testid="save-department"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="department-drawer-error"]').text()).toContain('部门编码已存在');
    expect((wrapper.get('[data-testid="department-name"]').element as HTMLInputElement).value).toBe('重复编码部门');
  });

  it('renders department loading, error and empty states', async () => {
    let rejectRequest: ((error: Error) => void) | undefined;
    const service = createMockOrganizationService();
    vi.spyOn(service, 'listDepartmentPage').mockReturnValue(new Promise((_resolve, reject) => { rejectRequest = reject; }));
    const wrapper = mount(DepartmentManagementView, { global: { provide: { organizationService: service } } });
    activeWrapper = wrapper;

    expect(wrapper.find('[data-testid="department-loading"]').exists()).toBe(true);
    rejectRequest?.(new Error('部门数据加载失败'));
    await flushPromises();
    expect(wrapper.get('[data-testid="department-error"]').text()).toContain('部门数据加载失败');

    vi.mocked(service.listDepartmentPage).mockResolvedValue({ records: [], page: 1, pageSize: 20, total: 0 });
    await wrapper.get('[data-testid="department-search-button"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="department-empty"]').text()).toContain('暂无匹配部门');
  });
});

describe('PositionManagementView', () => {
  it('filters positions by the selected department and opens a prefilled editor', async () => {
    const service = createMockOrganizationService();
    const listPositions = vi.spyOn(service, 'listPositions');
    const wrapper = await mountPositionPage(service);

    await wrapper.get('[data-testid="department-node-3"]').trigger('click');
    await flushPromises();
    expect(listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: 3, page: 1 }));

    await wrapper.get('[data-testid^="edit-position-"]').trigger('click');
    expect(wrapper.find('[data-testid="position-drawer"]').exists()).toBe(true);
    expect((wrapper.get('[data-testid="position-department"]').element as HTMLSelectElement).value).toBe('3');
  });

  it('filters by keyword and status and resets page state', async () => {
    const service = createMockOrganizationService();
    const originalList = service.listPositions.bind(service);
    const listPositions = vi.spyOn(service, 'listPositions');
    listPositions.mockImplementation(async (query) => ({ ...await originalList(query), total: 45 }));
    const wrapper = await mountPositionPage(service);

    await wrapper.get('[data-testid="page-2"]').trigger('click');
    await flushPromises();
    expect(listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));

    await wrapper.get('[data-testid="position-keyword"]').setValue('产品');
    await wrapper.get('[data-testid="position-search-button"]').trigger('click');
    await flushPromises();
    expect(listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '产品', page: 1 }));

    await wrapper.get('[data-testid="position-status-filter"]').setValue('disabled');
    await flushPromises();
    expect(listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '产品', status: 'disabled', page: 1 }));
  });

  it('preserves form values for duplicate names and occupied-position errors', async () => {
    const service = createMockOrganizationService();
    const wrapper = await mountPositionPage(service);

    await wrapper.get('[data-testid="add-position"]').trigger('click');
    await wrapper.get('[data-testid="position-code"]').setValue('PM-NEW');
    await wrapper.get('[data-testid="position-name"]').setValue('产品经理');
    await wrapper.get('[data-testid="position-department"]').setValue('2');
    await wrapper.get('[data-testid="save-position"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="position-drawer-error"]').text()).toContain('同一部门内岗位名称已存在');
    expect((wrapper.get('[data-testid="position-code"]').element as HTMLInputElement).value).toBe('PM-NEW');

    await wrapper.get('[data-testid="cancel-position"]').trigger('click');
    await wrapper.get('[data-testid="edit-position-3"]').trigger('click');
    await wrapper.get('[data-testid="position-department"]').setValue('5');
    await wrapper.get('[data-testid="save-position"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="position-drawer-error"]').text()).toContain('不能调整所属部门');
    expect((wrapper.get('[data-testid="position-department"]').element as HTMLSelectElement).value).toBe('5');
  });

  it('shows an occupied-position explanation when disabling fails', async () => {
    const wrapper = await mountPositionPage();

    await wrapper.get('[data-testid="position-status-action-3"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="position-page-error"]').text()).toContain('岗位已分配给在职员工');
  });

  it('renders a readable position error and empty result', async () => {
    const service = createMockOrganizationService();
    vi.spyOn(service, 'listPositions').mockRejectedValueOnce(new Error('岗位数据加载失败'));
    const wrapper = await mountPositionPage(service);

    expect(wrapper.get('[data-testid="position-error"]').text()).toContain('岗位数据加载失败');
    vi.mocked(service.listPositions).mockResolvedValue({ records: [], page: 1, pageSize: 20, total: 0 });
    await wrapper.get('[data-testid="position-search-button"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="position-empty"]').text()).toContain('暂无匹配岗位');
  });
});

describe('organization management drawers', () => {
  it('uses modal keyboard behavior for the department drawer', async () => {
    const trigger = document.createElement('button');
    document.body.appendChild(trigger);
    trigger.focus();
    const wrapper = mount(DepartmentFormDrawer, {
      attachTo: document.body,
      props: { mode: 'create', department: null, parentId: 2, departments, employees, saving: false, error: '' }
    });
    activeWrapper = wrapper;
    await nextTick();

    expect(document.activeElement).toBe(wrapper.get('[data-testid="department-code"]').element);
    const saveButton = wrapper.get('[data-testid="save-department"]');
    (saveButton.element as HTMLButtonElement).focus();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(wrapper.get('[data-testid="close-department-drawer"]').element);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(wrapper.emitted('close')).toHaveLength(1);
    wrapper.unmount();
    activeWrapper = undefined;
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });

  it('validates the position drawer without clearing input', async () => {
    const wrapper = mount(PositionFormDrawer, {
      attachTo: document.body,
      props: { mode: 'edit', position, departmentId: null, departments, saving: false, error: '' }
    });
    activeWrapper = wrapper;
    await nextTick();

    await wrapper.get('[data-testid="position-name"]').setValue('');
    await wrapper.get('[data-testid="save-position"]').trigger('click');
    expect(wrapper.get('[data-testid="position-drawer-error"]').text()).toContain('请完整填写岗位资料');
    expect((wrapper.get('[data-testid="position-code"]').element as HTMLInputElement).value).toBe('PM');
  });
});
