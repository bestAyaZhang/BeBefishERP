import type { PermissionAction, PermissionModule } from './types';

const VIEW: PermissionAction[] = ['view'];
const STANDARD: PermissionAction[] = ['view', 'create', 'edit', 'delete', 'export'];
const INVENTORY: PermissionAction[] = ['view', 'create', 'edit', 'approve', 'export'];
const SALES: PermissionAction[] = ['view', 'create', 'edit', 'delete', 'approve', 'export'];
const FINANCE: PermissionAction[] = ['view', 'create', 'edit', 'approve', 'export'];

export const PERMISSION_MODULES: PermissionModule[] = [
  { key: 'dashboard', label: '工作台', description: '查看工作台经营概览与待办信息', supportedActions: VIEW },
  { key: 'product', label: '商品', description: '维护商品资料及商品导出信息', supportedActions: STANDARD },
  { key: 'category', label: '分类', description: '维护商品分类及分类导出信息', supportedActions: STANDARD },
  { key: 'customer', label: '客户', description: '维护客户资料及客户导出信息', supportedActions: STANDARD },
  { key: 'supplier', label: '供应商', description: '维护供应商资料及供应商导出信息', supportedActions: STANDARD },
  { key: 'warehouse', label: '仓库', description: '维护仓库资料及仓库导出信息', supportedActions: STANDARD },
  { key: 'inventory', label: '库存', description: '维护库存资料、审核库存并导出信息', supportedActions: INVENTORY },
  { key: 'sales', label: '销售', description: '维护销售单据、审核销售并导出信息', supportedActions: SALES },
  { key: 'shipping', label: '发货管理', description: '维护发货记录及物流下单', supportedActions: ['view', 'create', 'edit', 'order'] },
  { key: 'finance', label: '财务', description: '维护财务资料、审核财务并导出信息', supportedActions: FINANCE },
  { key: 'organization', label: '组织架构', description: '维护组织架构资料、导出与飞书同步', supportedActions: [...STANDARD, 'sync'] }
];
