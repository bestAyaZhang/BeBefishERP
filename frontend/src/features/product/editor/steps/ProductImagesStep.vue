<script setup lang="ts">
import { Plus } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, reactive, ref, toRaw, useId } from 'vue';
import AccessibleDialog from '../../../../components/AccessibleDialog.vue';
import ProductImageAlbumPreview, { type AlbumPreview } from '../../components/ProductImageAlbumPreview.vue';
import type { ProductAlbumKind, ProductImagePack, ProductMediaImage, ProductService } from '../../types';
import { packImageCount, useProductImagePackDownload } from '../../useProductImagePackDownload';
import { setEditorImageFileId, setEditorImagePreview, type ProductEditorState } from '../productEditorState';
import ProductImageAlbum from './ProductImageAlbum.vue';
import { albumImageUrl, productImageAlbums } from './productImageAlbums';

type PackDialog = { action: 'add' } | { action: 'rename' | 'delete'; pack: ProductImagePack };

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{
  service: ProductService;
  disabled?: boolean;
  canonicalMainUploading?: boolean;
}>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();

const packKeys = new WeakMap<ProductImagePack, number>();
let lastPackKey = 0;
const activeUploads = reactive(new Set<string>());
const { downloadPack, isDownloading, downloadError, cancelDownload } = useProductImagePackDownload();
const addButton = ref<HTMLButtonElement>();
const packDialog = ref<PackDialog | null>(null);
const packName = ref('');
const packNameError = ref('');
const formId = useId();
const nameFieldId = useId();
const preview = ref<AlbumPreview | null>(null);
const packDialogTitle = computed(() => {
  if (packDialog.value?.action === 'delete') return '删除资料包';
  if (packDialog.value?.action === 'rename') return '重命名资料包';
  return '新增资料包';
});
const packDialogDescription = computed(() => {
  const dialog = packDialog.value;
  if (dialog?.action === 'delete') {
    return `「${dialog.pack.name}」包含 ${packImageCount(dialog.pack)} 张图片。`;
  }
  return '名称可用于区分渠道、版本或拍摄方案。';
});

function packKey(pack: ProductImagePack) {
  const raw = toRaw(pack);
  const existingKey = packKeys.get(raw);
  if (existingKey !== undefined) return existingKey;
  const key = ++lastPackKey;
  packKeys.set(raw, key);
  return key;
}

function packIsUploading(pack: ProductImagePack) {
  return productImageAlbums.some(({ kind }) => activeUploads.has(`${packKey(pack)}:${kind}`));
}

function setUploading(key: string, uploading: boolean) {
  const wasUploading = activeUploads.size > 0;
  if (uploading) activeUploads.add(key);
  else activeUploads.delete(key);
  if (wasUploading !== (activeUploads.size > 0)) emit('update:uploading', !wasUploading);
}

function openPackDialog(dialog: PackDialog) {
  packDialog.value = dialog;
  packName.value = dialog.action === 'add' ? '' : dialog.pack.name;
  packNameError.value = '';
}

function savePackName() {
  const dialog = packDialog.value;
  if (!dialog || dialog.action === 'delete' || props.disabled) return;
  const name = packName.value.trim();
  if (!name || name.length > 60) {
    packNameError.value = '请输入 1 到 60 个字符的资料包名称';
    return;
  }
  if (dialog.action === 'rename') dialog.pack.name = name;
  else state.value.imagePacks.push({ name, main: [], sku: [], detail: [], long: [] });
  packDialog.value = null;
}

async function deletePack() {
  const dialog = packDialog.value;
  if (dialog?.action !== 'delete' || props.disabled || packIsUploading(dialog.pack)) return;
  const index = state.value.imagePacks.indexOf(dialog.pack);
  if (index < 0) return;
  state.value.imagePacks.splice(index, 1);
  cancelDownload(dialog.pack);
  packDialog.value = null;
  await nextTick();
  addButton.value?.focus();
}

function addPackImage(pack: ProductImagePack, kind: ProductAlbumKind, image: ProductMediaImage) {
  const firstMainImage = kind === 'main' && pack.main.length === 0;
  pack[kind].push(image);
  if (
    firstMainImage
    && state.value.imagePacks[0] === pack
    && state.value.mainImageFileId === null
    && !state.value.imagePreviews.main
    && !props.canonicalMainUploading
  ) {
    setEditorImageFileId(state.value, 'main', null, image.fileId);
    setEditorImagePreview(state.value, 'main', null, albumImageUrl(image.url) ?? '');
  }
}

