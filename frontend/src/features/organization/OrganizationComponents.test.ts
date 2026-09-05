import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import DepartmentFormDrawer from './components/DepartmentFormDrawer.vue';
import DepartmentTree from './components/DepartmentTree.vue';
import OrganizationPagination from './components/OrganizationPagination.vue';
import OrganizationStatusBadge from './components/OrganizationStatusBadge.vue';
import { buildDepartmentTree } from './organizationTree';
import type {
  Department,
  Employee,
  EmployeeStatus,
  FeishuBindingStatus,
  OrganizationRecordStatus
} from './types';

const departments: Department[] = [
  { id: 1, departmentCode: 'HQ', departmentName: '总部', parentId: null, managerEmployeeId: 1, managerName: '张敏', sortOrder: 1, status: 'enabled' },
  { id: 2, departmentCode: 'PD', departmentName: '产品中心', parentId: 1, managerEmployeeId: 2, managerName: '陈雯', sortOrder: 1, status: 'enabled' },
  { id: 3, departmentCode: 'DS', departmentName: '设计组', parentId: 2, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' },
  { id: 4, departmentCode: 'FIN', departmentName: '财务部', parentId: null, managerEmployeeId: null, managerName: '', sortOrder: 2, status: 'disabled' }
];

const tree = buildDepartmentTree(departments);

describe('DepartmentTree', () => {
  it('selects all company and a nested department', async () => {
    const wrapper = mount(DepartmentTree, {
      props: {
        nodes: tree,
        selectedId: null,
        expandedIds: [1, 2],
        employeeCounts: { 1: 8, 2: 5, 3: 5, 4: 3 }
      }
    });

    await wrapper.get('[data-testid="department-all"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]).toEqual([null]);
    await wrapper.get('[data-testid="department-node-3"]').trigger('click');
    expect(wrapper.emitted('select')?.[1]).toEqual([3]);
  });

  it('emits toggle without selecting and respects controlled expansion', async () => {
    const wrapper = mount(DepartmentTree, {
      props: { nodes: tree, selectedId: null, expandedIds: [1], employeeCounts: {} }
    });

    expect(wrapper.find('[data-testid="department-node-2"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="department-node-3"]').exists()).toBe(false);
    await wrapper.get('[data-testid="department-toggle-2"]').trigger('click');

    expect(wrapper.emitted('toggle')?.[0]).toEqual([2]);
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('shows recursive employee totals for every node and counts all company exactly once', () => {
    const wrapper = mount(DepartmentTree, {
      props: {
        nodes: tree,
        selectedId: null,
        expandedIds: [1, 2],
        employeeCounts: { 1: 2, 2: 3, 3: 5, 4: 1 }
      }
    });

    expect(wrapper.get('[data-testid="department-all"]').text()).toContain('11');
    expect(wrapper.get('[data-testid="department-node-1"]').text()).toContain('10');
    expect(wrapper.get('[data-testid="department-node-2"]').text()).toContain('8');
    expect(wrapper.get('[data-testid="department-node-3"]').text()).toContain('5');
    expect(wrapper.get('[data-testid="department-node-4"]').text()).toContain('1');
  });

  it('renders a page-specific title and selected-node summary footer', () => {
    const wrapper = mount(DepartmentTree, {
      props: {
        nodes: tree,
        selectedId: 2,
        expandedIds: [1, 2],
        employeeCounts: { 1: 2, 2: 3, 3: 5, 4: 1 },
        title: '部门结构',
        summary: {
          label: '当前选中',
          title: '产品中心',
          meta: '负责人：陈雯 · 直属员工 3 人'
        }
      }
    });

    expect(wrapper.get('[data-testid="department-tree-title"]').text()).toBe('部门结构');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('当前选中');
    expect(wrapper.get('[data-testid="department-tree-summary-title"]').text()).toBe('产品中心');
    expect(wrapper.get('[data-testid="department-tree-summary"]').text()).toContain('负责人：陈雯 · 直属员工 3 人');
  });

  it('filters by department name while preserving matching ancestors and counts', async () => {
    const wrapper = mount(DepartmentTree, {
      props: {
        nodes: tree,
        selectedId: null,
        expandedIds: [],
        employeeCounts: { 1: 8, 2: 5, 3: 5, 4: 3 }
      }
    });

    await wrapper.get('[data-testid="department-search"]').setValue('设计');

    expect(wrapper.find('[data-testid="department-node-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="department-node-2"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="department-node-3"]').text()).toContain('5');
    expect(wrapper.find('[data-testid="department-node-4"]').exists()).toBe(false);
  });
});

describe('OrganizationPagination', () => {
  it('emits a valid page and disables boundary controls', async () => {
    const wrapper = mount(OrganizationPagination, { props: { page: 1, pageSize: 10, total: 83 } });

    expect(wrapper.get('[data-testid="previous-page"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="next-page"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[data-testid="page-2"]').trigger('click');
    expect(wrapper.emitted('page')?.[0]).toEqual([2]);
  });

  it('shows compact pages, total count, and changes to an allowed page size', async () => {
    const wrapper = mount(OrganizationPagination, { props: { page: 5, pageSize: 10, total: 100 } });

    expect(wrapper.get('[data-testid="pagination-total"]').text()).toBe('共 100 条');
    expect(wrapper.findAll('[data-testid="page-ellipsis"]')).toHaveLength(2);
    expect(wrapper.find('[data-testid="page-5"]').attributes('aria-current')).toBe('page');
    const pageSize = wrapper.get('[data-testid="page-size"]');
    expect(pageSize.findAll('option').map((option) => option.attributes('value'))).toEqual(['10', '20', '50']);
    await pageSize.setValue('20');
    expect(wrapper.emitted('page-size')?.[0]).toEqual([20]);
  });

  it('disables next at the last page and never emits an invalid boundary page', async () => {
    const wrapper = mount(OrganizationPagination, { props: { page: 9, pageSize: 10, total: 83 } });
    const next = wrapper.get('[data-testid="next-page"]');

    expect(next.attributes('disabled')).toBeDefined();
    await next.trigger('click');
    expect(wrapper.emitted('page')).toBeUndefined();
  });

  it('reconciles a parent page after the result shrinks to an earlier last page', async () => {
    const wrapper = mount(OrganizationPagination, { props: { page: 2, pageSize: 10, total: 9 } });
    await nextTick();

    expect(wrapper.emitted('page')).toEqual([[1]]);
  });
});

describe('DepartmentFormDrawer', () => {
  it('keeps saved disabled relationships visible as read-only options', async () => {
    const department: Department = {
      id: 5,
      departmentCode: 'ARCHIVE-CHILD',
      departmentName: '归档子部门',
      parentId: 4,
      managerEmployeeId: 9,
      managerName: '离职负责人',
      sortOrder: 10,
      status: 'disabled'
    };
    const inactiveManager: Employee = {
      id: 9,
      employeeNo: 'E009',
      employeeName: '离职负责人',
      mobile: '13800000009',
      departmentId: 5,
      positionId: 1,
      employmentType: 'formal',
      status: 'resigned',
      hireDate: '2025-01-01',
      feishuBindingStatus: 'unbound',
      feishuDisplayName: '',
      passwordLoginEnabled: false
    };
    const wrapper = mount(DepartmentFormDrawer, {
      props: {
        mode: 'edit',
        department,
        parentId: null,
        departments: [...departments, department],
        employees: [inactiveManager],
        saving: false,
        error: ''
      }
    });
    await nextTick();

    const parent = wrapper.get('[data-testid="department-parent"]');
    const manager = wrapper.get('[data-testid="department-manager"]');
    expect((parent.element as HTMLSelectElement).value).toBe('4');
    expect(parent.get('option[value="4"]').attributes('disabled')).toBeDefined();
    expect(parent.text()).toContain('财务部');
    expect((manager.element as HTMLSelectElement).value).toBe('9');
    expect(manager.get('option[value="9"]').attributes('disabled')).toBeDefined();
    expect(manager.text()).toContain('离职负责人');
  });
});

describe('OrganizationStatusBadge', () => {
  it.each<[
    EmployeeStatus | OrganizationRecordStatus | FeishuBindingStatus,
    string,
    string,
    string
  ]>([
    ['active', '在职', 'bg-emerald-50', 'text-emerald-700'],
    ['disabled', '停用', 'bg-slate-100', 'text-slate-600'],
    ['resigned', '离职', 'bg-rose-50', 'text-rose-600'],
    ['enabled', '启用', 'bg-emerald-50', 'text-emerald-700'],
    ['bound', '已绑定', 'bg-blue-50', 'text-blue-700'],
    ['pending', '待绑定', 'bg-amber-50', 'text-amber-700'],
    ['unbound', '未绑定', 'bg-slate-100', 'text-slate-600']
  ])('presents %s as %s with its status colors', (status, label, backgroundClass, textClass) => {
    const wrapper = mount(OrganizationStatusBadge, { props: { status } });
    const badge = wrapper.get('[data-testid="organization-status-badge"]');

    expect(badge.text()).toBe(label);
    expect(badge.classes()).toContain(backgroundClass);
    expect(badge.classes()).toContain(textClass);
  });
});
