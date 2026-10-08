<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Check, ChevronDown, X } from 'lucide-vue-next';
import type { PreparerOption } from './types';

const props = withDefaults(defineProps<{ modelValue: string[]; employeeIds?: number[]; options: PreparerOption[]; disabled?: boolean }>(), { disabled: false });
const emit = defineEmits<{ 'update:modelValue': [value: string[]]; 'update:employeeIds': [value: number[]] }>();
const root = ref<HTMLElement | null>(null);
const open = ref(false);
const selected = computed(() => new Set(props.modelValue));
const selections = computed(() => {
  if (props.employeeIds === undefined) return props.modelValue.map(name => ({ key: name, name, id: undefined as number | undefined, label: name }));
  const accounts = props.employeeIds.map(id => {
    const option = props.options.find(o => o.employeeId === id);
    const name = option?.employeeName ?? '';
    return { key: `employee:${id}`, id: id as number | undefined, name,
      label: name ? `${name}${duplicateName(name) ? ` #${id}` : ''}` : `不可用账号 #${id}` };
  });
  const legacyNames = props.modelValue.filter(name => !accounts.some(account => account.name === name));
  return [...accounts, ...legacyNames.map(name => ({ key: `legacy:${name}`, name, id: undefined as number | undefined, label: name }))];
});
function isSelected(option: PreparerOption) { return props.employeeIds === undefined ? selected.value.has(option.employeeName) : props.employeeIds.includes(option.employeeId); }
function duplicateName(name: string) { return props.options.filter(option => option.employeeName === name).length > 1; }
function toggle(option: PreparerOption) {
  if (props.disabled) return;
  if (props.employeeIds === undefined) { toggleName(option.employeeName); return; }
  const next = new Set(props.employeeIds);
  if (next.has(option.employeeId)) next.delete(option.employeeId); else next.add(option.employeeId);
  const previouslyLinkedNames = props.options.filter(o => props.employeeIds?.includes(o.employeeId)).map(o => o.employeeName);
  const legacyNames = props.modelValue.filter(name => name !== option.employeeName && !previouslyLinkedNames.includes(name));
  const names = props.options.filter(o => next.has(o.employeeId)).map(o => o.employeeName);
  emit('update:employeeIds', [...next]);
  const nextNames = new Set([...legacyNames, ...names]);
  emit('update:modelValue', [...props.modelValue.filter(name => nextNames.has(name)), ...names.filter(name => !props.modelValue.includes(name))]);
}

function toggleName(name: string) {
  const next = new Set(props.modelValue);
  if (next.has(name)) next.delete(name); else next.add(name);
  emit('update:modelValue', [...next]);
}
function remove(name: string) {
  if (props.disabled) return;
  if (props.employeeIds !== undefined) emit('update:employeeIds', props.employeeIds.filter(id => !props.options.some(o => o.employeeId === id && o.employeeName === name)));
  emit('update:modelValue', props.modelValue.filter(value => value !== name));
}
function removeAccount(id: number) {
  if (props.disabled) return;
  const nextIds = props.employeeIds?.filter(value => value !== id) ?? [];
  const name = props.options.find(o => o.employeeId === id)?.employeeName;
  emit('update:employeeIds', nextIds);
  if (name && !props.options.some(o => nextIds.includes(o.employeeId) && o.employeeName === name))
    emit('update:modelValue', props.modelValue.filter(value => value !== name));
}
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
      <span v-if="!selections.length" class="text-slate-400">选择备货人（可多选）</span>
      <span v-else class="flex flex-wrap gap-1.5">
        <span v-for="selection in selections" :key="selection.key" class="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-1 text-xs text-indigo-700">
          {{ selection.label }}<span role="button" tabindex="0" :data-testid="selection.id == null ? undefined : `preparer-remove-${selection.id}`" :aria-label="`移除${selection.label}`" @click.stop="selection.id == null ? remove(selection.name) : removeAccount(selection.id)" @keydown.enter.stop="selection.id == null ? remove(selection.name) : removeAccount(selection.id)"><X :size="12" /></span>
        </span>
      </span>
      <ChevronDown :size="16" class="shrink-0 text-slate-400" />
    </button>
    <div v-if="open" role="listbox" aria-multiselectable="true" class="absolute z-30 mt-2 max-h-60 w-full overflow-auto rounded-lg border border-slate-200 bg-white p-1.5 shadow-xl">
      <button v-for="option in options" :key="option.employeeId" type="button" role="option" :aria-selected="isSelected(option)" :disabled="disabled"
        :data-testid="`preparer-option-${option.employeeId}`" class="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm hover:bg-slate-50"
        @click="toggle(option)">
        <span>{{ option.employeeName }}<span v-if="duplicateName(option.employeeName)" class="ml-2 text-xs text-slate-400">员工 #{{ option.employeeId }}</span></span><Check v-if="isSelected(option)" :size="16" class="text-[#536dff]" />
      </button>
      <p v-if="!options.length" class="px-3 py-4 text-center text-xs text-slate-400">暂无可选在职员工</p>
    </div>
  </div>
</template>
