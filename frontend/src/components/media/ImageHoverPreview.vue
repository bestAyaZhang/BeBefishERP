<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

const props = withDefaults(defineProps<{
  src?: string | null;
  alt: string;
  imageClass?: string;
  testId?: string;
  clickable?: boolean;
}>(), {
  src: null,
  imageClass: 'h-full w-full object-cover',
  testId: undefined,
  clickable: false
});

const emit = defineEmits<{ error: [event: Event]; click: [] }>();

const visible = ref(false);
const thumbnailElement = ref<HTMLElement | null>(null);
const previewTop = ref(12);
const previewLeft = ref(12);
const PREVIEW_SIZE = 320;
const VIEWPORT_GAP = 12;

function updatePosition(target: EventTarget | null) {
  const element = target instanceof HTMLElement ? target : thumbnailElement.value;
  if (!element) return;

  const rect = element.getBoundingClientRect();
  const viewportWidth = window.innerWidth || PREVIEW_SIZE + VIEWPORT_GAP * 2;
  const viewportHeight = window.innerHeight || PREVIEW_SIZE + VIEWPORT_GAP * 2;
  const preferredLeft = rect.right + VIEWPORT_GAP;
  const preferredTop = rect.top + rect.height / 2 - PREVIEW_SIZE / 2;

  previewLeft.value = Math.max(
    VIEWPORT_GAP,
    Math.min(preferredLeft, viewportWidth - PREVIEW_SIZE - VIEWPORT_GAP)
  );
  previewTop.value = Math.max(
    VIEWPORT_GAP,
    Math.min(preferredTop, viewportHeight - PREVIEW_SIZE - VIEWPORT_GAP)
  );
}

function showPreview(event: MouseEvent | FocusEvent) {
  if (!props.src) return;
  thumbnailElement.value = event.currentTarget instanceof HTMLElement ? event.currentTarget : thumbnailElement.value;
  updatePosition(thumbnailElement.value);
  visible.value = true;
}

function hidePreview() {
  visible.value = false;
}

function handleImageError(event: Event) {
  emit('error', event);
}

function handleClick() {
  if (!props.clickable || !props.src) return;
  hidePreview();
  emit('click');
}

function handleViewportChange() {
  if (!visible.value) return;
  updatePosition(thumbnailElement.value);
}

onMounted(() => {
  window.addEventListener('resize', handleViewportChange);
  window.addEventListener('scroll', handleViewportChange, true);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportChange);
  window.removeEventListener('scroll', handleViewportChange, true);
});
</script>

<template>
  <span class="inline-flex min-w-0" :class="{ 'cursor-zoom-in': clickable }" @mouseenter="showPreview" @mouseleave="hidePreview" @focusin="showPreview" @focusout="hidePreview" @click="handleClick">
    <img
      v-if="src"
      :data-testid="testId"
      :src="src"
      :alt="alt"
      :class="imageClass"
      @mouseenter="showPreview"
      @mouseleave="hidePreview"
      @focus="showPreview"
      @blur="hidePreview"
      @error="handleImageError"
    />
    <slot v-else />
  </span>

  <Teleport to="body">
    <div
      v-if="visible && src"
      data-testid="image-hover-preview"
      role="tooltip"
      class="pointer-events-none fixed z-[100] flex h-[320px] w-[320px] items-center justify-center rounded-2xl border border-slate-200 bg-white p-3 shadow-[0_18px_50px_rgba(31,45,74,0.22)]"
      :style="{ top: `${previewTop}px`, left: `${previewLeft}px` }"
    >
      <img :src="src" :alt="alt" class="max-h-full max-w-full rounded-xl object-contain" />
    </div>
  </Teleport>
</template>
