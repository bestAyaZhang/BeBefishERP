<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowUpRight, Boxes, ChevronDown, ChevronLeft, ChevronRight, Download, Plus, RotateCcw, Search, SlidersHorizontal } from 'lucide-vue-next';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import { shippingService } from './shippingService';
import { downloadShipmentCsv } from './shipmentCsv';
import ShipmentPreparationDialog from './ShipmentPreparationDialog.vue';

import { canEditPreparationContent, canUpdatePreparation } from './preparationAccess';
import { freightText, logisticsOrderStatus, recipientFullAddress, shipmentStatus, SHIPMENT_STATUSES, todayDate,
  type Shipment, type ShipmentQuery, type ShipmentSummary } from './types';

const router = useRouter();
const records = ref<Shipment[]>([]);
const filterOptions = ref<import('./types').ShippingFilterOptions>({ platforms: [], shops: [], logisticsCompanies: [] });
const filterError = ref('');
async function loadFilterOptions() {
  try { filterOptions.value = await shippingService.getFilterOptions(); filterError.value = ''; }
  catch (e) { filterError.value = e instanceof Error ? e.message : '筛选选项加载失败'; }
}
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const error = ref('');
const summaryError = ref('');
const summary = ref<ShipmentSummary>({ todayCount: 0, unfinishedCount: 0, completedCount: 0, outOfStockCount: 0, partiallyShippedCount: 0 });
const query = reactive<ShipmentQuery>({ page: 1, size: 20, keyword: '', status: 'unfinished', dateFrom: '', dateTo: '', platform: '', logisticsCompany: '', incompleteOnly: false });
const advancedOpen = ref(false);
const appliedQuery = ref<ShipmentQuery>({ ...query });
const summaryLoaded = ref(false);
const advancedFilterCount = computed(() => [query.dateFrom || query.dateTo, query.platformId, query.shopId, query.platform, query.unlinkedOnly].filter(Boolean).length);
const scopeText = computed(() => {
  const filter = appliedQuery.value;
  const dates = filter.dateFrom && filter.dateTo && filter.dateFrom === filter.dateTo
    ? filter.dateFrom
    : filter.dateFrom || filter.dateTo ? `${filter.dateFrom || '不限'} 至 ${filter.dateTo || '不限'}` : '不限日期';
  const status = filter.incompleteOnly ? '全部备货未完成' : filter.status ? shipmentStatus(filter.status).label : '全部备货状态';
  return `${dates} · ${status}${filter.logisticsCompany ? ` · ${filter.logisticsCompany}` : ''}`;
});
const recordRange = computed(() => !total.value ? '0 条' : `${(appliedQuery.value.page - 1) * appliedQuery.value.size + 1}–${Math.min(appliedQuery.value.page * appliedQuery.value.size, total.value)} 条`);
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
let disposed = false;

async function loadSummary() {
  const request = ++summaryRequest;
  summaryError.value = '';
  try { const result = await shippingService.summary(todayDate()); if (request === summaryRequest) { summary.value = result; summaryLoaded.value = true; } }
  catch (cause) { if (request === summaryRequest) summaryError.value = cause instanceof Error ? cause.message : '统计数据加载失败'; }
}

async function load() {
  const request = ++listRequest;
  loading.value = true;
  error.value = '';
  const requestedQuery = { ...query };
  try {
    const result = await shippingService.list(requestedQuery);
    if (request !== listRequest) return;
    records.value = result.records;
    total.value = result.total;
    appliedQuery.value = requestedQuery;
    if (query.page > pages.value) { query.page = pages.value; await load(); }
  } catch (cause) { if (request === listRequest) error.value = cause instanceof Error ? cause.message : '发货列表加载失败'; }
  finally { if (request === listRequest) loading.value = false; }
}

async function exportShipments() {
  if (exporting.value || loading.value || error.value || !total.value) return;
  const { page, size, ...filters } = appliedQuery.value;
  exporting.value = true;
  try {
    const rows = await shippingService.listForExport(filters);
    if (disposed) return;
    if (!rows.length) {
      message.warning('当前筛选条件下暂无可导出的发货记录');
      return;
    }
    downloadShipmentCsv(rows, `发货列表-${todayDate()}.csv`);
    message.success(`已导出 ${rows.length} 条发货记录`);
  } catch (cause) {
    if (!disposed) message.error(cause instanceof Error ? `导出失败：${cause.message}` : '发货记录导出失败');
  } finally {
    exporting.value = false;
  }
}

