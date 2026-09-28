<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Info, Save, Send } from 'lucide-vue-next';
import { currentUser } from '../../services/authSession';
import PreparerMultiSelect from './PreparerMultiSelect.vue';
import RecipientRecognitionCard from './RecipientRecognitionCard.vue';
import ShipmentSuccessDialog from './ShipmentSuccessDialog.vue';
import { shippingService } from './shippingService';
import { emptyShipmentForm, SHIPMENT_STATUSES, type LogisticsAvailability, type LogisticsOrder,
  type RecipientFields, type Shipment, type ShipmentFormInput, type ShipmentStatus, type ShippingFormOptions } from './types';

const route = useRoute();
const router = useRouter();
const form = ref<ShipmentFormInput>(emptyShipmentForm());
const options = ref<ShippingFormOptions>({ shopNames: [], preparers: [] });
const availability = ref<LogisticsAvailability | null>(null);
const shipment = ref<Shipment | null>(null);
const existingOrder = ref<LogisticsOrder | null>(null);
const result = ref<LogisticsOrder | null>(null);
const status = ref<ShipmentStatus>('unfinished');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
let generation = 0;

const isEdit = computed(() => route.name === 'shipping-edit');
const locked = computed(() => !!existingOrder.value && existingOrder.value.state !== 'rejected');
const canOrder = computed(() => currentUser.value?.permissions.includes('shipping:order') ?? false);
const recipient = computed<RecipientFields>({
  get: () => ({ recipientName: form.value.recipientName, recipientPhone: form.value.recipientPhone,
    recipientProvince: form.value.recipientProvince, recipientCity: form.value.recipientCity,
    recipientCounty: form.value.recipientCounty, recipientDetailAddress: form.value.recipientDetailAddress }),
  set: value => Object.assign(form.value, value)
});

