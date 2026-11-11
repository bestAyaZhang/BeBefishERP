<script setup lang="ts">
import { ImageOff, Plus } from 'lucide-vue-next';
import { onBeforeUnmount, reactive, ref } from 'vue';
import type { ProductMediaImage, ProductService } from '../../types';
import { albumImageName, albumImageUrl } from './productImageAlbums';
import { imagePreviewUrl } from '../../../../components/media/imageUrls';

const props = defineProps<{
  label: string;
  title: string;
  testId: string;
  images: ProductMediaImage[];
  service?: ProductService;
  long?: boolean;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  uploaded: [image: ProductMediaImage];
  uploading: [value: boolean];
  preview: [index: number];
  remove: [index: number];
}>();

const acceptedTypes = ['image/jpeg', 'image/png', 'image/webp'];
const maxImageSize = 5 * 1024 * 1024;
const uploading = ref(false);
const progress = ref(0);
const failures = ref<string[]>([]);
const dragDepth = ref(0);
const failedImages = reactive(new Set<string>());
let mounted = true;

function imageSource(image: ProductMediaImage) {
  const url = albumImageUrl(image.url);
  return url && !failedImages.has(url) ? url : undefined;
}

function markImageError(image: ProductMediaImage) {
  const url = albumImageUrl(image.url);
  if (url) failedImages.add(url);
}

function dragEnter(event: DragEvent) {
  if (props.service && !props.disabled && !uploading.value && event.dataTransfer?.types.includes('Files')) {
    dragDepth.value++;
  }
}

function drop(event: DragEvent) {
  dragDepth.value = 0;
  if (event.dataTransfer) void uploadImages(Array.from(event.dataTransfer.files));
}

function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  void uploadImages(files);
}

async function uploadImages(files: File[]) {
  const service = props.service;
  if (!service || props.disabled || uploading.value || !files.length) return;
  const album = props.images;
  failures.value = [];
  const candidates = files.filter((file) => {
    if (acceptedTypes.includes(file.type) && file.size > 0 && file.size <= maxImageSize) return true;
    failures.value.push(`${file.name}：请选择不超过 5 MB 的 JPG、PNG 或 WEBP 图片`);
    return false;
  });
  if (!candidates.length) return;

  progress.value = 0;
  uploading.value = true;
  emit('uploading', true);
  try {
    // Keep file picker/drop order and retain successful images when one fails.
    for (const [index, file] of candidates.entries()) {
      if (!mounted || props.images !== album) return;
      try {
        const uploaded = await service.uploadImage(file, (percentage) => {
          if (mounted && props.images === album && Number.isFinite(percentage)) {
            progress.value = Math.round((index * 100 + Math.min(100, Math.max(0, percentage))) / candidates.length);
          }
        });
        if (!mounted || props.images !== album) return;
        emit('uploaded', {
          fileId: uploaded.id,
          url: uploaded.url || null,
          name: uploaded.originalFileName || file.name
        });
      } catch (error) {
        if (!mounted || props.images !== album) return;
        const detail = error instanceof Error ? error.message : '图片上传失败';
        failures.value.push(`${file.name}：${detail}`);
      }
      progress.value = Math.round((index + 1) * 100 / candidates.length);
    }
  } finally {
    if (mounted) {
      uploading.value = false;
      emit('uploading', false);
    }
  }
}

onBeforeUnmount(() => {
  mounted = false;
  if (uploading.value) emit('uploading', false);
});
</script>

