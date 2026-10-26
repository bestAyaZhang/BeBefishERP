<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Info, Save, Send } from 'lucide-vue-next';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import PreparerMultiSelect from './PreparerMultiSelect.vue';
import RecipientRecognitionCard from './RecipientRecognitionCard.vue';
import ShipmentSuccessDialog from './ShipmentSuccessDialog.vue';
import { shippingService } from './shippingService';
import { canEditPreparationContent } from './preparationAccess';
import ShipmentQuotePanel from '../logistics/components/ShipmentQuotePanel.vue';
import { logisticsService } from '../logistics/logisticsService';
import type { Quote, QuoteBatch, QuoteInput } from '../logistics/types';
import { emptyShipmentForm, SHIPMENT_STATUSES, type LogisticsAvailability, type LogisticsOrder,
  type RecipientFields, type Shipment, type ShipmentFormInput, type ShipmentStatus, type ShippingFormOptions } from './types';

const route = useRoute();
const router = useRouter();
const form = ref<ShipmentFormInput>(emptyShipmentForm());
const options = ref<ShippingFormOptions>({ shopNames: [], preparers: [], platforms: [], shops: [] });
const sourceChanged = ref(false);
const availableShops = computed(() => (options.value.shops ?? []).filter(s => s.platformId === form.value.platformId));
const originalSource = computed(() => isEdit.value && !sourceChanged.value);
const sourceUnavailable = computed(() => !isEdit.value && !loading.value && !options.value.shops?.length);
function platformChanged() {
  sourceChanged.value = true;
  form.value.platform = options.value.platforms.find(p => p.id === form.value.platformId)?.name ?? '';
  form.value.shopId = availableShops.value.length === 1 ? availableShops.value[0].id : null;
  form.value.shopName = availableShops.value.find(s => s.id === form.value.shopId)?.name ?? '';
}
function shopChanged() {
  sourceChanged.value = true;
  form.value.shopName = availableShops.value.find(s => s.id === form.value.shopId)?.name ?? '';
}
const availability = ref<LogisticsAvailability | null>(null);
const shipment = ref<Shipment | null>(null);
const existingOrder = ref<LogisticsOrder | null>(null);
const result = ref<LogisticsOrder | null>(null);
const status = ref<ShipmentStatus>('unfinished');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const requiresReload = ref(false);
const selectedQuote = ref<Quote | null>(null);
const quoteIntent = ref<Quote | null>(null);
const quotePending = ref(false);
const quoteTouched = ref(false);
const preferredCompanyId = ref<number | null>(null);
const legacyQuoteMode = ref<boolean | null>(null);
const hasHistoricalQuote = ref(false);
const repriceRequested = ref(false);
const senderPending = ref(false);
let senderGeneration = 0;
let configuredSenderSnapshot: string | null = null;
function senderSnapshot() {
  return JSON.stringify([form.value.senderName, form.value.senderPhone, form.value.senderProvince,
    form.value.senderCity, form.value.senderCounty, form.value.senderDetailAddress]);
}
const quoteInput = computed<QuoteInput>(() => ({
  senderProvince: form.value.senderProvince, senderCity: form.value.senderCity, senderCounty: form.value.senderCounty,
  recipientProvince: form.value.recipientProvince, recipientCity: form.value.recipientCity, recipientCounty: form.value.recipientCounty,
  weight: numberOrNull(form.value.orderDraft.weight), volume: numberOrNull(form.value.orderDraft.volume),
  pieceAmount: numberOrNull(form.value.orderDraft.pieceAmount), goodsType: form.value.orderDraft.goodsType, payType: form.value.orderDraft.payType
}));
watch(() => JSON.stringify(quoteInput.value), () => {
  if (loading.value || !hasHistoricalQuote.value || repriceRequested.value) return;
  repriceRequested.value = true;
  chooseQuote(null);
}, { flush: 'sync' });
const legacyOrderingAllowed = computed(() => legacyQuoteMode.value ?? availability.value?.managedTemplatesEnabled !== true);
const quoteAllowsOrder = computed(() => selectedQuote.value
  ? !quotePending.value && !senderPending.value && selectedQuote.value.apiReady && selectedQuote.value.available
    && (!selectedQuote.value.expiresAt || Date.parse(selectedQuote.value.expiresAt) > Date.now())
  : legacyOrderingAllowed.value);
