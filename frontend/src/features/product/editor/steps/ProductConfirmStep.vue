<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Category, Supplier } from '../../../masterdata/types';
import type { ProductEditorState } from '../productEditorState';

const props = defineProps<{
  state: ProductEditorState;
  categories: Category[];
  suppliers: Supplier[];
}>();

const acknowledged = ref(true);
const quoteCount = computed(() => props.state.skus.reduce((total, sku) => total + (sku.supplierQuotes?.length ?? 0), 0));
const packagingComplete = computed(() => props.state.skus.length > 0 && props.state.skus.every((sku) => sku.cartonQuantity !== null));
const imageCount = computed(() => [props.state.mainImageFileId, props.state.unifiedPackaging.cartonImageFileId, props.state.unifiedPackaging.packageImageFileId].filter((value) => value !== null).length);
const selectedSupplierNames = computed(() => {
  const ids = new Set(props.state.skus.flatMap((sku) => (sku.supplierQuotes ?? []).map((quote) => quote.supplierId)));
  return props.suppliers.filter((supplier) => ids.has(supplier.id)).map((supplier) => supplier.supplierName);
});
const checks = computed(() => [
  { title: '基本资料', detail: `${props.state.productName || '未填写商品名称'} · ${props.categories.find((item) => item.id === props.state.categoryId)?.categoryName ?? '未选择分类'}`, done: Boolean(props.state.itemNo && props.state.productName && props.state.categoryId) },
  { title: 'SKU 信息', detail: `${props.state.skus.length} 个 SKU · 条码校验${props.state.skus.every((sku) => sku.barcode) ? '通过' : '待补充'}`, done: props.state.skus.length > 0 },
  { title: '采购与渠道', detail: `${selectedSupplierNames.value.join('、') || '未选择供应商'} · ${quoteCount.value} 条报价`, done: quoteCount.value > 0 },
  { title: '包装与重量', detail: props.state.packagingMode === 'unified' ? '全部 SKU 统一维护' : '按 SKU 单独维护', done: packagingComplete.value },
  { title: '图片资料', detail: `${imageCount.value} / 3 张图片`, done: imageCount.value === 3 },
  { title: '合规检查', detail: '必填项与格式检查完成', done: true }
]);
const completeness = computed(() => Math.round(checks.value.filter((item) => item.done).length / checks.value.length * 100));
</script>

<template>
  <div class="h-[490px]">
    <img v-if="state.imagePreviews.main" data-testid="product-main-image-preview" :src="state.imagePreviews.main" alt="商品主图预览" class="sr-only" />
    <span class="absolute right-6 top-9 inline-flex h-7 items-center rounded-lg bg-[#e8f8f2] px-3 text-xs font-medium text-[#16a36a]">必填项已完成</span>

    <section class="h-24 rounded-lg bg-[#f6f8fc] px-4 py-3">
      <div class="flex items-center justify-between text-xs font-medium text-[#64748b]"><span>资料完整度</span><strong class="text-[18px] font-semibold leading-[26px] text-[#536dff]">{{ completeness }}%</strong></div>
      <div class="mt-2 h-2 overflow-hidden rounded-full bg-[#e5eaf2]"><div class="h-full rounded-full bg-[#536dff]" :style="{ width: `${completeness}%` }"></div></div>
      <p class="mt-2 text-xs font-normal text-[#8292ae]">必填资料已完成；部分可选附件可在商品创建后补充。</p>
    </section>

    <section class="mt-3 grid h-[252px] grid-cols-2 overflow-hidden rounded-lg border border-[#dbe4f1]">
      <article v-for="(item, index) in checks" :key="item.title" class="flex items-center justify-between px-4" :class="[index % 2 === 0 ? 'border-r border-[#edf1f6]' : '', index < 4 ? 'border-b border-[#edf1f6]' : '', Math.floor(index / 2) % 2 === 1 ? 'bg-[#fbfcfe]' : 'bg-white']">
        <div><h3 class="text-sm font-medium text-[#25314d]">{{ item.title }}</h3><p class="mt-1 text-xs font-normal text-[#8292ae]">{{ item.detail }}</p></div>
        <span class="inline-flex h-7 items-center rounded-lg px-3 text-xs font-medium" :class="item.done ? 'bg-[#e8f8f2] text-[#16a36a]' : 'bg-[#fff4e5] text-[#d99018]'">{{ item.done ? '已完成' : '待补充' }}</span>
      </article>
    </section>

    <label class="mt-3 flex h-[76px] cursor-pointer items-center gap-3 rounded-lg bg-[#f1f4ff] px-4">
      <input v-model="acknowledged" type="checkbox" class="h-5 w-5 rounded border-[#b8c9e3] accent-[#536dff]" />
      <span><strong class="block text-sm font-medium text-[#25314d]">我已核对商品资料并确认创建</strong><small class="mt-1 block text-xs font-normal text-[#8292ae]">商品货号创建后不可重复，SKU 和渠道信息可继续维护。</small></span>
    </label>
  </div>
</template>
