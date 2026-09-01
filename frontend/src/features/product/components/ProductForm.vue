<script setup lang="ts">
import { ArrowLeft, Check } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { message } from '../../../components/feedback/message';
import ImageHoverPreview from '../../../components/media/ImageHoverPreview.vue';
import { toProductFormPayload } from '../productFormMapper';
import type { Product, ProductFormPayload, ProductService, ProductSpecification, SkuForm } from '../types';

const props = defineProps<{ service: ProductService; initialValue?: Product; categories?: Array<{ id: number; categoryName: string }> }>();
const emit = defineEmits<{ saved: [] ; cancel: [] }>();

type SpecificationDraft = { name: string; valuesText: string };
type SkuImageField = 'skuImageFileId';
type PackagingImageField = 'packageImageFileId' | 'cartonImageFileId';
type PackagingForm = Pick<SkuForm, 'packageLengthCm' | 'packageWidthCm' | 'packageHeightCm' | 'packageVolumeCm3' | 'netWeightKg' | 'grossWeightKg' | 'gramWeightG' | 'packagingMethod' | 'cartonQuantity' | 'packageImageFileId' | 'cartonImageFileId'>;
type ProgressStatus = 'complete' | 'current' | 'pending';
type ImageStatus = 'idle' | 'uploading' | 'uploaded' | 'error';

const dimensionNames = ['口径', '高度', '容量', '重量'] as const;
type DimensionName = typeof dimensionNames[number];

function blankSku(values: string[] = []): SkuForm {
  return {
    skuCode: '', barcode: '', skuName: values.join(' / '), specificationValues: values,
    salesUnit: '只', defaultSalePrice: null, standardCost: null, safetyStockQuantity: null,
    packageLengthCm: null, packageWidthCm: null, packageHeightCm: null, packageVolumeCm3: null,
    innerPackageLengthCm: null, innerPackageWidthCm: null, innerPackageHeightCm: null,
    netWeightKg: null, grossWeightKg: null, gramWeightG: null, packagingMethod: '', cartonQuantity: null,
    innerPackageWeightKg: null,
    skuImageFileId: null, packageImageFileId: null, cartonImageFileId: null,
  };
}

function packagingFromSku(sku?: SkuForm): PackagingForm {
  return {
    packageLengthCm: sku?.packageLengthCm ?? null,
    packageWidthCm: sku?.packageWidthCm ?? null,
    packageHeightCm: sku?.packageHeightCm ?? null,
    packageVolumeCm3: sku?.packageVolumeCm3 ?? null,
    netWeightKg: sku?.netWeightKg ?? null,
    grossWeightKg: sku?.grossWeightKg ?? null,
    gramWeightG: sku?.gramWeightG ?? null,
    packagingMethod: sku?.packagingMethod ?? '',
    cartonQuantity: sku?.cartonQuantity ?? null,
    packageImageFileId: sku?.packageImageFileId ?? null,
    cartonImageFileId: sku?.cartonImageFileId ?? null
  };
}

const initialPayload = props.initialValue ? toProductFormPayload(props.initialValue) : undefined;
const itemNo = ref(initialPayload?.itemNo ?? '');
const productName = ref(initialPayload?.productName ?? '');
const categoryId = ref<number | null>(initialPayload?.categoryId ?? null);
const brand = ref(initialPayload?.brand ?? '');
const remark = ref(initialPayload?.remark ?? '');
const initialSpecifications = initialPayload?.specifications ?? [];
const dimensionParameters = ref<SpecificationDraft[]>(dimensionNames.map((name) => ({
  name,
  valuesText: initialSpecifications.find((specification) => specification.name === name)?.values.join(' / ') ?? ''
})));
const skuList = ref<SkuForm[]>(initialPayload?.skus ?? [blankSku()]);
const defaultResponseSku = props.initialValue?.skus.find((sku) => sku.defaultSku) ?? props.initialValue?.skus[0];
const packaging = ref<PackagingForm>(packagingFromSku(initialPayload?.skus[0]));
const mainImageFileId = ref<number | null>(initialPayload?.mainImageFileId ?? null);
function initialImagePreviews() {
  const previews: Record<string, string> = {};
  if (props.initialValue?.mainImageUrl) previews.main = props.initialValue.mainImageUrl;
  props.initialValue?.skus?.forEach((sku, index) => {
    if (sku.skuImageUrl) previews[`sku-${index}-skuImageFileId`] = sku.skuImageUrl;
  });
  if (defaultResponseSku?.packageImageUrl) previews['packaging-packageImageFileId'] = defaultResponseSku.packageImageUrl;
  if (defaultResponseSku?.cartonImageUrl) previews['packaging-cartonImageFileId'] = defaultResponseSku.cartonImageUrl;
  return previews;
}

