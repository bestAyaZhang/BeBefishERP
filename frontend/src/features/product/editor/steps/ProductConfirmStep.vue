<script setup lang="ts">
import { computed } from 'vue';
import type { Category, Supplier } from '../../../masterdata/types';
import type { ProductEditorState } from '../productEditorState';

const props = defineProps<{
  state: ProductEditorState;
  categories: Category[];
  suppliers: Supplier[];
}>();

const categoryName = computed(() => props.categories.find((category) => category.id === props.state.categoryId)?.categoryName ?? '--');
const supplierNames = computed(() => new Map(props.suppliers.map((supplier) => [supplier.id, supplier.supplierName])));
const quoteCount = computed(() => props.state.skus.reduce((total, sku) => total + (sku.supplierQuotes?.length ?? 0), 0));
</script>

<template>
  <div class="space-y-7">
    <section class="grid gap-6 border-b border-slate-200 pb-6 lg:grid-cols-[160px_minmax(0,1fr)]">
      <div>
        <img v-if="state.imagePreviews.main" data-testid="product-main-image-preview" :src="state.imagePreviews.main" alt="商品主图预览" class="aspect-square w-36 rounded-lg border border-slate-200 object-cover" />
        <div v-else class="grid aspect-square w-36 place-items-center rounded-lg border border-dashed border-slate-300 text-xs font-bold text-slate-400">未上传主图</div>
      </div>
      <dl class="grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2">
        <div><dt class="font-bold text-slate-400">货号</dt><dd class="mt-1 font-black text-[#25314d]">{{ state.itemNo || '--' }}</dd></div>
        <div><dt class="font-bold text-slate-400">商品名称</dt><dd class="mt-1 font-black text-[#25314d]">{{ state.productName || '--' }}</dd></div>
        <div><dt class="font-bold text-slate-400">分类</dt><dd class="mt-1 font-semibold text-slate-700">{{ categoryName }}</dd></div>
        <div><dt class="font-bold text-slate-400">品牌</dt><dd class="mt-1 font-semibold text-slate-700">{{ state.brand || '--' }}</dd></div>
        <div><dt class="font-bold text-slate-400">商品类型</dt><dd class="mt-1 font-semibold text-slate-700">{{ state.productType === 'simple' ? '单规格' : '多规格' }}</dd></div>
        <div><dt class="font-bold text-slate-400">商品状态</dt><dd class="mt-1 font-semibold text-slate-700">{{ state.status === 'enabled' ? '启用' : '停用' }}</dd></div>
        <div class="sm:col-span-2"><dt class="font-bold text-slate-400">备注</dt><dd class="mt-1 whitespace-pre-wrap font-semibold text-slate-700">{{ state.remark || '--' }}</dd></div>
      </dl>
    </section>

    <section>
      <div class="flex items-center justify-between gap-4">
        <h3 class="text-sm font-black text-[#25314d]">SKU 与采购</h3>
        <span class="text-xs font-bold text-slate-500">{{ state.skus.length }} 个 SKU · {{ quoteCount }} 条报价</span>
      </div>
      <div class="mt-3 overflow-x-auto border-y border-slate-200">
        <table class="w-full min-w-[760px] border-collapse text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black text-slate-500"><tr><th class="px-4 py-3">SKU</th><th class="px-4 py-3">规格</th><th class="px-4 py-3">供应商报价</th><th class="px-4 py-3">包装</th><th class="px-4 py-3">图片</th></tr></thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="(sku, index) in state.skus" :key="sku.id ?? `confirm-${index}`">
              <td class="px-4 py-4"><p class="font-black text-[#25314d]">{{ sku.skuName }}</p><p class="mt-1 text-xs font-semibold text-slate-400">{{ sku.skuCode || '保存后生成' }}</p><p class="mt-1 text-xs font-semibold text-slate-400">条码：{{ sku.barcode || '--' }} · {{ sku.status === 'enabled' ? '启用' : '停用' }}<span v-if="sku.defaultSku"> · 默认</span></p></td>
              <td class="px-4 py-4 font-semibold text-slate-600">{{ sku.specificationValues.join(' / ') || '--' }}</td>
              <td class="px-4 py-4">
                <p v-for="quote in sku.supplierQuotes ?? []" :key="quote.id ?? `${quote.supplierId}-${quote.supplierItemNo}`" class="font-semibold text-slate-600">{{ supplierNames.get(quote.supplierId) ?? '--' }}<span v-if="quote.defaultQuote" class="text-[#536dff]"> · 默认</span></p>
                <span v-if="!sku.supplierQuotes?.length" class="font-medium text-slate-400">--</span>
              </td>
              <td class="px-4 py-4 font-semibold text-slate-600">{{ sku.packageLengthCm ?? '--' }} × {{ sku.packageWidthCm ?? '--' }} × {{ sku.packageHeightCm ?? '--' }} cm</td>
              <td class="px-4 py-4 font-semibold text-slate-600">{{ [sku.skuImageFileId, sku.packageImageFileId, sku.cartonImageFileId].filter((value) => value !== null).length }}/3</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
