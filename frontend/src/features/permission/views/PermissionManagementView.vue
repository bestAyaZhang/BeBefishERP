<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import DataScopePanel from '../components/DataScopePanel.vue';
import MemberSelectionDrawer from '../components/MemberSelectionDrawer.vue';
import PermissionMatrix from '../components/PermissionMatrix.vue';
import RoleFormDrawer from '../components/RoleFormDrawer.vue';
import RoleListPanel from '../components/RoleListPanel.vue';
import RoleMembersPanel from '../components/RoleMembersPanel.vue';
import { PERMISSION_MODULES } from '../permissionCatalog';
import { permissionService, type PermissionService } from '../permissionService';
import { currentUser } from '../../../services/authSession';
import { organizationService } from '../../organization/organizationService';
import type {
  CreateRolePayload,
  PermissionDataScope,
  PermissionRole,
  PermissionRoleSummary,
  RoleMemberPage,
  UpdateRolePayload
} from '../types';

type Tab = 'permissions' | 'scope' | 'members';
type RoleFormMode = 'create' | 'edit';
type ConfigurationDraft = { permissionCodes: string[]; dataScope: PermissionDataScope };

const props = withDefaults(defineProps<{ service?: PermissionService }>(), {
  service: () => permissionService
});

const EMPTY_MEMBER_PAGE: RoleMemberPage = { records: [], page: 1, pageSize: 20, total: 0 };
const roles = ref<PermissionRoleSummary[]>([]);
const selectedRole = ref<PermissionRole | null>(null);
const savedConfiguration = ref<ConfigurationDraft | null>(null);
const configurationDraft = ref<ConfigurationDraft | null>(null);
const activeTab = ref<Tab>('permissions');
const saving = ref(false);
const saveError = ref('');
const feedback = ref('');
const roleFormOpen = ref(false);
const roleFormMode = ref<RoleFormMode>('create');
const roleFormError = ref('');
const copyFromRoleId = ref<number | null>(null);
const memberDrawerOpen = ref(false);
const memberPage = ref<RoleMemberPage>(EMPTY_MEMBER_PAGE);
const candidatePage = ref<RoleMemberPage>(EMPTY_MEMBER_PAGE);
const memberSelectedIds = ref<number[]>([]);
const candidateSelectedIds = ref<number[]>([]);
const memberQuery = ref({ page: 1, size: 20, keyword: '', departmentId: null as number | null });
const candidateQuery = ref({ page: 1, size: 20, keyword: '', departmentId: null as number | null });
const departments = ref<{ id: number; name: string }[]>([]);
const organizationSummary = ref('正在加载组织范围摘要');

const canCreate = computed(() => currentUser.value?.permissions.includes('system:role:manage') ?? false);
const canManage = computed(() => canCreate.value && Boolean(selectedRole.value) && !selectedRole.value!.immutable);
const readonly = computed(() => !canManage.value);
const dirty = computed(() => {
  if (!savedConfiguration.value || !configurationDraft.value) return false;
  const savedCodes = [...savedConfiguration.value.permissionCodes].sort();
  const draftCodes = [...configurationDraft.value.permissionCodes].sort();
  return savedConfiguration.value.dataScope !== configurationDraft.value.dataScope
    || savedCodes.length !== draftCodes.length
    || savedCodes.some((code, index) => code !== draftCodes[index]);
});
const selectedRoleSummary = computed(() => roles.value.find((role) => role.id === selectedRole.value?.id) ?? null);
const dirtyRoleIds = computed(() => dirty.value && selectedRole.value ? [selectedRole.value.id] : []);

function cloneConfiguration(role: PermissionRole): ConfigurationDraft {
  return { permissionCodes: [...role.permissionCodes], dataScope: role.dataScope };
}

function resetDraft() {
  if (!savedConfiguration.value) return;
  configurationDraft.value = {
    permissionCodes: [...savedConfiguration.value.permissionCodes],
    dataScope: savedConfiguration.value.dataScope
  };
  saveError.value = '';
}

function messageFrom(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '操作失败，请重试';
}

function confirmDiscardChanges(): boolean {
  return !dirty.value || window.confirm('当前角色存在未保存修改，是否放弃？');
}

async function refreshRoles() {
  roles.value = await props.service.listRoles();
}

async function refreshMembers() {
  if (!selectedRole.value) return;
  memberPage.value = await props.service.listMembers(selectedRole.value.id, memberQuery.value);
  memberSelectedIds.value = memberSelectedIds.value.filter((employeeId) => memberPage.value.records.some((member) => member.employeeId === employeeId && !member.lockedReason));
}