const imagePreviews = ref<Record<string, string>>(initialImagePreviews());
const imageStatuses = ref<Record<string, ImageStatus>>(Object.fromEntries(
  Object.keys(imagePreviews.value).map((slot) => [slot, 'uploaded'])
));
const imageErrors = ref<Record<string, string>>({});
const saving = ref(false);
const formTitle = computed(() => props.initialValue ? '编辑产品' : '新增产品');
const formSteps = [
  { id: 'basic', title: '基础信息', target: 'product-form-section-basic' },
  { id: 'pricing', title: '渠道价格', target: 'product-form-section-pricing' },
  { id: 'packaging', title: '规格包装', target: 'product-form-section-packaging' },
  { id: 'images', title: '图片资料', target: 'product-form-section-images' }
] as const;

const normalizedDimensions = computed<ProductSpecification[]>(() => dimensionParameters.value
  .map((specification) => ({ name: specification.name, values: specification.valuesText.split('/').map((value) => value.trim()).filter(Boolean) }))
  .filter((specification) => specification.values.length > 0));

function isFilled(value: unknown) {
  return value !== null && value !== undefined && String(value).trim() !== '';
}

function progressFor(values: unknown[]) {
  if (values.length === 0) return 0;
  return Math.round(values.filter(isFilled).length / values.length * 100);
}

const progressSteps = computed(() => {
  const pricingValues = skuList.value.flatMap((sku) => [sku.defaultSalePrice, sku.standardCost]);
  const packagingValues = [
    ...dimensionParameters.value.map((parameter) => parameter.valuesText),
    packaging.value.packageLengthCm, packaging.value.packageWidthCm, packaging.value.packageHeightCm,
    packaging.value.cartonQuantity, packaging.value.netWeightKg, packaging.value.grossWeightKg,
    packaging.value.packagingMethod, packaging.value.packageImageFileId, packaging.value.cartonImageFileId
  ];
  const imageValues = [
    mainImageFileId.value,
    packaging.value.packageImageFileId, packaging.value.cartonImageFileId,
    ...skuList.value.map((sku) => sku.skuImageFileId)
  ];
  const steps = [
    { ...formSteps[0], progress: progressFor([itemNo.value, productName.value, categoryId.value, brand.value]) },
    { ...formSteps[1], progress: progressFor(pricingValues) },
    { ...formSteps[2], progress: progressFor(packagingValues) },
    { ...formSteps[3], progress: progressFor(imageValues) }
  ];
  const currentIndex = steps.findIndex((step) => step.progress < 100);
  return steps.map((step, index) => {
    const status: ProgressStatus = step.progress === 100 ? 'complete' : index === currentIndex ? 'current' : 'pending';
    return { ...step, status };
  });
});

function statusLabel(status: 'complete' | 'current' | 'pending') {
  return status === 'complete' ? '已完成' : status === 'current' ? '当前填写' : '待完善';
}

function summaryStatusLabel(status: 'complete' | 'current' | 'pending') {
  return status === 'complete' ? '已完成' : '待完善';
}

function scrollToStep(target: string) {
  const element = document.querySelector(`[data-testid="${target}"]`);
  element?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
}

function addSku() {
  skuList.value.push(blankSku());
}

function removeSku(index: number) {
  if (skuList.value.length <= 1) return;
  skuList.value.splice(index, 1);
}

