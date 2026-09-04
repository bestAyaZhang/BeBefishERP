<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { currentUser } from '../../../services/authSession';
import DataScopePanel from '../components/DataScopePanel.vue';
import MemberSelectionDrawer from '../components/MemberSelectionDrawer.vue';
import PermissionMatrix from '../components/PermissionMatrix.vue';
import RoleFormDrawer from '../components/RoleFormDrawer.vue';
import RoleListPanel from '../components/RoleListPanel.vue';
import RoleMembersPanel from '../components/RoleMembersPanel.vue';
import { PERMISSION_MODULES } from '../permissionCatalog';
import { permissionService, type PermissionService } from '../permissionService';
import type { CreateRolePayload, PermissionDataScope, PermissionRole, PermissionRoleSummary, RoleMemberPage, RoleMemberQuery, UpdateRolePayload } from '../types';

type Tab = 'permissions' | 'scope' | 'members';
type FormMode = 'create' | 'edit';
type State = 'loading' | 'error' | 'ready';
type Configuration = { permissionCodes: string[]; dataScope: PermissionDataScope };
type Query = Required<Pick<RoleMemberQuery, 'page' | 'size'>> & Pick<RoleMemberQuery, 'keyword' | 'departmentId'>;
const TAB_ORDER: Tab[] = ['permissions', 'scope', 'members'];
const TAB_IDS: Record<Tab, string> = {
  permissions: 'role-permissions-tab',
  scope: 'role-scope-tab',
  members: 'role-members-tab'
};
const TAB_PANEL_IDS: Record<Tab, string> = {
  permissions: 'role-permissions-panel',
  scope: 'role-scope-panel',
  members: 'role-members-panel'
};
const props = withDefaults(defineProps<{ service?: PermissionService }>(), { service: () => permissionService });
const EMPTY_PAGE: RoleMemberPage = { records: [], page: 1, pageSize: 20, total: 0 };
const roles = ref<PermissionRoleSummary[]>([]);
const selectedRole = ref<PermissionRole | null>(null);
const savedConfiguration = ref<Configuration | null>(null);
const configurationDraft = ref<Configuration | null>(null);
const activeTab = ref<Tab>('permissions');
const loadState = ref<State>('loading');
const loadError = ref('');
const feedback = ref('');
const configurationSaving = ref(false);
const roleSaving = ref(false);
const memberSaving = ref(false);
const roleLoading = ref(false);
const roleLoadError = ref('');
const pendingRoleId = ref<number | null>(null);
const saveError = ref('');
const roleFormOpen = ref(false);
const roleFormMode = ref<FormMode>('create');
const roleFormError = ref('');
const roleFormDirty = ref(false);
const copyFromRoleId = ref<number | null>(null);
const memberDrawerOpen = ref(false);
const memberPage = ref<RoleMemberPage>(EMPTY_PAGE);
const candidatePage = ref<RoleMemberPage>(EMPTY_PAGE);
const memberError = ref('');
const candidateError = ref('');
const memberSelectedIds = ref<number[]>([]);
const candidateSelectedIds = ref<number[]>([]);
const memberQuery = ref<Query>({ page: 1, size: 20, keyword: '', departmentId: null });
const candidateQuery = ref<Query>({ page: 1, size: 20, keyword: '', departmentId: null });
const departments = ref<Array<{ id: number; name: string }>>([]);
const organizationSummary = ref('');
let loadEpoch = 0;
let roleEpoch = 0;
let memberEpoch = 0;
let candidateEpoch = 0;

