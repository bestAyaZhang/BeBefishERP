<script setup lang="ts">
import { Columns3, RotateCcw } from 'lucide-vue-next';
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  MIN_CONFIGURABLE_PRODUCT_COLUMNS,
  PRODUCT_CATALOG_COLUMNS,
  normalizeProductColumnIds
} from '../productCatalogColumns';
import type { ProductCatalogColumnId } from '../productCatalogColumns';

const props = defineProps<{
  modelValue: ProductCatalogColumnId[];
}>();

const emit = defineEmits<{
  'update:modelValue': [ids: ProductCatalogColumnId[]];
  reset: [];
}>();

const root = ref<HTMLElement>();
const trigger = ref<HTMLButtonElement>();
const open = ref(false);

function isSelected(id: ProductCatalogColumnId) {
  return props.modelValue.includes(id);
}

function isLocked(id: ProductCatalogColumnId) {
  return isSelected(id) && props.modelValue.length <= MIN_CONFIGURABLE_PRODUCT_COLUMNS;
}

function updateColumn(id: ProductCatalogColumnId) {
  const selected = isSelected(id);
  if (selected && props.modelValue.length <= MIN_CONFIGURABLE_PRODUCT_COLUMNS) return;

  const nextIds = selected
    ? props.modelValue.filter((columnId) => columnId !== id)
    : [...props.modelValue, id];
  emit('update:modelValue', normalizeProductColumnIds(nextIds));
}

function removeDocumentListeners() {
  document.removeEventListener('keydown', onKeydown);
  document.removeEventListener('pointerdown', onPointerDown);
}

function closeAndFocusTrigger() {
  open.value = false;
  void nextTick(() => trigger.value?.focus());
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeAndFocusTrigger();
}

function onPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false;
}

watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', onKeydown);
    document.addEventListener('pointerdown', onPointerDown);
    return;
  }
  removeDocumentListeners();
});

onBeforeUnmount(removeDocumentListeners);
</script>

<template>
  <div ref="root" class="relative ml-auto shrink-0">
    <button
      ref="trigger"
      data-testid="product-column-trigger"
      type="button"
      :aria-expanded="open"
      aria-controls="product-column-panel"
      class="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-[#25314d] transition hover:border-slate-300 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
      @click="open = !open"
    >
      <Columns3 class="h-4 w-4 text-slate-500" aria-hidden="true" />
      显示字段
    </button>

    <div
      v-if="open"
      id="product-column-panel"
      data-testid="product-column-panel"
      class="absolute right-0 top-[calc(100%+8px)] z-30 w-[520px] overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl"
    >
      <div class="flex h-14 items-center justify-between border-b border-slate-200 px-4">
        <div>
          <p class="font-medium text-[#25314d]">选择列表字段</p>
          <p data-testid="product-column-count" class="mt-0.5 text-xs text-slate-500">
            已选 {{ modelValue.length }} 个业务字段
          </p>
        </div>
        <button
          data-testid="product-column-reset"
          type="button"
          class="inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-xs font-medium text-[#536dff] transition hover:bg-blue-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#536dff]"
          @click="emit('reset')"
        >
          <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
          恢复默认
        </button>
      </div>

      <div class="max-h-[344px] overflow-y-auto p-3">
        <div class="grid grid-cols-2 gap-1">
          <label
            v-for="column in PRODUCT_CATALOG_COLUMNS"
            :key="column.id"
            :for="`product-column-${column.id}`"
            class="flex h-9 min-w-0 cursor-pointer items-center gap-2 rounded-md px-2.5 text-sm text-slate-700 transition hover:bg-slate-50"
            :class="isLocked(column.id) ? 'cursor-not-allowed text-slate-400' : ''"
          >
            <input
              :id="`product-column-${column.id}`"
              :data-testid="`product-column-checkbox-${column.id}`"
              type="checkbox"
              class="h-4 w-4 shrink-0 rounded border-slate-300 accent-[#536dff] focus:ring-[#536dff]"
              :checked="isSelected(column.id)"
              :disabled="isLocked(column.id)"
              @change="updateColumn(column.id)"
            />
            <span class="truncate">{{ column.label }}</span>
          </label>
        </div>
      </div>

      <p
        data-testid="product-column-help"
        class="border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-500"
      >
        至少保留 6 个业务字段，操作列固定显示。
      </p>
    </div>
  </div>
</template>
