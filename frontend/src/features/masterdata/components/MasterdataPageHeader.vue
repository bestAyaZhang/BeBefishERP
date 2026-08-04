<script setup lang="ts">
import { Plus } from 'lucide-vue-next';

defineProps<{
  title: string;
  description: string;
  buttonLabel: string;
  buttonTestId: string;
}>();

defineEmits<{ create: [] }>();
</script>

<template>
  <header data-testid="masterdata-page-header" class="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
    <div class="min-w-0">
      <p class="text-xs font-black uppercase text-[#536dff]">Master data</p>
      <h1 class="mt-1 text-2xl font-black text-[#25314d]">{{ title }}</h1>
      <p class="mt-2 max-w-2xl text-sm font-medium text-slate-400">{{ description }}</p>
    </div>
    <button
      :data-testid="buttonTestId"
      type="button"
      class="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-lg shadow-blue-100 transition hover:bg-[#435be3]"
      @click="$emit('create')"
    >
      <Plus class="h-4 w-4" aria-hidden="true" />
      {{ buttonLabel }}
    </button>
  </header>
</template>
