<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { collectDepartmentSubtreeIds } from '../organizationTree';
import type { Department, Employee, OrganizationRecordStatus, SaveDepartmentPayload } from '../types';

export type DepartmentDrawerMode = 'create' | 'edit';

const props = defineProps<{
  mode: DepartmentDrawerMode;
  department: Department | null;
  parentId: number | null;
  departments: Department[];
  employees: Employee[];
  saving: boolean;
  error: string;
}>();

const emit = defineEmits<{
  close: [];
  save: [payload: SaveDepartmentPayload];
}>();

interface DepartmentFormState {
  departmentCode: string;
  departmentName: string;
  parentId: number | null;
  managerEmployeeId: number | null;
  sortOrder: number;
  status: OrganizationRecordStatus;
}

function initialForm(): DepartmentFormState {
  const createParentId = props.parentId !== null
    && props.departments.some((department) => department.id === props.parentId && department.status === 'enabled')
    ? props.parentId
    : null;
  return {
    departmentCode: props.department?.departmentCode ?? '',
    departmentName: props.department?.departmentName ?? '',
    parentId: props.department ? props.department.parentId : createParentId,
    managerEmployeeId: props.department?.managerEmployeeId ?? null,
    sortOrder: props.department?.sortOrder ?? 10,
    status: props.department?.status ?? 'enabled'
  };
}

const form = ref<DepartmentFormState>(initialForm());
const validationError = ref('');
const drawerElement = ref<HTMLElement | null>(null);
const initialFocusElement = ref<HTMLInputElement | null>(null);
let previouslyFocusedElement: HTMLElement | null = null;

const title = computed(() => props.mode === 'create' ? '新增部门' : '编辑部门');
const displayedError = computed(() => validationError.value || props.error);
const excludedParentIds = computed(() => props.department
  ? collectDepartmentSubtreeIds(props.departments, props.department.id)
  : new Set<number>());
const parentOptions = computed(() => props.departments.filter((department) => (
  (department.status === 'enabled' || department.id === form.value.parentId)
  && !excludedParentIds.value.has(department.id)
)));
const managerOptions = computed(() => props.employees.filter((employee) => (
  employee.status === 'active' || employee.id === form.value.managerEmployeeId
)));

watch(() => [props.mode, props.department?.id, props.parentId] as const, () => {
  form.value = initialForm();
  validationError.value = '';
});

function validate() {
  if (!form.value.departmentCode.trim() || !form.value.departmentName.trim()) return '请完整填写部门资料';
  if (!Number.isInteger(form.value.sortOrder) || form.value.sortOrder < 0) return '排序值必须为大于或等于 0 的整数';
  return '';
}

function submit() {
  if (props.saving) return;
  validationError.value = validate();
  if (validationError.value) return;
  emit('save', {
    departmentCode: form.value.departmentCode.trim(),
    departmentName: form.value.departmentName.trim(),
    parentId: form.value.parentId,
    managerEmployeeId: form.value.managerEmployeeId,
    sortOrder: form.value.sortOrder,
    status: form.value.status
  });
}

function focusableElements() {
  if (!drawerElement.value) return [];
  return Array.from(drawerElement.value.querySelectorAll<HTMLElement>(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )).filter((element) => element.tabIndex >= 0);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('close');
    return;
  }
  if (event.key !== 'Tab') return;
  const focusable = focusableElements();
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (!first || !last) return;
  const active = document.activeElement;
  if (event.shiftKey && (active === first || !drawerElement.value?.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (active === last || !drawerElement.value?.contains(active))) {
    event.preventDefault();
    first.focus();
  }
}

onMounted(async () => {
  previouslyFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  document.addEventListener('keydown', handleKeydown);
  await nextTick();
  initialFocusElement.value?.focus();
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown);
  previouslyFocusedElement?.focus();
});
</script>

