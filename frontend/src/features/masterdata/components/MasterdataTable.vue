<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
import type { MasterdataRow } from '../types';

defineProps<{
  records: MasterdataRow[];
  columns: Array<{ key: string; label: string }>;
  page: number;
  pageSize: number;
  total: number;
  loading?: boolean;
}>();

defineEmits<{ page: [page: number] }>();
</script>

<template>
  <div data-testid="masterdata-table" class="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
    <div class="overflow-x-auto">
      <table class="w-full min-w-[900px] border-collapse text-left text-sm">
        <thead class="bg-slate-50 text-xs font-black text-slate-400">
          <tr>
            <th v-for="column in columns" :key="column.key" class="whitespace-nowrap border-b border-slate-100 px-5 py-3">{{ column.label }}</th>
            <th class="w-56 border-b border-slate-100 px-5 py-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 text-slate-700">
          <tr v-if="loading">
            <td :colspan="columns.length + 1" class="h-40 px-5 text-center text-sm font-bold text-slate-400">正在加载...</td>
          </tr>
          <tr v-else-if="records.length === 0">
            <td :colspan="columns.length + 1" class="h-40 px-5 text-center text-sm font-bold text-slate-400">暂无数据</td>
          </tr>
          <tr v-for="record in records" v-else :key="record.id" class="min-h-[76px] bg-white text-sm transition hover:bg-slate-50">
            <td v-for="column in columns" :key="column.key" class="max-w-64 px-5 py-4 align-middle">
              <slot :name="`cell-${column.key}`" :record="record">
                <span v-if="column.key === 'status'" class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="record.status === 'enabled' ? 'bg-emerald-50 text-emerald-500' : 'bg-slate-100 text-slate-500'">
                  {{ record.status === 'enabled' ? '启用' : '停用' }}
                </span>
                <span v-else class="block truncate font-bold text-[#25314d]">{{ record[column.key] ?? '-' }}</span>
              </slot>
            </td>
            <td class="whitespace-nowrap px-5 py-4 text-right"><slot name="actions" :record="record" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer data-testid="masterdata-table-pagination" class="mt-4 flex min-w-[900px] items-center justify-between gap-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 text-sm font-bold text-slate-500">
      <span class="text-slate-600">共 {{ total }} 条</span>
      <div class="flex items-center gap-2">
        <button type="button" class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300" :disabled="page <= 1" aria-label="上一页" @click="$emit('page', page - 1)">
          <ChevronLeft class="h-4 w-4" aria-hidden="true" />
        </button>
        <span>{{ page }} / {{ Math.max(1, Math.ceil(total / pageSize)) }}</span>
        <button type="button" class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300" :disabled="page >= Math.max(1, Math.ceil(total / pageSize))" aria-label="下一页" @click="$emit('page', page + 1)">
          <ChevronRight class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </footer>
  </div>
</template>