function skuSpecificationText(sku: SkuForm) {
  return sku.specificationValues.join(' / ').trim();
}

function updateSkuSpecification(event: Event, index: number) {
  const sku = skuList.value[index];
  if (!sku) return;
  const value = (event.target as HTMLInputElement).value.trim();
  sku.specificationValues = value.split('/').map((item) => item.trim()).filter(Boolean);
}

function previewFor(slot: string) {
  return imagePreviews.value[slot] ?? '';
}

function statusFor(slot: string): ImageStatus {
  return imageStatuses.value[slot] ?? 'idle';
}

function statusLabelFor(slot: string) {
  const status = statusFor(slot);
  return status === 'uploading' ? '上传中...' : status === 'uploaded' ? '上传成功' : status === 'error' ? '上传失败' : '未上传';
}

function statusClassFor(slot: string) {
  const status = statusFor(slot);
  return status === 'uploaded' ? 'text-emerald-600' : status === 'error' ? 'text-rose-500' : status === 'uploading' ? 'text-[#536dff]' : 'text-slate-400';
}

function markImageError(slot: string) {
  imageStatuses.value = { ...imageStatuses.value, [slot]: 'error' };
  imageErrors.value = { ...imageErrors.value, [slot]: '图片地址无法访问，请重新上传' };
}

function revokePreview(url: string) {
  if (url.startsWith('blob:') && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url);
}

function removeImage(slot: string, clearFileId: () => void) {
  const currentPreview = previewFor(slot);
  revokePreview(currentPreview);
  clearFileId();
  const previews = { ...imagePreviews.value };
  delete previews[slot];
  imagePreviews.value = previews;
  imageStatuses.value = { ...imageStatuses.value, [slot]: 'idle' };
  const errors = { ...imageErrors.value };
  delete errors[slot];
  imageErrors.value = errors;
}

async function uploadImage(event: Event, slot: string, assignFileId: (fileId: number | null) => void) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const previousPreview = previewFor(slot);
  const localPreview = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : '';
  if (localPreview) imagePreviews.value = { ...imagePreviews.value, [slot]: localPreview };
  imageStatuses.value = { ...imageStatuses.value, [slot]: 'uploading' };
  imageErrors.value = { ...imageErrors.value, [slot]: '' };
  try {
    const uploaded = await props.service.uploadImage(file);
    assignFileId(uploaded.id);
    if (uploaded.url) {
      revokePreview(localPreview);
      imagePreviews.value = { ...imagePreviews.value, [slot]: uploaded.url };
    }
    imageStatuses.value = { ...imageStatuses.value, [slot]: 'uploaded' };
  } catch (error) {
    revokePreview(localPreview);
    const previews = { ...imagePreviews.value };
    if (previousPreview) previews[slot] = previousPreview;
    else delete previews[slot];
    imagePreviews.value = previews;
    imageStatuses.value = { ...imageStatuses.value, [slot]: 'error' };
    imageErrors.value = { ...imageErrors.value, [slot]: error instanceof Error ? error.message : '图片上传失败' };
    message.error(error instanceof Error ? error.message : '图片上传失败');
  } finally {
    input.value = '';
  }
}

function uploadMainImage(event: Event) {
  return uploadImage(event, 'main', (fileId) => { mainImageFileId.value = fileId; });
}

function uploadSkuImage(event: Event, index: number, field: SkuImageField) {
  const sku = skuList.value[index];
  if (!sku) return Promise.resolve();
  return uploadImage(event, `sku-${index}-${field}`, (fileId) => { sku[field] = fileId; });
}

function uploadPackagingImage(event: Event, field: PackagingImageField) {
  return uploadImage(event, `packaging-${field}`, (fileId) => { packaging.value[field] = fileId; });
}

