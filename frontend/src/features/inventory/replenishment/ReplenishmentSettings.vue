<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue';
import { Calculator, CircleAlert, PencilLine, Warehouse, X } from 'lucide-vue-next';
import {
  demandHistoryDays, evaluate, isReplenishmentDays, isReplenishmentThreshold,
  replenishmentDayLimit, replenishmentThresholdLimit,
  type ReplenishmentConfig, type ReplenishmentRow
} from './replenishmentModel';

const props = defineProps<{ row: ReplenishmentRow; bulkCount?: number; saving?: boolean; saveError?: string }>();
const emit = defineEmits<{ close: []; save: [config: ReplenishmentConfig] }>();
const dialog = ref<HTMLDialogElement | null>(null);
const attempted = ref(false);
const bulk = computed(() => props.bulkCount !== undefined);
const initialConfig: ReplenishmentConfig = bulk.value
  ? { mode: 'auto', bufferDays: 5, leadDays: 8, manualSafety: null, manualPoint: null }
  : props.row.config;
const inputText = (value: number | null) => value === null ? '' : String(value);
const draft = reactive({
  mode: initialConfig.mode,
  bufferDays: inputText(initialConfig.bufferDays),
  leadDays: inputText(initialConfig.leadDays),
  manualSafety: inputText(initialConfig.manualSafety),
  manualPoint: inputText(initialConfig.manualPoint)
});

const shared = computed(() => props.row.warehouseId === null);
const warehouseNames = computed(() => props.row.warehouses.map(({ name }) => name).join('、'));
const historyWindow = computed(() => {
  const { windowStart, windowEnd } = props.row;
  const sameYear = windowStart.slice(0, 4) === windowEnd.slice(0, 4);
  return `${windowStart.replaceAll('-', '.')} – ${(sameYear ? windowEnd.slice(5) : windowEnd).replaceAll('-', '.')}`;
});
const historyReady = computed(() => props.row.historyDays >= demandHistoryDays);
const auto = computed(() => draft.mode === 'auto');
const fallback = computed(() => auto.value && (bulk.value || !historyReady.value));
const showThresholds = computed(() => !auto.value || fallback.value);
const number = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 });
const averageNumber = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 6 });
const format = (value: number | null | undefined) => value == null ? '—' : number.format(value);
const averageDisplay = computed(() => {
  if (!historyReady.value) return '—';
  const average = props.row.demandTotal / demandHistoryDays;
  const formatted = averageNumber.format(props.row.average ?? average);
  return Number(formatted.replaceAll(',', '')) === average ? formatted : `约 ${formatted}`;
});
const thresholdFields = [
  { key: 'manualSafety', label: '固定安全库存' },
  { key: 'manualPoint', label: '固定补货点' }
] as const;
const dayFields = [
  { key: 'bufferDays', label: '缓冲天数' },
  { key: 'leadDays', label: '采购到可售入库天数' }
] as const;
const states = { normal: '正常', warning: '建议补货', critical: '紧急补货', pending: '待设置' };

function integer(value: string): number | null {
  return /^\d+$/.test(value) ? Number(value) : null;
}

const validation = computed<{ config: ReplenishmentConfig; error?: never } | { config?: never; error: string }>(() => {
  const bufferDays = auto.value ? integer(draft.bufferDays) : initialConfig.bufferDays;
  const leadDays = auto.value ? integer(draft.leadDays) : initialConfig.leadDays;
  if (!isReplenishmentDays(bufferDays) || !isReplenishmentDays(leadDays)) {
    return { error: `天数须填写 0 至 ${format(replenishmentDayLimit)} 之间的整数。` };
  }

  const config = { ...initialConfig, mode: draft.mode, bufferDays, leadDays };
  if (!showThresholds.value) return { config };
  if (fallback.value && !draft.manualSafety && !draft.manualPoint) {
    return { config: { ...config, manualSafety: null, manualPoint: null } };
  }

  const manualSafety = integer(draft.manualSafety);
  const manualPoint = integer(draft.manualPoint);
  if (!isReplenishmentThreshold(manualSafety) || !isReplenishmentThreshold(manualPoint)) {
    return { error: `两个阈值都须填写 0 至 ${format(replenishmentThresholdLimit)} 之间的整数。` };
  }
  if (manualPoint < manualSafety) return { error: '补货点须大于或等于安全库存。' };
  return { config: { ...config, manualSafety, manualPoint } };
});