<template>
  <div class="fixed inset-0 z-50 bg-[#25314d]/30" @mousedown.self="emit('close')">
    <aside
      ref="drawerElement"
      data-testid="department-drawer"
      class="ml-auto flex h-full w-1/2 max-w-[720px] flex-col border-l border-slate-200 bg-white shadow-[-12px_0_32px_rgba(37,49,77,0.12)]"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="'department-drawer-title'"
    >
      <header class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 px-6">
        <div>
          <h2 id="department-drawer-title" class="text-lg font-medium leading-7 text-[#25314d]">{{ title }}</h2>
          <p class="mt-0.5 text-xs text-slate-400">维护部门归属、负责人和启停状态</p>
        </div>
        <button data-testid="close-department-drawer" type="button" class="flex h-9 w-9 items-center justify-center rounded-[6px] text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" aria-label="关闭部门表单" @click="emit('close')">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </header>

      <div class="min-h-0 flex-1 overflow-y-auto px-6 py-5">
        <div v-if="displayedError" data-testid="department-drawer-error" class="mb-4 flex items-start gap-2 rounded-[6px] border border-rose-200 bg-rose-50 px-3 py-2.5 text-sm text-rose-600" role="alert">
          <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{{ displayedError }}</span>
        </div>

        <section class="rounded-[6px] border border-slate-200 bg-white p-5">
          <h3 class="text-[15px] font-medium text-[#25314d]">部门资料</h3>
          <div class="mt-4 grid grid-cols-2 gap-x-4 gap-y-4">
            <label class="block text-xs font-medium text-slate-500">
              部门编码 <span class="text-rose-500">*</span>
              <input ref="initialFocusElement" v-model="form.departmentCode" data-testid="department-code" type="text" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 text-sm font-normal text-[#25314d] outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="请输入部门编码" />
            </label>
            <label class="block text-xs font-medium text-slate-500">
              部门名称 <span class="text-rose-500">*</span>
              <input v-model="form.departmentName" data-testid="department-name" type="text" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 text-sm font-normal text-[#25314d] outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="请输入部门名称" />
            </label>
            <label class="block text-xs font-medium text-slate-500">
              上级部门
              <select v-model.number="form.parentId" data-testid="department-parent" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10">
                <option :value="null">全公司</option>
                <option v-for="item in parentOptions" :key="item.id" :value="item.id" :disabled="item.status !== 'enabled'">
                  {{ item.departmentName }}{{ item.status === 'disabled' ? '（已停用）' : '' }}
                </option>
              </select>
            </label>
            <label class="block text-xs font-medium text-slate-500">
              部门负责人
              <select v-model.number="form.managerEmployeeId" data-testid="department-manager" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10">
                <option :value="null">暂不设置</option>
                <option v-for="employee in managerOptions" :key="employee.id" :value="employee.id" :disabled="employee.status !== 'active'">
                  {{ employee.employeeName }} · {{ employee.employeeNo }}{{ employee.status !== 'active' ? '（非在职）' : '' }}
                </option>
              </select>
            </label>
            <label class="block text-xs font-medium text-slate-500">
              排序
              <input v-model.number="form.sortOrder" data-testid="department-sort-order" type="number" min="0" step="1" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 font-numeric text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" />
            </label>
            <label class="block text-xs font-medium text-slate-500">
              状态
              <select v-model="form.status" data-testid="department-status" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10">
                <option value="enabled">启用</option>
                <option value="disabled">停用</option>
              </select>
            </label>
          </div>
        </section>

        <p class="mt-4 rounded-[6px] bg-[#f1f4ff] px-4 py-3 text-xs leading-5 text-[#536dff]">停用部门前，需要先处理启用中的子部门和部门内在职员工。</p>
      </div>

      <footer class="flex h-[72px] shrink-0 items-center justify-end gap-3 border-t border-slate-200 px-6">
        <button data-testid="cancel-department" type="button" class="h-10 w-20 rounded-[6px] border border-slate-200 bg-white text-sm font-medium text-slate-600 transition hover:bg-slate-50" @click="emit('close')">取消</button>
        <button data-testid="save-department" type="button" class="h-10 min-w-[104px] rounded-[6px] bg-[#536dff] px-5 text-sm font-medium text-white transition hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存部门' }}</button>
      </footer>
    </aside>
  </div>
</template>
