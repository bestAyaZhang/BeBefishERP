<script setup lang="ts">
import { Check, ChevronDown, FileText, Plus, Printer, Save, Trash2 } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { message } from '../../components/feedback/message';
import { currentUser } from '../../services/authSession';
import { salesCatalogService } from './salesCatalogService';
import { printSalesDocument } from './salesPrint';
import { salesOrderService } from './salesOrderService';
import type { ConfirmSalesOrderPayload, SaleProductOption, SalesCatalog, SalesCatalogService, SalesOrderService } from './types';

interface SalesLine extends SaleProductOption {
  quantity: number;
  unitPrice: number;
}

interface SalesDraft {
  customerId: number | null;
  warehouseId: number | null;
  orderDate: string;
  transportMethod: string;
  settlementCycle: string;
  freight: number;
  receivedAmount: number;
  remark: string;
}

const props = defineProps<{ service?: SalesCatalogService; orderService?: SalesOrderService }>();
const service = props.service ?? salesCatalogService;
const orderService = props.orderService ?? salesOrderService;
const router = useRouter();
const today = new Date().toISOString().slice(0, 10);
const catalog = ref<SalesCatalog>({ customers: [], warehouses: [], products: [] });
const lines = ref<SalesLine[]>([]);
const selectedProductId = ref<number | null>(null);
const productQuery = ref('');
const productDropdownOpen = ref(false);
const loading = ref(false);
const submitting = ref(false);
const printReady = ref(false);
const orderStatus = ref<'draft' | 'confirmed'>('draft');
const draft = reactive<SalesDraft>({
  customerId: null,
  warehouseId: null,
  orderDate: today,
  transportMethod: 'delivery',
  settlementCycle: 'monthly',
  freight: 0,
  receivedAmount: 0,
  remark: ''
});

const selectedProduct = computed(() => catalog.value.products.find((product) => product.skuId === selectedProductId.value));
const selectedCustomer = computed(() => catalog.value.customers.find((customer) => customer.id === draft.customerId));
const selectedWarehouse = computed(() => catalog.value.warehouses.find((warehouse) => warehouse.id === draft.warehouseId));
const selectedCustomerAddress = computed(() => [selectedCustomer.value?.province, selectedCustomer.value?.city, selectedCustomer.value?.district, selectedCustomer.value?.detailAddress].filter(Boolean).join('') || '—');
const filteredProducts = computed(() => {
  const query = productQuery.value.trim().toLowerCase();
  if (!query) return catalog.value.products;
  return catalog.value.products.filter((product) => [product.skuCode, product.itemNo, product.productName, product.skuName, product.specification]
    .join(' ')
    .toLowerCase()
    .includes(query));
});
const subtotal = computed(() => lines.value.reduce((total, line) => total + line.quantity * line.unitPrice, 0));
const total = computed(() => subtotal.value + Number(draft.freight || 0));
const receivable = computed(() => Math.max(total.value - Number(draft.receivedAmount || 0), 0));
const transportOptions = [
  { value: 'pickup', label: '自提' },
  { value: 'delivery', label: '送货上门' },
  { value: 'consignment', label: '托运' },
  { value: 'express', label: '快递' }
];
const settlementOptions = [
  { value: 'monthly', label: '月结' },
  { value: 'daily', label: '日结' },
  { value: 'quarterly', label: '季度' },
  { value: 'annual', label: '年结' }
];
const selectedTransportLabel = computed(() => transportOptions.find((option) => option.value === draft.transportMethod)?.label ?? '—');
const selectedSettlementLabel = computed(() => settlementOptions.find((option) => option.value === draft.settlementCycle)?.label ?? '—');
const salesRepresentative = computed(() => currentUser.value?.mobile || '—');
const paymentStatus = computed(() => {
  if (!draft.receivedAmount) return '未收款';
  if (receivable.value > 0) return '部分收款';
  return '已收款';
});

const chineseDigits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'];
const chineseSmallUnits = ['', '拾', '佰', '仟'];
const chineseLargeUnits = ['', '万', '亿', '兆'];

