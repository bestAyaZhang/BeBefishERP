<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Box, MapPin, PackageCheck, RefreshCw, Truck } from 'lucide-vue-next';
import { shippingService } from './shippingService';
import { currentUser } from '../../services/authSession';
import { freightText, logisticsStateLabel, recipientFullAddress, shipmentStatus,
  type LogisticsOrder, type Shipment } from './types';

const route = useRoute();
const router = useRouter();
const shipment = ref<Shipment | null>(null);
const order = ref<LogisticsOrder | null>(null);
const loading = ref(false);
const error = ref('');
const canEdit = computed(() => currentUser.value?.permissions.includes('shipping:edit'));
const editLabel = computed(() => order.value && order.value.state !== 'rejected' ? '编辑发货单' : '编辑并下单');
let generation = 0;

async function load() {
  const request = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const record = await shippingService.get(Number(route.params.id));
    if (request !== generation) return;
    shipment.value = record;
    try { order.value = await shippingService.getLogisticsOrder(record.id); }
    catch { order.value = null; }
  } catch (cause) { if (request === generation) error.value = cause instanceof Error ? cause.message : '发货单加载失败'; }
  finally { if (request === generation) loading.value = false; }
}
function back() { return router.push({ name: 'shipping-list' }); }
function edit() { return router.push({ name: 'shipping-edit', params: { id: String(shipment.value?.id) } }); }
onMounted(load);
onBeforeUnmount(() => { generation++; });
</script>

