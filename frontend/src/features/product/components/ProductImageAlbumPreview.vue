<script lang="ts">
import type { ProductMediaImage } from '../types';

export interface AlbumPreview {
  title: string;
  images: ProductMediaImage[];
  index: number;
  long?: boolean;
}
</script>

<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import { albumImageName, albumImageUrl } from '../editor/steps/productImageAlbums';

const props = defineProps<{ preview: AlbumPreview | null }>();
const emit = defineEmits<{ close: [] }>();

const index = ref(props.preview?.index ?? 0);
const canvas = ref<HTMLElement>();
const failed = ref(false);
const image = computed(() => props.preview?.images[index.value]);
const url = computed(() => image.value ? albumImageUrl(image.value.url) : null);

function move(offset: number) {
  const next = index.value + offset;
  if (props.preview && next >= 0 && next < props.preview.images.length) index.value = next;
}

watch(() => props.preview, (preview) => {
  index.value = preview?.index ?? 0;
});

watch([() => props.preview, index], () => {
  failed.value = false;
  if (canvas.value) canvas.value.scrollTop = 0;
});
</script>

<template>
  <Teleport to="body">
    <AccessibleDialog
      :open="image !== undefined"
      :title="preview?.title ?? ''"
      test-id="image-album-preview"
      body-test-id="image-album-preview-body"
      footer-test-id="image-album-preview-footer"
      close-test-id="image-album-preview-close"
      overlay-class="items-center justify-center p-4"
      panel-class="w-[min(900px,100%)]"
      header-class="px-5 py-3"
      body-class="px-4 py-3"
      footer-class="px-4 py-3"
      @cancel="emit('close')"
      @keydown.left.prevent="move(-1)"
      @keydown.right.prevent="move(1)"
    >
      <template v-if="preview && image">
        <p class="preview-caption" role="status">{{ albumImageName(image) }} · {{ index + 1 }} / {{ preview.images.length }}</p>
        <div
          ref="canvas"
          class="preview-canvas"
          :class="{ 'preview-long': preview.long }"
          :tabindex="preview.long ? 0 : undefined"
          :aria-label="preview.long ? '可滚动查看详情长图' : undefined"
        >
          <img
            v-if="url && !failed"
            :key="url"
            :src="url"
            :alt="albumImageName(image)"
            @error="failed = true"
          />
          <p v-else class="preview-error">图片无法显示</p>
        </div>
      </template>
      <template #footer>
        <button
          class="preview-button"
          type="button"
          aria-label="上一张"
          :aria-disabled="index === 0"
          @click="move(-1)"
        ><ChevronLeft aria-hidden="true" />上一张</button>
        <button
          class="preview-button"
          type="button"
          aria-label="下一张"
          :aria-disabled="!preview || index >= preview.images.length - 1"
          @click="move(1)"
        >下一张<ChevronRight aria-hidden="true" /></button>
      </template>
    </AccessibleDialog>
  </Teleport>
</template>

<style scoped>
.preview-caption,
.preview-error {
  color: #53657a;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.preview-caption { margin-bottom: 8px; }

.preview-canvas {
  max-height: calc(100dvh - 180px);
  overflow: auto;
  overscroll-behavior: contain;
  padding: 10px;
  border-radius: 6px;
  background: #f8fafd;
}

.preview-canvas img {
  display: block;
  max-width: 100%;
  max-height: calc(100dvh - 200px);
  height: auto;
  margin-inline: auto;
  object-fit: contain;
}

.preview-long img { max-height: none; }

.preview-button {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid #dfe5ed;
  border-radius: 6px;
  background: #fff;
  color: #53657a;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.preview-button svg { width: 15px; height: 15px; }

.preview-button:not([aria-disabled="true"]):hover {
  border-color: #9ebed9;
  color: #0968b6;
}

.preview-button:focus-visible,
.preview-canvas:focus-visible {
  outline: 2px solid #0968b6;
  outline-offset: 2px;
}

.preview-button[aria-disabled="true"] {
  opacity: .55;
  cursor: not-allowed;
}
</style>
