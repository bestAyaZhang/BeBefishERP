<script setup lang="ts">
import { AlertCircle, ArrowLeft, Check, ChevronLeft, ChevronRight, LoaderCircle, RotateCcw, Save } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter, type RouteLocationNormalized } from 'vue-router';
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
  { id: 'basic', title: '基础信息', description: '商品身份、分类与规格定义' },
  { id: 'sku', title: 'SKU 信息', description: 'SKU 货号、单位与成本库存' },
  { id: 'procurement', title: '采购信息', description: '按 SKU 维护供应商报价' },
  { id: 'packaging', title: '包装/重量', description: '统一或按 SKU 维护包装' },
  { id: 'images', title: '图片资料', description: '商品、SKU 与包装图片' },
  { id: 'confirm', title: '确认提交', description: '检查完整资料后保存' }
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
const isEdit = computed(() => route.name === 'product-edit');
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

function firstInvalidStep(targetIndex = productEditorSteps.length - 1) {
  for (let index = 0; index <= targetIndex; index += 1) {
    const step = productEditorSteps[index];
    if (!step) continue;
    const errors = stepErrors(step);
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

async function submit() {
  if (imageUploading.value) {
    currentStepIndex.value = productEditorSteps.indexOf('images');
    message.error('图片正在上传，请等待上传完成后再保存');
    return;
  }
  if (saving.value) return;
  const invalid = firstInvalidStep();
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
    const saved = state.value.productId === null
      ? await productService.createProduct(payload)
      : await productService.updateProduct(state.value.productId, payload);
    if (!componentActive || requestId !== latestSaveRequestId || routeGeneration !== sourceRouteGeneration) return;
    initialSnapshot.value = serializeState(state.value);
    message.success(wasCreate ? '商品已创建' : '商品已更新');
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
  void loadEditor();
}, { immediate: true, flush: 'sync' });

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
onBeforeUnmount(() => {
  componentActive = false;
  latestProductRequestId += 1;
  latestOptionRequestId += 1;
  latestSaveRequestId += 1;
  saveNavigationAuthorization = null;
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<template>
  <section class="min-w-0" data-testid="product-editor-view">
    <header class="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 pb-5">
      <div>
        <button data-testid="product-editor-back" type="button" class="mb-3 inline-flex items-center gap-2 text-sm font-bold text-slate-500 hover:text-[#536dff]" @click="cancel">
          <ArrowLeft class="h-4 w-4" aria-hidden="true" />
          返回商品列表
        </button>
        <h1 class="text-2xl font-black text-[#25314d]">{{ isEdit ? '编辑商品' : '新增商品' }}</h1>
        <p class="mt-2 text-sm font-medium text-slate-500">按业务顺序维护商品档案，提交后进入商品详情。</p>
      </div>
      <span v-if="dirty" class="rounded-lg bg-amber-50 px-3 py-2 text-xs font-bold text-amber-700">有未保存更改</span>
    </header>

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
      <div v-if="optionError" data-testid="product-editor-option-error" class="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-800">
        <span>{{ optionError }}</span>
        <button data-testid="retry-editor-options" type="button" class="inline-flex h-8 items-center gap-2 rounded-lg border border-amber-300 px-3 text-xs hover:bg-white" @click="loadOptions">
          <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
          重试选项
        </button>
      </div>

      <nav data-testid="editor-step-nav" class="mt-6 overflow-x-auto border-y border-slate-200 bg-white" aria-label="商品编辑步骤">
        <ol class="grid min-w-[900px] grid-cols-6">
          <li v-for="(step, index) in stepDefinitions" :key="step.id" class="border-r border-slate-100 last:border-r-0">
            <button :data-testid="`step-${step.id}`" type="button" class="flex min-h-20 w-full items-center gap-3 px-4 py-3 text-left transition hover:bg-slate-50" :aria-current="index === currentStepIndex ? 'step' : undefined" @click="goToStep(index)">
              <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-black" :class="index < currentStepIndex ? 'bg-emerald-500 text-white' : index === currentStepIndex ? 'bg-[#536dff] text-white' : 'bg-slate-100 text-slate-500'">
                <Check v-if="index < currentStepIndex" class="h-4 w-4" aria-hidden="true" />
                <span v-else>{{ index + 1 }}</span>
              </span>
              <span class="min-w-0"><strong class="block truncate text-sm text-[#25314d]">{{ step.title }}</strong><small class="mt-1 block truncate text-xs font-medium text-slate-400">{{ step.description }}</small></span>
            </button>
          </li>
        </ol>
      </nav>

      <section class="bg-white px-5 py-6 sm:px-7">
        <div class="mb-6 border-b border-slate-200 pb-5">
          <p class="text-xs font-black text-[#536dff]">步骤 {{ currentStepIndex + 1 }} / 6</p>
          <h2 data-testid="editor-step-title" class="mt-1 text-xl font-black text-[#25314d]">{{ currentStep.title }}</h2>
          <p class="mt-1 text-sm font-medium text-slate-500">{{ currentStep.description }}</p>
        </div>

        <ProductBasicStep v-if="currentStep.id === 'basic'" v-model="state" :categories="availableCategories" :errors="validationErrors" />
        <ProductSkuStep v-else-if="currentStep.id === 'sku'" v-model="state" :errors="validationErrors" />
        <ProductProcurementStep v-else-if="currentStep.id === 'procurement'" v-model="state" :suppliers="availableSuppliers" :errors="validationErrors" />
        <ProductPackagingStep v-else-if="currentStep.id === 'packaging'" v-model="state" :errors="validationErrors" />
        <ProductImagesStep v-else-if="currentStep.id === 'images'" v-model="state" :service="productService" @update:uploading="imageUploading = $event" />
        <ProductConfirmStep v-else :state="state" :categories="availableCategories" :suppliers="availableSuppliers" />
      </section>

      <footer class="sticky bottom-0 z-20 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-white/95 px-5 py-4 backdrop-blur sm:px-7">
        <div class="flex min-w-0 flex-col gap-1">
          <button data-testid="cancel-product" type="button" class="h-10 self-start rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="cancel">取消</button>
          <p v-if="imageUploading" data-testid="image-upload-blocker" class="text-xs font-bold text-amber-700">图片正在上传，请等待完成后继续</p>
        </div>
        <div class="flex gap-2">
          <button v-if="currentStepIndex > 0" data-testid="previous-step" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="previousStep">
            <ChevronLeft class="h-4 w-4" aria-hidden="true" />
            上一步
          </button>
          <button v-if="currentStepIndex < stepDefinitions.length - 1" data-testid="next-step" type="button" :disabled="imageUploading && currentStep.id === 'images'" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white hover:bg-[#435be0] disabled:cursor-not-allowed disabled:opacity-60" @click="nextStep">
            下一步
            <ChevronRight class="h-4 w-4" aria-hidden="true" />
          </button>
          <button v-else data-testid="submit-product" type="button" :disabled="saving || imageUploading" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-5 text-sm font-bold text-white hover:bg-[#435be0] disabled:cursor-not-allowed disabled:opacity-60" @click="submit">
            <LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <Save v-else class="h-4 w-4" aria-hidden="true" />
            {{ saving ? '保存中...' : '保存商品' }}
          </button>
        </div>
      </footer>
    </template>
  </section>
</template>
