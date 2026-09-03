<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref } from 'vue';
import { Plus, Trash2 } from 'lucide-vue-next';
import { useRoute } from 'vue-router';
import { inventoryService } from '../inventoryService';
import type { InventoryAdjustment, InventoryAdjustmentItem, InventoryService, SaveInventoryAdjustmentPayload } from '../types';
import { masterdataService } from '../../masterdata/masterdataService';
import type { MasterdataService, Warehouse } from '../../masterdata/types';
import { productService } from '../../product/productService';
import type { Product, ProductService } from '../../product/types';

const route = useRoute();
const service = inject<InventoryService>('inventoryService', inventoryService);
const masterdata = inject<MasterdataService>('masterdataService', masterdataService);
const products = inject<Pick<ProductService, 'getProduct'>>('productService', productService);
const form = reactive<SaveInventoryAdjustmentPayload>({ warehouseId: 0, reason: '', remark: '', items: [] });
const warehouses = ref<Warehouse[]>([]);
const openingProduct = ref<Product | null>(null);
const currentAdjustment = ref<InventoryAdjustment | null>(null);
const saving = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const isLocked = computed(() => currentAdjustment.value?.status !== undefined && currentAdjustment.value.status !== 'draft');
const openingRequested = computed(() => route.query.mode === 'opening');
const openingProductId = computed(() => {
  const raw = Array.isArray(route.query.productId) ? route.query.productId[0] : route.query.productId;
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return null;
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
});
const openingSkuById = computed(() => new Map(openingProduct.value?.skus.map((sku) => [sku.id, sku]) ?? []));

function addItem() {
  if (!isLocked.value) form.items.push({ skuId: 0, quantityDelta: 0 });
}

function removeItem(index: number) {
  if (!isLocked.value) form.items.splice(index, 1);
}

function resetMessages() {
  errorMessage.value = '';
  successMessage.value = '';
}

function payload(): SaveInventoryAdjustmentPayload {
  const items = form.items.map((item) => ({ skuId: Number(item.skuId), quantityDelta: Number(item.quantityDelta) }));
  return {
    warehouseId: Number(form.warehouseId),
    reason: form.reason.trim(),
    remark: form.remark.trim(),
    items: openingRequested.value ? items.filter((item) => item.quantityDelta > 0) : items
  };
}

function validate(value: SaveInventoryAdjustmentPayload) {
  if (!value.warehouseId || !value.reason) return '请选择仓库并填写调整原因';
  if (openingRequested.value && form.items.some((item) => Number(item.quantityDelta) < 0)) return '期初库存数量不能小于 0';
  if (!value.items.length || value.items.some((item) => !item.skuId || !item.quantityDelta)) return '请至少添加一条有效的库存调整明细';
  const skuIds = value.items.map((item) => item.skuId);
  if (new Set(skuIds).size !== skuIds.length) return '同一调整单中的 SKU 不能重复';
  return '';
}

async function save() {
  resetMessages();
  const value = payload();
  const validationMessage = validate(value);
  if (validationMessage) {
    errorMessage.value = validationMessage;
    return;
  }
  saving.value = true;
  try {
    currentAdjustment.value = currentAdjustment.value?.id
      ? await service.updateAdjustment(currentAdjustment.value.id, value)
      : await service.createAdjustment(value);
    if (!openingRequested.value) Object.assign(form, value);
    successMessage.value = '库存调整草稿已保存';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存库存调整失败';
  } finally {
    saving.value = false;
  }
}

async function confirmAdjustment() {
  if (!currentAdjustment.value) return;
  resetMessages();
  saving.value = true;
  try {
    currentAdjustment.value = await service.confirmAdjustment(currentAdjustment.value.id);
    successMessage.value = '库存调整已确认';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '确认库存调整失败';
  } finally {
    saving.value = false;
  }
}

async function initialize() {
  const requests: Promise<void>[] = [
    masterdata.listActiveWarehouses()
      .then((records) => { warehouses.value = records; })
      .catch((error) => { throw new Error(error instanceof Error ? error.message : '仓库加载失败'); })
  ];
  if (openingRequested.value && openingProductId.value !== null) {
    requests.push(products.getProduct(openingProductId.value)
      .then((product) => {
        openingProduct.value = product;
        form.reason = '期初库存';
        form.items = product.skus.map((sku) => ({ skuId: sku.id, quantityDelta: 0 }));
      })
      .catch((error) => { throw new Error(error instanceof Error ? error.message : '商品 SKU 加载失败'); }));
  }
  const results = await Promise.allSettled(requests);
  const errors = results
    .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
    .map((result) => result.reason instanceof Error ? result.reason.message : '页面加载失败');
  if (errors.length) errorMessage.value = [...new Set(errors)].join('；');
}

onMounted(() => void initialize());
</script>

