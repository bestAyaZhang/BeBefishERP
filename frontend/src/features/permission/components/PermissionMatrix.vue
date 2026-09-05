<script setup lang="ts">
import { moduleSelectionState, toggleModulePermission } from '../permissionRules';
import type { PermissionAction, PermissionModule } from '../types';

const actions: { key: PermissionAction; label: string }[] = [
  { key: 'view', label: '查看' }, { key: 'create', label: '新增' }, { key: 'edit', label: '编辑' },
  { key: 'delete', label: '删除' }, { key: 'approve', label: '审核' }, { key: 'export', label: '导出' }
];
const props = defineProps<{ modules: PermissionModule[]; modelValue: string[]; readonly: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [codes: string[]] }>();

function code(module: PermissionModule, action: PermissionAction) { return `${module.key}:${action}`; }
function setAction(module: PermissionModule, action: PermissionAction, checked: boolean) {
  if (!props.readonly) emit('update:modelValue', toggleModulePermission(module, props.modelValue, action, checked));
}
function toggleGroup(module: PermissionModule, checked: boolean) {
  if (props.readonly) return;
  const next = new Set(props.modelValue);
  module.supportedActions.forEach((action) => checked ? next.add(code(module, action)) : next.delete(code(module, action)));
  emit('update:modelValue', [...next].sort());
}
</script>

<template>
  <div class="overflow-x-auto" data-testid="permission-matrix">
    <table class="w-full min-w-[760px] border-collapse text-sm">
      <thead class="border-y border-slate-200 bg-slate-50 text-left text-xs font-semibold text-slate-500"><tr><th class="px-4 py-3">模块</th><th class="px-4 py-3">权限说明</th><th v-for="action in actions" :key="action.key" class="px-3 py-3 text-center">{{ action.label }}</th></tr></thead>
      <tbody>
        <tr v-for="module in modules" :key="module.key" class="border-b border-slate-200 text-[#25314d]">
          <td class="px-4 py-3"><label class="flex items-center gap-2"><input :data-testid="`permission-group-${module.key}`" type="checkbox" :checked="moduleSelectionState(module, modelValue) === 'checked'" :indeterminate="moduleSelectionState(module, modelValue) === 'mixed'" :aria-checked="moduleSelectionState(module, modelValue) === 'mixed' ? 'mixed' : moduleSelectionState(module, modelValue) === 'checked'" :disabled="readonly" @change="toggleGroup(module, ($event.target as HTMLInputElement).checked)" /><span class="font-medium">{{ module.label }}</span></label></td>
          <td class="px-4 py-3 text-slate-500">{{ module.description }}</td>
          <td v-for="action in actions" :key="action.key" class="px-3 py-3 text-center">
            <input v-if="module.supportedActions.includes(action.key)" :data-testid="`permission-${module.key}-${action.key}`" type="checkbox" :checked="modelValue.includes(code(module, action.key))" :disabled="readonly" :aria-label="`${module.label}${action.label}`" @change="setAction(module, action.key, ($event.target as HTMLInputElement).checked)" />
            <span v-else :data-testid="`permission-${module.key}-${action.key}`" class="text-slate-300" aria-label="不适用">—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
