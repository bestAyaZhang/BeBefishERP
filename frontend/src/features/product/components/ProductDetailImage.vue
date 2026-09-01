<script setup lang="ts">
import { ImageOff } from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';

const props = withDefaults(defineProps<{
  src?: string | null;
  alt: string;
  testId: string;
  size?: 'small' | 'medium' | 'large';
}>(), {
  src: null,
  size: 'medium'
});

const failed = ref(false);
const sizeClass = computed(() => ({
  small: 'h-14 w-14',
  medium: 'h-24 w-24',
  large: 'h-36 w-36 sm:h-40 sm:w-40'
}[props.size]));

watch(() => props.src, () => {
  failed.value = false;
});
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50 text-slate-300"
    :class="sizeClass"
  >
    <img
      v-if="src && !failed"
      :data-testid="testId"
      :src="src"
      :alt="alt"
      class="h-full w-full object-contain"
      @error="failed = true"
    />
    <span
      v-else
      :data-testid="`${testId}-fallback`"
      :aria-label="`${alt}不可用`"
      class="inline-flex h-full w-full items-center justify-center"
    >
      <ImageOff class="h-6 w-6" aria-hidden="true" />
    </span>
  </span>
</template>
