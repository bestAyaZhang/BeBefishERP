<script setup lang="ts">
import { PackageOpen, X } from 'lucide-vue-next';
import { computed } from 'vue';
import type { Product } from '../types';

const props = withDefaults(defineProps<{ product: Product; presentation?: 'drawer' | 'page' }>(), {
  presentation: 'drawer'
});
const emit = defineEmits<{ close: []; edit: [] }>();
const isDrawer = computed(() => props.presentation === 'drawer');

const primarySku = computed(() => props.product.skus.find((sku) => sku.defaultSku) ?? props.product.skus[0]);
const statusText = computed(() => props.product.status === 'enabled' ? '在售' : '已停用');
const auditText = computed(() => props.product.mainImageFileId && props.product.skus.length ? '资料完整' : '待完善');
const priceText = computed(() => primarySku.value?.defaultSalePrice === null || primarySku.value?.defaultSalePrice === undefined ? '-' : `¥${Number(primarySku.value.defaultSalePrice).toFixed(2)}`);
const specificationUnits: Record<string, string> = {
  口径: 'mm',
  高度: 'mm',
  容量: 'ml',
  重量: 'g'
};

function appendSpecificationUnit(value: string, unit?: string) {
  const normalizedValue = value.trim();
  if (!normalizedValue || !unit || /(mm|ml|g)\s*$/i.test(normalizedValue)) return normalizedValue;
  return `${normalizedValue}${unit}`;
}

function formatSpecification(name: string, value: string) {
  return `${name}：${appendSpecificationUnit(value, specificationUnits[name])}`;
}

const specificationText = computed(() => {
  const productSpecifications = props.product.specifications
    .map((specification) => formatSpecification(specification.name, specification.values.map((value) => appendSpecificationUnit(value, specificationUnits[specification.name])).join(' / ')))
    .filter(Boolean);
  return productSpecifications.join(' · ') || primarySku.value?.specificationValues.join(' / ') || '无规格';
});

function skuSpecificationText(values: string[]) {
  if (!values.length) return specificationText.value;
  if (values.length === props.product.specifications.length && props.product.specifications.length > 0) {
    return values.map((value, index) => formatSpecification(props.product.specifications[index].name, value)).join(' · ');
  }
  return values.join(' / ');
}
const outerBoxSize = computed(() => {
  const sku = primarySku.value;
  if (!sku || sku.packageLengthCm === null || sku.packageWidthCm === null || sku.packageHeightCm === null) return '-';
  return `${sku.packageLengthCm} × ${sku.packageWidthCm} × ${sku.packageHeightCm} cm`;
});
const completionSegments = computed(() => {
  const sku = primarySku.value;
  const packagingReady = Boolean(sku && sku.packageLengthCm !== null && sku.packageWidthCm !== null && sku.packageHeightCm !== null && sku.grossWeightKg !== null);
  return [
    { label: '基础字段', value: props.product.productName && props.product.itemNo ? 100 : 70, color: 'bg-[#536dff]' },
    { label: '图片素材', value: props.product.mainImageFileId ? 100 : 0, color: 'bg-[#36bee3]' },
    { label: 'SKU 资料', value: props.product.skus.length ? 100 : 0, color: 'bg-[#7b45f5]' },
    { label: '规格包装', value: packagingReady ? 100 : 40, color: 'bg-[#fb7fa2]' }
  ];
});
const completionRate = computed(() => Math.round(completionSegments.value.reduce((total, segment) => total + segment.value, 0) / completionSegments.value.length));

function close() {
  emit('close');
}
</script>