const isBusy = computed(() => configurationSaving.value || roleSaving.value || memberSaving.value || roleLoading.value);
const canCreate = computed(() => currentUser.value?.permissions.includes('system:role:manage') ?? false);
const isCustom = computed(() => Boolean(selectedRole.value && !selectedRole.value.immutable));
const canEdit = computed(() => canCreate.value && isCustom.value && selectedRole.value?.status === 'enabled');
const canChangeStatus = computed(() => canCreate.value && isCustom.value);
const canCopy = computed(() => canCreate.value && isCustom.value);
const readonly = computed(() => !canEdit.value);
const configurationDirty = computed(() => {
  if (!savedConfiguration.value || !configurationDraft.value) return false;
  const saved = [...savedConfiguration.value.permissionCodes].sort();
  const draft = [...configurationDraft.value.permissionCodes].sort();
  return savedConfiguration.value.dataScope !== configurationDraft.value.dataScope || saved.length !== draft.length || saved.some((code, index) => code !== draft[index]);
});
const selectedRoleSummary = computed(() => roles.value.find((role) => role.id === selectedRole.value?.id) ?? null);
const dirtyRoleIds = computed(() => configurationDirty.value && selectedRole.value ? [selectedRole.value.id] : []);
function cloneConfiguration(role: PermissionRole): Configuration { return { permissionCodes: [...role.permissionCodes], dataScope: role.dataScope }; }
function cloneQuery(query: Query): Query { return { page: query.page, size: query.size, keyword: query.keyword, departmentId: query.departmentId }; }
function messageFrom(error: unknown): string { return error instanceof Error && error.message ? error.message : '操作失败，请重试'; }
function resetConfiguration() {
  if (!savedConfiguration.value) return;
  configurationDraft.value = { permissionCodes: [...savedConfiguration.value.permissionCodes], dataScope: savedConfiguration.value.dataScope };
  saveError.value = '';
}
function confirmDiscard(kind: 'configuration' | 'role-form' | 'member-selection' | 'candidate-selection'): boolean {
  const dirty = kind === 'configuration' ? configurationDirty.value : kind === 'role-form' ? roleFormDirty.value : kind === 'candidate-selection' ? candidateSelectedIds.value.length > 0 : candidateSelectedIds.value.length > 0 || memberSelectedIds.value.length > 0;
  if (!dirty) return true;
  return window.confirm(kind === 'configuration' ? '当前角色存在未保存修改，是否放弃？' : kind === 'role-form' ? '当前角色资料存在未保存修改，是否放弃？' : '当前成员选择尚未提交，是否放弃？');
}
async function refreshRoles() { roles.value = await props.service.listRoles(); }
function toSummary(role: PermissionRole): PermissionRoleSummary {
  return { id: role.id, code: role.code, name: role.name, kind: role.kind, immutable: role.immutable, status: role.status, updatedBy: role.updatedBy, updatedAt: role.updatedAt, memberCount: role.memberIds.length };
}
function patchRoleSummary(role: PermissionRole) {
  const summary = toSummary(role);
  roles.value = roles.value.some((item) => item.id === role.id)
    ? roles.value.map((item) => item.id === role.id ? summary : item)
    : [...roles.value, summary];
}
function invalidateRoleSelection() {
  roleEpoch += 1;
  roleLoading.value = false;
  roleLoadError.value = '';
  pendingRoleId.value = null;
}
function updateSelectedMetadata(role: PermissionRole) {
  if (selectedRole.value?.id === role.id) {
    selectedRole.value = {
      ...selectedRole.value,
      ...role,
      permissionCodes: [...selectedRole.value.permissionCodes],
      dataScope: selectedRole.value.dataScope
    };
  }
  patchRoleSummary(role);
}
function clearMemberContext(resetQueries: boolean) {
  memberEpoch += 1; candidateEpoch += 1;
  memberSelectedIds.value = []; candidateSelectedIds.value = [];
  memberPage.value = EMPTY_PAGE; candidatePage.value = EMPTY_PAGE;
  memberError.value = ''; candidateError.value = '';
  if (resetQueries) {
    memberQuery.value = { page: 1, size: memberQuery.value.size, keyword: '', departmentId: null };
    candidateQuery.value = { page: 1, size: candidateQuery.value.size, keyword: '', departmentId: null };
  }
}
function commitSelectedRole(role: PermissionRole, resetMemberContext = true) {
  selectedRole.value = role;
  patchRoleSummary(role);
  savedConfiguration.value = cloneConfiguration(role);
  configurationDraft.value = cloneConfiguration(role);
  saveError.value = '';
  clearMemberContext(resetMemberContext);
}
async function refreshMembers() {
  const roleId = selectedRole.value?.id; if (!roleId) return;
  const query = cloneQuery(memberQuery.value); const request = ++memberEpoch; memberError.value = '';
  try {
    const page = await props.service.listMembers(roleId, query);
    if (request !== memberEpoch || selectedRole.value?.id !== roleId) return;
    memberPage.value = page;
    memberQuery.value = { ...memberQuery.value, page: page.page, size: page.pageSize };
  } catch (error) {
    if (request === memberEpoch && selectedRole.value?.id === roleId) memberError.value = messageFrom(error);
  }
}
async function refreshCandidates() {
  const roleId = selectedRole.value?.id; if (!roleId) return;
  const query = cloneQuery(candidateQuery.value); const request = ++candidateEpoch; candidateError.value = '';
  try {
    const page = await props.service.listCandidates(roleId, query);
    if (request !== candidateEpoch || selectedRole.value?.id !== roleId) return;
    candidatePage.value = page;
    candidateQuery.value = { ...candidateQuery.value, page: page.page, size: page.pageSize };
  } catch (error) {
    if (request === candidateEpoch && selectedRole.value?.id === roleId) candidateError.value = messageFrom(error);
  }
}
async function selectRole(roleId: number, discardConfiguration = false) {
  if (configurationSaving.value || roleSaving.value || memberSaving.value || selectedRole.value?.id === roleId) return;
  if (!discardConfiguration && !confirmDiscard('configuration')) return;
  if (!confirmDiscard('member-selection')) return;
  const request = ++roleEpoch;
  roleLoading.value = true; roleLoadError.value = ''; pendingRoleId.value = roleId;
  try {
    const role = await props.service.getRole(roleId);
    if (request !== roleEpoch) return;
    commitSelectedRole(role);
    pendingRoleId.value = null;
    void refreshMembers();
    void refreshCandidates();
  } catch (error) {
    if (request === roleEpoch) {
      const message = messageFrom(error);
      if (selectedRole.value) feedback.value = message;
      else roleLoadError.value = message;
    }
  } finally {
    if (request === roleEpoch) roleLoading.value = false;
  }
}
async function selectTab(tab: Tab) {
  if (isBusy.value || activeTab.value === tab) return;
  activeTab.value = tab;
  if (tab === 'members') await refreshMembers();
}
function handleTabKeydown(event: KeyboardEvent, currentTab: Tab) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  if (isBusy.value) return;
  const currentIndex = TAB_ORDER.indexOf(currentTab);
  const targetTab = event.key === 'Home'
    ? TAB_ORDER[0]
    : event.key === 'End'
      ? TAB_ORDER[TAB_ORDER.length - 1]
      : TAB_ORDER[(currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + TAB_ORDER.length) % TAB_ORDER.length];
  void selectTab(targetTab);
  document.getElementById(TAB_IDS[targetTab])?.focus();
}
async function saveConfiguration() {
  if (!selectedRole.value || !configurationDraft.value || !canEdit.value || !configurationDirty.value || isBusy.value) return;
  const roleId = selectedRole.value.id;
  const payload = { permissionCodes: [...configurationDraft.value.permissionCodes], dataScope: configurationDraft.value.dataScope };
  configurationSaving.value = true; saveError.value = ''; feedback.value = '';
  try {
    const updated = await props.service.saveConfiguration(roleId, payload);
    if (selectedRole.value?.id !== roleId) return;
    invalidateRoleSelection();
    updateSelectedMetadata(updated);
    savedConfiguration.value = cloneConfiguration(updated);
    configurationDraft.value = cloneConfiguration(updated);
    feedback.value = '权限配置已保存';
    void refreshMembers();
    try { await refreshRoles(); } catch (error) { feedback.value = '权限配置已保存，但角色列表刷新失败：' + messageFrom(error); }
  } catch (error) {
    saveError.value = messageFrom(error);
  } finally { configurationSaving.value = false; }
}
function cancelConfiguration() {
  if (isBusy.value || !confirmDiscard('configuration')) return;
  resetConfiguration();
}
function openCreateRole() {
  if (!canCreate.value || isBusy.value) return;
  roleFormMode.value = 'create'; copyFromRoleId.value = null; roleFormError.value = ''; roleFormDirty.value = false; roleFormOpen.value = true;
}
function openEditRole() {
  if (!canEdit.value || isBusy.value) return;
  roleFormMode.value = 'edit'; copyFromRoleId.value = null; roleFormError.value = ''; roleFormDirty.value = false; roleFormOpen.value = true;
}
function openCopyRole() {
  if (!canCopy.value || !selectedRole.value || isBusy.value) return;
  roleFormMode.value = 'create'; copyFromRoleId.value = selectedRole.value.id; roleFormError.value = ''; roleFormDirty.value = false; roleFormOpen.value = true;
}
function closeRoleForm() {
  if (roleSaving.value || !confirmDiscard('role-form')) return;
  roleFormOpen.value = false; roleFormDirty.value = false;
}
async function submitRole(payload: CreateRolePayload | UpdateRolePayload) {
  if (roleSaving.value) return;
  const creating = roleFormMode.value === 'create';
  if (creating && !confirmDiscard('configuration')) return;
  roleSaving.value = true; roleFormError.value = ''; feedback.value = '';
  try {
    const role = creating ? await props.service.createRole(payload as CreateRolePayload) : await props.service.updateRole(selectedRole.value!.id, payload as UpdateRolePayload);
    invalidateRoleSelection();
    if (creating) {
      commitSelectedRole(role); activeTab.value = 'permissions';
    } else {
      updateSelectedMetadata(role);
    }
    roleFormOpen.value = false; roleFormDirty.value = false;
    feedback.value = creating ? '角色已创建' : '角色资料已更新';
    void refreshMembers();
    if (creating) void refreshCandidates();
    try { await refreshRoles(); } catch (error) { feedback.value += '，但角色列表刷新失败：' + messageFrom(error); }
  } catch (error) { roleFormError.value = messageFrom(error); } finally { roleSaving.value = false; }
}
async function toggleRoleStatus() {
  if (!selectedRole.value || !canChangeStatus.value || isBusy.value) return;
  const roleId = selectedRole.value.id;
  const next = selectedRole.value.status === 'enabled' ? 'disabled' : 'enabled';
  if (next === 'disabled' && !window.confirm('停用角色将影响 ' + selectedRole.value.memberIds.length + ' 名成员，是否继续？')) return;
  roleSaving.value = true; feedback.value = '';
  try {
    const updated = await props.service.changeRoleStatus(roleId, next);
    invalidateRoleSelection();
    if (selectedRole.value?.id === roleId) updateSelectedMetadata(updated);
    feedback.value = next === 'disabled' ? '角色已停用' : '角色已启用';
    void refreshMembers();
    try { await refreshRoles(); } catch (error) { feedback.value += '，但角色列表刷新失败：' + messageFrom(error); }
  } catch (error) { feedback.value = messageFrom(error); } finally { roleSaving.value = false; }
}
function openMemberDrawer() {
  if (!canEdit.value || isBusy.value) return;
  candidateQuery.value = { page: 1, size: candidateQuery.value.size, keyword: '', departmentId: null };
  candidateSelectedIds.value = []; candidateError.value = ''; memberDrawerOpen.value = true;
  void refreshCandidates();
}
function closeMemberDrawer() {
  if (memberSaving.value || !confirmDiscard('candidate-selection')) return;
  memberDrawerOpen.value = false; candidateSelectedIds.value = []; candidateError.value = '';
}
async function addMembers(employeeIds: number[]) {
  if (!selectedRole.value || !canEdit.value || isBusy.value) return;
  const roleId = selectedRole.value.id;
  memberSaving.value = true; candidateError.value = '';
  try {
    const result = await props.service.addMembers(roleId, [...employeeIds]);
    invalidateRoleSelection();
    if (selectedRole.value?.id === roleId) updateSelectedMetadata(result.role);
    memberDrawerOpen.value = false; candidateSelectedIds.value = []; feedback.value = '已添加 ' + result.added + ' 人，跳过 ' + result.skipped + ' 人';
    try { await refreshRoles(); } catch (error) { feedback.value += '，但角色列表刷新失败：' + messageFrom(error); }
    await refreshMembers();
  } catch (error) { candidateError.value = messageFrom(error); } finally { memberSaving.value = false; }
}
async function removeMembers(employeeIds: number[]) {
  if (!selectedRole.value || !canEdit.value || isBusy.value) return;
  const locked = new Set(memberPage.value.records.filter((member) => member.lockedReason).map((member) => member.employeeId));
  const removable = employeeIds.filter((id) => !locked.has(id)); if (!removable.length) return;
  const roleId = selectedRole.value.id;
  memberSaving.value = true; memberError.value = '';
  try {
    const result = await props.service.removeMembers(roleId, [...removable]);
    invalidateRoleSelection();
    if (selectedRole.value?.id === roleId) updateSelectedMetadata(result.role);
    memberSelectedIds.value = memberSelectedIds.value.filter((id) => !removable.includes(id));
    feedback.value = '已移除 ' + result.removed + ' 人' + (result.skippedLocked ? '，跳过 ' + result.skippedLocked + ' 名锁定成员' : '');
    try { await refreshRoles(); } catch (error) { feedback.value += '，但角色列表刷新失败：' + messageFrom(error); }
    await refreshMembers();
  } catch (error) { memberError.value = messageFrom(error); } finally { memberSaving.value = false; }
}
function updateMemberQuery(next: Partial<Query>) { memberQuery.value = { ...memberQuery.value, ...next }; void refreshMembers(); }
function updateCandidateQuery(next: Partial<Query>) { candidateQuery.value = { ...candidateQuery.value, ...next }; void refreshCandidates(); }
function clearMemberFilters() { updateMemberQuery({ page: 1, keyword: '', departmentId: null }); }
async function loadPage() {
  const request = ++loadEpoch; loadState.value = 'loading'; loadError.value = ''; feedback.value = '';
  try {
    const [loadedRoles, context] = await Promise.all([props.service.listRoles(), props.service.getPermissionContext()]);
    if (request !== loadEpoch) return;
    roles.value = loadedRoles; departments.value = context.departments.map((department) => ({ ...department })); organizationSummary.value = context.organizationSummary; loadState.value = 'ready';
    const initial = loadedRoles.find((role) => role.code === 'SUPER_ADMIN') ?? loadedRoles[0];
    if (initial) void selectRole(initial.id, true);
  } catch (error) {
    if (request !== loadEpoch) return;
    loadState.value = 'error'; loadError.value = messageFrom(error);
  }
}
onMounted(loadPage);
</script>

