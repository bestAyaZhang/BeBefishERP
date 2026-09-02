<script setup lang="ts">
import { computed } from 'vue';
import type { ProductService } from '../../types';
import ProductImageUpload from '../ProductImageUpload.vue';
import {
  setEditorImageFileId,
  setEditorImagePreview,
  type ProductEditorImageKind,
  type ProductEditorState
} from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{ service: ProductService }>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();
const activeUploads = new Set<string>();
const primarySkuIndex = computed(() => {
  const index = state.value.skus.findIndex((sku) => sku.defaultSku);
  return index >= 0 ? index : 0;
});
const primarySku = computed(() => state.value.skus[primarySkuIndex.value]);
const uploadedCount = computed(() => [
  state.value.mainImageFileId,
  state.value.packagingMode === 'unified' ? state.value.unifiedPackaging.cartonImageFileId : primarySku.value?.cartonImageFileId,
  state.value.packagingMode === 'unified' ? state.value.unifiedPackaging.packageImageFileId : primarySku.value?.packageImageFileId
].filter((value) => value !== null && value !== undefined).length);

function setUploading(key: string, value: boolean) {
  if (value) activeUploads.add(key);
  else activeUploads.delete(key);
  emit('update:uploading', activeUploads.size > 0);
}

function setImageFileId(kind: ProductEditorImageKind, index: number | null, fileId: number | null) {
  setEditorImageFileId(state.value, kind, index, fileId);
}

function setImagePreview(kind: ProductEditorImageKind, index: number | null, preview: string) {
  setEditorImagePreview(state.value, kind, index, preview);
}
</script>

<template>
  <div class="h-[490px]">
    <span class="absolute right-6 top-9 inline-flex h-7 items-center rounded-lg bg-[#f1f4ff] px-3 text-xs font-medium text-[#536dff]">{{ uploadedCount }} / 3 已上传</span>

    <div class="grid grid-cols-3 gap-4">
      <section>
        <p class="mb-2 text-xs font-medium text-[#25314d]">商品主图 <em class="not-italic text-[#ef476f]">*</em></p>
        <ProductImageUpload
          :file-id="state.mainImageFileId"
          :preview="state.imagePreviews.main"
          :service="service"
          label="商品主图"
          test-id="product-main-image"
          variant="wizard-tile"
          @update:file-id="setImageFileId('main', null, $event)"
          @update:preview="setImagePreview('main', null, $event)"
          @update:uploading="setUploading('main', $event)"
        />
      </section>
      <section>
        <p class="mb-2 text-xs font-medium text-[#25314d]">外箱图片</p>
        <ProductImageUpload
          :file-id="state.packagingMode === 'unified' ? state.unifiedPackaging.cartonImageFileId : primarySku?.cartonImageFileId ?? null"
          :preview="state.imagePreviews.carton[primarySkuIndex] ?? ''"
          :service="service"
          label="外箱图片"
          test-id="carton-image-unified"
          variant="wizard-tile"
          @update:file-id="setImageFileId('carton', state.packagingMode === 'unified' ? null : primarySkuIndex, $event)"
          @update:preview="setImagePreview('carton', state.packagingMode === 'unified' ? null : primarySkuIndex, $event)"
          @update:uploading="setUploading('carton', $event)"
        />
      </section>
      <section>
        <p class="mb-2 text-xs font-medium text-[#25314d]">内盒包装图</p>
        <ProductImageUpload
          :file-id="state.packagingMode === 'unified' ? state.unifiedPackaging.packageImageFileId : primarySku?.packageImageFileId ?? null"
          :preview="state.imagePreviews.package[primarySkuIndex] ?? ''"
          :service="service"
          label="内盒包装图"
          test-id="package-image-unified"
          variant="wizard-tile"
          @update:file-id="setImageFileId('package', state.packagingMode === 'unified' ? null : primarySkuIndex, $event)"
          @update:preview="setImagePreview('package', state.packagingMode === 'unified' ? null : primarySkuIndex, $event)"
          @update:uploading="setUploading('package', $event)"
        />
      </section>
    </div>

    <div class="sr-only" aria-hidden="true">
      <ProductImageUpload
        v-for="(sku, index) in state.skus"
        :key="sku.id ?? `sku-image-${index}`"
        :file-id="sku.skuImageFileId"
        :preview="state.imagePreviews.sku[index] ?? ''"
        :service="service"
        :label="`${sku.skuName || `SKU ${index + 1}`} 图片`"
        :test-id="`sku-image-${index}`"
        @update:file-id="setImageFileId('sku', index, $event)"
        @update:preview="setImagePreview('sku', index, $event)"
        @update:uploading="setUploading(`sku-${index}`, $event)"
      />
      <template v-if="state.packagingMode === 'perSku'">
        <template v-for="(sku, index) in state.skus" :key="sku.id ?? `packaging-images-${index}`">
          <ProductImageUpload
            :file-id="sku.packageImageFileId"
            :preview="state.imagePreviews.package[index] ?? ''"
            :service="service"
            :label="`${sku.skuName || `SKU ${index + 1}`} 内盒包装图`"
            :test-id="`package-image-${index}`"
            @update:file-id="setImageFileId('package', index, $event)"
            @update:preview="setImagePreview('package', index, $event)"
            @update:uploading="setUploading(`package-${index}`, $event)"
          />
          <ProductImageUpload
            :file-id="sku.cartonImageFileId"
            :preview="state.imagePreviews.carton[index] ?? ''"
            :service="service"
            :label="`${sku.skuName || `SKU ${index + 1}`} 外箱图片`"
            :test-id="`carton-image-${index}`"
            @update:file-id="setImageFileId('carton', index, $event)"
            @update:preview="setImagePreview('carton', index, $event)"
            @update:uploading="setUploading(`carton-${index}`, $event)"
          />
        </template>
      </template>
    </div>
  </div>
</template>
