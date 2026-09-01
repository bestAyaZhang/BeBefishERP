<script setup lang="ts">
import { ChevronDown, ChevronRight, FolderTree, RefreshCw, Search } from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';
import type { Category } from '../../masterdata/types';

interface CategoryNode extends Category {
  children: CategoryNode[];
}

interface CategoryRow {
  node: CategoryNode;
  depth: number;
  expanded: boolean;
  hasVisibleChildren: boolean;
}

const props = withDefaults(defineProps<{
  categories?: Category[];
  categoryCounts?: Record<number, number>;
  selectedCategoryId?: number;
  loading?: boolean;
  error?: string;
}>(), {
  categories: () => [],
  categoryCounts: () => ({}),
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
let initializedExpansion = false;

function compareCategories(left: Category, right: Category) {
  return left.sortOrder - right.sortOrder
    || left.categoryName.localeCompare(right.categoryName, 'zh-CN')
    || left.id - right.id;
}

const tree = computed<CategoryNode[]>(() => {
  const nodes = new Map<number, CategoryNode>();
  for (const category of props.categories) {
    nodes.set(category.id, { ...category, children: [] });
  }

  const roots: CategoryNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentId === null ? undefined : nodes.get(node.parentId);
    if (parent && parent.id !== node.id) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  const sortNodes = (items: CategoryNode[]) => {
    items.sort(compareCategories);
    items.forEach((item) => sortNodes(item.children));
  };
  sortNodes(roots);
  return roots;
});

watch(tree, (roots) => {
  if (initializedExpansion || roots.length === 0) return;
  expandedIds.value = new Set(roots.map((root) => root.id));
  initializedExpansion = true;
}, { immediate: true });

const normalizedSearch = computed(() => searchKeyword.value.trim().toLocaleLowerCase('zh-CN'));

const matchingState = computed(() => {
  const visibleIds = new Set<number>();
  const autoExpandedIds = new Set<number>();
  if (!normalizedSearch.value) return { visibleIds, autoExpandedIds };

  const nodeById = new Map(props.categories.map((category) => [category.id, category]));
  for (const category of props.categories) {
    if (!category.categoryName.toLocaleLowerCase('zh-CN').includes(normalizedSearch.value)) continue;
    visibleIds.add(category.id);
    let parentId = category.parentId;
    const visited = new Set<number>([category.id]);
    while (parentId !== null && !visited.has(parentId)) {
      visited.add(parentId);
      visibleIds.add(parentId);
      autoExpandedIds.add(parentId);
      parentId = nodeById.get(parentId)?.parentId ?? null;
    }
  }
  return { visibleIds, autoExpandedIds };
});

const rows = computed<CategoryRow[]>(() => {
  const result: CategoryRow[] = [];
  const searchActive = Boolean(normalizedSearch.value);
  const visibleIds = matchingState.value.visibleIds;

  const visit = (nodes: CategoryNode[], depth: number) => {
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

const allProductCount = computed(() => tree.value.reduce(
  (total, root) => total + (props.categoryCounts[root.id] ?? 0),
  0
));

function toggleCategory(categoryId: number) {
  if (normalizedSearch.value) return;
  const next = new Set(expandedIds.value);
  if (next.has(categoryId)) next.delete(categoryId);
  else next.add(categoryId);
  expandedIds.value = next;
}
</script>

<template>
  <aside class="min-w-0 bg-slate-50/60" aria-label="商品分类筛选">
    <div class="border-b border-slate-200 px-4 py-4">
      <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <FolderTree class="h-4 w-4 text-blue-600" aria-hidden="true" />
        商品分类
      </div>
      <label class="relative block">
        <span class="sr-only">分类名称</span>
        <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        <input
          v-model="searchKeyword"
          data-testid="category-search"
          type="search"
          class="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          placeholder="分类名称"
        />
      </label>
    </div>

    <div
      v-if="error"
      data-testid="category-tree-error"
      class="m-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800"
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

    <div class="max-h-[320px] overflow-y-auto px-2 py-3 md:max-h-[calc(100vh-220px)] md:min-h-[520px]">
      <div v-if="loading && categories.length === 0" class="space-y-2 px-2" aria-label="正在加载分类">
        <div v-for="index in 6" :key="index" class="h-8 animate-pulse rounded bg-slate-200/70"></div>
      </div>
      <template v-else>
        <button
          data-testid="category-node-all"
          type="button"
          class="mb-1 flex h-9 w-full items-center gap-2 rounded-md px-2 text-left text-sm transition"
          :class="selectedCategoryId === undefined ? 'bg-blue-50 font-semibold text-blue-700' : 'text-slate-700 hover:bg-white'"
          :aria-pressed="selectedCategoryId === undefined"
          @click="emit('select-category', undefined)"
        >
          <span class="w-4 shrink-0"></span>
          <span class="min-w-0 flex-1 truncate" title="全部商品">全部商品</span>
          <span class="shrink-0 text-xs tabular-nums text-slate-400">{{ allProductCount }}</span>
        </button>

        <div v-if="normalizedSearch && rows.length === 0" data-testid="category-empty" class="px-3 py-8 text-center text-xs text-slate-500">
          未找到匹配分类
        </div>

        <div v-for="row in rows" :key="row.node.id" class="relative flex min-w-0 items-center">
          <button
            v-if="row.hasVisibleChildren"
            :data-testid="`category-toggle-${row.node.id}`"
            type="button"
            class="absolute z-10 flex h-7 w-7 items-center justify-center rounded text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
            :style="{ left: `${row.depth * 16 + 4}px` }"
            :aria-label="row.expanded ? `收起${row.node.categoryName}` : `展开${row.node.categoryName}`"
            @click.stop="toggleCategory(row.node.id)"
          >
            <ChevronDown v-if="row.expanded" class="h-4 w-4" aria-hidden="true" />
            <ChevronRight v-else class="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            :data-testid="`category-node-${row.node.id}`"
            type="button"
            class="mb-1 flex h-9 min-w-0 flex-1 items-center gap-2 rounded-md pr-2 text-left text-sm transition"
            :class="selectedCategoryId === row.node.id ? 'bg-blue-50 font-semibold text-blue-700' : 'text-slate-700 hover:bg-white'"
            :style="{ paddingLeft: `${row.depth * 16 + 36}px` }"
            :aria-pressed="selectedCategoryId === row.node.id"
            @click="emit('select-category', row.node.id)"
          >
            <span class="min-w-0 flex-1 truncate" :title="row.node.categoryName">{{ row.node.categoryName }}</span>
            <span class="shrink-0 text-xs tabular-nums text-slate-400">{{ categoryCounts[row.node.id] ?? 0 }}</span>
          </button>
        </div>
      </template>
    </div>
  </aside>
</template>
