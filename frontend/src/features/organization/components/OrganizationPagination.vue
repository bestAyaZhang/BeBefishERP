<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
import { computed } from 'vue';

type PageItem = number | 'ellipsis-left' | 'ellipsis-right';

const props = defineProps<{
  page: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  page: [page: number];
  'page-size': [pageSize: number];
}>();

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));
const currentPage = computed(() => Math.min(Math.max(props.page, 1), pageCount.value));

const pageItems = computed<PageItem[]>(() => {
  const totalPages = pageCount.value;
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);
  if (currentPage.value <= 4) return [1, 2, 3, 4, 5, 'ellipsis-right', totalPages];
  if (currentPage.value >= totalPages - 3) {
    return [1, 'ellipsis-left', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
  }
  return [1, 'ellipsis-left', currentPage.value - 1, currentPage.value, currentPage.value + 1, 'ellipsis-right', totalPages];
});

function changePage(page: number) {
  if (!Number.isInteger(page) || page < 1 || page > pageCount.value || page === currentPage.value) return;
  emit('page', page);
}

function changePageSize(event: Event) {
  const pageSize = Number((event.target as HTMLSelectElement).value);
  if ([10, 20, 50].includes(pageSize)) emit('page-size', pageSize);
}
</script>

<template>
  <footer
    data-testid="organization-pagination"
    class="flex min-h-16 min-w-0 items-center gap-2 border-t border-slate-200 bg-white px-4 text-sm text-slate-600"
  >
    <span data-testid="pagination-total" class="mr-auto whitespace-nowrap font-numeric tabular-nums">
      共 {{ total }} 条
    </span>

    <nav class="flex items-center gap-2" aria-label="组织数据分页">
      <button
        data-testid="previous-page"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-[6px] border border-slate-200 text-slate-600 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:text-slate-300"
        :disabled="currentPage <= 1"
        aria-label="上一页"
        @click="changePage(currentPage - 1)"
      >
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
      </button>

      <template v-for="item in pageItems" :key="item">
        <span
          v-if="typeof item !== 'number'"
          data-testid="page-ellipsis"
          class="flex h-8 min-w-8 items-center justify-center text-slate-400"
          aria-hidden="true"
        >
          ...
        </span>
        <button
          v-else
          :data-testid="`page-${item}`"
          type="button"
          class="flex h-8 min-w-8 items-center justify-center rounded-[6px] border px-2 font-numeric text-sm tabular-nums transition"
          :class="item === currentPage ? 'border-[#536dff] bg-[#536dff] text-white' : 'border-slate-200 text-slate-600 hover:border-[#536dff] hover:text-[#536dff]'"
          :aria-current="item === currentPage ? 'page' : undefined"
          @click="changePage(item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        data-testid="next-page"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-[6px] border border-slate-200 text-slate-600 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:text-slate-300"
        :disabled="currentPage >= pageCount"
        aria-label="下一页"
        @click="changePage(currentPage + 1)"
      >
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </nav>

    <label class="h-8 w-[92px] shrink-0">
      <span class="sr-only">每页条数</span>
      <select
        data-testid="page-size"
        :value="pageSize"
        class="h-8 w-full rounded-[6px] border border-slate-200 bg-white px-2 font-numeric text-xs text-slate-600 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
        aria-label="每页条数"
        @change="changePageSize"
      >
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
      </select>
    </label>
  </footer>
</template>