function formatMoney(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function lineAmount(line: SalesLine) {
  return line.quantity * line.unitPrice;
}

function convertFourDigitNumber(value: number) {
  let result = '';
  let hasValue = false;
  let pendingZero = false;

  for (let index = 0; index < 4; index += 1) {
    const divisor = 10 ** (3 - index);
    const digit = Math.floor(value / divisor) % 10;
    if (!digit) {
      if (hasValue) pendingZero = true;
      continue;
    }
    if (pendingZero) result += chineseDigits[0];
    result += chineseDigits[digit] + chineseSmallUnits[3 - index];
    hasValue = true;
    pendingZero = false;
  }

  return result;
}

function amountToChinese(value: number) {
  const amount = Math.max(0, Number(value || 0));
  const [integerPart, decimalPart] = amount.toFixed(2).split('.');
  const integer = Number(integerPart);
  let integerText = '';

  if (!integer) {
    integerText = chineseDigits[0];
  } else {
    const groups: number[] = [];
    let remaining = integer;
    while (remaining > 0) {
      groups.unshift(remaining % 10000);
      remaining = Math.floor(remaining / 10000);
    }

    let pendingZero = false;
    groups.forEach((group, index) => {
      if (!group) {
        if (integerText) pendingZero = true;
        return;
      }
      if (integerText && (pendingZero || group < 1000)) integerText += chineseDigits[0];
      integerText += convertFourDigitNumber(group) + chineseLargeUnits[groups.length - index - 1];
      pendingZero = false;
    });
  }

  const jiao = Number(decimalPart[0]);
  const fen = Number(decimalPart[1]);
  if (!jiao && !fen) return `${integerText}元整`;
  if (!jiao && fen) return `${integerText}元${chineseDigits[0]}${chineseDigits[fen]}分`;
  if (jiao && !fen) return `${integerText}元${chineseDigits[jiao]}角整`;
  return `${integerText}元${chineseDigits[jiao]}角${chineseDigits[fen]}分`;
}

function productLabel(product: SaleProductOption) {
  return `${product.itemNo} · ${product.skuCode} · ${product.productName} · ${product.skuName}`;
}

function selectProduct(product: SaleProductOption) {
  selectedProductId.value = product.skuId;
  productQuery.value = productLabel(product);
  productDropdownOpen.value = false;
}

function handleProductInput() {
  selectedProductId.value = null;
  productDropdownOpen.value = true;
}

function toggleProductDropdown() {
  if (!loading.value) productDropdownOpen.value = !productDropdownOpen.value;
}

function handleOutsideProductClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  if (!target?.closest('[data-testid="sales-product-picker"]')) productDropdownOpen.value = false;
}

function addProduct() {
  const product = selectedProduct.value;
  if (!product) {
    message.error('请选择商品');
    return;
  }
  if (lines.value.some((line) => line.skuId === product.skuId)) {
    message.error('该 SKU 已添加');
    return;
  }
  lines.value.push({ ...product, quantity: 1, unitPrice: product.defaultSalePrice });
  selectedProductId.value = null;
  productQuery.value = '';
  productDropdownOpen.value = false;
}

function removeLine(skuId: number) {
  lines.value = lines.value.filter((line) => line.skuId !== skuId);
}

function validateOrder() {
  if (!draft.customerId) return '请选择客户';
  if (!draft.warehouseId) return '请选择仓库';
  if (!lines.value.length) return '请至少添加一条商品明细';
  return '';
}

function buildPayload(): ConfirmSalesOrderPayload | null {
  const customerId = draft.customerId;
  const warehouseId = draft.warehouseId;
  if (!customerId || !warehouseId) return null;

  return {
    customerId,
    customerName: selectedCustomer.value?.customerName ?? '',
    warehouseId,
    warehouseName: selectedWarehouse.value?.warehouseName ?? '',
    orderDate: draft.orderDate,
    transportMethod: draft.transportMethod,
    settlementCycle: draft.settlementCycle,
    freight: Number(draft.freight || 0),
    totalAmount: total.value,
    receivedAmount: Number(draft.receivedAmount || 0),
    receivableAmount: receivable.value,
    remark: draft.remark,
    lines: lines.value.map((line) => ({
      skuId: line.skuId,
      quantity: Number(line.quantity || 0),
      unitPrice: Number(line.unitPrice || 0),
      amount: lineAmount(line)
    }))
  };
}