function chooseQuote(value: Quote | null) {
  const previous = selectedQuote.value;
  selectedQuote.value = value;
  quoteTouched.value = true;
  if (value) {
    quoteIntent.value = value;
    preferredCompanyId.value = value.companyId;
    form.value.estimatedFreight = value.amount;
    const product = Number(value.productCode);
    if (value.provider === 'ANE' && Number.isInteger(product) && product > 0) form.value.orderDraft.productTypeId = product;
  } else if (previous) form.value.estimatedFreight = null;
  if (value && value.companyId !== previous?.companyId) void loadCarrierSender(value.companyId);
  else if (!value) { senderGeneration++; senderPending.value = false; }
}
async function loadCarrierSender(companyId: number) {
  const request = ++senderGeneration;
  const before = senderSnapshot();
  const mayReplace = !isEdit.value && (before === configuredSenderSnapshot ||
    ![form.value.senderName, form.value.senderPhone, form.value.senderProvince, form.value.senderCity,
      form.value.senderCounty, form.value.senderDetailAddress].some(value => value?.trim()));
  senderPending.value = true;
  try {
    const loaded = await shippingService.logisticsAvailability(companyId);
    if (request !== senderGeneration || selectedQuote.value?.companyId !== companyId) return;
    availability.value = loaded;
    if (mayReplace && before === senderSnapshot() && loaded.sender) {
      setConfiguredSender(loaded.sender);
    }
  } catch { /* Quote readiness remains server-authoritative; keep the operator's sender details. */ }
  finally { if (request === senderGeneration) senderPending.value = false; }
}
function quotesLoaded(batch: QuoteBatch) { legacyQuoteMode.value = batch.legacyOrderingAllowed; }
let generation = 0;

const isEdit = computed(() => route.name === 'shipping-edit');
const canEditGeneral = computed(() => currentUser.value?.permissions.includes('shipping:edit') ?? false);
const contentOnly = computed(() => isEdit.value && !canEditGeneral.value);
const canEditContent = computed(() => !!shipment.value && canEditPreparationContent(shipment.value, currentUser.value));
const canSave = computed(() => !isEdit.value || (!!shipment.value && (canEditGeneral.value || canEditContent.value)));
const locked = computed(() => !!existingOrder.value && existingOrder.value.state !== 'rejected');
const detailsLocked = computed(() => locked.value || contentOnly.value);
const preparationLocked = computed(() => isEdit.value && shipment.value?.content.status === 'completed');
const contentLocked = computed(() => preparationLocked.value || (contentOnly.value && !canEditContent.value));
const canOrder = computed(() => currentUser.value?.permissions.includes('shipping:order') ?? false);
const canLinkLegacyPreparers = computed(() => isEdit.value && canEditGeneral.value && !shipment.value?.preparerEmployeeIds?.length && currentUser.value?.roles.includes('SUPER_ADMIN'));
const canReselectPreparers = computed(() => isEdit.value && canEditGeneral.value && !locked.value
  && (!!shipment.value?.preparerEmployeeIds?.length || currentUser.value?.roles.includes('SUPER_ADMIN')));
function reselectPreparers() { form.value.preparers = []; form.value.preparerEmployeeIds = []; }
const recipient = computed<RecipientFields>({
  get: () => ({ recipientName: form.value.recipientName, recipientPhone: form.value.recipientPhone,
    recipientProvince: form.value.recipientProvince, recipientCity: form.value.recipientCity,
    recipientCounty: form.value.recipientCounty, recipientDetailAddress: form.value.recipientDetailAddress }),
  set: value => Object.assign(form.value, value)
});

