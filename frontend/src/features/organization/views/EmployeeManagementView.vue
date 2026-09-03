<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue';
import { Plus, Search, UsersRound } from 'lucide-vue-next';
import DepartmentTree from '../components/DepartmentTree.vue';
import EmployeeFormDrawer, { type EmployeeDrawerMode } from '../components/EmployeeFormDrawer.vue';
import OrganizationPagination from '../components/OrganizationPagination.vue';
import OrganizationStatusBadge from '../components/OrganizationStatusBadge.vue';
import { buildDepartmentTree } from '../organizationTree';
import { organizationService, type OrganizationService } from '../organizationService';
import { currentUser } from '../../../services/authSession';
import type {
  Department,
  Employee,
  EmployeeStatus,
  EmploymentType,
  OrganizationSummary,
  PageResult,
  Position,
  SaveEmployeePayload
} from '../types';

const service = inject<OrganizationService>('organizationService', organizationService);

const departments = ref<Department[]>([]);
const positions = ref<Position[]>([]);
const employeeCounts = ref<Record<number, number>>({});
const summary = ref<OrganizationSummary>({ formalEmployees: 0, temporaryEmployees: 0, pendingFeishuBindings: 0, disabledAccounts: 0 });
const result = ref<PageResult<Employee>>({ records: [], page: 1, pageSize: 20, total: 0 });
const selectedDepartmentId = ref<number | null>(null);
const expandedIds = ref<number[]>([]);
const page = ref(1);
const pageSize = ref(20);
const keywordDraft = ref('');
const keyword = ref('');
const employmentType = ref<EmploymentType | ''>('');
const status = ref<EmployeeStatus | ''>('');
const loading = ref(true);
const error = ref('');
const pageNotice = ref('');
const drawerMode = ref<EmployeeDrawerMode | null>(null);
const selectedEmployee = ref<Employee | null>(null);
const saving = ref(false);
const saveError = ref('');
const selectedEmployeeIds = ref<number[]>([]);
let employeeListGeneration = 0;

const departmentTree = computed(() => buildDepartmentTree(departments.value));
const canManage = computed(() => currentUser.value?.permissions.includes('organization:manage') ?? false);
const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const listTitle = computed(() => selectedDepartment.value?.departmentName ?? '全部员工');
const departmentById = computed(() => new Map(departments.value.map((item) => [item.id, item])));
const positionById = computed(() => new Map(positions.value.map((item) => [item.id, item])));
const pageEmployeeIds = computed(() => result.value.records.map((item) => item.id));
const allPageEmployeesSelected = computed(() => pageEmployeeIds.value.length > 0
  && pageEmployeeIds.value.every((id) => selectedEmployeeIds.value.includes(id)));
const somePageEmployeesSelected = computed(() => !allPageEmployeesSelected.value
  && pageEmployeeIds.value.some((id) => selectedEmployeeIds.value.includes(id)));

watch(pageEmployeeIds, () => {
  selectedEmployeeIds.value = [];
});

function messageFrom(errorValue: unknown) {
  return errorValue instanceof Error ? errorValue.message : '请求失败，请稍后重试';
}

async function loadEmployees(context: 'standard' | 'after-save' = 'standard') {
  const requestGeneration = ++employeeListGeneration;
  loading.value = true;
  error.value = '';
  if (context === 'standard') pageNotice.value = '';
  try {
    const nextResult = await service.listEmployees({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      departmentId: selectedDepartmentId.value ?? undefined,
      employmentType: employmentType.value || undefined,
      status: status.value || undefined
    });
    if (requestGeneration !== employeeListGeneration) return;
    result.value = nextResult;
  } catch (requestError) {
    if (requestGeneration !== employeeListGeneration) return;
    if (context === 'after-save') {
      pageNotice.value = `员工保存成功，但列表刷新失败：${messageFrom(requestError)}`;
    } else {
      error.value = messageFrom(requestError);
      result.value = { records: [], page: page.value, pageSize: pageSize.value, total: 0 };
    }
  } finally {
    if (requestGeneration === employeeListGeneration) loading.value = false;
  }
}

async function loadReferenceData() {
  loading.value = true;
  error.value = '';
  try {
    const [departmentItems, positionItems, counts, summaryData] = await Promise.all([
      service.listAllDepartments(),
      service.listAllPositions(),
      service.getDepartmentEmployeeCounts(),
      service.getSummary()
    ]);
    departments.value = departmentItems;
    positions.value = positionItems;
    employeeCounts.value = counts;
    summary.value = summaryData;
    expandedIds.value = departmentItems.filter((item) => departmentItems.some((candidate) => candidate.parentId === item.id)).map((item) => item.id);
    await loadEmployees();
  } catch (requestError) {
    error.value = messageFrom(requestError);
    loading.value = false;
  }
}