<template>
  <section
    class="image-album"
    :class="{ 'album-long': long, 'drag-over': dragDepth > 0, 'album-readonly': !service }"
    :aria-label="title"
    :aria-busy="uploading"
    :data-testid="testId"
    @dragenter.prevent="dragEnter"
    @dragleave="dragDepth = Math.max(0, dragDepth - 1)"
    @dragover.prevent
    @drop.prevent.stop="drop"
  >
    <div class="album-heading">
      <h4>{{ label }} <span class="album-count">{{ images.length }} 张</span></h4>
      <label v-if="service" class="album-add" :class="{ disabled: disabled || uploading }">
        <Plus aria-hidden="true" />
        <span>添加图片</span>
        <input
          :data-testid="`${testId}-input`"
          class="sr-only"
          type="file"
          :accept="acceptedTypes.join(',')"
          multiple
          :disabled="disabled || uploading"
          :aria-label="`为${title}添加图片`"
          @change="selectFiles"
        />
      </label>
    </div>

    <div v-if="uploading" class="album-progress">
      <span>上传中 {{ progress }}%</span>
      <progress
        role="progressbar"
        :aria-label="`${title}上传进度`"
        :aria-valuenow="progress"
        max="100"
        :value="progress"
      />
    </div>
    <ul v-if="failures.length" class="album-errors" role="alert">
      <li v-for="(failure, index) in failures" :key="index">{{ failure }}</li>
    </ul>

    <div v-if="images.length" class="album-tiles">
      <figure v-for="(image, index) in images" :key="`${image.fileId}-${index}`" class="album-tile">
        <button
          class="album-preview"
          type="button"
          :aria-label="`查看 ${albumImageName(image)}`"
          :disabled="!imageSource(image)"
          @click="emit('preview', index)"
        >
          <img v-if="imageSource(image)" :src="imagePreviewUrl(imageSource(image), 'thumb') ?? undefined" alt="" loading="lazy" decoding="async" width="160" height="160" @error="markImageError(image)" />
          <ImageOff v-else aria-label="图片无法显示" />
        </button>
        <figcaption class="album-name" :title="albumImageName(image)">{{ albumImageName(image) }}</figcaption>
        <button
          v-if="service"
          class="album-remove"
          type="button"
          :aria-label="`移除 ${albumImageName(image)}`"
          :disabled="disabled || uploading"
          @click="emit('remove', index)"
        >移除</button>
      </figure>
    </div>
    <p v-else-if="!uploading" class="album-empty">{{ service ? '拖入图片，或点击「添加图片」' : '暂无图片' }}</p>
  </section>
</template>

<style scoped>
.image-album {
  min-width: 0;
  padding: 11px;
  border: 1px solid #e1e9f2;
  border-radius: 6px;
  background: #f8fafd;
}

.image-album.drag-over {
  border-color: #74a6cf;
  background: #eef6fc;
}

.album-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.album-heading h4 {
  color: #233449;
  font-size: 13px;
  font-weight: 600;
}

.album-count,
.album-name,
.album-empty {
  color: #65758a;
  font-size: 11px;
}

.album-count {
  margin-inline-start: 4px;
  font-weight: 400;
  white-space: nowrap;
}

.album-add {
  display: inline-flex;
  min-height: 32px;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border: 1px solid #ceddec;
  border-radius: 5px;
  background: #fff;
  color: #0968b6;
  font-size: 12px;
  cursor: pointer;
}

.album-add svg {
  width: 13px;
  height: 13px;
}

.album-add.disabled,
.album-remove:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.album-add:not(.disabled):hover,
.album-preview:enabled:hover {
  border-color: #80b0d6;
}

.album-add:focus-within,
.album-preview:focus-visible,
.album-remove:focus-visible {
  outline: 2px solid #0968b6;
  outline-offset: 2px;
}

.album-empty {
  margin-top: 8px;
  padding: 6px 8px;
  border: 1px dashed #d6e1ec;
  border-radius: 5px;
  line-height: 1.6;
}

.album-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 7px;
  margin-top: 8px;
}

.album-tile {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 3px;
  min-width: 0;
}

.album-preview {
  display: grid;
  grid-column: 1 / -1;
  width: 100%;
  height: 72px;
  place-items: center;
  padding: 3px;
  overflow: hidden;
  border: 1px solid #dfe7f0;
  border-radius: 5px;
  background: #fff;
  color: #8292a6;
  cursor: zoom-in;
}

.album-preview:disabled {
  cursor: default;
}

.album-preview img {
  width: 100%;
  height: 64px;
  object-fit: contain;
}

.album-long .album-preview img {
  object-fit: cover;
  object-position: top center;
}

.album-readonly .album-tiles {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 96px), 1fr));
  gap: 10px;
}

.album-readonly .album-preview { height: 104px; }
.album-readonly .album-preview img { height: 96px; }

.album-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-remove {
  min-width: 30px;
  min-height: 28px;
  padding: 0 3px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #963d3d;
  font-size: 11px;
  white-space: nowrap;
}

.album-remove:enabled:hover {
  border-color: #efdbdb;
  background: #fff5f5;
}

.album-progress,
.album-errors {
  display: grid;
  gap: 4px;
  margin-top: 8px;
  font-size: 11px;
}

.album-progress {
  color: #0968b6;
}

.album-progress progress {
  width: 100%;
  height: 6px;
  accent-color: #0968b6;
}

.album-errors {
  color: #963d3d;
  overflow-wrap: anywhere;
}
</style>
