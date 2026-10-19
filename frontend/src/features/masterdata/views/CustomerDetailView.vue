<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { ArrowLeft, FileText, Plus, Printer, Save, Trash2 } from 'lucide-vue-next';
import { currentUser } from '../../../services/authSession';
import { message } from '../../../components/feedback/message';
import { customerAccountService, type CustomerAccountService, type CustomerBills, type CustomerPlatform, type CustomerPrice } from '../customerAccountService';
import { printCustomerStatement } from '../customerPrint';
import { loadSaleProducts } from '../../sales/salesCatalogService';
import type { SaleProductOption } from '../../sales/types';
import type { Customer } from '../types';

const service = inject<CustomerAccountService>('customerAccountService', customerAccountService);
const route = useRoute();
const customer = ref<Customer | null>(null);
const platforms = ref<CustomerPlatform[]>([]);
const prices = ref<CustomerPrice[]>([]);
const products = ref<SaleProductOption[]>([]);
const tab = ref<'prices' | 'bills'>('prices');
const loading = ref(true);
const busy = ref(false);
const error = ref('');
const panelError = ref('');
const dateFrom = ref('');
const dateTo = ref('');
const appliedRange = ref({ dateFrom: '', dateTo: '' });
const bills = ref<CustomerBills | null>(null);
const skuId = ref<number | null>(null);
const unitPrice = ref<number | ''>('');
const productQuery = ref('');
let loadVersion = 0;
const has = (permission: string) => currentUser.value?.permissions.includes(permission) ?? false;
const canEdit = computed(() => has('customer:edit') && customer.value?.status === 'enabled');
const canBill = computed(() => has('sales:view'));
const platformName = computed(() => platforms.value.find(p => p.id === customer.value?.platformId)?.name || '未分配');
const filteredProducts = computed(() => products.value.filter(p => [p.itemNo, p.productName, p.skuCode, p.skuName].join(' ').toLowerCase().includes(productQuery.value.toLowerCase().trim())));
const money = (value: number) => `¥${Number(value).toFixed(2)}`;
const address = computed(() => customer.value ? [customer.value.province, customer.value.city, customer.value.district, customer.value.detailAddress].filter(Boolean).join('') : '');

async function load() {
  const version = ++loadVersion;
  loading.value = true; busy.value = false; customer.value = null; error.value = ''; panelError.value = '';
  bills.value = null; prices.value = []; products.value = []; platforms.value = [];
  tab.value = 'prices'; skuId.value = null; unitPrice.value = ''; productQuery.value = '';
  dateFrom.value = ''; dateTo.value = ''; appliedRange.value = { dateFrom: '', dateTo: '' };
  try {
    const id = Number(route.params.id);
    const record = await service.getCustomer(id);
    if (version !== loadVersion) return;
    customer.value = record;
    const results = await Promise.allSettled([service.listPlatforms(), service.listPrices(id)]);
    if (version !== loadVersion) return;
    if (results[0].status === 'fulfilled') platforms.value = results[0].value;
    else panelError.value = '平台信息加载失败，请刷新重试';
    if (results[1].status === 'fulfilled') prices.value = results[1].value;
    else panelError.value = '专属价格加载失败，请刷新重试';
    if (canEdit.value) {
      try { const options = await loadSaleProducts(); if (version === loadVersion) products.value = options; }
      catch (e) { if (version === loadVersion) panelError.value = e instanceof Error ? e.message : '货品选项加载失败'; }
    }
  } catch (e) { if (version === loadVersion) error.value = e instanceof Error ? e.message : '客户加载失败'; }
  finally { if (version === loadVersion) loading.value = false; }
}

async function savePrice() {
  if (!canEdit.value || !customer.value || busy.value) return;
  const version = loadVersion;
  if (!skuId.value || unitPrice.value === '' || !Number.isFinite(Number(unitPrice.value)) || Number(unitPrice.value) < 0) {
    message.error('请选择货品并填写非负单价'); return;
  }
  busy.value = true;
  try {
    const result = await service.savePrice(customer.value.id, skuId.value, Number(unitPrice.value));
    if (version !== loadVersion) return;
    prices.value = result;
    skuId.value = null; unitPrice.value = ''; message.success('客户专属价格已保存');
  } catch (e) { message.error(e instanceof Error ? e.message : '保存失败'); }
  finally { if (version === loadVersion) busy.value = false; }
}

