<script setup lang="ts">
import { Check, ChevronDown } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue';

export type FigmaSelectValue = string | number | null;
export interface FigmaSelectOption {
  value: FigmaSelectValue;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: FigmaSelectValue;
  options: readonly FigmaSelectOption[];
  label: string;
  accessibleLabel: string;
  testIdPrefix: string;
  triggerTestId?: string;
  inline?: boolean;
  disabled?: boolean;
}>(), {
  inline: false,
  disabled: false
});

const emit = defineEmits<{ 'update:modelValue': [value: FigmaSelectValue] }>();
const root = ref<HTMLElement | null>(null);
const open = ref(false);
const focusedIndex = ref(0);
const menuId = `figma-select-${useId().replace(/:/g, '')}`;

const selectedIndex = computed(() => props.options.findIndex(option => Object.is(option.value, props.modelValue)));
const selectedOption = computed(() => props.options[selectedIndex.value] ?? props.options[0]);
const selectedIsPlaceholder = computed(() => props.modelValue === '' || props.modelValue === null);

function optionTestId(option: FigmaSelectOption): string {
  const value = option.value === '' || option.value === null ? 'all' : String(option.value);
  return `${props.testIdPrefix}-option-${value}`;
}

function setOpen(next: boolean): void {
  if (props.disabled) return;
  open.value = next;
  if (next) focusedIndex.value = Math.max(0, selectedIndex.value);
}

function choose(option: FigmaSelectOption): void {
  if (option.disabled) return;
  emit('update:modelValue', option.value);
  open.value = false;
}

function moveFocus(direction: 1 | -1): void {
  if (!props.options.length) return;
  let next = focusedIndex.value;
  for (let count = 0; count < props.options.length; count += 1) {
    next = (next + direction + props.options.length) % props.options.length;
    if (!props.options[next]?.disabled) {
      focusedIndex.value = next;
      return;
    }
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (props.disabled) return;
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    if (!open.value) setOpen(true);
    else moveFocus(event.key === 'ArrowDown' ? 1 : -1);
    return;
  }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    if (!open.value) setOpen(true);
    else if (props.options[focusedIndex.value]) choose(props.options[focusedIndex.value]!);
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    open.value = false;
  } else if (event.key === 'Tab') {
    open.value = false;
  }
}

function handleOutside(event: PointerEvent): void {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}

onMounted(() => document.addEventListener('pointerdown', handleOutside));
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleOutside));
</script>

<template>
  <div ref="root" class="figma-select-field" :class="{ 'figma-select-field--inline': inline }">
    <span class="figma-select-label">{{ label }}</span>
    <button
      :data-testid="triggerTestId ?? `${testIdPrefix}-trigger`"
      type="button"
      class="figma-select-control"
      :class="{ 'figma-select-control--placeholder': selectedIsPlaceholder, 'figma-select-control--open': open }"
      role="combobox"
      aria-haspopup="listbox"
      :aria-label="accessibleLabel"
      :aria-controls="menuId"
      :aria-expanded="open"
      :disabled="disabled"
      @click="setOpen(!open)"
      @keydown="onKeydown"
    >
      <span class="figma-select-value">{{ selectedOption?.label ?? '' }}</span>
      <ChevronDown class="figma-select-chevron" aria-hidden="true" />
    </button>
    <div v-if="open" :id="menuId" :data-testid="`${testIdPrefix}-menu`" class="figma-select-menu" role="listbox" :aria-label="accessibleLabel">
      <button
        v-for="(option, index) in options"
        :id="`${menuId}-option-${index}`"
        :key="`${String(option.value)}-${index}`"
        :data-testid="optionTestId(option)"
        type="button"
        class="figma-select-option"
        :class="{ 'figma-select-option--focused': focusedIndex === index, 'figma-select-option--selected': Object.is(option.value, modelValue) }"
        role="option"
        :aria-selected="Object.is(option.value, modelValue)"
        :disabled="option.disabled"
        @mouseenter="!option.disabled && (focusedIndex = index)"
        @mousedown.prevent
        @click="choose(option)"
      >
        <span>{{ option.label }}</span>
        <Check v-if="Object.is(option.value, modelValue)" data-testid="figma-select-check" class="figma-select-check" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.figma-select-field { position: relative; display: flex; flex-direction: column; gap: 4px; height: 84px; color: #25314d; }
.figma-select-field--inline { min-width: 0; height: 40px; flex-direction: row; align-items: center; gap: 8px; }
.figma-select-field--inline .figma-select-label { flex: 0 0 auto; }
.figma-select-label { font-size: 12px; font-weight: 500; line-height: 18px; }
.figma-select-control { display: flex; align-items: center; gap: 8px; width: 100%; height: 40px; padding: 8px 12px; overflow: hidden; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: #25314d; font-size: 14px; font-weight: 400; line-height: 22px; text-align: left; outline: none; transition: border-color .16s ease, box-shadow .16s ease; }
.figma-select-field--inline .figma-select-control { width: auto; min-width: 0; flex: 1 1 auto; }
.figma-select-field--inline .figma-select-menu { top: 44px; min-width: 180px; }
.figma-select-control--placeholder { color: #94a3b8; }
.figma-select-control:hover:not(:disabled) { border-color: #cbd5e1; }
.figma-select-control:focus-visible, .figma-select-control--open { border-color: #536dff; box-shadow: 0 0 0 3px rgba(83,109,255,.08); }
.figma-select-control:disabled { cursor: not-allowed; background: #f8fafc; color: #94a3b8; }
.figma-select-value { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.figma-select-chevron, .figma-select-check { width: 20px; height: 20px; flex: 0 0 20px; }
.figma-select-chevron { color: #64748b; transition: transform .16s ease; }
.figma-select-control--open .figma-select-chevron { transform: rotate(180deg); }
.figma-select-menu { position: absolute; z-index: 40; top: 66px; left: 0; display: flex; flex-direction: column; width: 100%; max-height: 240px; padding: 8px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; box-shadow: 0 8px 10px rgba(18,26,41,.14); }
.figma-select-option { display: flex; align-items: center; justify-content: space-between; gap: 8px; width: 100%; height: 40px; flex: 0 0 40px; padding: 0 12px; border: 0; border-radius: 8px; background: #fff; color: #25314d; font-size: 14px; font-weight: 400; line-height: 22px; text-align: left; }
.figma-select-option--focused, .figma-select-option--selected { background: #f6f7fb; }
.figma-select-option:disabled { cursor: not-allowed; background: #f8fafc; color: #94a3b8; }
.figma-select-check { color: #64748b; }
</style>
