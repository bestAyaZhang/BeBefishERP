<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { CheckCircle2 } from 'lucide-vue-next';

const props = defineProps<{ open: boolean; trackingNo: string; orderNo: string }>();
const emit = defineEmits<{ 'back-list': []; continue: [] }>();
const panel = ref<HTMLElement | null>(null);
const firstAction = ref<HTMLButtonElement | null>(null);
let opener: HTMLElement | null = null;

function focusable() { return panel.value ? [...panel.value.querySelectorAll<HTMLElement>('button:not([disabled])')] : []; }
function containFocus(event: FocusEvent) { if (props.open && !panel.value?.contains(event.target as Node)) firstAction.value?.focus(); }
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); return; }
  if (event.key !== 'Tab') return;
  const items = focusable();
  if (!items.length) return;
  if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus(); }
  else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0].focus(); }
}
watch(() => props.open, open => {
  if (open) {
    opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    document.addEventListener('focusin', containFocus, true);
    void nextTick(() => firstAction.value?.focus());
  } else {
    document.removeEventListener('focusin', containFocus, true);
  }
}, { immediate: true });
onBeforeUnmount(() => { document.removeEventListener('focusin', containFocus, true); if (opener?.isConnected) opener.focus(); });
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-5">
    <section ref="panel" role="dialog" aria-modal="true" aria-labelledby="shipping-success-title" tabindex="-1"
      class="w-[min(520px,calc(100vw-40px))] rounded-2xl border border-slate-200 bg-white p-7 text-center shadow-2xl" @keydown="onKeydown">
      <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 text-emerald-600"><CheckCircle2 :size="34" /></span>
      <h2 id="shipping-success-title" class="mt-5 text-xl font-bold text-[#25314d]">下单成功</h2>
      <p class="mt-2 text-sm text-slate-500">安能物流单号已生成，请按此单号安排后续发货。</p>
      <div class="mt-5 rounded-xl bg-slate-50 px-5 py-4"><p class="text-xs text-slate-400">物流单号</p><p class="mt-1 break-all text-lg font-bold tracking-wide text-[#25314d]">{{ trackingNo }}</p><p class="mt-2 break-all text-xs text-slate-400">安能订单号：{{ orderNo }}</p></div>
      <div class="mt-6 grid gap-3 sm:grid-cols-2">
        <button ref="firstAction" data-testid="success-back-list" type="button" class="rounded-lg border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700" @click="emit('back-list')">返回发货列表</button>
        <button data-testid="success-continue" type="button" class="rounded-lg bg-[#536dff] px-4 py-3 text-sm font-semibold text-white" @click="emit('continue')">继续下单</button>
      </div>
    </section>
  </div>
</template>
