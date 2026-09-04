import { describe, expect, it } from 'vitest';
import { PERMISSION_MODULES } from './permissionCatalog';
import { moduleSelectionState, toggleModulePermission, widestDataScope } from './permissionRules';

describe('permissionRules', () => {
  const product = PERMISSION_MODULES.find((item) => item.key === 'product')!;

  it('selects view with a dependent action and removes dependants when view is cleared', () => {
    const withCreate = toggleModulePermission(product, [], 'create', true);
    expect(withCreate).toEqual(expect.arrayContaining(['product:view', 'product:create']));
    expect(toggleModulePermission(product, withCreate, 'view', false)).toEqual([]);
  });

  it('reports checked, mixed and unchecked module states', () => {
    expect(moduleSelectionState(product, [])).toBe('unchecked');
    expect(moduleSelectionState(product, ['product:view'])).toBe('mixed');
    expect(moduleSelectionState(product, [
      'product:view', 'product:create', 'product:edit', 'product:delete', 'product:export'
    ])).toBe('checked');
  });

  it('uses the widest active data scope', () => {
    expect(widestDataScope(['self', 'department', 'department-and-descendants'])).toBe('department-and-descendants');
    expect(widestDataScope([])).toBeNull();
  });
});
