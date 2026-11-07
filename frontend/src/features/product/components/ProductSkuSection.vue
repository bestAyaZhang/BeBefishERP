<script setup lang="ts">
import { Copy } from 'lucide-vue-next';
import { computed } from 'vue';
import { message } from '../../../components/feedback/message';
import { formatDimensions, formatMoney, formatProductDimensions, formatRecordStatus, formatSkuParameters, formatText, formatWithUnit } from '../productDetailFormatting';
import { formatProductNumber, getDefaultSupplierQuote } from '../productPresentation';
import type { ProductSku } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';
import ProductPackagingSection from './ProductPackagingSection.vue';

const props = defineProps<{ skus: ProductSku[]; selectedSkuId: number | null }>();
defineEmits<{ 'select-sku': [id: number] }>();
const currentSku = computed(() => props.skus.find((sku) => sku.id === props.selectedSkuId));

async function copySkuParameters(sku: ProductSku) {
  try {
    await navigator.clipboard.writeText(formatSkuParameters(sku));
    message.success(`已复制 ${formatText(sku.skuCode)} 的参数`);
  } catch {
    message.error('复制 SKU 参数失败，请检查剪贴板权限');
  }
}
</script>

<template>
  <section id="product-skus" data-testid="product-sku-section" class="pd-panel pd-sku-panel">
    <div class="pd-panel-heading"><h2>SKU 明细 <span class="pd-heading-count">{{ skus.length }}</span></h2><span>按 SKU 查看规格与包装信息</span></div>
    <div v-if="skus.length" class="pd-sku-table-wrap">
      <table class="pd-sku-table" role="table" aria-label="SKU 明细">
        <colgroup>
          <col class="pd-sku-identity-col" />
          <col class="pd-sku-dimension-col" />
          <col class="pd-sku-dimension-col" />
          <col class="pd-sku-carton-quantity-col" />
          <col class="pd-sku-volume-col" />
          <col class="pd-sku-gross-weight-col" />
          <col /><col /><col /><col />
          <col class="pd-sku-status-col" />
        </colgroup>
        <thead role="rowgroup">
          <tr role="row">
            <th scope="col" role="columnheader">SKU货号 / 规格</th>
            <th scope="col" role="columnheader">产品尺寸</th>
            <th scope="col" role="columnheader">外箱尺寸</th>
            <th scope="col" role="columnheader" class="pd-num">装箱数量</th>
            <th scope="col" role="columnheader" class="pd-num">箱规体积</th>
            <th scope="col" role="columnheader" class="pd-num">毛重</th>
            <th scope="col" role="columnheader" class="pd-num">采购价</th>
            <th scope="col" role="columnheader" class="pd-num">销售价</th>
            <th scope="col" role="columnheader" class="pd-num">库存</th>
            <th scope="col" role="columnheader" class="pd-num">安全库存</th>
            <th scope="col" role="columnheader">状态</th>
          </tr>
        </thead>
        <tbody v-for="(sku, index) in skus" :key="sku.id" role="rowgroup">
          <tr role="row" class="pd-sku-record" :class="{ 'pd-selected-sku': sku.id === selectedSkuId }" @click="$emit('select-sku', sku.id)">
            <td role="cell">
              <div class="pd-sku-identity">
                <div class="pd-sku-thumb" @click.stop><ProductDetailImage :src="sku.skuImageUrl" :alt="`${sku.skuName || sku.skuCode} SKU 图片`" :test-id="`sku-image-${sku.id}`" size="small" /></div>
                <div class="pd-sku-name-wrap">
                  <button :data-testid="`select-sku-${sku.id}`" type="button" class="pd-sku-select" :aria-pressed="sku.id === selectedSkuId" @click.stop="$emit('select-sku', sku.id)">{{ formatText(sku.specText || sku.specificationValues.join(' / ') || sku.skuName) }}</button>
                  <span v-if="sku.defaultSku" class="pd-tag">默认</span>
                  <div class="pd-sku-code-line">
                    <span :data-testid="`sku-item-number-${sku.id}`" class="pd-sku-code">{{ formatText(sku.skuCode) }}</span>
                    <button
                      :data-testid="`copy-sku-parameters-${sku.id}`"
                      class="pd-sku-copy"
                      type="button"
                      :aria-label="`复制参数（${formatText(sku.skuCode)}）`"
                      @click.stop="copySkuParameters(sku)"
                    ><Copy aria-hidden="true" />复制参数</button>
                  </div>
                  <span class="pd-sku-barcode">条码 {{ formatText(sku.barcode) }}</span>
                </div>
              </div>
            </td>
            <td :id="index === 0 ? 'product-packaging' : undefined" role="cell" class="pd-sku-package-field" data-label="产品尺寸">{{ formatProductDimensions(sku) }}</td>
            <td role="cell" class="pd-sku-package-field" data-label="外箱尺寸">{{ formatDimensions(sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm) }}</td>
            <td role="cell" class="pd-sku-package-field pd-num" data-label="装箱数量">{{ formatWithUnit(sku.cartonQuantity, `${sku.salesUnit?.trim() || ''}/箱`) }}</td>
            <td role="cell" class="pd-sku-package-field pd-num" data-label="箱规体积">{{ formatWithUnit(sku.packageVolumeCm3, 'cm³') }}</td>
            <td role="cell" class="pd-sku-package-field pd-num" data-label="毛重">{{ formatWithUnit(sku.grossWeightKg, 'kg') }}</td>
            <td role="cell" class="pd-num" data-label="采购价">{{ formatMoney(getDefaultSupplierQuote(sku)?.purchasePrice) }}</td>
            <td role="cell" class="pd-num pd-sale" data-label="销售价">{{ formatMoney(sku.defaultSalePrice) }}</td>
            <td role="cell" class="pd-num" data-label="库存">{{ formatProductNumber(sku.stockQuantity) }}</td>
            <td role="cell" class="pd-num pd-muted" data-label="安全库存">{{ formatProductNumber(sku.safetyStockQuantity) }}</td>
            <td role="cell" data-label="状态"><span class="pd-status" :class="{ 'pd-status-enabled': sku.status === 'enabled' }"><i aria-hidden="true"></i>{{ formatRecordStatus(sku.status) }}</span></td>
          </tr>
          <tr role="row" class="pd-sku-details-row" :class="{ 'pd-selected-sku': sku.id === selectedSkuId }">
            <td role="cell" colspan="11"><ProductPackagingSection :sku="sku" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else id="product-packaging" class="pd-empty">暂无 SKU 明细</p>
    <div v-if="skus.length" class="pd-panel-footer"><span>当前条码 <strong>{{ formatText(currentSku?.barcode) }}</strong></span><span>完整 SKU 信息通过“编辑商品”维护</span></div>
  </section>
</template>
