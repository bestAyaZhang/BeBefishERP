<script setup lang="ts">
import { CircleUserRound, Menu, PanelLeftClose, Settings2, X } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SidebarNav from '../components/navigation/SidebarNav.vue';
import MessageHost from '../components/feedback/MessageHost.vue';
import { getCurrentUser } from '../services/auth';
import { clearCurrentUser, currentUser, saveCurrentUser } from '../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';

const mobileNavigationOpen = ref(false);
const mobileNavigationTrigger = ref<HTMLButtonElement | null>(null);
const mobileNavigationDrawer = ref<HTMLElement | null>(null);
const mobileNavigationClose = ref<HTMLButtonElement | null>(null);
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

provide('openMobileNavigation', openMobileNavigation);

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

watch(() => route.fullPath, () => closeMobileNavigation());

function closeMobileNavigation() {
  if (!mobileNavigationOpen.value) return;
  mobileNavigationOpen.value = false;
  nextTick(() => mobileNavigationTrigger.value?.focus());
}

async function openMobileNavigation() {
  mobileNavigationOpen.value = true;
  await nextTick();
  mobileNavigationClose.value?.focus();
}

function trapMobileNavigationFocus(event: KeyboardEvent) {
  const drawer = mobileNavigationDrawer.value;
  if (!drawer) return;

  const focusableElements = Array.from(drawer.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )).filter((element) => element.tabIndex >= 0);
  const first = focusableElements[0];
  const last = focusableElements[focusableElements.length - 1];
  if (!first || !last) return;

  const activeElement = document.activeElement;
  if (event.shiftKey && (activeElement === first || !drawer.contains(activeElement))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (activeElement === last || !drawer.contains(activeElement))) {
    event.preventDefault();
    first.focus();
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (!mobileNavigationOpen.value) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    closeMobileNavigation();
  } else if (event.key === 'Tab') {
    trapMobileNavigationFocus(event);
  }
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
    <div data-testid="erp-layout-grid" class="grid min-h-screen grid-cols-1 lg:grid-cols-[244px_minmax(0,1fr)]" :inert="mobileNavigationOpen || undefined" :aria-hidden="mobileNavigationOpen ? 'true' : undefined">
      <aside class="sticky top-0 hidden h-screen min-h-0 border-r border-slate-200 bg-white lg:flex">
        <SidebarNav />
      </aside>

      <section data-testid="erp-main" class="min-w-0">
        <header v-if="!routeOwnsHeader" data-testid="erp-topbar" class="flex h-16 items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 sm:gap-4 sm:px-4 lg:gap-5 lg:px-6">
          <div data-testid="erp-topbar-leading" class="flex min-w-0 flex-1 items-center gap-2 overflow-hidden sm:gap-4">
            <button ref="mobileNavigationTrigger" data-testid="erp-mobile-trigger" class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition hover:bg-white hover:text-[#25314d] lg:hidden" type="button" aria-label="打开菜单" @click="openMobileNavigation">
              <Menu class="h-5 w-5" aria-hidden="true" />
            </button>
            <nav data-testid="erp-breadcrumb" class="flex min-w-0 items-center gap-2 overflow-hidden text-sm" aria-label="Breadcrumb">
              <RouterLink data-testid="breadcrumb-group" :to="{ name: pageHeader.groupRoute }" class="hidden font-medium text-slate-400 transition hover:text-[#536dff] sm:block">{{ pageHeader.group }}</RouterLink>
              <span data-testid="breadcrumb-separator" class="hidden text-slate-300 sm:inline">/</span>
              <RouterLink data-testid="breadcrumb-current" :to="{ name: pageHeader.titleRoute }" class="block min-w-0 truncate font-black text-[#0f172a]">{{ pageHeader.title }}</RouterLink>
            </nav>
          </div>

          <div data-testid="erp-topbar-actions" class="ml-auto flex shrink-0 items-center gap-1 sm:gap-2 lg:gap-3">
            <label class="hidden h-9 w-[220px] shrink-0 items-center rounded-md border border-slate-200 bg-white px-3 shadow-none sm:flex">
              <input v-model="searchQuery" class="w-full border-0 bg-transparent text-sm font-medium outline-none placeholder:text-slate-400" placeholder="Search here" />
            </label>
            <button data-testid="erp-account-action" class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d] sm:flex" type="button" aria-label="账户">
              <CircleUserRound class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button data-testid="erp-settings-action" class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d] sm:flex" type="button" aria-label="设置">
              <Settings2 class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button data-testid="erp-logout-action" class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-rose-500" type="button" aria-label="退出登录" title="退出登录" @click="handleLogout">
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
      <aside ref="mobileNavigationDrawer" data-testid="erp-mobile-drawer" class="relative h-full w-72 bg-white shadow-xl" role="dialog" aria-modal="true" aria-label="主导航" @click.stop>
        <button ref="mobileNavigationClose" data-testid="erp-mobile-close" type="button" class="absolute right-3 top-3 z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-100 hover:text-[#25314d]" aria-label="关闭菜单" @click="closeMobileNavigation">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
        <SidebarNav @navigate="closeMobileNavigation" />
      </aside>
    </div>
  </main>
</template>