function formFromShipment(value: Shipment): ShipmentFormInput {
  const { shipmentDate: _date, status: _status, orderer: _orderer, logisticsCompany: _company,
    trackingNo: _tracking, ...editable } = value.content;
  return { ...structuredClone(editable), preparerEmployeeIds: value.preparerEmployeeIds?.length ? [...value.preparerEmployeeIds] : undefined };
}
function numberOrNull(value: unknown) {
  if (value === '' || value == null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
function payload(): ShipmentFormInput {
  const value = JSON.parse(JSON.stringify(form.value)) as ShipmentFormInput;
  if (quoteTouched.value) value.logisticsChoice = { quoteId: selectedQuote.value?.quoteId ?? null,
    preferredCompanyId: selectedQuote.value?.companyId ?? preferredCompanyId.value, clear: !selectedQuote.value };
  value.estimatedFreight = numberOrNull(value.estimatedFreight);
  value.orderDraft.weight = numberOrNull(value.orderDraft.weight);
  value.orderDraft.volume = numberOrNull(value.orderDraft.volume);
  value.orderDraft.pieceAmount = numberOrNull(value.orderDraft.pieceAmount);
  return value;
}

function applyConfiguredSender() {
  const sender = availability.value?.sender;
  if (!sender || [form.value.senderName, form.value.senderPhone, form.value.senderProvince, form.value.senderCity,
    form.value.senderCounty, form.value.senderDetailAddress].some(value => value?.trim())) return;
  setConfiguredSender(sender);
}
function setConfiguredSender(sender: NonNullable<LogisticsAvailability['sender']>) {
  Object.assign(form.value, {
    senderName: sender.name,
    senderPhone: sender.phone,
    senderProvince: sender.province,
    senderCity: sender.city,
    senderCounty: sender.county,
    senderDetailAddress: sender.address
  });
  configuredSenderSnapshot = senderSnapshot();
}

async function initialize() {
  const request = ++generation;
  loading.value = true;
  error.value = '';
  shipment.value = null;
  sourceChanged.value = false;
  senderGeneration++; senderPending.value = false; configuredSenderSnapshot = null;
  selectedQuote.value = null; quoteTouched.value = false; preferredCompanyId.value = null; legacyQuoteMode.value = null;
  hasHistoricalQuote.value = false; repriceRequested.value = false;
  quoteIntent.value = null;
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
      const loadedOrder = await shippingService.getLogisticsOrder(loaded.id).catch(() => null);
      if (request !== generation) return;
      shipment.value = loaded;
      existingOrder.value = loadedOrder;
      form.value = formFromShipment(loaded);
      if (!contentOnly.value && (!loadedOrder || loadedOrder.state === 'rejected')) applyConfiguredSender();
      status.value = loaded.content.status;
      const selection = await logisticsService.getFreightSelection(loaded.id).catch(() => null);
      if (request !== generation) return;
      selectedQuote.value = selection;
      hasHistoricalQuote.value = !!selection;
      quoteIntent.value = selection;
      preferredCompanyId.value = selection?.companyId ?? null;
    } else {
      applyConfiguredSender();
      if (loadedOptions.platforms?.length === 1) {
        form.value.platformId = loadedOptions.platforms[0].id;
        platformChanged();
      }
    }
    requiresReload.value = false;
  } catch (cause) { if (request === generation) error.value = cause instanceof Error ? cause.message : '发货表单加载失败'; }
  finally { if (request === generation) loading.value = false; }
}

