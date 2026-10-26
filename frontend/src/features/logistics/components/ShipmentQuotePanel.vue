<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { message } from '../../../components/feedback/message';
import { logisticsService } from '../logisticsService';
import type { Quote, QuoteBatch, QuoteInput } from '../types';
const props=defineProps<{input:QuoteInput;modelValue:Quote|null;disabled?:boolean;preferredSelection?:Quote|null}>();
const emit=defineEmits<{'update:modelValue':[value:Quote|null];loaded:[batch:QuoteBatch];pending:[value:boolean]}>();
const rows=ref<Quote[]>([]), busy=ref(false), error=ref(''), legacy=ref(false);
const rowsInput=ref<string|null>(null);
let generation=0, timer:ReturnType<typeof setTimeout>|undefined;
const key=(quote:Quote)=>`${quote.companyId}:${quote.productCode}:${quote.templateId}`;
let preferred=props.modelValue || props.preferredSelection ? key((props.modelValue ?? props.preferredSelection)!) : null;
const complete=computed(()=>Boolean(props.input.senderProvince.trim() && props.input.senderCity.trim()
  && props.input.recipientProvince.trim() && props.input.recipientCity.trim()
  && ((props.input.weight ?? 0)>0 || (props.input.pieceAmount ?? 0)>0)));
const usable=(quote:Quote)=>rowsInput.value===JSON.stringify(props.input) && quote.available && quote.amount!==null && Boolean(quote.quoteId)
  && (!quote.expiresAt || Date.parse(quote.expiresAt)>Date.now());
