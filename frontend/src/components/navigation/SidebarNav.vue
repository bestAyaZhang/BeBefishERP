<script setup lang="ts">
import {
  Boxes,
  ChevronDown,
  ChevronRight,
  FileText,
  FolderTree,
  LayoutDashboard,
  Package,
  ReceiptText,
  Truck,
  UsersRound,
  WalletCards,
  Warehouse
} from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { navigationService } from '../../features/navigation/navigationService';
import type { SidebarNavigationItem } from '../../features/navigation/types';
import { currentUser, restoreCurrentUser } from '../../services/authSession';

const props = withDefaults(defineProps<{ navigationItems?: SidebarNavigationItem[] }>(), {
  navigationItems: () => navigationService.getCatalog().sidebar
});
defineEmits<{ navigate: [] }>();

const route = useRoute();
restoreCurrentUser();

const sidebarIcons = {
  dashboard: LayoutDashboard,
  product: Package,
  category: FolderTree,
  customer: UsersRound,
  supplier: Truck,
  warehouse: Warehouse,
  inventory: Boxes,
  'sales-orders': FileText,
  finance: WalletCards
};

const activeRouteName = computed(() => String(route.name ?? ''));
const expandedGroups = ref<string[]>(['库存管理', '财务管理']);
const visibleMenuItems = computed<SidebarNavigationItem[]>(() => props.navigationItems.flatMap((item) => {
  if (item.routeName) return currentUser.value?.permissions.includes(item.permission ?? '') ? [item] : [];
  const children = item.children?.filter((child) => currentUser.value?.permissions.includes(child.permission)) ?? [];
  return children.length ? [{ ...item, children }] : [];
}));
const currentUserMobile = computed(() => currentUser.value?.mobile ?? '未登录');
const currentUserInitials = computed(() => currentUser.value?.mobile.slice(-2) ?? '--');

function isActive(item: SidebarNavigationItem) {
  return item.routeName === activeRouteName.value || item.children?.some((child) => child.routeName === activeRouteName.value);
}

function isExpanded(item: SidebarNavigationItem) {
  return expandedGroups.value.includes(item.label);
}

function toggleGroup(item: SidebarNavigationItem) {
  expandedGroups.value = isExpanded(item)
    ? expandedGroups.value.filter((label) => label !== item.label)
    : [...expandedGroups.value, item.label];
}
</script>

<template>
  <nav data-testid="erp-sidebar" class="flex h-full min-h-0 w-full flex-col overflow-hidden bg-white" aria-label="主导航">
    <div data-testid="erp-sidebar-brand" class="flex h-[88px] shrink-0 items-center gap-3 border-b border-slate-100 px-5">
      <div data-testid="erp-sidebar-logo" class="flex h-10 w-10 items-center justify-center rounded-lg bg-[#536dff] text-sm font-black text-white">B</div>
      <div class="min-w-0"><p class="truncate text-sm font-bold">BeBefish ERP</p><p class="text-xs font-medium text-slate-400">主数据运营中心</p></div>
    </div>
    <div data-testid="erp-sidebar-menu" class="flex-1 overflow-y-auto px-4 py-6">
      <p class="mb-4 px-2 text-xs font-semibold text-slate-400">Menu</p>
      <div class="space-y-1">
      <template v-for="item in visibleMenuItems" :key="item.label">
        <RouterLink
          v-if="item.routeName"
          :to="{ name: item.routeName }"
          class="group relative flex h-11 w-full items-center gap-3 rounded-lg px-3 text-sm font-semibold transition"
          :class="isActive(item) ? 'bg-[#f4f6ff] text-[#25314d]' : 'text-slate-400 hover:bg-slate-50 hover:text-[#25314d]'"
          @click="$emit('navigate')"
        >
          <span v-if="isActive(item)" class="absolute -left-4 h-7 w-1 rounded-r-full bg-[#536dff]"></span>
          <component :is="sidebarIcons[item.icon]" class="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{{ item.label }}</span>
          <ChevronRight v-if="isActive(item)" class="ml-auto h-4 w-4 text-slate-400" aria-hidden="true" />
        </RouterLink>
        <div v-else class="mb-1">
          <button type="button" class="group relative flex h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm font-semibold text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]" :class="{ 'bg-[#f4f6ff] text-[#25314d]': isActive(item) }" @click="toggleGroup(item)">
            <span v-if="isActive(item)" class="absolute -left-4 h-7 w-1 rounded-r-full bg-[#536dff]"></span>
            <component :is="sidebarIcons[item.icon]" class="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{{ item.label }}</span>
            <ChevronDown class="ml-auto h-4 w-4 text-slate-400 transition" :class="{ 'rotate-180': isExpanded(item) }" aria-hidden="true" />
          </button>
          <div v-if="isExpanded(item)" class="mt-1 space-y-1">
          <RouterLink
            v-for="child in item.children"
            :key="child.routeName"
            :to="{ name: child.routeName }"
            class="ml-6 flex h-9 items-center rounded-lg px-3 text-sm font-semibold text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]"
            :class="{ 'bg-blue-50 text-[#536dff]': child.routeName === activeRouteName }"
            @click="$emit('navigate')"
          >
            {{ child.label }}
          </RouterLink>
          </div>
        </div>
      </template>
      </div>
    </div>
    <div class="px-5 pb-5"><p class="mb-4 mt-2 px-2 text-xs font-semibold text-slate-400">Topics</p><div class="space-y-2"><button type="button" class="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-slate-400 hover:bg-slate-50"><span class="flex h-5 w-5 items-center justify-center rounded-md bg-amber-50 text-amber-500">•</span>商品管理</button><button type="button" class="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-slate-400 hover:bg-slate-50"><span class="flex h-5 w-5 items-center justify-center rounded-md bg-sky-50 text-sky-500">•</span>库存协同</button></div></div>
    <div data-testid="erp-sidebar-user" class="mx-5 mb-5 flex items-center gap-3 border-t border-slate-100 pt-5"><span class="flex h-11 w-11 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-600">{{ currentUserInitials }}</span><div class="min-w-0"><p class="truncate text-sm font-bold">当前登录用户</p><p class="truncate text-xs text-slate-400">{{ currentUserMobile }}</p></div><ChevronRight class="ml-auto h-4 w-4 text-slate-400" aria-hidden="true" /></div>
  </nav>
</template>
