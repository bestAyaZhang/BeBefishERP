<script setup lang="ts">
import { ImageOff } from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';

const props = withDefaults(defineProps<{
  src?: string | null;
  alt: string;
  testId: string;
  size?: 'small' | 'medium' | 'large' | 'preview';
}>(), {
  src: null,
  size: 'medium'
});

const failed = ref(false);
const previewOpen = ref(false);
const sizeClass = computed(() => ({
  small: 'h-10 w-10',
  medium: 'h-24 w-24',
  large: 'h-[196px] w-[220px] max-w-full',
  preview: 'h-[114px] w-full'
}[props.size]));

watch(() => props.src, () => {
  failed.value = false;
  previewOpen.value = false;
});

function handleError() {
  failed.value = true;
  previewOpen.value = false;
}
</script>

<template>
  <button
    v-if="src && !failed"
    :data-testid="`${testId}-preview-trigger`"
    type="button"
    class="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50 text-slate-300"
    :class="[sizeClass, 'cursor-zoom-in outline-none transition-shadow hover:border-[#c7d2fe] hover:shadow-sm focus-visible:ring-2 focus-visible:ring-[#536dff]/30']"
    :aria-label="`预览${alt}`"
    @click="previewOpen = true"
  >
    <img
      :data-testid="testId"
      :src="src"
      :alt="alt"
      class="h-full w-full object-contain"
      @error="handleError"
    />
  </button>
  <span
    v-else
    class="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50 text-slate-300"
    :class="sizeClass"
  >
    <span
      :data-testid="`${testId}-fallback`"
      :aria-label="`${alt}不可用`"
      class="inline-flex h-full w-full items-center justify-center"
    >
      <ImageOff class="h-6 w-6" aria-hidden="true" />
    </span>
  </span>

  <AccessibleDialog
    :open="previewOpen"
    :title="alt"
    description="图片预览"
    :test-id="`${testId}-preview-dialog`"
    :body-test-id="`${testId}-preview-body`"
    :footer-test-id="`${testId}-preview-footer`"
    :close-test-id="`${testId}-preview-close`"
    panel-class="w-[min(1120px,calc(100vw-80px))]"
    header-class="h-16 px-6"
    body-class="flex h-[min(720px,calc(100vh-144px))] items-center justify-center bg-[#f8fafc] p-6"
    footer-class="hidden"
    @cancel="previewOpen = false"
  >
    <img
      v-if="src"
      :data-testid="`${testId}-preview`"
      :src="src"
      :alt="`${alt}大图预览`"
      class="max-h-full max-w-full object-contain"
    />
  </AccessibleDialog>
</template>
