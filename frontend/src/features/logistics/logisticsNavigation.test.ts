import { describe, expect, it } from 'vitest';
import { getMockNavigationCatalog } from '../navigation/mockNavigationService';
import { PERMISSION_MODULES } from '../permission/permissionCatalog';

describe('logistics access boundary', () => {
  it('places logistics beneath shipping with its own view permission', () => {
    const children = getMockNavigationCatalog().sidebar.find(row => row.id === 'shipping')!.children!;
    expect(children).toContainEqual({ label: '物流管理', routeName: 'logistics-management', permission: 'logistics:view' });
    expect(children.find(row => row.routeName === 'shipping-list')?.permission).toBe('shipping:view');
  });
  it('separates account and tariff permissions from shipping', () => {
    expect(PERMISSION_MODULES.find(row => row.key === 'logistics')?.supportedActions).toEqual(['view', 'edit', 'configure', 'template']);
    expect(PERMISSION_MODULES.find(row => row.key === 'shipping')?.supportedActions).not.toContain('configure');
  });
});
