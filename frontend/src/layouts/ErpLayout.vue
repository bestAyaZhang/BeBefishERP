<script setup lang="ts">
import { CircleUserRound, LogOut, Menu, Search, Settings2, X } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SidebarNav from '../components/navigation/SidebarNav.vue';
import MessageHost from '../components/feedback/MessageHost.vue';
import { getCurrentUser } from '../services/auth';
import { clearCurrentUser, currentUser, saveCurrentUser } from '../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';

const DESKTOP_MEDIA_QUERY = '(min-width: 1024px)';
const mobileNavigationOpen = ref(false);
const desktopViewport = ref(false);
const mobileNavigationTrigger = ref<HTMLButtonElement | null>(null);
const mobileNavigationDrawer = ref<HTMLElement | null>(null);
const mobileNavigationClose = ref<HTMLButtonElement | null>(null);
let desktopMediaQueryList: MediaQueryList | null = null;
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
  products: { group: '主数据', groupRoute: 'products', title: '商品资料', titleRoute: 'products' },
  'product-detail': { group: '主数据', groupRoute: 'products', title: '商品资料', titleRoute: 'products' },
  'product-new': { group: '主数据', groupRoute: 'products', title: '商品资料', titleRoute: 'product-new' },
  'product-edit': { group: '主数据', groupRoute: 'products', title: '商品资料', titleRoute: 'products' },
  customers: { group: 'Master Data', groupRoute: 'categories', title: 'Customers', titleRoute: 'customers' },
  'customer-new': { group: 'Master Data', groupRoute: 'customers', title: 'New Customer', titleRoute: 'customer-new' },
  'customer-edit': { group: 'Master Data', groupRoute: 'customers', title: 'Edit Customer', titleRoute: 'customer-edit' },
  suppliers: { group: 'Master Data', groupRoute: 'categories', title: 'Suppliers', titleRoute: 'suppliers' },
  warehouses: { group: 'Master Data', groupRoute: 'categories', title: 'Warehouses', titleRoute: 'warehouses' },
  'inventory-balances': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Balances', titleRoute: 'inventory-balances' },
  'inventory-ledger': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Ledger', titleRoute: 'inventory-ledger' },
  'inventory-adjustments': { group: 'Inventory', groupRoute: 'inventory-balances', title: 'Adjustments', titleRoute: 'inventory-adjustments' },
  'organization-employees': { group: '组织架构', groupRoute: 'organization-employees', title: '员工管理', titleRoute: 'organization-employees' },
  'organization-departments': { group: '组织架构', groupRoute: 'organization-employees', title: '部门管理', titleRoute: 'organization-departments' },
  'organization-positions': { group: '组织架构', groupRoute: 'organization-employees', title: '岗位管理', titleRoute: 'organization-positions' },
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
const globalSearchPlaceholder = computed(() => {
  const routeName = String(route.name);
  if (routeName.startsWith('organization-')) return '搜索员工、手机号或岗位';
  if (routeName.startsWith('product')) return '搜索商品、货号或供应商';
  return 'Search here';
});
const routeOwnsHeader = computed(() => route.meta.ownsPrototypeHeader === true);
const mobileNavigationModalActive = computed(() => mobileNavigationOpen.value && !desktopViewport.value);

provide('openMobileNavigation', openMobileNavigation);

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown);
  if (typeof window.matchMedia === 'function') {
    desktopMediaQueryList = window.matchMedia(DESKTOP_MEDIA_QUERY);
    desktopViewport.value = desktopMediaQueryList.matches;
    desktopMediaQueryList.addEventListener('change', handleDesktopViewportChange);
  }

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
  desktopMediaQueryList?.removeEventListener('change', handleDesktopViewportChange);
  desktopMediaQueryList = null;
});

watch(() => route.fullPath, () => closeMobileNavigation());

function closeMobileNavigation({ restoreFocus = true }: { restoreFocus?: boolean } = {}) {
  if (!mobileNavigationOpen.value) return;
  mobileNavigationOpen.value = false;
  if (restoreFocus) nextTick(() => mobileNavigationTrigger.value?.focus());
}

async function openMobileNavigation() {
  if (desktopViewport.value) return;
  mobileNavigationOpen.value = true;
  await nextTick();
  mobileNavigationClose.value?.focus();
}

function handleDesktopViewportChange(event: MediaQueryListEvent) {
  desktopViewport.value = event.matches;
  if (event.matches) closeMobileNavigation({ restoreFocus: false });
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
  if (!mobileNavigationModalActive.value) return;
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
    <div data-testid="erp-layout-grid" class="grid min-h-screen grid-cols-1 lg:grid-cols-[244px_minmax(0,1fr)]" :inert="mobileNavigationModalActive || undefined" :aria-hidden="mobileNavigationModalActive ? 'true' : undefined">
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
            <label data-testid="erp-global-search-shell" class="hidden h-9 w-[220px] shrink-0 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 shadow-none sm:flex lg:w-[280px]">
              <Search class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
              <input data-testid="erp-global-search" v-model="searchQuery" class="min-w-0 flex-1 border-0 bg-transparent text-sm font-medium outline-none placeholder:text-slate-400" :placeholder="globalSearchPlaceholder" />
            </label>
            <button data-testid="erp-account-action" class="hidden h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 transition hover:border-slate-300 hover:text-[#25314d] sm:flex" type="button" aria-label="账户">
              <CircleUserRound class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button data-testid="erp-settings-action" class="hidden h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 transition hover:border-slate-300 hover:text-[#25314d] sm:flex" type="button" aria-label="设置">
              <Settings2 class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button data-testid="erp-logout-action" class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 transition hover:border-rose-200 hover:text-rose-500" type="button" aria-label="退出登录" title="退出登录" @click="handleLogout">
              <LogOut class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div data-testid="erp-page-content" class="min-w-0 p-4 lg:px-8 lg:pb-3 lg:pt-8">
          <RouterView />
        </div>
      </section>
    </div>

    <div v-if="mobileNavigationModalActive" data-testid="erp-mobile-navigation" class="fixed inset-0 z-50 bg-slate-950/35 lg:hidden" @click="closeMobileNavigation()">
      <aside ref="mobileNavigationDrawer" data-testid="erp-mobile-drawer" class="relative h-full w-72 bg-white shadow-xl" role="dialog" aria-modal="true" aria-label="主导航" @click.stop>
        <button ref="mobileNavigationClose" data-testid="erp-mobile-close" type="button" class="absolute right-3 top-3 z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-100 hover:text-[#25314d]" aria-label="关闭菜单" @click="closeMobileNavigation()">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
        <SidebarNav @navigate="closeMobileNavigation" />
      </aside>
    </div>
  </main>
</template>
