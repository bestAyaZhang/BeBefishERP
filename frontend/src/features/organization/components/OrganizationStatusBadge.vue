<script setup lang="ts">
import { computed } from 'vue';
import type { EmployeeStatus, FeishuBindingStatus, OrganizationRecordStatus } from '../types';

type OrganizationStatus = EmployeeStatus | OrganizationRecordStatus | FeishuBindingStatus;

const statusPresentation = {
  active: ['在职', 'bg-emerald-50 text-emerald-700'],
  disabled: ['停用', 'bg-slate-100 text-slate-600'],
  resigned: ['离职', 'bg-rose-50 text-rose-600'],
  enabled: ['启用', 'bg-emerald-50 text-emerald-700'],
  bound: ['已绑定', 'bg-blue-50 text-blue-700'],
  pending: ['待绑定', 'bg-amber-50 text-amber-700'],
  unbound: ['未绑定', 'bg-slate-100 text-slate-600']
} as const satisfies Record<OrganizationStatus, readonly [string, string]>;

const props = defineProps<{
  status: OrganizationStatus;
}>();

const presentation = computed(() => statusPresentation[props.status]);
</script>

<template>
  <span
    data-testid="organization-status-badge"
    class="inline-flex h-7 min-w-14 items-center justify-center whitespace-nowrap rounded-[6px] px-2 text-xs font-medium"
    :class="presentation[1]"
  >
    {{ presentation[0] }}
  </span>
</template>