<template>
  <Transition name="product-drawer" :css="isDrawer">
    <div
      :data-testid="isDrawer ? undefined : 'product-detail-page'"
      :class="isDrawer ? 'fixed inset-0 z-50 flex justify-end' : 'mx-auto max-w-[1440px]'"
      :aria-modal="isDrawer ? 'true' : undefined"
      :role="isDrawer ? 'dialog' : undefined"
    >
      <button v-if="isDrawer" class="absolute inset-0 bg-slate-900/20 backdrop-blur-[1px]" type="button" aria-label="关闭商品详情抽屉" @click="close"></button>

      <component
        :is="isDrawer ? 'aside' : 'section'"
        :data-testid="isDrawer ? 'product-detail-drawer' : undefined"
        :class="isDrawer ? 'product-drawer-panel relative z-10 h-full w-[50vw] min-w-[620px] max-w-[840px] max-[900px]:min-w-0 max-[900px]:w-[calc(100vw-24px)] overflow-y-auto border-l border-slate-200 bg-white p-6 shadow-[0_24px_80px_rgba(31,45,74,0.24)]' : 'overflow-hidden rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)] sm:p-8'"
      >
        <div data-testid="product-detail-drawer-header" class="mb-6 flex items-start justify-between gap-4">
          <div>
            <button v-if="!isDrawer" data-testid="product-detail-back" class="mb-5 inline-flex items-center text-sm font-bold text-slate-400 transition hover:text-[#536dff]" type="button" @click="close">返回产品列表</button>
            <p class="text-sm font-bold text-slate-400">商品详情</p>
            <h2 class="mt-1 text-2xl font-black leading-tight text-[#25314d]">{{ product.productName }}</h2>
          </div>
          <button v-if="isDrawer" data-testid="close-product-detail-drawer" class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]" type="button" aria-label="关闭详情" @click="close">
            <X class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div data-testid="product-detail-drawer-summary" class="grid gap-5 min-[1180px]:grid-cols-[260px_minmax(0,1fr)]">
          <section class="rounded-2xl border border-slate-100 bg-slate-50 p-4">
            <div class="mb-3 flex items-center justify-between">
              <p class="text-xs font-black text-slate-400">产品图片</p>
              <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-[#536dff]">{{ product.itemNo }}</span>
            </div>
            <div class="flex aspect-[4/3] items-center justify-center overflow-hidden rounded-2xl bg-white text-[#536dff] shadow-inner shadow-slate-100">
              <img v-if="product.mainImageUrl" :src="product.mainImageUrl" :alt="product.productName" class="h-full w-full object-cover" />
              <PackageOpen v-else class="h-16 w-16" aria-hidden="true" />
            </div>
          </section>

          <section class="rounded-2xl border border-slate-100 p-4">
            <div class="mb-4 flex flex-wrap items-center gap-2">
              <span class="inline-flex rounded-lg bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-500">{{ statusText }}</span>
              <span class="inline-flex rounded-lg bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">{{ auditText }}</span>
            </div>
            <div class="grid grid-cols-2 gap-3 text-sm">
              <section class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">SPU</p><p class="mt-2 font-black text-[#25314d]">{{ product.productCode }}</p></section>
              <section class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">货号</p><p class="mt-2 font-black text-[#25314d]">{{ product.itemNo }}</p></section>
              <section class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">规格</p><p class="mt-2 font-black text-[#25314d]">{{ specificationText }}</p></section>
              <section class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">单价</p><p class="mt-2 font-black text-[#25314d]">{{ priceText }}</p></section>
              <section class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">供应商</p><p class="mt-2 font-black text-[#25314d]">{{ product.defaultSupplierName || '待维护' }}</p></section>
            </div>
          </section>
        </div>

        <section data-testid="product-detail-drawer-sku" class="mt-5 rounded-2xl border border-slate-100 p-4">
          <div class="mb-4 flex items-center justify-between gap-3">
            <h3 class="text-base font-black text-[#25314d]">SKU 信息</h3>
            <span class="shrink-0 rounded-full bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">{{ product.skus.length }} 个 SKU</span>
          </div>
          <div class="space-y-3">
            <article v-for="(sku, index) in product.skus" :key="sku.id ?? sku.skuCode ?? index" data-testid="product-detail-sku-card" class="rounded-2xl border border-slate-100 bg-slate-50 p-3">
              <div class="grid gap-3 sm:grid-cols-[148px_minmax(0,1fr)]">
                <div data-testid="product-detail-sku-image" class="rounded-xl bg-white p-2.5">
                  <p class="text-[11px] font-black text-slate-400">SKU 图片</p>
                  <div class="mt-2 flex aspect-square items-center justify-center overflow-hidden rounded-xl bg-slate-50 text-[#36bee3]">
                    <PackageOpen class="h-10 w-10" aria-hidden="true" />
                  </div>
                  <p class="mt-2 text-center text-[11px] font-bold text-slate-400">{{ sku.skuImageFileId ? '已上传 SKU 图' : '未上传 SKU 图' }}</p>
                </div>
                <div data-testid="product-detail-sku-fields" class="grid gap-2 sm:grid-cols-2">
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">SKU 编码</p><p class="mt-1.5 break-all text-sm font-black leading-5 tracking-tight text-[#25314d]">{{ sku.skuCode || '待生成 SKU' }}</p></div>
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">货号</p><p class="mt-1.5 break-all text-sm font-black leading-5 text-[#25314d]">{{ sku.barcode || product.itemNo || '-' }}</p></div>
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">SKU 名称</p><p class="mt-1.5 break-words text-sm font-black leading-5 text-[#25314d]">{{ sku.skuName || '默认规格' }}</p></div>
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">规格</p><p class="mt-1.5 break-words text-sm font-black leading-5 text-[#25314d]">{{ skuSpecificationText(sku.specificationValues) }}</p></div>
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">售价</p><p class="mt-1.5 text-sm font-black leading-5 text-[#25314d]">{{ sku.defaultSalePrice === null || sku.defaultSalePrice === undefined ? '-' : `¥${Number(sku.defaultSalePrice).toFixed(2)}` }}</p></div>
                  <div class="rounded-xl bg-white p-2.5"><p class="text-[11px] font-bold text-slate-400">标准成本</p><p class="mt-1.5 text-sm font-black leading-5 text-[#25314d]">{{ sku.standardCost === null || sku.standardCost === undefined ? '-' : `¥${Number(sku.standardCost).toFixed(2)}` }}</p></div>
                </div>
              </div>
            </article>
            <p v-if="product.skus.length === 0" class="rounded-xl bg-slate-50 p-4 text-sm font-bold text-slate-400">暂无 SKU</p>
          </div>
        </section>

        <section data-testid="product-detail-drawer-packaging" class="mt-5 rounded-2xl border border-slate-100 p-4">
          <h3 class="mb-4 text-base font-black text-[#25314d]">包装尺寸</h3>
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">外箱尺寸（长、宽、高）</p><p class="mt-2 font-black text-[#25314d]">{{ outerBoxSize }}</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">包装方式</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.packagingMethod || '-' }}</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">装箱数</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.cartonQuantity ?? '-' }} 件/箱</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">体积</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.packageVolumeCm3 ? `${Number(primarySku.packageVolumeCm3 / 1000000).toFixed(3)} 立方` : '-' }}</p></div>
          </div>
        </section>

        <section data-testid="product-detail-drawer-weight" class="mt-5 rounded-2xl border border-slate-100 p-4">
          <h3 class="mb-4 text-base font-black text-[#25314d]">重量与条码</h3>
          <div class="grid gap-3 sm:grid-cols-2 min-[1180px]:grid-cols-4">
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">条码</p><p class="mt-2 truncate font-black text-[#25314d]">{{ primarySku?.barcode || '-' }}</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">克重</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.gramWeightG ?? '-' }} g</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">净重</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.netWeightKg ?? '-' }} kg</p></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-bold text-slate-400">毛重</p><p class="mt-2 font-black text-[#25314d]">{{ primarySku?.grossWeightKg ?? '-' }} kg</p></div>
          </div>
        </section>

        <section data-testid="product-detail-drawer-images" class="mt-5 rounded-2xl border border-slate-100 p-4">
          <h3 class="mb-4 text-base font-black text-[#25314d]">图片资料</h3>
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-black text-slate-400">产品主图</p><div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3"><PackageOpen class="h-7 w-7 text-[#536dff]" aria-hidden="true" /><span class="truncate text-sm font-black text-[#25314d]">{{ product.mainImageFileId ? '已上传产品主图' : '未上传产品主图' }}</span></div></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-black text-slate-400">SKU 图片</p><div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3"><PackageOpen class="h-7 w-7 text-[#36bee3]" aria-hidden="true" /><span class="truncate text-sm font-black text-[#25314d]">{{ primarySku?.skuImageFileId ? '已上传 SKU 图' : '未上传 SKU 图' }}</span></div></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-black text-slate-400">彩盒图片</p><div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3"><PackageOpen class="h-7 w-7 text-[#7b45f5]" aria-hidden="true" /><span class="truncate text-sm font-black text-[#25314d]">{{ primarySku?.packageImageFileId ? '已上传彩盒图' : '未上传彩盒图' }}</span></div></div>
            <div class="rounded-xl bg-slate-50 p-3"><p class="text-xs font-black text-slate-400">外箱图片</p><div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3"><PackageOpen class="h-7 w-7 text-amber-500" aria-hidden="true" /><span class="truncate text-sm font-black text-[#25314d]">{{ primarySku?.cartonImageFileId ? '已上传外箱图' : '未上传外箱图' }}</span></div></div>
          </div>
        </section>

        <section data-testid="product-detail-drawer-completion" class="mt-5 rounded-2xl border border-slate-100 p-4">
          <div class="mb-5 flex items-center justify-between"><h3 class="text-base font-black text-[#25314d]">资料完整度</h3><span class="rounded-full bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">{{ completionRate }}%</span></div>
          <div class="space-y-4"><section v-for="segment in completionSegments" :key="segment.label"><div class="mb-2 flex items-center justify-between text-xs font-black"><span class="text-slate-500">{{ segment.label }}</span><span class="text-slate-400">{{ segment.value }}%</span></div><div class="h-2 overflow-hidden rounded-full bg-slate-100"><span class="block h-full rounded-full" :class="segment.color" :style="{ width: `${segment.value}%` }"></span></div></section></div>
        </section>

        <div class="mt-6 grid grid-cols-2 gap-3">
          <button data-testid="sync-product-channel" class="h-11 rounded-xl border border-slate-200 text-sm font-black text-slate-600 transition hover:bg-slate-50" type="button">同步渠道</button>
          <button data-testid="edit-product-detail" class="h-11 rounded-xl bg-[#536dff] text-sm font-black text-white shadow-lg shadow-blue-200" type="button" @click="emit('edit')">编辑资料</button>
        </div>
      </component>
    </div>
  </Transition>
</template>

<style scoped>
.product-drawer-enter-active,
.product-drawer-leave-active {
  transition: opacity 180ms ease;
}

.product-drawer-enter-from,
.product-drawer-leave-to {
  opacity: 0;
}

.product-drawer-enter-active .product-drawer-panel,
.product-drawer-leave-active .product-drawer-panel {
  transition: transform 220ms ease;
}

.product-drawer-enter-from .product-drawer-panel,
.product-drawer-leave-to .product-drawer-panel {
  transform: translateX(28px);
}
</style>
