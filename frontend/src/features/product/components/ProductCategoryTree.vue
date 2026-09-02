<script setup lang="ts">
import { ChevronDown, ChevronRight, FolderTree, RefreshCw, Search } from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';
import type { Category } from '../../masterdata/types';
import { buildProductCategoryTree, type ProductCategoryNode } from '../productCategoryTree';

interface CategoryRow {
  node: ProductCategoryNode;
  depth: number;
  expanded: boolean;
  hasVisibleChildren: boolean;
}

const props = withDefaults(defineProps<{
  categories?: Category[];
  categoryCounts?: Record<number, number>;
  allProductTotal?: number | null;
  selectedCategoryId?: number;
  loading?: boolean;
  error?: string;
}>(), {
  categories: () => [],
  categoryCounts: () => ({}),
  allProductTotal: null,
  selectedCategoryId: undefined,
  loading: false,
  error: ''
});

const emit = defineEmits<{
  'select-category': [categoryId?: number];
  retry: [];
}>();

const searchKeyword = ref('');
const expandedIds = ref<Set<number>>(new Set());
let knownRootIds = new Set<number>();

const treeModel = computed(() => buildProductCategoryTree(props.categories));
const tree = computed(() => treeModel.value.roots);
const rootCategoryCount = computed(() => tree.value.length);
const childCategoryCount = computed(() => Math.max(0, treeModel.value.categories.length - rootCategoryCount.value));

watch(treeModel, (model) => {
  const existingIds = new Set(model.categories.map((category) => category.id));
  const nextRootIds = new Set(model.roots.map((root) => root.id));
  const nextExpandedIds = new Set([...expandedIds.value].filter((id) => existingIds.has(id)));
  for (const rootId of nextRootIds) {
    if (!knownRootIds.has(rootId)) nextExpandedIds.add(rootId);
  }
  expandedIds.value = nextExpandedIds;
  knownRootIds = nextRootIds;
}, { immediate: true });

const normalizedSearch = computed(() => searchKeyword.value.trim().toLocaleLowerCase('zh-CN'));

const matchingState = computed(() => {
  const visibleIds = new Set<number>();
  const autoExpandedIds = new Set<number>();
  if (!normalizedSearch.value) return { visibleIds, autoExpandedIds };

  for (const category of treeModel.value.categories) {
    if (!category.categoryName.toLocaleLowerCase('zh-CN').includes(normalizedSearch.value)) continue;
    visibleIds.add(category.id);
    let parentId = treeModel.value.parentById.get(category.id) ?? null;
    const visited = new Set<number>([category.id]);
    while (parentId !== null && !visited.has(parentId)) {
      visited.add(parentId);
      visibleIds.add(parentId);
      autoExpandedIds.add(parentId);
      parentId = treeModel.value.parentById.get(parentId) ?? null;
    }
  }
  return { visibleIds, autoExpandedIds };
});

const rows = computed<CategoryRow[]>(() => {
  const result: CategoryRow[] = [];
  const searchActive = Boolean(normalizedSearch.value);
  const visibleIds = matchingState.value.visibleIds;

  const visit = (nodes: ProductCategoryNode[], depth: number) => {
    for (const node of nodes) {
      if (searchActive && !visibleIds.has(node.id)) continue;
      const visibleChildren = searchActive
        ? node.children.filter((child) => visibleIds.has(child.id))
        : node.children;
      const expanded = searchActive
        ? matchingState.value.autoExpandedIds.has(node.id)
        : expandedIds.value.has(node.id);
      result.push({ node, depth, expanded, hasVisibleChildren: visibleChildren.length > 0 });
      if (expanded) visit(visibleChildren, depth + 1);
    }
  };

  visit(tree.value, 0);
  return result;
});

function toggleCategory(categoryId: number) {
  if (normalizedSearch.value) return;
  const next = new Set(expandedIds.value);
  if (next.has(categoryId)) next.delete(categoryId);
  else next.add(categoryId);
  expandedIds.value = next;
}
</script>