function search() { query.page = 1; query.incompleteOnly = false; void load(); }
function filterStatus(status: ShipmentQuery['status']) { query.status = status; search(); }
function filterLogisticsCompany() { query.page = 1; void load(); }
function showToday() { Object.assign(query, { page: 1, dateFrom: todayDate(), dateTo: todayDate(), incompleteOnly: false }); void load(); }
function showIncomplete() { Object.assign(query, { page: 1, dateFrom: '', dateTo: '', status: '', incompleteOnly: true }); void load(); }
function reset() { Object.assign(query, { page: 1, keyword: '', status: 'unfinished', dateFrom: '', dateTo: '', platform: '', logisticsCompany: '', platformId: undefined, shopId: undefined, unlinkedOnly: false, incompleteOnly: false }); void load(); }
function pageTo(page: number) { query.page = page; void load(); }
function maskPhone(value: string) { return /^\d{11}$/.test(value) ? `${value.slice(0, 3)}****${value.slice(-4)}` : value; }
function compactShipmentNo(value: string) { return value.length > 18 ? `${value.slice(0, 17)}…` : value; }
function openDetail(id: number) { return router.push({ name: 'shipping-detail', params: { id: String(id) } }); }
function openEdit(id: number) { return router.push({ name: 'shipping-edit', params: { id: String(id) } }); }
function openCreate() { return router.push({ name: 'shipping-new' }); }

onMounted(() => { void loadSummary(); void load(); void loadFilterOptions(); });
onBeforeUnmount(() => { disposed = true; listRequest++; summaryRequest++; });
</script>

