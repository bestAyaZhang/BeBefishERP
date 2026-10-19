import { computed, ref, watch } from 'vue';
import { currentUser } from './authSession';

// Share expansion between the desktop sidebar and the mobile drawer.
const accountKey = computed(() => {
  const user = currentUser.value;
  if (!user) return null;
  const identity = user.mobile ? `mobile:${user.mobile}` : user.employeeId != null ? `employee:${user.employeeId}` : null;
  return identity ? `bebefish_sidebar_group_v1:${user.loginMethod}:${identity}` : null;
});
export const expandedSidebarGroup = ref<string | null>(null);

watch(accountKey, (key) => {
  expandedSidebarGroup.value = null;
  if (!key) return;
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (typeof stored === 'string') expandedSidebarGroup.value = stored;
  } catch {
    // Unavailable or invalid browser storage should leave all groups closed.
  }
}, { immediate: true, flush: 'sync' });

export function toggleSidebarGroup(groupId: string) {
  expandedSidebarGroup.value = expandedSidebarGroup.value === groupId ? null : groupId;
  if (!accountKey.value) return;
  try {
    localStorage.setItem(accountKey.value, JSON.stringify(expandedSidebarGroup.value));
  } catch {
    // Navigation remains usable when browser storage is unavailable.
  }
}
