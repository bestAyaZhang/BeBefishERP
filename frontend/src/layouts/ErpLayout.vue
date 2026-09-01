<script setup lang="ts">
import { CircleUserRound, Menu, PanelLeftClose, Settings2 } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SidebarNav from '../components/navigation/SidebarNav.vue';
import MessageHost from '../components/feedback/MessageHost.vue';
import { getCurrentUser } from '../services/auth';
import { clearCurrentUser, currentUser, saveCurrentUser } from '../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';

const mobileNavigationOpen = ref(false);
const searchQuery = ref('');
const router = useRouter();
const route = useRoute();

type PageHeader = {
  group: string;
  groupRoute: string;
  title: string;
  titleRoute: string;
};

const pageHeaders: Record<string, PageHeader> = {
  workbench: { group: 'Dashboards', groupRoute: 'workbench', title: 'Analytics', titleRoute: 'workbench' },
  categories: { group: 'Master Data', groupRoute: 'categories', title: 'Categories', titleRoute: 'categories' },
  products: { group: 'Master Data', groupRoute: 'products', title: 'Products', titleRoute: 'products' },
  'product-detail': { group: 'Master Data', groupRoute: 'products', title: 'Product Detail', titleRoute: 'products' },
  'product-new': { group: 'Master Data', groupRoute: 'products', title: 'New Product', titleRoute: 'product-new' },
  'product-edit': { group: 'Master Data', groupRoute: 'products', title: 'Edit Product', titleRoute: 'products' },
  customers: { group: 'Master Data', groupRoute: 'categories', title: 'Customers', titleRoute: 'customers' },
  'customer-new': { group: 'Master Data', groupRoute: 'customers', title: 'New Customer', titleRoute: 'customer-new' },
  'customer-edit': { group: 'Master Data', groupRoute: 'customers', title: 'Edit Customer', titleRoute: 'customer-edit' },
  suppliers: { group: 'Master Data', groupRoute: 'categories', title: 'Suppliers', titleRoute: 'suppliers' },
  warehouses: { group: 'Master Data', groupRoute: 'categories', title: 'Warehouses', titleRoute: 'warehouses' },
  'inventory-balances': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Balances', titleRoute: 'inventory-balances' },
  'inventory-ledger': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Ledger', titleRoute: 'inventory-ledger' },
  'inventory-adjustments': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Adjustments', titleRoute: 'inventory-adjustments' },
  'sales-create': { group: 'Sales', groupRoute: 'sales-orders', title: 'Create Order', titleRoute: 'sales-create' },
  'sales-orders': { group: 'Sales', groupRoute: 'sales-orders', title: 'Orders', titleRoute: 'sales-orders' },
  receipts: { group: 'Finance', groupRoute: 'receipts', title: 'Receipts', titleRoute: 'receipts' },
  receivables: { group: 'Finance', groupRoute: 'receipts', title: 'Receivables', titleRoute: 'receivables' }
};

const pageHeader = computed<PageHeader>(() => pageHeaders[String(route.name)] ?? {
  group: 'Workspace',
  groupRoute: 'workbench',
  title: 'Overview',
  titleRoute: 'workbench'
});
const routeOwnsHeader = computed(() => route.meta.ownsPrototypeHeader === true);

provide('openMobileNavigation', () => {
  mobileNavigationOpen.value = true;
});

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown);

  const accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  if (currentUser.value || !accessToken) return;

  try {
    saveCurrentUser(await getCurrentUser(accessToken));
  } catch {
    // The HTTP layer handles expired sessions; keep the layout empty until login succeeds again.
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
});

watch(() => route.fullPath, closeMobileNavigation);

function closeMobileNavigation() {
  mobileNavigationOpen.value = false;
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMobileNavigation();
}

async function handleLogout() {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  clearCurrentUser();
  await router.push({ name: 'login' });
}
</script>

<template>
  <main data-testid="erp-shell" class="bebefish-prototype min-h-screen bg-[#f6f7fb] text-[#25314d]">
    <MessageHost />
    <div data-testid="erp-layout-grid" class="grid min-h-screen grid-cols-1 lg:grid-cols-[244px_minmax(0,1fr)]">
      <aside class="sticky top-0 hidden h-screen min-h-0 border-r border-slate-200 bg-white lg:flex">
        <SidebarNav />
      </aside>

      <section data-testid="erp-main" class="min-w-0">
        <header v-if="!routeOwnsHeader" data-testid="erp-topbar" class="flex h-16 items-center justify-between gap-5 border-b border-slate-200 bg-white px-4 lg:px-6">
          <div class="flex min-w-0 items-center gap-4">
            <button class="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white hover:text-[#25314d] lg:hidden" type="button" aria-label="打开菜单" @click="mobileNavigationOpen = true">
              <Menu class="h-5 w-5" aria-hidden="true" />
            </button>
            <nav class="flex items-center gap-2 text-sm" aria-label="Breadcrumb">
              <RouterLink data-testid="breadcrumb-group" :to="{ name: pageHeader.groupRoute }" class="font-medium text-slate-400 transition hover:text-[#536dff]">{{ pageHeader.group }}</RouterLink>
              <span class="text-slate-300">/</span>
              <RouterLink data-testid="breadcrumb-current" :to="{ name: pageHeader.titleRoute }" class="font-black text-[#0f172a]">{{ pageHeader.title }}</RouterLink>
            </nav>
          </div>

          <div class="ml-auto flex items-center gap-3">
            <label class="hidden h-9 w-[220px] items-center rounded-md border border-slate-200 bg-white px-3 shadow-none sm:flex">
              <input v-model="searchQuery" class="w-full border-0 bg-transparent text-sm font-medium outline-none placeholder:text-slate-400" placeholder="Search here" />
            </label>
            <button class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" aria-label="账户">
              <CircleUserRound class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" aria-label="设置">
              <Settings2 class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-rose-500" type="button" aria-label="退出登录" title="退出登录" @click="handleLogout">
              <PanelLeftClose class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div data-testid="erp-page-content" class="min-w-0 p-4 lg:p-6">
          <RouterView />
        </div>
      </section>
    </div>

    <div v-if="mobileNavigationOpen" data-testid="erp-mobile-navigation" class="fixed inset-0 z-50 bg-slate-950/35 lg:hidden" @click="closeMobileNavigation">
      <aside data-testid="erp-mobile-drawer" class="h-full w-72 bg-white shadow-xl" role="dialog" aria-modal="true" aria-label="主导航" @click.stop>
        <SidebarNav @navigate="closeMobileNavigation" />
      </aside>
    </div>
  </main>
</template>