async function refreshCandidates() {
  if (!selectedRole.value) return;
  candidatePage.value = await props.service.listCandidates(selectedRole.value.id, candidateQuery.value);
  candidateSelectedIds.value = candidateSelectedIds.value.filter((employeeId) => candidatePage.value.records.some((member) => member.employeeId === employeeId));
}

async function applySelectedRole(role: PermissionRole, resetMemberContext = true) {
  selectedRole.value = role;
  savedConfiguration.value = cloneConfiguration(role);
  configurationDraft.value = cloneConfiguration(role);
  saveError.value = '';
  memberSelectedIds.value = [];
  candidateSelectedIds.value = [];
  if (resetMemberContext) {
    memberQuery.value.page = 1;
    candidateQuery.value.page = 1;
  }
  await Promise.all([refreshMembers(), refreshCandidates()]);
}

async function selectRole(roleId: number) {
  if (saving.value || selectedRole.value?.id === roleId) return;
  if (!confirmDiscardChanges()) return;
  if (dirty.value) resetDraft();
  try {
    await applySelectedRole(await props.service.getRole(roleId));
  } catch (error) {
    feedback.value = messageFrom(error);
  }
}

async function selectTab(tab: Tab) {
  if (saving.value || activeTab.value === tab) return;
  if (!confirmDiscardChanges()) return;
  if (dirty.value) resetDraft();
  activeTab.value = tab;
  if (tab === 'members') await refreshMembers();
}

async function saveConfiguration() {
  if (!selectedRole.value || !configurationDraft.value || !canManage.value || saving.value) return;
  saving.value = true;
  saveError.value = '';
  feedback.value = '';
  try {
    const updated = await props.service.saveConfiguration(selectedRole.value.id, configurationDraft.value);
    await refreshRoles();
    await applySelectedRole(updated, false);
    feedback.value = '权限配置已保存';
  } catch (error) {
    saveError.value = messageFrom(error);
  } finally {
    saving.value = false;
  }
}

function openCreateRole() {
  if (!canCreate.value || saving.value) return;
  roleFormMode.value = 'create';
  copyFromRoleId.value = null;
  roleFormError.value = '';
  roleFormOpen.value = true;
}

function openEditRole() {
  if (!canManage.value || saving.value) return;
  roleFormMode.value = 'edit';
  copyFromRoleId.value = null;
  roleFormError.value = '';
  roleFormOpen.value = true;
}

function openCopyRole() {
  if (!canManage.value || !selectedRole.value || saving.value) return;
  roleFormMode.value = 'create';
  copyFromRoleId.value = selectedRole.value.id;
  roleFormError.value = '';
  roleFormOpen.value = true;
}

function closeRoleForm() {
  if (saving.value || !confirmDiscardChanges()) return;
  if (dirty.value) resetDraft();
  roleFormOpen.value = false;
}

async function submitRole(payload: CreateRolePayload | UpdateRolePayload) {
  if (saving.value) return;
  saving.value = true;
  roleFormError.value = '';
  feedback.value = '';
  try {
    const role = roleFormMode.value === 'create'
      ? await props.service.createRole(payload as CreateRolePayload)
      : await props.service.updateRole(selectedRole.value!.id, payload as UpdateRolePayload);
    await refreshRoles();
    await applySelectedRole(role, roleFormMode.value === 'create');
    activeTab.value = 'permissions';
    roleFormOpen.value = false;
    feedback.value = roleFormMode.value === 'create' ? '角色已创建' : '角色资料已更新';
  } catch (error) {
    roleFormError.value = messageFrom(error);
  } finally {
    saving.value = false;
  }
}

async function toggleRoleStatus() {
  if (!selectedRole.value || !canManage.value || saving.value) return;
  const nextStatus = selectedRole.value.status === 'enabled' ? 'disabled' : 'enabled';
  if (nextStatus === 'disabled' && !window.confirm(`停用角色将影响 ${selectedRole.value.memberIds.length} 名成员，是否继续？`)) return;
  saving.value = true;
  feedback.value = '';
  try {
    const updated = await props.service.changeRoleStatus(selectedRole.value.id, nextStatus);
    await refreshRoles();
    await applySelectedRole(updated, false);
    feedback.value = nextStatus === 'disabled' ? '角色已停用' : '角色已启用';
  } catch (error) {
    feedback.value = messageFrom(error);
  } finally {
    saving.value = false;
  }
}

function openMemberDrawer() {
  if (!canManage.value || saving.value) return;
  candidateQuery.value = { page: 1, size: 20, keyword: '', departmentId: null };
  candidateSelectedIds.value = [];
  memberDrawerOpen.value = true;
  void refreshCandidates();
}