function payload(): ProductFormPayload {
  const specificationValues = [...new Set(skuList.value.map(skuSpecificationText).filter(Boolean))];
  const isVariant = skuList.value.length > 1 || specificationValues.length > 0;
  const packagingOwnerIndex = 0;
  const skus = skuList.value.map((sku, index) => ({
    ...sku,
    specificationValues: isVariant ? (skuSpecificationText(sku) ? [skuSpecificationText(sku)] : []) : [],
    packageLengthCm: packagingOwnerIndex === index ? packaging.value.packageLengthCm : null,
    packageWidthCm: packagingOwnerIndex === index ? packaging.value.packageWidthCm : null,
    packageHeightCm: packagingOwnerIndex === index ? packaging.value.packageHeightCm : null,
    packageVolumeCm3: packagingOwnerIndex === index ? packaging.value.packageVolumeCm3 : null,
    netWeightKg: packagingOwnerIndex === index ? packaging.value.netWeightKg : null,
    grossWeightKg: packagingOwnerIndex === index ? packaging.value.grossWeightKg : null,
    gramWeightG: packagingOwnerIndex === index ? packaging.value.gramWeightG : null,
    packagingMethod: packagingOwnerIndex === index ? packaging.value.packagingMethod : '',
    cartonQuantity: packagingOwnerIndex === index ? packaging.value.cartonQuantity : null,
    packageImageFileId: packagingOwnerIndex === index ? packaging.value.packageImageFileId : null,
    cartonImageFileId: packagingOwnerIndex === index ? packaging.value.cartonImageFileId : null
  }));
  return {
    itemNo: itemNo.value, productName: productName.value, categoryId: categoryId.value,
    brand: brand.value, productType: isVariant ? 'variant' : 'simple', mainImageFileId: mainImageFileId.value, remark: remark.value,
    specifications: [...normalizedDimensions.value, ...(specificationValues.length > 0 ? [{ name: '规格', values: specificationValues }] : [])], skus
  };
}

function hasInvalidSkuSpecifications() {
  if (skuList.value.length <= 1) return false;
  const values = skuList.value.map(skuSpecificationText);
  if (values.every((value) => !value)) return false;
  return values.some((value) => !value) || new Set(values).size !== values.length;
}

function hasInvalidWeight() {
  return packaging.value.netWeightKg !== null
    && packaging.value.grossWeightKg !== null
    && packaging.value.grossWeightKg < packaging.value.netWeightKg;
}