<template>
  <aside
    data-testid="product-category-panel"
    class="flex min-w-0 flex-col overflow-hidden rounded-lg border border-slate-200 bg-white p-4"
    aria-label="商品分类筛选"
  >
    <div class="flex h-9 shrink-0 items-center border-b border-slate-200 pb-3 text-card-title text-[#25314d]">
      <div class="flex items-center gap-2">
        <FolderTree class="h-[18px] w-[18px] text-slate-500" aria-hidden="true" />
        商品分类
      </div>
    </div>

    <div class="pt-3">
      <label class="relative block">
        <span class="sr-only">分类名称</span>
        <Search class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" aria-hidden="true" />
        <input
          v-model="searchKeyword"
          data-testid="category-search"
          type="search"
          class="h-10 w-full rounded-lg border border-slate-200 bg-white pl-10 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10"
          placeholder="搜索分类名称"
        />
      </label>
    </div>

    <div
      v-if="error"
      data-testid="category-tree-error"
      class="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800"
      role="alert"
    >
      <p>{{ error }}</p>
      <button
        type="button"
        class="mt-2 inline-flex h-7 items-center gap-1.5 font-semibold text-rose-700 hover:text-rose-900"
        @click="emit('retry')"
      >
        <RefreshCw class="h-3.5 w-3.5" aria-hidden="true" />
        重新加载
      </button>
    </div>

    <div class="min-h-[240px] flex-1 overflow-y-auto py-3 lg:min-h-0">
      <div v-if="loading && categories.length === 0" class="space-y-2 px-2" aria-label="正在加载分类">
        <div v-for="index in 6" :key="index" class="h-8 animate-pulse rounded bg-slate-200/70"></div>
      </div>
      <template v-else>
        <button
          data-testid="category-node-all"
          type="button"
          class="mb-0.5 flex h-9 w-full items-center gap-2 rounded-md pl-2 pr-1.5 text-left text-sm transition"
          :class="selectedCategoryId === undefined ? 'bg-slate-50 font-medium text-[#536dff]' : 'text-[#25314d] hover:bg-slate-50'"
          :aria-pressed="selectedCategoryId === undefined"
          @click="emit('select-category', undefined)"
        >
          <span class="h-5 w-[3px] shrink-0 rounded-sm" :class="selectedCategoryId === undefined ? 'bg-[#536dff]' : 'bg-transparent'"></span>
          <span class="w-4 shrink-0"></span>
          <span class="min-w-0 flex-1 truncate" title="全部商品">全部商品</span>
          <span class="shrink-0 text-xs tabular-nums text-slate-400">{{ allProductTotal ?? '--' }}</span>
        </button>

        <div v-if="normalizedSearch && rows.length === 0" data-testid="category-empty" class="px-3 py-8 text-center text-xs text-slate-500">
          未找到匹配分类
        </div>

        <div v-for="row in rows" :key="row.node.id" class="relative flex min-w-0 items-center">
          <button
            v-if="row.hasVisibleChildren"
            :data-testid="`category-toggle-${row.node.id}`"
            type="button"
            class="absolute z-10 flex h-7 w-7 items-center justify-center rounded text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
            :style="{ left: `${row.depth * 16 + 9}px` }"
            :aria-label="row.expanded ? `收起${row.node.categoryName}` : `展开${row.node.categoryName}`"
            @click.stop="toggleCategory(row.node.id)"
          >
            <ChevronDown v-if="row.expanded" class="h-4 w-4" aria-hidden="true" />
            <ChevronRight v-else class="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            :data-testid="`category-node-${row.node.id}`"
            type="button"
            class="mb-0.5 flex h-9 min-w-0 flex-1 items-center gap-2 rounded-md pr-1.5 text-left text-sm transition"
            :class="selectedCategoryId === row.node.id ? 'bg-slate-50 font-medium text-[#536dff]' : 'text-[#25314d] hover:bg-slate-50'"
            :style="{ paddingLeft: `${row.depth * 16 + 8}px` }"
            :aria-pressed="selectedCategoryId === row.node.id"
            @click="emit('select-category', row.node.id)"
          >
            <span class="h-5 w-[3px] shrink-0 rounded-sm" :class="selectedCategoryId === row.node.id ? 'bg-[#536dff]' : 'bg-transparent'"></span>
            <span v-if="row.hasVisibleChildren" class="w-4 shrink-0"></span>
            <span v-else class="w-4 shrink-0"></span>
            <span class="min-w-0 flex-1 truncate" :title="row.node.categoryName">{{ row.node.categoryName }}</span>
            <span class="shrink-0 text-xs tabular-nums text-slate-400">{{ categoryCounts[row.node.id] ?? 0 }}</span>
          </button>
        </div>
      </template>
    </div>

    <div class="shrink-0 border-t border-slate-100 pt-3 text-xs leading-5 text-slate-500">
      <p>{{ rootCategoryCount }} 个一级分类 · {{ childCategoryCount }} 个子分类</p>
      <p>选择分类即可筛选右侧商品</p>
    </div>
  </aside>
</template>
