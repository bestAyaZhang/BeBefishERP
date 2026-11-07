<script setup lang="ts">
import { AlertCircle, Boxes, Check, ChevronLeft, ChevronRight, LoaderCircle, RotateCcw } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter, type RouteLocationNormalized } from 'vue-router';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import { message } from '../../../components/feedback/message';
import { masterdataService } from '../../masterdata/masterdataService';
import type { Category, PageResult, Supplier } from '../../masterdata/types';
import { productService } from '../productService';
import type { Product } from '../types';
import {
  createEditorState,
  productEditorSteps,
  setEditorImageFileId,
  setEditorImagePreview,
  toProductPayload,
  validateStep,
  type ProductEditorState,
  type ProductEditorStep
} from './productEditorState';
import ProductBasicStep from './steps/ProductBasicStep.vue';
import ProductConfirmStep from './steps/ProductConfirmStep.vue';
import ProductImagesStep from './steps/ProductImagesStep.vue';
import ProductPackagingStep from './steps/ProductPackagingStep.vue';
import ProductProcurementStep from './steps/ProductProcurementStep.vue';
import ProductSkuStep from './steps/ProductSkuStep.vue';
import ProductImageUpload from './ProductImageUpload.vue';

const route = useRoute();
const router = useRouter();

const stepDefinitions: Array<{ id: ProductEditorStep; title: string; description: string }> = [
  { id: 'basic', title: '基本资料', description: '维护商品识别信息与销售状态。' },
  { id: 'sku', title: 'SKU 信息', description: '为每个可销售规格维护独立资料。' },
  { id: 'procurement', title: '采购与渠道', description: '维护采购条件与供应商报价。' },
  { id: 'packaging', title: '包装与重量', description: '依次录入产品、外箱和内盒资料。' },
  { id: 'images', title: '图片资料', description: '按资料包维护主图、SKU 图、详情图和详情长图。' },
  { id: 'confirm', title: '确认提交', description: '核对资料完整度后提交商品。' }
];

const state = ref<ProductEditorState>(createEditorState());
const currentStepIndex = ref(0);
const validationErrors = ref<Record<string, string>>({});
const categories = ref<Category[]>([]);
const suppliers = ref<Supplier[]>([]);
const loading = ref(false);
const saving = ref(false);
const mainUploading = ref(false);
const stepUploading = ref(false);
const imageUploading = computed(() => mainUploading.value || stepUploading.value);
const loadError = ref('');
const optionError = ref('');
const createdProduct = ref<Product | null>(null);
const isDraft = ref(false);
const selectedPreviewSkuIndex = ref<number | null>(null);
const stage = ref<HTMLFieldSetElement | null>(null);
const stepHeading = ref<HTMLElement | null>(null);
const initialSnapshot = ref(serializeState(state.value));
let latestProductRequestId = 0;
let latestOptionRequestId = 0;
let latestSaveRequestId = 0;
let routeGeneration = 0;
let componentActive = true;
let saveNavigationAuthorization: {
  requestId: number;
  routeGeneration: number;
  savedProductId: number;
} | null = null;

