<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { Archive, Calculator, Copy, Plus, RotateCcw, Save, Search, Trash2 } from 'lucide-vue-next';
import AccessibleDialog from '../../../components/AccessibleDialog.vue';
import { currentUser } from '../../../services/authSession';
import { message } from '../../../components/feedback/message';
import { calculateCost, emptyCostLine, priceForMargin, validateCostLine, type CostLine } from './pddCostCalculator';
import { pddCostService, type CostPlan, type CostPlanSummary, type CostSkuOption, type PddCostService } from './pddCostService';

const props=defineProps<{service?:PddCostService}>();
const service=props.service??pddCostService;
const canEdit=computed(()=>currentUser.value?.roles.includes('SUPER_ADMIN') || currentUser.value?.permissions.includes('product:edit'));
const id=ref<number|null>(null), version=ref<number|null>(null), name=ref(''), remark=ref(''), archived=ref(false);
const lines=ref<CostLine[]>([emptyCostLine()]);
const savedState=ref('');
const serialize=()=>JSON.stringify({name:name.value,remark:remark.value,lines:lines.value});
savedState.value=serialize();
const dirty=computed(()=>serialize()!==savedState.value);
const editable=computed(()=>canEdit.value&&!archived.value);
const saving=ref(false), opening=ref(false), listLoading=ref(false), listError=ref(''), formError=ref('');
const plans=ref<CostPlanSummary[]>([]), total=ref(0), page=ref(1), keyword=ref(''), showArchived=ref(false);
const skuQuery=ref(''), skuOptions=ref<CostSkuOption[]>([]), skuOpen=ref(false), skuLoading=ref(false), skuError=ref('');
const targetMargin=ref<number|null>(null);
const calculatorOpen=ref(true), linePage=ref(1), viewportHeight=ref(window.innerHeight), viewportWidth=ref(window.innerWidth);
const rowsPerPage=computed(()=>viewportWidth.value<1100 ? 1 : Math.max(1,Math.floor((viewportHeight.value-430)/82)));
const linePageCount=computed(()=>Math.max(1,Math.ceil(lines.value.length/rowsPerPage.value)));
const visibleLines=computed(()=>lines.value.map((line,index)=>({line,index})).slice((linePage.value-1)*rowsPerPage.value,linePage.value*rowsPerPage.value));
watch(linePageCount,count=>{linePage.value=Math.min(linePage.value,count);});
const skuPage=ref(1);
const visibleSkus=computed(()=>skuOptions.value.slice(skuPage.value-1,skuPage.value));
function resizeCalculator(){viewportHeight.value=window.innerHeight;viewportWidth.value=window.innerWidth;}
function closeCalculator(){skuOpen.value=false;calculatorOpen.value=false;}
function openCalculator(){calculatorOpen.value=true;}

let skuTimer:ReturnType<typeof setTimeout>|undefined;
let skuRequest=0,listRequest=0;
const results=computed(()=>lines.value.map(calculateCost));
const profitableCount=computed(()=>results.value.filter(r=>r&&r.netProfit>=0).length);
const lossCount=computed(()=>results.value.filter(r=>r&&r.netProfit<0).length);
const money=(value:number|null|undefined)=>value==null?'—':new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(value);
const percent=(value:number|null|undefined)=>value==null?'—':`${money(value)}%`;
const busy=computed(()=>saving.value||opening.value);
const amountFields=[{key:'unitCost',label:'货品成本'},{key:'shippingCost',label:'快递费'},{key:'packagingCost',label:'包装费'},{key:'otherCost',label:'其他成本'},{key:'salePrice',label:'售价'}] as const;
const rateFields=[{key:'refundRate',label:'售后率 %'},{key:'platformFeeRate',label:'平台费率 %'}] as const;

