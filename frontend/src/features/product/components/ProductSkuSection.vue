<script setup lang="ts">
import { formatMoney, formatRecordStatus, formatText } from '../productDetailFormatting';
import type { ProductSku } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

defineProps<{ skus: ProductSku[] }>();
</script>

<template>
  <section data-testid="product-sku-section" class="min-h-[318px] w-full rounded-lg border border-[#e2e8f0] bg-white px-[23px] py-[23px]">
    <div class="flex min-h-[26px] items-center justify-between gap-4">
      <div class="flex min-w-0 items-center gap-2">
        <h2 class="text-[18px] font-medium leading-[26px] text-[#25314d]">SKU 明细</h2>
        <span class="inline-flex h-[26px] items-center rounded-lg bg-[#f8fafc] px-2 text-xs font-normal leading-[18px] text-[#64748b]">{{ skus.length }} 个规格</span>
      </div>
      <p class="hidden text-xs font-normal leading-[18px] text-[#94a3b8] lg:block">商品级信息只读，点击右上角“编辑”维护 SKU</p>
    </div>

    <div v-if="skus.length" class="mt-3 w-full overflow-x-auto rounded-lg border border-[#e2e8f0]">
      <table class="w-full min-w-[900px] table-fixed text-left text-sm">
        <colgroup>
          <col class="w-[7%]" />
          <col class="w-[15%]" />
          <col class="w-[20%]" />
          <col class="w-[16%]" />
          <col class="w-[10%]" />
          <col class="w-[8%]" />
          <col class="w-[11%]" />
          <col class="w-[13%]" />
        </colgroup>
        <thead class="bg-[#f8fafc] text-xs font-normal leading-[18px] text-[#94a3b8]">
          <tr>
            <th class="h-10 px-3 font-normal">SKU图片</th>
            <th class="h-10 px-3 font-normal">SKU货号</th>
            <th class="h-10 px-3 font-normal">规格组合</th>
            <th class="h-10 px-3 font-normal">商品条码</th>
            <th class="h-10 px-3 text-right font-normal">销售价</th>
            <th class="h-10 px-3 font-normal">库存</th>
            <th class="h-10 px-3 font-normal">安全库存</th>
            <th class="h-10 px-3 font-normal">状态</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-[#e2e8f0] text-[#25314d]">
          <tr v-for="sku in skus" :key="sku.id" class="h-16 align-middle">
            <td class="px-4 py-3">
              <ProductDetailImage
                :src="sku.skuImageUrl"
                :alt="`${sku.skuName || sku.skuCode} SKU 图片`"
                :test-id="`sku-image-${sku.id}`"
                size="small"
              />
            </td>
            <td :data-testid="`sku-item-number-${sku.id}`" class="truncate px-3 py-3 text-sm font-medium leading-[22px] text-[#4f6bff]">{{ formatText(sku.skuCode) }}</td>
            <td class="truncate px-3 py-3 text-sm font-normal leading-[22px]">{{ formatText(sku.specText || sku.specificationValues.join(' / ')) }}</td>
            <td class="truncate px-3 py-3 font-sans text-sm font-normal leading-[22px] text-[#64748b]">{{ formatText(sku.barcode) }}</td>
            <td class="px-3 py-3 text-right font-medium tabular-nums">{{ formatMoney(sku.defaultSalePrice) }}</td>
            <td class="px-3 py-3 font-normal tabular-nums">{{ formatText(sku.stockQuantity) }}</td>
            <td class="px-3 py-3 font-normal tabular-nums text-[#64748b]">{{ formatText(sku.safetyStockQuantity) }}</td>
            <td class="px-3 py-3">
              <span class="inline-flex h-[26px] items-center rounded-md px-2.5 text-xs font-medium leading-[18px]" :class="sku.status === 'enabled' ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-[#64748b]'">{{ formatRecordStatus(sku.status) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="mt-3 rounded-lg border border-dashed border-[#e2e8f0] py-16 text-center text-sm font-normal text-[#94a3b8]">暂无 SKU 明细</p>
  </section>
</template>
