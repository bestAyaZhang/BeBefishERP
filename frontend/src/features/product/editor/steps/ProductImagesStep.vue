<script setup lang="ts">
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
  <div class="space-y-7">
    <section>
      <h3 class="text-sm font-black text-[#25314d]">商品主图</h3>
      <div class="mt-3 max-w-md">
        <ProductImageUpload
          :file-id="state.mainImageFileId"
          :preview="state.imagePreviews.main"
          :service="service"
          label="商品主图"
          test-id="product-main-image"
          @update:file-id="setImageFileId('main', null, $event)"
          @update:preview="setImagePreview('main', null, $event)"
          @update:uploading="setUploading('main', $event)"
        />
      </div>
    </section>

    <section class="border-t border-slate-200 pt-6">
      <h3 class="text-sm font-black text-[#25314d]">SKU 图片</h3>
      <div class="mt-3 grid gap-4 lg:grid-cols-2">
        <div v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" class="min-w-0">
          <p class="mb-2 truncate text-xs font-bold text-slate-500">{{ sku.skuName || sku.skuCode }}</p>
          <ProductImageUpload
            :file-id="sku.skuImageFileId"
            :preview="state.imagePreviews.sku[index] ?? ''"
            :service="service"
            :label="`${sku.skuName || `SKU ${index + 1}`} 图片`"
            :test-id="`sku-image-${index}`"
            @update:file-id="setImageFileId('sku', index, $event)"
            @update:preview="setImagePreview('sku', index, $event)"
            @update:uploading="setUploading(`sku-${index}`, $event)"
          />
        </div>
      </div>
    </section>

    <section class="border-t border-slate-200 pt-6">
      <div>
        <h3 class="text-sm font-black text-[#25314d]">包装图片</h3>
        <p class="mt-1 text-xs font-medium text-slate-500">{{ state.packagingMode === 'unified' ? '当前统一应用到全部 SKU。' : '当前按 SKU 分别维护。' }}</p>
      </div>
      <div v-if="state.packagingMode === 'unified'" class="mt-3 grid gap-4 lg:grid-cols-2">
        <ProductImageUpload
          :file-id="state.unifiedPackaging.packageImageFileId"
          :preview="state.imagePreviews.package[0] ?? ''"
          :service="service"
          label="内包装图片"
          test-id="package-image-unified"
          @update:file-id="setImageFileId('package', null, $event)"
          @update:preview="setImagePreview('package', null, $event)"
          @update:uploading="setUploading('package-unified', $event)"
        />
        <ProductImageUpload
          :file-id="state.unifiedPackaging.cartonImageFileId"
          :preview="state.imagePreviews.carton[0] ?? ''"
          :service="service"
          label="外箱图片"
          test-id="carton-image-unified"
          @update:file-id="setImageFileId('carton', null, $event)"
          @update:preview="setImagePreview('carton', null, $event)"
          @update:uploading="setUploading('carton-unified', $event)"
        />
      </div>
      <div v-else class="mt-4 divide-y divide-slate-200 border-y border-slate-200">
        <div v-for="(sku, index) in state.skus" :key="sku.id ?? `package-${index}`" class="py-5">
          <p class="mb-3 text-sm font-black text-[#25314d]">{{ sku.skuName || sku.skuCode }}</p>
          <div class="grid gap-4 lg:grid-cols-2">
            <ProductImageUpload
              :file-id="sku.packageImageFileId"
              :preview="state.imagePreviews.package[index] ?? ''"
              :service="service"
              label="内包装图片"
              :test-id="`package-image-${index}`"
              @update:file-id="setImageFileId('package', index, $event)"
              @update:preview="setImagePreview('package', index, $event)"
              @update:uploading="setUploading(`package-${index}`, $event)"
            />
            <ProductImageUpload
              :file-id="sku.cartonImageFileId"
              :preview="state.imagePreviews.carton[index] ?? ''"
              :service="service"
              label="外箱图片"
              :test-id="`carton-image-${index}`"
              @update:file-id="setImageFileId('carton', index, $event)"
              @update:preview="setImagePreview('carton', index, $event)"
              @update:uploading="setUploading(`carton-${index}`, $event)"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