function allowDiscard() { return !dirty.value || window.confirm('当前方案有未保存的修改，确认放弃这些修改？'); }
function applyPlan(plan:CostPlan) {
  id.value=plan.id;version.value=plan.version;name.value=plan.name;remark.value=plan.remark;archived.value=plan.archived;
  lines.value=plan.lines.map(line=>({...line.input})); savedState.value=serialize();formError.value='';calculatorOpen.value=true;linePage.value=1;
}
function newPlan() {
  if(busy.value||!allowDiscard())return;
  id.value=null;version.value=null;name.value='';remark.value='';archived.value=false;lines.value=[emptyCostLine()];
  savedState.value=serialize();formError.value='';calculatorOpen.value=true;linePage.value=1;
}
async function loadPlans() {
  const request=++listRequest;listLoading.value=true;listError.value='';
  try { const result=await service.list({keyword:keyword.value,archived:showArchived.value,page:page.value,size:10});
    if(request!==listRequest)return;plans.value=result.records;total.value=result.total;
  } catch(error) { if(request===listRequest)listError.value=error instanceof Error?error.message:'方案列表加载失败'; }
  finally { if(request===listRequest)listLoading.value=false; }
}
function searchPlans() {page.value=1;void loadPlans();}
async function openPlan(planId:number) {
  if(busy.value||!allowDiscard())return; calculatorOpen.value=true;opening.value=true;formError.value='';
  try {applyPlan(await service.get(planId));}
  catch(error){formError.value=error instanceof Error?error.message:'方案加载失败';}
  finally{opening.value=false;}
}
function copyPlan() {
  if(busy.value||!canEdit.value)return;
  id.value=null;version.value=null;archived.value=false;name.value=`${name.value||'成本方案'}（副本）`.slice(0,120);
  savedState.value='';formError.value='';
}
async function savePlan() {
  if(busy.value||!editable.value)return;formError.value='';
  if(!name.value.trim()){formError.value='请填写方案名称';return;}
  if(!lines.value.length){formError.value='请至少添加一条规格';return;}
  for(let index=0;index<lines.value.length;index++){
    const error=validateCostLine(lines.value[index]);if(error){linePage.value=Math.floor(index/rowsPerPage.value)+1;formError.value=`第${index+1}行：${error}`;return;}
  }
  saving.value=true;
  try {const plan=await service.save(id.value,{name:name.value,remark:remark.value,version:version.value,lines:lines.value.map(line=>({...line}))});
    applyPlan(plan);message.success('成本方案和利润率已保存');showArchived.value=false;page.value=1;await loadPlans();
  }catch(error){formError.value=error instanceof Error?error.message:'保存失败，请重试';}
  finally{saving.value=false;}
}
async function archivePlan() {
  if(busy.value||!canEdit.value||id.value===null||version.value===null)return;
  if(dirty.value){formError.value='请先保存修改或重新打开方案，再归档';return;}
  if(!window.confirm(archived.value?'恢复此成本方案？':'将此方案移入归档？以后可以在“已归档”中恢复。'))return;
  saving.value=true;
  try {await service.archive(id.value,version.value,!archived.value);applyPlan(await service.get(id.value));message.success(archived.value?'方案已归档':'方案已恢复');await loadPlans();}
  catch(error){formError.value=error instanceof Error?error.message:'操作失败';}
  finally{saving.value=false;}
}
async function searchSkus() {
  const request=++skuRequest;skuLoading.value=true;skuError.value='';skuOpen.value=true;
  try{const options=await service.skuOptions(skuQuery.value);if(request===skuRequest){skuOptions.value=options;skuPage.value=1;}}
  catch(error){if(request===skuRequest)skuError.value=error instanceof Error?error.message:'SKU加载失败';}
  finally{if(request===skuRequest)skuLoading.value=false;}
}
function queueSkuSearch() {clearTimeout(skuTimer);skuRequest++;skuOptions.value=[];skuLoading.value=true;skuOpen.value=true;skuTimer=setTimeout(()=>void searchSkus(),250);}
function addSku(sku:CostSkuOption) {
  if(!editable.value||busy.value||lines.value.length>=200)return;
  const line={...emptyCostLine(),skuId:sku.skuId,skuCode:sku.skuCode,specification:`${sku.productName} · ${sku.specification}`.slice(0,200),unitCost:sku.unitCost,salePrice:sku.salePrice};
  if(lines.value.length===1 && JSON.stringify(lines.value[0])===JSON.stringify(emptyCostLine()))lines.value=[line];
  else lines.value.push(line);
  linePage.value=linePageCount.value;skuOpen.value=false;skuQuery.value='';
}
function addLine(source?:CostLine) {if(editable.value&&!busy.value&&lines.value.length<200){lines.value.push(source?{...source}:emptyCostLine());linePage.value=linePageCount.value;}}
function setNumber(line:CostLine,key:'unitCost'|'shippingCost'|'packagingCost'|'otherCost'|'salePrice'|'refundRate'|'platformFeeRate'|'roi'|'adCost',event:Event) {
  const input=event.target as HTMLInputElement;
  line[key]=input.value===''||!Number.isFinite(input.valueAsNumber)?null:input.valueAsNumber;
}
function applyTarget(index:number) {
  if(typeof targetMargin.value!=='number'){message.error('请先输入目标毛利率');return;}
  const price=priceForMargin(lines.value[index],Number(targetMargin.value));
  if(price===null){message.error('目标毛利率须在0至100%之间，且完整成本须大于零');return;}
  lines.value[index].salePrice=price;
}
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value){event.preventDefault();event.returnValue='';}}
function closeSku(event:MouseEvent){if(!(event.target as HTMLElement)?.closest('[data-testid="cost-sku-picker"]'))skuOpen.value=false;}
onBeforeRouteLeave(()=>!dirty.value||window.confirm('成本方案尚未保存，确认离开？'));
onMounted(()=>{void loadPlans();window.addEventListener('erp:open-pdd-cost',openCalculator);window.addEventListener('resize',resizeCalculator);window.addEventListener('beforeunload',beforeUnload);document.addEventListener('click',closeSku);});
onBeforeUnmount(()=>{window.removeEventListener('erp:open-pdd-cost',openCalculator);window.removeEventListener('resize',resizeCalculator);clearTimeout(skuTimer);skuRequest++;listRequest++;window.removeEventListener('beforeunload',beforeUnload);document.removeEventListener('click',closeSku);});
</script>