<template>
  <section class="flex min-h-[620px] flex-col gap-4" data-testid="permission-page">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div><h1 data-testid="permission-page-title" class="text-2xl font-bold text-[#25314d]">权限管理</h1><p data-testid="permission-page-subtitle" class="mt-1 text-sm text-slate-500">按角色维护功能权限、数据范围与授权成员</p><p data-testid="permission-role-count" class="mt-2 inline-flex rounded bg-slate-100 px-2 py-1 text-sm text-slate-500">{{ roles.length }} 个角色</p></div>
      <button v-if="loadState === 'ready' && roles.length" data-testid="create-role" type="button" class="rounded-[6px] bg-[#536dff] px-3 py-2 text-sm text-white disabled:opacity-50" :disabled="!canCreate || isBusy" @click="openCreateRole">新增角色</button>
    </header>
    <div v-if="loadState === 'loading'" data-testid="permission-loading" class="rounded-[8px] border border-slate-200 bg-white p-8 text-sm text-slate-400">正在加载角色…</div>
    <div v-else-if="loadState === 'error'" data-testid="permission-load-error" class="rounded-[8px] border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">{{ loadError }} <button data-testid="retry-permission-load" type="button" class="ml-2 text-[#536dff] underline" @click="loadPage">重试</button></div>
    <div v-else-if="roles.length === 0" data-testid="permission-empty" class="rounded-[8px] border border-slate-200 bg-white p-8 text-center text-sm text-slate-500"><p>暂无角色</p><button data-testid="empty-create-role" type="button" class="mt-3 rounded-[6px] bg-[#536dff] px-3 py-2 text-sm text-white disabled:opacity-50" :disabled="!canCreate" @click="openCreateRole">新增角色</button></div>
    <section v-else class="flex min-h-[620px] overflow-hidden rounded-[8px] border border-slate-200 bg-white" data-testid="permission-workspace" :data-readonly="readonly ? 'true' : 'false'" :data-disabled="selectedRole?.status === 'disabled' ? 'true' : 'false'">
      <RoleListPanel :roles="roles" :selected-role-id="selectedRole?.id ?? null" :disabled="configurationSaving || roleSaving || memberSaving" :dirty-role-ids="dirtyRoleIds" @select="selectRole" />
      <div class="flex min-w-0 flex-1 flex-col p-6">
        <template v-if="selectedRole">
          <div class="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 pb-5">
            <div>
              <div class="flex flex-wrap items-center gap-2"><h2 data-testid="selected-role-name" class="text-xl font-bold text-[#25314d]">{{ selectedRole.name }}</h2><span data-testid="selected-role-code" class="rounded bg-slate-100 px-2 py-1 font-mono text-xs text-slate-500">{{ selectedRole.code }}</span><span data-testid="selected-role-status" class="rounded px-2 py-1 text-xs" :class="selectedRole.status === 'enabled' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ selectedRole.status === 'enabled' ? '启用' : '停用' }}</span></div>
              <p class="mt-2 text-sm text-slate-500">{{ selectedRole.description || '暂无角色说明' }} · <span data-testid="selected-role-member-count">{{ selectedRoleSummary?.memberCount ?? selectedRole.memberIds.length }} 名成员</span></p>
              <p data-testid="role-updated-at" class="mt-2 text-xs text-slate-400">最近更新：{{ selectedRole.updatedBy }} · {{ selectedRole.updatedAt }}</p>
              <p v-if="selectedRole.immutable" data-testid="system-role-readonly-notice" class="mt-2 text-xs text-slate-500">系统角色不可编辑</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <button v-if="canEdit" data-testid="edit-role" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="isBusy" @click="openEditRole">编辑角色</button>
              <button v-if="canCopy" data-testid="copy-role" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="isBusy" @click="openCopyRole">复制角色</button>
              <button v-if="canChangeStatus" data-testid="role-status" type="button" class="rounded-[6px] border border-slate-200 px-3 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="isBusy" @click="toggleRoleStatus">{{ selectedRole.status === 'enabled' ? '停用' : '启用' }}</button>
            </div>
          </div>
          <div class="mt-5 flex border-b border-slate-200" role="tablist" aria-label="角色管理页签">
            <button :id="TAB_IDS.permissions" type="button" role="tab" :aria-selected="activeTab === 'permissions'" :aria-controls="TAB_PANEL_IDS.permissions" :tabindex="activeTab === 'permissions' ? 0 : -1" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'permissions' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="isBusy" @click="selectTab('permissions')" @keydown="handleTabKeydown($event, 'permissions')">权限配置</button>
            <button :id="TAB_IDS.scope" type="button" role="tab" :aria-selected="activeTab === 'scope'" :aria-controls="TAB_PANEL_IDS.scope" :tabindex="activeTab === 'scope' ? 0 : -1" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'scope' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="isBusy" @click="selectTab('scope')" @keydown="handleTabKeydown($event, 'scope')">数据范围</button>
            <button :id="TAB_IDS.members" data-testid="tab-members" type="button" role="tab" :aria-selected="activeTab === 'members'" :aria-controls="TAB_PANEL_IDS.members" :tabindex="activeTab === 'members' ? 0 : -1" class="px-4 py-3 text-sm font-medium" :class="activeTab === 'members' ? 'border-b-2 border-[#536dff] text-[#536dff]' : 'text-slate-500'" :disabled="isBusy" @click="selectTab('members')" @keydown="handleTabKeydown($event, 'members')">成员管理</button>
          </div>
          <div v-if="configurationDraft" class="min-h-0 flex-1 py-5">
            <div :id="TAB_PANEL_IDS.permissions" role="tabpanel" :aria-labelledby="TAB_IDS.permissions" :hidden="activeTab !== 'permissions'">
              <PermissionMatrix v-if="activeTab === 'permissions'" :modules="PERMISSION_MODULES" :model-value="configurationDraft.permissionCodes" :readonly="readonly || isBusy" @update:model-value="configurationDraft.permissionCodes = $event" />
            </div>
            <div :id="TAB_PANEL_IDS.scope" role="tabpanel" :aria-labelledby="TAB_IDS.scope" :hidden="activeTab !== 'scope'">
              <DataScopePanel v-if="activeTab === 'scope'" :model-value="configurationDraft.dataScope" :readonly="readonly || isBusy" :organization-summary="organizationSummary" @update:model-value="configurationDraft.dataScope = $event" />
            </div>
            <div :id="TAB_PANEL_IDS.members" role="tabpanel" :aria-labelledby="TAB_IDS.members" :hidden="activeTab !== 'members'">
              <template v-if="activeTab === 'members'">
                <p v-if="memberError" data-testid="member-load-error" class="mb-3 text-sm text-rose-600">{{ memberError }} <button type="button" class="text-[#536dff] underline" @click="refreshMembers">重试</button></p>
                <RoleMembersPanel :page="memberPage" :can-manage="canEdit && !isBusy" :selected-ids="memberSelectedIds" :keyword="memberQuery.keyword" :department-id="memberQuery.departmentId" :departments="departments" @page="updateMemberQuery({ page: $event })" @page-size="updateMemberQuery({ size: $event, page: 1 })" @keyword="updateMemberQuery({ keyword: $event, page: 1 })" @department="updateMemberQuery({ departmentId: $event, page: 1 })" @update:selected-ids="memberSelectedIds = $event" @add="openMemberDrawer" @remove="removeMembers" @clear="clearMemberFilters" />
              </template>
            </div>
          </div>
          <div v-if="configurationDraft" data-testid="configuration-footer" class="sticky bottom-0 z-10 flex items-center gap-3 border-t border-slate-200 bg-white py-4"><button data-testid="cancel-role-configuration" type="button" class="rounded-[6px] border border-slate-200 px-4 py-2 text-sm text-slate-600 disabled:opacity-50" :disabled="!configurationDirty || isBusy" @click="cancelConfiguration">取消修改</button><button data-testid="save-role-configuration" type="button" class="rounded-[6px] bg-[#536dff] px-4 py-2 text-sm text-white disabled:opacity-50" :disabled="!canEdit || !configurationDirty || isBusy" @click="saveConfiguration">{{ configurationSaving ? '保存中...' : '保存配置' }}</button><p v-if="saveError" data-testid="permission-save-error" class="text-sm text-rose-600">{{ saveError }}</p></div>
        </template>
        <p v-else-if="roleLoading" data-testid="permission-role-loading" class="p-8 text-sm text-slate-400">正在加载角色…</p>
        <p v-else-if="roleLoadError" data-testid="permission-role-load-error" class="p-8 text-sm text-rose-600">{{ roleLoadError }} <button type="button" class="ml-2 text-[#536dff] underline" @click="pendingRoleId !== null && selectRole(pendingRoleId, true)">重试</button></p>
        <p v-else data-testid="permission-role-empty" class="p-8 text-sm text-slate-400">请选择角色</p>
        <p v-if="feedback" data-testid="permission-feedback" class="mt-3 text-sm text-slate-600" role="status">{{ feedback }}</p>
      </div>
    </section>
  </section>
  <RoleFormDrawer :open="roleFormOpen" :mode="roleFormMode" :role="roleFormMode === 'edit' ? selectedRole : null" :copy-sources="roles" :existing-codes="roles.map((role) => role.code)" :initial-copy-from-role-id="copyFromRoleId" :saving="roleSaving" :error="roleFormError" @dirty="roleFormDirty = $event" @close="closeRoleForm" @submit="submitRole" />
  <MemberSelectionDrawer :open="memberDrawerOpen" :page="candidatePage" :selected-ids="candidateSelectedIds" :saving="isBusy" :keyword="candidateQuery.keyword" :department-id="candidateQuery.departmentId" :error="candidateError" :departments="departments" @close="closeMemberDrawer" @keyword="updateCandidateQuery({ keyword: $event, page: 1 })" @department="updateCandidateQuery({ departmentId: $event, page: 1 })" @page="updateCandidateQuery({ page: $event })" @page-size="updateCandidateQuery({ size: $event, page: 1 })" @update:selected-ids="candidateSelectedIds = $event" @submit="addMembers" />
</template>