function closeMemberDrawer() {
  if (saving.value || !confirmDiscardChanges()) return;
  if (dirty.value) resetDraft();
  memberDrawerOpen.value = false;
}

async function addMembers(employeeIds: number[]) {
  if (!selectedRole.value || !canManage.value || saving.value) return;
  saving.value = true;
  feedback.value = '';
  try {
    const result = await props.service.addMembers(selectedRole.value.id, employeeIds);
    await refreshRoles();
    await applySelectedRole(result.role, false);
    memberDrawerOpen.value = false;
    feedback.value = `已添加 ${result.added} 人，跳过 ${result.skipped} 人`;
  } catch (error) {
    feedback.value = messageFrom(error);
  } finally {
    saving.value = false;
  }
}

async function removeMembers(employeeIds: number[]) {
  if (!selectedRole.value || !canManage.value || saving.value) return;
  const removable = employeeIds.filter((employeeId) => !memberPage.value.records.find((member) => member.employeeId === employeeId)?.lockedReason);
  if (!removable.length) return;
  saving.value = true;
  feedback.value = '';
  try {
    const result = await props.service.removeMembers(selectedRole.value.id, removable);
    await refreshRoles();
    await applySelectedRole(result.role, false);
    await refreshMembers();
    feedback.value = `已移除 ${result.removed} 人${result.skippedLocked ? `，跳过 ${result.skippedLocked} 名锁定成员` : ''}`;
  } catch (error) {
    feedback.value = messageFrom(error);
  } finally {
    saving.value = false;
  }
}

async function changeMemberPage(page: number) {
  memberQuery.value.page = page;
  await refreshMembers();
}

async function changeMemberPageSize(size: number) {
  memberQuery.value.size = size;
  memberQuery.value.page = 1;
  await refreshMembers();
}

async function changeCandidatePage(page: number) {
  candidateQuery.value.page = page;
  await refreshCandidates();
}

async function changeCandidatePageSize(size: number) {
  candidateQuery.value.size = size;
  candidateQuery.value.page = 1;
  await refreshCandidates();
}

async function loadPage() {
  try {
    const [loadedRoles, loadedDepartments, summary] = await Promise.all([
      props.service.listRoles(),
      organizationService.listAllDepartments(),
      organizationService.getSummary()
    ]);
    roles.value = loadedRoles;
    departments.value = loadedDepartments.map((department) => ({ id: department.id, name: department.departmentName }));
    organizationSummary.value = `当前组织有 ${summary.formalEmployees + summary.temporaryEmployees} 名在职员工，其中正式员工 ${summary.formalEmployees} 名。`;
    const initial = loadedRoles.find((role) => role.code === 'SUPER_ADMIN') ?? loadedRoles[0];
    if (initial) await applySelectedRole(await props.service.getRole(initial.id));
  } catch (error) {
    feedback.value = messageFrom(error);
  }
}

onMounted(loadPage);
</script>

