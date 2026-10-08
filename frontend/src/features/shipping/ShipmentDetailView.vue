<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Box, MapPin, PackageCheck, RefreshCw, Truck } from 'lucide-vue-next';
import { shippingService } from './shippingService';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import AccessibleDialog from '../../components/AccessibleDialog.vue';
import ShipmentPreparationDialog from './ShipmentPreparationDialog.vue';
import { canUpdatePreparation } from './preparationAccess';
import { freightText, logisticsStateLabel, recipientFullAddress, shipmentSenderFullAddress, shipmentStatus,
  type LogisticsOrder, type Shipment } from './types';

const route = useRoute();
const router = useRouter();
const shipment = ref<Shipment | null>(null);
const order = ref<LogisticsOrder | null>(null);
const loading = ref(false);
const error = ref('');
const orderVerified = ref(false);
const cancelOpen = ref(false);
const cancelling = ref(false);
const preparationOpen = ref(false);
function preparationSaved(updated: Shipment) {
  shipment.value = updated;
  preparationOpen.value = false;
  message.success('备货状态与实际重量已保存');
}
const canEdit = computed(() => currentUser.value?.permissions.includes('shipping:edit'));
const canCancel = computed(() => orderVerified.value && currentUser.value?.permissions.includes('shipping:cancel')
  && !!order.value && ['succeeded', 'cancel_rejected'].includes(order.value.state));
const editLabel = computed(() => order.value && order.value.state !== 'rejected' ? '编辑发货单' : '编辑并下单');
let generation = 0;

