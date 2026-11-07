<script setup lang="ts">
import { Download } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import ProductImageAlbum from '../editor/steps/ProductImageAlbum.vue';
import { albumImageUrl, productImageAlbums } from '../editor/steps/productImageAlbums';
import { formatText } from '../productDetailFormatting';
import { initialImagePacks } from '../productMedia';
import type { Product, ProductMediaImage, ProductSku } from '../types';
import { packImageCount, useProductImagePackDownload } from '../useProductImagePackDownload';
import ProductDetailImage from './ProductDetailImage.vue';
import ProductImageAlbumPreview, { type AlbumPreview } from './ProductImageAlbumPreview.vue';

const props = defineProps<{ product: Product; selectedSku: ProductSku | null }>();
const packs = computed(() => props.product.imagePacks ?? initialImagePacks(props.product).filter(packImageCount));
const materialImages = computed(() => props.product.materialImages ?? []);
const preview = ref<AlbumPreview | null>(null);
const { downloadPack, isDownloading, downloadError } = useProductImagePackDownload();

function showPreview(title: string, images: ProductMediaImage[], index: number, long = false) {
  preview.value = { title, images, index, long };
}

const hasPerSkuPackagingImages = computed(() => {
  const [first, ...rest] = props.product.skus;
  return !!first && rest.some((sku) =>
    sku.packageImageFileId !== first.packageImageFileId
    || sku.cartonImageFileId !== first.cartonImageFileId
    || (sku.productPhotoFileId ?? null) !== (first.productPhotoFileId ?? null)
  );
});
const packagingImageSkus = computed(() => {
  const skus = hasPerSkuPackagingImages.value ? props.product.skus : props.selectedSku ? [props.selectedSku] : [];
  return skus.filter((sku) => sku.productPhotoUrl || sku.cartonImageUrl || sku.packageImageUrl);
});
</script>

<template>
  <section id="product-images" data-testid="product-images-section" class="pd-panel pd-full-section">
    <div class="pd-panel-heading">
      <h2>图片资料 <span class="pd-heading-count">{{ packs.length }} 个素材包</span></h2>
      <span>点击缩略图查看大图</span>
    </div>

    <div class="detail-album-stack">
      <article
        v-for="(pack, index) in packs"
        :key="index"
        :data-testid="`detail-image-pack-${index}`"
        class="detail-image-pack"
      >
        <header class="detail-pack-heading">
          <div class="min-w-0">
            <h3>{{ pack.name }}</h3>
            <span>{{ packImageCount(pack) }} 张图片</span>
          </div>
          <button
            class="pd-button detail-pack-download"
            type="button"
            :aria-label="`打包下载${pack.name}`"
            :disabled="!packImageCount(pack) || isDownloading(pack)"
            :title="packImageCount(pack) ? '下载素材包 ZIP' : '素材包中暂无图片'"
            @click="downloadPack(pack)"
          ><Download aria-hidden="true" />{{ isDownloading(pack) ? '打包中…' : '打包下载' }}</button>
        </header>
        <p v-if="downloadError(pack)" class="detail-download-error" role="alert">{{ downloadError(pack) }}</p>
        <div class="detail-album-grid">
          <ProductImageAlbum
            v-for="{ kind, label } in productImageAlbums"
            :key="kind"
            :test-id="`detail-image-album-${index}-${kind}`"
            :label="label"
            :title="`${pack.name}的${label}`"
            :images="pack[kind]"
            :long="kind === 'long'"
            @preview="showPreview(`${pack.name} · ${label}`, pack[kind], $event, kind === 'long')"
          />
        </div>
      </article>
      <p v-if="!packs.length" class="detail-album-empty">暂无素材包</p>
      <ProductImageAlbum
        v-if="materialImages.length"
        test-id="detail-image-album-material"
        label="原始素材"
        title="原始素材"
        :images="materialImages"
        @preview="showPreview('原始素材', materialImages, $event)"
      />
    </div>

    <section v-if="packagingImageSkus.length" aria-label="尺寸包装配图">
      <div class="detail-packaging-heading">
        <h3>尺寸包装配图</h3>
        <span>{{ hasPerSkuPackagingImages ? '按 SKU 展示' : '随当前 SKU 切换' }}</span>
      </div>
      <div class="pd-media-grid">
        <template v-for="sku in packagingImageSkus" :key="sku.id">
          <figure v-if="sku.productPhotoUrl" class="pd-media-item">
            <ProductDetailImage
              :src="albumImageUrl(sku.productPhotoUrl)"
              :alt="`${sku.skuName || sku.skuCode || product.productName}产品实拍图`"
              :test-id="`packaging-product-photo-${sku.id}`"
              size="preview"
            />
            <figcaption><strong>产品实拍图</strong><span>{{ formatText(sku.skuCode) }}</span></figcaption>
          </figure>
          <figure v-if="sku.cartonImageUrl" class="pd-media-item">
            <ProductDetailImage
              :src="albumImageUrl(sku.cartonImageUrl)"
              :alt="`${sku.skuName || sku.skuCode || product.productName}外箱图片`"
              :test-id="`packaging-carton-image-${sku.id}`"
              size="preview"
            />
            <figcaption><strong>外箱图片</strong><span>{{ formatText(sku.skuCode) }}</span></figcaption>
          </figure>
          <figure v-if="sku.packageImageUrl" class="pd-media-item">
            <ProductDetailImage
              :src="albumImageUrl(sku.packageImageUrl)"
              :alt="`${sku.skuName || sku.skuCode || product.productName}内盒包装图片`"
              :test-id="`packaging-package-image-${sku.id}`"
              size="preview"
            />
            <figcaption><strong>内盒包装图</strong><span>{{ formatText(sku.skuCode) }}</span></figcaption>
          </figure>
        </template>
      </div>
    </section>
    <p class="pd-panel-footer">图片资料可在「编辑商品」中维护。</p>
    <ProductImageAlbumPreview :preview="preview" @close="preview = null" />
  </section>
</template>

<style scoped>
.detail-album-stack {
  display: grid;
  gap: 14px;
  margin: 0 19px 6px;
  container-type: inline-size;
}

.detail-image-pack,
.detail-album-empty {
  padding: 14px;
  border: 1px solid var(--pd-border);
  border-radius: 7px;
}

.detail-pack-heading,
.detail-packaging-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px 14px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.detail-pack-heading h3,
.detail-packaging-heading h3 {
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.detail-pack-heading span,
.detail-packaging-heading span,
.detail-album-empty {
  color: var(--pd-muted);
  font-size: 12px;
}

.detail-pack-download { height: 34px; color: var(--pd-accent); }
.detail-pack-download:disabled { opacity: .55; cursor: not-allowed; }
.detail-download-error { margin-bottom: 10px; color: #963d3d; overflow-wrap: anywhere; }
.detail-packaging-heading { padding: 12px 22px 0; }

.detail-album-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@container (max-width: 490px) {
  .detail-album-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