async function save() {
  if (hasInvalidWeight()) {
    message.error('毛重不能小于净重');
    return;
  }
  if (hasInvalidSkuSpecifications()) {
    message.error('请填写每个 SKU 的规格信息，且规格不能重复');
    return;
  }
  saving.value = true;
  try {
    if (props.initialValue?.id) {
      await props.service.updateProduct(props.initialValue.id, payload());
    } else {
      await props.service.createProduct(payload());
    }
    message.success(props.initialValue ? '产品已更新' : '产品已保存');
    emit('saved');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存产品失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <form class="space-y-6" @submit.prevent="save">
    <section data-testid="product-form-header">
      <div>
        <button data-testid="product-form-back" type="button" class="mb-3 inline-flex items-center gap-2 text-sm font-bold text-slate-400 transition hover:text-[#536dff]" @click="$emit('cancel')">
          <ArrowLeft class="h-4 w-4" aria-hidden="true" />
          返回产品列表
        </button>
        <p class="text-[11px] font-black uppercase tracking-[0.18em] text-[#536dff]">MASTER DATA / PRODUCTS</p>
        <h1 class="mt-2 text-2xl font-black tracking-tight text-[#25314d]">{{ formTitle }}</h1>
        <p class="mt-2 text-sm font-medium text-slate-400">产品信息较多，使用独立页面录入，便于完整维护档案。</p>
      </div>
    </section>

    <section data-testid="product-form-steps" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <button v-for="(step, index) in progressSteps" :key="step.id" :data-testid="`progress-step-${step.id}`" :aria-controls="step.target" :aria-current="step.status === 'current' ? 'step' : undefined" type="button" class="flex min-h-16 items-center gap-3 rounded-2xl border bg-white px-4 py-3 text-left shadow-[0_10px_30px_rgba(31,45,74,0.03)] transition hover:-translate-y-0.5" :class="step.status === 'complete' ? 'border-emerald-100 hover:border-emerald-200' : step.status === 'current' ? 'border-[#536dff]/30 hover:border-[#536dff]' : 'border-slate-100 hover:border-slate-200'" @click="scrollToStep(step.target)">
        <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-sm font-black" :class="step.status === 'complete' ? 'bg-emerald-500 text-white' : step.status === 'current' ? 'bg-[#536dff] text-white' : 'bg-slate-50 text-slate-400'"><Check v-if="step.status === 'complete'" class="h-4 w-4" aria-hidden="true" /><span v-else>{{ index + 1 }}</span></span>
        <span>
          <span class="block text-sm font-black text-[#25314d]">{{ step.title }}</span>
          <span class="mt-0.5 block text-xs font-bold" :class="step.status === 'complete' ? 'text-emerald-500' : step.status === 'current' ? 'text-[#536dff]' : 'text-slate-400'">{{ statusLabel(step.status) }}</span>
        </span>
      </button>
    </section>

    <div data-testid="product-form-shell" class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
      <section data-testid="product-form-main" class="min-w-0 rounded-[22px] border border-slate-200 bg-white px-6 py-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
        <div class="space-y-8">
          <section data-testid="product-form-section-basic" class="border-b border-slate-100 pb-7">
            <div class="mb-5">
              <h2 class="text-lg font-black text-[#25314d]">基础信息</h2>
              <p class="mt-1 text-sm font-medium text-slate-400">先确定商品身份，后续库存、销售和平台资料共用同一套产品信息。</p>
            </div>
            <div class="grid gap-4 md:grid-cols-2">
              <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">货号</span><input v-model="itemNo" required class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" placeholder="例如：EW43249" /></label>
              <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">产品名称</span><input data-testid="product-name" v-model="productName" required class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" placeholder="请输入产品名称" /></label>
              <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">分类</span><select data-testid="product-category" v-model="categoryId" required class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"><option :value="null" disabled>请选择分类</option><option v-for="category in categories ?? []" :key="category.id" :value="category.id">{{ category.categoryName }}</option></select></label>
              <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">品牌</span><input v-model="brand" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" placeholder="请输入品牌" /></label>
            <div data-testid="product-form-section-images" class="mt-5">
                <span class="mb-2 block text-xs font-black text-slate-400">产品实拍图</span>
                <div class="max-w-xl rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 px-4 py-4 transition hover:border-[#536dff] hover:bg-blue-50">
                  <input id="product-main-image-input" data-testid="product-main-image" class="sr-only" type="file" accept="image/*" @change="uploadMainImage" />
                  <label for="product-main-image-input" class="group flex min-h-[80px] cursor-pointer items-center gap-4">
                    <ImageHoverPreview v-if="previewFor('main')" :src="previewFor('main')" alt="产品实拍图预览" test-id="product-main-image-preview" image-class="h-20 w-20 shrink-0 rounded-xl object-cover" @error="markImageError('main')" />
                    <span v-else class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-white text-2xl font-light text-[#536dff] shadow-sm shadow-blue-100">+</span>
                    <span class="min-w-0">
                      <span class="block text-sm font-black text-[#25314d]">点击上传产品图片</span>
                      <span class="mt-1 block text-xs font-bold text-slate-400">建议上传清晰的产品实拍图，支持 JPG / PNG</span>
                    </span>
                  </label>
                  <div class="mt-2 flex items-center justify-between gap-3 text-xs font-bold">
                    <span data-testid="product-main-image-status" :class="statusClassFor('main')">{{ statusLabelFor('main') }}</span>
                    <button v-if="previewFor('main')" data-testid="product-main-image-delete" type="button" class="text-rose-500 transition hover:text-rose-700" @click="removeImage('main', () => { mainImageFileId = null; })">删除图片</button>
                  </div>
                  <p v-if="imageErrors.main" class="mt-1 text-xs font-bold text-rose-500">{{ imageErrors.main }}</p>
                </div>
              </div>
            </div>
              <div data-testid="product-dimensions" class="mt-6 border-t border-slate-100 pt-6">
                <div class="mb-4">
                  <h3 class="text-base font-black text-[#25314d]">产品尺寸参数</h3>
                  <p class="mt-1 text-xs font-medium text-slate-400">维护产品本体尺寸、容量和重量参数，用于商品资料和平台展示。</p>
                </div>
                <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">口径</span><input data-testid="product-dimension-diameter" v-model="dimensionParameters[0].valuesText" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：70±1mm" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">高度</span><input data-testid="product-dimension-height" v-model="dimensionParameters[1].valuesText" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：83.5±1mm" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">容量</span><input data-testid="product-dimension-capacity" v-model="dimensionParameters[2].valuesText" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：210ml" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">重量</span><input data-testid="product-dimension-weight" v-model="dimensionParameters[3].valuesText" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：200±12g" /></label>
                </div>
              </div>
          </section>

          <section data-testid="product-form-section-pricing" class="border-b border-slate-100 pb-7">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 class="text-lg font-black text-[#25314d]">规格与价格</h2>
                <p class="mt-1 text-sm font-medium text-slate-400">通过添加 SKU 维护货号、名称、规格、售价和 SKU 图。</p>
              </div>
              <button data-testid="add-sku" type="button" class="h-10 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-lg shadow-blue-100 transition hover:bg-[#435be0]" @click="addSku">添加 SKU</button>
            </div>
            <div class="mt-5 space-y-3">
              <article v-for="(sku, index) in skuList" :key="sku.id ?? `sku-${index}`" data-testid="sku-editor" class="rounded-2xl border border-slate-200 bg-slate-50/40 p-4">
                <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div class="font-black text-[#25314d]">{{ skuSpecificationText(sku) || `SKU ${index + 1}` }}</div>
                    <span v-if="index === 0" data-testid="default-sku-badge" class="mt-1 inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-black text-emerald-700">默认 SKU</span>
                  </div>
                  <button v-if="skuList.length > 1" :data-testid="`remove-sku-${index}`" type="button" class="h-9 rounded-xl border border-slate-200 bg-white px-3 text-xs font-black text-slate-500 transition hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600" @click="removeSku(index)">删除 SKU</button>
                </div>
                <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">SKU 编码</span><div :data-testid="`sku-code-${index}`" class="flex h-11 items-center rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm font-bold text-slate-400">保存后自动生成</div></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">货号</span><input :data-testid="`sku-item-no-${index}`" v-model="sku.barcode" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="请输入 SKU 货号" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">SKU 名称</span><input :data-testid="`sku-name-${index}`" v-model="sku.skuName" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="请输入 SKU 名称" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">售价</span><input :data-testid="`sku-sale-price-${index}`" v-model.number="sku.defaultSalePrice" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="0.00" /></label>
                  <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">标准成本</span><input :data-testid="`sku-standard-cost-${index}`" v-model.number="sku.standardCost" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="0.00" /></label>
                </div>
                <label class="mt-4 block min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">规格信息</span><input :data-testid="`sku-specification-${index}`" :value="skuSpecificationText(sku)" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：透明 / 竖纹" @input="updateSkuSpecification($event, index)" /></label>
                <div class="mt-4 grid gap-3 md:grid-cols-3">
                  <div class="group min-w-0 rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 p-3 transition hover:border-[#536dff] hover:bg-blue-50">
                    <span class="block text-xs font-black text-slate-400">SKU 图</span>
                    <input :id="`sku-image-input-${index}`" :data-testid="`sku-image-${index}`" class="sr-only" type="file" accept="image/*" @change="uploadSkuImage($event, index, 'skuImageFileId')" />
                    <label :for="`sku-image-input-${index}`" class="mt-2 flex min-h-20 cursor-pointer items-center gap-3"><ImageHoverPreview v-if="previewFor(`sku-${index}-skuImageFileId`)" :src="previewFor(`sku-${index}-skuImageFileId`)" alt="SKU 图预览" :test-id="`sku-image-${index}-preview`" image-class="h-16 w-16 shrink-0 rounded-xl object-cover" @error="markImageError(`sku-${index}-skuImageFileId`)" /><span v-else class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-xl font-light text-[#536dff]">+</span><span class="text-xs font-bold text-slate-400">上传 SKU 图</span></label>
                    <div class="mt-2 flex items-center justify-between gap-2 text-xs font-bold"><span :data-testid="`sku-image-${index}-status`" :class="statusClassFor(`sku-${index}-skuImageFileId`)">{{ statusLabelFor(`sku-${index}-skuImageFileId`) }}</span><button v-if="previewFor(`sku-${index}-skuImageFileId`)" :data-testid="`sku-image-${index}-delete`" type="button" class="text-rose-500 transition hover:text-rose-700" @click="removeImage(`sku-${index}-skuImageFileId`, () => { sku.skuImageFileId = null; })">删除</button></div>
                    <p v-if="imageErrors[`sku-${index}-skuImageFileId`]" class="mt-1 text-xs font-bold text-rose-500">{{ imageErrors[`sku-${index}-skuImageFileId`] }}</p>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <section data-testid="product-form-section-packaging">
            <div class="mb-5">
              <h2 class="text-lg font-black text-[#25314d]">规格包装</h2>
              <p class="mt-1 text-sm font-medium text-slate-400">统一维护产品包装尺寸、重量、装箱数、包装方式和包装图片，所有 SKU 共用。</p>
            </div>
            <div data-testid="packaging-editor">
              <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">包装长 (cm)</span><input v-model.number="packaging.packageLengthCm" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="长" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">包装宽 (cm)</span><input v-model.number="packaging.packageWidthCm" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="宽" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">包装高 (cm)</span><input v-model.number="packaging.packageHeightCm" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="高" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">装箱数</span><input v-model.number="packaging.cartonQuantity" type="number" min="1" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="每箱数量" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">净重 (kg)</span><input data-testid="net-weight" v-model.number="packaging.netWeightKg" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="0.00" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">毛重 (kg)</span><input data-testid="gross-weight" v-model.number="packaging.grossWeightKg" type="number" min="0" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="0.00" /></label>
                <label class="min-w-0 space-y-2 text-sm"><span class="block text-xs font-black text-slate-400">包装方式</span><input v-model="packaging.packagingMethod" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 font-bold outline-none focus:border-[#536dff]" placeholder="例如：普盒/1*12*4/48*" /></label>
              </div>
              <div class="mt-4 grid gap-3 md:grid-cols-2">
                <div class="group min-w-0 rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 p-3 transition hover:border-[#536dff] hover:bg-blue-50">
                  <span class="block text-xs font-black text-slate-400">彩盒图</span>
                  <input id="package-image-input" data-testid="package-image" class="sr-only" type="file" accept="image/*" @change="uploadPackagingImage($event, 'packageImageFileId')" />
                  <label for="package-image-input" class="mt-2 flex min-h-20 cursor-pointer items-center gap-3"><ImageHoverPreview v-if="previewFor('packaging-packageImageFileId')" :src="previewFor('packaging-packageImageFileId')" alt="彩盒图预览" test-id="package-image-preview" image-class="h-16 w-16 shrink-0 rounded-xl object-cover" @error="markImageError('packaging-packageImageFileId')" /><span v-else class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-xl font-light text-[#536dff]">+</span><span class="text-xs font-bold text-slate-400">上传彩盒图</span></label>
                  <div class="mt-2 flex items-center justify-between gap-2 text-xs font-bold"><span data-testid="package-image-status" :class="statusClassFor('packaging-packageImageFileId')">{{ statusLabelFor('packaging-packageImageFileId') }}</span><button v-if="previewFor('packaging-packageImageFileId')" data-testid="package-image-delete" type="button" class="text-rose-500 transition hover:text-rose-700" @click="removeImage('packaging-packageImageFileId', () => { packaging.packageImageFileId = null; })">删除</button></div>
                  <p v-if="imageErrors['packaging-packageImageFileId']" class="mt-1 text-xs font-bold text-rose-500">{{ imageErrors['packaging-packageImageFileId'] }}</p>
                </div>
                <div class="group min-w-0 rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 p-3 transition hover:border-[#536dff] hover:bg-blue-50">
                  <span class="block text-xs font-black text-slate-400">外箱图</span>
                  <input id="carton-image-input" data-testid="carton-image" class="sr-only" type="file" accept="image/*" @change="uploadPackagingImage($event, 'cartonImageFileId')" />
                  <label for="carton-image-input" class="mt-2 flex min-h-20 cursor-pointer items-center gap-3"><ImageHoverPreview v-if="previewFor('packaging-cartonImageFileId')" :src="previewFor('packaging-cartonImageFileId')" alt="外箱图预览" test-id="carton-image-preview" image-class="h-16 w-16 shrink-0 rounded-xl object-cover" @error="markImageError('packaging-cartonImageFileId')" /><span v-else class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-xl font-light text-[#536dff]">+</span><span class="text-xs font-bold text-slate-400">上传外箱图</span></label>
                  <div class="mt-2 flex items-center justify-between gap-2 text-xs font-bold"><span data-testid="carton-image-status" :class="statusClassFor('packaging-cartonImageFileId')">{{ statusLabelFor('packaging-cartonImageFileId') }}</span><button v-if="previewFor('packaging-cartonImageFileId')" data-testid="carton-image-delete" type="button" class="text-rose-500 transition hover:text-rose-700" @click="removeImage('packaging-cartonImageFileId', () => { packaging.cartonImageFileId = null; })">删除</button></div>
                  <p v-if="imageErrors['packaging-cartonImageFileId']" class="mt-1 text-xs font-bold text-rose-500">{{ imageErrors['packaging-cartonImageFileId'] }}</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </section>

      <aside data-testid="product-form-sidebar" class="h-fit rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-lg font-black text-[#25314d]">资料预检</h2>
          <span class="rounded-full bg-amber-50 px-3 py-1 text-xs font-black text-amber-500">草稿</span>
        </div>
        <div class="space-y-4">
          <div class="rounded-2xl border border-dashed border-blue-200 bg-blue-50/50 p-4">
            <p class="text-sm font-black text-[#25314d]">商品资料完整度</p>
            <p class="mt-1 text-xs font-bold leading-5 text-slate-400">基础信息、SKU 和包装资料会在保存时一起提交。</p>
          </div>
          <div class="space-y-4 text-sm font-bold text-slate-500">
            <div v-for="step in progressSteps" :key="step.id" :data-testid="`progress-summary-${step.id}`">
              <div class="flex items-center justify-between"><span>{{ step.title }}</span><span :data-testid="`progress-summary-${step.id}-status`" :class="step.status === 'complete' ? 'text-emerald-500' : step.status === 'current' ? 'text-[#536dff]' : 'text-slate-400'">{{ summaryStatusLabel(step.status) }}</span></div>
              <div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-100"><span :data-testid="`progress-summary-${step.id}-bar`" class="block h-full rounded-full transition-[width] duration-300" :class="step.status === 'complete' ? 'bg-emerald-500' : step.status === 'current' ? 'bg-[#536dff]' : 'bg-slate-200'" :style="{ width: `${step.progress}%` }"></span></div>
            </div>
          </div>
        <p class="rounded-xl bg-slate-50 px-3 py-3 text-xs font-bold leading-5 text-slate-400">产品主图、SKU 图、彩盒图和外箱图均可在当前页面上传。</p>
        </div>
      </aside>
    </div>

    <footer class="flex justify-end gap-3 border-t border-slate-200 pt-5">
      <button type="button" class="h-10 rounded-xl border border-slate-200 px-4 text-sm font-black text-slate-500 transition hover:bg-slate-50" @click="$emit('cancel')">取消</button>
      <button data-testid="save-product" type="button" :disabled="saving" class="h-10 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-lg shadow-blue-200 transition hover:bg-[#435be0] disabled:opacity-50" @click="save">{{ saving ? '保存中...' : '保存商品资料' }}</button>
    </footer>
  </form>
</template>
