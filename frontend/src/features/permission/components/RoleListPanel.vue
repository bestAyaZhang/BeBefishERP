<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PermissionRoleSummary } from '../types';

const props = withDefaults(defineProps<{
  roles: PermissionRoleSummary[];
  selectedRoleId: number | null;
  disabled: boolean;
  dirtyRoleIds?: number[];
}>(), { dirtyRoleIds: () => [] });

const emit = defineEmits<{ select: [roleId: number] }>();
const keyword = ref('');

const filteredRoles = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return props.roles.filter((role) => !query || [role.name, role.code].some((value) => value.toLowerCase().includes(query)));
});
const systemRoles = computed(() => filteredRoles.value.filter((role) => role.kind === 'system'));
const customRoles = computed(() => filteredRoles.value.filter((role) => role.kind === 'custom'));
const selectedIsFilteredOut = computed(() => props.selectedRoleId !== null && !filteredRoles.value.some((role) => role.id === props.selectedRoleId));

function select(roleId: number) {
  if (!props.disabled && roleId !== props.selectedRoleId) emit('select', roleId);
}
</script>

<template>
  <aside class="flex w-[280px] shrink-0 flex-col border-r border-slate-200 bg-white" aria-label="角色列表">
    <header class="border-b border-slate-200 px-4 py-4">
      <h2 class="text-base font-bold text-[#25314d]">角色列表</h2>
      <input v-model="keyword" data-testid="role-search" type="search" class="mt-3 h-9 w-full rounded-[6px] border border-slate-200 px-3 text-sm outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="搜索角色名称或编码" aria-label="搜索角色" />
      <p v-if="selectedIsFilteredOut" data-testid="selected-role-filtered-notice" class="mt-3 rounded-[6px] bg-amber-50 px-3 py-2 text-xs text-amber-700">当前角色不在筛选结果中</p>
    </header>
    <div class="min-h-0 flex-1 overflow-y-auto p-3">
      <section v-for="group in [{ label: '系统角色', roles: systemRoles }, { label: '自定义角色', roles: customRoles }]" :key="group.label" class="mb-5">
        <h3 class="mb-2 px-2 text-xs font-semibold text-slate-400">{{ group.label }}</h3>
        <p v-if="group.roles.length === 0" class="px-2 text-xs text-slate-400">暂无角色</p>
        <button v-for="role in group.roles" :key="role.id" :data-testid="`role-item-${role.id}`" type="button" :disabled="disabled" class="mb-1 flex w-full items-center gap-2 rounded-[6px] px-3 py-2.5 text-left disabled:cursor-not-allowed disabled:opacity-60" :class="role.id === selectedRoleId ? 'bg-[#f1f4ff] text-[#536dff]' : 'text-[#25314d] hover:bg-slate-50'" @click="select(role.id)">
          <span v-if="dirtyRoleIds.includes(role.id)" class="h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" aria-label="有未保存修改" />
          <span class="min-w-0 flex-1"><span class="block truncate text-sm font-medium">{{ role.name }}</span><span class="mt-0.5 block truncate text-xs text-slate-400">{{ role.memberCount }} 名成员 · {{ role.code }}</span></span>
          <span v-if="role.immutable" class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">系统</span>
          <span v-if="role.status === 'disabled'" class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">停用</span>
        </button>
      </section>
    </div>
  </aside>
</template>