<template>
  <section class="flex min-h-[620px] overflow-hidden rounded-[8px] border border-slate-200 bg-white" data-testid="permission-workspace" :data-readonly="readonly ? 'true' : 'false'" :data-disabled="selectedRole?.status === 'disabled' ? 'true' : 'false'">
    <RoleListPanel :roles="roles" :selected-role-id="selectedRole?.id ?? null" :disabled="saving" :dirty-role-ids="dirtyRoleIds" @select="selectRole" />

    <div class="flex min-w-0 flex-1 flex-col p-6">
      <div v-if="selectedRole" class="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <div class="flex flex-wrap items-center gap-2"><h1 data-testid="selected-role-name" class="text-xl font-bold text-[#25314d]">{{ selectedRole.name }}</h1><span data-testid="selected-role-code" class="rounded bg-slate-100 px-2 py-1 font-mono text-xs text-slate-500">{{ selectedRole.code }}</span><span data-testid="selected-role-status" class="rounded px-2 py-1 text-xs" :class="selectedRole.status === 'enabled' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ selectedRole.status === 'enabled' ? '已启用' : '已停用' }}</span></div>
          <p class="mt-2 text-sm text-slate-500">{{ selectedRole.description || '暂无角色说明' }} · <span data-testid="selected-role-member-count">{{ selectedRoleSummary?.memberCount ?? selectedRole.memberIds.length }} 名成员</span></p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button data-testid="create-role" type="button" class="rounded-[6px] bg-[#536dff] px-3 py-2 text-sm text-white disabled:opacity-50" :disabled="!canCreate || saving" @click="openCreateRole">新增角色</button>
          <template v-if="canManage"><button data-testid="edit-role" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="saving" @click="openEditRole">编辑</button><button data-testid="copy-role" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="saving" @click="openCopyRole">复制</button><button data-testid="role-status" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="saving" @click="toggleRoleStatus">{{ selectedRole.status === 'enabled' ? '停用角色' : '启用角色' }}</button></template>
        </div>
      </div>

      <div v-if="selectedRole" class="mt-5 flex border-b border-slate-200" role="tablist" aria-label="角色管理页签">
        <button type="button" role="tab" :aria-selected="activeTab === 'permissions'" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'permissions' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="saving" @click="selectTab('permissions')">权限配置</button>
        <button type="button" role="tab" :aria-selected="activeTab === 'scope'" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'scope' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="saving" @click="selectTab('scope')">数据范围</button>
        <button data-testid="tab-members" type="button" role="tab" :aria-selected="activeTab === 'members'" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'members' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="saving" @click="selectTab('members')">成员管理</button>
      </div>

      <div v-if="selectedRole && configurationDraft" class="min-h-0 flex-1 py-5">
        <template v-if="activeTab === 'permissions'"><PermissionMatrix :modules="PERMISSION_MODULES" :model-value="configurationDraft.permissionCodes" :readonly="readonly" @update:model-value="configurationDraft.permissionCodes = $event" /><div class="mt-5 flex items-center gap-3"><button data-testid="save-role-configuration" type="button" class="rounded-[6px] bg-[#536dff] px-4 py-2 text-sm text-white disabled:opacity-50" :disabled="!canManage || !dirty || saving" @click="saveConfiguration">{{ saving ? '保存中...' : '保存权限配置' }}</button><button v-if="dirty" type="button" class="text-sm text-slate-500" :disabled="saving" @click="resetDraft">取消修改</button><p v-if="saveError" data-testid="permission-save-error" class="text-sm text-rose-600">{{ saveError }}</p></div></template>
        <template v-else-if="activeTab === 'scope'"><DataScopePanel :model-value="configurationDraft.dataScope" :readonly="readonly" :organization-summary="organizationSummary" @update:model-value="configurationDraft.dataScope = $event" /><div class="mt-5 flex items-center gap-3"><button data-testid="save-role-configuration" type="button" class="rounded-[6px] bg-[#536dff] px-4 py-2 text-sm text-white disabled:opacity-50" :disabled="!canManage || !dirty || saving" @click="saveConfiguration">{{ saving ? '保存中...' : '保存数据范围' }}</button><button v-if="dirty" type="button" class="text-sm text-slate-500" :disabled="saving" @click="resetDraft">取消修改</button><p v-if="saveError" data-testid="permission-save-error" class="text-sm text-rose-600">{{ saveError }}</p></div></template>
        <RoleMembersPanel v-else :page="memberPage" :can-manage="canManage" :selected-ids="memberSelectedIds" :departments="departments" @page="changeMemberPage" @page-size="changeMemberPageSize" @keyword="memberQuery.keyword = $event; memberQuery.page = 1; refreshMembers()" @department="memberQuery.departmentId = $event; memberQuery.page = 1; refreshMembers()" @update:selected-ids="memberSelectedIds = $event" @add="openMemberDrawer" @remove="removeMembers" @clear="memberQuery.keyword = ''; memberQuery.departmentId = null; memberQuery.page = 1; refreshMembers()" />
      </div>
      <p v-else class="p-8 text-sm text-slate-400">正在加载角色…</p>
      <p v-if="feedback" data-testid="permission-feedback" class="mt-3 text-sm text-slate-600" role="status">{{ feedback }}</p>
    </div>
  </section>

  <RoleFormDrawer :open="roleFormOpen" :mode="roleFormMode" :role="roleFormMode === 'edit' ? selectedRole : null" :copy-sources="roles" :existing-codes="roles.map((role) => role.code)" :initial-copy-from-role-id="copyFromRoleId" :saving="saving" :error="roleFormError" @close="closeRoleForm" @submit="submitRole" />
  <MemberSelectionDrawer :open="memberDrawerOpen" :page="candidatePage" :selected-ids="candidateSelectedIds" :saving="saving" :departments="departments" @close="closeMemberDrawer" @keyword="candidateQuery.keyword = $event; candidateQuery.page = 1; refreshCandidates()" @department="candidateQuery.departmentId = $event; candidateQuery.page = 1; refreshCandidates()" @page="changeCandidatePage" @page-size="changeCandidatePageSize" @update:selected-ids="candidateSelectedIds = $event" @submit="addMembers" />
</template>