function formFromShipment(value: Shipment): ShipmentFormInput {
  const { shipmentDate: _date, status: _status, orderer: _orderer, logisticsCompany: _company,
    trackingNo: _tracking, ...editable } = value.content;
  return structuredClone(editable);
}
function numberOrNull(value: unknown) {
  if (value === '' || value == null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
function payload(): ShipmentFormInput {
  const value = JSON.parse(JSON.stringify(form.value)) as ShipmentFormInput;
  value.estimatedFreight = numberOrNull(value.estimatedFreight);
  value.orderDraft.weight = numberOrNull(value.orderDraft.weight);
  value.orderDraft.volume = numberOrNull(value.orderDraft.volume);
  value.orderDraft.pieceAmount = numberOrNull(value.orderDraft.pieceAmount);
  return value;
}

async function initialize() {
  const request = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const [loadedOptions, loadedAvailability] = await Promise.all([
      shippingService.formOptions(),
      shippingService.logisticsAvailability().catch(() => null)
    ]);
    if (request !== generation) return;
    options.value = loadedOptions;
    availability.value = loadedAvailability;
    if (isEdit.value) {
      const loaded = await shippingService.get(Number(route.params.id));
      if (request !== generation) return;
      shipment.value = loaded;
      form.value = formFromShipment(loaded);
      status.value = loaded.content.status;
      existingOrder.value = await shippingService.getLogisticsOrder(loaded.id).catch(() => null);
    } else if (loadedOptions.shopNames.length === 1) {
      form.value.shopName = loadedOptions.shopNames[0];
    }
  } catch (cause) { if (request === generation) error.value = cause instanceof Error ? cause.message : '发货表单加载失败'; }
  finally { if (request === generation) loading.value = false; }
}

function validateShipment(value: ShipmentFormInput) {
  if (!value.shopName.trim()) return '请选择店铺名称';
  if (![value.recipientName, value.recipientPhone, value.recipientProvince, value.recipientCity,
    value.recipientCounty, value.recipientDetailAddress, value.preparationContent].every(item => item.trim())) {
    return '请填写完整的收件人、地址和备货清单';
  }
  if (value.preparers.length > 20) return '备货人最多选择20人';
  if (value.estimatedFreight != null && value.estimatedFreight < 0) return '运费预测不能小于零';
  return '';
}
function validateOrder(value: ShipmentFormInput) {
  const draft = value.orderDraft;
  if (!draft.cargoName.trim() || !draft.packType.trim() || draft.weight == null || draft.weight < 1
    || draft.volume == null || draft.volume < 0.01 || draft.pieceAmount == null || draft.pieceAmount < 1
    || draft.productTypeId == null || draft.goodsType == null || draft.payType == null) {
    return '一键下单前请填写货物名称、包装、重量、体积、件数和物流选项';
  }
  if (!availability.value?.available) return availability.value?.message || '安能物流下单服务暂不可用';
  return '';
}

async function persist(andOrder: boolean) {
  if (busy.value) return;
  error.value = '';
  const value = payload();
  const invalid = validateShipment(value) || (andOrder ? validateOrder(value) : '');
  if (invalid) { error.value = invalid; return; }
  busy.value = true;
  try {
    const saved = isEdit.value && shipment.value
      ? await shippingService.update(shipment.value.id, value, status.value, shipment.value.version)
      : await shippingService.create(value);
    shipment.value = saved;
    form.value = formFromShipment(saved);
    status.value = saved.content.status;
    if (!andOrder) { await router.push({ name: 'shipping-detail', params: { id: String(saved.id) } }); return; }
    try {
      const placed = await shippingService.placeLogisticsOrder(saved.id, { version: saved.version });
      existingOrder.value = placed;
      if (placed.state === 'succeeded') result.value = placed;
      else {
        error.value = `发货单已保存，当前下单状态：${placed.message || placed.state}。请在详情页刷新核对。`;
        await router.push({ name: 'shipping-detail', params: { id: String(saved.id) } });
      }
    } catch (cause) {
      error.value = `发货单已保存，但安能下单失败：${cause instanceof Error ? cause.message : '请稍后重试'}`;
      try {
        const [latest, latestOrder] = await Promise.all([
          shippingService.get(saved.id),
          shippingService.getLogisticsOrder(saved.id).catch(() => null)
        ]);
        shipment.value = latest;
        form.value = formFromShipment(latest);
        status.value = latest.content.status;
        existingOrder.value = latestOrder;
      } catch {
        // Keep the saved values on screen when the backend itself is temporarily unreachable.
      }
      await router.replace({ name: 'shipping-edit', params: { id: String(saved.id) } });
    }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存失败，请重试'; }
  finally { busy.value = false; }
}

function cancel() { return router.push(isEdit.value && shipment.value ? { name: 'shipping-detail', params: { id: String(shipment.value.id) } } : { name: 'shipping-list' }); }
function backToList() { result.value = null; return router.push({ name: 'shipping-list' }); }
async function continueOrder() {
  result.value = null; shipment.value = null; existingOrder.value = null; status.value = 'unfinished'; form.value = emptyShipmentForm();
  if (options.value.shopNames.length === 1) form.value.shopName = options.value.shopNames[0];
  await router.replace({ name: 'shipping-new', query: { reset: String(Date.now()) } });
}
onMounted(initialize);
onBeforeUnmount(() => { generation++; });
</script>

<template>
  <section data-testid="shipment-form-page" class="mx-auto w-full max-w-[1500px] space-y-5 pb-8">
    <header class="flex flex-wrap items-center justify-between gap-4"><div class="flex items-center gap-3"><button type="button" class="rounded-lg border border-slate-200 bg-white p-2 text-slate-500" @click="cancel"><ArrowLeft :size="18" /></button><div><h1 class="text-2xl font-bold text-[#25314d]">{{ isEdit ? '编辑发货单' : '新建发货单' }}</h1><p class="mt-1 text-sm text-slate-500">{{ isEdit ? shipment?.shipmentNo : '填写发货资料，可保存后直接向安能下单。' }}</p></div></div></header>
    <p v-if="error" role="alert" class="rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">{{ error }}</p>
    <p v-if="locked" class="flex items-start gap-2 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700"><Info class="mt-0.5 shrink-0" :size="16" />该发货单已经提交物流，平台、店铺、备货人、收件资料、运费预测与安能下单资料已锁定；仍可维护备货清单、备注和状态。</p>
    <div v-if="loading" role="status" class="rounded-xl border border-slate-200 bg-white py-24 text-center text-sm text-slate-500">正在准备发货表单…</div>
    <form v-else class="space-y-5" @submit.prevent>
      <section class="form-card"><div class="section-heading"><span>01</span><div><h2>发货单信息</h2><p>选择来源平台、店铺和本次参与备货的人员。</p></div></div><div class="mt-5 grid gap-4 lg:grid-cols-3">
        <label class="field-label">平台<input v-model="form.platform" data-testid="shipment-platform" :disabled="busy || locked" maxlength="100" placeholder="如淘宝、抖音、线下" class="field-input" /></label>
        <label class="field-label">店铺名称 <span class="text-rose-500">*</span><select v-model="form.shopName" data-testid="shipment-shop" :disabled="busy || locked" class="field-input"><option value="">请选择店铺</option><option v-for="shop in options.shopNames" :key="shop" :value="shop">{{ shop }}</option></select></label>
        <label class="field-label">备货人（可多选）<PreparerMultiSelect v-model="form.preparers" class="mt-1.5" :options="options.preparers" :disabled="busy || locked" /></label>
      </div></section>

      <section class="form-card"><div class="section-heading"><span>02</span><div><h2>收件人信息</h2><p>粘贴原始信息后在当前卡片内识别，并核对拆分结果。</p></div></div><div class="mt-5"><RecipientRecognitionCard v-model="recipient" :disabled="busy || locked" /></div></section>

      <section class="form-card"><div class="section-heading"><span>03</span><div><h2>备货清单</h2><p>按行记录商品、规格和数量，可容纳多 SKU 的自由文本。</p></div></div><div class="mt-5 space-y-4">
        <label class="field-label block">备货内容 <span class="text-rose-500">*</span><textarea v-model="form.preparationContent" data-testid="shipment-preparation-content" :disabled="busy" maxlength="10000" rows="12" placeholder="例如：&#10;蓝色水族箱 × 2&#10;过滤棉 × 6&#10;外箱 × 2" class="field-input min-h-72 resize-y" /></label>
        <label class="field-label block">备注<textarea v-model="form.remark" data-testid="shipment-remark" :disabled="busy" maxlength="5000" rows="3" placeholder="缺货、补发、包装注意事项等" class="field-input resize-y" /></label>
      </div></section>

      <section class="form-card"><div class="flex flex-wrap items-start justify-between gap-4"><div class="section-heading"><span>04</span><div><h2>安能一键下单</h2><p>{{ availability?.message || '正在检查安能物流服务配置' }}</p></div></div><span class="rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700">物流公司：安能物流</span></div>
        <div class="mt-5 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <label class="field-label lg:col-span-2">货物名称 <span class="text-rose-500">*</span><input v-model="form.orderDraft.cargoName" data-testid="ane-cargo-name" :disabled="busy || locked" maxlength="32" placeholder="如水族用品" class="field-input" /></label>
          <label class="field-label">包装方式 <span class="text-rose-500">*</span><input v-model="form.orderDraft.packType" data-testid="ane-pack-type" :disabled="busy || locked" maxlength="50" class="field-input" /></label>
          <label class="field-label">件数 <span class="text-rose-500">*</span><input v-model="form.orderDraft.pieceAmount" data-testid="ane-piece-amount" :disabled="busy || locked" type="number" min="1" max="9999" step="1" class="field-input" /></label>
          <label class="field-label">重量（kg） <span class="text-rose-500">*</span><input v-model="form.orderDraft.weight" data-testid="ane-weight" :disabled="busy || locked" type="number" min="1" step="0.001" class="field-input" /></label>
          <label class="field-label">体积（m³） <span class="text-rose-500">*</span><input v-model="form.orderDraft.volume" data-testid="ane-volume" :disabled="busy || locked" type="number" min="0.01" step="0.01" class="field-input" /></label>
          <label class="field-label">物流产品 <span class="text-rose-500">*</span><select v-model="form.orderDraft.productTypeId" :disabled="busy || locked" class="field-input"><option :value="524">MiNi 电商小件</option><option :value="95">Mini 电商大件</option><option :value="24">精准零担</option><option :value="23">定时达</option><option :value="270">普惠达</option><option :value="546">安心达</option></select></label>
          <label class="field-label">送货方式 <span class="text-rose-500">*</span><select v-model="form.orderDraft.goodsType" :disabled="busy || locked" class="field-input"><option :value="180">送货（不含上楼）</option><option :value="179">送货上楼</option><option :value="285">自提</option></select></label>
          <label class="field-label">付款方式 <span class="text-rose-500">*</span><select v-model="form.orderDraft.payType" :disabled="busy || locked" class="field-input"><option :value="102">现金</option><option :value="104">月结</option><option :value="103">到付</option></select></label>
          <label class="field-label">运费预测（元，可选）<input v-model="form.estimatedFreight" :disabled="busy || locked" type="number" min="0" step="0.01" class="field-input" /></label>
          <label class="field-label md:col-span-2">物流备注<textarea v-model="form.orderDraft.logisticsRemark" :disabled="busy || locked" maxlength="200" rows="2" class="field-input resize-y" /></label>
        </div>
      </section>

      <section v-if="isEdit" class="form-card"><div class="section-heading"><span>05</span><div><h2>发货状态</h2><p>下单成功不代表已完成发货，请按实际进度维护。</p></div></div><label class="field-label mt-5 block max-w-sm">当前状态<select v-model="status" data-testid="shipment-status" :disabled="busy" class="field-input"><option v-for="item in SHIPMENT_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></section>

      <div class="flex flex-wrap justify-end gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><button type="button" :disabled="busy" class="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 disabled:opacity-40" @click="cancel">取消</button><button data-testid="shipment-save-only" type="button" :disabled="busy" class="inline-flex items-center gap-2 rounded-lg border border-indigo-200 px-5 py-2.5 text-sm font-semibold text-[#536dff] disabled:opacity-40" @click="persist(false)"><Save :size="16" />{{ busy ? '处理中…' : isEdit ? '仅保存修改' : '仅保存发货单' }}</button><button v-if="canOrder && !locked" data-testid="shipment-save-and-order" type="button" :disabled="busy" class="inline-flex items-center gap-2 rounded-lg bg-[#536dff] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-40" @click="persist(true)"><Send :size="16" />{{ busy ? '处理中…' : '保存并一键下单' }}</button></div>
    </form>
    <ShipmentSuccessDialog :open="!!result" :tracking-no="result?.trackingNo || ''" :order-no="result?.orderNo || ''" :test-environment="result?.testEnvironment || false" @back-list="backToList" @continue="continueOrder" />
  </section>
</template>

<style scoped>
.form-card { @apply rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6; }
.section-heading { @apply flex items-start gap-3; }
.section-heading > span { @apply flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-xs font-bold text-[#536dff]; }
.section-heading h2 { @apply text-base font-bold text-[#25314d]; }
.section-heading p { @apply mt-1 text-xs leading-5 text-slate-400; }
.field-label { @apply text-sm font-medium text-slate-600; }
.field-input { @apply mt-1.5 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-800 outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500; }
</style>
