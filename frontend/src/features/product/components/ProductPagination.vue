<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
import { computed } from 'vue';

type PageItem = number | 'ellipsis-left' | 'ellipsis-right';

const props = defineProps<{
  page: number;
  size: number;
  total: number;
}>();

const emit = defineEmits<{
  'change-page': [page: number];
  'change-size': [size: number];
}>();

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)));

const pageItems = computed<PageItem[]>(() => {
  const totalPages = pageCount.value;
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);

  if (props.page <= 4) return [1, 2, 3, 4, 5, 'ellipsis-right', totalPages];
  if (props.page >= totalPages - 3) {
    return [1, 'ellipsis-left', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
  }
  return [1, 'ellipsis-left', props.page - 1, props.page, props.page + 1, 'ellipsis-right', totalPages];
});

function changeSize(event: Event) {
  const size = Number((event.target as HTMLSelectElement).value);
  if ([10, 20, 50].includes(size)) emit('change-size', size);
}
</script>

<template>
  <footer
    data-testid="product-pagination"
    class="flex min-w-0 flex-col items-stretch gap-3 border-t border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:gap-2 sm:px-5"
  >
    <span class="whitespace-nowrap sm:mr-auto">共 <strong class="font-semibold text-slate-900">{{ total }}</strong> 条</span>

    <label
      data-testid="product-page-size-control"
      class="hidden items-center gap-2 whitespace-nowrap text-xs text-slate-500 sm:flex"
    >
      每页
      <select
        data-testid="product-page-size"
        :value="size"
        class="h-8 rounded-md border border-slate-200 bg-white px-2 text-sm text-slate-700 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        aria-label="每页条数"
        @change="changeSize"
      >
        <option :value="10">10</option>
        <option :value="20">20</option>
        <option :value="50">50</option>
      </select>
    </label>

    <nav
      data-testid="product-pagination-mobile"
      class="flex w-full min-w-0 items-center justify-between gap-2 sm:hidden"
      aria-label="商品分页"
    >
      <button
        data-testid="product-page-prev-mobile"
        type="button"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 text-slate-600 transition enabled:hover:border-blue-400 enabled:hover:text-blue-700 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="emit('change-page', page - 1)"
      >
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
      </button>
      <span class="min-w-0 truncate px-2 text-center tabular-nums" :title="`第 ${page} / ${pageCount} 页`">
        第 {{ page }} / {{ pageCount }} 页
      </span>
      <button
        data-testid="product-page-next-mobile"
        type="button"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 text-slate-600 transition enabled:hover:border-blue-400 enabled:hover:text-blue-700 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
        :disabled="page >= pageCount"
        aria-label="下一页"
        @click="emit('change-page', page + 1)"
      >
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </nav>

    <nav data-testid="product-pagination-desktop" class="hidden items-center gap-1 sm:flex" aria-label="商品分页">
      <button
        data-testid="product-page-prev"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 transition enabled:hover:border-blue-400 enabled:hover:text-blue-700 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="emit('change-page', page - 1)"
      >
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
      </button>

      <template v-for="item in pageItems" :key="item">
        <span
          v-if="typeof item !== 'number'"
          data-testid="product-page-ellipsis"
          class="flex h-8 min-w-6 items-center justify-center text-slate-400"
          aria-hidden="true"
        >
          ...
        </span>
        <button
          v-else
          :data-testid="`product-page-${item}`"
          type="button"
          class="flex h-8 min-w-8 items-center justify-center rounded-md border px-2 text-sm transition"
          :class="item === page ? 'border-blue-600 bg-blue-600 font-semibold text-white' : 'border-slate-200 text-slate-600 hover:border-blue-400 hover:text-blue-700'"
          :aria-current="item === page ? 'page' : undefined"
          @click="emit('change-page', item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        data-testid="product-page-next"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 transition enabled:hover:border-blue-400 enabled:hover:text-blue-700 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
        :disabled="page >= pageCount"
        aria-label="下一页"
        @click="emit('change-page', page + 1)"
      >
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </nav>
  </footer>
</template>