const result = computed(() => !bulk.value && validation.value.config ? evaluate(props.row, validation.value.config) : null);
const demandTerm = computed(() => {
  const average = result.value?.average;
  if (average == null) return '';
  const { demandTotal, demandTotalExact } = props.row;
  if (demandTotalExact === undefined && Number(format(average).replaceAll(',', '')) === average) return format(average);
  const exact = demandTotalExact?.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
  const total = exact ?? format(demandTotal);
  const displayedAverage = format(average);
  const scaledTotal = Number(total.replaceAll(',', ''));
  if (Number.isSafeInteger(scaledTotal) && scaledTotal % demandHistoryDays === 0) return displayedAverage;
  return `(${total} ÷ ${demandHistoryDays})`;
});
const results = computed(() => {
  const bufferDays = validation.value.config?.bufferDays;
  const leadDays = validation.value.config?.leadDays;
  const demand = demandTerm.value;
  return [
    { label: '安全库存', key: 'safety', rule: '日均需求 × 缓冲天数', calculation: `${demand} × ${bufferDays}` },
    { label: '补货点', key: 'point', rule: '日均需求 × 交期 + 安全库存', calculation: `${demand} × ${leadDays} + ${demand} × ${bufferDays}` }
  ] as const;
});

onMounted(() => dialog.value?.showModal());
onBeforeUnmount(() => {
  if (dialog.value?.open) dialog.value.close();
});

function close() {
  if (props.saving) return;
  dialog.value?.close();
  emit('close');
}

function save() {
  if (props.saving) return;
  attempted.value = true;
  if (validation.value.config) emit('save', validation.value.config);
}
</script>

