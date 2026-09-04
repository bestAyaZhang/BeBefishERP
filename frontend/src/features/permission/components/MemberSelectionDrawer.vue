<script setup lang="ts">
import { ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import OrganizationPagination from '../../organization/components/OrganizationPagination.vue';
import type { RoleMemberPage } from '../types';

const props = defineProps<{ open: boolean; page: RoleMemberPage; selectedIds: number[]; saving: boolean; departments?: { id: number; name: string }[] }>();
const emit = defineEmits<{ close: []; keyword: [keyword: string]; department: [departmentId: number | null]; page: [page: number]; 'page-size': [pageSize: number]; 'update:selectedIds': [ids: number[]]; submit: [employeeIds: number[]] }>();
const pendingIds = ref<number[]>([]);
watch(() => [props.open, props.selectedIds] as const, () => { pendingIds.value = [...props.selectedIds]; }, { immediate: true });
function toggle(employeeId: number, checked: boolean) { const next = new Set(pendingIds.value); checked ? next.add(employeeId) : next.delete(employeeId); pendingIds.value = [...next]; emit('update:selectedIds', pendingIds.value); }
function submit() { if (!props.saving && pendingIds.value.length) emit('submit', pendingIds.value); }
</script>

<template>
  <AccessibleDialog :open="open" title="添加成员" description="按组织和关键词筛选后批量添加成员" test-id="member-selection-drawer" body-test-id="member-selection-body" footer-test-id="member-selection-footer" close-test-id="close-member-selection" overlay-class="items-stretch justify-end" panel-class="h-full w-[min(720px,100vw)] rounded-none border-y-0 border-r-0" body-class="min-h-0 flex-1 overflow-y-auto px-6 py-5" footer-class="px-6 py-4" @cancel="emit('close')">
    <div class="mb-4 flex gap-3"><input data-dialog-initial-focus data-testid="candidate-keyword" aria-label="搜索可添加成员" type="search" class="h-9 flex-1 rounded-[6px] border border-slate-200 px-3 text-sm" placeholder="搜索姓名、手机号、工号" @input="emit('keyword', ($event.target as HTMLInputElement).value)" /><select data-testid="candidate-department" aria-label="筛选候选成员部门" class="h-9 rounded-[6px] border border-slate-200 px-3 text-sm" @change="emit('department', ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"><option value="">全部组织</option><option v-for="department in departments ?? []" :key="department.id" :value="department.id">{{ department.name }}</option></select></div>
    <div class="overflow-hidden rounded-[6px] border border-slate-200"><label v-for="member in page.records" :key="member.employeeId" class="flex items-center gap-3 border-b border-slate-200 px-4 py-3 last:border-b-0"><input :data-testid="`candidate-select-${member.employeeId}`" :aria-label="`选择成员 ${member.employeeName}`" type="checkbox" :checked="pendingIds.includes(member.employeeId)" @change="toggle(member.employeeId, ($event.target as HTMLInputElement).checked)" /><span class="min-w-0 flex-1"><span class="block text-sm font-medium text-[#25314d]">{{ member.employeeName }}</span><span class="block text-xs text-slate-400">{{ member.employeeNo }} · {{ member.departmentName }} / {{ member.positionName }}</span></span></label><p v-if="page.records.length === 0" class="p-8 text-center text-sm text-slate-400">没有可添加的成员</p></div>
    <OrganizationPagination class="mt-4" :page="page.page" :page-size="page.pageSize" :total="page.total" @page="emit('page', $event)" @page-size="emit('page-size', $event)" />
    <template #footer><span data-testid="candidate-selected-count" class="mr-auto text-sm text-slate-500">已选择 {{ pendingIds.length }} 人</span><button type="button" class="h-10 rounded-[6px] border border-slate-200 px-5 text-sm text-slate-600" @click="emit('close')">取消</button><button data-testid="member-selection-submit" type="button" class="h-10 rounded-[6px] bg-[#536dff] px-5 text-sm text-white disabled:opacity-60" :disabled="saving || pendingIds.length === 0" @click="submit">{{ saving ? '添加中...' : '添加成员' }}</button></template>
  </AccessibleDialog>
</template>
