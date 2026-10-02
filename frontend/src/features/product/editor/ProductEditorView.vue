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

const route = useRoute();
const router = useRouter();

const stepDefinitions: Array<{ id: ProductEditorStep; title: string; description: string }> = [
  { id: 'basic', title: '基本资料', description: '填写商品级基础资料' },
  { id: 'sku', title: 'SKU 信息', description: '添加商品 SKU 与规格组合' },
  { id: 'procurement', title: '采购与渠道', description: '维护采购条件与供应商报价' },
  { id: 'packaging', title: '包装与重量', description: '设置包装箱规与重量模式' },
  { id: 'images', title: '图片资料', description: '上传商品与包装图片' },
  { id: 'confirm', title: '确认提交', description: '检查资料后提交商品' }
];

const state = ref<ProductEditorState>(createEditorState());
const currentStepIndex = ref(0);
const validationErrors = ref<Record<string, string>>({});
const categories = ref<Category[]>([]);
const suppliers = ref<Supplier[]>([]);
const loading = ref(false);
const saving = ref(false);
const imageUploading = ref(false);
const loadError = ref('');
const optionError = ref('');
const createdProduct = ref<Product | null>(null);
const isDraft = ref(false);
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
const dirty = computed(() => serializeState(state.value) !== initialSnapshot.value);

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
  imageUploading.value = false;
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
    validSupplierIds: new Set(suppliers.value.map((supplier) => supplier.id))
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
          : 'input, select, button';
  document.querySelector<HTMLElement>(selector)?.focus();
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
  if (index !== currentStepIndex.value && blockActiveOperation()) return;
  if (index > currentStepIndex.value) {
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
  if (currentStepIndex.value <= 0) return;
  if (blockActiveOperation()) return;
  currentStepIndex.value -= 1;
  validationErrors.value = {};
}

