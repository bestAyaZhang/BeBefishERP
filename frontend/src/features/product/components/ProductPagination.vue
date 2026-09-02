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
    class="flex min-w-0 flex-col items-stretch gap-3 border-t border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 lg:h-16 lg:flex-row lg:flex-wrap lg:items-center lg:gap-2 lg:py-0"
  >
    <div
      data-testid="product-pagination-summary"
      class="flex min-w-0 w-full items-center justify-between gap-3 lg:mr-auto lg:w-auto lg:flex-1"
    >
      <span class="min-w-0 truncate" :title="`共 ${total} 条`">
        共 <span class="tabular-nums">{{ total }}</span> 条
      </span>

      <label
        data-testid="product-page-size-control"
        class="flex shrink-0 items-center whitespace-nowrap text-xs text-slate-500 lg:hidden"
      >
        <select
          data-testid="product-page-size"
          :value="size"
          class="h-8 rounded-md border border-slate-200 bg-white px-2 text-sm text-slate-700 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
          aria-label="每页条数"
          @change="changeSize"
        >
          <option :value="10">10 条 / 页</option>
          <option :value="20">20 条 / 页</option>
          <option :value="50">50 条 / 页</option>
        </select>
      </label>
    </div>

    <nav
      data-testid="product-pagination-mobile"
      class="flex w-full min-w-0 items-center justify-between gap-2 lg:hidden"
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

    <nav data-testid="product-pagination-desktop" class="hidden min-w-0 items-center gap-2 lg:flex" aria-label="商品分页">
      <button
        data-testid="product-page-prev"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:opacity-45"
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
          class="flex h-8 min-w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600"
          aria-hidden="true"
        >
          ...
        </span>
        <button
          v-else
          :data-testid="`product-page-${item}`"
          type="button"
          class="flex h-8 min-w-8 items-center justify-center rounded-md border px-2 text-sm transition"
          :class="item === page ? 'border-[#536dff] bg-[#536dff] text-white' : 'border-slate-200 text-[#25314d] hover:border-[#536dff] hover:text-[#536dff]'"
          :aria-current="item === page ? 'page' : undefined"
          @click="emit('change-page', item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        data-testid="product-page-next"
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:opacity-45"
        :disabled="page >= pageCount"
        aria-label="下一页"
        @click="emit('change-page', page + 1)"
      >
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </nav>

    <label
      data-testid="product-page-size-control-desktop"
      class="hidden h-8 w-[104px] shrink-0 items-center lg:flex"
    >
      <span class="sr-only">每页条数</span>
      <select
        data-testid="product-page-size-desktop"
        :value="size"
        class="h-8 w-full rounded-md border border-slate-200 bg-white px-2 text-sm text-[#25314d] outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
        aria-label="每页条数"
        @change="changeSize"
      >
        <option :value="10">10 条 / 页</option>
        <option :value="20">20 条 / 页</option>
        <option :value="50">50 条 / 页</option>
      </select>
    </label>
  </footer>
</template>