async function saveDraft() {
  const validationMessage = validateOrder();
  if (validationMessage) {
    message.error(validationMessage);
    return;
  }
  const payload = buildPayload();
  if (!payload) return;

  submitting.value = true;
  try {
    const result = await orderService.saveDraft(payload);
    orderStatus.value = 'draft';
    await router.push({ name: 'sales-orders', query: { created: result.orderNo, type: 'draft' } });
  } catch (error) {
    message.error(error instanceof Error ? error.message : '销售单草稿保存失败');
  } finally {
    submitting.value = false;
  }
}

async function confirmOrder() {
  const validationMessage = validateOrder();
  if (validationMessage) {
    message.error(validationMessage);
    return;
  }
  const payload = buildPayload();
  if (!payload) return;

  submitting.value = true;
  try {
    const result = await orderService.confirmOrder(payload);
    orderStatus.value = 'confirmed';
    await router.push({ name: 'sales-orders', query: { created: result.orderNo } });
  } catch (error) {
    const failureMessage = error instanceof Error ? error.message : '销售单确认失败';
    message.error(failureMessage);
  } finally {
    submitting.value = false;
  }
}

async function printOrder() {
  if (printReady.value) return;
  printReady.value = true;
  await nextTick();
  document.body.classList.add('sales-order-printing');

  try {
    printSalesDocument(document.querySelector<HTMLElement>('#sales-print-document'), {
      onPrintDialogClose: exitPrintMode,
      onError: (error) => {
        exitPrintMode();
        message.error(error instanceof Error ? error.message : '销售单打印失败');
      }
    });
  } catch (error) {
    exitPrintMode();
    message.error(error instanceof Error ? error.message : '销售单打印失败');
  }
}

function exitPrintMode() {
  document.body.classList.remove('sales-order-printing');
  printReady.value = false;
}

async function loadCatalog() {
  loading.value = true;
  try {
    catalog.value = await service.loadCatalog();
    const defaultWarehouse = catalog.value.warehouses.find((warehouse) => warehouse.defaultWarehouse) ?? catalog.value.warehouses[0];
    draft.warehouseId = defaultWarehouse?.id ?? null;
  } catch (error) {
    const failureMessage = error instanceof Error ? error.message : '销售开单基础数据加载失败';
    message.error(failureMessage);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadCatalog();
  document.addEventListener('click', handleOutsideProductClick);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleOutsideProductClick);
  document.body.classList.remove('sales-order-printing');
});
</script>

