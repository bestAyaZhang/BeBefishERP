<script setup lang="ts">
import { computed,ref } from 'vue';
import { currentUser } from '../../services/authSession';
import { message } from '../../components/feedback/message';
import { logisticsService } from '../logistics/logisticsService';
import type { AccountView,Company } from '../logistics/types';
const props=defineProps<{shipmentId:number;version:number;testEnvironment:boolean}>();
const emit=defineEmits<{saved:[]}>();
const open=ref(false),loading=ref(false),saving=ref(false),error=ref('');
const retrySave=ref(false);
const companies=ref<Company[]>([]),accounts=ref<AccountView[]>([]),companyId=ref(''),accountId=ref(''),confirmed=ref(false);
const allowed=computed(()=>!!currentUser.value?.roles.includes('SUPER_ADMIN')&&currentUser.value.permissions.includes('logistics:configure')&&currentUser.value.permissions.includes('logistics:view'));
let generation=0;
async function show(){
  open.value=true;loading.value=true;error.value='';retrySave.value=false;
  try{
    const all:Company[]=[];let page=1;
    while(true){const batch=await logisticsService.listCompanies({page:page++,size:100});all.push(...batch.records);if(all.length>=batch.total||!batch.records.length)break;}
    companies.value=all.filter(item=>item.provider==='ANE');
  }catch(cause){error.value=cause instanceof Error?cause.message:'合作物流加载失败';}
  finally{loading.value=false;}
}
async function loadAccounts(){
  const token=++generation;accounts.value=[];accountId.value='';confirmed.value=false;error.value='';retrySave.value=false;
  if(!companyId.value)return;
  loading.value=true;
  try{
    const all:AccountView[]=[];let page=1;
    while(true){const batch=await logisticsService.listAccounts(Number(companyId.value),{page:page++,size:100});if(token!==generation)return;all.push(...batch.records);if(all.length>=batch.total||!batch.records.length)break;}
    accounts.value=all.filter(item=>item.environment===(props.testEnvironment?'test':'production'));
  }catch(cause){if(token===generation)error.value=cause instanceof Error?cause.message:'账号加载失败';}
  finally{if(token===generation)loading.value=false;}
}
async function save(){
  if(!allowed.value||!confirmed.value||!accountId.value||saving.value||loading.value)return;
  saving.value=true;error.value='';
  try{await logisticsService.confirmLogisticsAccount(props.shipmentId,{accountId:Number(accountId.value),version:props.version,confirmed:true});message.success('原物流账号已确认');open.value=false;emit('saved');}
  catch(cause){retrySave.value=true;error.value=cause instanceof Error?cause.message:'账号确认失败';message.warning(error.value);}
  finally{saving.value=false;}
}
function retry(){return retrySave.value?save():companyId.value?loadAccounts():show();}
</script>
<template>
  <article class="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
    <p>此历史单需超级管理员核实原物流账号后才能取消，避免向其他账号发送取消请求。</p>
    <button v-if="allowed&&!open" data-testid="logistics-account-confirm-open" type="button" class="mt-3 font-semibold text-indigo-600" @click="show">确认原物流账号</button>
    <div v-if="allowed&&open" class="mt-4 space-y-3">
      <label class="block">合作物流<select v-model="companyId" data-testid="historical-company" :disabled="loading||saving" class="ml-3 rounded-lg border border-amber-200 bg-white px-3 py-2" @change="loadAccounts"><option value="">请选择原合作公司</option><option v-for="item in companies" :key="item.id" :value="String(item.id)">{{item.name}}{{item.status==='disabled'?'（已停用）':''}}</option></select></label>
      <label class="block">原下单账号<select v-model="accountId" data-testid="historical-account" :disabled="loading||saving" class="ml-3 rounded-lg border border-amber-200 bg-white px-3 py-2"><option value="">请选择已核实的原账号</option><option v-for="item in accounts" :key="item.id" :value="String(item.id)">账号 #{{item.id}} · {{item.customerCode}} · {{item.environment==='test'?'测试':'正式'}}</option></select></label>
      <p v-if="companyId&&!loading&&!accounts.length" class="text-xs">没有与原订单环境一致的账号，请先到物流管理中配置原账号。</p>
      <label class="flex items-center gap-2"><input v-model="confirmed" data-testid="historical-account-confirmed" type="checkbox" :disabled="loading||saving"/>我已核实此账号为原订单下单账号</label>
      <button data-testid="historical-account-save" type="button" :disabled="loading||saving||!accountId||!confirmed" class="rounded-lg bg-indigo-600 px-4 py-2 text-white disabled:opacity-40" @click="save">{{saving?'正在保存…':'保存账号关联'}}</button>
      <p v-if="error" role="alert" class="text-rose-700">{{error}} <button type="button" class="underline" :disabled="loading||saving" @click="retry">重试</button></p>
    </div>
  </article>
</template>