<template>
  <section data-testid="shipment-detail-page" class="mx-auto w-full max-w-[1500px] space-y-5 pb-8">
    <header class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-3"><button data-testid="shipment-back-list" type="button" class="rounded-lg border border-slate-200 bg-white p-2 text-slate-500" @click="back"><ArrowLeft :size="18" /></button><div><h1 class="text-2xl font-bold text-[#25314d]">发货单详情</h1><p class="mt-1 text-sm text-slate-500">{{ shipment?.shipmentNo || '查看发货资料与物流进度' }}</p></div></div>
      <button v-if="shipment && canEdit" data-testid="shipment-detail-edit" type="button" class="rounded-lg bg-[#536dff] px-5 py-2.5 text-sm font-semibold text-white" @click="edit">{{ editLabel }}</button>
    </header>
    <div v-if="loading" role="status" class="rounded-xl border border-slate-200 bg-white py-24 text-center text-sm text-slate-500">正在加载发货单…</div>
    <div v-else-if="error" data-testid="shipment-detail-error" role="alert" class="rounded-xl border border-rose-100 bg-white py-20 text-center"><p class="text-sm text-rose-600">{{ error }}</p><button data-testid="shipment-detail-retry" type="button" class="mt-4 inline-flex items-center gap-2 rounded-lg border border-slate-200 px-5 py-2 text-sm text-[#536dff]" @click="load"><RefreshCw :size="15" />重新加载</button></div>
    <div v-else-if="shipment" class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
      <main class="space-y-5">
        <article class="detail-card">
          <div class="card-title"><MapPin :size="18" />收件人信息</div>
          <div class="mt-5 grid gap-5 sm:grid-cols-2"><div><p class="meta-label">姓名</p><p class="meta-value">{{ shipment.content.recipientName }}</p></div><div><p class="meta-label">电话</p><p class="meta-value">{{ shipment.content.recipientPhone }}</p></div><div class="sm:col-span-2"><p class="meta-label">完整地址</p><p class="meta-value break-words leading-7">{{ recipientFullAddress(shipment.content) }}</p></div></div>
        </article>
        <article class="detail-card"><div class="card-title"><Box :size="18" />备货清单</div><p class="mt-5 min-h-36 whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-4 text-sm leading-7 text-slate-700">{{ shipment.content.preparationContent }}</p><div class="mt-5 border-t border-slate-100 pt-4"><p class="meta-label">备注</p><p class="meta-value whitespace-pre-wrap break-words">{{ shipment.content.remark || '暂无备注' }}</p></div></article>
        <article class="detail-card"><div class="card-title"><PackageCheck :size="18" />发货记录</div><dl class="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3"><div><dt class="meta-label">平台</dt><dd class="meta-value">{{ shipment.content.platform || '未填写' }}</dd></div><div><dt class="meta-label">店铺</dt><dd class="meta-value">{{ shipment.content.shopName }}</dd></div><div><dt class="meta-label">发货日期</dt><dd class="meta-value">{{ shipment.content.shipmentDate }}</dd></div><div><dt class="meta-label">备货人</dt><dd class="meta-value">{{ shipment.content.preparers.join('、') || '未指定' }}</dd></div><div><dt class="meta-label">下单人</dt><dd class="meta-value">{{ shipment.content.orderer }}</dd></div><div><dt class="meta-label">运费预测</dt><dd class="meta-value">{{ freightText(shipment.content.estimatedFreight) }}</dd></div></dl></article>
      </main>
      <aside class="space-y-5 lg:sticky lg:top-5">
        <article class="detail-card"><div class="card-title"><Truck :size="18" />物流与备货状态</div><dl class="mt-5 space-y-4"><div><dt class="meta-label">安能下单状态</dt><dd data-testid="shipment-detail-logistics-status" class="meta-value">{{ order ? logisticsStateLabel(order.state) : '尚未下单' }}<span v-if="order?.testEnvironment" class="ml-1 text-xs text-amber-600">（测试）</span></dd></div><div><dt class="meta-label">备货状态</dt><dd data-testid="shipment-detail-preparation-status" class="mt-1.5"><span class="inline-flex rounded-full px-2.5 py-1 text-xs font-medium" :class="shipmentStatus(shipment.content.status).tone">{{ shipmentStatus(shipment.content.status).label }}</span></dd></div><div><dt class="meta-label">物流公司</dt><dd class="meta-value">{{ shipment.content.logisticsCompany || '待下单' }}</dd></div><div><dt class="meta-label">物流单号</dt><dd data-testid="shipment-real-tracking" class="meta-value break-all">{{ (order?.testEnvironment ? '' : order?.trackingNo) || shipment.content.trackingNo || '暂无' }}</dd></div><div v-if="order && !order.testEnvironment"><dt class="meta-label">安能订单号</dt><dd class="meta-value break-all">{{ order.orderNo }}</dd></div></dl></article>
        <article v-if="order?.testEnvironment" data-testid="shipment-test-order" class="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900 shadow-sm"><h2 class="font-bold">安能测试订单</h2><p class="mt-2 leading-6">此单来自测试环境，不可用于实际走货。正式发货请新建发货单。</p><dl class="mt-4 space-y-3"><div><dt class="text-xs text-amber-700">测试下单状态</dt><dd class="mt-1">{{ logisticsStateLabel(order.state) }}</dd></div><div><dt class="text-xs text-amber-700">测试运单号</dt><dd class="mt-1 break-all font-medium">{{ order.trackingNo || '暂无' }}</dd></div><div><dt class="text-xs text-amber-700">安能测试订单号</dt><dd class="mt-1 break-all">{{ order.orderNo }}</dd></div></dl></article>
        <p v-if="order && order.state !== 'succeeded' && order.message" data-testid="shipment-order-message" role="alert" class="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">{{ order.message }}</p>
        <article class="detail-card"><div class="card-title">货物信息</div><dl class="mt-5 grid grid-cols-2 gap-4"><div><dt class="meta-label">货物名称</dt><dd class="meta-value">{{ shipment.content.orderDraft.cargoName || '未填写' }}</dd></div><div><dt class="meta-label">包装</dt><dd class="meta-value">{{ shipment.content.orderDraft.packType || '未填写' }}</dd></div><div><dt class="meta-label">重量</dt><dd class="meta-value">{{ shipment.content.orderDraft.weight == null ? '未填写' : `${shipment.content.orderDraft.weight} kg` }}</dd></div><div><dt class="meta-label">件数</dt><dd class="meta-value">{{ shipment.content.orderDraft.pieceAmount ?? '未填写' }}</dd></div></dl></article>
        <article class="rounded-xl border border-slate-200 bg-slate-50 p-5 text-xs leading-6 text-slate-500"><p>创建：{{ shipment.createdAt.replace('T', ' ') }}</p><p>更新：{{ shipment.updatedAt.replace('T', ' ') }}</p><p>版本：{{ shipment.version }}</p></article>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.detail-card { @apply rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6; }
.card-title { @apply flex items-center gap-2 text-sm font-bold text-[#25314d]; }
.meta-label { @apply text-xs font-medium text-slate-400; }
.meta-value { @apply mt-1.5 text-sm text-slate-700; }
</style>