<template>
  <section data-testid="sales-create-page" class="mx-auto max-w-[1440px]">
    <div data-testid="sales-screen" :data-print-state="printReady ? 'excluded' : 'active'" class="space-y-5" :class="{ 'sales-screen--print-hidden': printReady }">
    <header data-testid="sales-create-header" class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <RouterLink data-testid="sales-back" :to="{ name: 'sales-orders' }" class="text-sm font-bold text-slate-400 transition hover:text-[#536dff]">← 返回销售单据</RouterLink>
        <p class="mt-5 text-[11px] font-black tracking-[0.22em] text-[#536dff]">SALES / CREATE ORDER</p>
        <h1 class="mt-2 text-2xl font-black text-[#25314d]">销售开单</h1>
        <p class="mt-1 text-sm font-medium text-slate-400">录入线下销售订单，确认后用于库存扣减、收款和欠款管理。</p>
      </div>
      <div class="flex items-center gap-2">
        <span data-testid="sales-status" class="rounded-full px-3 py-1 text-xs font-black" :class="orderStatus === 'confirmed' ? 'bg-emerald-50 text-emerald-500' : 'bg-amber-50 text-amber-500'">{{ orderStatus === 'confirmed' ? '已确认' : '草稿' }}</span>
        <button data-testid="sales-print" type="button" class="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-600 transition hover:bg-slate-50" @click="printOrder"><Printer class="h-4 w-4" aria-hidden="true" />打印</button>
      </div>
    </header>

    <form data-testid="sales-order-form" class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]" @submit.prevent="saveDraft">
      <div class="min-w-0 space-y-5">
        <section data-testid="sales-order-basic" class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
          <div class="mb-5 flex items-center gap-3"><span class="flex h-9 w-9 items-center justify-center rounded-full bg-blue-50 text-[#536dff]"><FileText class="h-5 w-5" aria-hidden="true" /></span><div><h2 class="text-lg font-black">单据信息</h2><p class="mt-1 text-sm font-medium text-slate-400">客户、仓库和结算方式会写入销售单据。</p></div></div>
          <div class="grid gap-4 md:grid-cols-2">
            <label class="space-y-2"><span class="text-xs font-black text-slate-500">客户 <b class="text-rose-500">*</b></span><select data-testid="sales-customer" v-model.number="draft.customerId" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]"><option :value="null">请选择客户</option><option v-for="customer in catalog.customers" :key="customer.id" :value="customer.id">{{ customer.customerNo }} · {{ customer.customerName }}</option></select></label>
            <label class="space-y-2"><span class="text-xs font-black text-slate-500">仓库 <b class="text-rose-500">*</b></span><select data-testid="sales-warehouse" v-model.number="draft.warehouseId" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]"><option :value="null">请选择仓库</option><option v-for="warehouse in catalog.warehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.warehouseName }}{{ warehouse.defaultWarehouse ? ' · 默认仓' : '' }}</option></select></label>
            <label class="space-y-2"><span class="text-xs font-black text-slate-500">开单日期</span><input data-testid="sales-order-date" v-model="draft.orderDate" type="date" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]" /></label>
            <label class="space-y-2"><span class="text-xs font-black text-slate-500">运输方式</span><select data-testid="sales-transport" v-model="draft.transportMethod" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]"><option value="pickup">自提</option><option value="delivery">送货上门</option><option value="consignment">托运</option><option value="express">快递</option></select></label>
            <label class="space-y-2"><span class="text-xs font-black text-slate-500">结算方式</span><select data-testid="sales-settlement" v-model="draft.settlementCycle" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]"><option value="monthly">月结</option><option value="daily">日结</option><option value="quarterly">季度</option><option value="annual">年结</option></select></label>
          </div>
        </section>

        <section data-testid="sales-order-lines" class="rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
          <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-6 py-5"><div><h2 class="text-lg font-black">商品明细</h2><p class="mt-1 text-sm font-medium text-slate-400">选择 SKU 后维护数量和本次销售单价。</p></div><span class="rounded-full bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">{{ lines.length }} 项</span></div>
          <div class="flex flex-col gap-3 border-b border-slate-100 bg-slate-50/50 px-6 py-4 sm:flex-row"><div data-testid="sales-product-picker" class="relative min-w-0 flex-1"><input data-testid="sales-product-input" v-model="productQuery" type="search" autocomplete="off" :placeholder="loading ? '商品加载中...' : '输入货号、SKU或商品名称'" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 pr-10 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]" :disabled="loading" @focus="productDropdownOpen = true" @input="handleProductInput" /><button data-testid="sales-product-toggle" type="button" class="absolute right-1 top-1 inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-50 hover:text-[#536dff]" :disabled="loading" @click="toggleProductDropdown"><ChevronDown class="h-4 w-4" aria-hidden="true" /></button><div v-if="productDropdownOpen" data-testid="sales-product-options" class="absolute left-0 top-12 z-20 max-h-72 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white p-1 shadow-xl"><button v-for="product in filteredProducts" :key="product.skuId" :data-testid="`sales-product-option-${product.skuId}`" type="button" class="block w-full rounded-lg px-3 py-2 text-left transition hover:bg-blue-50" @mousedown.prevent @click="selectProduct(product)"><p class="text-sm font-black text-[#25314d]">{{ product.productName }} · {{ product.skuName }}</p><p class="mt-1 text-xs font-bold text-slate-400">{{ product.skuCode }} · {{ product.itemNo }} · {{ product.specification || '默认规格' }}</p></button><p v-if="!filteredProducts.length" data-testid="sales-product-no-results" class="px-3 py-4 text-center text-sm font-bold text-slate-400">未找到匹配商品</p></div></div><button data-testid="sales-add-product" type="button" class="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-md shadow-blue-100 transition hover:bg-[#465eea]" @click="addProduct"><Plus class="h-4 w-4" aria-hidden="true" />添加商品</button></div>
          <div class="overflow-x-auto">
            <table v-if="lines.length" class="w-full min-w-[760px] border-collapse text-left text-sm"><thead class="bg-slate-50 text-xs font-black text-slate-400"><tr><th class="whitespace-nowrap px-5 py-3">商品信息</th><th class="whitespace-nowrap px-5 py-3">规格</th><th class="whitespace-nowrap px-5 py-3">数量</th><th class="whitespace-nowrap px-5 py-3">单价</th><th class="whitespace-nowrap px-5 py-3 text-right">金额</th><th class="w-16 px-5 py-3"></th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-for="line in lines" :key="line.skuId" :data-testid="`sales-line-${line.skuId}`"><td class="px-5 py-4"><p class="font-black text-[#25314d]">{{ line.productName }} · {{ line.skuName }}</p><p class="mt-1 text-xs font-bold text-slate-400">{{ line.skuCode }} · {{ line.itemNo }}</p></td><td class="px-5 py-4 font-bold text-slate-600">{{ line.specification || '默认规格' }}</td><td class="px-5 py-4"><div class="flex items-center gap-2"><input :data-testid="`sales-line-qty-${line.skuId}`" v-model.number="line.quantity" min="1" type="number" class="h-10 w-24 rounded-lg border border-slate-200 px-3 text-sm font-bold outline-none focus:border-[#536dff]" /><span class="text-xs font-bold text-slate-400">{{ line.salesUnit }}</span></div></td><td class="px-5 py-4"><div class="flex items-center gap-1"><span class="font-bold text-slate-400">¥</span><input :data-testid="`sales-line-price-${line.skuId}`" v-model.number="line.unitPrice" min="0" step="0.01" type="number" class="h-10 w-28 rounded-lg border border-slate-200 px-3 text-sm font-bold outline-none focus:border-[#536dff]" /></div></td><td :data-testid="`sales-line-amount-${line.skuId}`" class="whitespace-nowrap px-5 py-4 text-right font-black text-[#25314d]">{{ formatMoney(lineAmount(line)) }}</td><td class="px-5 py-4 text-right"><button type="button" class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-rose-50 hover:text-rose-500" :aria-label="`删除 ${line.skuName}`" @click="removeLine(line.skuId)"><Trash2 class="h-4 w-4" aria-hidden="true" /></button></td></tr></tbody></table>
            <div v-else data-testid="sales-empty-lines" class="px-6 py-14 text-center"><p class="text-sm font-black text-slate-500">暂无商品明细</p><p class="mt-1 text-xs font-medium text-slate-400">请从上方输入或选择 SKU 添加到销售单。</p></div>
          </div>
        </section>

        <section class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><label class="block space-y-2"><span class="text-xs font-black text-slate-500">备注</span><textarea data-testid="sales-remark" v-model="draft.remark" rows="3" class="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm font-medium outline-none focus:border-[#536dff]" placeholder="填写客户要求、发货说明等"></textarea></label></section>
      </div>

      <aside data-testid="sales-summary" class="h-fit rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)] xl:sticky xl:top-6"><div class="flex items-center justify-between"><h2 class="text-lg font-black">结算汇总</h2><span class="text-xs font-bold text-slate-400">{{ lines.length }} 项商品</span></div><dl class="mt-6 space-y-4 text-sm"><div class="flex items-center justify-between"><dt class="font-bold text-slate-400">商品金额</dt><dd data-testid="sales-subtotal" class="font-black text-[#25314d]">{{ formatMoney(subtotal) }}</dd></div><div class="flex items-center justify-between gap-3"><dt class="font-bold text-slate-400">运费</dt><dd><input data-testid="sales-freight" v-model.number="draft.freight" min="0" step="0.01" type="number" class="h-9 w-28 rounded-lg border border-slate-200 px-2 text-right text-sm font-bold outline-none focus:border-[#536dff]" /></dd></div><div class="flex items-center justify-between border-t border-slate-100 pt-4"><dt class="font-black text-[#25314d]">合计金额</dt><dd data-testid="sales-total" class="text-xl font-black text-[#25314d]">{{ formatMoney(total) }}</dd></div><div class="flex items-center justify-between gap-3"><dt class="font-bold text-slate-400">已收款</dt><dd><input data-testid="sales-received" v-model.number="draft.receivedAmount" min="0" step="0.01" type="number" class="h-9 w-28 rounded-lg border border-slate-200 px-2 text-right text-sm font-bold outline-none focus:border-[#536dff]" /></dd></div><div class="flex items-center justify-between"><dt class="font-bold text-slate-400">欠款应收</dt><dd data-testid="sales-receivable" class="font-black text-rose-500">{{ formatMoney(receivable) }}</dd></div></dl><div class="mt-6 grid gap-3"><button data-testid="sales-confirm" type="button" :disabled="submitting" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#536dff] text-sm font-black text-white shadow-lg shadow-blue-200 transition hover:bg-[#465eea] disabled:cursor-wait disabled:opacity-60" @click="confirmOrder"><Check class="h-4 w-4" aria-hidden="true" />{{ submitting ? '提交中...' : '确认销售单' }}</button><button data-testid="sales-save-draft" type="submit" :disabled="submitting" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-black text-slate-600 transition hover:bg-slate-50 disabled:cursor-wait disabled:opacity-60"><Save class="h-4 w-4" aria-hidden="true" />{{ submitting ? '保存中...' : '保存草稿' }}</button></div></aside>
    </form>
    </div>

    <Teleport v-if="printReady" to="body">
    <article id="sales-print-document" data-testid="sales-print-document" class="sales-print-document">
      <header class="sales-print-header">
        <div>
          <p class="sales-print-company">BeBefish ERP</p>
          <p class="sales-print-company-subtitle">电商经营管理</p>
        </div>
        <div class="sales-print-title-wrap">
          <h1>出库送货单</h1>
          <p>销售单据 · {{ orderStatus === 'confirmed' ? '已确认' : '草稿' }}</p>
        </div>
        <div class="sales-print-order-meta"><span>销售单号</span><strong>待生成</strong><span>开单日期</span><strong>{{ draft.orderDate }}</strong></div>
      </header>

      <section class="sales-print-section sales-print-info-grid">
        <div class="sales-print-info-card">
          <h2>客户信息</h2>
          <dl><div><dt>客户名称</dt><dd>{{ selectedCustomer?.customerName || '—' }}</dd></div><div><dt>收货人</dt><dd>{{ selectedCustomer?.contactPerson || '—' }}</dd></div><div><dt>联系电话</dt><dd>{{ selectedCustomer?.mobile || selectedCustomer?.telephone || '—' }}</dd></div><div><dt>送货地址</dt><dd>{{ selectedCustomerAddress }}</dd></div></dl>
        </div>
        <div class="sales-print-info-card">
          <h2>发货信息</h2>
          <dl><div><dt>发货仓库</dt><dd>{{ selectedWarehouse?.warehouseName || '—' }}</dd></div><div><dt>仓库地址</dt><dd>{{ selectedWarehouse?.address || '—' }}</dd></div><div><dt>计划发货日期</dt><dd>{{ draft.orderDate }}</dd></div><div><dt>经办人</dt><dd>—</dd></div></dl>
        </div>
      </section>

      <section class="sales-print-section sales-print-status-grid">
        <div><span>运输方式</span><strong>{{ selectedTransportLabel }}</strong></div><div><span>结算周期</span><strong>{{ selectedSettlementLabel }}</strong></div><div><span>收款状态</span><strong>{{ paymentStatus }}</strong></div><div><span>开票状态</span><strong>未开票</strong></div>
      </section>

      <section class="sales-print-section">
        <div class="sales-print-section-title"><h2>商品明细</h2><span>{{ lines.length }} 项 SKU</span></div>
        <table class="sales-print-lines">
          <colgroup><col class="sales-print-col-index" /><col class="sales-print-col-item" /><col class="sales-print-col-name" /><col class="sales-print-col-spec" /><col class="sales-print-col-package" /><col class="sales-print-col-carton" /><col class="sales-print-col-barcode" /><col class="sales-print-col-quantity" /><col class="sales-print-col-unit" /><col class="sales-print-col-price" /><col class="sales-print-col-amount" /></colgroup>
          <thead><tr><th>序号</th><th>货号</th><th>商品名称</th><th>规格</th><th>包装</th><th>箱规</th><th>条形码</th><th>数量</th><th>单位</th><th>单价</th><th>金额</th></tr></thead>
          <tbody><tr v-for="(line, index) in lines" :key="`print-row-${line.skuId}`" data-testid="sales-print-detail-row"><td>{{ index + 1 }}</td><td>{{ line.itemNo }}</td><td class="sales-print-left">{{ line.productName }}</td><td class="sales-print-left">{{ line.specification || '默认规格' }}</td><td class="sales-print-left">{{ line.packagingMethod || '—' }}</td><td>{{ line.cartonQuantity ? `${line.cartonQuantity} 件/箱` : '—' }}</td><td>{{ line.barcode || '—' }}</td><td>{{ line.quantity }}</td><td>{{ line.salesUnit }}</td><td>{{ formatMoney(line.unitPrice) }}</td><td>{{ formatMoney(lineAmount(line)) }}</td></tr></tbody>
        </table>
      </section>

      <section class="sales-print-section sales-print-summary">
        <div class="sales-print-section-title"><h2>金额汇总</h2><span>单位：人民币</span></div>
        <div class="sales-print-summary-list">
          <div data-testid="sales-print-summary-row" class="sales-print-summary-row"><span>商品金额</span><strong>{{ formatMoney(subtotal) }}</strong></div>
          <div data-testid="sales-print-summary-row" class="sales-print-summary-row"><span>运费</span><strong>{{ formatMoney(draft.freight) }}</strong></div>
          <div data-testid="sales-print-summary-row" class="sales-print-summary-row sales-print-summary-row--total"><span>应收合计</span><strong data-testid="sales-print-total-amount" class="sales-print-summary-value">{{ formatMoney(total) }}</strong></div>
          <div data-testid="sales-print-summary-row" class="sales-print-summary-row sales-print-summary-row--chinese sales-print-summary-row--inline-value"><span>金额大写</span><strong>{{ amountToChinese(total) }}</strong></div>
        </div>
      </section>

      <section class="sales-print-section sales-print-delivery">
        <div class="sales-print-section-title"><h2>包装与交付说明</h2></div>
        <div class="sales-print-delivery-grid"><div><span>包装方式</span><strong>—</strong></div><div><span>运输方式</span><strong>{{ selectedTransportLabel }}</strong></div><div class="sales-print-delivery-note"><span>备注</span><strong>{{ draft.remark || '—' }}</strong></div></div>
      </section>

      <footer class="sales-print-signatures"><div><span>库管员</span><i></i><small>签字 / 日期</small></div><div class="sales-print-business-representative"><span>业务代表</span><i class="sales-print-signature-line"><strong class="sales-print-signature-name">{{ salesRepresentative }}</strong></i><small>签字 / 日期</small></div><div><span>客户签字</span><i></i><small>签字 / 日期</small></div></footer>
    </article>
    </Teleport>
  </section>
</template>

<style>
@page {
  size: A4 portrait;
  margin: 0;
}

.sales-print-document {
  display: block;
  position: fixed;
  top: 0;
  left: -100000px;
  box-sizing: border-box;
  width: 100%;
  max-width: 210mm;
  padding: 12mm;
  color: #111827;
  font-family: Arial, "Microsoft YaHei", sans-serif;
}

.sales-print-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: end;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 2px solid #1f2937;
}

