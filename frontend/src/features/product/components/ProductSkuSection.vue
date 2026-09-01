<script setup lang="ts">
import { formatMoney, formatRecordStatus, formatText } from '../productDetailFormatting';
import type { ProductSku } from '../types';
import ProductDetailImage from './ProductDetailImage.vue';

defineProps<{ skus: ProductSku[] }>();
</script>

<template>
  <section data-testid="product-sku-section" class="w-full rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
    <div class="flex items-center justify-between gap-3 border-b border-slate-100 pb-3">
      <h2 class="text-base font-black text-[#25314d]">SKU 明细</h2>
      <span class="text-xs font-bold text-slate-500">{{ skus.length }} 个 SKU</span>
    </div>

    <div v-if="skus.length" class="w-full overflow-x-auto">
      <table class="min-w-[1280px] table-fixed text-left text-sm">
        <thead class="border-b border-slate-200 text-xs font-bold text-slate-400">
          <tr>
            <th class="w-20 px-3 py-3">图片</th>
            <th class="w-44 px-3 py-3">SKU 货号</th>
            <th class="w-44 px-3 py-3">规格</th>
            <th class="w-40 px-3 py-3">单杯条码</th>
            <th class="w-24 px-3 py-3">销售单位</th>
            <th class="w-28 px-3 py-3 text-right">单价</th>
            <th class="w-28 px-3 py-3 text-right">成本</th>
            <th class="w-28 px-3 py-3 text-right">当前库存</th>
            <th class="w-28 px-3 py-3 text-right">安全库存</th>
            <th class="w-24 px-3 py-3">状态</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 text-slate-600">
          <tr v-for="sku in skus" :key="sku.id" class="align-middle">
            <td class="px-3 py-3">
              <ProductDetailImage
                :src="sku.skuImageUrl"
                :alt="`${sku.skuName || sku.skuCode} SKU 图片`"
                :test-id="`sku-image-${sku.id}`"
                size="small"
              />
            </td>
            <td :data-testid="`sku-item-number-${sku.id}`" class="break-all px-3 py-3 font-bold text-[#25314d]">{{ formatText(sku.skuCode) }}</td>
            <td class="break-words px-3 py-3">{{ formatText(sku.specText || sku.specificationValues.join(' / ')) }}</td>
            <td class="break-all px-3 py-3 font-mono text-xs">{{ formatText(sku.barcode) }}</td>
            <td class="px-3 py-3">{{ formatText(sku.salesUnit) }}</td>
            <td class="px-3 py-3 text-right tabular-nums">{{ formatMoney(sku.defaultSalePrice) }}</td>
            <td class="px-3 py-3 text-right tabular-nums">{{ formatMoney(sku.standardCost) }}</td>
            <td class="px-3 py-3 text-right tabular-nums">{{ formatText(sku.stockQuantity) }}</td>
            <td class="px-3 py-3 text-right tabular-nums">{{ formatText(sku.safetyStockQuantity) }}</td>
            <td class="px-3 py-3 font-bold" :class="sku.status === 'enabled' ? 'text-emerald-600' : 'text-slate-400'">{{ formatRecordStatus(sku.status) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="pt-4 text-sm font-medium text-slate-400">暂无 SKU 明细</p>
  </section>
</template>
