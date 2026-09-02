<script setup lang="ts">
import { X } from 'lucide-vue-next';
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';

const props = withDefaults(defineProps<{
  open: boolean;
  title: string;
  description?: string;
  testId: string;
  bodyTestId: string;
  footerTestId: string;
  closeTestId: string;
  overlayClass?: string;
  panelClass?: string;
  headerClass?: string;
  bodyClass?: string;
  footerClass?: string;
}>(), {
  description: '',
  overlayClass: 'items-center justify-center p-6',
  panelClass: 'w-[min(960px,calc(100vw-48px))]',
  headerClass: 'px-6 py-4',
  bodyClass: 'max-h-[calc(100vh-180px)] overflow-y-auto px-6 py-5 pb-8',
  footerClass: 'px-6 py-4'
});

const emit = defineEmits<{ cancel: [] }>();
const panel = ref<HTMLElement>();
const titleId = `dialog-title-${Math.random().toString(36).slice(2)}`;
let opener: HTMLElement | null = null;

const focusableSelector = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',');

function focusableElements() {
  return panel.value
    ? [...panel.value.querySelectorAll<HTMLElement>(focusableSelector)].filter((element) => !element.hidden)
    : [];
}

function focusFirst() {
  const preferred = panel.value?.querySelector<HTMLElement>('[data-dialog-initial-focus]');
  (preferred ?? focusableElements()[0] ?? panel.value)?.focus();
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('cancel');
    return;
  }
  if (event.key !== 'Tab') return;
  const focusable = focusableElements();
  if (focusable.length === 0) {
    event.preventDefault();
    panel.value?.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function containFocus(event: FocusEvent) {
  if (!props.open || panel.value?.contains(event.target as Node)) return;
  focusFirst();
}

function activate() {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  document.addEventListener('focusin', containFocus, true);
  void nextTick(focusFirst);
}

function deactivate(restoreFocus: boolean) {
  document.removeEventListener('focusin', containFocus, true);
  const focusTarget = opener;
  if (restoreFocus && focusTarget?.isConnected) void nextTick(() => focusTarget.focus());
  opener = null;
}

watch(() => props.open, (open, wasOpen) => {
  if (open) activate();
  else if (wasOpen) deactivate(true);
}, { immediate: true });

onBeforeUnmount(() => deactivate(true));
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex bg-slate-950/35" :class="overlayClass" @mousedown.self="emit('cancel')">
    <section
      ref="panel"
      :data-testid="testId"
      tabindex="-1"
      class="max-w-none overflow-hidden rounded-lg border border-slate-200 bg-white shadow-2xl"
      :class="panelClass"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      @keydown="handleKeydown"
    >
      <header class="flex items-center justify-between border-b border-slate-200" :class="headerClass">
        <div class="min-w-0">
          <h2 :id="titleId" class="truncate text-lg font-black text-[#25314d]">{{ title }}</h2>
          <p v-if="description" class="mt-1 text-sm font-medium text-slate-500">{{ description }}</p>
        </div>
        <button
          :data-testid="closeTestId"
          data-dialog-initial-focus
          type="button"
          class="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-slate-400 outline-none hover:bg-slate-100 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-[#536dff]/30"
          :aria-label="`关闭${title}`"
          @click="emit('cancel')"
        >
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </header>

      <div :data-testid="bodyTestId" :class="bodyClass">
        <slot />
      </div>

      <footer :data-testid="footerTestId" class="sticky bottom-0 z-10 flex justify-end gap-3 border-t border-slate-200 bg-white" :class="footerClass">
        <slot name="footer" />
      </footer>
    </section>
  </div>
</template>
