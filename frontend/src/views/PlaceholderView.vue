<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<template>
  <section class="flex min-h-[420px] flex-col justify-center border border-dashed border-slate-300 bg-white px-8 text-center">
    <h1 class="text-xl font-semibold text-slate-900">{{ title }}</h1>
    <p class="mt-2 text-sm text-slate-500">模块正在接入业务数据。</p>
  </section>
</template>