<template>
  <section data-testid="pdd-cost-page" class="mx-auto max-w-[1800px] text-[#25314d]">
    <div :inert="calculatorOpen || undefined" class="space-y-5">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div><p class="text-xs font-black tracking-widest text-[#536dff]">软件工具 / 拼多多成本计算</p><h1 class="mt-2 text-2xl font-black">拼多多成本计算</h1><p class="mt-2 text-sm text-slate-500">独立弹框测算成本与利润，全部指标同屏查看。</p></div>
      <button v-if="canEdit" data-testid="cost-new" :disabled="busy" type="button" class="cost-button" @click="newPlan"><Plus class="h-4 w-4" />新建方案</button>
    </header>
    <div class="grid gap-4 lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside class="self-start rounded-2xl border border-slate-200 bg-white p-4">
        <h2 class="font-black">已保存方案</h2>
        <form class="relative mt-3" @submit.prevent="searchPlans"><input v-model="keyword" data-testid="cost-plan-search" aria-label="搜索成本方案" maxlength="100" placeholder="搜索方案名称" class="cost-input w-full pr-8" /><button type="submit" aria-label="搜索" class="absolute right-2 top-2.5 text-slate-400"><Search class="h-4 w-4" /></button></form>
        <label class="my-3 flex items-center gap-2 text-xs text-slate-500"><input v-model="showArchived" type="checkbox" data-testid="cost-archived-filter" @change="searchPlans" />查看已归档</label>
        <p v-if="listLoading" role="status" class="py-4 text-sm text-slate-400">正在加载方案…</p>
        <div v-else-if="listError" role="alert" class="text-sm text-red-600">{{ listError }}<button class="ml-1 underline" @click="loadPlans">重试</button></div>
        <div v-else class="space-y-2"><button v-for="plan in plans" :key="plan.id" :data-testid="`cost-plan-${plan.id}`" type="button" :disabled="busy" class="w-full rounded-xl border p-3 text-left hover:border-blue-300" :class="id===plan.id?'border-blue-300 bg-blue-50':'border-slate-100'" @click="openPlan(plan.id)"><p class="break-words text-sm font-bold">{{ plan.name }}</p><p class="mt-1 text-xs text-slate-400">{{ plan.lineCount }} 个规格 · {{ plan.updatedAt.replace('T',' ').slice(0,16) }}</p></button><p v-if="!plans.length" class="py-8 text-center text-sm text-slate-400">{{ showArchived?'暂无归档方案':'暂无保存的方案' }}</p></div>
        <div v-if="total>10" class="mt-4 flex items-center justify-between text-xs"><button :disabled="page===1||listLoading" @click="page--;loadPlans()">上一页</button><span>{{ page }} / {{ Math.ceil(total/10) }}</span><button :disabled="page*10>=total||listLoading" @click="page++;loadPlans()">下一页</button></div>
      </aside>
      <div class="self-start rounded-2xl border border-slate-200 bg-white p-8">
        <Calculator class="mb-4 h-9 w-9 text-[#536dff]" /><h2 class="text-xl font-black">SKU 利润测算表</h2>
        <p class="mt-3 text-sm text-slate-500">{{ name || '新建成本方案' }} · {{ lines.length }} 个规格</p>
        <p class="mt-2 text-sm leading-7 text-slate-500">成本、售价、推广与到手利润集中展示。关闭弹框后会保留当前填写内容，可以继续编辑。</p>
        <button data-testid="cost-open-calculator" class="cost-button cost-primary mt-6" @click="calculatorOpen=true">打开利润测算表</button>
      </div>
    </div>
        <details class="rounded-2xl border border-slate-200 bg-white p-5 text-xs leading-7 text-slate-500"><summary class="cursor-pointer font-bold text-[#25314d]"><Calculator class="mr-1 inline h-4 w-4" />计算口径与填写说明</summary><p>总成本 = 货品成本 + 快递费 + 包装费 + 其他成本；毛利润 = 售价 − 总成本；毛利率 = 毛利润 ÷ 售价。</p><p>推广前利润 =（毛利润 − 售价 × 平台费率）×（1 − 售后率）；保本投产比 = 售价 ÷ 推广前利润，推广前利润 ≤ 0 时无法保本。</p><p>按投产比：推广费 = 售价 ÷ 投产比；按成交出价：推广费 = 填写的单笔成交出价。预计到手利润 = 推广前利润 − 推广费；预计到手利润率 = 预计到手利润 ÷ 售价。</p><p>目标毛利率用于反推售价，售价向上取整到分；计算过程保留精度，页面金额显示两位小数。售后订单不计商品毛利，推广费仍计入；平台费按未退款成交额计提。未单独模拟退货运费、货损和税费，请按实际情况计入其他成本。</p><p>售后率、费率和推广参数由你填写，初始 0 代表暂未计入。测算不代表平台结算单。保存只记录成本方案，不修改商品标准成本、售价、库存，也不向拼多多发布或改价。</p></details>
    </div>
    <AccessibleDialog :open="calculatorOpen" title="SKU 利润测算表" description="软件工具 / 拼多多成本计算 · 金额单位：元 / 单"
      test-id="cost-calculator-dialog" body-test-id="cost-calculator-body" footer-test-id="cost-calculator-footer" close-test-id="cost-calculator-close"
      overlay-class="!m-0 items-center justify-center p-2 sm:p-4" panel-class="cost-dialog flex max-h-[calc(100dvh-16px)] w-full flex-col sm:max-h-[calc(100dvh-32px)]"
      header-class="cost-dialog-header shrink-0 px-4 py-3" body-class="cost-dialog-body min-h-0 px-4 py-3" footer-class="cost-dialog-footer shrink-0 flex-wrap items-center px-4 py-3"
      @cancel="closeCalculator">
      <main class="min-w-0 space-y-3">
        <div v-if="formError" data-testid="cost-error" role="alert" class="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ formError }}</div>
        <div v-if="!canEdit" class="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">当前账号可查看方案，编辑和保存需要商品编辑权限。</div>
        <section class="cost-plan-fields rounded-xl border border-slate-200 bg-white p-3">
          <div class="mb-2 flex flex-wrap items-center justify-between gap-3"><h2 class="font-black">{{ id?'方案详情':'新方案' }} <span class="ml-2 text-xs font-medium" :class="dirty?'text-amber-600':'text-slate-400'">{{ archived?'已归档':dirty?'有修改，尚未保存':id?'已保存':'待填写' }}</span></h2><div class="flex gap-2">
            <button v-if="canEdit && id" data-testid="cost-copy-plan" :disabled="busy" class="cost-button" @click="copyPlan"><Copy class="h-4 w-4" />复制方案</button>
            <button v-if="canEdit && id" data-testid="cost-archive" :disabled="busy" class="cost-button" @click="archivePlan"><RotateCcw v-if="archived" class="h-4 w-4" /><Archive v-else class="h-4 w-4" />{{ archived?'恢复':'归档' }}</button>
            <button v-if="editable" data-testid="cost-save" :disabled="busy" class="cost-button cost-primary" @click="savePlan"><Save class="h-4 w-4" />{{ saving?'保存中…':'保存方案' }}</button>
          </div></div>
          <fieldset :disabled="!editable||busy" class="grid gap-3 md:grid-cols-2"><label class="space-y-2 text-xs font-bold text-slate-500">方案名称 *<input v-model="name" data-testid="cost-name" maxlength="120" placeholder="例如：玻璃杯 · 日常售价方案" class="cost-input mt-2 w-full" /></label><label class="space-y-2 text-xs font-bold text-slate-500">备注<input v-model="remark" data-testid="cost-remark" maxlength="1000" placeholder="记录适用店铺、包装或活动条件" class="cost-input mt-2 w-full" /></label></fieldset>
        </section>
        <div class="cost-stats grid grid-cols-3 gap-3"><div class="cost-stat"><p>规格数</p><strong>{{ lines.length }}</strong></div><div class="cost-stat"><p>预计不亏损</p><strong class="text-emerald-600">{{ profitableCount }}</strong></div><div class="cost-stat"><p>预计亏损</p><strong class="text-rose-600">{{ lossCount }}</strong></div></div>
        <section class="overflow-visible rounded-2xl border border-slate-200 bg-white">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-2"><div><h2 class="font-black">规格明细</h2><p class="mt-1 text-xs text-slate-400">全部指标同屏 · 规格较多时翻页查看</p></div><button v-if="editable" data-testid="cost-add-line" :disabled="busy||lines.length>=200" class="cost-button" @click="addLine()"><Plus class="h-4 w-4" />手工新增规格</button></div>
          <div v-if="editable" class="flex flex-wrap items-center gap-3 border-b border-slate-100 bg-slate-50/50 p-2">
            <div data-testid="cost-sku-picker" class="relative min-w-[220px] flex-1"><input v-model="skuQuery" data-testid="cost-sku-search" :disabled="busy" maxlength="100" aria-label="搜索已有商品SKU" placeholder="输入商品名称、货号或SKU，带入已有成本" class="cost-input w-full" @focus="searchSkus" @input="queueSkuSearch" @keydown.esc="skuOpen=false" />
              <div v-if="skuOpen" class="absolute bottom-full left-0 z-40 mb-2 w-full rounded-xl border border-slate-200 bg-white p-2 shadow-xl"><p v-if="skuLoading" class="p-3 text-sm text-slate-400">搜索中…</p><p v-else-if="skuError" role="alert" class="p-3 text-sm text-rose-600">{{ skuError }}<button class="ml-2 underline" @click="searchSkus">重试</button></p><template v-else><button v-for="sku in visibleSkus" :key="sku.skuId" :data-testid="`cost-sku-${sku.skuId}`" type="button" class="block w-full rounded-lg p-2 text-left text-xs hover:bg-blue-50" @click="addSku(sku)"><strong class="block truncate" :title="`${sku.productName} · ${sku.specification}`">{{ sku.productName }} · {{ sku.specification }}</strong><p class="mt-1 text-xs text-slate-400">{{ sku.skuCode }} · 标准成本 {{ money(sku.unitCost) }} · 默认售价 {{ money(sku.salePrice) }}</p></button><p v-if="!skuOptions.length" class="p-3 text-sm text-slate-400">未找到匹配 SKU，可手工新增规格</p><p v-else class="p-2 text-xs text-slate-400">可输入关键词缩小范围</p><div v-if="skuOptions.length>1" class="flex items-center justify-between p-2 text-xs"><button type="button" :disabled="skuPage===1" @click="skuPage--">上一页</button><span>{{ skuPage }} / {{ skuOptions.length }}</span><button type="button" :disabled="skuPage>=skuOptions.length" @click="skuPage++">下一页</button></div></template></div>
            </div>
            <label class="flex items-center gap-2 text-xs text-slate-500">目标毛利率<input v-model.number="targetMargin" data-testid="cost-target-margin" type="number" min="0" max="99.99" step="0.01" class="cost-input w-24" placeholder="选填" />%</label>
          </div>
          <div class="rounded-b-2xl"><fieldset :disabled="!editable||busy"><table class="cost-table w-full table-fixed text-left text-xs"><thead><tr><th class="cost-spec-column">规格 / SKU</th><th v-for="field in amountFields" :key="field.key">{{ field.label }}</th><th>总成本</th><th>毛利润</th><th>毛利率</th><th v-for="field in rateFields" :key="field.key">{{ field.label }}</th><th>保本投产比</th><th>推广方式</th><th>投产比 / 成交出价</th><th>单笔推广费</th><th>预计到手利润</th><th>预计到手利润率</th><th>行操作</th></tr></thead>
            <tbody><tr v-for="{line,index} in visibleLines" :key="index" :data-testid="`cost-line-${index}`"><td data-label="规格 / SKU" class="bg-white"><input v-model="line.specification" :data-testid="`cost-spec-${index}`" :aria-label="`第${index+1}行规格`" maxlength="200" class="cost-input w-full" placeholder="填写规格名称" /><p v-if="line.skuId" class="mt-2 text-slate-400">{{ line.skuCode }} <button type="button" :aria-label="`第${index+1}行解除SKU关联`" class="text-blue-600" @click="line.skuId=null;line.skuCode=''">解除关联</button></p></td>
              <td v-for="field in amountFields" :key="field.key" :data-label="field.label"><input :value="line[field.key]" :data-testid="`cost-${field.key}-${index}`" :aria-label="`第${index+1}行${field.label}`" type="number" min="0" max="999999999.99" step="0.01" class="cost-input w-24" @input="setNumber(line,field.key,$event)" /></td>
              <td data-label="总成本" :data-testid="`cost-total-${index}`" class="font-bold">{{ money(results[index]?.totalCost) }}</td><td data-label="毛利润">{{ money(results[index]?.grossProfit) }}</td><td data-label="毛利率">{{ percent(results[index]?.grossMargin) }}</td>
              <td v-for="field in rateFields" :key="field.key" :data-label="field.label"><input :value="line[field.key]" :data-testid="`cost-${field.key}-${index}`" :aria-label="`第${index+1}行${field.label}`" type="number" min="0" max="100" step="0.01" class="cost-input w-24" @input="setNumber(line,field.key,$event)" /></td>
              <td data-label="保本投产比" :data-testid="`cost-break-roi-${index}`">{{ results[index] ? (results[index]!.breakEvenRoi===null?'无法保本':money(results[index]!.breakEvenRoi)):'—' }}</td>
              <td data-label="推广方式"><select v-model="line.adMode" :data-testid="`cost-adMode-${index}`" :aria-label="`第${index+1}行推广方式`" class="cost-input w-32"><option value="none">不计推广</option><option value="roi">按投产比</option><option value="cpa">按成交出价</option></select></td>
              <td data-label="投产比 / 成交出价"><input v-if="line.adMode==='roi'" :value="line.roi" :data-testid="`cost-roi-${index}`" :aria-label="`第${index+1}行投产比`" type="number" min="0.0001" max="1000000" step="0.0001" class="cost-input w-28" @input="setNumber(line,'roi',$event)" /><input v-else-if="line.adMode==='cpa'" :value="line.adCost" :data-testid="`cost-adCost-${index}`" :aria-label="`第${index+1}行成交出价`" type="number" min="0" max="999999999.99" step="0.01" class="cost-input w-28" @input="setNumber(line,'adCost',$event)" /><span v-else class="text-slate-400">—</span></td>
              <td data-label="单笔推广费">{{ money(results[index]?.promotionCost) }}</td><td data-label="预计到手利润" :data-testid="`cost-net-${index}`" class="text-sm font-black" :class="(results[index]?.netProfit??0)<0?'text-rose-600':'text-emerald-600'">{{ money(results[index]?.netProfit) }}</td><td data-label="预计到手利润率" class="font-bold" :class="(results[index]?.netMargin??0)<0?'text-rose-600':'text-emerald-600'">{{ percent(results[index]?.netMargin) }}</td>
              <td data-label="行操作"><div class="flex flex-wrap gap-2"><button type="button" :data-testid="`cost-target-${index}`" class="whitespace-nowrap text-blue-600" @click="applyTarget(index)">按目标定价</button><button type="button" :disabled="lines.length>=200" :data-testid="`cost-copy-line-${index}`" aria-label="复制此规格" @click="addLine(line)"><Copy class="h-4 w-4" /></button><button type="button" :data-testid="`cost-remove-line-${index}`" aria-label="移除此规格" class="text-rose-500" @click="lines.splice(index,1)"><Trash2 class="h-4 w-4" /></button></div></td>
            </tr><tr v-if="!lines.length"><td colspan="18" class="p-8 text-center text-slate-400">暂无规格，请选择已有 SKU 或手工新增</td></tr></tbody>
          </table></fieldset></div>
        </section>

      </main>
      <template #footer>
        <div data-testid="cost-rows-pagination" class="mr-auto flex items-center gap-3 text-xs text-slate-500">
          <span>共 {{ lines.length }} 个规格</span>
          <button data-testid="cost-rows-prev" class="cost-button" :disabled="linePage===1" @click="linePage--">上一页</button>
          <span>{{ linePage }} / {{ linePageCount }}</span>
          <button data-testid="cost-rows-next" class="cost-button" :disabled="linePage>=linePageCount" @click="linePage++">下一页</button>
        </div>
        <button class="cost-button" @click="closeCalculator">收起测算表</button>
      </template>
    </AccessibleDialog>
  </section>