<template>
  <dialog ref="dialog" class="stock-drawer" aria-labelledby="stock-settings-title" @cancel.prevent="close" @click.self="close">
    <form class="stock-form" novalidate @submit.prevent="save">
      <header class="stock-header">
        <h2 id="stock-settings-title">{{ bulk ? '批量设置安全库存' : '安全库存设置' }}</h2>
        <button type="button" class="stock-close" aria-label="关闭设置" :disabled="saving" autofocus @click="close"><X :size="20" aria-hidden="true" /></button>
      </header>

      <div class="stock-body">
        <div v-if="bulk" class="stock-sku">
          <div class="stock-sku-line"><strong>已选 {{ bulkCount }} 条库存记录</strong></div>
          <p class="stock-warehouse">将覆盖所选记录的补货设置。自动模式按每条记录的销量分别计算。</p>
        </div>
        <div v-else class="stock-sku">
          <div class="stock-sku-line">
            <span><strong>{{ row.sku }}</strong><span class="stock-product">{{ row.name }}</span></span>
            <span class="stock-scope">{{ shared ? '共用库存' : '独立补货' }}</span>
          </div>
          <p class="stock-warehouse"><Warehouse :size="15" aria-hidden="true" />{{ warehouseNames || '—' }}<span>·</span>{{ shared ? '按 SKU 设置' : '按 SKU × 仓库设置' }}</p>
        </div>

        <fieldset class="stock-mode" :disabled="saving">
          <legend>设置模式</legend>
          <div class="stock-mode-options">
            <label :class="{ selected: auto }">
              <input v-model="draft.mode" type="radio" name="stock-mode" value="auto" @change="attempted = false" />
              <Calculator :size="16" aria-hidden="true" />自动计算
            </label>
            <label :class="{ selected: !auto }">
              <input v-model="draft.mode" type="radio" name="stock-mode" value="manual" @change="attempted = false" />
              <PencilLine :size="16" aria-hidden="true" />手工设置
            </label>
          </div>
        </fieldset>

        <section v-if="auto" class="stock-section" aria-labelledby="stock-demand-title">
          <h3 id="stock-demand-title">{{ bulk ? '缓冲与交期' : '需求与交期' }}</h3>
          <div v-if="!bulk" class="stock-demand">
            <div>
              <span>最近 {{ demandHistoryDays }} 个完整自然日</span>
              <strong>{{ historyWindow }}</strong>
            </div>
            <div>
              <span>日均需求</span>
              <strong class="stock-average">{{ averageDisplay }}<small v-if="historyReady">个 / 天</small></strong>
            </div>
          </div>
          <div class="stock-fields">
            <label v-for="field in dayFields" :key="field.key">
              <span>{{ field.label }}</span>
              <span class="stock-input">
                <input v-model.trim="draft[field.key]" :data-stock-field="field.key" type="text" inputmode="numeric" autocomplete="off" :disabled="saving" />
                <span aria-hidden="true">天</span>
              </span>
            </label>
          </div>
          <p v-if="fallback && !bulk" class="stock-history"><CircleAlert :size="16" aria-hidden="true" />已有 {{ row.historyDays }} / {{ demandHistoryDays }} 天销量历史。可先填写手工兜底，两项都留空时显示「待设置」。历史满 {{ demandHistoryDays }} 天后，自动阈值生效。</p>
        </section>

        <section v-if="showThresholds" class="stock-section" aria-labelledby="stock-thresholds-title">
          <h3 id="stock-thresholds-title">{{ fallback ? (bulk ? '手工兜底（选填）' : '手工兜底') : '固定阈值' }}</h3>
          <p v-if="!fallback" class="stock-help">直接指定生效的安全库存与补货点。</p>
          <p v-else-if="bulk" class="stock-help">销量历史不足 {{ demandHistoryDays }} 天的记录使用兜底阈值。两项均留空时，这些记录显示「待设置」。</p>
          <div class="stock-fields">
            <label v-for="field in thresholdFields" :key="field.key">
              <span>{{ field.label }}</span>
              <span class="stock-input">
                <input v-model.trim="draft[field.key]" :data-stock-field="field.key" type="text" inputmode="numeric" autocomplete="off" :disabled="saving" />
                <span aria-hidden="true">个</span>
              </span>
            </label>
          </div>
        </section>

        <section v-if="!bulk" class="stock-section" aria-labelledby="stock-result-title">
          <div class="stock-result-heading">
            <h3 id="stock-result-title">结果预览</h3>
            <span v-if="result" class="stock-status" :data-state="result.status">{{ states[result.status] }}</span>
          </div>
          <div class="stock-results" aria-live="polite" aria-atomic="true">
            <div v-for="item in results" :key="item.key" class="stock-result">
              <div>
                <strong>{{ item.label }}</strong>
                <p v-if="auto && historyReady">{{ item.rule }}<template v-if="result?.source === 'auto'"><br />{{ item.calculation }}</template></p>
                <p v-else>{{ fallback ? '手工兜底阈值' : '手工固定阈值' }}</p>
              </div>
              <span class="stock-value" :data-stock-result="item.key">{{ format(result?.[item.key]) }}<small v-if="result?.[item.key] != null">个</small></span>
            </div>
          </div>
          <p v-if="result?.source === 'auto'" class="stock-help">两项结果分别向上取整，补货点使用取整前的安全库存计算。</p>
          <p v-else-if="auto && historyReady && result?.source === 'pending'" class="stock-help">计算结果超出可用范围，可缩短缓冲天数或采购交期。</p>
        </section>
      </div>

      <footer class="stock-footer">
        <p v-if="(attempted && validation.error) || saveError" class="stock-error" role="alert"><CircleAlert :size="16" aria-hidden="true" />{{ (attempted && validation.error) || saveError }}</p>
        <div class="stock-actions">
          <button type="button" class="stock-cancel" :disabled="saving" @click="close">取消</button>
          <button type="submit" class="stock-apply" :disabled="saving">{{ saving ? '正在保存…' : (bulk ? '保存批量设置' : '保存设置') }}</button>
        </div>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.stock-drawer {
  --stock-ink: #25314d;
  --stock-muted: #5b6980;
  --stock-border: #e2e8f0;
  --stock-blue: #4059df;
  position: fixed; inset: 0 0 0 auto; margin: 0;
  width: min(100%, 520px); max-width: none; height: 100dvh; max-height: none;
  overflow: hidden; padding: 0; border: 0; border-left: 1px solid var(--stock-border);
  background: white; color: var(--stock-ink); box-shadow: -10px 0 32px rgb(15 23 42 / 12%);
  font: inherit;
}
.stock-drawer::backdrop { background: rgb(15 23 42 / 30%); }
.stock-drawer[open] { animation: stock-enter 160ms ease-out both; }
@keyframes stock-enter { from { transform: translateX(20px); opacity: .75; } to { transform: translateX(0); opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .stock-drawer[open] { animation: none; } }
.stock-form { height: 100%; display: flex; flex-direction: column; }
.stock-header, .stock-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-inline: 24px; flex-shrink: 0; }
.stock-header { min-height: 68px; border-bottom: 1px solid var(--stock-border); }
.stock-header h2 { margin: 0; font-size: 20px; font-weight: 700; }
.stock-close { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; color: var(--stock-muted); }
.stock-close:hover { background: #f3f5fa; color: var(--stock-ink); }
.stock-body { flex: 1; min-height: 0; overflow-y: auto; padding: 20px 24px 24px; }
.stock-sku { padding: 13px 14px; border: 1px solid var(--stock-border); border-radius: 10px; background: #f8fafc; }
.stock-sku-line { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.stock-sku-line strong { font-size: 14px; font-weight: 720; }
.stock-product { display: inline-block; margin-left: 9px; font-size: 13px; }
.stock-scope { flex-shrink: 0; padding: 2px 8px; border-radius: 5px; background: #eaf0ff; color: #3f53a7; font-size: 11px; font-weight: 650; }
.stock-warehouse { display: flex; align-items: center; gap: 6px; margin: 7px 0 0; color: var(--stock-muted); font-size: 12px; }
.stock-warehouse svg { flex: none; }
.stock-mode, .stock-section { margin: 18px 0 0; }
.stock-mode { min-width: 0; }
.stock-mode legend, .stock-section h3 { margin: 0 0 8px; font-size: 14px; font-weight: 700; }
.stock-mode-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; border: 1px solid var(--stock-border); border-radius: 9px; padding: 4px; background: #f6f7fb; }
.stock-mode-options label { position: relative; display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 36px; border-radius: 6px; border: 1px solid transparent; color: var(--stock-muted); font-size: 13px; font-weight: 650; cursor: pointer; }
.stock-mode-options label:hover { color: var(--stock-blue); }
.stock-mode-options label.selected { border-color: #dbe2ff; background: white; color: var(--stock-blue); }
.stock-mode-options input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.stock-mode-options label:has(input:focus-visible) { outline: 2px solid var(--stock-blue); outline-offset: 1px; }
.stock-demand, .stock-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.stock-demand { margin-bottom: 12px; padding: 11px 13px; border-radius: 8px; background: #f6f7fb; }
.stock-demand span { display: block; color: var(--stock-muted); font-size: 12px; }
.stock-demand strong { display: block; margin-top: 4px; font-size: 13px; font-weight: 650; font-variant-numeric: tabular-nums; }
.stock-demand .stock-average { font-size: 15px; color: var(--stock-blue); }
.stock-average small, .stock-value small { margin-left: 5px; color: var(--stock-muted); font-size: 11px; font-weight: 500; }
.stock-fields label { display: grid; gap: 6px; min-width: 0; font-size: 12px; font-weight: 600; }
.stock-input { display: flex; align-items: center; min-height: 38px; border: 1px solid var(--stock-border); border-radius: 8px; background: white; }
.stock-input:focus-within { border-color: #536dff; outline: 1px solid #536dff; }
.stock-input input { min-width: 0; width: 100%; padding: 8px 11px; outline: none; border: 0; background: transparent; font-size: 14px; }
.stock-input > span { padding-right: 10px; color: var(--stock-muted); font-weight: 500; }
.stock-help { margin: 7px 0 0; color: var(--stock-muted); font-size: 12px; line-height: 1.6; }
#stock-thresholds-title + .stock-help { margin: -3px 0 8px; }
.stock-history, .stock-error { display: flex; align-items: flex-start; gap: 7px; margin: 10px 0 0; font-size: 12px; line-height: 1.55; }
.stock-history { padding: 8px 10px; border-radius: 6px; background: #fff8e8; color: #85470d; }
.stock-error { color: #a32338; }
.stock-history svg, .stock-error svg { flex: none; margin-top: 1px; }
.stock-result-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
.stock-status { padding: 2px 7px; border-radius: 5px; background: #f1f4f8; color: #4c5c76; font-size: 11px; font-weight: 650; }
.stock-status[data-state="warning"] { background: #fff3dc; color: #8a4b0f; }
.stock-status[data-state="critical"] { background: #ffedf0; color: #a32338; }
.stock-status[data-state="normal"] { background: #e7f5ef; color: #19604a; }
.stock-results { padding-inline: 13px; border: 1px solid #dfe6fc; border-radius: 9px; background: #f8faff; }
.stock-result { min-height: 62px; display: flex; justify-content: space-between; align-items: center; gap: 9px; padding-block: 8px; }
.stock-result + .stock-result { border-top: 1px solid #e6ebf9; }
.stock-result strong { font-size: 13px; font-weight: 700; }
.stock-result p { margin: 2px 0 0; color: var(--stock-muted); font-size: 11px; line-height: 1.45; }
.stock-value { white-space: nowrap; color: var(--stock-blue); font-size: 22px; font-weight: 700; font-variant-numeric: tabular-nums; }
.stock-footer { min-height: 67px; flex-direction: column; align-items: stretch; justify-content: center; padding-block: 12px; border-top: 1px solid var(--stock-border); background: white; }
.stock-footer .stock-error { margin: 0; }
.stock-actions { display: flex; gap: 8px; flex-shrink: 0; align-self: flex-end; }
.stock-actions button { min-height: 36px; padding: 0 13px; border: 1px solid transparent; border-radius: 8px; white-space: nowrap; font-size: 13px; font-weight: 650; }
.stock-actions .stock-cancel { border-color: var(--stock-border); background: white; }
.stock-cancel:hover { background: #f6f7fb; }
.stock-apply { background: var(--stock-blue); color: white; }
.stock-apply:hover { background: #344bc9; }
.stock-actions button:disabled, .stock-close:disabled { opacity: .6; cursor: wait; }
.stock-mode:disabled label, .stock-input:has(input:disabled), .stock-input input:disabled { cursor: wait; }
.stock-drawer button:focus-visible { outline: 2px solid var(--stock-blue); outline-offset: 2px; }
@media (max-width: 480px) {
  .stock-header, .stock-footer { padding-inline: 16px; }
  .stock-body { padding-inline: 16px; }
  .stock-warehouse { flex-wrap: wrap; }
  .stock-footer { flex-wrap: wrap; }
  .stock-actions { margin-left: auto; }
}
</style>
