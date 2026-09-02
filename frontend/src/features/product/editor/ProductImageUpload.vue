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
  variant?: 'default' | 'wizard-main' | 'wizard-tile' | 'dialog-sku' | 'dialog-packaging';
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
  <div
    v-if="variant === 'wizard-main' || variant === 'wizard-tile' || variant === 'dialog-sku'"
    class="group relative grid min-w-0 place-items-center overflow-hidden rounded-lg border border-dashed border-[#b8c9e3] bg-[#fbfdff]"
    :class="variant === 'wizard-main' ? 'h-[244px] w-[260px]' : variant === 'dialog-sku' ? 'h-[248px] w-[200px]' : 'h-[168px] w-full'"
  >
    <img
      v-if="previewUrl"
      :data-testid="`${testId}-preview`"
      :src="previewUrl"
      :alt="`${label}预览`"
      class="absolute inset-3 h-[calc(100%-24px)] w-[calc(100%-24px)] rounded-md object-contain"
      @error="markPreviewError"
    />
    <label :for="inputId" class="relative z-10 flex cursor-pointer flex-col items-center text-center" :class="previewUrl ? 'absolute inset-0 justify-center bg-white/85 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100' : ''">
      <span class="grid h-12 w-12 place-items-center rounded-lg bg-[#eef2ff] text-[#536dff]">
        <Upload class="h-6 w-6" aria-hidden="true" />
      </span>
      <strong class="mt-2 text-sm font-medium leading-5 text-[#25314d]">{{ previewUrl ? `替换${label}` : variant === 'wizard-main' ? '点击上传商品主图' : variant === 'dialog-sku' ? '上传 SKU 图片' : '点击或拖拽上传' }}</strong>
      <small class="mt-2 text-xs font-normal leading-[18px] text-[#8292ae]">{{ variant === 'dialog-sku' ? 'PNG / JPG，建议 1:1' : `JPG / PNG，${variant === 'wizard-main' ? '单张' : ''}不超过 5 MB` }}</small>
    </label>
    <input :id="inputId" :data-testid="testId" class="sr-only" type="file" accept="image/*" @change="upload" />
    <button v-if="previewUrl" :data-testid="`${testId}-remove`" type="button" class="absolute right-3 top-3 z-20 inline-flex h-8 items-center gap-1 rounded-lg border border-[#dbe4f1] bg-white px-2 text-xs font-medium text-[#ef476f] shadow-sm" :aria-label="`删除${label}`" @click="remove">
      <Trash2 class="h-4 w-4" aria-hidden="true" />
      删除
    </button>
    <span :data-testid="`${testId}-status`" class="sr-only">{{ statusLabel() }}</span>
    <p v-if="error" class="absolute bottom-2 left-3 right-3 z-20 truncate text-center text-xs font-medium text-[#ef476f]">{{ error }}</p>
  </div>

  <div v-else-if="variant === 'dialog-packaging'" class="flex h-[168px] w-full items-center gap-4 rounded-lg border border-[#dbe4f1] bg-[#f8faff] p-4">
    <img v-if="previewUrl" :data-testid="`${testId}-preview`" :src="previewUrl" :alt="`${label}预览`" class="h-[104px] w-[104px] rounded-lg bg-[#eef2ff] object-contain" @error="markPreviewError" />
    <span v-else class="grid h-[104px] w-[104px] shrink-0 place-items-center rounded-lg bg-[#eef2ff] text-xl font-medium text-[#536dff]">{{ label.includes('外箱') ? 'BOX' : 'IN' }}</span>
    <div class="min-w-0 flex-1">
      <p class="text-sm font-medium text-[#25314d]">{{ label }}</p>
      <p :data-testid="`${testId}-status`" class="mt-2 text-xs font-medium" :class="status === 'error' ? 'text-[#ef476f]' : status === 'uploaded' ? 'text-[#16a36a]' : 'text-[#8292ae]'">{{ statusLabel() }}</p>
      <p class="mt-2 text-xs text-[#8292ae]">PNG / JPG，建议 1:1</p>
      <div class="mt-2 flex items-center gap-2">
        <input :id="inputId" :data-testid="testId" class="sr-only" type="file" accept="image/*" @change="upload" />
        <label :for="inputId" class="inline-flex h-8 cursor-pointer items-center rounded-lg border border-[#dbe4f1] bg-white px-3 text-xs font-medium text-[#536dff]">{{ previewUrl ? '更换图片' : '上传图片' }}</label>
        <button v-if="previewUrl" :data-testid="`${testId}-remove`" type="button" class="text-xs font-medium text-[#ef476f]" :aria-label="`删除${label}`" @click="remove">删除</button>
      </div>
      <p v-if="error" class="mt-1 truncate text-xs text-[#ef476f]">{{ error }}</p>
    </div>
  </div>

  <div v-else class="min-w-0 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3">
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
