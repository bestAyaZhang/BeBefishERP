<script setup lang="ts">
import { BriefcaseBusiness, Plus, Search } from 'lucide-vue-next';
import { computed, inject, onMounted, ref, watch } from 'vue';
import DepartmentTree from '../components/DepartmentTree.vue';
import OrganizationPagination from '../components/OrganizationPagination.vue';
import OrganizationStatusBadge from '../components/OrganizationStatusBadge.vue';
import PositionFormDrawer, { type PositionDrawerMode } from '../components/PositionFormDrawer.vue';
import { buildDepartmentTree, collectDepartmentSubtreeIds } from '../organizationTree';
import { organizationService, type OrganizationService } from '../organizationService';
import { currentUser } from '../../../services/authSession';
import type {
  Department,
  Employee,
  OrganizationRecordStatus,
  PageResult,
  Position,
  SavePositionPayload
} from '../types';

const service = inject<OrganizationService>('organizationService', organizationService);

const departments = ref<Department[]>([]);
const employees = ref<Employee[]>([]);
const employeeCounts = ref<Record<number, number>>({});
const result = ref<PageResult<Position>>({ records: [], page: 1, pageSize: 20, total: 0 });
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
const drawerMode = ref<PositionDrawerMode | null>(null);
const selectedPosition = ref<Position | null>(null);
const saving = ref(false);
const saveError = ref('');
const selectedPositionIds = ref<number[]>([]);
let positionOperationGeneration = 0;

const departmentTree = computed(() => buildDepartmentTree(departments.value));
const canCreate = computed(() => currentUser.value?.permissions.includes('organization:create') ?? false);
const canManage = computed(() => currentUser.value?.permissions.includes('organization:edit') ?? false);
const departmentById = computed(() => new Map(departments.value.map((department) => [department.id, department])));
const selectedDepartment = computed(() => departmentById.value.get(selectedDepartmentId.value ?? -1));
const listTitle = computed(() => selectedDepartment.value ? `${selectedDepartment.value.departmentName}岗位` : '全部岗位');
const scopedActiveEmployeeCount = computed(() => {
  const departmentIds = selectedDepartmentId.value === null
    ? null
    : collectDepartmentSubtreeIds(departments.value, selectedDepartmentId.value);
  return employees.value.filter((employee) => (
    employee.status === 'active' && employee.positionId !== null
    && (departmentIds === null || departmentIds.has(employee.departmentId ?? -1))
  )).length;
});
const treeSummary = computed(() => ({
  label: '岗位范围',
  title: selectedDepartment.value?.departmentName ?? '全公司',
  meta: `共 ${result.value.total} 个岗位 · 在岗员工 ${scopedActiveEmployeeCount.value} 人`
}));
const pagePositionIds = computed(() => result.value.records.map((item) => item.id));
const allPagePositionsSelected = computed(() => pagePositionIds.value.length > 0
  && pagePositionIds.value.every((id) => selectedPositionIds.value.includes(id)));
const somePagePositionsSelected = computed(() => !allPagePositionsSelected.value
  && pagePositionIds.value.some((id) => selectedPositionIds.value.includes(id)));

watch(pagePositionIds, () => {
  selectedPositionIds.value = [];
});

function messageFrom(value: unknown) {
  return value instanceof Error ? value.message : '请求失败，请稍后重试';
}

function currentPositionQuery() {
  return {
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value || undefined,
    departmentId: selectedDepartmentId.value ?? undefined,
    status: status.value || undefined
  };
}

async function fetchPositions(requestGeneration: number) {
  loading.value = true;
  error.value = '';
  try {
    const nextResult = await service.listPositions(currentPositionQuery());
    if (requestGeneration !== positionOperationGeneration) return;
    result.value = nextResult;
  } catch (requestError) {
    if (requestGeneration !== positionOperationGeneration) return;
    error.value = messageFrom(requestError);
    result.value = { records: [], page: page.value, pageSize: pageSize.value, total: 0 };
    throw requestError;
  } finally {
    if (requestGeneration === positionOperationGeneration) loading.value = false;
  }
}