<template>
  <section class="mx-auto max-w-[1100px] space-y-5">
    <header class="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div>
        <p class="text-xs font-black uppercase text-[#536dff]">Inventory</p>
        <h1 class="mt-1 text-2xl font-black text-[#25314d]">{{ openingRequested ? '期初库存录入' : '库存调整' }}</h1>
        <p class="mt-2 text-sm font-medium text-slate-400">{{ openingRequested ? '选择仓库并填写各 SKU 的期初数量，数量为 0 的 SKU 不生成流水。' : '先保存草稿，确认后一次性写入库存余额和库存流水。' }}</p>
      </div>
      <span v-if="currentAdjustment" class="inline-flex w-fit rounded-lg px-3 py-2 text-xs font-black" :class="currentAdjustment.status === 'draft' ? 'bg-amber-50 text-amber-600' : currentAdjustment.status === 'confirmed' ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'">{{ currentAdjustment.status === 'draft' ? '草稿' : currentAdjustment.status === 'confirmed' ? '已确认' : '已作废' }}</span>
    </header>

    <section v-if="openingProduct" data-testid="opening-stock-context" class="flex items-center justify-between gap-4 rounded-xl border border-blue-100 bg-[#f5f7ff] px-5 py-4">
      <div class="min-w-0">
        <p class="truncate text-base font-black text-[#25314d]">{{ openingProduct.productName }}</p>
        <p class="mt-1 text-xs font-medium text-slate-500">{{ openingProduct.itemNo }} · 已带入 {{ openingProduct.skus.length }} 个 SKU</p>
      </div>
      <span class="shrink-0 rounded-lg bg-white px-3 py-2 text-xs font-black text-[#536dff]">期初库存</span>
    </section>

    <section class="rounded-2xl border border-slate-100 bg-white p-5 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
      <div class="grid gap-4 sm:grid-cols-2">
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>仓库</span><select data-testid="adjustment-warehouse" v-model.number="form.warehouseId" :disabled="isLocked" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"><option :value="0">请选择仓库</option><option v-for="warehouse in warehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.warehouseName }}</option></select></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>调整原因</span><input data-testid="adjustment-reason" v-model="form.reason" :disabled="isLocked" :readonly="openingRequested" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50 read-only:bg-slate-50 read-only:text-slate-500" placeholder="例如：期初盘点、盘盈盘亏" /></label>
      </div>
      <label class="mt-4 block space-y-2 text-sm font-bold text-slate-500"><span>备注</span><textarea v-model="form.remark" :disabled="isLocked" rows="3" class="w-full resize-y rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"></textarea></label>

      <div class="mt-6 flex items-center justify-between border-t border-slate-100 pt-5">
        <div><h2 class="text-base font-black text-[#25314d]">调整明细</h2><p class="mt-1 text-xs font-medium text-slate-400">正数增加库存，负数减少库存。</p></div>
        <button v-if="!openingRequested" data-testid="add-adjustment-item" type="button" :disabled="isLocked" class="inline-flex h-10 items-center gap-2 rounded-xl border border-[#536dff] px-3 text-sm font-black text-[#536dff] disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-300" @click="addItem"><Plus class="h-4 w-4" aria-hidden="true" />添加 SKU</button>
      </div>
      <div v-if="form.items.length === 0" class="mt-4 rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center text-sm font-bold text-slate-400">暂无明细，请添加 SKU</div>
      <div v-for="(item, index) in form.items" :key="item.skuId || index" class="mt-3 grid gap-3 rounded-xl border border-slate-200 p-4 sm:grid-cols-[minmax(0,1.4fr)_minmax(220px,1fr)_auto] sm:items-end">
        <div v-if="openingRequested && openingSkuById.get(item.skuId)" :data-testid="`opening-stock-sku-${item.skuId}`" class="min-w-0 space-y-1">
          <p class="truncate text-sm font-black text-[#25314d]">{{ openingSkuById.get(item.skuId)?.skuCode }}</p>
          <p class="truncate text-xs font-medium text-slate-400">{{ openingSkuById.get(item.skuId)?.skuName }}<span v-if="openingSkuById.get(item.skuId)?.specText"> · {{ openingSkuById.get(item.skuId)?.specText }}</span></p>
        </div>
        <label v-else class="space-y-2 text-sm font-bold text-slate-500"><span>SKU ID</span><input :data-testid="`adjustment-sku-${index}`" v-model.number="item.skuId" :disabled="isLocked" inputmode="numeric" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" placeholder="输入 SKU ID" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>{{ openingRequested ? '期初数量' : '调整数量' }}</span><input :data-testid="`quantity-delta-${index}`" v-model.number="item.quantityDelta" :disabled="isLocked" inputmode="decimal" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" :placeholder="openingRequested ? '输入期初数量' : '正数增加，负数减少'" /></label>
        <button v-if="!openingRequested" type="button" :disabled="isLocked" class="inline-flex h-11 items-center justify-center rounded-xl border border-slate-200 px-3 text-slate-500 hover:border-rose-200 hover:text-rose-600 disabled:cursor-not-allowed disabled:text-slate-300" aria-label="删除明细" @click="removeItem(index)"><Trash2 class="h-4 w-4" aria-hidden="true" /></button>
        <span v-else class="h-11 w-1" aria-hidden="true"></span>
      </div>

      <p v-if="errorMessage" class="mt-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">{{ errorMessage }}</p>
      <p v-if="successMessage" class="mt-4 border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-700">{{ successMessage }}</p>
      <div class="mt-6 flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5">
        <button data-testid="save-adjustment" type="button" :disabled="saving || isLocked" class="h-11 rounded-xl border border-slate-200 px-5 text-sm font-black text-slate-600 disabled:cursor-not-allowed disabled:text-slate-300" @click="save">保存草稿</button>
        <button data-testid="confirm-adjustment" type="button" :disabled="saving || !currentAdjustment || currentAdjustment.status !== 'draft'" class="h-11 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-md shadow-blue-100 disabled:cursor-not-allowed disabled:bg-slate-300" @click="confirmAdjustment">确认调整</button>
      </div>
    </section>
  </section>
</template>
