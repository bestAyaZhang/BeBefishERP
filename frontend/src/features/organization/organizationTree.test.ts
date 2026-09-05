import { describe, expect, it, vi } from 'vitest';
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

  it('orders siblings by sort order then department name', () => {
    const departmentsWithSiblings: Department[] = [
      { id: 4, departmentCode: 'ROOT', departmentName: 'Root', parentId: null, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' },
      { id: 5, departmentCode: 'ALPHA', departmentName: 'Alpha', parentId: 4, managerEmployeeId: null, managerName: '', sortOrder: 2, status: 'enabled' },
      { id: 6, departmentCode: 'ZULU', departmentName: 'Zulu', parentId: 4, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' },
      { id: 7, departmentCode: 'BETA', departmentName: 'Beta', parentId: 4, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' }
    ];

    const tree = buildDepartmentTree(departmentsWithSiblings);
    expect(tree[0].children.map((node) => node.departmentName)).toEqual(['Beta', 'Zulu', 'Alpha']);
  });

  it('collects the selected department and every descendant', () => {
    expect([...collectDepartmentSubtreeIds(departments, 2)]).toEqual([2, 3]);
  });

  it('keeps ancestors when a descendant matches search', () => {
    const filtered = filterDepartmentTree(buildDepartmentTree(departments), '设计');
    expect(filtered[0].children[0].children[0].departmentName).toBe('设计组');
  });

  it('filters consistently when the host locale has special casing rules', () => {
    const originalToLocaleLowerCase = String.prototype.toLocaleLowerCase;
    const localeSpy = vi.spyOn(String.prototype, 'toLocaleLowerCase').mockImplementation(function (
      this: string,
      locales?: Intl.LocalesArgument
    ) {
      return originalToLocaleLowerCase.call(this, locales ?? 'tr-TR');
    });
    const localeSensitiveDepartments: Department[] = [
      { id: 4, departmentCode: 'IT', departmentName: 'IT', parentId: null, managerEmployeeId: null, managerName: '', sortOrder: 1, status: 'enabled' }
    ];

    try {
      const filtered = filterDepartmentTree(buildDepartmentTree(localeSensitiveDepartments), 'i');
      expect(filtered.map((node) => node.departmentName)).toEqual(['IT']);
    } finally {
      localeSpy.mockRestore();
    }
  });
});