async function loadPositions() {
  const requestGeneration = ++positionOperationGeneration;
  try {
    await fetchPositions(requestGeneration);
  } catch {}
}

async function loadReferenceData() {
  const requestGeneration = ++positionOperationGeneration;
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
    if (requestGeneration !== positionOperationGeneration) return;
    selectedDepartmentId.value = departmentItems.find((department) => department.departmentName === '产品中心')?.id ?? null;
    await fetchPositions(requestGeneration);
  } catch (requestError) {
    if (requestGeneration !== positionOperationGeneration) return;
    error.value = messageFrom(requestError);
    loading.value = false;
  }
}

async function refreshReferenceData() {
  const requestGeneration = ++positionOperationGeneration;
  const [departmentItems, employeeItems, counts] = await Promise.all([
    service.listAllDepartments(),
    service.listAllEmployees(),
    service.getDepartmentEmployeeCounts()
  ]);
  departments.value = departmentItems;
  employees.value = employeeItems;
  employeeCounts.value = counts;
  if (requestGeneration !== positionOperationGeneration) return;
  await fetchPositions(requestGeneration);
}

async function selectDepartment(id: number | null) {
  selectedDepartmentId.value = id;
  page.value = 1;
  await loadPositions();
}

function toggleDepartment(id: number) {
  expandedIds.value = expandedIds.value.includes(id)
    ? expandedIds.value.filter((item) => item !== id)
    : [...expandedIds.value, id];
}

async function searchPositions() {
  keyword.value = keywordDraft.value.trim();
  page.value = 1;
  await loadPositions();
}

async function changeStatusFilter(event: Event) {
  status.value = (event.target as HTMLSelectElement).value as OrganizationRecordStatus | '';
  page.value = 1;
  await loadPositions();
}

async function resetFilters() {
  keywordDraft.value = '';
  keyword.value = '';
  status.value = '';
  page.value = 1;
  await loadPositions();
}

async function changePage(nextPage: number) {
  page.value = nextPage;
  await loadPositions();
}

async function changePageSize(nextSize: number) {
  pageSize.value = nextSize;
  page.value = 1;
  await loadPositions();
}

function openCreate() {
  if (!canCreate.value) return;
  selectedPosition.value = null;
  drawerMode.value = 'create';
  saveError.value = '';
}

function openEdit(position: Position) {
  if (!canManage.value) return;
  selectedPosition.value = { ...position };
  drawerMode.value = 'edit';
  saveError.value = '';
}

function closeDrawer() {
  drawerMode.value = null;
  selectedPosition.value = null;
  saveError.value = '';
}

