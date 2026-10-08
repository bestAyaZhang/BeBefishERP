<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import AccessibleDialog from '../../components/AccessibleDialog.vue';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import { shippingService } from './shippingService';
import { canUpdatePreparation } from './preparationAccess';
import { SHIPMENT_STATUSES, type Shipment, type ShipmentStatus } from './types';

const props = defineProps<{ shipmentId: number | null }>();
const emit = defineEmits<{ close: []; saved: [shipment: Shipment] }>();
const shipment = ref<Shipment | null>(null);
const status = ref<ShipmentStatus>('unfinished');
const actualWeight = ref<string | number>('');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const conflict = ref(false);
let generation = 0;
async function load() {
  const id = props.shipmentId;
  const request = ++generation;
  if (id == null) return;
  loading.value = true; error.value = ''; conflict.value = false; shipment.value = null;
  try {
    const latest = await shippingService.get(id);
    if (request !== generation) return;
    if (!canUpdatePreparation(latest, currentUser.value)) throw new Error('此单未分配给当前账号，或没有更新备货权限');
    shipment.value = latest; status.value = latest.content.status; actualWeight.value = latest.preparation?.actualWeight ?? '';
  } catch (cause) { if (request === generation) error.value = cause instanceof Error ? cause.message : '发货单加载失败'; }
  finally { if (request === generation) loading.value = false; }
}
async function save() {
  if (saving.value || loading.value || conflict.value || !shipment.value) return;
  const weight = actualWeight.value === '' ? null : Number(actualWeight.value);
  if (weight != null && (!Number.isFinite(weight) || weight <= 0 || weight > 999999999.999 || !/^\d+(\.\d{1,3})?$/.test(String(actualWeight.value)))) {
    message.warning('实际重量必须大于零，最多9位整数、3位小数'); return;
  }
  saving.value = true; error.value = '';
  try {
    const updated = await shippingService.updatePreparation(shipment.value.id, { status: status.value, actualWeight: weight, version: shipment.value.version });
    emit('saved', updated);
  } catch (cause) {
    const code = (cause as { code?: string })?.code;
    conflict.value = code === 'SHIPMENT_VERSION_CONFLICT';
    const feedback = cause instanceof Error ? cause.message : '保存失败，请重试';
    if (conflict.value || code === 'VALIDATION_FAILED') message.warning(feedback, 6000);
    else message.error(feedback, 6000);
  } finally { saving.value = false; }
}
function close() { if (!saving.value) emit('close'); }
watch(() => props.shipmentId, load, { immediate: true });
onBeforeUnmount(() => generation++);
</script>

<template>
  <AccessibleDialog :open="shipmentId != null" title="更新备货" test-id="preparation-dialog" body-test-id="preparation-body" footer-test-id="preparation-footer" close-test-id="preparation-close" panel-class="w-[min(560px,calc(100vw-32px))]" @cancel="close">
    <p v-if="loading" role="status" class="py-8 text-center text-sm text-slate-500">正在加载最新备货信息…</p>
    <div v-if="shipment" class="space-y-5">
      <div class="rounded-lg bg-slate-50 p-4 text-sm leading-6 text-slate-600"><p class="font-medium text-slate-800">{{ shipment.content.recipientName }} · {{ shipment.content.shopName }}</p><p class="mt-2 max-h-36 overflow-y-auto whitespace-pre-wrap break-words">{{ shipment.content.preparationContent }}</p><p class="mt-2 text-xs text-slate-400">备货人：{{ shipment.content.preparers.join('、') }}</p></div>
      <label class="block text-sm font-medium text-slate-700">备货状态 <span class="text-rose-500">*</span><select v-model="status" data-testid="preparation-status" :disabled="saving" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-white px-3"><option v-for="option in SHIPMENT_STATUSES" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      <label class="block text-sm font-medium text-slate-700">实际重量（kg）<input v-model="actualWeight" data-testid="preparation-actual-weight" type="number" min="0.001" max="999999999.999" step="0.001" :disabled="saving" placeholder="称重后填写，最多3位小数" class="mt-2 h-11 w-full rounded-lg border border-slate-200 px-3" @keydown.enter.prevent="save" /></label>
      <p class="text-xs leading-6 text-slate-500">尚未称重可留空；已称重时留空保留原实际重量。安能下单重量：{{ shipment.content.orderDraft.weight == null ? '未填写' : `${shipment.content.orderDraft.weight} kg` }}，本次回写不修改物流订单。</p>
      <p v-if="shipment.preparation?.updatedAt" class="text-xs text-slate-400">上次更新：{{ shipment.preparation.updatedBy }} · {{ shipment.preparation.updatedAt.replace('T', ' ') }}</p>
    </div>
    <div v-if="error" role="alert" class="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{{ error }}<button v-if="!saving" data-testid="preparation-reload" type="button" class="mt-2 block font-medium underline" @click="load">重新加载最新信息</button></div>
    <p v-if="conflict" class="mt-4 text-sm leading-6 text-amber-700">发货单已被更新，当前填写内容已保留。请重新加载最新信息后再保存。<button data-testid="preparation-reload" type="button" class="mt-2 block font-medium underline" @click="load">重新加载最新信息</button></p>
    <template #footer><button type="button" :disabled="saving" class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600 disabled:opacity-40" @click="close">取消</button><button data-testid="preparation-save" type="button" :disabled="saving || loading || !shipment || conflict" class="rounded-lg bg-[#536dff] px-5 py-2 text-sm font-semibold text-white disabled:opacity-40" @click="save">{{ saving ? '正在保存…' : '保存备货信息' }}</button></template>
  </AccessibleDialog>
</template>
