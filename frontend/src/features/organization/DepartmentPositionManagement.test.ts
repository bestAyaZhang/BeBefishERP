import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DepartmentFormDrawer from './components/DepartmentFormDrawer.vue';
import PositionFormDrawer from './components/PositionFormDrawer.vue';
import { createMockOrganizationService } from './mockOrganizationService';
import type { OrganizationService } from './organizationService';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import type { Department, DepartmentListItem, Employee, PageResult, Position } from './types';
import DepartmentManagementView from './views/DepartmentManagementView.vue';
import PositionManagementView from './views/PositionManagementView.vue';

const departments: Department[] = [
  { id: 1, departmentCode: 'BBF', departmentName: '贝贝鱼科技有限公司', parentId: null, managerEmployeeId: 1, managerName: '陈立', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PD', departmentName: '产品中心', parentId: 1, managerEmployeeId: 2, managerName: '李娜', sortOrder: 10, status: 'enabled' },
  { id: 3, departmentCode: 'RD', departmentName: '研发组', parentId: 2, managerEmployeeId: 3, managerName: '张伟', sortOrder: 10, status: 'enabled' }
];

const disabledDepartment: Department = {
  id: 4,
  departmentCode: 'OLD',
  departmentName: '停用部门',
  parentId: 1,
  managerEmployeeId: null,
  managerName: '',
  sortOrder: 90,
  status: 'disabled'
};

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

  it('matches the department tree title and selected summary from Figma', async () => {
    const wrapper = await mountDepartmentPage();

    expect(wrapper.get('[data-testid="department-tree-title"]').text()).toBe('部门结构');
    await wrapper.get('[data-testid="department-node-2"]').trigger('click');
    expect(wrapper.get('[data-testid="department-tree-summary-title"]').text()).toBe('产品中心');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('负责人：李娜');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('直属员工');
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
    expect(blockedAction.classes()).toContain('text-xs');

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

  it('keeps the newest department request authoritative while stale requests finish around it', async () => {
    const service = createMockOrganizationService();
    const initialPage = await service.listDepartmentPage({ page: 1, size: 20 });
    const productPage = await service.listDepartmentPage({ page: 1, size: 20, keyword: '产品' });
    const salesPage = await service.listDepartmentPage({ page: 1, size: 20, keyword: '销售' });
    const initialRequest = deferred<PageResult<DepartmentListItem>>();
    const productRequest = deferred<PageResult<DepartmentListItem>>();
    const salesRequest = deferred<PageResult<DepartmentListItem>>();
    vi.spyOn(service, 'listDepartmentPage')
      .mockReturnValueOnce(initialRequest.promise)
      .mockReturnValueOnce(productRequest.promise)
      .mockReturnValueOnce(salesRequest.promise);
    const wrapper = mount(DepartmentManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;
    await flushPromises();

    await wrapper.get('[data-testid="department-keyword"]').setValue('产品');
    await wrapper.get('[data-testid="department-search-button"]').trigger('click');
    await wrapper.get('[data-testid="department-keyword"]').setValue('销售');
    await wrapper.get('[data-testid="department-search-button"]').trigger('click');
    productRequest.resolve(productPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="department-loading"]').exists()).toBe(true);

    salesRequest.resolve(salesPage);
    await flushPromises();
    expect(wrapper.get('table').text()).toContain('销售中心');
    expect(wrapper.get('table').text()).not.toContain('产品研发部');

    initialRequest.resolve(initialPage);
    await flushPromises();
    expect(wrapper.get('table').text()).toContain('销售中心');
    expect(wrapper.get('table').text()).not.toContain('产品研发部');
  });

  it('supports row and select-all department selection and clears stale selections on new records', async () => {
    const wrapper = await mountDepartmentPage();
    const selectAll = wrapper.get('[data-testid="select-all-departments"]');
    const firstRow = wrapper.findAll('[data-testid^="select-department-"]')[0];

    await firstRow.setValue(true);
    expect((selectAll.element as HTMLInputElement).indeterminate).toBe(true);
    await selectAll.setValue(true);
    expect(wrapper.findAll('[data-testid^="select-department-"]')
      .every((item) => (item.element as HTMLInputElement).checked)).toBe(true);
    await selectAll.setValue(false);
    await firstRow.setValue(true);

    await wrapper.get('[data-testid="department-keyword"]').setValue('销售');
    await wrapper.get('[data-testid="department-search-button"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-testid^="select-department-"]')
      .every((item) => !(item.element as HTMLInputElement).checked)).toBe(true);
    expect((wrapper.get('[data-testid="select-all-departments"]').element as HTMLInputElement).indeterminate).toBe(false);
  });

  it('separates a successful department status mutation from a successful refresh', async () => {
    const service = createMockOrganizationService();
    const changeStatus = vi.spyOn(service, 'changeDepartmentStatus');
    const wrapper = await mountDepartmentPage(service);

    await wrapper.get('[data-testid="department-status-action-11"]').trigger('click');
    await flushPromises();

    expect(changeStatus).toHaveBeenCalledWith(11, 'enabled');
    expect(wrapper.find('[data-testid="department-page-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="department-status-action-11"]').text()).toBe('停用');
  });

  it('reports that a department status mutation succeeded when its refresh fails', async () => {
    const service = createMockOrganizationService();
    const initialDepartments = await service.listAllDepartments();
    vi.spyOn(service, 'listAllDepartments')
      .mockResolvedValueOnce(initialDepartments)
      .mockRejectedValueOnce(new Error('部门刷新失败'));
    const changeStatus = vi.spyOn(service, 'changeDepartmentStatus');
    const wrapper = await mountDepartmentPage(service);

    await wrapper.get('[data-testid="department-status-action-11"]').trigger('click');
    await flushPromises();

    expect(changeStatus).toHaveBeenCalledOnce();
    expect(wrapper.get('[data-testid="department-page-error"]').text())
      .toContain('操作已成功，但列表刷新失败，请手动重试');
  });

  it('clears an earlier department warning after a later save succeeds', async () => {
    const service = createMockOrganizationService();
    vi.spyOn(service, 'changeDepartmentStatus').mockRejectedValueOnce(new Error('部门状态服务暂不可用'));
    const wrapper = await mountDepartmentPage(service);

    await wrapper.get('[data-testid="department-status-action-11"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="department-page-error"]').text()).toContain('部门状态服务暂不可用');

    await wrapper.get('[data-testid="add-department"]').trigger('click');
    await wrapper.get('[data-testid="department-code"]').setValue('WARN-CLEAR');
    await wrapper.get('[data-testid="department-name"]').setValue('告警清理部门');
    await wrapper.get('[data-testid="save-department"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="department-page-error"]').exists()).toBe(false);
  });

  it('lists departments but hides every mutation control without manage permission', async () => {
    setPermissions(['organization:view']);
    const wrapper = await mountDepartmentPage();

    expect(wrapper.text()).toContain('贝贝鱼科技有限公司');
    expect(wrapper.find('[data-testid="add-department"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="edit-department-"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="department-status-action-"]').exists()).toBe(false);
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

  it('includes descendant positions for a parent selection and renders the Figma tree summary', async () => {
    const service = createMockOrganizationService();
    const listPositions = vi.spyOn(service, 'listPositions');
    const wrapper = await mountPositionPage(service);

    expect(wrapper.get('[data-testid="department-tree-title"]').text()).toBe('所属部门');
    expect(listPositions).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: 2 }));
    expect(wrapper.get('[data-testid="position-list-title"]').text()).toBe('产品中心岗位');
    expect(wrapper.text()).toContain('后端工程师');
    expect(wrapper.text()).toContain('产品设计师');
    expect(wrapper.get('[data-testid="department-tree-summary-title"]').text()).toBe('产品中心');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('个岗位');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('在岗员工');
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

    expect(wrapper.get('[data-testid="position-status-action-3"]').classes()).toContain('text-xs');

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

  it('keeps the newest position request authoritative while stale requests finish around it', async () => {
    const service = createMockOrganizationService();
    const initialPage = await service.listPositions({ page: 1, size: 20, departmentId: 2 });
    const researchPage = await service.listPositions({ page: 1, size: 20, departmentId: 3 });
    const designPage = await service.listPositions({ page: 1, size: 20, departmentId: 4 });
    const initialRequest = deferred<PageResult<Position>>();
    const researchRequest = deferred<PageResult<Position>>();
    const designRequest = deferred<PageResult<Position>>();
    vi.spyOn(service, 'listPositions')
      .mockReturnValueOnce(initialRequest.promise)
      .mockReturnValueOnce(researchRequest.promise)
      .mockReturnValueOnce(designRequest.promise);
    const wrapper = mount(PositionManagementView, {
      global: { provide: { organizationService: service } }
    });
    activeWrapper = wrapper;
    await flushPromises();

    await wrapper.get('[data-testid="department-node-3"]').trigger('click');
    await wrapper.get('[data-testid="department-node-4"]').trigger('click');
    researchRequest.resolve(researchPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="position-loading"]').exists()).toBe(true);

    designRequest.resolve(designPage);
    await flushPromises();
    expect(wrapper.text()).toContain('产品设计师');
    expect(wrapper.text()).not.toContain('后端工程师');

    initialRequest.resolve(initialPage);
    await flushPromises();
    expect(wrapper.text()).toContain('产品设计师');
    expect(wrapper.text()).not.toContain('后端工程师');
  });

  it('supports row and select-all position selection and clears stale selections on new records', async () => {
    const wrapper = await mountPositionPage();
    const selectAll = wrapper.get('[data-testid="select-all-positions"]');
    const firstRow = wrapper.findAll('[data-testid^="select-position-"]')[0];

    await firstRow.setValue(true);
    expect((selectAll.element as HTMLInputElement).indeterminate).toBe(true);
    await selectAll.setValue(true);
    expect(wrapper.findAll('[data-testid^="select-position-"]')
      .every((item) => (item.element as HTMLInputElement).checked)).toBe(true);
    await selectAll.setValue(false);
    await firstRow.setValue(true);

    await wrapper.get('[data-testid="department-node-3"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-testid^="select-position-"]')
      .every((item) => !(item.element as HTMLInputElement).checked)).toBe(true);
    expect((wrapper.get('[data-testid="select-all-positions"]').element as HTMLInputElement).indeterminate).toBe(false);
  });

  it('separates a successful position status mutation from a successful refresh', async () => {
    const service = createMockOrganizationService();
    await service.changeDepartmentStatus(11, 'enabled');
    const changeStatus = vi.spyOn(service, 'changePositionStatus');
    const wrapper = await mountPositionPage(service);

    await wrapper.get('[data-testid="department-all"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="position-status-action-13"]').trigger('click');
    await flushPromises();

    expect(changeStatus).toHaveBeenCalledWith(13, 'enabled');
    expect(wrapper.find('[data-testid="position-page-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="position-status-action-13"]').text()).toBe('停用');
  });

  it('reports that a position status mutation succeeded when its refresh fails', async () => {
    const service = createMockOrganizationService();
    await service.changeDepartmentStatus(11, 'enabled');
    const initialDepartments = await service.listAllDepartments();
    vi.spyOn(service, 'listAllDepartments')
      .mockResolvedValueOnce(initialDepartments)
      .mockRejectedValueOnce(new Error('岗位刷新失败'));
    const changeStatus = vi.spyOn(service, 'changePositionStatus');
    const wrapper = await mountPositionPage(service);

    await wrapper.get('[data-testid="department-all"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="position-status-action-13"]').trigger('click');
    await flushPromises();

    expect(changeStatus).toHaveBeenCalledOnce();
    expect(wrapper.get('[data-testid="position-page-error"]').text())
      .toContain('操作已成功，但列表刷新失败，请手动重试');
  });

  it('clears an earlier position warning after a later save succeeds', async () => {
    const wrapper = await mountPositionPage();

    await wrapper.get('[data-testid="position-status-action-3"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="position-page-error"]').text()).toContain('岗位已分配给在职员工');

    await wrapper.get('[data-testid="add-position"]').trigger('click');
    await wrapper.get('[data-testid="position-code"]').setValue('WARN-CLEAR');
    await wrapper.get('[data-testid="position-name"]').setValue('告警清理岗位');
    await wrapper.get('[data-testid="save-position"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="position-page-error"]').exists()).toBe(false);
  });

  it('lists positions but hides every mutation control without manage permission', async () => {
    setPermissions(['organization:view']);
    const wrapper = await mountPositionPage();

    expect(wrapper.text()).toContain('产品经理');
    expect(wrapper.find('[data-testid="add-position"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="edit-position-"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid^="position-status-action-"]').exists()).toBe(false);
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

  it('does not retain a disabled selected department when creating a department', async () => {
    const wrapper = mount(DepartmentFormDrawer, {
      attachTo: document.body,
      props: {
        mode: 'create',
        department: null,
        parentId: disabledDepartment.id,
        departments: [...departments, disabledDepartment],
        employees,
        saving: false,
        error: ''
      }
    });
    activeWrapper = wrapper;
    await nextTick();

    expect((wrapper.get('[data-testid="department-parent"]').element as HTMLSelectElement).value).not.toBe('4');
    await wrapper.get('[data-testid="department-code"]').setValue('NEW');
    await wrapper.get('[data-testid="department-name"]').setValue('新部门');
    await wrapper.get('[data-testid="save-department"]').trigger('click');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({ parentId: null });
  });

  it('uses a visible enabled fallback when creating a position from a disabled department', async () => {
    const wrapper = mount(PositionFormDrawer, {
      attachTo: document.body,
      props: {
        mode: 'create',
        position: null,
        departmentId: disabledDepartment.id,
        departments: [...departments, disabledDepartment],
        saving: false,
        error: ''
      }
    });
    activeWrapper = wrapper;
    await nextTick();

    const departmentSelect = wrapper.get('[data-testid="position-department"]');
    expect((departmentSelect.element as HTMLSelectElement).value).toBe('1');
    expect(departmentSelect.find('option[value="4"]').exists()).toBe(false);
    await wrapper.get('[data-testid="position-code"]').setValue('NEW-POS');
    await wrapper.get('[data-testid="position-name"]').setValue('新岗位');
    await wrapper.get('[data-testid="save-position"]').trigger('click');
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({ departmentId: 1 });
  });

  it('uses modal keyboard behavior for the position drawer', async () => {
    const trigger = document.createElement('button');
    document.body.appendChild(trigger);
    trigger.focus();
    const wrapper = mount(PositionFormDrawer, {
      attachTo: document.body,
      props: { mode: 'create', position: null, departmentId: 2, departments, saving: false, error: '' }
    });
    activeWrapper = wrapper;
    await nextTick();

    expect(document.activeElement).toBe(wrapper.get('[data-testid="position-code"]').element);
    const saveButton = wrapper.get('[data-testid="save-position"]');
    (saveButton.element as HTMLButtonElement).focus();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(wrapper.get('[data-testid="close-position-drawer"]').element);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(wrapper.emitted('close')).toHaveLength(1);
    wrapper.unmount();
    activeWrapper = undefined;
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}
