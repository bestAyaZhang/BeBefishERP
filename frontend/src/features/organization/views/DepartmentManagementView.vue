<script setup lang="ts">
import { Building2, Plus, Search } from 'lucide-vue-next';
import { computed, inject, onMounted, ref, watch } from 'vue';
import DepartmentFormDrawer, { type DepartmentDrawerMode } from '../components/DepartmentFormDrawer.vue';
import DepartmentTree from '../components/DepartmentTree.vue';
import OrganizationPagination from '../components/OrganizationPagination.vue';
import OrganizationStatusBadge from '../components/OrganizationStatusBadge.vue';
import { buildDepartmentTree } from '../organizationTree';
import { organizationService, type OrganizationService } from '../organizationService';
import { currentUser } from '../../../services/authSession';
import type {
  Department,
  DepartmentListItem,
  Employee,
  OrganizationRecordStatus,
  PageResult,
  SaveDepartmentPayload
} from '../types';

const service = inject<OrganizationService>('organizationService', organizationService);

const departments = ref<Department[]>([]);
const employees = ref<Employee[]>([]);
const employeeCounts = ref<Record<number, number>>({});
const result = ref<PageResult<DepartmentListItem>>({ records: [], page: 1, pageSize: 20, total: 0 });
const selectedDepartmentId = ref<number | null>(null);
const expandedIds = ref<number[]>([]);
const page = ref(1);
const pageSize = ref(20);
const keywordDraft = ref('');
const keyword = ref('');
const status = ref<OrganizationRecordStatus | ''>('');
const loading = ref(true);
const error = ref('');
const pageNotice = ref('');
const drawerMode = ref<DepartmentDrawerMode | null>(null);
const selectedDepartment = ref<Department | null>(null);
const saving = ref(false);
const saveError = ref('');
const selectedDepartmentIds = ref<number[]>([]);
let departmentListGeneration = 0;

const departmentTree = computed(() => buildDepartmentTree(departments.value));
const canManage = computed(() => currentUser.value?.permissions.includes('organization:manage') ?? false);
const departmentById = computed(() => new Map(departments.value.map((department) => [department.id, department])));
const selectedTreeDepartment = computed(() => departmentById.value.get(selectedDepartmentId.value ?? -1));
const treeSummary = computed(() => {
  const department = selectedTreeDepartment.value;
  if (!department) {
    const total = Object.values(employeeCounts.value).reduce((sum, count) => sum + count, 0);
    return { label: '当前选中', title: '全公司', meta: `共 ${total} 人` };
  }
  return {
    label: '当前选中',
    title: department.departmentName,
    meta: `负责人：${department.managerName || '未设置'} · 直属员工 ${employeeCounts.value[department.id] ?? 0} 人`
  };
});
const pageDepartmentIds = computed(() => result.value.records.map((item) => item.id));
const allPageDepartmentsSelected = computed(() => pageDepartmentIds.value.length > 0
  && pageDepartmentIds.value.every((id) => selectedDepartmentIds.value.includes(id)));
const somePageDepartmentsSelected = computed(() => !allPageDepartmentsSelected.value
  && pageDepartmentIds.value.some((id) => selectedDepartmentIds.value.includes(id)));

watch(pageDepartmentIds, () => {
  selectedDepartmentIds.value = [];
});

function messageFrom(value: unknown) {
  return value instanceof Error ? value.message : '请求失败，请稍后重试';
}

function currentDepartmentQuery() {
  return {
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value || undefined,
    status: status.value || undefined
  };
}

async function fetchDepartments() {
  const requestGeneration = ++departmentListGeneration;
  loading.value = true;
  error.value = '';
  try {
    const nextResult = await service.listDepartmentPage(currentDepartmentQuery());
    if (requestGeneration !== departmentListGeneration) return;
    result.value = nextResult;
  } catch (requestError) {
    if (requestGeneration !== departmentListGeneration) return;
    error.value = messageFrom(requestError);
    result.value = { records: [], page: page.value, pageSize: pageSize.value, total: 0 };
    throw requestError;
  } finally {
    if (requestGeneration === departmentListGeneration) loading.value = false;
  }
}

