<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Pencil, X } from 'lucide-vue-next';
import type {
  Department,
  Employee,
  EmployeeStatus,
  EmploymentType,
  Position,
  SaveEmployeePayload
} from '../types';

export type EmployeeDrawerMode = 'create' | 'edit' | 'view';

const props = defineProps<{
  mode: EmployeeDrawerMode;
  employee: Employee | null;
  departments: Department[];
  positions: Position[];
  saving: boolean;
  error: string;
}>();

const emit = defineEmits<{
  close: [];
  edit: [];
  save: [payload: SaveEmployeePayload];
}>();

interface EmployeeFormState {
  employeeNo: string;
  employeeName: string;
  mobile: string;
  departmentId: number | '';
  positionId: number | '';
  employmentType: EmploymentType;
  status: EmployeeStatus;
  hireDate: string;
  passwordLoginEnabled: boolean;
  password: string;
  passwordConfirm: string;
}

const today = () => new Date().toISOString().slice(0, 10);

function initialForm(): EmployeeFormState {
  const item = props.employee;
  return {
    employeeNo: item?.employeeNo ?? '',
    employeeName: item?.employeeName ?? '',
    mobile: item?.mobile ?? '',
    departmentId: item?.departmentId ?? '',
    positionId: item?.positionId ?? '',
    employmentType: item?.employmentType ?? 'formal',
    status: item?.status ?? 'active',
    hireDate: item?.hireDate ?? today(),
    passwordLoginEnabled: item?.employmentType === 'temporary' ? true : item?.passwordLoginEnabled ?? false,
    password: '',
    passwordConfirm: ''
  };
}

const form = ref<EmployeeFormState>(initialForm());
const validationError = ref('');
const drawerElement = ref<HTMLElement | null>(null);
const initialFocusElement = ref<HTMLInputElement | null>(null);
const editFromViewElement = ref<HTMLButtonElement | null>(null);
let previouslyFocusedElement: HTMLElement | null = null;
const readOnly = computed(() => props.mode === 'view');
const isCreate = computed(() => props.mode === 'create');
const drawerTitle = computed(() => ({ create: '新增员工', edit: '编辑员工', view: '员工详情' })[props.mode]);
const visibleDepartments = computed(() => props.departments.filter((item) => item.status === 'enabled' || item.id === props.employee?.departmentId));
const visiblePositions = computed(() => props.positions.filter((item) => (
  item.departmentId === form.value.departmentId
  && (item.status === 'enabled' || item.id === props.employee?.positionId)
)));
const displayedError = computed(() => validationError.value || props.error);
const temporaryPasswordRequired = computed(() => {
  if (form.value.employmentType !== 'temporary') return false;
  if (isCreate.value || !props.employee) return true;
  return props.employee.employmentType !== 'temporary' || !props.employee.passwordLoginEnabled;
});
const feishuStatusLabel = computed(() => {
  if (!props.employee) return '保存后生成绑定二维码';
  return {
    bound: props.employee.feishuDisplayName ? `已绑定：${props.employee.feishuDisplayName}` : '飞书已绑定',
    pending: '等待员工扫码绑定',
    unbound: '飞书已解绑'
  }[props.employee.feishuBindingStatus];
});

watch(() => [props.mode, props.employee?.id] as const, () => {
  form.value = initialForm();
  validationError.value = '';
});

watch(() => form.value.departmentId, () => {
  if (!visiblePositions.value.some((item) => item.id === form.value.positionId)) {
    form.value.positionId = '';
  }
  validationError.value = '';
});

watch(() => form.value.employmentType, (employmentType) => {
  if (employmentType === 'temporary') form.value.passwordLoginEnabled = true;
  validationError.value = '';
});

function validate(): string {
  const value = form.value;
  if (value.employmentType === 'temporary' && (
    !value.passwordLoginEnabled || (temporaryPasswordRequired.value && !value.password.trim())
  )) return '临时员工必须启用手机号和密码登录';
  if (!value.employeeName.trim() || !value.mobile.trim() || !value.employeeNo.trim()) return '请完整填写员工基本资料';
  if (!/^1\d{10}$/.test(value.mobile.trim())) return '请输入正确的手机号';
  if (value.departmentId === '' || value.positionId === '') return '请选择员工所属部门和岗位';
  if (!value.hireDate) return '请选择入职日期';
  if (value.password && value.password !== value.passwordConfirm) return '两次输入的密码不一致';
  return '';
}

