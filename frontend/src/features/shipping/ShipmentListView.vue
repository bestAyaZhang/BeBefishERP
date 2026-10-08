<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { AlertTriangle, Boxes, CheckCircle2, ChevronLeft, ChevronRight, Clock3, Plus, RotateCcw, Search, Truck } from 'lucide-vue-next';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import { shippingService } from './shippingService';
import ShipmentPreparationDialog from './ShipmentPreparationDialog.vue';
import { canUpdatePreparation } from './preparationAccess';
import { freightText, logisticsOrderStatus, recipientFullAddress, shipmentStatus, SHIPMENT_STATUSES, todayDate,
  type Shipment, type ShipmentQuery, type ShipmentSummary } from './types';

const router = useRouter();
const records = ref<Shipment[]>([]);
const filterOptions = ref<import('./types').ShippingFilterOptions>({ platforms: [], shops: [] });
const filterError = ref('');
async function loadFilterOptions() {
  try { filterOptions.value = await shippingService.getFilterOptions(); filterError.value = ''; }
  catch (e) { filterError.value = e instanceof Error ? e.message : '筛选选项加载失败'; }
}
const total = ref(0);
const loading = ref(false);
const error = ref('');
const summaryError = ref('');
const summary = ref<ShipmentSummary>({ todayCount: 0, unfinishedCount: 0, completedCount: 0, outOfStockCount: 0, partiallyShippedCount: 0 });
const query = reactive<ShipmentQuery>({ page: 1, size: 20, keyword: '', status: 'unfinished', dateFrom: '', dateTo: '', platform: '', incompleteOnly: false });
const canCreate = computed(() => currentUser.value?.permissions.includes('shipping:create'));
const canEdit = computed(() => currentUser.value?.permissions.includes('shipping:edit'));
const preparingId = ref<number | null>(null);
async function preparationSaved() {
  preparingId.value = null;
  message.success('备货状态与实际重量已保存');
  await Promise.all([load(), loadSummary()]);
}
const pages = computed(() => Math.max(1, Math.ceil(total.value / query.size)));
let listRequest = 0;
let summaryRequest = 0;

async function loadSummary() {
  const request = ++summaryRequest;
  summaryError.value = '';
  try { const result = await shippingService.summary(todayDate()); if (request === summaryRequest) summary.value = result; }
  catch (cause) { if (request === summaryRequest) summaryError.value = cause instanceof Error ? cause.message : '统计数据加载失败'; }
}

async function load() {
  const request = ++listRequest;
  loading.value = true;
  error.value = '';
  try {
    const result = await shippingService.list({ ...query });
    if (request !== listRequest) return;
    records.value = result.records;
    total.value = result.total;
    if (query.page > pages.value) { query.page = pages.value; await load(); }
  } catch (cause) { if (request === listRequest) error.value = cause instanceof Error ? cause.message : '发货列表加载失败'; }
  finally { if (request === listRequest) loading.value = false; }
}

function search() { query.page = 1; query.incompleteOnly = false; void load(); }
function showToday() { Object.assign(query, { page: 1, dateFrom: todayDate(), dateTo: todayDate(), incompleteOnly: false }); void load(); }
function showIncomplete() { Object.assign(query, { page: 1, dateFrom: '', dateTo: '', status: '', incompleteOnly: true }); void load(); }
function reset() { Object.assign(query, { page: 1, keyword: '', status: 'unfinished', dateFrom: '', dateTo: '', platform: '', platformId: undefined, shopId: undefined, unlinkedOnly: false, incompleteOnly: false }); void load(); }
function pageTo(page: number) { query.page = page; void load(); }
function maskPhone(value: string) { return /^\d{11}$/.test(value) ? `${value.slice(0, 3)}****${value.slice(-4)}` : value; }
function compactShipmentNo(value: string) { return value.length > 18 ? `${value.slice(0, 17)}…` : value; }
function openDetail(id: number) { return router.push({ name: 'shipping-detail', params: { id: String(id) } }); }
function openEdit(id: number) { return router.push({ name: 'shipping-edit', params: { id: String(id) } }); }
function openCreate() { return router.push({ name: 'shipping-new' }); }