const currentStep = computed(() => stepDefinitions[currentStepIndex.value] ?? stepDefinitions[0]);
const isEdit = computed(() => route.name === 'product-edit' || state.value.productId !== null);
const selectedSupplierIds = computed(() => new Set(
  state.value.skus.flatMap((sku) => (sku.supplierQuotes ?? []).map((quote) => quote.supplierId))
));
const availableCategories = computed(() => categories.value.filter((category) => (
  category.status === 'enabled' || category.id === state.value.categoryId
)));
const availableSuppliers = computed(() => suppliers.value.filter((supplier) => (
  supplier.status === 'enabled' || selectedSupplierIds.value.has(supplier.id)
)));
const validSupplierIds = computed(() => new Set(suppliers.value.map((supplier) => supplier.id)));
const dirty = computed(() => serializeState(state.value) !== initialSnapshot.value);
const defaultSkuIndex = computed(() => Math.max(0, state.value.skus.findIndex((sku) => sku.defaultSku)));
const previewSkuIndex = computed({
  get: () => selectedPreviewSkuIndex.value !== null && state.value.skus[selectedPreviewSkuIndex.value]
    ? selectedPreviewSkuIndex.value
    : defaultSkuIndex.value,
  set: (index: number) => { selectedPreviewSkuIndex.value = index; }
});
const previewSku = computed(() => state.value.skus[previewSkuIndex.value]);
const categoryName = computed(() => categories.value.find((category) => category.id === state.value.categoryId)?.categoryName ?? '');
const checks = computed(() => {
  const product = state.value;
  const quotes = product.skus.flatMap((sku) => sku.supplierQuotes ?? []);
  const quotedSupplierIds = new Set(quotes.map((quote) => quote.supplierId));
  const supplierNames = availableSuppliers.value.filter((supplier) => quotedSupplierIds.has(supplier.id)).map((supplier) => supplier.supplierName);
  const quoteDetail = quotes.length > 0 ? [...supplierNames, `${quotes.length} 条供应商报价`].join(' · ') : '暂无报价，可稍后补充';
  const packs = product.imagePacks;
  const packImageCount = packs.reduce((count, pack) => count + pack.main.length + pack.sku.length + pack.detail.length + pack.long.length, 0);
  const materialCount = product.materialImages.length;
  const missingPhotos = [
    {
      title: '产品实拍图',
      missing: product.skus.some((sku) => !sku.productPhotoFileId && [
        sku.productMouthDiameterCm, sku.productLengthCm, sku.productWidthCm, sku.productHeightCm,
        sku.capacityMl, sku.gramWeightG
      ].some((value) => value != null))
    },
    {
      title: '外箱图片',
      missing: product.skus.some((sku) => !sku.cartonImageFileId && [
        sku.packageLengthCm, sku.packageWidthCm, sku.packageHeightCm, sku.cartonQuantity,
        sku.packageVolumeCm3, sku.netWeightKg, sku.grossWeightKg
      ].some((value) => value != null))
    },
    {
      title: '内盒包装图',
      missing: product.skus.some((sku) => !sku.packageImageFileId && ([
        sku.innerPackageLengthCm, sku.innerPackageWidthCm, sku.innerPackageHeightCm, sku.innerPackageWeightKg
      ].some((value) => value != null) || Boolean(sku.packagingMethod?.trim())))
    }
  ].filter((photo) => photo.missing).map((photo) => photo.title);
  return [
    { title: stepDefinitions[0].title, detail: categoryName.value || '商品名称、货号与分类', done: !Object.keys(stepErrors('basic')).length },
    { title: stepDefinitions[1].title, detail: `${product.skus.length} 个 SKU`, done: !Object.keys(stepErrors('sku')).length },
    { title: stepDefinitions[2].title, detail: quoteDetail, done: quotes.length > 0 && !Object.keys(stepErrors('procurement')).length },
    {
      title: stepDefinitions[3].title,
      detail: missingPhotos.length ? `待补图 · ${missingPhotos.join('、')}` : product.packagingMode === 'unified' ? '全部 SKU 统一维护' : '按 SKU 单独维护',
      done: product.skus.length > 0 && product.skus.every((sku) => sku.cartonQuantity !== null)
        && missingPhotos.length === 0 && !Object.keys(stepErrors('packaging')).length
    },
    {
      title: stepDefinitions[4].title,
      detail: `${packs.length} 个资料包 · ${packImageCount + materialCount} 张图片`,
      done: Boolean(product.mainImageFileId || packs.some((pack) => pack.main.length > 0))
    }
  ];
});
const completedCheckCount = computed(() => checks.value.filter((check) => check.done).length);

function serializeState(value: ProductEditorState) {
  return JSON.stringify(toProductPayload(value));
}