.sales-print-company {
  margin: 0;
  color: #172554;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: .02em;
}

.sales-print-company-subtitle {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 10px;
}

.sales-print-title-wrap {
  text-align: center;
}

.sales-print-title-wrap h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.2;
}

.sales-print-title-wrap p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 10px;
}

.sales-print-order-meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 10px;
  justify-self: end;
  color: #64748b;
  font-size: 10px;
  text-align: right;
}

.sales-print-order-meta strong {
  color: #0f172a;
  font-weight: 700;
}

.sales-print-order-meta > * {
  white-space: nowrap;
}

.sales-print-section {
  margin-top: 14px;
  page-break-inside: avoid;
}

.sales-print-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sales-print-info-card {
  padding: 13px 14px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #f8fafc;
}

.sales-print-info-card h2,
.sales-print-section-title h2 {
  margin: 0;
  color: #1e293b;
  font-size: 13px;
  font-weight: 800;
}

.sales-print-info-card dl {
  display: grid;
  gap: 6px;
  margin: 11px 0 0;
}

.sales-print-info-card dl div {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr);
  gap: 7px;
  align-items: start;
}

.sales-print-info-card dt,
.sales-print-status-grid span,
.sales-print-summary-grid span,
.sales-print-delivery-grid span {
  color: #64748b;
  font-size: 10px;
}

