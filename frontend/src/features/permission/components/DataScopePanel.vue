<script setup lang="ts">
import type { PermissionDataScope } from '../types';

const scopes: { value: PermissionDataScope; label: string; description: string }[] = [
  { value: 'company', label: '全公司', description: '可访问公司内所有部门和员工数据；示例：总部管理角色。' },
  { value: 'department-and-descendants', label: '本部门及下级', description: '可访问所在部门及全部下级部门；示例：业务中心负责人。' },
  { value: 'department', label: '本部门', description: '仅可访问所在部门；示例：部门主管。' },
  { value: 'self', label: '仅本人', description: '仅可访问本人创建或负责的数据；示例：一线业务员工。' }
];
const props = defineProps<{ modelValue: PermissionDataScope; readonly: boolean; organizationSummary: string }>();
const emit = defineEmits<{ 'update:modelValue': [scope: PermissionDataScope] }>();
function select(scope: PermissionDataScope) { if (!props.readonly) emit('update:modelValue', scope); }
</script>

<template>
  <section class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
    <div><p class="mb-4 rounded-[6px] bg-[#f1f4ff] px-4 py-3 text-sm text-[#536dff]">员工拥有多个角色时，最终数据范围按最宽授权计算。</p>
      <div class="space-y-2"><label v-for="scope in scopes" :key="scope.value" class="flex cursor-pointer items-start gap-3 rounded-[6px] border border-slate-200 px-4 py-3 has-[:checked]:border-[#536dff] has-[:checked]:bg-[#f8f9ff]"><input :data-testid="`scope-${scope.value}`" type="radio" name="data-scope" :checked="modelValue === scope.value" :disabled="readonly" :value="scope.value" @change="select(scope.value)" /><span><span class="block font-medium text-[#25314d]">{{ scope.label }}：</span><span class="mt-1 block text-sm text-slate-500">{{ scope.description }}</span></span></label></div>
    </div>
    <aside data-testid="organization-summary" class="h-fit rounded-[6px] border border-slate-200 p-4"><h3 class="font-medium text-[#25314d]">当前可访问范围</h3><p class="mt-2 text-sm text-slate-500">{{ organizationSummary }}</p><p class="mt-3 text-xs text-slate-400">此范围为只读组织摘要。</p></aside>
  </section>
</template>