function positiveInteger(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return null;
  const parsed = Number(raw);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

async function listAll<T extends { id: number }>(
  loader: (query: { page: number; size: number }) => Promise<PageResult<T>>,
  label: string
) {
  const records = new Map<number, T>();
  let expectedTotal = 0;
  let expectedPages = 1;
  for (let page = 1; page <= 100; page += 1) {
    const result = await loader({ page, size: 100 });
    expectedTotal = Math.max(expectedTotal, result.total);
    const responseSize = result.pageSize > 0 ? result.pageSize : 100;
    expectedPages = Math.max(expectedPages, Math.max(1, Math.ceil(result.total / responseSize)), result.page || page);
    for (const record of result.records) if (!records.has(record.id)) records.set(record.id, record);
    if (page >= expectedPages && records.size >= expectedTotal) {
      return [...records.values()].sort((left, right) => left.id - right.id);
    }
    if (result.records.length === 0 && page >= expectedPages) break;
  }
  throw new Error(`${label}数据不完整，请重试`);
}

async function loadOptions() {
  const requestId = ++latestOptionRequestId;
  optionError.value = '';
  const [categoryResult, supplierResult] = await Promise.allSettled([
    listAll((query) => masterdataService.listCategories(query), '分类'),
    listAll((query) => masterdataService.listSuppliers(query), '供应商')
  ]);
  if (requestId !== latestOptionRequestId) return;
  const errors: string[] = [];
  if (categoryResult.status === 'fulfilled') categories.value = categoryResult.value;
  else errors.push(categoryResult.reason instanceof Error ? categoryResult.reason.message : '分类加载失败');
  if (supplierResult.status === 'fulfilled') suppliers.value = supplierResult.value;
  else errors.push(supplierResult.reason instanceof Error ? supplierResult.reason.message : '供应商加载失败');
  optionError.value = [...new Set(errors)].join('；');
}

function setInitialState(product?: Product) {
  state.value = createEditorState(product);
  isDraft.value = product?.status === 'draft';
  currentStepIndex.value = 0;
  validationErrors.value = {};
  mainUploading.value = false;
  stepUploading.value = false;
  selectedPreviewSkuIndex.value = null;
  initialSnapshot.value = serializeState(state.value);
}

async function loadEditor() {
  const requestId = ++latestProductRequestId;
  loadError.value = '';
  if (route.name !== 'product-edit') {
    loading.value = false;
    setInitialState();
    await loadOptions();
    return;
  }

  const productId = positiveInteger(route.params.id);
  if (productId === null) {
    loading.value = false;
    loadError.value = '商品编号无效，请返回商品列表后重试。';
    return;
  }

  loading.value = true;
  const optionsPromise = loadOptions();
  try {
    const product = await productService.getProduct(productId);
    if (requestId !== latestProductRequestId) return;
    setInitialState(product);
  } catch (error) {
    if (requestId !== latestProductRequestId) return;
    loadError.value = error instanceof Error ? error.message : '商品资料加载失败';
  } finally {
    await optionsPromise;
    if (requestId === latestProductRequestId) loading.value = false;
  }
}

function stepErrors(step: ProductEditorStep) {
  return validateStep(state.value, step, {
    validSupplierIds: validSupplierIds.value
  });
}

async function focusFirstError(errors: Record<string, string>) {
  await nextTick();
  const firstField = Object.keys(errors)[0];
  const selector = firstField === 'itemNo'
    ? '[data-testid="product-item-no"]'
    : firstField === 'productName'
      ? '[data-testid="product-name"]'
      : firstField === 'categoryId'
        ? '[data-testid="product-category"]'
        : firstField === 'skus'
          ? '[data-testid="add-sku"]'
          : '[aria-invalid="true"]';
  const field = stage.value?.querySelector<HTMLElement>(selector);
  (field ?? stepHeading.value)?.focus();
}

async function focusStepHeading() {
  await nextTick();
  stepHeading.value?.focus();
}

function firstInvalidStep(targetIndex = productEditorSteps.length - 1, allowDefaultSku = false) {
  for (let index = 0; index <= targetIndex; index += 1) {
    const step = productEditorSteps[index];
    if (!step) continue;
    const errors = stepErrors(step);
    if (allowDefaultSku && step === 'sku' && state.value.productType === 'simple' && state.value.skus.length === 0) {
      delete errors.skus;
    }
    if (Object.keys(errors).length > 0) return { index, errors };
  }
  return null;
}

async function goToStep(index: number) {
  if (index < 0 || index >= stepDefinitions.length) return;
  if (index === currentStepIndex.value || blockActiveOperation()) return;
  if (!isEdit.value && index > currentStepIndex.value) {
    const invalid = firstInvalidStep(index - 1);
    if (invalid) {
      currentStepIndex.value = invalid.index;
      validationErrors.value = invalid.errors;
      await focusFirstError(invalid.errors);
      return;
    }
  }
  currentStepIndex.value = index;
  validationErrors.value = {};
  await focusStepHeading();
}

async function nextStep() {
  if (blockActiveOperation()) return;
  const errors = stepErrors(currentStep.value.id);
  validationErrors.value = errors;
  if (Object.keys(errors).length > 0) {
    await focusFirstError(errors);
    return;
  }
  await goToStep(currentStepIndex.value + 1);
}

function previousStep() {
  void goToStep(currentStepIndex.value - 1);
}

async function submit(asDraft = false) {
  if (imageUploading.value) {
    message.error('图片正在上传，请等待上传完成后再保存');
    return;
  }
  if (saving.value) return;
  const invalid = firstInvalidStep(productEditorSteps.length - 1, asDraft);
  if (invalid) {
    currentStepIndex.value = invalid.index;
    validationErrors.value = invalid.errors;
    await focusFirstError(invalid.errors);
    return;
  }

  const requestId = ++latestSaveRequestId;
  const sourceRouteGeneration = routeGeneration;
  const wasCreate = state.value.productId === null;
  saveNavigationAuthorization = null;
  saving.value = true;
  try {
    const payload = toProductPayload(state.value);
    if (asDraft) payload.status = 'draft';
    const saved = state.value.productId === null
      ? await productService.createProduct(payload)
      : await productService.updateProduct(state.value.productId, payload);
    if (!componentActive || requestId !== latestSaveRequestId || routeGeneration !== sourceRouteGeneration) return;
    if (asDraft) {
      const packagingMode = state.value.packagingMode;
      const intendedStatus = state.value.status;
      state.value = createEditorState(saved);
      state.value.packagingMode = packagingMode;
      state.value.status = intendedStatus;
      isDraft.value = true;
      initialSnapshot.value = serializeState(state.value);
      message.success('草稿已保存，可在商品列表中继续编辑');
      return;
    }
    initialSnapshot.value = serializeState(state.value);
    message.success(wasCreate ? '商品已创建' : '商品已更新');
    if (wasCreate) {
      createdProduct.value = saved;
      return;
    }
    const navigationAuthorization = {
      requestId,
      routeGeneration: sourceRouteGeneration,
      savedProductId: saved.id
    };
    saveNavigationAuthorization = navigationAuthorization;
    try {
      await router.push({ name: 'product-detail', params: { id: saved.id } });
    } finally {
      if (saveNavigationAuthorization === navigationAuthorization) saveNavigationAuthorization = null;
    }
  } catch (error) {
    if (!componentActive || requestId !== latestSaveRequestId || routeGeneration !== sourceRouteGeneration) return;
    message.error(error instanceof Error ? error.message : '商品保存失败');
  } finally {
    if (requestId === latestSaveRequestId && routeGeneration === sourceRouteGeneration) saving.value = false;
  }
}

function finishProductCreate() {
  const saved = createdProduct.value;
  if (!saved) return;
  createdProduct.value = null;
  void router.push({ name: 'product-detail', params: { id: saved.id } });
}

function startOpeningStock() {
  const saved = createdProduct.value;
  if (!saved) return;
  createdProduct.value = null;
  void router.push({
    name: 'inventory-adjustments',
    query: { mode: 'opening', productId: String(saved.id) }
  });
}

function activeOperationMessage() {
  return saving.value ? '商品正在保存，请等待完成后再离开' : '图片正在上传，请等待完成后再离开';
}

function blockActiveOperation() {
  if (!saving.value && !imageUploading.value) return false;
  message.warning(activeOperationMessage());
  return true;
}

function confirmLeave(to: RouteLocationNormalized) {
  if (
    saveNavigationAuthorization !== null
    && saveNavigationAuthorization.requestId === latestSaveRequestId
    && saveNavigationAuthorization.routeGeneration === routeGeneration
    && to.name === 'product-detail'
    && String(to.params.id) === String(saveNavigationAuthorization.savedProductId)
  ) return true;
  if (blockActiveOperation()) return false;
  return !dirty.value || window.confirm('商品资料尚未保存，确定离开当前页面吗？');
}

function cancel() {
  void router.push({ name: 'products' });
}

function saveDraft() {
  void submit(true);
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value && !saving.value && !imageUploading.value) return;
  event.preventDefault();
  event.returnValue = '';
}

