<script setup lang="ts">
import { X } from 'lucide-vue-next';

defineProps<{
  title: string;
  description?: string;
}>();

defineEmits<{
  close: [];
  submit: [];
}>();
</script>

<template>
  <div class="fixed inset-0 z-50">
    <button class="absolute inset-0 bg-slate-950/25 backdrop-blur-[1px]" type="button" aria-label="关闭录入抽屉" @click="$emit('close')"></button>

    <aside data-testid="masterdata-drawer" class="fixed inset-y-0 right-0 flex w-[min(100vw,520px)] flex-col border-l border-slate-200 bg-white shadow-[-24px_0_70px_rgba(31,45,74,0.18)]">
      <div class="flex items-start justify-between border-b border-slate-100 px-6 py-5">
        <div class="min-w-0">
          <p class="text-[11px] font-black uppercase tracking-[0.18em] text-[#536dff]">Master data</p>
          <h2 class="mt-1 text-xl font-black text-[#25314d]">{{ title }}</h2>
          <p v-if="description" class="mt-1 text-sm font-medium leading-6 text-slate-400">{{ description }}</p>
        </div>
        <button data-testid="close-masterdata-drawer" type="button" class="ml-4 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]" aria-label="关闭" @click="$emit('close')">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <form class="flex min-h-0 flex-1 flex-col" @submit.prevent="$emit('submit')">
        <div class="min-h-0 flex-1 overflow-y-auto px-6 py-6">
          <slot />
        </div>
        <footer class="flex justify-end gap-3 border-t border-slate-100 bg-white px-6 py-4">
          <slot name="actions" />
        </footer>
      </form>
    </aside>
  </div>
</template>