async function submit(asDraft = false) {
  if (imageUploading.value) {
    currentStepIndex.value = productEditorSteps.indexOf('images');
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
  <section class="min-w-0" data-testid="product-editor-view">
    <div v-if="loading" data-testid="product-editor-loading" class="space-y-4 py-8">
      <div class="h-16 animate-pulse rounded-lg bg-slate-100"></div>
      <div class="h-80 animate-pulse rounded-lg bg-slate-100"></div>
    </div>

    <div v-else-if="loadError" data-testid="product-editor-error" class="py-16 text-center">
      <AlertCircle class="mx-auto h-8 w-8 text-rose-500" aria-hidden="true" />
      <h2 class="mt-4 text-lg font-black text-[#25314d]">商品编辑器无法加载</h2>
      <p class="mt-2 text-sm font-medium text-slate-500">{{ loadError }}</p>
      <div class="mt-5 flex justify-center gap-3">
        <button data-testid="product-editor-back" type="button" class="h-10 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="cancel">返回列表</button>
        <button v-if="positiveInteger(route.params.id)" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white" @click="loadEditor">
          <RotateCcw class="h-4 w-4" aria-hidden="true" />
          重试
        </button>
      </div>
    </div>

    <template v-else>
      <section data-testid="product-editor-wizard" class="h-[904px] min-w-[1132px]">
        <header data-testid="product-editor-header" class="flex h-16 items-center justify-between">
          <div class="min-w-0">
            <h1 class="text-page-title text-[#25314d]">{{ isDraft ? '编辑商品草稿' : isEdit ? '编辑商品' : '新增商品' }}</h1>
            <p data-testid="product-editor-subtitle" class="mt-1 text-xs font-normal leading-5 text-[#8292ae]">第 {{ currentStepIndex + 1 }} 步，共 6 步 · {{ currentStep.description }}</p>
          </div>
          <div class="inline-flex h-8 items-center gap-2 rounded-lg bg-[#f8fafc] px-3 text-xs font-medium text-[#64748b]">
            <span class="h-2 w-2 rounded-full bg-[#32c5a4]"></span>
            {{ saving ? '保存中...' : dirty || state.productId === null ? '资料待保存' : '资料已保存' }}
          </div>
        </header>

        <div v-if="optionError" data-testid="product-editor-option-error" class="absolute left-1/2 top-20 z-30 flex -translate-x-1/2 items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-800 shadow-lg">
          <span>{{ optionError }}</span>
          <button data-testid="retry-editor-options" type="button" class="inline-flex h-8 items-center gap-2 rounded-lg border border-amber-300 px-3 text-xs hover:bg-white" @click="loadOptions">
            <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            重试选项
          </button>
        </div>

        <nav data-testid="editor-step-nav" class="mt-4 h-[92px] overflow-hidden rounded-lg border border-[#dbe4f1] bg-white px-4 py-6" aria-label="商品编辑步骤">
          <ol class="grid grid-cols-[repeat(6,164px)] gap-4">
            <li v-for="(step, index) in stepDefinitions" :key="step.id" class="h-11">
              <button :data-testid="`step-${step.id}`" type="button" class="flex h-11 w-full items-center gap-2.5 rounded-lg text-left outline-none focus-visible:ring-2 focus-visible:ring-[#536dff]/30" :aria-current="index === currentStepIndex ? 'step' : undefined" @click="goToStep(index)">
                <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-semibold" :class="index < currentStepIndex ? 'bg-[#e6f8f2] text-[#16a36a]' : index === currentStepIndex ? 'bg-[#536dff] text-white' : 'bg-[#f1f5f9] text-[#8292ae]'">
                  <Check v-if="index < currentStepIndex" class="h-4 w-4" aria-hidden="true" />
                  <span v-else>{{ index + 1 }}</span>
                </span>
                <span class="min-w-0">
                  <strong class="block truncate text-sm font-medium leading-5" :class="index === currentStepIndex ? 'text-[#25314d]' : 'text-[#64748b]'">{{ step.title }}</strong>
                  <small class="block text-xs font-normal leading-[18px]" :class="index < currentStepIndex ? 'text-[#16a36a]' : index === currentStepIndex ? 'text-[#536dff]' : 'text-[#94a3b8]'">{{ index < currentStepIndex ? '已完成' : index === currentStepIndex ? '进行中' : '待填写' }}</small>
                </span>
              </button>
            </li>
          </ol>
        </nav>

        <fieldset data-testid="product-editor-stage" :disabled="saving" :aria-label="currentStep.title" class="relative mt-4 h-[620px] min-w-0 overflow-hidden rounded-lg border border-[#dbe4f1] bg-white p-6">
          <div class="flex h-[50px] items-center justify-between gap-4">
            <div class="min-w-0">
              <h2 data-testid="editor-step-title" class="text-[18px] font-semibold leading-[26px] text-[#25314d]">{{ currentStep.title }}</h2>
              <p class="mt-1 text-xs font-normal leading-[18px] text-[#8292ae]">{{ currentStep.description }}</p>
            </div>
            <span v-if="currentStep.id === 'basic'" class="inline-flex h-7 items-center rounded-lg bg-[#fff1f2] px-2.5 text-xs font-medium text-[#ef476f]">* 为必填项</span>
          </div>

          <div class="mt-4 h-[490px] overflow-y-auto overflow-x-hidden">
            <ProductBasicStep v-if="currentStep.id === 'basic'" v-model="state" :draft="isDraft" :categories="availableCategories" :errors="validationErrors" :service="productService" @update:uploading="imageUploading = $event" />
            <ProductSkuStep v-else-if="currentStep.id === 'sku'" v-model="state" :errors="validationErrors" :service="productService" @update:uploading="imageUploading = $event" />
            <ProductProcurementStep v-else-if="currentStep.id === 'procurement'" v-model="state" :suppliers="availableSuppliers" :errors="validationErrors" />
            <ProductPackagingStep v-else-if="currentStep.id === 'packaging'" v-model="state" :errors="validationErrors" :service="productService" @update:uploading="imageUploading = $event" />
            <ProductImagesStep v-else-if="currentStep.id === 'images'" v-model="state" :service="productService" @update:uploading="imageUploading = $event" />
            <ProductConfirmStep v-else :state="state" :categories="availableCategories" :suppliers="availableSuppliers" />
          </div>
        </fieldset>

        <footer data-testid="product-editor-footer" class="mt-4 flex h-20 items-center justify-between rounded-lg border border-[#dbe4f1] bg-white px-4">
          <div class="flex items-center gap-2.5">
            <button data-testid="cancel-product" type="button" class="h-10 w-[88px] rounded-lg border border-[#dbe4f1] bg-white text-sm font-medium text-[#25314d] hover:border-[#b9c8df]" @click="cancel">取消</button>
            <button data-testid="save-product-draft" type="button" :disabled="saving || imageUploading" class="inline-flex h-10 w-28 items-center justify-center gap-2 rounded-lg border border-[#dbe4f1] bg-white text-sm font-medium text-[#25314d] hover:border-[#b9c8df] disabled:cursor-not-allowed disabled:opacity-60" @click="saveDraft">
              <LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" aria-hidden="true" />
              {{ saving ? '保存中...' : '保存草稿' }}
            </button>
            <p v-if="imageUploading" data-testid="image-upload-blocker" class="text-xs font-medium text-amber-700">图片正在上传，请等待完成后继续</p>
          </div>
          <div class="flex gap-2.5">
            <button data-testid="previous-step" type="button" :disabled="currentStepIndex === 0" class="inline-flex h-10 w-[104px] items-center justify-center gap-2 rounded-lg border border-[#dbe4f1] bg-white text-sm font-medium text-[#25314d] disabled:border-transparent disabled:bg-[#f1f5f9] disabled:text-[#94a3b8]" @click="previousStep">
            <ChevronLeft class="h-4 w-4" aria-hidden="true" />
              上一步
            </button>
            <button v-if="currentStepIndex < stepDefinitions.length - 1" data-testid="next-step" type="button" :disabled="imageUploading && currentStep.id === 'images'" class="inline-flex h-10 w-28 items-center justify-center gap-2 rounded-lg bg-[#536dff] text-sm font-medium text-white hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" @click="nextStep">
              下一步
              <ChevronRight class="h-4 w-4" aria-hidden="true" />
            </button>
            <button v-else data-testid="submit-product" type="button" :disabled="saving || imageUploading" class="inline-flex h-10 w-28 items-center justify-center gap-2 rounded-lg bg-[#536dff] text-sm font-medium text-white hover:bg-[#465eea] disabled:cursor-not-allowed disabled:opacity-60" @click="submit()">
              <LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" aria-hidden="true" />
              {{ saving ? '保存中...' : isDraft ? '提交商品' : isEdit ? '保存修改' : '创建商品' }}
            </button>
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
          <div class="flex items-start gap-3 rounded-lg bg-[#f5f7ff] px-4 py-3 text-sm leading-6 text-slate-600">
            <Boxes class="mt-0.5 h-4 w-4 shrink-0 text-[#536dff]" aria-hidden="true" />
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
            class="h-10 min-w-36 rounded-lg bg-[#536dff] px-5 text-sm font-medium text-white hover:bg-[#465eea]"
            @click="startOpeningStock"
          >
            录入期初库存
          </button>
        </template>
      </AccessibleDialog>
    </template>
  </section>
</template>
