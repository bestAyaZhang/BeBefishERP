import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import ErpLayout from '../layouts/ErpLayout.vue';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';
import CustomerFormView from '../features/masterdata/views/CustomerFormView.vue';
import InventoryBalanceView from '../features/inventory/views/InventoryBalanceView.vue';
import InventoryLedgerView from '../features/inventory/views/InventoryLedgerView.vue';
import StockAdjustmentView from '../features/inventory/views/StockAdjustmentView.vue';
import LoginView from '../views/LoginView.vue';

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
  { path: 'warehouses', name: 'warehouses', component: () => import('../features/masterdata/views/WarehouseView.vue') },
  {
    path: 'inventory/warehouse-canvas',
    name: 'warehouse-canvas',
    component: () => import('../features/inventory/views/WarehouseCanvasView.vue'),
    meta: { flushContent: true }
  },
  { path: 'inventory/balances', name: 'inventory-balances', component: InventoryBalanceView },
  { path: 'inventory/ledger', name: 'inventory-ledger', component: InventoryLedgerView },
  { path: 'inventory/adjustments', name: 'inventory-adjustments', component: StockAdjustmentView },
  { path: 'sales/create', name: 'sales-create', component: () => import('../features/sales/SalesCreateView.vue') },
  { path: 'sales/orders/:id', name: 'sales-order-detail', component: () => import('../features/sales/SalesOrderDetailView.vue'), props: (route) => ({ orderId: Number(route.params.id) }) },
  { path: 'sales/orders', name: 'sales-orders', component: () => import('../features/sales/SalesOrdersView.vue') },
  { path: 'finance/receipts', name: 'receipts', component: () => import('../views/PlaceholderView.vue'), props: { title: '收款记录' } },
  { path: 'finance/receivables', name: 'receivables', component: () => import('../views/PlaceholderView.vue'), props: { title: '欠款应收' } }
];

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'workbench' } },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
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
  return true;
});

export default router;
