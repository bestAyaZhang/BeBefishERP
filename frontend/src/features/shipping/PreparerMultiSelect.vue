<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Check, ChevronDown, X } from 'lucide-vue-next';
import type { PreparerOption } from './types';

const props = withDefaults(defineProps<{ modelValue: string[]; options: PreparerOption[]; disabled?: boolean }>(), { disabled: false });
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();
const root = ref<HTMLElement | null>(null);
const open = ref(false);
const selected = computed(() => new Set(props.modelValue));

function toggleName(name: string) {
  const next = new Set(props.modelValue);
  if (next.has(name)) next.delete(name); else next.add(name);
  emit('update:modelValue', [...next]);
}
function remove(name: string) { emit('update:modelValue', props.modelValue.filter(value => value !== name)); }
function onPointerDown(event: PointerEvent) { if (!root.value?.contains(event.target as Node)) open.value = false; }
function onKeyDown(event: KeyboardEvent) { if (event.key === 'Escape') open.value = false; }
onMounted(() => { document.addEventListener('pointerdown', onPointerDown); document.addEventListener('keydown', onKeyDown); });
onBeforeUnmount(() => { document.removeEventListener('pointerdown', onPointerDown); document.removeEventListener('keydown', onKeyDown); });
</script>

<template>
  <div ref="root" class="relative">
    <button type="button" data-testid="preparer-toggle" :disabled="disabled" :aria-expanded="open" aria-haspopup="listbox"
      class="flex min-h-11 w-full items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-sm outline-none focus:border-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50"
      @click="open = !open">
      <span v-if="!modelValue.length" class="text-slate-400">选择备货人（可多选）</span>
      <span v-else class="flex flex-wrap gap-1.5">
        <span v-for="name in modelValue" :key="name" class="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-1 text-xs text-indigo-700">
          {{ name }}<span role="button" tabindex="0" :aria-label="`移除${name}`" @click.stop="remove(name)" @keydown.enter.stop="remove(name)"><X :size="12" /></span>
        </span>
      </span>
      <ChevronDown :size="16" class="shrink-0 text-slate-400" />
    </button>
    <div v-if="open" role="listbox" aria-multiselectable="true" class="absolute z-30 mt-2 max-h-60 w-full overflow-auto rounded-lg border border-slate-200 bg-white p-1.5 shadow-xl">
      <button v-for="option in options" :key="option.employeeId" type="button" role="option" :aria-selected="selected.has(option.employeeName)"
        :data-testid="`preparer-option-${option.employeeId}`" class="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm hover:bg-slate-50"
        @click="toggleName(option.employeeName)">
        <span>{{ option.employeeName }}</span><Check v-if="selected.has(option.employeeName)" :size="16" class="text-[#536dff]" />
      </button>
      <p v-if="!options.length" class="px-3 py-4 text-center text-xs text-slate-400">暂无可选在职员工</p>
    </div>
  </div>
</template>
