import type { PermissionAction, PermissionDataScope, PermissionModule } from './types';

const SCOPE_RANK: Record<PermissionDataScope, number> = {
  self: 0,
  department: 1,
  'department-and-descendants': 2,
  company: 3
};

export type ModuleSelectionState = 'checked' | 'mixed' | 'unchecked';

export function toggleModulePermission(
  module: PermissionModule,
  selected: string[],
  action: PermissionAction,
  checked: boolean
): string[] {
  if (!module.supportedActions.includes(action)) return [...selected];

  const codes = new Set(selected);
  const code = `${module.key}:${action}`;
  if (checked) {
    codes.add(code);
    if (action !== 'view') codes.add(`${module.key}:view`);
  } else if (action === 'view') {
    module.supportedActions.forEach((item) => codes.delete(`${module.key}:${item}`));
  } else {
    codes.delete(code);
  }

  return [...codes].sort();
}

export function moduleSelectionState(
  module: PermissionModule,
  selected: string[]
): ModuleSelectionState {
  const selectedCount = module.supportedActions.reduce(
    (count, action) => count + (selected.includes(`${module.key}:${action}`) ? 1 : 0),
    0
  );

  if (selectedCount === 0) return 'unchecked';
  if (selectedCount === module.supportedActions.length) return 'checked';
  return 'mixed';
}

export function widestDataScope(scopes: PermissionDataScope[]): PermissionDataScope | null {
  return scopes.reduce<PermissionDataScope | null>((widest, scope) => {
    if (widest === null || SCOPE_RANK[scope] > SCOPE_RANK[widest]) return scope;
    return widest;
  }, null);
}