</template>

<style scoped>
.cost-input{height:2.5rem;border:1px solid #e2e8f0;border-radius:.6rem;background:white;padding:0 .65rem;font-size:.8rem;color:#25314d;outline:none;font-weight:500}.cost-input:focus{border-color:#536dff}.cost-input:disabled{background:#f8fafc;color:#64748b}.cost-button{display:inline-flex;align-items:center;justify-content:center;gap:.4rem;border:1px solid #e2e8f0;border-radius:.7rem;padding:.6rem .85rem;font-size:.8rem;font-weight:700;background:white}.cost-button:disabled,button:disabled{opacity:.5;cursor:not-allowed}.cost-primary{background:#536dff;color:white;border-color:#536dff}.cost-stat{border:1px solid #e2e8f0;border-radius:1rem;background:white;padding:1rem}.cost-stat p{font-size:.75rem;color:#64748b}.cost-stat strong{display:block;margin-top:.5rem;font-size:1.5rem}.cost-table th{background:#f8fafc;color:#64748b;font-weight:700;overflow-wrap:anywhere;white-space:normal}.cost-table th,.cost-table td{padding:.45rem .25rem;border-bottom:1px solid #f1f5f9}.cost-table td{vertical-align:top;overflow-wrap:anywhere;white-space:normal}.cost-table td:first-child{white-space:normal}.cost-table .cost-input{height:2rem;width:100%;min-width:0;padding:0 .2rem;font-size:11px}.cost-spec-column{width:12%}.cost-table th:nth-child(13){width:8%}fieldset:disabled button{pointer-events:none}

:deep(.cost-dialog-body){overflow:visible}
.cost-stat{border-radius:.65rem;padding:.45rem .75rem}
.cost-stat p,.cost-stat strong{display:inline;margin:0;font-size:.75rem}
.cost-stat strong{margin-left:.75rem;font-size:1rem}
.cost-table{font-size:11px}
.cost-table input[type=number]{appearance:textfield;-moz-appearance:textfield}
.cost-table input::-webkit-inner-spin-button,.cost-table input::-webkit-outer-spin-button{-webkit-appearance:none;margin:0}
.cost-table td[data-label="预计到手利润"]{font-size:13px}
.cost-table td[data-label="行操作"] button{font-size:10px}
.cost-table td[data-label="行操作"] svg{width:13px;height:13px}
@media (max-width:1099px){
  .cost-table,.cost-table tbody{display:block}
  .cost-table thead{display:none}
  .cost-table tr{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.35rem;padding:.4rem}
  .cost-table td{display:flex;min-width:0;flex-direction:column;justify-content:center;border:0;border-radius:.4rem;background:#f8fafc;padding:.35rem}
  .cost-table td::before{content:attr(data-label);font-size:10px;color:#64748b;line-height:1.4;margin-bottom:.2rem}
  .cost-table td:first-child{grid-column:span 2}
  .cost-table td:last-child{grid-column:span 2}
  .cost-table td[colspan]{grid-column:1/-1}
  .cost-table td[data-label="行操作"] div{align-items:center}
  .cost-plan-fields .cost-button{padding:.3rem .4rem;font-size:11px}
  .cost-dialog-body .cost-input{height:1.9rem}
}
@media (max-height:740px){
  :deep(.cost-dialog-header){padding-top:.4rem;padding-bottom:.4rem}
  :deep(.cost-dialog-header p){display:none}
  :deep(.cost-dialog-body){padding-top:.4rem;padding-bottom:.4rem}
  .cost-dialog-body main{gap:.4rem}
  .cost-dialog-body main>*+*{margin-top:.4rem}
  .cost-plan-fields{padding:.5rem}
  .cost-plan-fields fieldset{grid-template-columns:repeat(2,minmax(0,1fr))}
  .cost-plan-fields .cost-input{height:1.7rem;margin-top:.2rem}
  .cost-stats{display:none}
  :deep(.cost-dialog-footer){padding-top:.4rem;padding-bottom:.4rem}
  .cost-dialog-footer .cost-button{padding:.3rem .4rem}
  .cost-table td{padding:.2rem}
  .cost-table .cost-input{height:1.65rem}
}
@media (max-width:639px){
  .cost-stats{display:none}
  :deep(.cost-dialog-header p){display:none}
  :deep(.cost-dialog-header){padding-top:.4rem;padding-bottom:.4rem}
  .cost-plan-fields .mb-2{gap:.25rem}
  .cost-plan-fields h2{font-size:13px}
  .cost-table .cost-input{font-size:11px}
  .cost-table select.cost-input{padding:0;font-size:10px}

  :deep(.cost-dialog-body){padding-left:.5rem;padding-right:.5rem}
  .cost-plan-fields fieldset{grid-template-columns:repeat(2,minmax(0,1fr))}
  .cost-dialog-body [data-testid="cost-sku-picker"]{min-width:0}
  .cost-table tr{gap:.2rem;padding:.2rem}
  .cost-table td::before{font-size:9px}
  :deep(.cost-dialog-footer){gap:.4rem}
  .cost-dialog-footer>div{gap:.5rem}
}
</style>
