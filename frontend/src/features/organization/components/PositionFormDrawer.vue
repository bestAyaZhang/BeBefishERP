<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { Department, OrganizationRecordStatus, Position, SavePositionPayload } from '../types';

export type PositionDrawerMode = 'create' | 'edit';

const props = defineProps<{
  mode: PositionDrawerMode;
  position: Position | null;
  departmentId: number | null;
  departments: Department[];
  saving: boolean;
  error: string;
}>();

const emit = defineEmits<{
  close: [];
  save: [payload: SavePositionPayload];
}>();

interface PositionFormState {
  positionCode: string;
  positionName: string;
  departmentId: number | '';
  responsibilities: string;
  status: OrganizationRecordStatus;
}

function firstEnabledDepartmentId() {
  return props.departments.find((department) => department.status === 'enabled')?.id ?? '';
}

function initialForm(): PositionFormState {
  const createDepartmentId = props.departmentId !== null
    && props.departments.some((department) => department.id === props.departmentId && department.status === 'enabled')
    ? props.departmentId
    : firstEnabledDepartmentId();
  return {
    positionCode: props.position?.positionCode ?? '',
    positionName: props.position?.positionName ?? '',
    departmentId: props.position ? props.position.departmentId : createDepartmentId,
    responsibilities: props.position?.responsibilities ?? '',
    status: props.position?.status ?? 'enabled'
  };
}

const form = ref<PositionFormState>(initialForm());
const validationError = ref('');
const drawerElement = ref<HTMLElement | null>(null);
const initialFocusElement = ref<HTMLInputElement | null>(null);
let previouslyFocusedElement: HTMLElement | null = null;

const title = computed(() => props.mode === 'create' ? '新增岗位' : '编辑岗位');
const displayedError = computed(() => validationError.value || props.error);
const departmentOptions = computed(() => props.departments.filter((department) => (
  department.status === 'enabled' || department.id === props.position?.departmentId
)));

watch(() => [props.mode, props.position?.id, props.departmentId] as const, () => {
  form.value = initialForm();
  validationError.value = '';
});

function validate() {
  if (!form.value.positionCode.trim() || !form.value.positionName.trim()) return '请完整填写岗位资料';
  if (form.value.departmentId === '') return '请选择所属部门';
  return '';
}

function submit() {
  if (props.saving) return;
  validationError.value = validate();
  if (validationError.value) return;
  emit('save', {
    positionCode: form.value.positionCode.trim(),
    positionName: form.value.positionName.trim(),
    departmentId: Number(form.value.departmentId),
    responsibilities: form.value.responsibilities.trim(),
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
      data-testid="position-drawer"
      class="ml-auto flex h-full w-1/2 max-w-[720px] flex-col border-l border-slate-200 bg-white shadow-[-12px_0_32px_rgba(37,49,77,0.12)]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="position-drawer-title"
    >
      <header class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 px-6">
        <div>
          <h2 id="position-drawer-title" class="text-lg font-medium leading-7 text-[#25314d]">{{ title }}</h2>
          <p class="mt-0.5 text-xs text-slate-400">维护岗位归属、职责与启停状态</p>
        </div>
        <button data-testid="close-position-drawer" type="button" class="flex h-9 w-9 items-center justify-center rounded-[6px] text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" aria-label="关闭岗位表单" @click="emit('close')">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </header>

      <div class="min-h-0 flex-1 overflow-y-auto px-6 py-5">
        <div v-if="displayedError" data-testid="position-drawer-error" class="mb-4 flex items-start gap-2 rounded-[6px] border border-rose-200 bg-rose-50 px-3 py-2.5 text-sm text-rose-600" role="alert">
          <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{{ displayedError }}</span>
        </div>

        <section class="rounded-[6px] border border-slate-200 bg-white p-5">
          <h3 class="text-[15px] font-medium text-[#25314d]">岗位资料</h3>
          <div class="mt-4 grid grid-cols-2 gap-x-4 gap-y-4">
            <label class="block text-xs font-medium text-slate-500">
              岗位编码 <span class="text-rose-500">*</span>
              <input ref="initialFocusElement" v-model="form.positionCode" data-testid="position-code" type="text" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 text-sm font-normal text-[#25314d] outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="请输入岗位编码" />
            </label>
            <label class="block text-xs font-medium text-slate-500">
              岗位名称 <span class="text-rose-500">*</span>
              <input v-model="form.positionName" data-testid="position-name" type="text" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 text-sm font-normal text-[#25314d] outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="请输入岗位名称" />
            </label>
            <label class="block text-xs font-medium text-slate-500">
              所属部门 <span class="text-rose-500">*</span>
              <select v-model.number="form.departmentId" data-testid="position-department" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10">
                <option disabled value="">请选择所属部门</option>
                <option v-for="department in departmentOptions" :key="department.id" :value="department.id">{{ department.departmentName }}</option>
              </select>
            </label>
            <label class="block text-xs font-medium text-slate-500">
              状态
              <select v-model="form.status" data-testid="position-status" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-sm font-normal text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10">
                <option value="enabled">启用</option>
                <option value="disabled">停用</option>
              </select>
            </label>
            <label class="col-span-2 block text-xs font-medium text-slate-500">
              岗位职责
              <textarea v-model="form.responsibilities" data-testid="position-responsibilities" rows="5" class="mt-1.5 w-full resize-none rounded-[6px] border border-slate-200 px-3 py-2.5 text-sm font-normal leading-6 text-[#25314d] outline-none placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10" placeholder="请输入岗位职责"></textarea>
            </label>
          </div>
        </section>

        <p class="mt-4 rounded-[6px] bg-[#f1f4ff] px-4 py-3 text-xs leading-5 text-[#536dff]">岗位名称和编码在同一部门内保持唯一；已有员工的岗位不能调整所属部门。</p>
      </div>

      <footer class="flex h-[72px] shrink-0 items-center justify-end gap-3 border-t border-slate-200 px-6">
        <button data-testid="cancel-position" type="button" class="h-10 w-20 rounded-[6px] border border-slate-200 bg-white text-sm font-medium text-slate-600 transition hover:bg-slate-50" @click="emit('close')">取消</button>
        <button data-testid="save-position" type="button" class="h-10 min-w-[104px] rounded-[6px] bg-[#536dff] px-5 text-sm font-medium text-white transition hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存岗位' }}</button>
      </footer>
    </aside>
  </div>
</template>