.sales-print-info-card dd {
  margin: 0;
  color: #1e293b;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.sales-print-status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
}

.sales-print-status-grid div {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border-right: 1px solid #dbe3ef;
}

.sales-print-status-grid div:last-child {
  border-right: 0;
}

.sales-print-status-grid strong {
  color: #0f172a;
  font-size: 11px;
}

.sales-print-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.sales-print-section-title h2,
.sales-print-section-title span {
  white-space: nowrap;
}

.sales-print-section-title span {
  color: #64748b;
  font-size: 10px;
}

.sales-print-lines {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 10px;
}

.sales-print-lines thead {
  display: table-header-group;
}

.sales-print-lines th,
.sales-print-lines td {
  padding: 6px 4px;
  border-top: 1px solid #dbe3ef;
  border-bottom: 1px solid #dbe3ef;
  vertical-align: middle;
  text-align: center;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.sales-print-lines th {
  color: #475569;
  background: #f1f5f9;
  font-size: 9px;
  font-weight: 800;
}

.sales-print-lines td {
  color: #1e293b;
  font-weight: 600;
}

.sales-print-col-index { width: 5%; }
.sales-print-col-item { width: 9%; }
.sales-print-col-name { width: 15%; }
.sales-print-col-spec { width: 11%; }
.sales-print-col-package { width: 10%; }
.sales-print-col-carton { width: 10%; }
.sales-print-col-barcode { width: 11%; }
.sales-print-col-quantity { width: 5%; }
.sales-print-col-unit { width: 5%; }
.sales-print-col-price { width: 9%; }
.sales-print-col-amount { width: 10%; }

.sales-print-lines tr {
  page-break-inside: avoid;
}

.sales-print-left {
  text-align: left !important;
}

.sales-print-summary {
  padding: 0;
  page-break-inside: avoid;
}

.sales-print-summary-list {
  padding-top: 2px;
}

.sales-print-summary-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 16px;
  min-height: 26px;
  padding: 3px 0;
}