<template>
  <section data-testid="shipment-list-page" class="shipment-page">
    <header class="page-heading">
      <h1>发货列表</h1>
      <div class="heading-actions">
        <button data-testid="shipment-export" type="button" class="secondary-button export-button"
          :disabled="loading || !!error || !total || exporting" title="导出当前筛选条件下的全部发货记录" @click="exportShipments">
          <Download :size="16" aria-hidden="true" />{{ exporting ? '导出中…' : '导出数据' }}
        </button>
        <button v-if="canCreate" data-testid="shipment-add" type="button" class="primary-button" @click="openCreate"><Plus :size="16" aria-hidden="true" />新建发货单</button>
      </div>
    </header>

    <section class="today-summary" aria-label="今日概览">
      <div class="summary-scope"><span>今日概览</span><time :datetime="todayDate()">{{ todayDate().slice(5).replace('-', ' / ') }}</time></div>
      <div data-testid="shipment-summary-today" class="summary-item"><span>今日发货</span><strong>{{ summaryLoaded ? summary.todayCount : '—' }} <small>单</small></strong></div>
      <div class="summary-item"><span>备货未完成</span><strong>{{ summaryLoaded ? summary.unfinishedCount : '—' }} <small>单</small></strong></div>
      <div class="summary-item"><span>备货已完成</span><strong>{{ summaryLoaded ? summary.completedCount : '—' }} <small>单</small></strong></div>
      <div class="summary-item summary-exceptions"><span>缺货 / 部分发货</span><strong>{{ summaryLoaded ? summary.outOfStockCount : '—' }} / {{ summaryLoaded ? summary.partiallyShippedCount : '—' }} <small>单</small></strong></div>
    </section>
    <p v-if="summaryError" role="status" class="inline-notice">今日统计暂时无法加载：{{ summaryError }} <button type="button" class="text-button" @click="loadSummary">重试</button></p>

    <div class="work-sheet">
      <div class="status-tabs" role="group" aria-label="按备货状态筛选">
        <button type="button" :aria-pressed="!appliedQuery.status && !appliedQuery.incompleteOnly" :class="{ selected: !appliedQuery.status && !appliedQuery.incompleteOnly }" @click="filterStatus('')">全部</button>
        <button v-for="item in SHIPMENT_STATUSES" :key="item.value" type="button" :aria-pressed="appliedQuery.status === item.value && !appliedQuery.incompleteOnly" :class="{ selected: appliedQuery.status === item.value && !appliedQuery.incompleteOnly }" @click="filterStatus(item.value)">{{ item.label }}</button>
      </div>

      <form data-testid="shipment-filter-form" class="filter-form" @submit.prevent="search">
        <div class="filter-toolbar">
          <label class="search-field"><span class="sr-only">关键词</span><Search :size="16" aria-hidden="true" /><input v-model="query.keyword" data-testid="shipment-search" maxlength="200" placeholder="搜索姓名、电话、单号或备货内容" /></label>
          <button type="submit" class="secondary-button search-button">查询</button>
          <label class="carrier-filter"><span class="sr-only">物流公司</span><select v-model="query.logisticsCompany" aria-label="物流公司筛选" class="filter-input" @change="filterLogisticsCompany"><option value="">全部物流公司</option><option v-for="company in filterOptions.logisticsCompanies ?? []" :key="company" :value="company">{{ company }}</option></select></label>
          <div class="quick-filters">
            <button data-testid="shipment-scope-today" type="button" class="text-button" @click="showToday">今天</button>
            <span class="filter-separator" aria-hidden="true"></span>
            <button data-testid="shipment-scope-incomplete" type="button" class="text-button" :class="{ 'active-shortcut': appliedQuery.incompleteOnly }" @click="showIncomplete">全部备货未完成</button>
          </div>
          <button data-testid="shipment-advanced-toggle" type="button" class="secondary-button advanced-toggle" :aria-expanded="advancedOpen" aria-controls="shipment-advanced-filters" @click="advancedOpen = !advancedOpen"><SlidersHorizontal :size="15" aria-hidden="true" />筛选<span v-if="advancedFilterCount" class="filter-count">{{ advancedFilterCount }}</span><ChevronDown :size="14" :class="{ 'rotate-180': advancedOpen }" aria-hidden="true" /></button>
          <button data-testid="shipment-reset" type="button" class="text-button reset-button" @click="reset"><RotateCcw :size="14" aria-hidden="true" />重置</button>
        </div>
        <div id="shipment-advanced-filters" v-show="advancedOpen" class="advanced-filters">
          <label>开始日期<input v-model="query.dateFrom" data-testid="shipment-date-from" type="date" class="filter-input" /></label>
          <label>结束日期<input v-model="query.dateTo" data-testid="shipment-date-to" type="date" :min="query.dateFrom" class="filter-input" /></label>
          <label data-testid="shipment-filter-status-label">备货状态<select v-model="query.status" data-testid="shipment-filter-status" class="filter-input"><option value="">全部备货状态</option><option v-for="item in SHIPMENT_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
          <label>平台<select v-model="query.platformId" aria-label="平台筛选" class="filter-input" @change="query.shopId = undefined; query.platform = ''"><option :value="undefined">全部平台</option><option v-for="p in filterOptions.platforms" :key="p.id" :value="p.id">{{ p.name }}{{ p.status === 'disabled' ? '（已停用）' : '' }}</option></select></label>
          <label>店铺<select v-model="query.shopId" :disabled="query.unlinkedOnly" aria-label="店铺筛选" class="filter-input"><option :value="undefined">全部店铺</option><option v-for="s in filterOptions.shops.filter(s => !query.platformId || s.platformId === query.platformId)" :key="s.id" :value="s.id">{{ s.optionLabel || s.name }}{{ s.status === 'disabled' || s.platformStatus === 'disabled' ? '（已停用）' : '' }}</option></select></label>
          <label>历史平台名称<input v-model="query.platform" data-testid="shipment-platform" :disabled="!!query.platformId" maxlength="100" placeholder="按原名称查找" class="filter-input" /></label>
          <label class="unlinked-filter"><input v-model="query.unlinkedOnly" type="checkbox" @change="query.shopId = undefined" />历史未关联</label>
        </div>
        <p v-if="filterError" class="inline-notice">{{ filterError }} <button type="button" class="text-button" @click="loadFilterOptions">重试</button></p>
      </form>

      <div class="list-caption"><h2>发货记录 <span>{{ error || loading ? '—' : total }} <small>条</small></span></h2><p>{{ scopeText }}</p></div>
      <div class="shipment-table-frame" :aria-busy="loading">
        <table class="shipment-table">
          <colgroup><col class="identity-column" /><col class="preparation-column" /><col class="logistics-column" /><col class="weight-column" /><col class="status-column" /><col class="actions-column" /></colgroup>
          <thead><tr><th scope="col">发货单 / 收件信息</th><th scope="col">备货清单 / 备注</th><th scope="col">物流信息</th><th scope="col" class="numeric-cell">重量 / 运费预测</th><th scope="col">备货状态 / 人员</th><th scope="col" class="actions-heading">操作</th></tr></thead>
          <tbody v-if="loading && !records.length"><tr v-for="n in 3" :key="n" class="skeleton-row" aria-hidden="true"><td v-for="c in 6" :key="c"><span class="skeleton-line"></span><span class="skeleton-line short"></span></td></tr></tbody>
          <tbody v-else-if="error"><tr><td colspan="6"><div data-testid="shipment-load-error" role="alert" class="list-state"><p>{{ error }}</p><button data-testid="shipment-retry" type="button" class="secondary-button" @click="load">重新加载</button></div></td></tr></tbody>
          <tbody v-else-if="!records.length"><tr><td colspan="6"><div class="list-state"><Boxes :size="28" aria-hidden="true" /><p>当前条件下暂无发货记录</p><span>可调整筛选条件或新建发货单。</span><button type="button" class="text-button" @click="reset">重置筛选</button></div></td></tr></tbody>
          <tbody v-else><tr v-for="row in records" :key="row.id" :data-testid="`shipment-row-${row.id}`" class="shipment-row">
            <td class="identity-cell">
              <button :data-testid="`shipment-number-${row.id}`" :title="row.shipmentNo" type="button" class="shipment-number" @click="openDetail(row.id)">{{ compactShipmentNo(row.shipmentNo) }}<ArrowUpRight :size="13" aria-hidden="true" /></button>
              <p class="record-meta"><time>{{ row.content.shipmentDate }}</time><span>{{ row.content.platform || '未填平台' }}<template v-if="row.content.shopName"> · {{ row.content.shopName }}</template></span></p>
              <p class="recipient-name">{{ row.content.recipientName }} <span>{{ maskPhone(row.content.recipientPhone) }}</span></p>
              <p class="recipient-address" :title="recipientFullAddress(row.content)">{{ recipientFullAddress(row.content) }}</p>
            </td>
            <td class="preparation-cell"><span class="mobile-cell-label">备货清单</span><p :data-testid="`shipment-preparation-${row.id}`" :title="row.content.preparationContent" class="preparation-content line-clamp-3">{{ row.content.preparationContent }}</p><p v-if="row.content.remark" :data-testid="`shipment-remark-${row.id}`" :title="`备注：${row.content.remark}`" class="record-remark line-clamp-1">备注：{{ row.content.remark }}</p></td>
            <td class="logistics-cell"><span class="mobile-cell-label">物流</span><p class="carrier-name">{{ row.content.logisticsCompany || '待下单' }}</p><span :data-testid="`shipment-logistics-status-${row.id}`" class="status-pill logistics-status" :class="logisticsOrderStatus(row.logisticsOrderState).tone">{{ logisticsOrderStatus(row.logisticsOrderState).label }}</span><p class="tracking-number">{{ row.logisticsOrderState === 'cancelled' ? '原运单已作废' : row.content.trackingNo || '暂无物流单号' }}</p></td>
            <td class="numeric-cell weight-cell"><span class="mobile-cell-label">重量 / 运费</span><p :data-testid="`shipment-actual-weight-${row.id}`" class="actual-weight">{{ row.preparation?.actualWeight == null ? '未称重' : `${row.preparation.actualWeight} kg` }}</p><p class="weight-draft">下单 {{ row.content.orderDraft.weight == null ? '—' : `${row.content.orderDraft.weight} kg` }}</p><p class="freight-value">{{ freightText(row.content.estimatedFreight) }}</p></td>
            <td class="preparation-status-cell"><span :data-testid="`shipment-preparation-status-${row.id}`" class="status-pill preparation-status" :class="shipmentStatus(row.content.status).tone">{{ shipmentStatus(row.content.status).label }}</span><p class="assigned-person"><span>备货</span>{{ row.content.preparers.join('、') || '未指定' }}</p><p class="assigned-person"><span>下单</span>{{ row.content.orderer || '—' }}</p></td>
            <td class="actions-cell"><div class="row-actions"><button v-if="canUpdatePreparation(row, currentUser)" :data-testid="`shipment-prepare-${row.id}`" type="button" class="prepare-button" @click="preparingId = row.id">更新备货</button><div class="secondary-actions"><button :data-testid="`shipment-detail-${row.id}`" type="button" class="text-button" @click="openDetail(row.id)">查看详情</button><button v-if="canEdit || canEditPreparationContent(row, currentUser)" :data-testid="`shipment-edit-${row.id}`" type="button" class="text-button" @click="openEdit(row.id)">编辑</button></div></div></td>
          </tr></tbody>
        </table>
        <p v-if="loading" role="status" class="loading-caption">正在加载发货记录…</p>
      </div>
      <footer class="list-footer"><span>{{ error ? '—' : `共 ${total} 条记录` }}<span v-if="!error && total" class="record-range"> · 当前 {{ recordRange }}</span></span><div class="pagination"><button aria-label="上一页" :disabled="loading || !!error || query.page <= 1" type="button" class="page-button" @click="pageTo(query.page - 1)"><ChevronLeft :size="16" /></button><span>{{ appliedQuery.page }} <span class="page-divider">/ {{ pages }}</span></span><button data-testid="shipment-next-page" aria-label="下一页" :disabled="loading || !!error || query.page >= pages" type="button" class="page-button" @click="pageTo(query.page + 1)"><ChevronRight :size="16" /></button></div></footer>
    </div>
    <ShipmentPreparationDialog :shipment-id="preparingId" @close="preparingId = null" @saved="preparationSaved" />
  </section>
