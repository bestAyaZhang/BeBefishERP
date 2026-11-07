<script setup lang="ts">
import { ImageOff } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { formatMoney, formatProductType, formatText, formatWithUnit } from '../productDetailFormatting';
import { formatProductNumber, getDefaultSupplierQuote } from '../productPresentation';
import type { Product, ProductSku } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ product: Product; selectedSku: ProductSku | null }>();
defineEmits<{ 'select-sku': [id: number] }>();
const failedThumbnails = ref<Record<number, string | null>>({});
const defaultQuote = computed(() => getDefaultSupplierQuote(props.selectedSku ?? undefined));
const specification = computed(() => props.selectedSku?.specText || props.selectedSku?.specificationValues.join(' / '));
const lowStock = computed(() => Number.isFinite(props.product.totalStock)
  && Number.isFinite(props.product.totalSafetyStock)
  && props.product.totalStock < props.product.totalSafetyStock);
</script>

<template>
  <section id="product-overview" data-testid="product-overview-section" class="pd-overview pd-panel">
    <div class="pd-gallery">
      <div class="pd-gallery-heading"><h2>商品图片</h2><span>点击查看大图</span></div>
      <div class="pd-hero-image">
        <ProductDetailImage
          :src="selectedSku?.skuImageUrl || product.mainImageUrl"
          :alt="`${product.productName}${selectedSku ? ` · ${selectedSku.skuName || selectedSku.skuCode}` : ''}商品主图`"
          test-id="product-overview-main-image"
          size="large"
        />
      </div>
      <div v-if="product.skus.length" class="pd-thumbnails" aria-label="选择商品规格">
        <button
          v-for="sku in product.skus" :key="sku.id" type="button" class="pd-thumbnail"
          :data-testid="`sku-thumbnail-${sku.id}`" :aria-pressed="selectedSku?.id === sku.id"
          :aria-label="`选择 ${sku.skuName || sku.skuCode}`" :title="sku.specText || sku.skuName || sku.skuCode"
          @click="$emit('select-sku', sku.id)"
        >
          <img v-if="sku.skuImageUrl && failedThumbnails[sku.id] !== sku.skuImageUrl" :src="sku.skuImageUrl" :alt="sku.skuName || sku.skuCode" @error="failedThumbnails[sku.id] = sku.skuImageUrl" />
          <ImageOff v-else aria-hidden="true" />
        </button>
      </div>
      <p v-else class="pd-gallery-empty">暂无 SKU 图片</p>
    </div>

    <div class="pd-overview-info">
      <div class="pd-panel-heading"><h2>商品档案</h2><span>{{ formatProductType(product.productType) }} · {{ formatText(product.productCode) }}</span></div>
      <dl class="pd-overview-fields">
        <div><dt>所属分类</dt><dd>{{ formatText(product.categoryName) }}</dd></div>
        <div><dt>品牌</dt><dd data-testid="product-brand">{{ formatText(product.brand) }}</dd></div>
        <div><dt>销售单位</dt><dd>{{ formatText(selectedSku?.salesUnit) }}</dd></div>
        <div><dt>当前 SKU 供应商</dt><dd>{{ formatText(defaultQuote?.supplierName) }}</dd></div>
        <div><dt>当前规格 / 容量</dt><dd>{{ formatText(specification) }} · {{ formatWithUnit(selectedSku?.capacityMl, 'ml') }}<small data-testid="selected-sku-code">{{ formatText(selectedSku?.skuCode) }}</small></dd></div>
        <div><dt>SKU 数量</dt><dd>{{ product.skus.length }} 个规格</dd></div>
      </dl>
      <div class="pd-metrics">
        <div class="pd-metric"><span>当前规格售价</span><strong data-testid="selected-sku-sale-price">{{ formatMoney(selectedSku?.defaultSalePrice ?? product.defaultSalePrice) }}<small v-if="selectedSku?.salesUnit">/ {{ selectedSku.salesUnit }}</small></strong><p>销售单位价格</p></div>
        <div class="pd-metric"><span>默认采购价</span><strong data-testid="selected-sku-purchase-price">{{ formatMoney(defaultQuote?.purchasePrice) }}<small v-if="defaultQuote && selectedSku?.salesUnit">/ {{ selectedSku.salesUnit }}</small></strong><p>标准成本 {{ formatMoney(selectedSku?.standardCost) }}<template v-if="defaultQuote"> · 起订 {{ formatProductNumber(defaultQuote.minPurchaseQuantity) }}</template></p></div>
        <div class="pd-metric" :class="{ 'pd-stock-attention': lowStock }"><span>商品库存合计</span><strong data-testid="product-total-stock">{{ formatProductNumber(product.totalStock) }}</strong><p>安全库存 {{ formatProductNumber(product.totalSafetyStock) }}<template v-if="lowStock"> · 低于安全库存</template></p></div>
      </div>
    </div>
  </section>
</template>