async function savePosition(payload: SavePositionPayload) {
  if (drawerMode.value === 'create' ? !canCreate.value : !canManage.value) return;
  saving.value = true;
  saveError.value = '';
  try {
    if (drawerMode.value === 'edit' && selectedPosition.value) {
      await service.updatePosition(selectedPosition.value.id, payload);
    } else {
      await service.createPosition(payload);
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
    pageNotice.value = `岗位保存成功，但列表刷新失败：${messageFrom(requestError)}`;
  } finally {
    saving.value = false;
  }
}

async function togglePositionStatus(position: Position) {
  if (!canManage.value) return;
  pageNotice.value = '';
  try {
    await service.changePositionStatus(
      position.id,
      position.status === 'enabled' ? 'disabled' : 'enabled'
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

function togglePositionSelection(id: number, event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  selectedPositionIds.value = checked
    ? [...new Set([...selectedPositionIds.value, id])]
    : selectedPositionIds.value.filter((item) => item !== id);
}

function toggleAllPositions(event: Event) {
  selectedPositionIds.value = (event.target as HTMLInputElement).checked ? [...pagePositionIds.value] : [];
}

onMounted(loadReferenceData);
</script>

<template>
  <div class="min-w-0 text-[#25314d]">
    <header class="mb-5 flex min-h-[60px] items-center justify-between gap-6">
      <div class="min-w-0">
        <div class="flex items-center gap-2">
          <h1 class="text-2xl font-bold leading-8 tracking-[0]">岗位管理</h1>
          <span class="inline-flex h-6 min-w-6 items-center justify-center rounded-[6px] border border-slate-200 bg-slate-50 px-2 font-numeric text-xs text-slate-500">{{ result.total }}</span>
        </div>
        <p class="mt-1 text-sm leading-[22px] text-slate-500">按部门维护岗位名称、岗位编码、职责与启停状态。</p>
      </div>
      <button v-if="canCreate" data-testid="add-position" type="button" class="inline-flex h-10 w-32 shrink-0 items-center justify-center gap-2 rounded-[6px] bg-[#536dff] text-sm font-medium text-white shadow-[0_8px_18px_rgba(83,109,255,0.2)] transition hover:bg-[#465eea]" @click="openCreate">
        <Plus class="h-[18px] w-[18px]" aria-hidden="true" />
        新增岗位
      </button>
    </header>

    <p v-if="pageNotice" data-testid="position-page-error" class="mb-4 rounded-[6px] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700" role="alert">{{ pageNotice }}</p>

    <div data-testid="position-workspace" class="grid h-[804px] grid-cols-[280px_minmax(0,1fr)] gap-4">
      <DepartmentTree
        :nodes="departmentTree"
        :selected-id="selectedDepartmentId"
        :expanded-ids="expandedIds"
        :employee-counts="employeeCounts"
        :total-employee-count="employees.length"
        title="所属部门"
        :summary="treeSummary"
        @select="selectDepartment"
        @toggle="toggleDepartment"
      />

      <section class="flex min-w-0 flex-col overflow-hidden rounded-[6px] border border-slate-200 bg-white">
        <div class="flex h-[60px] shrink-0 items-center justify-between border-b border-slate-200 px-4">
          <h2 data-testid="position-list-title" class="text-[15px] font-medium">{{ listTitle }}</h2>
          <span class="font-numeric text-sm text-slate-500">共 {{ result.total }} 条</span>
        </div>

        <div class="flex h-[68px] shrink-0 items-center gap-2 border-b border-slate-200 bg-slate-50/50 px-4">
          <label class="relative h-10 min-w-[260px] flex-1">
            <span class="sr-only">岗位名称或编码</span>
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input v-model="keywordDraft" data-testid="position-keyword" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="岗位名称 / 岗位编码" @keyup.enter="searchPositions" />
          </label>
          <select :value="status" data-testid="position-status-filter" class="h-10 w-[132px] rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" aria-label="岗位状态" @change="changeStatusFilter">
            <option value="">全部状态</option>
            <option value="enabled">启用</option>
            <option value="disabled">停用</option>
          </select>
          <button data-testid="position-search-button" type="button" class="h-10 w-16 rounded-[6px] bg-[#536dff] text-sm font-medium text-white transition hover:bg-[#465eea]" @click="searchPositions">查询</button>
          <button type="button" class="h-10 w-16 rounded-[6px] border border-slate-200 bg-white text-sm font-medium text-slate-500 transition hover:bg-slate-50" @click="resetFilters">重置</button>
        </div>

        <div class="min-h-0 flex-1 overflow-auto">
          <div v-if="loading" data-testid="position-loading" class="space-y-px bg-slate-100" aria-label="正在加载岗位">
            <div v-for="index in 7" :key="index" class="flex h-[72px] items-center gap-4 bg-white px-4">
              <span class="h-3 w-20 animate-pulse rounded bg-slate-100"></span>
              <span class="h-3 w-32 animate-pulse rounded bg-slate-100"></span>
              <span class="h-3 flex-1 animate-pulse rounded bg-slate-100"></span>
            </div>
          </div>

          <div v-else-if="error" data-testid="position-error" class="m-4 rounded-[6px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600" role="alert">{{ error }}</div>

          <div v-else-if="result.records.length === 0" data-testid="position-empty" class="flex min-h-[420px] flex-col items-center justify-center text-center">
            <span class="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400"><BriefcaseBusiness class="h-5 w-5" aria-hidden="true" /></span>
            <p class="mt-3 text-sm font-medium">暂无匹配岗位</p>
            <p class="mt-1 text-xs text-slate-400">请调整部门或查询条件后重试</p>
          </div>

          <table v-else class="w-full min-w-[796px] table-fixed border-collapse text-left">
            <thead class="sticky top-0 z-10 bg-slate-50 text-xs font-normal text-slate-500">
              <tr class="h-11">
                <th class="w-[42px] px-3 font-normal"><input data-testid="select-all-positions" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" aria-label="选择全部岗位" :checked="allPagePositionsSelected" :indeterminate="somePagePositionsSelected" @change="toggleAllPositions" /></th>
                <th class="w-[94px] px-2.5 font-normal">岗位编码</th>
                <th class="w-[132px] px-2.5 font-normal">岗位名称</th>
                <th class="w-[116px] px-2.5 font-normal">所属部门</th>
                <th class="w-[68px] px-2.5 font-normal">人数</th>
                <th class="w-[174px] px-2.5 font-normal">岗位职责</th>
                <th class="w-[70px] px-2.5 font-normal">状态</th>
                <th class="w-[100px] px-2.5 font-normal">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="positionItem in result.records" :key="positionItem.id" class="h-[72px] border-t border-slate-200 bg-white transition hover:bg-slate-50/70">
                <td class="px-3"><input :data-testid="`select-position-${positionItem.id}`" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" :aria-label="`选择${positionItem.positionName}`" :checked="selectedPositionIds.includes(positionItem.id)" @change="togglePositionSelection(positionItem.id, $event)" /></td>
                <td class="truncate px-2.5 font-numeric text-xs text-slate-500">{{ positionItem.positionCode }}</td>
                <td class="truncate px-2.5 text-sm font-medium">{{ positionItem.positionName }}</td>
                <td class="truncate px-2.5 text-sm text-slate-500">{{ departmentById.get(positionItem.departmentId ?? -1)?.departmentName ?? '--' }}</td>
                <td class="px-2.5 font-numeric text-sm">{{ positionItem.employeeCount }}</td>
                <td class="px-2.5 text-xs leading-[18px] text-slate-500"><span class="line-clamp-2">{{ positionItem.responsibilities || '--' }}</span></td>
                <td class="px-2.5"><OrganizationStatusBadge :status="positionItem.status" /></td>
                <td class="px-2.5">
                  <span class="flex items-center gap-2.5 whitespace-nowrap text-sm">
                    <button v-if="canManage" :data-testid="`edit-position-${positionItem.id}`" type="button" class="font-medium text-[#536dff] transition hover:text-[#465eea]" @click="openEdit(positionItem)">编辑</button>
                    <button v-if="canManage" :data-testid="`position-status-action-${positionItem.id}`" type="button" class="text-xs text-slate-500 transition hover:text-[#25314d]" @click="togglePositionStatus(positionItem)">{{ positionItem.status === 'enabled' ? '停用' : '启用' }}</button>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <OrganizationPagination v-if="!loading && !error" :page="page" :page-size="pageSize" :total="result.total" @page="changePage" @page-size="changePageSize" />
      </section>
    </div>

    <PositionFormDrawer
      v-if="drawerMode"
      :mode="drawerMode"
      :position="selectedPosition"
      :department-id="selectedDepartmentId"
      :departments="departments"
      :saving="saving"
      :error="saveError"
      @close="closeDrawer"
      @save="savePosition"
    />
  </div>
</template>