async function loadDepartments() {
  try {
    await fetchDepartments();
  } catch {}
}

async function loadReferenceData() {
  loading.value = true;
  error.value = '';
  try {
    const [departmentItems, employeeItems, counts] = await Promise.all([
      service.listAllDepartments(),
      service.listAllEmployees(),
      service.getDepartmentEmployeeCounts()
    ]);
    departments.value = departmentItems;
    employees.value = employeeItems;
    employeeCounts.value = counts;
    expandedIds.value = departmentItems
      .filter((department) => departmentItems.some((candidate) => candidate.parentId === department.id))
      .map((department) => department.id);
    await loadDepartments();
  } catch (requestError) {
    error.value = messageFrom(requestError);
    loading.value = false;
  }
}

async function refreshReferenceData() {
  const [departmentItems, employeeItems, counts] = await Promise.all([
    service.listAllDepartments(),
    service.listAllEmployees(),
    service.getDepartmentEmployeeCounts()
  ]);
  departments.value = departmentItems;
  employees.value = employeeItems;
  employeeCounts.value = counts;
  await fetchDepartments();
}

function selectDepartment(id: number | null) {
  selectedDepartmentId.value = id;
}

function toggleDepartment(id: number) {
  expandedIds.value = expandedIds.value.includes(id)
    ? expandedIds.value.filter((item) => item !== id)
    : [...expandedIds.value, id];
}

async function searchDepartments() {
  keyword.value = keywordDraft.value.trim();
  page.value = 1;
  await loadDepartments();
}

async function changeStatusFilter(event: Event) {
  status.value = (event.target as HTMLSelectElement).value as OrganizationRecordStatus | '';
  page.value = 1;
  await loadDepartments();
}

async function resetFilters() {
  keywordDraft.value = '';
  keyword.value = '';
  status.value = '';
  page.value = 1;
  await loadDepartments();
}

async function changePage(nextPage: number) {
  page.value = nextPage;
  await loadDepartments();
}

async function changePageSize(nextSize: number) {
  pageSize.value = nextSize;
  page.value = 1;
  await loadDepartments();
}

function openCreate() {
  if (!canManage.value) return;
  selectedDepartment.value = null;
  drawerMode.value = 'create';
  saveError.value = '';
}

function openEdit(department: DepartmentListItem) {
  if (!canManage.value) return;
  selectedDepartment.value = { ...department };
  drawerMode.value = 'edit';
  saveError.value = '';
}

function closeDrawer() {
  drawerMode.value = null;
  selectedDepartment.value = null;
  saveError.value = '';
}

async function saveDepartment(payload: SaveDepartmentPayload) {
  if (!canManage.value) return;
  saving.value = true;
  saveError.value = '';
  try {
    if (drawerMode.value === 'edit' && selectedDepartment.value) {
      await service.updateDepartment(selectedDepartment.value.id, payload);
    } else {
      await service.createDepartment(payload);
    }
  } catch (requestError) {
    saveError.value = messageFrom(requestError);
    saving.value = false;
    return;
  }

  closeDrawer();
  try {
    await refreshReferenceData();
    pageNotice.value = '';
  } catch (requestError) {
    pageNotice.value = `部门保存成功，但列表刷新失败：${messageFrom(requestError)}`;
  } finally {
    saving.value = false;
  }
}

async function toggleDepartmentStatus(department: DepartmentListItem) {
  if (!canManage.value) return;
  if (department.status === 'enabled' && department.statusActionDisabled) return;
  pageNotice.value = '';
  try {
    await service.changeDepartmentStatus(
      department.id,
      department.status === 'enabled' ? 'disabled' : 'enabled'
    );
  } catch (requestError) {
    pageNotice.value = messageFrom(requestError);
    return;
  }

  try {
    await refreshReferenceData();
    pageNotice.value = '';
  } catch {
    pageNotice.value = '操作已成功，但列表刷新失败，请手动重试';
  }
}

function toggleDepartmentSelection(id: number, event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  selectedDepartmentIds.value = checked
    ? [...new Set([...selectedDepartmentIds.value, id])]
    : selectedDepartmentIds.value.filter((item) => item !== id);
}