.sales-print-summary-row span {
  color: #64748b;
  font-size: 10px;
}

.sales-print-summary-row strong {
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

.sales-print-summary-row--total {
  min-height: 30px;
}

.sales-print-summary-row--total span,
.sales-print-summary-row--total strong {
  color: #1e293b;
  font-weight: 800;
}

.sales-print-summary-row--chinese {
  min-height: 28px;
}

.sales-print-summary-row--chinese strong {
  font-size: 11px;
}

.sales-print-summary-row--inline-value {
  grid-template-columns: auto minmax(0, 1fr);
  justify-content: start;
  gap: 8px;
}

.sales-print-summary-row--inline-value strong {
  min-width: 0;
  text-align: left;
  overflow-wrap: anywhere;
}

.sales-print-delivery {
  padding: 13px 14px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  page-break-inside: avoid;
}

.sales-print-delivery-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sales-print-delivery-grid div {
  display: grid;
  gap: 5px;
}

.sales-print-delivery-grid strong {
  color: #1e293b;
  font-size: 11px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.sales-print-delivery-note {
  grid-column: span 2;
}

.sales-print-signatures {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
  page-break-inside: avoid;
}

.sales-print-signatures div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: end;
  gap: 6px;
  min-width: 0;
  color: #1e293b;
  font-size: 10px;
}

.sales-print-signatures i {
  display: block;
  height: 18px;
  border-bottom: 1px solid #94a3b8;
}

.sales-print-signatures small {
  grid-column: 2;
  color: #94a3b8;
  font-size: 8px;
  white-space: nowrap;
}

.sales-print-business-representative .sales-print-signature-line {
  grid-column: 2;
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  padding-bottom: 1px;
}

.sales-print-business-representative .sales-print-signature-name {
  color: #1e293b;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.sales-print-business-representative small {
  grid-column: 2;
}

@media print {
  body.sales-order-printing {
    background: #fff;
  }

  body.sales-order-printing > #app {
    display: none !important;
  }

  body.sales-order-printing > .sales-print-document {
    display: block !important;
    position: static !important;
    inset: auto !important;
    left: auto !important;
    top: auto !important;
    width: 100%;
    max-width: 210mm;
    margin: 0 auto;
    min-height: auto;
    padding: 12mm;
    background: #fff;
    box-shadow: none;
    overflow: visible;
  }
}
</style>
