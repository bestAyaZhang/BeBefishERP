import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import ErpLayout from '../layouts/ErpLayout.vue';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';
import CustomerFormView from '../features/masterdata/views/CustomerFormView.vue';
import InventoryBalanceView from '../features/inventory/views/InventoryBalanceView.vue';
import InventoryLedgerView from '../features/inventory/views/InventoryLedgerView.vue';
import StockAdjustmentView from '../features/inventory/views/StockAdjustmentView.vue';
import LoginView from '../views/LoginView.vue';
import { restoreCurrentUser } from '../services/authSession';

const placeholderPages: RouteRecordRaw[] = [
  {
    path: 'workbench',
    name: 'workbench',
    component: () => import('../views/WorkbenchView.vue')
  },
  {
    path: 'products',
    name: 'products',
    component: () => import('../features/product/views/ProductListView.vue')
  },
  {
    path: 'products/new',
    name: 'product-new',
    component: () => import('../features/product/editor/ProductEditorView.vue')
  },
  {
    path: 'products/:id/edit',
    name: 'product-edit',
    component: () => import('../features/product/editor/ProductEditorView.vue')
  },
  {
    path: 'products/:id',
    name: 'product-detail',
    component: () => import('../features/product/views/ProductDetailView.vue')
  },
  { path: 'categories', name: 'categories', component: () => import('../features/masterdata/views/CategoryView.vue') },
  { path: 'customers/new', name: 'customer-new', component: CustomerFormView },
  { path: 'customers/:id/edit', name: 'customer-edit', component: CustomerFormView },
  { path: 'customers', name: 'customers', component: () => import('../features/masterdata/views/CustomerView.vue') },
  { path: 'suppliers', name: 'suppliers', component: () => import('../features/masterdata/views/SupplierView.vue') },
  { path: 'platforms', name: 'platforms', component: () => import('../features/platform/PlatformManagementView.vue'),
    meta: { requiredPermission: 'platform:view' } },
  { path: 'warehouses', name: 'warehouses', component: () => import('../features/masterdata/views/WarehouseView.vue') },
  {
    path: 'inventory/warehouse-canvas',
    name: 'warehouse-canvas',
    component: () => import('../features/inventory/views/WarehouseCanvasView.vue'),
    meta: { flushContent: true }
  },
  { path: 'inventory/balances', name: 'inventory-balances', component: InventoryBalanceView, meta: { flushContent: true } },
  { path: 'inventory/ledger', name: 'inventory-ledger', component: InventoryLedgerView },
  { path: 'inventory/adjustments', name: 'inventory-adjustments', component: StockAdjustmentView },
  { path: 'inventory/stocktakes', name: 'inventory-stocktakes', component: () => import('../features/inventory/views/StocktakeTaskListView.vue') },
  {
    path: 'inventory/stocktakes/:id',
    name: 'inventory-stocktake-execution',
    component: () => import('../features/inventory/views/StocktakeExecutionView.vue'),
    props: (route) => ({ taskId: route.params.id })
  },
  { path: 'organization/employees', name: 'organization-employees', component: () => import('../features/organization/views/EmployeeManagementView.vue') },
  { path: 'organization/departments', name: 'organization-departments', component: () => import('../features/organization/views/DepartmentManagementView.vue') },
  { path: 'organization/positions', name: 'organization-positions', component: () => import('../features/organization/views/PositionManagementView.vue') },
  {
    path: 'organization/permissions',
    name: 'organization-permissions',
    component: () => import('../features/permission/views/PermissionManagementView.vue'),
    meta: { requiredPermission: 'system:role:view' }
  },
  { path: 'sales/create', name: 'sales-create', component: () => import('../features/sales/SalesCreateView.vue') },
  { path: 'sales/orders/:id', name: 'sales-order-detail', component: () => import('../features/sales/SalesOrderDetailView.vue'), props: (route) => ({ orderId: Number(route.params.id) }) },
  { path: 'sales/orders', name: 'sales-orders', component: () => import('../features/sales/SalesOrdersView.vue') },
  { path: 'shipping/new', name: 'shipping-new', component: () => import('../features/shipping/ShipmentFormView.vue'), meta: { requiredPermission: 'shipping:create' } },
  { path: 'shipping/list', name: 'shipping-list', component: () => import('../features/shipping/ShipmentListView.vue'), meta: { requiredPermission: 'shipping:view', flushContent: true } },
  { path: 'shipping/:id/edit', name: 'shipping-edit', component: () => import('../features/shipping/ShipmentFormView.vue'), meta: { requiredPermission: 'shipping:edit' } },
  { path: 'shipping/:id', name: 'shipping-detail', component: () => import('../features/shipping/ShipmentDetailView.vue'), meta: { requiredPermission: 'shipping:view' } },
  { path: 'finance/receipts', name: 'receipts', component: () => import('../views/PlaceholderView.vue'), props: { title: '收款记录' } },
  { path: 'finance/receivables', name: 'receivables', component: () => import('../views/PlaceholderView.vue'), props: { title: '欠款应收' } }
];

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'workbench' } },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/auth/feishu/result',
      name: 'feishu-login-result',
      component: () => import('../views/FeishuLoginResultView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: ErpLayout,
      meta: { requiresAuth: true },
      children: placeholderPages
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'workbench' } }
  ]
});

router.beforeEach((to) => {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  if (to.meta.requiresAuth && !accessToken) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.name === 'login' && accessToken) {
    return { name: 'workbench' };
  }
  if (typeof to.meta.requiredPermission === 'string') {
    const user = restoreCurrentUser();
    if (!user?.permissions.includes(to.meta.requiredPermission)) return { name: 'workbench' };
  }
  return true;
});

export default router;
