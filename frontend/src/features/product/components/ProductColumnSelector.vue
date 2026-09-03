<script setup lang="ts">
import { ChevronDown, ChevronUp, Columns3, GripVertical, RotateCcw } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
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
const draggedId = ref<ProductCatalogColumnId | null>(null);
const dragOverId = ref<ProductCatalogColumnId | null>(null);
const selectedColumns = computed(() => props.modelValue
  .map((id) => PRODUCT_CATALOG_COLUMNS.find((column) => column.id === id))
  .filter((column): column is (typeof PRODUCT_CATALOG_COLUMNS)[number] => Boolean(column)));
const availableColumns = computed(() => PRODUCT_CATALOG_COLUMNS.filter((column) => !isSelected(column.id)));

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

function reorderColumn(sourceId: ProductCatalogColumnId, targetId: ProductCatalogColumnId, placeAfter = false) {
  if (sourceId === targetId) return;
  const nextIds = [...props.modelValue];
  const sourceIndex = nextIds.indexOf(sourceId);
  let targetIndex = nextIds.indexOf(targetId);
  if (sourceIndex < 0 || targetIndex < 0) return;

  nextIds.splice(sourceIndex, 1);
  if (sourceIndex < targetIndex) targetIndex -= 1;
  if (placeAfter) targetIndex += 1;
  nextIds.splice(targetIndex, 0, sourceId);
  emit('update:modelValue', normalizeProductColumnIds(nextIds));
}

function moveColumn(id: ProductCatalogColumnId, offset: -1 | 1) {
  const index = props.modelValue.indexOf(id);
  const targetId = props.modelValue[index + offset];
  if (!targetId) return;
  reorderColumn(id, targetId, offset > 0);
}

function onDragStart(id: ProductCatalogColumnId, event: DragEvent) {
  draggedId.value = id;
  if (!event.dataTransfer) return;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', id);
}

function onDragOver(id: ProductCatalogColumnId, event: DragEvent) {
  if (!draggedId.value || draggedId.value === id) return;
  event.preventDefault();
  dragOverId.value = id;
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
}

function onDrop(id: ProductCatalogColumnId, event: DragEvent) {
  event.preventDefault();
  const sourceId = draggedId.value;
  draggedId.value = null;
  dragOverId.value = null;
  if (!sourceId) return;
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
  reorderColumn(sourceId, id, event.clientY > bounds.top + bounds.height / 2);
}

function clearDragState() {
  draggedId.value = null;
  dragOverId.value = null;
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

      <div
        data-testid="product-column-options"
        class="max-h-[clamp(220px,calc(100vh-440px),430px)] overflow-y-auto p-3"
      >
        <p class="mb-2 px-1 text-xs font-medium text-slate-500">已显示字段</p>
        <div class="space-y-1">
          <div
            v-for="(column, index) in selectedColumns"
            :key="column.id"
            :data-testid="`product-column-row-${column.id}`"
            class="flex h-10 min-w-0 items-center gap-2 rounded-md border bg-white px-2 text-sm text-slate-700 transition hover:border-slate-300"
            :class="[
              isLocked(column.id) ? 'text-slate-400' : '',
              dragOverId === column.id ? 'border-[#536dff] bg-[#f5f7ff]' : 'border-slate-200'
            ]"
            @dragover="onDragOver(column.id, $event)"
            @drop="onDrop(column.id, $event)"
          >
            <button
              :data-testid="`product-column-drag-${column.id}`"
              type="button"
              draggable="true"
              :aria-label="`拖动${column.label}排序`"
              :title="`拖动${column.label}排序`"
              class="grid h-7 w-7 shrink-0 cursor-grab place-items-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-600 active:cursor-grabbing"
              @dragstart="onDragStart(column.id, $event)"
              @dragend="clearDragState"
            >
              <GripVertical class="h-4 w-4" aria-hidden="true" />
            </button>
            <span class="w-5 shrink-0 text-center text-xs tabular-nums text-slate-400">{{ index + 1 }}</span>
            <label :for="`product-column-${column.id}`" class="flex min-w-0 flex-1 cursor-pointer items-center gap-2" :class="isLocked(column.id) ? 'cursor-not-allowed' : ''">
              <input
                :id="`product-column-${column.id}`"
                :data-testid="`product-column-checkbox-${column.id}`"
                type="checkbox"
                class="h-4 w-4 shrink-0 rounded border-slate-300 accent-[#536dff] focus:ring-[#536dff]"
                :checked="true"
                :disabled="isLocked(column.id)"
                @change="updateColumn(column.id)"
              />
              <span class="min-w-0 flex-1 truncate">{{ column.label }}</span>
            </label>
            <span class="flex shrink-0 items-center gap-0.5">
              <button
                type="button"
                :disabled="index === 0"
                :aria-label="`${column.label}上移`"
                :title="`${column.label}上移`"
                class="grid h-7 w-7 place-items-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-600 disabled:cursor-not-allowed disabled:opacity-30"
                @click.prevent="moveColumn(column.id, -1)"
              >
                <ChevronUp class="h-4 w-4" aria-hidden="true" />
              </button>
              <button
                type="button"
                :disabled="index === selectedColumns.length - 1"
                :aria-label="`${column.label}下移`"
                :title="`${column.label}下移`"
                class="grid h-7 w-7 place-items-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-600 disabled:cursor-not-allowed disabled:opacity-30"
                @click.prevent="moveColumn(column.id, 1)"
              >
                <ChevronDown class="h-4 w-4" aria-hidden="true" />
              </button>
            </span>
          </div>
        </div>

        <template v-if="availableColumns.length">
          <p class="mb-2 mt-4 px-1 text-xs font-medium text-slate-500">可添加字段</p>
          <div class="grid grid-cols-2 gap-1">
            <label
              v-for="column in availableColumns"
              :key="column.id"
              :for="`product-column-${column.id}`"
              class="flex h-9 min-w-0 cursor-pointer items-center gap-2 rounded-md px-2.5 text-sm text-slate-700 transition hover:bg-slate-50"
            >
              <input
                :id="`product-column-${column.id}`"
                :data-testid="`product-column-checkbox-${column.id}`"
                type="checkbox"
                class="h-4 w-4 shrink-0 rounded border-slate-300 accent-[#536dff] focus:ring-[#536dff]"
                :checked="false"
                @change="updateColumn(column.id)"
              />
              <span class="truncate">{{ column.label }}</span>
            </label>
          </div>
        </template>
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
