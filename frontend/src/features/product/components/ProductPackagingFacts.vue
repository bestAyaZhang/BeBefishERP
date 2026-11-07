<script setup lang="ts">
import { formatDimensions, formatProductDimensions, formatText, formatWithUnit } from '../productDetailFormatting';
import type { ProductSku } from '../types';

defineProps<{ sku: ProductSku }>();
</script>

<template>
  <div class="pd-packing-columns">
    <div class="pd-packing-column"><h3>产品与包装</h3><dl>
      <div><dt>产品尺寸</dt><dd>{{ formatProductDimensions(sku) }}</dd></div>
      <div><dt>容量</dt><dd>{{ formatWithUnit(sku.capacityMl, 'ml') }}</dd></div>
      <div><dt>包装方式</dt><dd>{{ formatText(sku.packagingMethod) }}</dd></div>
      <div><dt>装箱数量</dt><dd>{{ formatWithUnit(sku.cartonQuantity, `${sku.salesUnit?.trim() || ''}/箱`) }}</dd></div>
    </dl></div>
    <div class="pd-packing-column"><h3>箱规尺寸</h3><dl>
      <div><dt>外箱尺寸</dt><dd>{{ formatDimensions(sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm) }}</dd></div>
      <div><dt>内盒尺寸</dt><dd>{{ formatDimensions(sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm) }}</dd></div>
      <div><dt>箱规体积</dt><dd>{{ formatWithUnit(sku.packageVolumeCm3, 'cm³') }}</dd></div>
      <div><dt>出库单位</dt><dd>{{ formatText(sku.salesUnit) }}</dd></div>
    </dl></div>
    <div class="pd-packing-column"><h3>重量信息</h3><dl>
      <div><dt>克重</dt><dd>{{ formatWithUnit(sku.gramWeightG, 'g') }}</dd></div>
      <div><dt>内盒重量</dt><dd>{{ formatWithUnit(sku.innerPackageWeightKg, 'kg') }}</dd></div>
      <div><dt>净重</dt><dd>{{ formatWithUnit(sku.netWeightKg, 'kg') }}</dd></div>
      <div><dt>毛重</dt><dd>{{ formatWithUnit(sku.grossWeightKg, 'kg') }}</dd></div>
    </dl></div>
  </div>
</template>
