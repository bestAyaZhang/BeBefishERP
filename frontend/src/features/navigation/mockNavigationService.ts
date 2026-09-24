import type { NavigationCatalog } from './types';

const mockNavigationCatalog: NavigationCatalog = {
  sidebar: [
    { id: 'workbench', label: '工作台', icon: 'dashboard', routeName: 'workbench', permission: 'dashboard:view' },
    { id: 'products', label: '商品管理', icon: 'product', routeName: 'products', permission: 'product:view' },
    { id: 'categories', label: '分类管理', icon: 'category', routeName: 'categories', permission: 'masterdata:view' },
    { id: 'customers', label: '客户管理', icon: 'customer', routeName: 'customers', permission: 'masterdata:view' },
    { id: 'suppliers', label: '供应商管理', icon: 'supplier', routeName: 'suppliers', permission: 'masterdata:view' },
    { id: 'warehouses', label: '仓库管理', icon: 'warehouse', routeName: 'warehouses', permission: 'masterdata:view' },
    {
      id: 'inventory',
      label: '库存管理',
      icon: 'inventory',
      children: [
        { label: '仓库画布', routeName: 'warehouse-canvas', permission: 'inventory:view' },
        { label: '库存余额', routeName: 'inventory-balances', permission: 'inventory:view' },
        { label: '库存流水', routeName: 'inventory-ledger', permission: 'inventory:view' },
        { label: '库存调整', routeName: 'inventory-adjustments', permission: 'inventory:adjust' }
      ]
    },
    {
      id: 'organization',
      label: '组织架构',
      icon: 'organization',
      children: [
        { label: '员工管理', routeName: 'organization-employees', permission: 'organization:view' },
        { label: '部门管理', routeName: 'organization-departments', permission: 'organization:view' },
        { label: '岗位管理', routeName: 'organization-positions', permission: 'organization:view' }
      ]
    },
    { id: 'permissions', label: '权限管理', icon: 'permission', routeName: 'organization-permissions', permission: 'system:role:view' },
    { id: 'sales-orders', label: '销售单据', icon: 'sales-orders', routeName: 'sales-orders', permission: 'sales:view' },
    {
      id: 'shipping',
      label: '发货管理',
      icon: 'shipping',
      children: [{ label: '发货列表', routeName: 'shipping-list', permission: 'shipping:view' }]
    },
    {
      id: 'finance',
      label: '财务管理',
      icon: 'finance',
      children: [
        { label: '收款记录', routeName: 'receipts', permission: 'finance:receipt' },
        { label: '欠款应收', routeName: 'receivables', permission: 'finance:view' }
      ]
    }
  ],
  workspace: [
    { id: 'dashboard', key: 'dashboard', label: '工作台', icon: 'dashboard', permission: 'dashboard:view' },
    { id: 'products', key: 'products', label: '产品资料', icon: 'product', permission: 'product:view' },
    { id: 'tasks', key: 'tasks', label: '任务清单', icon: 'tasks', permission: 'dashboard:view' },
    { id: 'inventory-ledger', key: 'inventory', label: '库存流水', icon: 'inventory', permission: 'inventory:view' },
    { id: 'purchase-receipt', key: 'purchase', label: '采购入库', icon: 'purchase', permission: 'inventory:view' },
    { id: 'sales-orders', key: 'sales', label: '销售单据', icon: 'sales', permission: 'sales:view' },
    { id: 'schedule', key: 'calendar', label: '日程排班', icon: 'calendar', permission: 'masterdata:view' },
    { id: 'messages', key: 'messages', label: '消息中心', icon: 'messages', permission: 'dashboard:view' }
  ]
};

export function getMockNavigationCatalog(): NavigationCatalog {
  return {
    sidebar: mockNavigationCatalog.sidebar.map((item) => ({
      ...item,
      children: item.children?.map((child) => ({ ...child }))
    })),
    workspace: mockNavigationCatalog.workspace.map((item) => ({ ...item }))
  };
}
