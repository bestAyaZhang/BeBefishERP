<script setup lang="ts">
import { ImageOff, Trash2, Upload } from 'lucide-vue-next';
import { onBeforeUnmount, ref, watch } from 'vue';
import { message } from '../../../components/feedback/message';
import type { ProductService } from '../types';

type UploadStatus = 'idle' | 'uploading' | 'uploaded' | 'error';

const props = defineProps<{
  service: ProductService;
  fileId: number | null;
  preview: string;
  label: string;
  testId: string;
}>();

const emit = defineEmits<{
  'update:fileId': [value: number | null];
  'update:preview': [value: string];
  'update:uploading': [value: boolean];
}>();

const inputId = `image-upload-${Math.random().toString(36).slice(2)}`;
const previewUrl = ref(props.preview);
const committedPreview = ref(props.preview);
const status = ref<UploadStatus>(props.fileId !== null || Boolean(props.preview) ? 'uploaded' : 'idle');
const error = ref('');
const ownedBlobUrls = new Set<string>();
let requestGeneration = 0;
let mounted = true;
let uploading = false;

watch(() => props.preview, (value) => {
  if (value === previewUrl.value) return;
  invalidatePendingRequest();
  revokeOwned(previewUrl.value);
  previewUrl.value = value;
  committedPreview.value = value;
  status.value = value ? 'uploaded' : 'idle';
  error.value = '';
});

function revokeOwned(url: string) {
  if (!ownedBlobUrls.has(url)) return;
  ownedBlobUrls.delete(url);
  URL.revokeObjectURL?.(url);
}

function setUploading(value: boolean) {
  if (uploading === value) return;
  uploading = value;
  emit('update:uploading', value);
}

function invalidatePendingRequest() {
  requestGeneration += 1;
  setUploading(false);
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  invalidatePendingRequest();
  revokeOwned(previewUrl.value);
  const generation = requestGeneration;
  const localPreview = URL.createObjectURL(file);
  ownedBlobUrls.add(localPreview);
  previewUrl.value = localPreview;
  status.value = 'uploading';
  error.value = '';
  setUploading(true);
  try {
    const uploaded = await props.service.uploadImage(file);
    if (!mounted || generation !== requestGeneration) return;
    emit('update:fileId', uploaded.id);
    const nextPreview = uploaded.url || localPreview;
    if (uploaded.url) revokeOwned(localPreview);
    previewUrl.value = nextPreview;
    committedPreview.value = nextPreview;
    emit('update:preview', nextPreview);
    status.value = 'uploaded';
  } catch (cause) {
    if (!mounted || generation !== requestGeneration) return;
    revokeOwned(localPreview);
    previewUrl.value = committedPreview.value;
    status.value = 'error';
    error.value = cause instanceof Error ? cause.message : '图片上传失败';
    message.error(error.value);
  } finally {
    if (mounted && generation === requestGeneration) setUploading(false);
    if (generation !== requestGeneration) revokeOwned(localPreview);
    input.value = '';
  }
}

function remove() {
  invalidatePendingRequest();
  revokeOwned(previewUrl.value);
  previewUrl.value = '';
  committedPreview.value = '';
  status.value = 'idle';
  error.value = '';
  emit('update:fileId', null);
  emit('update:preview', '');
}

function markPreviewError() {
  status.value = 'error';
  error.value = '图片无法显示，请重新上传';
}

function statusLabel() {
  if (status.value === 'uploading') return '上传中...';
  if (status.value === 'uploaded') return '上传成功';
  if (status.value === 'error') return '上传失败';
  return '未上传';
}

onBeforeUnmount(() => {
  mounted = false;
  invalidatePendingRequest();
  for (const url of [...ownedBlobUrls]) revokeOwned(url);
});
</script>

<template>
  <div class="min-w-0 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3">
    <div class="flex min-h-20 items-center gap-3">
      <img
        v-if="previewUrl"
        :data-testid="`${testId}-preview`"
        :src="previewUrl"
        :alt="`${label}预览`"
        class="h-16 w-16 shrink-0 rounded-lg border border-slate-200 bg-white object-cover"
        @error="markPreviewError"
      />
      <span v-else class="grid h-16 w-16 shrink-0 place-items-center rounded-lg border border-slate-200 bg-white text-slate-400">
        <ImageOff class="h-5 w-5" aria-hidden="true" />
      </span>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-black text-[#25314d]">{{ label }}</p>
        <p :data-testid="`${testId}-status`" class="mt-1 text-xs font-bold" :class="status === 'error' ? 'text-rose-600' : status === 'uploaded' ? 'text-emerald-600' : 'text-slate-500'">{{ statusLabel() }}</p>
        <p v-if="error" class="mt-1 break-words text-xs font-semibold text-rose-600">{{ error }}</p>
      </div>
    </div>
    <div class="mt-3 flex items-center gap-2">
      <input :id="inputId" :data-testid="testId" class="sr-only" type="file" accept="image/*" @change="upload" />
      <label :for="inputId" class="inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-xs font-bold text-slate-600 hover:border-[#536dff] hover:text-[#536dff]">
        <Upload class="h-4 w-4" aria-hidden="true" />
        选择图片
      </label>
      <button v-if="previewUrl" :data-testid="`${testId}-remove`" type="button" class="grid h-9 w-9 place-items-center rounded-lg text-rose-600 hover:bg-rose-50" :aria-label="`删除${label}`" @click="remove">
        <Trash2 class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>