async function load() {
  const request = ++generation;
  loading.value = true;
  orderVerified.value = false;
  error.value = '';
  try {
    const record = await shippingService.get(Number(route.params.id));
    if (request !== generation) return;
    shipment.value = record;
    try {
      const loadedOrder = await shippingService.getLogisticsOrder(record.id);
      if (request !== generation) return;
      order.value = loadedOrder;
      orderVerified.value = true;
    } catch { /* Preserve a known result, but never allow another cancellation until refresh succeeds. */ }
  } catch (cause) { if (request === generation) error.value = cause instanceof Error ? cause.message : '发货单加载失败'; }
  finally { if (request === generation) loading.value = false; }
}
function dismissCancellation() { if (!cancelling.value) cancelOpen.value = false; }
async function cancelOrder() {
  if (cancelling.value || !canCancel.value || !shipment.value) return;
  cancelling.value = true;
  try {
    const result = await shippingService.cancelLogisticsOrder(shipment.value.id, { version: shipment.value.version });
    order.value = result;
    cancelOpen.value = false;
    const feedback = result.message || logisticsStateLabel(result.state);
    if (result.state === 'cancelled') message.success(feedback);
    else if (result.state === 'cancel_rejected') message.error(feedback, 6000);
    else message.warning(feedback, 6000);
    await load();
  } catch (cause) {
    cancelOpen.value = false;
    message.warning(`${cause instanceof Error ? cause.message : '取消请求异常'}。请刷新核实订单状态，勿重复取消或重新下单。`, 6000);
    await load();
    orderVerified.value = false;
  } finally { cancelling.value = false; }
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
      <div class="flex gap-3"><button data-testid="shipment-refresh-order" type="button" :disabled="loading || cancelling" class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-600 disabled:opacity-40" @click="load"><RefreshCw :size="15" />刷新状态</button><button v-if="shipment && canEdit" data-testid="shipment-detail-edit" type="button" :disabled="cancelling" class="rounded-lg bg-[#536dff] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-40" @click="edit">{{ editLabel }}</button></div>
    </header>
    <p v-if="shipment && !loading && !orderVerified" class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">物流状态尚未核实，请点击“刷新状态”后核对，勿重复取消或重新下单。</p>
    <div v-if="loading" role="status" class="rounded-xl border border-slate-200 bg-white py-24 text-center text-sm text-slate-500">正在加载发货单…</div>
    <div v-else-if="error" data-testid="shipment-detail-error" role="alert" class="rounded-xl border border-rose-100 bg-white py-20 text-center"><p class="text-sm text-rose-600">{{ error }}</p><button data-testid="shipment-detail-retry" type="button" class="mt-4 inline-flex items-center gap-2 rounded-lg border border-slate-200 px-5 py-2 text-sm text-[#536dff]" @click="load"><RefreshCw :size="15" />重新加载</button></div>
    <div v-else-if="shipment" class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
      <main class="space-y-5">
        <article data-testid="shipment-detail-sender" class="detail-card">
          <div class="card-title"><Truck :size="18" />发货人信息</div>
          <div class="mt-5 grid gap-5 sm:grid-cols-2"><div><p class="meta-label">姓名</p><p class="meta-value">{{ shipment.content.senderName || '未记录' }}</p></div><div><p class="meta-label">电话</p><p class="meta-value">{{ shipment.content.senderPhone || '未记录' }}</p></div><div class="sm:col-span-2"><p class="meta-label">完整地址</p><p class="meta-value break-words leading-7">{{ shipment.content.senderName ? shipmentSenderFullAddress(shipment.content) : '历史发货单未记录发货人地址' }}</p></div></div>
        </article>
        <article class="detail-card">
          <div class="card-title"><MapPin :size="18" />收件人信息</div>
          <div class="mt-5 grid gap-5 sm:grid-cols-2"><div><p class="meta-label">姓名</p><p class="meta-value">{{ shipment.content.recipientName }}</p></div><div><p class="meta-label">电话</p><p class="meta-value">{{ shipment.content.recipientPhone }}</p></div><div class="sm:col-span-2"><p class="meta-label">完整地址</p><p class="meta-value break-words leading-7">{{ recipientFullAddress(shipment.content) }}</p></div></div>
        </article>
        <article class="detail-card"><div class="card-title"><Box :size="18" />备货清单</div><p class="mt-5 min-h-36 whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-4 text-sm leading-7 text-slate-700">{{ shipment.content.preparationContent }}</p><div class="mt-5 border-t border-slate-100 pt-4"><p class="meta-label">备注</p><p class="meta-value whitespace-pre-wrap break-words">{{ shipment.content.remark || '暂无备注' }}</p></div></article>
        <article class="detail-card"><div class="card-title"><PackageCheck :size="18" />发货记录</div><dl class="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3"><div><dt class="meta-label">平台</dt><dd class="meta-value">{{ shipment.content.platform || '未填写' }}</dd></div><div><dt class="meta-label">店铺</dt><dd class="meta-value">{{ shipment.content.shopName }}</dd></div><div><dt class="meta-label">发货日期</dt><dd class="meta-value">{{ shipment.content.shipmentDate }}</dd></div><div><dt class="meta-label">备货人</dt><dd class="meta-value">{{ shipment.content.preparers.join('、') || '未指定' }}</dd></div><div><dt class="meta-label">下单人</dt><dd class="meta-value">{{ shipment.content.orderer }}</dd></div><div><dt class="meta-label">运费预测</dt><dd class="meta-value">{{ freightText(shipment.content.estimatedFreight) }}</dd></div></dl></article>
      </main>
      <aside class="space-y-5 lg:sticky lg:top-5">
        <article class="detail-card"><div class="card-title"><Truck :size="18" />物流与备货状态</div><dl class="mt-5 space-y-4"><div><dt class="meta-label">安能下单状态</dt><dd data-testid="shipment-detail-logistics-status" class="meta-value">{{ order ? logisticsStateLabel(order.state) : '尚未下单' }}<span v-if="order?.testEnvironment" class="ml-1 text-xs text-amber-600">（测试）</span></dd></div><div><dt class="meta-label">备货状态</dt><dd data-testid="shipment-detail-preparation-status" class="mt-1.5"><span class="inline-flex rounded-full px-2.5 py-1 text-xs font-medium" :class="shipmentStatus(shipment.content.status).tone">{{ shipmentStatus(shipment.content.status).label }}</span></dd></div><div><dt class="meta-label">物流公司</dt><dd class="meta-value">{{ shipment.content.logisticsCompany || '待下单' }}</dd></div><div><dt class="meta-label">物流单号</dt><dd data-testid="shipment-real-tracking" class="meta-value break-all">{{ order?.state === 'cancelled' ? ((order.testEnvironment ? '' : order.trackingNo) || '原运单') + '（已作废）' : (order?.testEnvironment ? '' : order?.trackingNo) || shipment.content.trackingNo || '暂无' }}</dd></div><div v-if="order && !order.testEnvironment"><dt class="meta-label">安能订单号</dt><dd class="meta-value break-all">{{ order.orderNo }}</dd></div></dl></article>
        <article v-if="order?.testEnvironment" data-testid="shipment-test-order" class="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900 shadow-sm"><h2 class="font-bold">安能测试订单</h2><p class="mt-2 leading-6">此单来自测试环境，不可用于实际走货。正式发货请新建发货单。</p><dl class="mt-4 space-y-3"><div><dt class="text-xs text-amber-700">测试下单状态</dt><dd class="mt-1">{{ logisticsStateLabel(order.state) }}</dd></div><div><dt class="text-xs text-amber-700">{{ order.state === 'cancelled' ? '原测试运单号（已作废）' : '测试运单号' }}</dt><dd class="mt-1 break-all font-medium">{{ order.trackingNo || '暂无' }}</dd></div><div><dt class="text-xs text-amber-700">安能测试订单号</dt><dd class="mt-1 break-all">{{ order.orderNo }}</dd></div></dl></article>
        <div v-if="canCancel" class="rounded-xl border border-rose-100 bg-white p-5"><p class="text-xs leading-6 text-slate-500">订单仅在安能揽收前可取消。取消成功后，原运单不可用于走货。</p><button data-testid="shipment-cancel-order" type="button" :disabled="cancelling" class="mt-3 w-full rounded-lg border border-rose-200 px-4 py-2.5 text-sm font-semibold text-rose-600 disabled:opacity-40" @click="cancelOpen = true">{{ cancelling ? '正在取消…' : '取消安能订单' }}</button></div>
        <p v-if="order && order.state !== 'succeeded' && order.message" data-testid="shipment-order-message" role="alert" class="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">{{ order.message }}</p>
        <article class="detail-card"><div class="flex items-center justify-between gap-3"><div class="card-title"><PackageCheck :size="18" />备货反馈</div><button v-if="canUpdatePreparation(shipment, currentUser)" data-testid="shipment-detail-prepare" type="button" :disabled="loading || cancelling" class="rounded-lg border border-indigo-200 px-3 py-2 text-xs font-semibold text-[#536dff] disabled:opacity-40" @click="preparationOpen = true">更新备货</button></div><dl class="mt-5 space-y-4"><div><dt class="meta-label">实际重量</dt><dd data-testid="shipment-detail-actual-weight" class="meta-value font-semibold">{{ shipment.preparation?.actualWeight == null ? '未称重' : `${shipment.preparation.actualWeight} kg` }}</dd></div><div v-if="shipment.preparation?.updatedAt"><dt class="meta-label">最近备货更新</dt><dd class="meta-value">{{ shipment.preparation.updatedBy }}<p class="mt-1 text-xs text-slate-400">{{ shipment.preparation.updatedAt.replace('T', ' ') }}</p></dd></div></dl></article>
        <article class="detail-card"><div class="card-title">货物信息</div><dl class="mt-5 grid grid-cols-2 gap-4"><div><dt class="meta-label">货物名称</dt><dd class="meta-value">{{ shipment.content.orderDraft.cargoName || '未填写' }}</dd></div><div><dt class="meta-label">包装</dt><dd class="meta-value">{{ shipment.content.orderDraft.packType || '未填写' }}</dd></div><div><dt class="meta-label">安能下单重量</dt><dd class="meta-value">{{ shipment.content.orderDraft.weight == null ? '未填写' : `${shipment.content.orderDraft.weight} kg` }}</dd></div><div><dt class="meta-label">件数</dt><dd class="meta-value">{{ shipment.content.orderDraft.pieceAmount ?? '未填写' }}</dd></div></dl></article>
        <article class="rounded-xl border border-slate-200 bg-slate-50 p-5 text-xs leading-6 text-slate-500"><p>创建：{{ shipment.createdAt.replace('T', ' ') }}</p><p>更新：{{ shipment.updatedAt.replace('T', ' ') }}</p><p>版本：{{ shipment.version }}</p></article>
      </aside>
    </div>
    <AccessibleDialog :open="cancelOpen" title="取消安能订单" test-id="cancel-order-dialog" body-test-id="cancel-order-body" footer-test-id="cancel-order-footer" close-test-id="cancel-order-close" panel-class="w-[min(520px,calc(100vw-48px))]" @cancel="dismissCancellation">
      <p class="text-sm leading-7 text-slate-600">确认取消这张{{ order?.testEnvironment ? '测试' : '' }}安能订单？仅未揽收的订单可取消，最终以安能返回结果为准。</p>
      <dl class="mt-4 space-y-3 rounded-lg bg-slate-50 p-4 text-sm"><div><dt class="text-xs text-slate-400">原运单号</dt><dd class="mt-1 break-all font-semibold text-slate-700">{{ order?.trackingNo }}</dd></div><div><dt class="text-xs text-slate-400">安能订单号</dt><dd class="mt-1 break-all text-slate-700">{{ order?.orderNo }}</dd></div></dl>
      <p class="mt-4 text-xs leading-6 text-slate-500">取消成功后原运单作废，备货状态保持不变。如需重新下单，请新建发货单。</p>
      <template #footer><button data-testid="cancel-order-dismiss" type="button" :disabled="cancelling" class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600 disabled:opacity-40" @click="dismissCancellation">暂不取消</button><button data-testid="cancel-order-confirm" type="button" :disabled="cancelling" class="rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40" @click="cancelOrder">{{ cancelling ? '正在取消…' : '确认取消订单' }}</button></template>
    </AccessibleDialog>
    <ShipmentPreparationDialog :shipment-id="preparationOpen ? shipment?.id ?? null : null" @close="preparationOpen = false" @saved="preparationSaved" />
  </section>
</template>

<style scoped>
.detail-card { @apply rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6; }
.card-title { @apply flex items-center gap-2 text-sm font-bold text-[#25314d]; }
.meta-label { @apply text-xs font-medium text-slate-400; }
.meta-value { @apply mt-1.5 text-sm text-slate-700; }
</style>
