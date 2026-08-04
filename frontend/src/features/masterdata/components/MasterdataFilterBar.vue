<script setup lang="ts">
import { Search } from 'lucide-vue-next';
import type { RecordStatus } from '../types';

defineProps<{
  keyword: string;
  status: RecordStatus | '';
  placeholder: string;
}>();

const emit = defineEmits<{
  'update:keyword': [value: string];
  'update:status': [value: RecordStatus | ''];
  search: [];
}>();

function updateKeyword(event: Event) {
  emit('update:keyword', (event.target as HTMLInputElement).value);
}

function updateStatus(event: Event) {
  emit('update:status', (event.target as HTMLSelectElement).value as RecordStatus | '');
  emit('search');
}
</script>

<template>
  <div data-testid="masterdata-filter-bar" class="flex flex-col gap-3 rounded-[18px] border border-slate-200 bg-white p-4 shadow-[0_10px_30px_rgba(31,45,74,0.03)] sm:flex-row sm:items-center">
    <label class="flex h-11 min-w-0 flex-1 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 transition focus-within:border-[#536dff] focus-within:ring-4 focus-within:ring-blue-50">
      <Search class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
      <input :value="keyword" class="h-full min-w-0 flex-1 border-0 bg-transparent text-sm font-bold text-[#25314d] outline-none placeholder:text-slate-400" :placeholder="placeholder" @input="updateKeyword" @keyup.enter="$emit('search')" />
    </label>
    <select :value="status" class="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition hover:border-[#536dff] focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" @change="updateStatus">
      <option value="">全部状态</option>
      <option value="enabled">启用</option>
      <option value="disabled">停用</option>
    </select>
    <button data-testid="masterdata-search-button" type="button" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-md shadow-blue-100 transition hover:bg-[#435be3]" @click="$emit('search')">
      <Search class="h-4 w-4" aria-hidden="true" />
      查询
    </button>
  </div>
</template>