function showPreview(title: string, images: ProductMediaImage[], index: number, long = false) {
  preview.value = { title, images, index, long };
}

onBeforeUnmount(() => {
  if (activeUploads.size) emit('update:uploading', false);
  activeUploads.clear();
});
</script>

<template>
  <div class="asset-workspace">
    <div class="asset-pack-toolbar">
      <button
        ref="addButton"
        data-testid="add-image-pack"
        class="pack-button"
        type="button"
        :disabled="disabled"
        @click="openPackDialog({ action: 'add' })"
      >
        <Plus aria-hidden="true" />
        新增资料包
      </button>
    </div>

    <article
      v-for="(pack, packIndex) in state.imagePacks"
      :key="packKey(pack)"
      :data-testid="`image-pack-${packIndex}`"
      class="asset-pack"
    >
      <header class="asset-pack-heading">
        <div class="asset-pack-title">
          <h3>{{ pack.name }}</h3>
          <span>{{ packImageCount(pack) }} 张图片</span>
        </div>
        <div class="asset-pack-actions">
          <button
            class="pack-action"
            type="button"
            :aria-label="`打包下载${pack.name}`"
            :disabled="!packImageCount(pack) || isDownloading(pack) || packIsUploading(pack)"
            :title="packImageCount(pack) ? '下载这个资料包的 ZIP 压缩包' : '添加图片后可打包下载'"
            @click="!packIsUploading(pack) && downloadPack(pack)"
          >{{ isDownloading(pack) ? '打包中…' : '打包下载' }}</button>
          <button
            class="pack-action"
            type="button"
            :aria-label="`重命名${pack.name}`"
            :disabled="disabled"
            @click="openPackDialog({ action: 'rename', pack })"
          >改名</button>
          <button
            class="pack-action pack-action-danger"
            type="button"
            :aria-label="`删除${pack.name}`"
            :disabled="disabled || packIsUploading(pack)"
            @click="openPackDialog({ action: 'delete', pack })"
          >删除</button>
        </div>
      </header>

      <p v-if="downloadError(pack)" class="pack-error" role="alert">{{ downloadError(pack) }}</p>
      <div class="album-grid">
        <ProductImageAlbum
          v-for="{ kind, label } in productImageAlbums"
          :key="kind"
          :test-id="`image-album-${packIndex}-${kind}`"
          :label="label"
          :title="`${pack.name}的${label}`"
          :images="pack[kind]"
          :service="service"
          :long="kind === 'long'"
          :disabled="disabled"
          @uploading="setUploading(`${packKey(pack)}:${kind}`, $event)"
          @uploaded="addPackImage(pack, kind, $event)"
          @preview="showPreview(`${pack.name} · ${label}`, pack[kind], $event, kind === 'long')"
          @remove="pack[kind].splice($event, 1)"
        />
      </div>
    </article>
    <div v-if="!state.imagePacks.length" class="asset-pack-empty">
      <strong>暂无资料包</strong>
      <p>新增资料包，开始整理商品相册。</p>
    </div>

    <article class="asset-pack">
      <ProductImageAlbum
        test-id="image-album-material"
        label="原始素材"
        title="原始素材"
        :images="state.materialImages"
        :service="service"
        :disabled="disabled"
        @uploading="setUploading('material', $event)"
        @uploaded="state.materialImages.push($event)"
        @preview="showPreview('原始素材', state.materialImages, $event)"
        @remove="state.materialImages.splice($event, 1)"
      />
    </article>
  </div>

  <Teleport to="body">
    <AccessibleDialog
      :open="packDialog !== null"
      :title="packDialogTitle"
      :description="packDialogDescription"
      test-id="image-pack-dialog"
      body-test-id="image-pack-dialog-body"
      footer-test-id="image-pack-dialog-footer"
      close-test-id="image-pack-dialog-close"
      overlay-class="items-center justify-center p-4"
      panel-class="w-[min(480px,100%)]"
      header-class="px-5 py-4"
      body-class="px-5 py-4"
      footer-class="px-5 py-4"
      @cancel="packDialog = null"
    >
      <p v-if="packDialog?.action === 'delete'" class="pack-dialog-copy">删除后，这个资料包及其相册将从图片资料移除。</p>
      <form v-else :id="formId" data-testid="image-pack-form" class="pack-name-form" @submit.prevent="savePackName">
        <label :for="nameFieldId">资料包名称</label>
        <input
          :id="nameFieldId"
          v-model="packName"
          data-testid="image-pack-name"
          maxlength="60"
          placeholder="例如：1688 上架图"
          :aria-invalid="Boolean(packNameError)"
          :aria-describedby="packNameError ? nameFieldId + '-error' : undefined"
          @input="packNameError = ''"
        />
        <p v-if="packNameError" :id="nameFieldId + '-error'" class="pack-error" role="alert">{{ packNameError }}</p>
      </form>
      <template #footer>
        <button class="pack-button" type="button" @click="packDialog = null">取消</button>
        <button
          v-if="packDialog?.action === 'delete'"
          data-testid="confirm-delete-image-pack"
          class="pack-button pack-button-danger"
          type="button"
          :disabled="disabled"
          @click="deletePack"
        >确认删除</button>
        <button v-else class="pack-button pack-button-primary" type="submit" :form="formId" :disabled="disabled">
          {{ packDialog?.action === 'rename' ? '保存名称' : '创建资料包' }}
        </button>
      </template>
    </AccessibleDialog>

  </Teleport>
  <ProductImageAlbumPreview :preview="preview" @close="preview = null" />
