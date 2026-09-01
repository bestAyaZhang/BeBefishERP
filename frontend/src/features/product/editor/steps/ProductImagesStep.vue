<script setup lang="ts">
import type { ProductService } from '../../types';
import ProductImageUpload from '../ProductImageUpload.vue';
import { applyUnifiedPackaging, type ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{ service: ProductService }>();

function ensurePreviewSlots() {
  while (state.value.imagePreviews.sku.length < state.value.skus.length) state.value.imagePreviews.sku.push('');
  while (state.value.imagePreviews.package.length < state.value.skus.length) state.value.imagePreviews.package.push('');
  while (state.value.imagePreviews.carton.length < state.value.skus.length) state.value.imagePreviews.carton.push('');
}

function setUnifiedImage(field: 'packageImageFileId' | 'cartonImageFileId', fileId: number | null) {
  state.value.unifiedPackaging[field] = fileId;
  state.value.skus = applyUnifiedPackaging(state.value.skus, state.value.unifiedPackaging);
}

function setUnifiedPreview(field: 'package' | 'carton', preview: string) {
  ensurePreviewSlots();
  state.value.imagePreviews[field] = state.value.skus.map(() => preview);
}

ensurePreviewSlots();
</script>

<template>
  <div class="space-y-7">
    <section>
      <h3 class="text-sm font-black text-[#25314d]">商品主图</h3>
      <div class="mt-3 max-w-md">
        <ProductImageUpload
          v-model:file-id="state.mainImageFileId"
          v-model:preview="state.imagePreviews.main"
          :service="service"
          label="商品主图"
          test-id="product-main-image"
        />
      </div>
    </section>

    <section class="border-t border-slate-200 pt-6">
      <h3 class="text-sm font-black text-[#25314d]">SKU 图片</h3>
      <div class="mt-3 grid gap-4 lg:grid-cols-2">
        <div v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" class="min-w-0">
          <p class="mb-2 truncate text-xs font-bold text-slate-500">{{ sku.skuName || sku.skuCode }}</p>
          <ProductImageUpload
            v-model:file-id="sku.skuImageFileId"
            v-model:preview="state.imagePreviews.sku[index]"
            :service="service"
            :label="`${sku.skuName || `SKU ${index + 1}`} 图片`"
            :test-id="`sku-image-${index}`"
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
          @update:file-id="setUnifiedImage('packageImageFileId', $event)"
          @update:preview="setUnifiedPreview('package', $event)"
        />
        <ProductImageUpload
          :file-id="state.unifiedPackaging.cartonImageFileId"
          :preview="state.imagePreviews.carton[0] ?? ''"
          :service="service"
          label="外箱图片"
          test-id="carton-image-unified"
          @update:file-id="setUnifiedImage('cartonImageFileId', $event)"
          @update:preview="setUnifiedPreview('carton', $event)"
        />
      </div>
      <div v-else class="mt-4 divide-y divide-slate-200 border-y border-slate-200">
        <div v-for="(sku, index) in state.skus" :key="sku.id ?? `package-${index}`" class="py-5">
          <p class="mb-3 text-sm font-black text-[#25314d]">{{ sku.skuName || sku.skuCode }}</p>
          <div class="grid gap-4 lg:grid-cols-2">
            <ProductImageUpload
              v-model:file-id="sku.packageImageFileId"
              v-model:preview="state.imagePreviews.package[index]"
              :service="service"
              label="内包装图片"
              :test-id="`package-image-${index}`"
            />
            <ProductImageUpload
              v-model:file-id="sku.cartonImageFileId"
              v-model:preview="state.imagePreviews.carton[index]"
              :service="service"
              label="外箱图片"
              :test-id="`carton-image-${index}`"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
