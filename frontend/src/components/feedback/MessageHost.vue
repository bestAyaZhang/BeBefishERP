<script setup lang="ts">
import { CheckCircle2, CircleAlert, Info, TriangleAlert, X } from 'lucide-vue-next';
import { messages, message, type MessageItem, type MessageType } from './message';

const variantClasses: Record<MessageType, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  error: 'border-rose-200 bg-rose-50 text-rose-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800'
};

function iconFor(item: MessageItem) {
  return item.type === 'success' ? CheckCircle2 : item.type === 'error' ? CircleAlert : item.type === 'warning' ? TriangleAlert : Info;
}
</script>

<template>
  <div data-testid="message-host" class="pointer-events-none fixed left-1/2 top-5 z-[100] flex w-[min(calc(100vw-2rem),520px)] -translate-x-1/2 flex-col items-center gap-3" aria-live="polite">
    <TransitionGroup name="message" tag="div" class="flex w-full flex-col items-center gap-3">
      <div v-for="item in messages" :key="item.id" :data-testid="`message-${item.id}`" role="alert" class="pointer-events-auto flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-sm font-semibold shadow-[0_12px_32px_rgba(15,23,42,0.14)]" :class="variantClasses[item.type]">
        <component :is="iconFor(item)" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="min-w-0 flex-1 break-words">{{ item.text }}</span>
        <button :data-testid="`message-close-${item.id}`" type="button" class="shrink-0 rounded p-0.5 opacity-70 transition hover:bg-black/5 hover:opacity-100" aria-label="关闭提示" @click="message.close(item.id)">
          <X class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.message-enter-active,
.message-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.message-enter-from,
.message-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
