import type { Department, DepartmentTreeNode } from './types';

function compareDepartments(left: Department, right: Department) {
  return left.sortOrder - right.sortOrder
    || left.departmentName.localeCompare(right.departmentName, 'zh-CN');
}

function sortDepartmentNodes(nodes: DepartmentTreeNode[]) {
  nodes.sort(compareDepartments);
  nodes.forEach((node) => sortDepartmentNodes(node.children));
}

export function buildDepartmentTree(departments: Department[]): DepartmentTreeNode[] {
  const nodeById = new Map<number, DepartmentTreeNode>(departments.map((department) => [
    department.id,
    { ...department, children: [] }
  ]));
  const roots: DepartmentTreeNode[] = [];

  for (const node of nodeById.values()) {
    const parent = node.parentId === null ? undefined : nodeById.get(node.parentId);
    if (parent) parent.children.push(node);
    else roots.push(node);
  }

  sortDepartmentNodes(roots);
  return roots;
}

export function collectDepartmentSubtreeIds(departments: Department[], rootId: number): Set<number> {
  const childrenByParentId = new Map<number, number[]>();
  for (const department of departments) {
    if (department.parentId === null) continue;
    const childIds = childrenByParentId.get(department.parentId) ?? [];
    childIds.push(department.id);
    childrenByParentId.set(department.parentId, childIds);
  }

  const subtreeIds = new Set<number>();
  const collect = (departmentId: number) => {
    if (subtreeIds.has(departmentId)) return;
    subtreeIds.add(departmentId);
    childrenByParentId.get(departmentId)?.forEach(collect);
  };

  collect(rootId);
  return subtreeIds;
}

export function filterDepartmentTree(nodes: DepartmentTreeNode[], keyword: string): DepartmentTreeNode[] {
  const normalizedKeyword = keyword.trim().toLowerCase();

  return nodes.flatMap((node) => {
    const children = filterDepartmentTree(node.children, normalizedKeyword);
    const matches = node.departmentName.toLowerCase().includes(normalizedKeyword);
    return matches || children.length > 0 ? [{ ...node, children }] : [];
  });
}
