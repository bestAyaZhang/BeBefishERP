import type { Category } from '../masterdata/types';

export interface ProductCategoryNode extends Category {
  children: ProductCategoryNode[];
}

export interface ProductCategoryTreeModel {
  categories: Category[];
  roots: ProductCategoryNode[];
  parentById: Map<number, number | null>;
}

function compareDuplicateCandidates(left: Category, right: Category) {
  return left.sortOrder - right.sortOrder
    || left.categoryCode.localeCompare(right.categoryCode)
    || left.categoryName.localeCompare(right.categoryName, 'zh-CN')
    || (left.parentId ?? 0) - (right.parentId ?? 0)
    || left.status.localeCompare(right.status)
    || left.remark.localeCompare(right.remark);
}

function compareTreeOrder(left: Category, right: Category) {
  return left.sortOrder - right.sortOrder
    || left.categoryName.localeCompare(right.categoryName, 'zh-CN')
    || left.id - right.id;
}

export function deduplicateCategories(categories: Category[]) {
  const candidatesById = new Map<number, Category[]>();
  for (const category of categories) {
    const candidates = candidatesById.get(category.id) ?? [];
    candidates.push(category);
    candidatesById.set(category.id, candidates);
  }

  return [...candidatesById.entries()]
    .sort(([leftId], [rightId]) => leftId - rightId)
    .map(([, candidates]) => [...candidates].sort(compareDuplicateCandidates)[0]);
}

export function buildProductCategoryTree(categories: Category[]): ProductCategoryTreeModel {
  const deduplicated = deduplicateCategories(categories);
  const categoryById = new Map(deduplicated.map((category) => [category.id, category]));
  const rawParentById = new Map<number, number | null>();

  for (const category of deduplicated) {
    const parentId = category.parentId;
    rawParentById.set(
      category.id,
      parentId === null || parentId === 0 || !categoryById.has(parentId) ? null : parentId
    );
  }

  const reachesRoot = new Map<number, boolean>();
  for (const category of deduplicated) {
    if (reachesRoot.has(category.id)) continue;

    const path: number[] = [];
    const pathPositions = new Map<number, number>();
    let currentId = category.id;
    let reachable = true;

    while (true) {
      const knownResult = reachesRoot.get(currentId);
      if (knownResult !== undefined) {
        reachable = knownResult;
        break;
      }
      if (pathPositions.has(currentId)) {
        reachable = false;
        break;
      }

      pathPositions.set(currentId, path.length);
      path.push(currentId);
      const parentId = rawParentById.get(currentId) ?? null;
      if (parentId === null) break;
      currentId = parentId;
    }

    for (const id of path) reachesRoot.set(id, reachable);
  }

  const parentById = new Map<number, number | null>();
  for (const category of deduplicated) {
    parentById.set(category.id, reachesRoot.get(category.id) ? rawParentById.get(category.id) ?? null : null);
  }

  const nodeById = new Map<number, ProductCategoryNode>(deduplicated.map((category) => [
    category.id,
    { ...category, parentId: parentById.get(category.id) ?? null, children: [] }
  ]));
  const roots: ProductCategoryNode[] = [];

  for (const node of nodeById.values()) {
    const parentId = parentById.get(node.id) ?? null;
    const parent = parentId === null ? undefined : nodeById.get(parentId);
    if (parent) parent.children.push(node);
    else roots.push(node);
  }

  const sortNodes = (nodes: ProductCategoryNode[]) => {
    nodes.sort(compareTreeOrder);
    nodes.forEach((node) => sortNodes(node.children));
  };
  sortNodes(roots);

  return { categories: deduplicated, roots, parentById };
}