async function refreshAfterSave() {
  const [counts, summaryData, positionItems] = await Promise.all([
    service.getDepartmentEmployeeCounts(),
    service.getSummary(),
    service.listAllPositions()
  ]);
  employeeCounts.value = counts;
  summary.value = summaryData;
  positions.value = positionItems;
  await loadEmployees('after-save');
}

async function selectDepartment(id: number | null) {
  selectedDepartmentId.value = id;
  page.value = 1;
  await loadEmployees();
}

function toggleDepartment(id: number) {
  expandedIds.value = expandedIds.value.includes(id)
    ? expandedIds.value.filter((item) => item !== id)
    : [...expandedIds.value, id];
}

async function searchEmployees() {
  keyword.value = keywordDraft.value.trim();
  page.value = 1;
  await loadEmployees();
}

async function changeEmploymentType(event: Event) {
  employmentType.value = (event.target as HTMLSelectElement).value as EmploymentType | '';
  page.value = 1;
  await loadEmployees();
}

async function changeStatus(event: Event) {
  status.value = (event.target as HTMLSelectElement).value as EmployeeStatus | '';
  page.value = 1;
  await loadEmployees();
}

async function resetFilters() {
  keywordDraft.value = '';
  keyword.value = '';
  employmentType.value = '';
  status.value = '';
  page.value = 1;
  await loadEmployees();
}

async function changePage(nextPage: number) {
  page.value = nextPage;
  await loadEmployees();
}

async function changePageSize(nextSize: number) {
  pageSize.value = nextSize;
  page.value = 1;
  await loadEmployees();
}

function openCreate() {
  if (!canManage.value) return;
  selectedEmployee.value = null;
  drawerMode.value = 'create';
  saveError.value = '';
}

function openEmployee(employee: Employee, mode: EmployeeDrawerMode) {
  if (mode === 'edit' && !canManage.value) return;
  selectedEmployee.value = { ...employee };
  drawerMode.value = mode;
  saveError.value = '';
}

function closeDrawer() {
  drawerMode.value = null;
  selectedEmployee.value = null;
  saveError.value = '';
}

async function saveEmployee(payload: SaveEmployeePayload) {
  if (!canManage.value) return;
  saving.value = true;
  saveError.value = '';
  try {
    if (drawerMode.value === 'edit' && selectedEmployee.value) {
      await service.updateEmployee(selectedEmployee.value.id, payload);
    } else {
      await service.createEmployee(payload);
    }
  } catch (requestError) {
    saveError.value = messageFrom(requestError);
    saving.value = false;
    return;
  }

  closeDrawer();
  pageNotice.value = '';
  try {
    await refreshAfterSave();
  } catch (requestError) {
    pageNotice.value = `员工保存成功，但列表刷新失败：${messageFrom(requestError)}`;
  } finally {
    saving.value = false;
  }
}

function loginMethod(employee: Employee) {
  if (employee.status !== 'active') return '不可登录';
  if (employee.employmentType === 'temporary') {
    return employee.passwordLoginEnabled ? '手机号账号' : '不可登录';
  }
  if (employee.feishuBindingStatus === 'bound') {
    return employee.passwordLoginEnabled ? '飞书 + 手机号' : '飞书已绑定';
  }
  return employee.passwordLoginEnabled ? '手机号账号' : '待绑定飞书';
}

function loginMethodClass(employee: Employee) {
  if (employee.status !== 'active' || (employee.employmentType === 'temporary' && !employee.passwordLoginEnabled)) return 'text-slate-400';
  if (employee.employmentType === 'temporary' || employee.passwordLoginEnabled) return 'text-cyan-600';
  if (employee.feishuBindingStatus !== 'bound') return 'text-amber-500';
  return 'text-emerald-600';
}

function editViewedEmployee() {
  if (canManage.value) drawerMode.value = 'edit';
}

function avatarClass(index: number) {
  return ['bg-emerald-50', 'bg-blue-50', 'bg-amber-50', 'bg-violet-50', 'bg-rose-50'][index % 5];
}

function toggleEmployeeSelection(id: number, event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  selectedEmployeeIds.value = checked
    ? [...new Set([...selectedEmployeeIds.value, id])]
    : selectedEmployeeIds.value.filter((item) => item !== id);
}

function toggleAllEmployees(event: Event) {
  selectedEmployeeIds.value = (event.target as HTMLInputElement).checked ? [...pageEmployeeIds.value] : [];
}

onMounted(loadReferenceData);
</script>

