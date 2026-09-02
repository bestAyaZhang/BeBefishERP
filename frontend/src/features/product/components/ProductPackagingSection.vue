<script setup lang="ts">
import { computed } from 'vue';
import { formatDimensions, formatText, formatWithUnit } from '../productDetailFormatting';
import { getProductPackagingMode } from '../productPackaging';
import type { ProductSku } from '../types';

const props = defineProps<{ skus: ProductSku[] }>();
const mode = computed(() => getProductPackagingMode(props.skus));
const uniformSku = computed(() => props.skus[0] ?? null);
const totalStock = computed(() => props.skus.reduce((sum, sku) => sum + (Number.isFinite(sku.stockQuantity) ? sku.stockQuantity : 0), 0));
const totalSafetyStock = computed(() => props.skus.reduce((sum, sku) => sum + (Number.isFinite(sku.safetyStockQuantity) ? sku.safetyStockQuantity : 0), 0));
const primaryUnit = computed(() => props.skus.find((sku) => sku.defaultSku)?.salesUnit ?? props.skus[0]?.salesUnit ?? null);
</script>

<template>
  <section data-testid="product-packaging-section" class="min-h-[246px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[19px]" :class="mode === 'uniform' && skus.length ? 'lg:h-[246px] lg:overflow-hidden' : ''">
    <div class="flex min-h-[30px] flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">包装与重量信息</h2>
        <span v-if="skus.length" class="inline-flex h-[26px] items-center rounded-lg bg-[#eef2ff] px-2 text-xs font-medium leading-[18px] text-[#536dff]">
          {{ mode === 'uniform' ? '全部 SKU 统一包装' : '按 SKU 展示' }}
        </span>
      </div>
      <p v-if="skus.length" class="hidden text-xs font-normal leading-[18px] text-[#94a3b8] lg:block">
        {{ mode === 'uniform' ? `适用于全部 ${skus.length} 个 SKU` : '每个 SKU 使用独立包装箱规与重量' }}
      </p>
    </div>

    <template v-if="skus.length && mode === 'uniform' && uniformSku">
      <div class="mt-3 grid min-h-[164px] gap-6 lg:grid-cols-[1fr_1px_1fr_1px_1fr]">
        <div class="min-w-0">
          <h3 class="text-[18px] font-medium leading-[26px] text-[#25314d]">包装规格</h3>
          <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-[5px]">
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">外箱尺寸</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(uniformSku.packageLengthCm, uniformSku.packageWidthCm, uniformSku.packageHeightCm) }}</dd>
            </div>
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">内盒尺寸</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(uniformSku.innerPackageLengthCm, uniformSku.innerPackageWidthCm, uniformSku.innerPackageHeightCm) }}</dd>
            </div>
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">产品尺寸</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(uniformSku.productLengthCm, uniformSku.productWidthCm, uniformSku.productHeightCm) }}</dd>
            </div>
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">容量</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.capacityMl, 'ml') }}</dd>
            </div>
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">包装方式</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatText(uniformSku.packagingMethod) }}</dd>
            </div>
            <div class="min-h-10 min-w-0">
              <dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">装箱数量</dt>
              <dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.cartonQuantity, '件/箱') }}</dd>
            </div>
          </dl>
        </div>
        <div class="hidden bg-[#e2e8f0] lg:block" aria-hidden="true"></div>
        <div class="min-w-0">
          <h3 class="text-[18px] font-medium leading-[26px] text-[#25314d]">重量信息</h3>
          <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-[5px]">
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">克重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.gramWeightG, 'g') }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">内盒重量</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.innerPackageWeightKg, 'kg') }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">净重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.netWeightKg, 'kg') }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">毛重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.grossWeightKg, 'kg') }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">箱规体积</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(uniformSku.packageVolumeCm3, 'cm³') }}</dd></div>
          </dl>
        </div>
        <div class="hidden bg-[#e2e8f0] lg:block" aria-hidden="true"></div>
        <div class="min-w-0">
          <h3 class="text-[18px] font-medium leading-[26px] text-[#25314d]">仓储物流</h3>
          <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-[5px]">
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">默认仓库</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">--</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">库位</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">--</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">当前库存</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ totalStock }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">安全库存</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ totalSafetyStock }}</dd></div>
            <div class="min-h-10 min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">出库单位</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatText(primaryUnit) }}</dd></div>
          </dl>
        </div>
      </div>
    </template>

    <div v-else-if="skus.length" class="mt-3 space-y-3">
      <article v-for="sku in skus" :key="sku.id" :data-testid="`packaging-sku-${sku.id}`" class="grid min-h-[156px] gap-6 rounded-lg bg-[#f8fafc] px-4 py-[15px] lg:grid-cols-[244px_minmax(0,1.3fr)_minmax(0,1fr)]">
        <div class="flex min-w-0 flex-col justify-center">
          <p class="truncate text-sm font-medium leading-[22px] text-[#4f6bff]">{{ formatText(sku.skuCode) }}</p>
          <p class="mt-1 truncate text-xs font-normal leading-[18px] text-[#94a3b8]">{{ formatText(sku.specText || sku.specificationValues.join(' / ')) }}</p>
        </div>
        <div class="min-w-0 border-t border-[#e2e8f0] pt-3 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <h3 class="text-sm font-medium leading-[22px] text-[#25314d]">包装规格</h3>
          <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-2">
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">外箱尺寸</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm) }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">内盒尺寸</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm) }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">产品尺寸</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatDimensions(sku.productLengthCm, sku.productWidthCm, sku.productHeightCm) }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">容量</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.capacityMl, 'ml') }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">包装方式</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatText(sku.packagingMethod) }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">装箱数量</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.cartonQuantity, '件/箱') }}</dd></div>
          </dl>
        </div>
        <div class="min-w-0 border-t border-[#e2e8f0] pt-3 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <h3 class="text-sm font-medium leading-[22px] text-[#25314d]">重量信息</h3>
          <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-2">
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">克重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.gramWeightG, 'g') }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">内盒重量</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.innerPackageWeightKg, 'kg') }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">净重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.netWeightKg, 'kg') }}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-normal leading-[18px] text-[#94a3b8]">毛重</dt><dd class="truncate text-sm font-medium leading-5 text-[#25314d]">{{ formatWithUnit(sku.grossWeightKg, 'kg') }}</dd></div>
          </dl>
        </div>
      </article>
      <div class="grid gap-x-6 rounded-lg bg-[#f8fafc] px-4 py-3 sm:grid-cols-3">
        <div><p class="text-xs font-normal leading-[18px] text-[#94a3b8]">当前库存</p><p class="text-sm font-medium leading-[22px] text-[#25314d]">{{ totalStock }}</p></div>
        <div><p class="text-xs font-normal leading-[18px] text-[#94a3b8]">安全库存</p><p class="text-sm font-medium leading-[22px] text-[#25314d]">{{ totalSafetyStock }}</p></div>
        <div><p class="text-xs font-normal leading-[18px] text-[#94a3b8]">出库单位</p><p class="text-sm font-medium leading-[22px] text-[#25314d]">{{ formatText(primaryUnit) }}</p></div>
      </div>
    </div>
    <p v-else class="mt-3 rounded-lg border border-dashed border-[#e2e8f0] py-14 text-center text-sm font-normal text-[#94a3b8]">暂无 SKU 包装数据</p>
  </section>
</template>