function validateShipment(value: ShipmentFormInput) {
  if ((!isEdit.value || sourceChanged.value) && (!value.platformId || !value.shopId)) return '请选择平台和店铺';
  if (!value.preparers.length) return '请至少选择一名备货人';
  if (![value.recipientName, value.recipientPhone, value.recipientProvince, value.recipientCity,
    value.recipientCounty, value.recipientDetailAddress, value.preparationContent].every(item => item.trim())) {
    return '请填写完整的收件人、地址和备货清单';
  }
  if (value.preparers.length > 20) return '备货人最多选择20人';
  if (value.estimatedFreight != null && value.estimatedFreight < 0) return '运费预测不能小于零';
  return '';
}
function validateOrder(value: ShipmentFormInput) {
  if (![value.senderName, value.senderPhone, value.senderProvince, value.senderCity, value.senderCounty,
    value.senderDetailAddress].every(item => item.trim())) {
    return '一键下单前请填写完整的发货人、电话和地址信息';
  }
  const draft = value.orderDraft;
  if (!draft.cargoName.trim() || !draft.packType.trim() || draft.weight == null || draft.weight < 1
    || draft.volume == null || draft.volume < 0.01 || draft.pieceAmount == null || draft.pieceAmount < 1
    || draft.productTypeId == null || draft.goodsType == null || draft.payType == null) {
    return '一键下单前请填写货物名称、包装、重量、体积、件数和物流选项';
  }
  if (!selectedQuote.value && !availability.value?.available) return availability.value?.message || '安能物流下单服务暂不可用';
  return '';
}