<template>
  <div class="min-w-0 text-[#25314d]">
    <header class="mb-5 flex min-h-[60px] items-center justify-between gap-6">
      <div class="min-w-0">
        <div class="flex items-center gap-2">
          <h1 class="text-2xl font-bold leading-8 tracking-[0]">员工管理</h1>
          <span class="inline-flex h-6 min-w-6 items-center justify-center rounded-[6px] border border-slate-200 bg-slate-50 px-2 font-numeric text-xs text-slate-500">{{ result.total }}</span>
        </div>
        <p class="mt-1 text-sm leading-[22px] text-slate-500">统一维护员工归属、岗位、用工类型与登录账号。</p>
      </div>
      <button v-if="canManage" data-testid="add-employee" type="button" class="inline-flex h-10 w-32 shrink-0 items-center justify-center gap-2 rounded-[6px] bg-[#536dff] text-sm font-medium text-white shadow-[0_8px_18px_rgba(83,109,255,0.2)] transition hover:bg-[#465eea]" @click="openCreate">
        <Plus class="h-[18px] w-[18px]" aria-hidden="true" />
        新增员工
      </button>
    </header>

    <p v-if="pageNotice" data-testid="employee-page-notice" class="mb-4 rounded-[6px] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700" role="status">{{ pageNotice }}</p>

    <div data-testid="employee-workspace" class="grid h-[804px] grid-cols-[280px_minmax(0,1fr)] gap-4">
      <DepartmentTree
        :nodes="departmentTree"
        :selected-id="selectedDepartmentId"
        :expanded-ids="expandedIds"
        :employee-counts="employeeCounts"
        @select="selectDepartment"
        @toggle="toggleDepartment"
      />

      <section class="flex min-w-0 flex-col overflow-hidden rounded-[6px] border border-slate-200 bg-white">
        <div class="flex min-h-[60px] items-center justify-between gap-4 border-b border-slate-200 px-4">
          <h2 data-testid="employee-list-title" class="text-[15px] font-medium text-[#25314d]">{{ listTitle }}</h2>
          <span class="font-numeric text-sm text-slate-500">共 {{ result.total }} 人</span>
        </div>

        <div class="flex flex-wrap items-center gap-2 border-b border-slate-200 bg-slate-50/50 px-4 py-3">
          <label class="relative h-10 min-w-[220px] flex-1">
            <span class="sr-only">姓名、手机号或工号</span>
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input data-testid="employee-keyword" v-model="keywordDraft" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="姓名 / 手机号 / 工号" @keyup.enter="searchEmployees" />
          </label>
          <select data-testid="employee-employment-filter" :value="employmentType" class="h-10 w-[132px] rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" aria-label="用工类型" @change="changeEmploymentType">
            <option value="">全部用工类型</option>
            <option value="formal">正式员工</option>
            <option value="temporary">临时员工</option>
          </select>
          <select data-testid="employee-status-filter" :value="status" class="h-10 w-[116px] rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" aria-label="员工状态" @change="changeStatus">
            <option value="">全部状态</option>
            <option value="active">在职</option>
            <option value="disabled">停用</option>
            <option value="resigned">离职</option>
          </select>
          <button data-testid="employee-search" type="button" class="h-10 w-16 rounded-[6px] bg-[#536dff] text-sm font-medium text-white transition hover:bg-[#465eea]" @click="searchEmployees">查询</button>
          <button type="button" class="h-10 w-16 rounded-[6px] border border-slate-200 bg-white text-sm font-medium text-slate-500 transition hover:bg-slate-50" @click="resetFilters">重置</button>
        </div>

        <div class="flex flex-wrap gap-2 border-b border-slate-200 px-4 py-2">
          <span data-testid="formal-employee-count" class="rounded-[6px] bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-600">正式员工 {{ summary.formalEmployees }}</span>
          <span data-testid="temporary-employee-count" class="rounded-[6px] bg-cyan-50 px-3 py-1.5 text-xs font-medium text-cyan-600">临时员工 {{ summary.temporaryEmployees }}</span>
          <span class="rounded-[6px] bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-600">待绑定飞书 {{ summary.pendingFeishuBindings }}</span>
          <span class="rounded-[6px] bg-rose-50 px-3 py-1.5 text-xs font-medium text-rose-500">账号停用 {{ summary.disabledAccounts }}</span>
        </div>

        <div data-testid="employee-table-content" class="h-[572px] shrink-0 overflow-auto">
          <div v-if="loading" data-testid="employee-loading" class="space-y-px bg-slate-100" aria-label="正在加载员工">
            <div v-for="index in 6" :key="index" class="flex h-[72px] items-center gap-4 bg-white px-4">
              <span class="h-9 w-9 animate-pulse rounded-full bg-slate-100"></span>
              <span class="h-3 w-28 animate-pulse rounded bg-slate-100"></span>
              <span class="h-3 flex-1 animate-pulse rounded bg-slate-100"></span>
            </div>
          </div>

          <div v-else-if="error" data-testid="employee-error" class="m-4 rounded-[6px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600" role="alert">{{ error }}</div>

          <div v-else-if="result.records.length === 0" data-testid="employee-empty" class="flex min-h-[360px] flex-col items-center justify-center text-center">
            <span class="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400"><UsersRound class="h-5 w-5" aria-hidden="true" /></span>
            <p class="mt-3 text-sm font-medium text-[#25314d]">暂无匹配员工</p>
            <p class="mt-1 text-xs text-slate-400">请调整部门或查询条件后重试</p>
          </div>

          <table v-else class="w-full min-w-[806px] table-fixed border-collapse text-left">
            <thead class="bg-slate-50 text-xs font-normal text-slate-500">
              <tr class="h-11">
                <th class="w-[36px] px-3"><input data-testid="select-all-employees" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" aria-label="选择全部员工" :checked="allPageEmployeesSelected" :indeterminate="somePageEmployeesSelected" @change="toggleAllEmployees" /></th>
                <th class="w-[150px] px-2.5 font-normal">员工信息</th>
                <th class="w-[80px] px-2.5 font-normal">工号</th>
                <th class="w-[120px] px-2.5 font-normal">部门 / 岗位</th>
                <th class="w-[65px] px-2.5 font-normal">类型</th>
                <th class="w-[100px] px-2.5 font-normal">登录方式</th>
                <th class="w-[65px] px-2.5 font-normal">状态</th>
                <th class="w-[90px] px-2.5 font-normal">入职日期</th>
                <th class="w-[100px] px-2.5 font-normal">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(employee, index) in result.records" :key="employee.id" class="h-[72px] border-t border-slate-200 bg-white transition hover:bg-slate-50/70">
                <td class="px-3"><input :data-testid="`select-employee-${employee.id}`" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300" :aria-label="`选择${employee.employeeName}`" :checked="selectedEmployeeIds.includes(employee.id)" @change="toggleEmployeeSelection(employee.id, $event)" /></td>
                <td class="px-2.5">
                  <div class="flex min-w-0 items-center gap-2">
                    <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium" :class="avatarClass(index)">{{ employee.employeeName.slice(0, 1) }}</span>
                    <span class="min-w-0">
                      <span class="block truncate text-sm font-medium text-[#25314d]">{{ employee.employeeName }}</span>
                      <span class="mt-0.5 block truncate font-numeric text-xs text-slate-400">{{ employee.mobile }}</span>
                    </span>
                  </div>
                </td>
                <td class="truncate px-2.5 font-numeric text-xs text-slate-600">{{ employee.employeeNo }}</td>
                <td class="px-2.5">
                  <span class="block truncate text-sm text-[#25314d]">{{ departmentById.get(employee.departmentId)?.departmentName ?? '--' }}</span>
                  <span class="mt-0.5 block truncate text-xs text-slate-400">{{ positionById.get(employee.positionId)?.positionName ?? '--' }}</span>
                </td>
                <td class="px-2.5"><span class="inline-flex h-7 min-w-[54px] items-center justify-center rounded-[6px] px-2 text-xs font-medium" :class="employee.employmentType === 'formal' ? 'bg-emerald-50 text-emerald-600' : 'bg-cyan-50 text-cyan-600'">{{ employee.employmentType === 'formal' ? '正式' : '临时' }}</span></td>
                <td :data-testid="`employee-login-method-${employee.id}`" class="truncate px-2.5 text-xs" :class="loginMethodClass(employee)">{{ loginMethod(employee) }}</td>
                <td class="px-2.5"><OrganizationStatusBadge :status="employee.status" /></td>
                <td class="truncate px-2.5 font-numeric text-xs text-slate-600">{{ employee.hireDate }}</td>
                <td class="px-2.5">
                  <span class="flex items-center gap-2.5 whitespace-nowrap text-sm font-medium">
                    <button :data-testid="`view-employee-${employee.id}`" type="button" class="text-[#536dff] transition hover:text-[#465eea]" @click="openEmployee(employee, 'view')">查看</button>
                    <button v-if="canManage" :data-testid="`edit-employee-${employee.id}`" type="button" class="text-slate-500 transition hover:text-[#25314d]" @click="openEmployee(employee, 'edit')">编辑</button>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <OrganizationPagination v-if="!loading && !error" :page="page" :page-size="pageSize" :total="result.total" @page="changePage" @page-size="changePageSize" />
      </section>
    </div>

    <EmployeeFormDrawer
      v-if="drawerMode"
      :mode="drawerMode"
      :employee="selectedEmployee"
      :departments="departments"
      :positions="positions"
      :saving="saving"
      :error="saveError"
      :can-manage="canManage"
      @close="closeDrawer"
      @edit="editViewedEmployee"
      @save="saveEmployee"
    />
  </div>
</template>
