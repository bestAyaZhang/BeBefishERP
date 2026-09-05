import { describe, expect, it } from 'vitest';
import { PERMISSION_MODULES } from './permissionCatalog';
import { moduleSelectionState, toggleModulePermission, widestDataScope } from './permissionRules';

describe('permissionRules', () => {
  const product = PERMISSION_MODULES.find((item) => item.key === 'product')!;

  it('matches the exact approved module and action catalog', () => {
    expect(PERMISSION_MODULES.map(({ key, label, supportedActions }) => ({ key, label, supportedActions }))).toEqual([
      { key: 'dashboard', label: '工作台', supportedActions: ['view'] },
      { key: 'product', label: '商品', supportedActions: ['view', 'create', 'edit', 'delete', 'export'] },
      { key: 'category', label: '分类', supportedActions: ['view', 'create', 'edit', 'delete', 'export'] },
      { key: 'customer', label: '客户', supportedActions: ['view', 'create', 'edit', 'delete', 'export'] },
      { key: 'supplier', label: '供应商', supportedActions: ['view', 'create', 'edit', 'delete', 'export'] },
      { key: 'warehouse', label: '仓库', supportedActions: ['view', 'create', 'edit', 'delete', 'export'] },
      { key: 'inventory', label: '库存', supportedActions: ['view', 'create', 'edit', 'approve', 'export'] },
      { key: 'sales', label: '销售', supportedActions: ['view', 'create', 'edit', 'delete', 'approve', 'export'] },
      { key: 'finance', label: '财务', supportedActions: ['view', 'create', 'edit', 'approve', 'export'] },
      { key: 'organization', label: '组织架构', supportedActions: ['view', 'create', 'edit', 'delete', 'export', 'sync'] }
    ]);
  });

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