function submit() {
  if (readOnly.value || props.saving) return;
  validationError.value = validate();
  if (validationError.value) return;

  const payload: SaveEmployeePayload = {
    employeeNo: form.value.employeeNo.trim(),
    employeeName: form.value.employeeName.trim(),
    mobile: form.value.mobile.trim(),
    departmentId: Number(form.value.departmentId),
    positionId: Number(form.value.positionId),
    employmentType: form.value.employmentType,
    status: form.value.status,
    hireDate: form.value.hireDate,
    passwordLoginEnabled: form.value.passwordLoginEnabled
  };
  if (form.value.password.trim()) payload.password = form.value.password;
  emit('save', payload);
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
  if (readOnly.value) editFromViewElement.value?.focus();
  else initialFocusElement.value?.focus();
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown);
  if (previouslyFocusedElement?.isConnected) previouslyFocusedElement.focus();
});
</script>

<template>
  <div class="fixed inset-0 z-50 bg-[#25314d]/35" data-testid="employee-drawer-backdrop" @click.self="emit('close')">
    <aside
      ref="drawerElement"
      data-testid="employee-drawer"
      :data-mode="mode"
      class="absolute inset-y-0 right-0 flex w-[min(50vw,720px)] flex-col border-l border-slate-200 bg-white shadow-[-16px_0_36px_rgba(37,49,77,0.12)]"
      role="dialog"
      aria-modal="true"
      :aria-label="drawerTitle"
    >
      <header class="flex h-[76px] shrink-0 items-center border-b border-slate-200 px-6">
        <div class="min-w-0 flex-1">
          <h2 class="text-lg font-semibold leading-7 text-[#25314d]">{{ drawerTitle }}</h2>
          <p class="mt-0.5 text-xs leading-[18px] text-slate-400">
            {{ mode === 'view' ? '查看员工资料与登录状态' : mode === 'create' ? '创建员工档案并配置登录方式' : '维护员工资料与登录方式' }}
          </p>
        </div>
        <button
          v-if="mode === 'view'"
          ref="editFromViewElement"
          data-testid="edit-from-view"
          type="button"
          class="mr-2 inline-flex h-9 items-center gap-1.5 rounded-[6px] border border-slate-200 px-3 text-sm font-medium text-[#536dff] transition hover:border-[#536dff] hover:bg-indigo-50"
          @click="emit('edit')"
        >
          <Pencil class="h-4 w-4" aria-hidden="true" />
          编辑
        </button>
        <button
          data-testid="close-employee-drawer"
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-[6px] border border-slate-200 text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]"
          aria-label="关闭员工表单"
          @click="emit('close')"
        >
          <X class="h-[18px] w-[18px]" aria-hidden="true" />
        </button>
      </header>

      <div class="min-h-0 flex-1 overflow-y-auto bg-[#f8f9fc] p-4">
        <p
          v-if="displayedError"
          data-testid="employee-drawer-error"
          class="mb-3 rounded-[6px] border border-rose-200 bg-rose-50 px-3 py-2.5 text-sm text-rose-600"
          role="alert"
        >
          {{ displayedError }}
        </p>

        <section class="rounded-[6px] border border-slate-200 bg-white p-4">
          <h3 class="mb-4 text-[15px] font-medium text-[#25314d]">基本资料</h3>
          <div class="grid grid-cols-2 gap-x-3 gap-y-3">
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>员工姓名 <span class="text-amber-500">*</span></span>
              <input ref="initialFocusElement" data-testid="employee-name" v-model="form.employeeName" :disabled="readOnly" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500" placeholder="输入姓名" />
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>手机号 <span class="text-amber-500">*</span></span>
              <input data-testid="employee-mobile" v-model="form.mobile" :disabled="readOnly" inputmode="tel" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 font-numeric text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500" placeholder="输入手机号" />
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>员工工号 <span class="text-amber-500">*</span></span>
              <input data-testid="employee-number" v-model="form.employeeNo" :disabled="readOnly" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 font-numeric text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500" placeholder="自动生成 / 可修改" />
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>入职日期 <span class="text-amber-500">*</span></span>
              <input data-testid="employee-hire-date" v-model="form.hireDate" :disabled="readOnly" type="date" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 font-numeric text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500" />
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>员工状态 <span class="text-amber-500">*</span></span>
              <select v-model="form.status" :disabled="readOnly" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500">
                <option value="active">在职</option>
                <option value="disabled">停用</option>
                <option value="resigned">离职</option>
              </select>
            </label>
          </div>
        </section>

        <section class="mt-3 rounded-[6px] border border-slate-200 bg-white p-4">
          <h3 class="mb-4 text-[15px] font-medium text-[#25314d]">部门与岗位</h3>
          <div class="grid grid-cols-2 gap-3">
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>所属部门 <span class="text-amber-500">*</span></span>
              <select data-testid="employee-department" v-model="form.departmentId" :disabled="readOnly" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500">
                <option value="">请选择部门</option>
                <option v-for="department in visibleDepartments" :key="department.id" :value="department.id">{{ department.departmentName }}</option>
              </select>
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>所属岗位 <span class="text-amber-500">*</span></span>
              <select data-testid="employee-position" v-model="form.positionId" :disabled="readOnly || form.departmentId === ''" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:bg-slate-50 disabled:text-slate-500">
                <option value="">请选择岗位</option>
                <option v-for="position in visiblePositions" :key="position.id" :value="position.id" :data-department-id="position.departmentId">{{ position.positionName }}</option>
              </select>
            </label>
          </div>
          <p class="mt-3 rounded-[6px] bg-indigo-50 px-3 py-2 text-xs leading-[18px] text-[#536dff]">岗位选项随所属部门联动，岗位不直接等于系统角色。</p>
        </section>

        <section data-testid="employee-login-section" class="mt-3 rounded-[6px] border border-slate-200 bg-white p-4">
          <h3 class="mb-4 text-[15px] font-medium text-[#25314d]">登录账号</h3>

          <fieldset data-testid="employment-type-fieldset" :disabled="readOnly" class="mb-3">
            <legend class="mb-1.5 text-xs font-medium text-slate-500">用工类型 <span class="text-amber-500">*</span></legend>
            <div class="grid h-10 w-[232px] grid-cols-2 rounded-[6px] bg-slate-100 p-0.5">
              <label class="flex cursor-pointer items-center justify-center rounded-[5px] text-sm font-medium" :class="form.employmentType === 'formal' ? 'bg-[#536dff] text-white shadow-sm' : 'text-slate-500'">
                <input data-testid="employment-formal" v-model="form.employmentType" type="radio" value="formal" class="sr-only" />
                正式员工
              </label>
              <label class="flex cursor-pointer items-center justify-center rounded-[5px] text-sm font-medium" :class="form.employmentType === 'temporary' ? 'bg-[#536dff] text-white shadow-sm' : 'text-slate-500'">
                <input data-testid="employment-temporary" v-model="form.employmentType" type="radio" value="temporary" class="sr-only" />
                临时员工
              </label>
            </div>
          </fieldset>

          <div v-if="form.employmentType === 'formal'" class="flex min-h-[58px] items-center justify-between gap-4 rounded-[6px] bg-amber-50 px-3.5 py-2">
            <div>
              <p class="text-sm font-medium text-[#25314d]">飞书账号</p>
              <p class="mt-0.5 text-xs text-amber-600">{{ feishuStatusLabel }}</p>
            </div>
            <button v-if="!readOnly" type="button" class="h-9 rounded-[6px] border border-slate-200 bg-white px-3 text-xs font-medium text-slate-600">生成二维码</button>
          </div>

          <label class="mt-3 flex min-h-[52px] items-center justify-between gap-4 rounded-[6px] bg-slate-50 px-3.5 py-2">
            <span>
              <span class="block text-sm font-medium text-[#25314d]">允许手机号 + 密码登录</span>
              <span class="mt-0.5 block text-xs text-slate-400">{{ form.employmentType === 'formal' ? '正式员工可作为飞书登录的备用方式' : '临时员工必须使用手机号和密码登录' }}</span>
            </span>
            <input data-testid="password-login-enabled" v-model="form.passwordLoginEnabled" :disabled="readOnly || form.employmentType === 'temporary'" type="checkbox" class="h-5 w-9 accent-[#536dff]" />
          </label>

          <div v-if="!readOnly && form.passwordLoginEnabled" class="mt-3 grid grid-cols-2 gap-3">
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>{{ isCreate ? '初始密码' : '重置密码' }}</span>
              <input data-testid="employee-password" v-model="form.password" type="password" autocomplete="new-password" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" :placeholder="isCreate ? '输入初始密码' : '留空则不修改'" />
            </label>
            <label class="space-y-1.5 text-xs font-medium text-slate-500">
              <span>确认密码</span>
              <input data-testid="employee-password-confirm" v-model="form.passwordConfirm" type="password" autocomplete="new-password" class="h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="再次输入密码" />
            </label>
          </div>
        </section>
      </div>

      <footer v-if="mode !== 'view'" class="flex h-[76px] shrink-0 items-center justify-end gap-3 border-t border-slate-200 bg-white px-6">
        <button data-testid="cancel-employee" type="button" class="h-10 min-w-20 rounded-[6px] border border-slate-200 bg-white px-4 text-sm font-medium text-slate-600 transition hover:bg-slate-50" @click="emit('close')">取消</button>
        <button data-testid="save-employee" type="button" class="h-10 min-w-[96px] rounded-[6px] bg-[#536dff] px-4 text-sm font-medium text-white transition hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存员工' }}</button>
      </footer>
    </aside>
  </div>
</template>