onMounted(() => { void loadSummary(); void load(); void loadFilterOptions(); });
onBeforeUnmount(() => { listRequest++; summaryRequest++; });
</script>

<template>
  <section data-testid="shipment-list-page" class="min-w-0 w-full space-y-5 px-4 pb-8 pt-4 lg:px-6 lg:pt-8">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div><h1 class="text-2xl font-bold text-[#25314d]">发货列表</h1><p class="mt-2 text-sm text-slate-500">统一查看每天的备货内容、收件资料与物流进度。</p></div>
      <button v-if="canCreate" data-testid="shipment-add" type="button" class="inline-flex h-11 items-center gap-2 rounded-lg bg-[#536dff] px-5 text-sm font-semibold text-white shadow-sm hover:bg-[#465eea]" @click="openCreate"><Plus :size="17" />新建发货单</button>
    </header>

    <p v-if="summaryError" class="rounded-lg border border-amber-100 bg-amber-50 px-4 py-2 text-xs text-amber-700">今日统计暂时无法加载：{{ summaryError }} <button class="ml-2 underline" @click="loadSummary">重试</button></p>
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <article data-testid="shipment-summary-today" class="summary-card"><span class="summary-icon bg-indigo-50 text-indigo-600"><Truck :size="20" /></span><div><p class="summary-label">今日发货</p><p class="summary-value">{{ summary.todayCount }} 单</p></div></article>
      <article class="summary-card"><span class="summary-icon bg-amber-50 text-amber-600"><Clock3 :size="20" /></span><div><p class="summary-label">备货未完成</p><p class="summary-value">{{ summary.unfinishedCount }} 单</p></div></article>
      <article class="summary-card"><span class="summary-icon bg-emerald-50 text-emerald-600"><CheckCircle2 :size="20" /></span><div><p class="summary-label">备货已完成</p><p class="summary-value">{{ summary.completedCount }} 单</p></div></article>
      <article class="summary-card"><span class="summary-icon bg-rose-50 text-rose-600"><AlertTriangle :size="20" /></span><div><p class="summary-label">缺货 / 部分发货</p><p class="summary-value">{{ summary.outOfStockCount }} / {{ summary.partiallyShippedCount }} 单</p></div></article>
    </div>

    <form data-testid="shipment-filter-form" class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" @submit.prevent="search">
      <div class="flex flex-wrap items-end gap-3">
        <label class="min-w-[240px] flex-1 text-xs font-medium text-slate-500">关键词<div class="relative mt-1.5"><Search class="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" /><input v-model="query.keyword" data-testid="shipment-search" maxlength="200" placeholder="姓名、电话、单号、备货内容或人员" class="filter-input w-full pl-9" /></div></label>
        <label class="text-xs font-medium text-slate-500">开始日期<input v-model="query.dateFrom" data-testid="shipment-date-from" type="date" class="filter-input mt-1.5 block" /></label>
        <label class="text-xs font-medium text-slate-500">结束日期<input v-model="query.dateTo" data-testid="shipment-date-to" type="date" :min="query.dateFrom" class="filter-input mt-1.5 block" /></label>
        <label data-testid="shipment-filter-status-label" class="text-xs font-medium text-slate-500">备货状态<select v-model="query.status" data-testid="shipment-filter-status" class="filter-input mt-1.5 block min-w-32"><option value="">全部备货状态</option><option v-for="item in SHIPMENT_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label class="text-xs font-medium text-slate-500">平台<select v-model="query.platformId" aria-label="平台筛选" class="filter-input mt-1.5 block w-36" @change="query.shopId = undefined; query.platform = ''"><option :value="undefined">全部平台</option><option v-for="p in filterOptions.platforms" :key="p.id" :value="p.id">{{ p.name }}{{ p.status === 'disabled' ? '（已停用）' : '' }}</option></select></label>
        <label class="text-xs font-medium text-slate-500">店铺<select v-model="query.shopId" :disabled="query.unlinkedOnly" aria-label="店铺筛选" class="filter-input mt-1.5 block w-40"><option :value="undefined">全部店铺</option><option v-for="s in filterOptions.shops.filter(s => !query.platformId || s.platformId === query.platformId)" :key="s.id" :value="s.id">{{ s.optionLabel || s.name }}{{ s.status === 'disabled' || s.platformStatus === 'disabled' ? '（已停用）' : '' }}</option></select></label>
        <label class="text-xs font-medium text-slate-500">历史平台名称<input v-model="query.platform" data-testid="shipment-platform" :disabled="!!query.platformId" maxlength="100" placeholder="按原名称查找" class="filter-input mt-1.5 block w-36" /></label>
        <label class="flex items-center gap-2 text-xs text-slate-500"><input v-model="query.unlinkedOnly" type="checkbox" @change="query.shopId = undefined" />历史未关联</label>
        <button type="submit" class="h-10 rounded-lg bg-[#536dff] px-5 text-sm font-semibold text-white">查询</button>
        <button data-testid="shipment-reset" type="button" class="inline-flex h-10 items-center gap-1.5 rounded-lg border border-slate-200 px-4 text-sm text-slate-600" @click="reset"><RotateCcw :size="15" />重置</button>
      </div>
      <p v-if="filterError" class="mt-3 text-xs text-amber-700">{{ filterError }} <button type="button" class="underline" @click="loadFilterOptions">重试</button></p>
      <div class="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
        <button data-testid="shipment-scope-today" type="button" class="shortcut-button" @click="showToday">今天</button>
        <button data-testid="shipment-scope-incomplete" type="button" class="shortcut-button" @click="showIncomplete">全部备货未完成</button>
      </div>
    </form>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div v-if="loading" role="status" class="py-20 text-center text-sm text-slate-500">正在加载发货记录…</div>
      <div v-else-if="error" data-testid="shipment-load-error" role="alert" class="space-y-4 px-5 py-16 text-center"><p class="text-sm text-rose-600">{{ error }}</p><button data-testid="shipment-retry" type="button" class="rounded-lg border border-slate-200 px-5 py-2 text-sm text-[#536dff]" @click="load">重新加载</button></div>
      <div v-else-if="!records.length" class="px-5 py-20 text-center"><Boxes class="mx-auto h-10 w-10 text-slate-300" /><p class="mt-4 text-sm font-semibold text-slate-600">当前条件下暂无发货记录</p><p class="mt-2 text-xs text-slate-400">可调整筛选条件或新建发货单。</p></div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[1420px] text-left text-sm">
          <thead class="bg-slate-50 text-xs font-medium text-slate-500"><tr><th class="table-cell">发货单</th><th class="table-cell">收件信息</th><th class="table-cell">备货清单 / 备注</th><th class="table-cell">物流信息</th><th class="table-cell">重量 / 运费预测</th><th class="table-cell">下单 / 备货状态</th><th class="table-cell">备货人 / 下单人</th><th class="table-cell text-right">操作</th></tr></thead>
          <tbody class="divide-y divide-slate-100"><tr v-for="row in records" :key="row.id" :data-testid="`shipment-row-${row.id}`" class="align-top hover:bg-slate-50/60">
            <td class="table-cell"><p :data-testid="`shipment-number-${row.id}`" :title="row.shipmentNo" class="whitespace-nowrap font-semibold text-slate-800">{{ compactShipmentNo(row.shipmentNo) }}</p><p class="mt-1 text-xs text-slate-400">{{ row.content.shipmentDate }} · {{ row.content.platform || '未填平台' }}</p><p class="mt-1 text-xs text-slate-400">{{ row.content.shopName }}</p></td>
            <td class="table-cell"><p class="font-medium text-slate-800">{{ row.content.recipientName }} <span class="ml-2 font-normal text-slate-500">{{ maskPhone(row.content.recipientPhone) }}</span></p><p class="mt-1.5 max-w-72 break-words text-xs leading-5 text-slate-500">{{ recipientFullAddress(row.content) }}</p></td>
            <td class="table-cell"><p :data-testid="`shipment-preparation-${row.id}`" :title="row.content.preparationContent" class="line-clamp-3 max-w-72 whitespace-pre-wrap break-words leading-6 text-slate-700">{{ row.content.preparationContent }}</p><p v-if="row.content.remark" :data-testid="`shipment-remark-${row.id}`" :title="`备注：${row.content.remark}`" class="mt-1 line-clamp-1 max-w-72 text-xs text-slate-400">备注：{{ row.content.remark }}</p></td>
            <td class="table-cell"><p class="text-slate-700">{{ row.content.logisticsCompany || '待下单' }}</p><p class="mt-1 max-w-40 break-all text-xs text-slate-400">{{ row.logisticsOrderState === 'cancelled' ? '原运单已作废' : row.content.trackingNo || '暂无物流单号' }}</p></td>
            <td class="table-cell whitespace-nowrap"><p :data-testid="`shipment-actual-weight-${row.id}`" class="font-medium">实际：{{ row.preparation?.actualWeight == null ? '未称重' : `${row.preparation.actualWeight} kg` }}</p><p class="mt-1 text-xs text-slate-500">下单：{{ row.content.orderDraft.weight == null ? '未填写' : `${row.content.orderDraft.weight} kg` }}</p><p class="mt-1 text-xs text-slate-400">{{ freightText(row.content.estimatedFreight) }}</p></td>
            <td class="table-cell"><div class="flex items-start gap-3"><div class="shrink-0"><p class="mb-1 whitespace-nowrap text-[11px] text-slate-400">安能下单</p><span :data-testid="`shipment-logistics-status-${row.id}`" class="inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium" :class="logisticsOrderStatus(row.logisticsOrderState).tone">{{ logisticsOrderStatus(row.logisticsOrderState).label }}</span></div><div class="shrink-0"><p class="mb-1 whitespace-nowrap text-[11px] text-slate-400">备货</p><span :data-testid="`shipment-preparation-status-${row.id}`" class="inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium" :class="shipmentStatus(row.content.status).tone">{{ shipmentStatus(row.content.status).label }}</span></div></div></td>
            <td class="table-cell"><p class="max-w-36 break-words text-slate-700">{{ row.content.preparers.join('、') || '未指定' }}</p><p class="mt-1 text-xs text-slate-400">{{ row.content.orderer || '—' }}</p></td>
            <td class="table-cell text-right"><div class="flex items-center justify-end gap-2 whitespace-nowrap"><button :data-testid="`shipment-detail-${row.id}`" type="button" class="shipment-action-button" @click="openDetail(row.id)">查看详情</button><button v-if="canEdit" :data-testid="`shipment-edit-${row.id}`" type="button" class="shipment-action-button" @click="openEdit(row.id)">编辑</button><button v-if="canUpdatePreparation(row, currentUser)" :data-testid="`shipment-prepare-${row.id}`" type="button" class="shipment-action-button" @click="preparingId = row.id">更新备货</button></div></td>
          </tr></tbody>
        </table>
      </div>
      <footer class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 py-4 text-sm text-slate-500"><span>{{ error ? '—' : `共 ${total} 条记录` }}</span><div class="flex items-center gap-3"><button aria-label="上一页" :disabled="loading || !!error || query.page <= 1" type="button" class="page-button" @click="pageTo(query.page - 1)"><ChevronLeft :size="16" /></button><span>{{ query.page }} / {{ pages }}</span><button data-testid="shipment-next-page" aria-label="下一页" :disabled="loading || !!error || query.page >= pages" type="button" class="page-button" @click="pageTo(query.page + 1)"><ChevronRight :size="16" /></button></div></footer>
    </div>
    <ShipmentPreparationDialog :shipment-id="preparingId" @close="preparingId = null" @saved="preparationSaved" />
  </section>
</template>

<style scoped>
.summary-card { @apply flex min-h-28 items-center gap-4 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm; }
.summary-icon { @apply flex h-11 w-11 shrink-0 items-center justify-center rounded-xl; }
.summary-label { @apply text-xs font-medium text-slate-500; }
.summary-value { @apply mt-1 text-xl font-bold text-[#25314d]; }
.filter-input { @apply h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10; }
.shortcut-button { @apply rounded-full border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700; }
.table-cell { @apply px-5 py-4; }
.shipment-action-button { @apply inline-flex h-8 min-w-20 items-center justify-center whitespace-nowrap rounded-md border border-indigo-200 bg-white px-3 text-xs font-medium text-[#536dff] transition-colors hover:border-indigo-300 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 focus-visible:ring-offset-2; }
.page-button { @apply rounded-md border border-slate-200 p-1.5 disabled:opacity-30; }
</style>