function pending(value:boolean) { busy.value=value; emit('pending',value); }
watch(()=>props.modelValue,quote=>{if(quote) preferred=key(quote);});
async function refresh() {
  clearTimeout(timer);
  const token=++generation;
  if(props.disabled || !complete.value) {pending(false);return;}
  pending(true); error.value='';
  const inputKey=JSON.stringify(props.input);
  try {
    const result=await logisticsService.getQuotes(JSON.parse(inputKey) as QuoteInput);
    if(token!==generation)return;
    rowsInput.value=inputKey;rows.value=result.quotes; legacy.value=result.legacyOrderingAllowed; emit('loaded',result);
    if(preferred) {
      const retained=result.quotes.find(row=>key(row)===preferred && usable(row));
      if(retained && retained.quoteId!==props.modelValue?.quoteId) emit('update:modelValue',retained);
      else if(!retained && props.modelValue) {emit('update:modelValue',null);message.warning('所选方案已失效，请重新选择物流');}
    }
  }catch(cause) {
    if(token!==generation)return;
    error.value=cause instanceof Error?cause.message:'运费测算失败';
    message.error(error.value);
  }finally {if(token===generation)pending(false);}
}
watch(()=>JSON.stringify(props.input),(_value,old)=>{
  ++generation; clearTimeout(timer); error.value='';
  if(old!==undefined && props.modelValue) {preferred=key(props.modelValue);emit('update:modelValue',null);}
  if(props.disabled || !complete.value){rows.value=[];pending(false);return;}
  pending(true);timer=setTimeout(()=>void refresh(),250);
},{immediate:true,flush:'sync'});
watch(()=>props.disabled,disabled=>{
  ++generation;clearTimeout(timer);pending(false);
  if(!disabled && complete.value)void refresh();
});
function select(row:Quote) { if(busy.value||props.disabled||!usable(row))return;preferred=key(row);emit('update:modelValue',row); }
function clearSelection(){preferred=null;emit('update:modelValue',null);}
onBeforeUnmount(()=>{++generation;clearTimeout(timer);emit('pending',false);});
</script>
<template>
  <section data-testid="shipment-quote-panel" class="rounded-xl border border-slate-200 bg-white">
    <header class="flex items-center justify-between gap-3 border-b p-4"><div><h3 class="font-semibold text-slate-800">合作物流运费比较</h3><p class="mt-1 text-xs text-slate-500">合同模板预估，报价有效期 10 分钟；额外服务费用未包含，最终以承运商账单为准。</p></div><button data-testid="quote-refresh" type="button" :disabled="disabled || busy || !complete" class="shrink-0 text-sm text-indigo-600 disabled:opacity-40" @click="refresh">重新测算</button></header>
    <p v-if="!complete" class="p-4 text-sm text-slate-500">填写寄收省市及重量或件数后自动测算。没有报价也可先保存发货资料。</p>
    <p v-if="busy" role="status" class="p-4 text-sm text-indigo-600">正在测算，原报价暂不可选择…</p>
    <p v-if="error" role="alert" class="p-4 text-sm text-rose-600">{{ error }}，可点击重新测算。</p>
    <div v-if="rows.length" class="overflow-x-auto"><table class="w-full min-w-[720px] text-left text-sm"><thead class="bg-slate-50 text-xs text-slate-500"><tr><th>物流 / 产品</th><th>预估运费</th><th>计费依据</th><th>接口能力</th><th>选择</th></tr></thead><tbody><tr v-for="(row,index) in rows" :key="row.quoteId ?? `${row.companyId}-${row.productCode}-${index}`" class="border-t border-slate-100" :class="{'bg-indigo-50/50': modelValue?.quoteId && modelValue.quoteId===row.quoteId}"><td><p class="font-semibold text-slate-800">{{ row.companyName }} <span v-if="row.recommended && row.available" class="ml-1 text-xs text-emerald-600" title="满足当前报价条件，预估总价最低">最低价推荐</span></p><p class="mt-1 text-xs text-slate-500">{{ row.productName || row.productCode || '未配置运输产品' }}</p></td><td><strong v-if="row.available && row.amount!==null" class="text-lg text-indigo-600">¥{{ row.amount.toFixed(2) }}</strong><span v-else class="text-slate-500">无法估价</span><p v-if="!row.available" class="mt-1 max-w-48 text-xs text-amber-700">{{ row.reason }}</p></td><td class="text-xs text-slate-500"><template v-if="row.available">{{ row.matchedRegion }} · {{ row.billedQuantity }} {{ row.chargeMode==='piece'?'件':'kg' }}<br>区间 ({{ row.lowerExclusive }}, {{ row.upperInclusive ?? '∞' }}]；首 {{ row.firstQuantity }} / ¥{{ row.firstFee }}<br>续 {{ row.additionalQuantity }} / ¥{{ row.additionalFee }} × {{ row.renewalCount }}</template><template v-else>—</template></td><td class="max-w-44 text-xs" :class="row.apiReady?'text-emerald-600':'text-amber-700'">{{ row.apiReady?'接口可下单':row.apiMessage || '需线下下单' }}</td><td><button :data-testid="`quote-select-${row.quoteId}`" type="button" :disabled="disabled || busy || !usable(row)" class="rounded border border-indigo-200 px-3 py-2 text-indigo-600 disabled:cursor-not-allowed disabled:opacity-40" @click="select(row)">{{ modelValue?.quoteId && modelValue.quoteId===row.quoteId?'已选择':'选择' }}</button></td></tr></tbody></table></div>
    <p v-else-if="complete && !busy && !error" class="p-4 text-sm text-slate-500">暂无有效合作报价，可先保存资料。{{ legacy?'尚未启用模板，原安能人工预计流程仍可使用。':'' }}</p>
    <footer v-if="modelValue" class="flex items-center justify-between border-t p-4 text-xs text-slate-500"><span>已选 {{ modelValue.companyName }} / {{ modelValue.productName }}{{ modelValue.apiReady?'':' · 需线下下单' }}</span><button type="button" :disabled="disabled || busy" class="text-indigo-600 disabled:opacity-40" @click="clearSelection">清除选择</button></footer>
  </section>
</template>
<style scoped>th,td{@apply px-4 py-3;}</style>
