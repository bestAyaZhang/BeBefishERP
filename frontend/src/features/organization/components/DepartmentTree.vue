<script setup lang="ts">
import { Building2, ChevronDown, ChevronRight, Search } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { filterDepartmentTree } from '../organizationTree';
import type { DepartmentTreeNode } from '../types';

interface DepartmentRow {
  node: DepartmentTreeNode;
  depth: number;
  expanded: boolean;
}

export interface DepartmentTreeSummary {
  label: string;
  title: string;
  meta: string;
}

const props = withDefaults(defineProps<{
  nodes: DepartmentTreeNode[];
  selectedId: number | null;
  expandedIds?: number[];
  employeeCounts?: Record<number, number>;
  title?: string;
  summary?: DepartmentTreeSummary | null;
}>(), {
  expandedIds: () => [],
  employeeCounts: () => ({}),
  title: '组织架构',
  summary: null
});

const emit = defineEmits<{
  select: [id: number | null];
  toggle: [id: number];
}>();

const searchKeyword = ref('');
const normalizedSearch = computed(() => searchKeyword.value.trim());
const visibleTree = computed(() => filterDepartmentTree(props.nodes, normalizedSearch.value));
const expandedIdSet = computed(() => new Set(props.expandedIds));
const subtreeEmployeeCounts = computed(() => {
  const totals: Record<number, number> = {};
  const sumNode = (node: DepartmentTreeNode): number => {
    const total = (props.employeeCounts[node.id] ?? 0)
      + node.children.reduce((sum, child) => sum + sumNode(child), 0);
    totals[node.id] = total;
    return total;
  };

  props.nodes.forEach(sumNode);
  return totals;
});

const allEmployeeCount = computed(() => props.nodes.reduce(
  (total, root) => total + (subtreeEmployeeCounts.value[root.id] ?? 0),
  0
));

const rows = computed<DepartmentRow[]>(() => {
  const result: DepartmentRow[] = [];
  const searching = normalizedSearch.value.length > 0;

  const visit = (nodes: DepartmentTreeNode[], depth: number) => {
    for (const node of nodes) {
      const expanded = searching || expandedIdSet.value.has(node.id);
      result.push({ node, depth, expanded });
      if (expanded) visit(node.children, depth + 1);
    }
  };

  visit(visibleTree.value, 0);
  return result;
});
</script>

<template>
  <aside
    data-testid="department-tree"
    class="flex h-full w-[280px] min-w-[280px] flex-col overflow-hidden rounded-[6px] border border-slate-200 bg-white"
    aria-label="组织架构筛选"
  >
    <header class="flex h-12 shrink-0 items-center gap-2 border-b border-slate-200 px-3 text-card-title text-[#25314d]">
      <Building2 class="h-[18px] w-[18px] text-slate-500" aria-hidden="true" />
      <span data-testid="department-tree-title" class="min-w-0 flex-1 truncate">{{ title }}</span>
    </header>

    <div class="shrink-0 px-3 pb-2 pt-3">
      <label class="relative block">
        <span class="sr-only">搜索部门</span>
        <Search
          class="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        <input
          v-model="searchKeyword"
          data-testid="department-search"
          type="search"
          class="h-8 w-full rounded-[6px] border border-slate-200 bg-white pl-8 pr-2.5 text-xs text-[#25314d] outline-none transition placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
          placeholder="搜索部门"
        />
      </label>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
      <button
        data-testid="department-all"
        type="button"
        class="mb-0.5 flex h-9 w-full items-center gap-2 rounded-[6px] px-2 text-left text-sm transition"
        :class="selectedId === null ? 'bg-[#eef2ff] font-medium text-[#536dff]' : 'text-slate-600 hover:bg-slate-50'"
        :aria-pressed="selectedId === null"
        @click="emit('select', null)"
      >
        <Building2 class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="min-w-0 flex-1 truncate">全公司</span>
        <span class="shrink-0 font-numeric text-xs tabular-nums text-slate-400">{{ allEmployeeCount }}</span>
      </button>

      <p
        v-if="normalizedSearch && rows.length === 0"
        data-testid="department-empty"
        class="px-3 py-8 text-center text-xs text-slate-500"
      >
        未找到匹配部门
      </p>

      <div v-for="row in rows" :key="row.node.id" class="relative flex min-w-0 items-center">
        <button
          v-if="row.node.children.length > 0"
          :data-testid="`department-toggle-${row.node.id}`"
          type="button"
          class="absolute z-10 flex h-7 w-7 items-center justify-center rounded-[6px] text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          :style="{ left: `${row.depth * 16 + 5}px` }"
          :aria-label="row.expanded ? `收起${row.node.departmentName}` : `展开${row.node.departmentName}`"
          @click.stop="emit('toggle', row.node.id)"
        >
          <ChevronDown v-if="row.expanded" class="h-4 w-4" aria-hidden="true" />
          <ChevronRight v-else class="h-4 w-4" aria-hidden="true" />
        </button>

        <button
          :data-testid="`department-node-${row.node.id}`"
          type="button"
          class="mb-0.5 flex h-9 min-w-0 flex-1 items-center gap-2 rounded-[6px] pr-2 text-left text-sm transition"
          :class="selectedId === row.node.id ? 'bg-[#eef2ff] font-medium text-[#536dff]' : 'text-slate-600 hover:bg-slate-50'"
          :style="{ paddingLeft: `${row.depth * 16 + 8}px` }"
          :aria-pressed="selectedId === row.node.id"
          @click="emit('select', row.node.id)"
        >
          <span class="h-4 w-4 shrink-0" aria-hidden="true"></span>
          <span class="min-w-0 flex-1 truncate" :title="row.node.departmentName">
            {{ row.node.departmentName }}
          </span>
          <span class="shrink-0 font-numeric text-xs tabular-nums text-slate-400">
            {{ subtreeEmployeeCounts[row.node.id] ?? 0 }}
          </span>
        </button>
      </div>
    </div>

    <footer v-if="summary" data-testid="department-tree-summary" class="min-h-[104px] shrink-0 border-t border-slate-200 bg-slate-50 px-4 py-3">
      <p class="text-xs text-slate-400">{{ summary.label }}</p>
      <p data-testid="department-tree-summary-title" class="mt-1 truncate text-sm font-medium text-[#25314d]">{{ summary.title }}</p>
      <p class="mt-1 text-xs leading-[18px] text-slate-500">{{ summary.meta }}</p>
    </footer>
  </aside>
</template>
