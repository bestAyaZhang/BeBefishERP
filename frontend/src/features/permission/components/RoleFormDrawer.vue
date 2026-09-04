<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import type { CreateRolePayload, PermissionRole, PermissionRoleSummary, UpdateRolePayload } from '../types';

type Mode = 'create' | 'edit';
const props = defineProps<{ open: boolean; mode: Mode; role: PermissionRole | null; copySources: PermissionRoleSummary[]; existingCodes: string[]; saving: boolean; error: string }>();
const emit = defineEmits<{ close: []; submit: [payload: CreateRolePayload | UpdateRolePayload] }>();
const form = ref({ name: '', code: '', description: '', copyFromRoleId: null as number | null });
const validationError = ref('');
const title = computed(() => props.mode === 'create' ? '新增角色' : '编辑角色');
const normalizedCode = computed(() => form.value.code.trim().toUpperCase());
const codePattern = /^[A-Z0-9_]+$/;

function reset() {
  form.value = { name: props.role?.name ?? '', code: props.role?.code ?? '', description: props.role?.description ?? '', copyFromRoleId: null };
  validationError.value = '';
}
watch(() => [props.open, props.mode, props.role?.id] as const, reset, { immediate: true });
function validate() {
  if (!form.value.name.trim()) return '请输入角色名称';
  if (props.mode === 'create') {
    if (!normalizedCode.value || !codePattern.test(normalizedCode.value)) return '角色编码仅支持大写字母、数字和下划线';
    const duplicate = props.existingCodes.some((code) => code.toUpperCase() === normalizedCode.value && code.toUpperCase() !== props.role?.code.toUpperCase());
    if (duplicate) return '角色编码已存在';
  }
  return '';
}
function submit() {
  if (props.saving) return;
  validationError.value = validate();
  if (validationError.value) return;
  if (props.mode === 'create') emit('submit', { name: form.value.name.trim(), code: normalizedCode.value, description: form.value.description.trim(), copyFromRoleId: form.value.copyFromRoleId });
  else emit('submit', { name: form.value.name.trim(), description: form.value.description.trim() });
}
</script>

<template>
  <AccessibleDialog :open="open" :title="title" description="维护角色基础资料与权限复制来源" test-id="role-form-drawer" body-test-id="role-form-drawer-body" footer-test-id="role-form-drawer-footer" close-test-id="close-role-form-drawer" overlay-class="items-stretch justify-end" panel-class="h-full w-[min(720px,100vw)] rounded-none border-y-0 border-r-0" body-class="min-h-0 flex-1 overflow-y-auto px-6 py-5" footer-class="px-6 py-4" @cancel="emit('close')">
    <div v-if="error || validationError" data-testid="role-form-error" class="mb-4 rounded-[6px] border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{{ validationError || error }}</div>
    <div class="grid gap-4"><label class="text-sm text-slate-600">角色名称 <span class="text-rose-500">*</span><input v-model="form.name" data-testid="role-name" data-dialog-initial-focus type="text" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 text-[#25314d] outline-none focus:border-[#536dff]" /></label>
      <label class="text-sm text-slate-600">角色编码 <span v-if="mode === 'create'" class="text-rose-500">*</span><input v-model="form.code" data-testid="role-code" type="text" :disabled="mode === 'edit'" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 px-3 font-mono uppercase text-[#25314d] outline-none focus:border-[#536dff] disabled:bg-slate-50" @blur="form.code = normalizedCode" /><span v-if="validationError.includes('编码')" data-testid="role-code-error" class="mt-1 block text-xs text-rose-600">{{ validationError }}</span></label>
      <label class="text-sm text-slate-600">角色说明<textarea v-model="form.description" data-testid="role-description" rows="4" class="mt-1.5 w-full rounded-[6px] border border-slate-200 px-3 py-2 text-[#25314d] outline-none focus:border-[#536dff]" /></label>
      <label v-if="mode === 'create'" class="text-sm text-slate-600">复制权限来源<select v-model="form.copyFromRoleId" data-testid="role-copy-source" class="mt-1.5 h-10 w-full rounded-[6px] border border-slate-200 bg-white px-3 text-[#25314d]"><option :value="null">不复制权限</option><option v-for="source in copySources" :key="source.id" :value="source.id">{{ source.name }}（{{ source.code }}）</option></select></label>
      <p v-if="mode === 'create'" class="rounded-[6px] bg-[#f1f4ff] px-3 py-2 text-xs leading-5 text-[#536dff]">复制权限仅包含功能权限和数据范围，不复制成员。</p>
    </div>
    <template #footer><button data-testid="role-cancel" type="button" class="h-10 rounded-[6px] border border-slate-200 px-5 text-sm text-slate-600" @click="emit('close')">取消</button><button data-testid="role-submit" type="button" class="h-10 rounded-[6px] bg-[#536dff] px-5 text-sm text-white disabled:opacity-60" :disabled="saving" @click="submit">{{ saving ? '保存中...' : mode === 'create' ? '创建角色' : '保存角色' }}</button></template>
  </AccessibleDialog>
</template>