onBeforeRouteLeave((to) => confirmLeave(to));
onBeforeRouteUpdate((to) => confirmLeave(to));

watch(() => route.fullPath, () => {
  routeGeneration += 1;
  latestSaveRequestId += 1;
  saving.value = false;
  saveNavigationAuthorization = null;
  createdProduct.value = null;
  void loadEditor();
}, { immediate: true, flush: 'sync' });

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
onBeforeUnmount(() => {
  componentActive = false;
  latestProductRequestId += 1;
  latestOptionRequestId += 1;
  latestSaveRequestId += 1;
  saveNavigationAuthorization = null;
  createdProduct.value = null;
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<template>
  <section class="product-editor" data-testid="product-editor-view">
    <div v-if="loading" data-testid="product-editor-loading" class="product-editor-loading" aria-label="正在加载商品资料" aria-busy="true">
      <div></div>
      <div></div>
    </div>

    <div v-else-if="loadError" data-testid="product-editor-error" class="product-editor-load-error">
      <AlertCircle class="mx-auto h-7 w-7 text-rose-600" aria-hidden="true" />
      <h1>商品编辑器无法加载</h1>
      <p>{{ loadError }}</p>
      <div class="mt-5 flex flex-wrap justify-center gap-3">
        <button data-testid="product-editor-back" type="button" class="product-editor-button" @click="cancel">返回列表</button>
        <button v-if="positiveInteger(route.params.id)" type="button" class="product-editor-button is-primary" @click="loadEditor">
          <RotateCcw class="h-4 w-4" aria-hidden="true" />
          重试
        </button>
      </div>
    </div>

    <template v-else>
      <section data-testid="product-editor-wizard" class="product-editor-wizard">
        <header data-testid="product-editor-header" class="product-editor-header">
          <div>
            <div class="product-editor-title">
              <h1>{{ isDraft ? '编辑商品草稿' : isEdit ? '编辑商品' : '新增商品' }}</h1>
              <span v-if="isEdit && state.itemNo">{{ state.itemNo }}</span>
            </div>
            <p data-testid="product-editor-subtitle">
              {{ isEdit ? '维护商品、SKU 与包装资料。' : '从基础资料开始，逐步建立商品档案。' }}
              <span class="sr-only">第 {{ currentStepIndex + 1 }} 步，共 {{ stepDefinitions.length }} 步</span>
            </p>
          </div>
          <p class="product-editor-save-state" :class="{ 'is-dirty': dirty }" role="status">
            {{ saving ? '保存中…' : dirty ? '资料尚未保存' : state.productId === null ? '新商品 · 尚未创建' : '资料已保存' }}
          </p>
        </header>

        <div v-if="optionError" data-testid="product-editor-option-error" class="product-editor-option-error" role="alert">
          <span>{{ optionError }}</span>
          <button data-testid="retry-editor-options" type="button" class="product-editor-button" @click="loadOptions">
            <RotateCcw class="h-4 w-4" aria-hidden="true" />
            重试选项
          </button>
        </div>

        <nav data-testid="editor-step-nav" class="product-editor-steps" aria-label="商品编辑步骤">
          <ol>
            <li v-for="(step, index) in stepDefinitions" :key="step.id">
              <button
                :data-testid="`step-${step.id}`"
                type="button"
                :aria-current="index === currentStepIndex ? 'step' : undefined"
                :disabled="saving"
                @click="goToStep(index)"
              >
                <span class="product-editor-step-number" aria-hidden="true">{{ index + 1 }}</span>
                <span>{{ step.title }}</span>
              </button>
            </li>
          </ol>
        </nav>

        <div class="product-editor-layout" :class="{ 'is-images-step': currentStep.id === 'images' }">
          <fieldset :key="routeGeneration" ref="stage" data-testid="product-editor-stage" class="product-editor-stage" :disabled="saving" :aria-label="currentStep.title">
            <div class="product-editor-stage-heading">
              <div>
                <h2 ref="stepHeading" data-testid="editor-step-title" tabindex="-1">{{ currentStep.title }}</h2>
                <p>{{ currentStep.description }}</p>
              </div>
              <span v-if="currentStep.id === 'basic'" class="product-editor-required">* 为必填项</span>
            </div>

            <div class="product-editor-stage-body">
              <ProductBasicStep v-if="currentStep.id === 'basic'" v-model="state" :draft="isDraft" :categories="availableCategories" :errors="validationErrors" @edit-skus="goToStep(1)" />
              <ProductSkuStep v-else-if="currentStep.id === 'sku'" v-model="state" :errors="validationErrors" :service="productService" @update:uploading="stepUploading = $event" />
              <ProductProcurementStep v-else-if="currentStep.id === 'procurement'" v-model="state" :suppliers="availableSuppliers" :errors="validationErrors" />
              <ProductPackagingStep v-else-if="currentStep.id === 'packaging'" v-model="state" :errors="validationErrors" :service="productService" :disabled="saving" @update:uploading="stepUploading = $event" />
              <ProductImagesStep
                v-else-if="currentStep.id === 'images'"
                v-model="state"
                :service="productService"
                :disabled="saving"
                :canonical-main-uploading="mainUploading"
                @update:uploading="stepUploading = $event"
              />
              <ProductConfirmStep
                v-else
                :state="state"
                :category-name="categoryName"
                :checks="checks"
                :is-edit="isEdit"
                :valid="firstInvalidStep() === null"
                @edit-step="goToStep"
              />
            </div>
          </fieldset>

          <aside class="product-editor-rail" aria-label="商品资料预览">
            <section class="product-editor-rail-panel product-editor-preview" data-testid="editor-product-preview">
              <h3>商品预览 <small>主图</small></h3>
              <fieldset class="product-editor-preview-upload" :disabled="saving" aria-label="商品主图">
                <ProductImageUpload
                  :key="routeGeneration"
                  :file-id="state.mainImageFileId"
                  :preview="state.imagePreviews.main"
                  :service="productService"
                  :disabled="saving"
                  label="商品主图"
                  test-id="product-main-image"
                  variant="wizard-main"
                  @update:file-id="setEditorImageFileId(state, 'main', null, $event)"
                  @update:preview="setEditorImagePreview(state, 'main', null, $event)"
                  @update:uploading="mainUploading = $event"
                />
              </fieldset>
              <div class="product-editor-preview-meta">
                <h4>{{ state.productName.trim() || '商品名称待填写' }}</h4>
                <p>{{ state.itemNo.trim() || '货号待填写' }}</p>
                <div class="product-editor-badges">
                  <span class="product-editor-badge" :class="{ 'is-complete': state.status === 'enabled' && !isDraft }">
                    {{ isDraft ? '草稿' : state.status === 'enabled' ? '启用' : '停用' }}
                  </span>
                  <span class="product-editor-badge">{{ state.productType === 'variant' ? '多规格' : '单规格' }}</span>
                </div>
              </div>
            </section>

            <section class="product-editor-rail-panel" data-testid="editor-sku-preview">
              <h3>{{ previewSku ? '当前 SKU' : 'SKU 概览' }} <small>{{ state.skus.length }} 个规格</small></h3>
              <div class="product-editor-rail-body">
                <label v-if="state.skus.length > 1" class="product-editor-sku-picker">
                  <span class="sr-only">选择预览的 SKU</span>
                  <select v-model="previewSkuIndex">
                    <option v-for="(sku, index) in state.skus" :key="sku.id ?? index" :value="index">
                      {{ sku.skuCode || sku.skuName || `SKU ${index + 1}` }}{{ sku.defaultSku ? '（默认）' : '' }}
                    </option>
                  </select>
                </label>
                <dl class="product-editor-sku-fields">
                  <div><dt>SKU 货号</dt><dd>{{ previewSku?.skuCode || '—' }}</dd></div>
                  <div><dt>规格</dt><dd>{{ previewSku?.specificationValues.join(' / ') || previewSku?.skuName || '—' }}</dd></div>
                  <div><dt>容量</dt><dd>{{ previewSku?.capacityMl == null ? '—' : `${previewSku.capacityMl} ml` }}</dd></div>
                  <div><dt>销售单位</dt><dd>{{ previewSku?.salesUnit || '—' }}</dd></div>
                </dl>
              </div>
            </section>

            <section class="product-editor-rail-panel" data-testid="editor-completeness">
              <h3>资料检查 <small>{{ completedCheckCount }} / {{ checks.length }} 已填写</small></h3>
              <div class="product-editor-rail-body">
                <progress :value="completedCheckCount" :max="checks.length" aria-label="商品资料填写进度"></progress>
                <ul class="product-editor-checks">
                  <li v-for="(check, index) in checks" :key="check.title">
                    <button type="button" :disabled="saving" @click="goToStep(index)">{{ check.title }}</button>
                    <span :class="{ 'is-complete': check.done }">{{ check.done ? '已填写' : '待补充' }}</span>
                  </li>
                </ul>
              </div>
            </section>
          </aside>
        </div>

        <footer data-testid="product-editor-footer" class="product-editor-footer">
          <p v-if="imageUploading" data-testid="image-upload-blocker" class="product-editor-upload-blocker" role="status">图片正在上传，请等待完成后继续</p>
          <div class="product-editor-actions">
            <div>
              <button data-testid="cancel-product" type="button" class="product-editor-button" @click="cancel">取消</button>
              <button data-testid="save-product-draft" type="button" :disabled="saving || imageUploading" class="product-editor-button" @click="saveDraft">
                保存草稿
              </button>
            </div>
            <div>
              <span class="product-editor-step-count">{{ currentStepIndex + 1 }} / {{ stepDefinitions.length }}</span>
              <button data-testid="previous-step" type="button" :disabled="currentStepIndex === 0 || saving" class="product-editor-button" @click="previousStep">
                <ChevronLeft class="product-editor-step-arrow" aria-hidden="true" />
                上一步
              </button>
              <button v-if="!isEdit && currentStepIndex < stepDefinitions.length - 1" data-testid="next-step" type="button" :disabled="saving || imageUploading" class="product-editor-button is-primary" @click="nextStep">
                下一步
                <ChevronRight class="product-editor-step-arrow" aria-hidden="true" />
              </button>
              <button v-else data-testid="submit-product" type="button" :disabled="saving || imageUploading" class="product-editor-button is-primary" @click="submit()">
                <LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" aria-hidden="true" />
                {{ saving ? '保存中…' : isDraft ? '提交商品' : isEdit ? '保存修改' : '创建商品' }}
              </button>
            </div>
          </div>
        </footer>
      </section>

      <AccessibleDialog
        :open="createdProduct !== null"
        title="商品创建成功"
        description="商品主资料与 SKU 已保存"
        test-id="product-create-success-dialog"
        body-test-id="product-create-success-body"
        footer-test-id="product-create-success-footer"
        close-test-id="product-create-success-close"
        panel-class="w-[520px]"
        body-class="px-6 py-6"
        footer-class="px-6 py-4"
        @cancel="finishProductCreate"
      >
        <div v-if="createdProduct" class="space-y-4">
          <div class="flex items-center gap-4 rounded-lg border border-emerald-100 bg-emerald-50/70 p-4">
            <span class="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-emerald-100 text-emerald-600">
              <Check class="h-5 w-5" aria-hidden="true" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-base font-semibold text-[#25314d]">{{ createdProduct.productName }}</p>
              <p class="mt-1 text-xs text-slate-500">{{ createdProduct.itemNo }} · {{ createdProduct.skus.length }} 个 SKU</p>
            </div>
          </div>
          <div class="flex items-start gap-3 rounded-lg bg-[#f6f9fc] px-4 py-3 text-sm leading-6 text-slate-600">
            <Boxes class="mt-0.5 h-4 w-4 shrink-0 text-[#0968b6]" aria-hidden="true" />
            <p>库存作为独立业务数据维护。现在录入可自动带入本商品的全部 SKU，也可以稍后从库存调整中处理。</p>
          </div>
        </div>
        <template #footer>
          <button
            data-testid="product-create-success-complete"
            type="button"
            class="h-10 min-w-24 rounded-lg border border-[#dbe4f1] bg-white px-4 text-sm font-medium text-[#25314d] hover:border-[#b9c8df]"
            @click="finishProductCreate"
          >
            完成
          </button>
          <button
            data-testid="product-create-success-opening-stock"
            type="button"
            class="h-10 min-w-36 rounded-lg bg-[#0968b6] px-5 text-sm font-medium text-white hover:bg-[#075a9e]"
            @click="startOpeningStock"
          >
            录入期初库存
          </button>
        </template>
      </AccessibleDialog>
    </template>
  </section>
</template>

<style src="./productEditor.css"></style>