function toggleAllDepartments(event: Event) {
  selectedDepartmentIds.value = (event.target as HTMLInputElement).checked ? [...pageDepartmentIds.value] : [];
}

onMounted(loadReferenceData);
</script>

<template>
  <div class="min-w-0 text-[#25314d]">
    <header class="mb-5 flex min-h-[60px] items-center justify-between gap-6">
      <div class="min-w-0">
        <div class="flex items-center gap-2">
          <h1 class="text-2xl font-bold leading-8 tracking-[0]">部门管理</h1>
          <span class="inline-flex h-6 min-w-6 items-center justify-center rounded-[6px] border border-slate-200 bg-slate-50 px-2 font-numeric text-xs text-slate-500">{{ result.total }}</span>
        </div>
        <p class="mt-1 text-sm leading-[22px] text-slate-500">维护多级部门结构、负责人、排序与启停状态。</p>
      </div>
      <button v-if="canManage" data-testid="add-department" type="button" class="inline-flex h-10 w-32 shrink-0 items-center justify-center gap-2 rounded-[6px] bg-[#536dff] text-sm font-medium text-white shadow-[0_8px_18px_rgba(83,109,255,0.2)] transition hover:bg-[#465eea]" @click="openCreate">
        <Plus class="h-[18px] w-[18px]" aria-hidden="true" />
        新增部门
      </button>
    </header>

    <p v-if="pageNotice" data-testid="department-page-error" class="mb-4 rounded-[6px] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700" role="alert">{{ pageNotice }}</p>

    <div data-testid="department-workspace" class="grid h-[804px] grid-cols-[280px_minmax(0,1fr)] gap-4">
      <DepartmentTree
        :nodes="departmentTree"
        :selected-id="selectedDepartmentId"
        :expanded-ids="expandedIds"
        :employee-counts="employeeCounts"
        title="部门结构"
        :summary="treeSummary"
        @select="selectDepartment"
        @toggle="toggleDepartment"
      />

      <section class="flex min-w-0 flex-col overflow-hidden rounded-[6px] border border-slate-200 bg-white">
        <div class="flex h-[60px] shrink-0 items-center justify-between border-b border-slate-200 px-4">
          <h2 class="text-[15px] font-medium">部门列表</h2>
          <span class="font-numeric text-sm text-slate-500">共 {{ result.total }} 条</span>
        </div>

        <div class="flex h-[68px] shrink-0 items-center gap-2 border-b border-slate-200 bg-slate-50/50 px-4">
          <label class="relative h-10 min-w-[260px] flex-1">
            <span class="sr-only">部门名称或编码</span>
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input v-model="keywordDraft" data-testid="department-keyword" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="部门名称 / 部门编码" @keyup.enter="searchDepartments" />
          </label>
          <select :value="status" data-testid="department-status-filter" class="h-10 w-[132px] rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" aria-label="部门状态" @change="changeStatusFilter">
            <option value="">全部状态</option>
            <option value="enabled">启用</option>
            <option value="disabled">停用</option>
          </select>
          <button data-testid="department-search-button" type="button" class="h-10 w-16 rounded-[6px] bg-[#536dff] text-sm font-medium text-white transition hover:bg-[#465eea]" @click="searchDepartments">查询</button>
          <button type="button" class="h-10 w-16 rounded-[6px] border border-slate-200 bg-white text-sm font-medium text-slate-500 transition hover:bg-slate-50" @click="resetFilters">重置</button>
        </div>

        <div class="min-h-0 flex-1 overflow-auto">
          <div v-if="loading" data-testid="department-loading" class="space-y-px bg-slate-100" aria-label="正在加载部门">
            <div v-for="index in 7" :key="index" class="flex h-16 items-center gap-4 bg-white px-4">
              <span class="h-3 w-20 animate-pulse rounded bg-slate-100"></span>
              <span class="h-3 w-32 animate-pulse rounded bg-slate-100"></span>
              <span class="h-3 flex-1 animate-pulse rounded bg-slate-100"></span>
            </div>
          </div>

          <div v-else-if="error" data-testid="department-error" class="m-4 rounded-[6px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600" role="alert">{{ error }}</div>

          <div v-else-if="result.records.length === 0" data-testid="department-empty" class="flex min-h-[420px] flex-col items-center justify-center text-center">
            <span class="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400"><Building2 class="h-5 w-5" aria-hidden="true" /></span>
            <p class="mt-3 text-sm font-medium">暂无匹配部门</p>
            <p class="mt-1 text-xs text-slate-400">请调整查询条件后重试</p>
          </div>

          <table v-else class="w-full min-w-[796px] table-fixed border-collapse text-left">
            <thead class="sticky top-0 z-10 bg-slate-50 text-xs font-normal text-slate-500">
              <tr class="h-11">
                <th class="w-[42px] px-3 font-normal"><input data-testid="select-all-departments" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" aria-label="选择全部部门" :checked="allPageDepartmentsSelected" :indeterminate="somePageDepartmentsSelected" @change="toggleAllDepartments" /></th>
                <th class="w-[90px] px-2.5 font-normal">部门编码</th>
                <th class="w-[150px] px-2.5 font-normal">部门名称</th>
                <th class="w-[130px] px-2.5 font-normal">上级部门</th>
                <th class="w-[110px] px-2.5 font-normal">负责人</th>
                <th class="w-[70px] px-2.5 font-normal">人数</th>
                <th class="w-[70px] px-2.5 font-normal">状态</th>
                <th class="w-[134px] px-2.5 font-normal">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="department in result.records" :key="department.id" class="h-16 border-t border-slate-200 bg-white transition hover:bg-slate-50/70">
                <td class="px-3"><input :data-testid="`select-department-${department.id}`" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" :aria-label="`选择${department.departmentName}`" :checked="selectedDepartmentIds.includes(department.id)" @change="toggleDepartmentSelection(department.id, $event)" /></td>
                <td class="truncate px-2.5 font-numeric text-xs text-slate-500">{{ department.departmentCode }}</td>
                <td class="truncate px-2.5 text-sm font-medium">{{ department.departmentName }}</td>
                <td class="truncate px-2.5 text-sm text-slate-500">{{ department.parentId === null ? '全公司' : departmentById.get(department.parentId)?.departmentName ?? '--' }}</td>
                <td class="truncate px-2.5 text-sm">{{ department.managerName || '--' }}</td>
                <td class="px-2.5 font-numeric text-sm">{{ department.employeeCount }}</td>
                <td class="px-2.5"><OrganizationStatusBadge :status="department.status" /></td>
                <td class="px-2.5">
                  <span class="flex items-center gap-2.5 whitespace-nowrap text-sm">
                    <button v-if="canManage" :data-testid="`edit-department-${department.id}`" type="button" class="font-medium text-[#536dff] transition hover:text-[#465eea]" @click="openEdit(department)">编辑</button>
                    <button
                      v-if="canManage"
                      :data-testid="`department-status-action-${department.id}`"
                      type="button"
                      class="text-xs text-slate-500 transition enabled:hover:text-[#25314d] disabled:cursor-not-allowed disabled:text-slate-400"
                      :disabled="department.status === 'enabled' && department.statusActionDisabled"
                      :title="department.status === 'enabled' && department.statusActionDisabled ? '部门存在启用中的子部门或在职员工，暂不可停用' : undefined"
                      @click="toggleDepartmentStatus(department)"
                    >{{ department.status === 'disabled' ? '启用' : department.statusActionDisabled ? '不可停用' : '停用' }}</button>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <OrganizationPagination v-if="!loading && !error" :page="page" :page-size="pageSize" :total="result.total" @page="changePage" @page-size="changePageSize" />
      </section>
    </div>

    <DepartmentFormDrawer
      v-if="drawerMode"
      :mode="drawerMode"
      :department="selectedDepartment"
      :parent-id="selectedDepartmentId"
      :departments="departments"
      :employees="employees"
      :saving="saving"
      :error="saveError"
      @close="closeDrawer"
      @save="saveDepartment"
    />
  </div>
</template>