async function persist(andOrder: boolean) {
  if (busy.value || loading.value || senderPending.value || !canSave.value || requiresReload.value || (contentOnly.value && andOrder)) return;
  if (andOrder && !quoteAllowsOrder.value) { message.warning(selectedQuote.value?.apiMessage || '请重新测算并选择支持接口下单的合作物流'); return; }
  error.value = '';
  const value = payload();
  const invalid = contentOnly.value
    ? (!value.preparationContent.trim() || value.preparationContent.length > 10000 || value.remark.length > 5000
      ? '请填写备货物品，备货物品最多10000字，备注最多5000字' : '')
    : validateShipment(value) || (andOrder ? validateOrder(value) : '');
  if (invalid) { message.warning(invalid); return; }
  busy.value = true;
  try {
    const saved = isEdit.value && shipment.value
      ? contentOnly.value
        ? await shippingService.updatePreparationContent(shipment.value.id, {
          preparationContent: value.preparationContent.trim(), remark: value.remark.trim(), version: shipment.value.version
        })
        : await shippingService.update(shipment.value.id, value, status.value, shipment.value.version)
      : await shippingService.create(value);
    shipment.value = saved;
    form.value = formFromShipment(saved);
    status.value = saved.content.status;
    if (!andOrder) { message.success(contentOnly.value ? '备货清单与备注已保存' : '发货单已保存'); await router.push({ name: 'shipping-detail', params: { id: String(saved.id) } }); return; }
    try {
      const placed = await shippingService.placeLogisticsOrder(saved.id, { version: saved.version });
      existingOrder.value = placed;
      if (placed.state === 'succeeded') result.value = placed;
      else {
        const feedback = `发货单已保存，当前下单状态：${placed.message || placed.state}。请在详情页刷新核对。`;
        if (placed.state === 'rejected') message.error(feedback, 6000);
        else message.warning(feedback, 6000);
        await router.push({ name: 'shipping-detail', params: { id: String(saved.id) } });
      }
    } catch (cause) {
      message.warning(`发货单已保存，安能下单请求异常：${cause instanceof Error ? cause.message : '请稍后重试'}。请核实下单状态，勿重复下单。`, 6000);
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
  } catch (cause) {
    const code = (cause as { code?: string })?.code;
    if (contentOnly.value && ['SHIPMENT_VERSION_CONFLICT', 'SHIPMENT_PREPARATION_COMPLETED', 'SHIPMENT_CONTENT_FORBIDDEN'].includes(code ?? '')) requiresReload.value = true;
    const feedback = cause instanceof Error ? cause.message : '保存失败，请重试';
    if (['SHIPMENT_VERSION_CONFLICT', 'SHIPMENT_PREPARATION_COMPLETED', 'SHIPMENT_CONTENT_FORBIDDEN', 'VALIDATION_FAILED', 'SHIPMENT_PREPARER_SELECTION_INVALID', 'PLATFORM_DISABLED', 'PLATFORM_SHOP_DISABLED'].includes(code ?? '')) message.warning(feedback, 6000);
    else message.error(feedback, 6000);
    if (code === 'PLATFORM_DISABLED' || code === 'PLATFORM_SHOP_DISABLED') {
      try { options.value = await shippingService.formOptions(); } catch { /* Preserve the original save error and form. */ }
    }
  }
  finally { busy.value = false; }
}

function cancel() { return router.push(isEdit.value && shipment.value ? { name: 'shipping-detail', params: { id: String(shipment.value.id) } } : { name: 'shipping-list' }); }
function backToList() { result.value = null; return router.push({ name: 'shipping-list' }); }
async function continueOrder() {
  result.value = null; shipment.value = null; existingOrder.value = null; status.value = 'unfinished'; form.value = emptyShipmentForm();
  sourceChanged.value = false;
  selectedQuote.value = null; quoteTouched.value = false; preferredCompanyId.value = null; legacyQuoteMode.value = null;
  hasHistoricalQuote.value = false; repriceRequested.value = false;
  quoteIntent.value = null;
  applyConfiguredSender();
  await router.replace({ name: 'shipping-new', query: { reset: String(Date.now()) } });
  await initialize();
}
onMounted(initialize);
onBeforeUnmount(() => { generation++; senderGeneration++; });
</script>

<template>
  <section data-testid="shipment-form-page" class="mx-auto w-full max-w-[1500px] space-y-5 pb-8">
    <p v-if="sourceUnavailable" role="status" class="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">暂无可用店铺，请联系运营管理员在平台管理中创建或启用平台和店铺。</p>
    <header class="flex flex-wrap items-center justify-between gap-4"><div class="flex items-center gap-3"><button type="button" class="rounded-lg border border-slate-200 bg-white p-2 text-slate-500" @click="cancel"><ArrowLeft :size="18" /></button><div><h1 class="text-2xl font-bold text-[#25314d]">{{ isEdit ? '编辑发货单' : '新建发货单' }}</h1><p class="mt-1 text-sm text-slate-500">{{ isEdit ? shipment?.shipmentNo : '填写发货资料，可保存后直接向安能下单。' }}</p></div></div></header>
    <p v-if="error" role="alert" class="rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">{{ error }}<button data-testid="shipment-form-retry" type="button" :disabled="busy || loading" class="ml-3 font-semibold underline" @click="initialize">重新加载</button></p>
    <p v-if="contentOnly && shipment && !loading" class="rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm text-indigo-700">{{ preparationLocked ? '备货已完成，备货内容和备注已锁定。' : canEditContent ? '可修改本单的备货清单和备注，其他发货资料仅供查看。' : '只有本单下单人可以在备货完成前修改清单和备注。' }}</p>
    <p v-if="requiresReload && !error" role="alert" class="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700">发货单状态已变化，当前填写内容已保留，请重新加载后核对。<button data-testid="shipment-edit-reload" type="button" :disabled="busy || loading" class="ml-3 font-semibold underline" @click="initialize">重新加载最新信息</button></p>
    <p v-if="locked && !contentOnly" class="flex items-start gap-2 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700"><Info class="mt-0.5 shrink-0" :size="16" />该发货单已经提交物流，平台、店铺、备货人、发货人、收件资料、运费预测与安能下单资料已锁定；{{ preparationLocked ? '备货清单和备注也已锁定。' : '仍可维护备货清单、备注和状态。' }}</p>
    <div v-if="loading" role="status" class="rounded-xl border border-slate-200 bg-white py-24 text-center text-sm text-slate-500">正在准备发货表单…</div>
    <form v-else class="space-y-5" @submit.prevent>
      <section class="form-card"><div class="section-heading"><span>01</span><div><h2>发货单信息</h2><p>选择来源平台、店铺和本次参与备货的人员，并核对发货人资料。</p></div></div><div class="mt-5 grid gap-4 lg:grid-cols-3">
        <label class="field-label">平台 <span class="text-rose-500">*</span><select v-model="form.platformId" data-testid="shipment-platform" :disabled="busy || detailsLocked" class="field-input" @change="platformChanged"><option v-if="!originalSource" :value="null">请选择平台</option><option v-if="originalSource" :value="shipment?.content.platformId">{{ shipment?.content.platform || '历史未关联' }}（原记录）</option><option v-for="p in (options.platforms ?? []).filter(p => !originalSource || p.id !== shipment?.content.platformId)" :key="p.id" :value="p.id">{{ p.name }}</option></select></label>
        <label class="field-label">门店选项 <span class="text-rose-500">*</span><select v-model="form.shopId" data-testid="shipment-shop" :disabled="busy || detailsLocked || (!originalSource && !form.platformId)" class="field-input" @change="shopChanged"><option v-if="!originalSource" :value="null">请选择店铺</option><option v-if="originalSource" :value="shipment?.content.shopId">{{ shipment?.content.shopName || '历史未关联' }}（原记录）</option><option v-for="s in availableShops.filter(s => !originalSource || s.id !== shipment?.content.shopId)" :key="s.id" :value="s.id">{{ s.optionLabel || s.name }}</option></select></label>
        <label class="field-label">备货人（可多选） <span class="text-rose-500">*</span><PreparerMultiSelect v-model="form.preparers" v-model:employee-ids="form.preparerEmployeeIds" class="mt-1.5" :options="options.preparers" :disabled="busy || detailsLocked" /></label>
      </div>
        <button v-if="canReselectPreparers" data-testid="shipment-reselect-preparers" type="button" :disabled="busy" class="mt-3 text-xs font-medium text-[#536dff] disabled:opacity-40" @click="reselectPreparers">重新选择备货人</button>
        <div v-if="canLinkLegacyPreparers" class="mt-3 text-xs leading-6 text-amber-700">
          <p>历史单尚未关联备货账号，请确认原备货人对应的员工账号后保存。已下单记录仅允许关联原备货人。</p>
          <button v-if="form.preparerEmployeeIds === undefined" data-testid="shipment-link-preparers" type="button" class="font-medium text-[#536dff]" @click="form.preparerEmployeeIds = []">确认备货账号</button>
          <PreparerMultiSelect v-else v-model="form.preparers" v-model:employee-ids="form.preparerEmployeeIds" class="mt-2 max-w-lg" :options="options.preparers" :disabled="busy" />
        </div>
        <div data-testid="shipment-sender" class="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2"><p class="text-sm font-bold text-[#25314d]">发货人信息</p><span class="text-xs text-indigo-600">{{ detailsLocked ? '本单发货人资料' : '默认读取物流公司配置，可按本单修改' }}</span></div>
          <div class="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <label class="field-label">姓名 <span class="text-rose-500">*</span><input v-model="form.senderName" data-testid="sender-name" :disabled="busy || detailsLocked" maxlength="30" class="field-input" /></label>
            <label class="field-label">电话 <span class="text-rose-500">*</span><input v-model="form.senderPhone" data-testid="sender-phone" :disabled="busy || detailsLocked" maxlength="30" class="field-input" /></label>
            <label class="field-label">省 <span class="text-rose-500">*</span><input v-model="form.senderProvince" data-testid="sender-province" :disabled="busy || detailsLocked" maxlength="30" class="field-input" /></label>
            <label class="field-label">市 <span class="text-rose-500">*</span><input v-model="form.senderCity" data-testid="sender-city" :disabled="busy || detailsLocked" maxlength="30" class="field-input" /></label>
            <label class="field-label">区 / 县 <span class="text-rose-500">*</span><input v-model="form.senderCounty" data-testid="sender-county" :disabled="busy || detailsLocked" maxlength="30" class="field-input" /></label>
            <label class="field-label">详细地址 <span class="text-rose-500">*</span><input v-model="form.senderDetailAddress" data-testid="sender-detail-address" :disabled="busy || detailsLocked" maxlength="100" class="field-input" /></label>
          </div>
        </div>
      </section>

      <section class="form-card"><div class="section-heading"><span>02</span><div><h2>收件人信息</h2><p>粘贴原始信息后在当前卡片内识别，并核对拆分结果。</p></div></div><div class="mt-5"><RecipientRecognitionCard v-model="recipient" :disabled="busy || detailsLocked" /></div></section>

      <section class="form-card"><div class="section-heading"><span>03</span><div><h2>备货清单</h2><p>按行记录商品、规格和数量，可容纳多 SKU 的自由文本。</p></div></div><div class="mt-5 space-y-4">
        <p v-if="preparationLocked" class="text-xs text-amber-700">备货已完成，备货内容和备注已锁定。</p>
        <label class="field-label block">备货内容 <span class="text-rose-500">*</span><textarea v-model="form.preparationContent" data-testid="shipment-preparation-content" :disabled="busy || contentLocked" maxlength="10000" rows="12" placeholder="例如：&#10;蓝色水族箱 × 2&#10;过滤棉 × 6&#10;外箱 × 2" class="field-input min-h-72 resize-y" /></label>
        <label class="field-label block">备注<textarea v-model="form.remark" data-testid="shipment-remark" :disabled="busy || contentLocked" maxlength="5000" rows="3" placeholder="缺货、补发、包装注意事项等" class="field-input resize-y" /></label>
      </div></section>

      <section class="form-card"><div class="flex flex-wrap items-start justify-between gap-4"><div class="section-heading"><span>04</span><div><h2>物流资料与运费预测</h2><p>{{ availability?.message || '正在检查物流服务配置' }}</p></div></div><span class="rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700">物流公司：{{ selectedQuote?.companyName || shipment?.content.logisticsCompany || (legacyOrderingAllowed ? '安能物流' : '待选择') }}</span></div>
        <div class="mt-5 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <label class="field-label lg:col-span-2">货物名称 <span class="text-rose-500">*</span><input v-model="form.orderDraft.cargoName" data-testid="ane-cargo-name" :disabled="busy || detailsLocked" maxlength="32" placeholder="如水族用品" class="field-input" /></label>
          <label class="field-label">包装方式 <span class="text-rose-500">*</span><input v-model="form.orderDraft.packType" data-testid="ane-pack-type" :disabled="busy || detailsLocked" maxlength="50" class="field-input" /></label>
          <label class="field-label">件数 <span class="text-rose-500">*</span><input v-model="form.orderDraft.pieceAmount" data-testid="ane-piece-amount" :disabled="busy || detailsLocked" type="number" min="1" max="9999" step="1" class="field-input" /></label>
          <label class="field-label">重量（kg） <span class="text-rose-500">*</span><input v-model="form.orderDraft.weight" data-testid="ane-weight" :disabled="busy || detailsLocked" type="number" min="1" step="0.001" class="field-input" /></label>
          <label class="field-label">体积（m³） <span class="text-rose-500">*</span><input v-model="form.orderDraft.volume" data-testid="ane-volume" :disabled="busy || detailsLocked" type="number" min="0.01" step="0.01" class="field-input" /></label>
          <label class="field-label">物流产品 <span class="text-rose-500">*</span><select v-model="form.orderDraft.productTypeId" data-testid="shipment-logistics-product" :disabled="busy || detailsLocked || !!selectedQuote" class="field-input"><option :value="524">MiNi 电商小件</option><option :value="95">Mini 电商大件</option><option :value="24">精准零担</option><option :value="23">定时达</option><option :value="270">普惠达</option><option :value="546">安心达</option></select></label>
          <label class="field-label">送货方式 <span class="text-rose-500">*</span><select v-model="form.orderDraft.goodsType" :disabled="busy || detailsLocked" class="field-input"><option :value="180">送货（不含上楼）</option><option :value="179">送货上楼</option><option :value="285">自提</option></select></label>
          <label class="field-label">付款方式 <span class="text-rose-500">*</span><select v-model="form.orderDraft.payType" :disabled="busy || detailsLocked" class="field-input"><option :value="102">现金</option><option :value="104">月结</option><option :value="103">到付</option></select></label>
          <label class="field-label">运费预测（元，可选）<input v-model="form.estimatedFreight" :disabled="busy || detailsLocked || !!selectedQuote || !legacyOrderingAllowed" type="number" min="0" step="0.01" class="field-input" /></label>
          <label class="field-label md:col-span-2">物流备注<textarea v-model="form.orderDraft.logisticsRemark" :disabled="busy || detailsLocked" maxlength="200" rows="2" class="field-input resize-y" /></label>
        </div>
        <div v-if="!contentOnly && !locked && (!hasHistoricalQuote || repriceRequested)" class="mt-5"><ShipmentQuotePanel :input="quoteInput" :model-value="selectedQuote" :preferred-selection="quoteIntent" :disabled="busy" @update:model-value="chooseQuote" @loaded="quotesLoaded" @pending="quotePending = $event" /></div>
        <div v-else-if="selectedQuote" class="mt-5 rounded-lg bg-slate-50 p-4 text-sm text-slate-600"><p>已选物流：{{ selectedQuote.companyName }} / {{ selectedQuote.productName }} · 预计 ¥{{ selectedQuote.amount?.toFixed(2) }}</p><p class="mt-1 text-xs">{{ selectedQuote.matchedRegion }} · {{ selectedQuote.billedQuantity }} {{ selectedQuote.chargeMode === 'piece' ? '件' : 'kg' }}，历史报价按保存时模板保留。仅修改备货内容和备注不重新计价。</p><button v-if="!contentOnly && !locked" data-testid="shipment-requote" type="button" :disabled="busy" class="mt-3 font-semibold text-indigo-600" @click="repriceRequested = true">重新测算并选择物流</button></div>
        <p v-if="selectedQuote && !selectedQuote.apiReady" class="mt-3 text-xs text-amber-700">{{ selectedQuote.apiMessage || '该物流暂未接入接口，可保存发货单后线下下单。' }}</p>
      </section>

      <section v-if="isEdit && canEditGeneral" class="form-card"><div class="section-heading"><span>05</span><div><h2>备货状态</h2><p>安能下单成功不会改变备货状态，请按实际备货进度维护。</p></div></div><label class="field-label mt-5 block max-w-sm">当前备货状态<select v-model="status" data-testid="shipment-status" :disabled="busy" class="field-input"><option v-for="item in SHIPMENT_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></section>

      <div class="flex flex-wrap justify-end gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><button type="button" :disabled="busy" class="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 disabled:opacity-40" @click="cancel">取消</button><button data-testid="shipment-save-only" type="button" :disabled="busy || senderPending || sourceUnavailable || !canSave || requiresReload" class="inline-flex items-center gap-2 rounded-lg border border-indigo-200 px-5 py-2.5 text-sm font-semibold text-[#536dff] disabled:opacity-40" @click="persist(false)"><Save :size="16" />{{ busy ? '处理中…' : isEdit ? '仅保存修改' : '仅保存发货单' }}</button><button v-if="canOrder && !locked && !contentOnly" data-testid="shipment-save-and-order" type="button" :disabled="busy || sourceUnavailable || !canSave || requiresReload || !quoteAllowsOrder" class="inline-flex items-center gap-2 rounded-lg bg-[#536dff] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-40" @click="persist(true)"><Send :size="16" />{{ busy ? '处理中…' : '保存并一键下单' }}</button></div>
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
.meta-label { @apply text-xs font-medium text-slate-400; }
.meta-value { @apply mt-1.5 text-sm text-slate-700; }
</style>