async function removePrice(price: CustomerPrice) {
  if (!canEdit.value || !customer.value || busy.value || !window.confirm(`取消「${price.productName} · ${price.skuName || price.skuCode}」的专属价格？后续开单使用商品默认价。`)) return;
  const version = loadVersion;
  busy.value = true;
  try {
    await service.deletePrice(customer.value.id, price.skuId);
    if (version !== loadVersion) return;
    prices.value = prices.value.filter(p => p.skuId !== price.skuId);
    message.success('专属价格已取消');
  } catch (e) { message.error(e instanceof Error ? e.message : '取消失败'); }
  finally { if (version === loadVersion) busy.value = false; }
}

async function loadBills(page = 1, apply = false) {
  if (!canBill.value || !customer.value || busy.value) return;
  const version = loadVersion;
  const range = apply ? { dateFrom: dateFrom.value, dateTo: dateTo.value } : appliedRange.value;
  if (range.dateFrom && range.dateTo && range.dateFrom > range.dateTo) { panelError.value = '开始日期不能晚于结束日期'; return; }
  busy.value = true; panelError.value = '';
  try {
    const result = await service.listBills(customer.value.id, { page, size: 20, ...range });
    if (version !== loadVersion) return;
    bills.value = result; appliedRange.value = { ...range };
  } catch (e) { if (version === loadVersion) { bills.value = null; panelError.value = e instanceof Error ? e.message : '账单加载失败'; } }
  finally { if (version === loadVersion) busy.value = false; }
}

async function printBills() {
  if (!customer.value || !bills.value?.total || busy.value) return;
  const version = loadVersion;
  const record = customer.value;
  const range = { ...appliedRange.value };
  busy.value = true;
  try {
    const first = await service.listBills(record.id, { page: 1, size: 100, ...range });
    if (version !== loadVersion) return;
    const records = [...first.records];
    for (let page = 2; records.length < first.total; page++) {
      const next = await service.listBills(record.id, { page, size: 100, ...range });
      if (version !== loadVersion) return;
      if (!next.records.length || next.total !== first.total) throw new Error('账单已变更，请重新查询后打印');
      records.push(...next.records);
    }
    if (new Set(records.map(r => r.id)).size !== first.total) throw new Error('账单已变更，请重新查询后打印');
    printCustomerStatement(record, { ...first, records }, range.dateFrom, range.dateTo);
  } catch (e) { message.error(e instanceof Error ? e.message : '打印失败'); }
  finally { if (version === loadVersion) busy.value = false; }
}