</template>

<style scoped>
/* Fluid sheet: available width = viewport - 244px navigation - 48px gutters.
   Table columns: 24 + 25 + 16 + 12 + 13 + 10 = 100%. */
.shipment-page { --ink: #25314d; --muted: #5f6b7a; --line: #e8ebef; --blue: #2459b8; width: 100%; min-width: 0; padding: 28px 24px 32px; color: var(--ink); font-size: 14px; }
.page-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.page-heading h1 { font-size: 24px; line-height: 32px; font-weight: 600; letter-spacing: -.025em; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.primary-button, .secondary-button, .prepare-button, .page-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; border-radius: 6px; font-weight: 500; white-space: nowrap; }
.primary-button { min-height: 36px; padding: 0 14px; background: var(--blue); color: white; font-size: 13px; }
.primary-button:hover { background: #1d4896; }
.secondary-button { height: 36px; padding: 0 12px; border: 1px solid #d1d7df; background: white; color: var(--ink); font-size: 13px; }
.secondary-button:hover, .page-button:not(:disabled):hover { background: #f3f5f8; border-color: #a9b4c3; }
.export-button:disabled { border-color: var(--line); background: #f7f8fa; color: #697586; cursor: not-allowed; }
.search-button { border-color: #c8daf5; color: var(--blue); }
.search-button:hover { border-color: #aac7ef; background: #eff6ff; }
.text-button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 32px; white-space: nowrap; font-size: 12px; color: var(--muted); }
.text-button:hover { color: var(--ink); text-decoration: underline; text-underline-offset: 4px; }
button:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; }
.today-summary { display: grid; grid-template-columns: 114px repeat(4, minmax(0, 1fr)); margin-bottom: 24px; font-variant-numeric: tabular-nums; }
.summary-scope { display: flex; flex-direction: column; justify-content: center; gap: 5px; font-size: 12px; color: var(--muted); }
.summary-scope time { font-size: 12px; }
.summary-item { display: flex; flex-direction: column; gap: 4px; padding-left: 24px; border-left: 1px solid #dfe4eb; }
.summary-item > span { font-size: 12px; color: var(--muted); }
.summary-item strong { font-size: 24px; line-height: 30px; font-weight: 500; }
.summary-item small { font-size: 12px; font-weight: 400; color: var(--muted); }
.work-sheet { border: 1px solid var(--line); border-radius: 12px; background: white; overflow: hidden; }
.status-tabs { display: flex; gap: 28px; padding: 0 20px; border-bottom: 1px solid var(--line); overflow-x: auto; }
.status-tabs button { flex: none; position: relative; min-height: 49px; padding: 0 2px; color: var(--muted); font-size: 13px; }
.status-tabs button:hover { color: var(--ink); }
.status-tabs button.selected { color: var(--ink); font-weight: 600; }
.status-tabs button.selected::after { content: ''; position: absolute; height: 2px; inset: auto 0 0; background: var(--blue); }
.filter-form { padding: 18px 20px 0; }
.filter-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.search-field { display: flex; align-items: center; gap: 9px; width: clamp(240px, 27vw, 320px); height: 36px; padding: 0 11px; border: 1px solid #d1d7df; border-radius: 6px; color: var(--muted); }
.search-field:focus-within { outline: 2px solid var(--blue); outline-offset: 2px; }
.search-field input { width: 100%; min-width: 0; background: transparent; outline: none; color: var(--ink); font-size: 13px; }
.search-field input:focus-visible { outline: none; }
.search-field input::placeholder, .filter-input::placeholder { color: #657181; opacity: 1; }
.carrier-filter { flex: none; width: 166px; }
.quick-filters { display: flex; align-items: center; gap: 10px; margin-right: auto; }
.filter-separator { height: 12px; width: 1px; background: #dfe4eb; }
.active-shortcut { color: var(--blue); font-weight: 500; }
.filter-count { padding: 0 4px; color: var(--blue); font-variant-numeric: tabular-nums; }
.reset-button { padding: 0 2px; }
.advanced-filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 16px; margin-top: 18px; padding: 18px 0; border-top: 1px solid var(--line); }
.advanced-filters > label { display: flex; flex-direction: column; gap: 7px; font-size: 12px; color: var(--muted); min-width: 0; }
.filter-input { width: 100%; min-width: 0; height: 36px; padding: 0 10px; border: 1px solid #d1d7df; border-radius: 6px; background: white; color: var(--ink); font-size: 13px; }
.filter-input:disabled { background: #f3f5f8; color: var(--muted); cursor: not-allowed; }
.advanced-filters .unlinked-filter { flex-direction: row; align-items: center; gap: 8px; }
.unlinked-filter input { width: 15px; height: 15px; accent-color: var(--blue); }
.inline-notice { padding: 8px 0; font-size: 12px; color: #92400e; }
.list-caption { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 6px 12px; padding: 20px 20px 10px; }
.list-caption h2 { font-size: 14px; font-weight: 500; }
.list-caption h2 > span { margin-left: 7px; font-variant-numeric: tabular-nums; }
.list-caption small, .list-caption p { font-size: 12px; font-weight: 400; color: var(--muted); }
.shipment-table-frame { position: relative; width: 100%; max-height: min(65dvh, 760px); overflow: auto; }
.shipment-table { width: 100%; min-width: 1000px; table-layout: fixed; text-align: left; font-variant-numeric: tabular-nums; }
.identity-column { width: 24%; }.preparation-column { width: 25%; }.logistics-column { width: 16%; }.weight-column { width: 12%; }.status-column { width: 13%; }.actions-column { width: 10%; }
.shipment-table th { position: sticky; top: 0; z-index: 1; height: 38px; padding: 0 12px; color: var(--muted); background: white; font-size: 12px; font-weight: 400; }
.shipment-table td { vertical-align: top; padding: 18px 12px; border-top: 1px solid #edf0f3; font-size: 13px; }
.shipment-table th:first-child, .shipment-table td:first-child { padding-left: 20px; }
.shipment-table th:last-child, .shipment-table td:last-child { padding-right: 20px; }
.shipment-row:hover { background: #fcfcfd; }
.shipment-number { display: inline-flex; align-items: center; gap: 4px; max-width: 100%; font-family: 'Inter Variable', Inter, sans-serif; font-variant-numeric: tabular-nums; font-size: 12px; font-weight: 600; line-height: 20px; white-space: nowrap; }
.shipment-number svg { color: var(--muted); flex: none; }
.shipment-number:hover { color: var(--blue); }
.record-meta { display: flex; flex-wrap: wrap; gap: 2px 8px; margin-top: 3px; color: var(--muted); font-size: 12px; line-height: 18px; }
.recipient-name { margin-top: 12px; font-weight: 500; line-height: 20px; }
.recipient-name span { margin-left: 6px; font-size: 12px; font-weight: 400; color: var(--muted); white-space: nowrap; }
.recipient-address { margin-top: 3px; font-size: 12px; line-height: 19px; color: var(--muted); overflow-wrap: anywhere; }
.preparation-content { line-height: 22px; white-space: pre-wrap; overflow-wrap: anywhere; }
.record-remark { margin-top: 7px; font-size: 12px; color: var(--muted); line-height: 19px; overflow-wrap: anywhere; }
.carrier-name { line-height: 21px; overflow-wrap: anywhere; }
.status-pill { display: inline-flex; align-items: center; flex: none; min-height: 22px; border-radius: 4px; padding: 2px 7px; font-size: 12px; font-weight: 500; white-space: nowrap; line-height: 18px; }
.logistics-status { margin-top: 7px; }
.tracking-number { margin-top: 6px; font-size: 12px; line-height: 19px; color: var(--muted); overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.numeric-cell { text-align: right; font-variant-numeric: tabular-nums; }
.actual-weight { line-height: 22px; font-weight: 500; white-space: nowrap; }
.weight-draft { margin-top: 5px; font-size: 12px; color: var(--muted); white-space: nowrap; }
.freight-value { margin-top: 5px; font-size: 12px; color: var(--muted); white-space: nowrap; }
.assigned-person { display: flex; gap: 6px; margin-top: 9px; color: var(--muted); font-size: 12px; line-height: 19px; overflow-wrap: anywhere; }
.assigned-person + .assigned-person { margin-top: 4px; }
.assigned-person > span { flex: none; }
.actions-heading { text-align: right; }
.row-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 7px; }
.prepare-button { height: 30px; padding: 0 9px; border: 1px solid #c8daf5; background: #eff6ff; color: var(--blue); font-size: 12px; }
.prepare-button:hover { border-color: #aac7ef; background: #dbeafe; }
.secondary-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 0 10px; }
.secondary-actions .text-button { min-height: 28px; color: var(--blue); }
.secondary-actions .text-button:hover { color: #1d4896; }
.mobile-cell-label { display: none; }
.list-state { display: flex; min-height: 190px; padding: 32px 16px; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; color: var(--muted); }
.list-state p { font-size: 14px; color: var(--ink); }
.list-state > span { font-size: 12px; }
.loading-caption { padding: 10px 20px; color: var(--muted); font-size: 12px; }
.skeleton-line { display: block; width: 80%; height: 12px; margin: 5px 0 12px; border-radius: 3px; background: #eef1f4; }
.skeleton-line.short { width: 55%; }
.list-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 13px 20px; border-top: 1px solid var(--line); font-size: 12px; color: var(--muted); font-variant-numeric: tabular-nums; }
.pagination { display: flex; align-items: center; gap: 14px; }
.page-button { height: 30px; width: 30px; border: 1px solid #d1d7df; color: var(--ink); }
.page-button:disabled { border-color: #edf0f3; color: #9aa4b2; cursor: not-allowed; }
.page-divider { margin-left: 3px; color: var(--muted); }
@media (min-width: 641px) and (max-width: 1250px) {
  .shipment-page { padding: 24px 16px; }
  .today-summary { grid-template-columns: 94px repeat(4, minmax(0, 1fr)); }
  .summary-item { padding-left: 16px; }
  .quick-filters { order: 4; flex-basis: 100%; margin-top: 2px; }
  .advanced-toggle { margin-left: auto; }
  .shipment-table { display: block; min-width: 0; }
  .shipment-table colgroup { display: none; }
  .shipment-table thead { display: block; position: sticky; top: 0; z-index: 2; background: white; }
  .shipment-table tbody { display: block; }
  .shipment-table tr { display: grid; grid-template-columns: 28% 30% 24% 18%; }
  .shipment-table thead th { display: flex; align-items: center; position: static; }
  .shipment-table thead th:nth-child(4), .shipment-table thead th:last-child { display: none; }
  .shipment-table .shipment-row { grid-template-rows: auto auto; padding: 16px 20px; gap: 14px 0; border-top: 1px solid #edf0f3; }
  .shipment-table .shipment-row td { min-width: 0; padding: 0 12px 0 0; border: 0; }
  .shipment-table .identity-cell { grid-column: 1; grid-row: 1 / 3; }
  .shipment-table .preparation-cell { grid-column: 2; grid-row: 1 / 3; }
  .shipment-table .logistics-cell { grid-column: 3; grid-row: 1; }
  .shipment-table .weight-cell { grid-column: 3; grid-row: 2; text-align: left; }
  .weight-cell .mobile-cell-label { display: block; font-size: 12px; color: var(--muted); margin-bottom: 4px; }
  .shipment-table .preparation-status-cell { grid-column: 4; grid-row: 1; }
  .shipment-table .actions-cell { grid-column: 4; grid-row: 2; padding-right: 0; }
  .row-actions { align-items: flex-start; }
  .secondary-actions { justify-content: flex-start; }
  .assigned-person { display: block; }
  .assigned-person > span { margin-right: 5px; }
}
@media (max-width: 640px) {
  .shipment-page { padding: 20px 16px 24px; }
  .page-heading { margin-bottom: 20px; gap: 8px; }
  .page-heading h1 { font-size: 22px; }
  .primary-button { min-height: 44px; padding: 0 10px; font-size: 12px; }
  .export-button { height: 44px; padding: 0 10px; font-size: 12px; }
  .today-summary { grid-template-columns: repeat(4, minmax(0, 1fr)); row-gap: 13px; margin-bottom: 20px; }
  .summary-scope { grid-column: 1 / -1; flex-direction: row; justify-content: space-between; }
  .summary-item { padding-left: 12px; gap: 3px; }
  .summary-item:nth-child(2) { padding-left: 0; border-left: 0; }
  .summary-item > span { font-size: 12px; white-space: nowrap; }
  .summary-item strong { font-size: 20px; line-height: 26px; }
  .summary-item small { display: none; }
  .summary-exceptions > span { font-size: 0; }
  .summary-exceptions > span::after { content: '缺货 / 部分'; font-size: 12px; }
  .work-sheet { border-radius: 8px; }
  .status-tabs { padding: 0 14px; gap: 24px; }
  .status-tabs button { min-height: 44px; font-size: 13px; }
  .filter-form { padding: 14px 14px 0; }
  .filter-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 6px 9px; }
  .search-field { grid-column: 1 / 3; width: auto; height: 44px; padding: 0 9px; gap: 6px; }
  .search-field input { font-size: 12px; }
  .search-button { grid-column: 3; height: 44px; }
  .carrier-filter { grid-column: 1 / -1; grid-row: 2; width: 100%; }
  .quick-filters { grid-column: 1; gap: 8px; margin-right: 0; }
  .quick-filters .text-button { font-size: 12px; min-height: 44px; }
  .filter-separator { display: none; }
  .advanced-toggle { grid-column: 2; height: 44px; padding: 0 8px; gap: 4px; }
  .advanced-toggle svg:first-child { display: none; }
  .reset-button { grid-column: 3; min-height: 44px; }
  .reset-button svg { display: none; }
  .advanced-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 10px; }
  .filter-input { min-height: 44px; font-size: 14px; }
  .list-caption { padding: 16px 14px 12px; gap: 5px; }
  .list-caption h2 { font-size: 13px; }
  .list-caption p { font-size: 12px; }
  .shipment-table-frame { max-height: none; }
  .shipment-table { display: block; min-width: 0; }
  .shipment-table colgroup, .shipment-table thead { display: none; }
  .shipment-table tbody { display: block; }
  .shipment-table .shipment-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); position: relative; padding: 14px; gap: 14px 16px; border-top: 1px solid var(--line); }
  .shipment-table .shipment-row > td { display: block; padding: 0; border: 0; min-width: 0; }
  .identity-cell, .preparation-cell { grid-column: 1 / -1; }
  .shipment-number { font-size: 12px; padding-right: 63px; }
  .record-meta { padding-right: 55px; }
  .recipient-name { margin-top: 9px; font-size: 14px; }
  .recipient-address { line-height: 19px; }
  .preparation-status { position: absolute; right: 14px; top: 14px; }
  .preparation-content { font-size: 14px; line-height: 22px; }
  .mobile-cell-label { display: block; margin-bottom: 5px; font-size: 12px; color: var(--muted); line-height: 18px; }
  .record-remark { margin-top: 4px; }
  .logistics-cell { grid-column: 1; }
  .weight-cell { grid-column: 2; }
  .logistics-status { margin-top: 5px; }
  .actual-weight { font-size: 14px; }
  .preparation-status-cell { grid-column: 1 / -1; }
  .assigned-person { display: inline; margin-top: 0; font-size: 12px; }
  .assigned-person > span { margin-right: 5px; }
  .assigned-person + .assigned-person { display: block; margin-top: 3px; }
  .shipment-table .shipment-row .actions-cell { grid-column: 1 / -1; padding-top: 9px; border-top: 1px solid #edf0f3; }
  .row-actions { flex-direction: row-reverse; justify-content: space-between; align-items: center; gap: 12px; }
  .prepare-button { min-height: 44px; padding: 0 12px; font-size: 13px; }
  .secondary-actions { flex: 1; justify-content: flex-start; gap: 20px; }
  .secondary-actions .text-button { min-height: 44px; font-size: 13px; }
  .list-footer { padding: 12px 14px; }
  .record-range { display: none; }
  .page-button { width: 44px; height: 44px; }
  .pagination { gap: 10px; }
  .list-state { min-height: 180px; }
  .shipment-table .skeleton-row { display: grid; grid-template-columns: 1fr 1fr; padding: 14px; }
  .shipment-table .skeleton-row td { padding: 10px; }
}
</style>