</template>

<style scoped>
.asset-workspace {
  display: grid;
  gap: 14px;
  min-width: 0;
  container: product-albums / inline-size;
}

.asset-pack-toolbar {
  display: flex;
  justify-content: flex-end;
}

.asset-pack {
  min-width: 0;
  padding: 16px;
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  background: #fff;
}

.asset-pack-heading,
.asset-pack-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.asset-pack-heading {
  justify-content: space-between;
  margin-bottom: 13px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf0f4;
}

.asset-pack-title {
  min-width: 0;
  color: #65758a;
  font-size: 11px;
}

.asset-pack-title h3 {
  color: #233449;
  font-size: 15px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.asset-pack-actions {
  flex: 0 0 auto;
  gap: 4px;
}

.pack-button,
.pack-action {
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

.pack-button svg {
  width: 15px;
  height: 15px;
}

.pack-button:enabled:not(.pack-button-primary, .pack-button-danger):hover {
  border-color: #9ebed9;
  color: #0968b6;
}

.pack-button-primary {
  border-color: #0968b6;
  background: #0968b6;
  color: #fff;
}

.pack-button-primary:enabled:hover {
  background: #075a9e;
}

.pack-button-danger {
  border-color: #a53939;
  background: #a53939;
  color: #fff;
}

.pack-button-danger:enabled:hover {
  background: #8c2c2c;
}

.pack-action {
  min-width: 44px;
  padding: 0 8px;
  border-color: transparent;
  color: #0968b6;
}

.pack-action:enabled:hover {
  background: #eef6fc;
}

.pack-action-danger {
  color: #963d3d;
}

.pack-action-danger:enabled:hover {
  background: #fff4f4;
}

.pack-button:focus-visible,
.pack-action:focus-visible {
  outline: 2px solid #0968b6;
  outline-offset: 2px;
}

.pack-button:disabled,
.pack-action:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.asset-pack-empty {
  padding: 12px 14px;
  border: 1px dashed #d6e1ec;
  border-radius: 8px;
  background: #f8fafd;
  color: #65758a;
  font-size: 12px;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 12px;
}

.pack-name-form {
  display: grid;
  gap: 7px;
  color: #44566d;
  font-size: 13px;
}

.pack-name-form input {
  width: 100%;
  height: 40px;
  padding: 0 11px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
}

.pack-name-form input:focus-visible {
  border-color: #0968b6;
  outline: 2px solid #99c8ee;
}

.pack-error {
  margin-bottom: 8px;
  color: #963d3d;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.pack-dialog-copy {
  color: #53657a;
  font-size: 13px;
  overflow-wrap: anywhere;
}

@container product-albums (max-width: 480px) {
  .asset-pack {
    padding: 12px;
  }

  .album-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@container product-albums (max-width: 330px) {
  .asset-pack-heading {
    flex-wrap: wrap;
  }
}
</style>