watch(tab, value => { panelError.value = ''; if (value === 'bills') void loadBills(1); });
watch(() => route.params.id, () => void load());
onMounted(load);
onBeforeUnmount(() => { loadVersion++; });
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5" data-testid="customer-detail">
    <RouterLink :to="{ name: 'customers' }" class="inline-flex items-center gap-2 text-sm font-bold text-slate-500"><ArrowLeft class="h-4 w-4" />返回客户列表</RouterLink>
    <p v-if="error" role="alert" class="rounded-xl bg-rose-50 p-5 text-rose-700">{{ error }} <button class="underline" @click="load">重试</button></p>
    <p v-else-if="loading" class="rounded-2xl bg-white p-8 text-slate-500">正在加载客户档案…</p>
    <template v-else-if="customer">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div><p class="text-xs font-bold tracking-widest text-[#536dff]">客户档案 / {{ customer.customerNo }}</p><h1 class="mt-2 text-2xl font-black text-[#25314d]">{{ customer.customerName }}</h1></div>
        <div class="flex gap-3">
          <RouterLink v-if="has('customer:edit')" :to="{ name: 'customer-edit', params: { id: customer.id } }" class="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-bold">编辑资料</RouterLink>
          <RouterLink v-if="has('sales:create') && customer.status === 'enabled'" data-testid="customer-create-order" :to="{ name: 'sales-create', query: { customerId: customer.id } }" class="inline-flex items-center gap-2 rounded-xl bg-[#536dff] px-4 py-3 text-sm font-bold text-white"><Plus class="h-4 w-4" />给客户开单</RouterLink>
        </div>
      </header>
      <dl class="grid gap-5 rounded-2xl border border-slate-200 bg-white p-6 text-sm sm:grid-cols-4">
        <div><dt class="text-slate-400">所属平台</dt><dd class="mt-2 font-bold">{{ platformName }}</dd></div>
        <div><dt class="text-slate-400">联系人</dt><dd class="mt-2 font-bold">{{ customer.contactPerson || customer.customerName }}</dd></div>
        <div><dt class="text-slate-400">联系电话</dt><dd class="mt-2 font-bold">{{ customer.mobile || customer.telephone || '未填写' }}</dd></div>
        <div><dt class="text-slate-400">状态</dt><dd class="mt-2 font-bold" :class="customer.status === 'enabled' ? 'text-emerald-600' : 'text-slate-400'">{{ customer.status === 'enabled' ? '启用' : '停用' }}</dd></div>
        <div class="sm:col-span-4"><dt class="text-slate-400">配送地址</dt><dd class="mt-2 font-bold">{{ address || '未填写' }}</dd></div>
      </dl>
      <div class="flex gap-6 border-b border-slate-200" role="tablist" aria-label="客户业务">
        <button role="tab" :aria-selected="tab === 'prices'" class="border-b-2 px-1 py-3 text-sm font-bold" :class="tab === 'prices' ? 'border-[#536dff] text-[#536dff]' : 'border-transparent text-slate-500'" @click="tab = 'prices'">货品与专属价格</button>
        <button v-if="canBill" data-testid="customer-bills-tab" role="tab" :aria-selected="tab === 'bills'" class="border-b-2 px-1 py-3 text-sm font-bold" :class="tab === 'bills' ? 'border-[#536dff] text-[#536dff]' : 'border-transparent text-slate-500'" @click="tab = 'bills'">客户账单</button>
      </div>
      <p v-if="panelError" role="alert" class="rounded-xl bg-rose-50 p-4 text-sm text-rose-700">{{ panelError }}</p>
      <section v-if="tab === 'prices'" class="space-y-5" role="tabpanel">
        <form v-if="canEdit" class="grid items-end gap-4 rounded-2xl border border-slate-200 bg-white p-5 md:grid-cols-[1fr_1.7fr_180px_auto]" @submit.prevent="savePrice">
          <label class="space-y-2 text-sm"><span class="font-bold text-slate-500">搜索货品</span><input v-model="productQuery" class="h-11 w-full rounded-xl border border-slate-200 px-3" placeholder="货号、名称、规格" /></label>
          <label class="space-y-2 text-sm"><span class="font-bold text-slate-500">货品 / 规格 *</span><select v-model.number="skuId" data-testid="customer-price-sku" required class="h-11 w-full rounded-xl border border-slate-200 px-3"><option :value="null">请选择货品</option><option v-for="p in filteredProducts" :key="p.skuId" :value="p.skuId">{{ p.itemNo }} · {{ p.productName }} · {{ p.skuName }}</option></select></label>
          <label class="space-y-2 text-sm"><span class="font-bold text-slate-500">客户单价（元）*</span><input v-model="unitPrice" data-testid="customer-price-value" type="number" min="0" max="9999999999999999.99" step="0.01" required class="h-11 w-full rounded-xl border border-slate-200 px-3" /></label>
          <button data-testid="customer-price-save" :disabled="busy" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-bold text-white disabled:opacity-50"><Save class="h-4 w-4" />保存价格</button>
        </form>
        <div class="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <div class="p-5"><h2 class="font-black">客户专属货品</h2><p class="mt-1 text-sm text-slate-500">同一货品可为不同客户设置不同价格。开单时优先带入客户价；未设置时使用商品默认价。</p></div>
          <table class="w-full min-w-[650px] text-left text-sm"><thead class="bg-slate-50 text-slate-500"><tr><th class="p-4">货品 / 规格</th><th class="p-4">单位</th><th class="p-4 text-right">默认价</th><th class="p-4 text-right">客户价</th><th class="p-4 text-right">操作</th></tr></thead>
            <tbody class="divide-y divide-slate-100"><tr v-for="p in prices" :key="p.skuId"><td class="p-4"><p class="font-bold">{{ p.productName }} · {{ p.skuName || '默认规格' }} <span v-if="p.status !== 'enabled'" class="text-slate-400">（已停用）</span></p><p class="mt-1 text-xs text-slate-400">{{ p.itemNo }} · {{ p.skuCode }}</p></td><td class="p-4">{{ p.salesUnit }}</td><td class="p-4 text-right">{{ money(p.defaultSalePrice) }}</td><td class="p-4 text-right font-bold text-[#536dff]">{{ money(p.unitPrice) }}</td><td class="p-4"><div v-if="canEdit" class="flex justify-end gap-4"><button :disabled="busy || p.status !== 'enabled'" class="text-[#536dff] disabled:opacity-40" @click="skuId = p.skuId; unitPrice = p.unitPrice; productQuery = ''">改价</button><button :disabled="busy" class="text-rose-500" aria-label="取消专属价格" @click="removePrice(p)"><Trash2 class="h-4 w-4" /></button></div></td></tr>
              <tr v-if="!prices.length"><td colspan="5" class="p-12 text-center text-slate-400">尚未设置专属价格</td></tr></tbody>
          </table>
        </div>
      </section>
      <section v-else class="space-y-5" role="tabpanel">
        <form class="flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-5" @submit.prevent="loadBills(1, true)">
          <label class="space-y-2 text-sm"><span class="block text-slate-500">开始日期</span><input v-model="dateFrom" type="date" class="h-11 rounded-xl border border-slate-200 px-3" /></label>
          <label class="space-y-2 text-sm"><span class="block text-slate-500">结束日期</span><input v-model="dateTo" type="date" class="h-11 rounded-xl border border-slate-200 px-3" /></label>
          <button :disabled="busy" class="h-11 rounded-xl bg-[#536dff] px-5 text-sm font-bold text-white disabled:opacity-50">查询账单</button>
          <button data-testid="customer-bills-print" type="button" :disabled="busy || !bills?.total" class="ml-auto inline-flex h-11 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-bold disabled:opacity-40" @click="printBills"><Printer class="h-4 w-4" />{{ busy ? '处理中…' : '打印对账单' }}</button>
        </form>
        <p class="text-sm text-slate-500">按开单日期统计已确认单据；已收金额为开单时登记的收款。打印包含查询范围内的全部明细。</p>
        <div v-if="bills" class="grid gap-4 sm:grid-cols-3">
          <div class="rounded-2xl border border-slate-200 bg-white p-5"><p class="text-sm text-slate-500">应收合计 · {{ bills.summary.orderCount }} 单</p><p class="mt-3 text-2xl font-black">{{ money(bills.summary.totalAmount) }}</p></div>
          <div class="rounded-2xl border border-slate-200 bg-white p-5"><p class="text-sm text-slate-500">已收合计</p><p class="mt-3 text-2xl font-black text-emerald-600">{{ money(bills.summary.receivedAmount) }}</p></div>
          <div class="rounded-2xl border border-slate-200 bg-white p-5"><p class="text-sm text-slate-500">欠款合计</p><p class="mt-3 text-2xl font-black text-rose-500">{{ money(bills.summary.outstandingAmount) }}</p></div>
        </div>
        <div v-if="bills" class="overflow-x-auto rounded-2xl border border-slate-200 bg-white"><table class="w-full min-w-[650px] text-left text-sm"><thead class="bg-slate-50 text-slate-500"><tr><th class="p-4">销售单号</th><th class="p-4">日期</th><th class="p-4 text-right">应收</th><th class="p-4 text-right">已收</th><th class="p-4 text-right">欠款</th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-for="b in bills.records" :key="b.id"><td class="p-4"><RouterLink :to="{ name: 'sales-order-detail', params: { id: b.id } }" class="inline-flex items-center gap-2 font-bold text-[#536dff]"><FileText class="h-4 w-4" />{{ b.orderNo }}</RouterLink></td><td class="p-4">{{ b.orderDate }}</td><td class="p-4 text-right">{{ money(b.totalAmount) }}</td><td class="p-4 text-right">{{ money(b.receivedAmount) }}</td><td class="p-4 text-right font-bold text-rose-500">{{ money(b.outstandingAmount) }}</td></tr><tr v-if="!bills.records.length"><td colspan="5" class="p-12 text-center text-slate-400">该日期范围暂无已确认账单</td></tr></tbody></table>
          <div class="flex items-center justify-between border-t border-slate-100 p-4 text-sm text-slate-500"><span>共 {{ bills.total }} 单 · 第 {{ bills.page }} / {{ Math.max(1, Math.ceil(bills.total / bills.pageSize)) }} 页</span><div class="flex gap-4"><button :disabled="busy || bills.page <= 1" class="disabled:opacity-40" @click="loadBills(bills.page - 1)">上一页</button><button :disabled="busy || bills.page * bills.pageSize >= bills.total" class="disabled:opacity-40" @click="loadBills(bills.page + 1)">下一页</button></div></div>
        </div>
      </section>
    </template>
  </section>
</template>
