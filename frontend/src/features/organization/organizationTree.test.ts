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
