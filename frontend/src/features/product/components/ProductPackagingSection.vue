<script setup lang="ts">
import { computed } from 'vue';
import { formatDimensions, formatText, formatWithUnit } from '../productDetailFormatting';
import { getProductPackagingMode } from '../productPackaging';
import type { ProductSku } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

const props = defineProps<{ skus: ProductSku[] }>();
const mode = computed(() => getProductPackagingMode(props.skus));
const uniformSku = computed(() => props.skus[0] ?? null);
const applicableSkus = computed(() => props.skus.map((sku) => sku.skuCode).join('、'));
</script>

<template>
  <section data-testid="product-packaging-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">包装重量</h2>
      <span v-if="skus.length" class="text-xs font-bold" :class="mode === 'uniform' ? 'text-[#536dff]' : 'text-amber-600'">
        {{ mode === 'uniform' ? '统一包装' : '按 SKU 展示' }}
      </span>
    </div>

    <template v-if="skus.length && mode === 'uniform' && uniformSku">
      <p class="pt-4 text-xs font-medium text-slate-500">适用 SKU：{{ applicableSkus }}</p>
      <dl class="grid grid-cols-1 gap-x-6 pt-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">外箱长宽高</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatDimensions(uniformSku.packageLengthCm, uniformSku.packageWidthCm, uniformSku.packageHeightCm) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">内盒长宽高</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatDimensions(uniformSku.innerPackageLengthCm, uniformSku.innerPackageWidthCm, uniformSku.innerPackageHeightCm) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">包装体积</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.packageVolumeCm3, 'cm³') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">装箱数量</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.cartonQuantity, '件/箱') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">包装方式</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatText(uniformSku.packagingMethod) }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">克重</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.gramWeightG, 'g') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">净重</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.netWeightKg, 'kg') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">毛重</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.grossWeightKg, 'kg') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">内盒重量</dt>
          <dd class="mt-1 text-sm font-bold text-[#25314d]">{{ formatWithUnit(uniformSku.innerPackageWeightKg, 'kg') }}</dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">内盒包装图</dt>
          <dd class="mt-2">
            <ProductDetailImage
              :src="uniformSku.packageImageUrl"
              :alt="`${uniformSku.skuName || uniformSku.skuCode} 内盒包装图`"
              :test-id="`packaging-package-image-${uniformSku.id}`"
              size="small"
            />
          </dd>
        </div>
        <div class="border-b border-slate-100 py-3">
          <dt class="text-xs font-bold text-slate-400">外箱图</dt>
          <dd class="mt-2">
            <ProductDetailImage
              :src="uniformSku.cartonImageUrl"
              :alt="`${uniformSku.skuName || uniformSku.skuCode} 外箱图`"
              :test-id="`packaging-carton-image-${uniformSku.id}`"
              size="small"
            />
          </dd>
        </div>
      </dl>
    </template>

    <div v-else-if="skus.length" class="w-full overflow-x-auto">
      <table class="min-w-[1640px] table-fixed text-left text-sm">
        <thead class="border-b border-slate-200 text-xs font-bold text-slate-400">
          <tr>
            <th class="w-44 px-3 py-3">SKU 货号</th>
            <th class="w-48 px-3 py-3">外箱长宽高</th>
            <th class="w-48 px-3 py-3">内盒长宽高</th>
            <th class="w-28 px-3 py-3">包装体积</th>
            <th class="w-28 px-3 py-3">装箱数量</th>
            <th class="w-28 px-3 py-3">包装方式</th>
            <th class="w-24 px-3 py-3">克重</th>
            <th class="w-24 px-3 py-3">净重</th>
            <th class="w-24 px-3 py-3">毛重</th>
            <th class="w-28 px-3 py-3">内盒重量</th>
            <th class="w-24 px-3 py-3">内盒包装图</th>
            <th class="w-24 px-3 py-3">外箱图</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 text-slate-600">
          <tr v-for="sku in skus" :key="sku.id">
            <td class="break-all px-3 py-3 font-bold text-[#25314d]">{{ formatText(sku.skuCode) }}</td>
            <td class="px-3 py-3">{{ formatDimensions(sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm) }}</td>
            <td class="px-3 py-3">{{ formatDimensions(sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm) }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.packageVolumeCm3, 'cm³') }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.cartonQuantity, '件/箱') }}</td>
            <td class="px-3 py-3">{{ formatText(sku.packagingMethod) }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.gramWeightG, 'g') }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.netWeightKg, 'kg') }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.grossWeightKg, 'kg') }}</td>
            <td class="px-3 py-3">{{ formatWithUnit(sku.innerPackageWeightKg, 'kg') }}</td>
            <td class="px-3 py-3">
              <ProductDetailImage
                :src="sku.packageImageUrl"
                :alt="`${sku.skuName || sku.skuCode} 内盒包装图`"
                :test-id="`packaging-package-image-${sku.id}`"
                size="small"
              />
            </td>
            <td class="px-3 py-3">
              <ProductDetailImage
                :src="sku.cartonImageUrl"
                :alt="`${sku.skuName || sku.skuCode} 外箱图`"
                :test-id="`packaging-carton-image-${sku.id}`"
                size="small"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="pt-4 text-sm font-medium text-slate-400">暂无 SKU 包装数据</p>
  </section>
</template>
