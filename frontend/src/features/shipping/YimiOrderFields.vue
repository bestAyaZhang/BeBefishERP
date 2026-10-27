<script setup lang="ts">
import type { YimiOrderOptions } from './types';
const props=defineProps<{modelValue?:YimiOrderOptions|null;goodsType:number|null;productCode?:string;disabled?:boolean}>();
const emit=defineEmits<{'update:modelValue':[value:YimiOrderOptions]}>();
function update(field:keyof YimiOrderOptions,event:Event){
  const raw=(event.target as HTMLSelectElement).value;
  const value={visitDelivery:null,deliveryFlag:null,floorNo:'',...props.modelValue};
  if(field==='floorNo')value.floorNo=raw;
  else value[field]=raw===''?null:Number(raw);
  if(field==='deliveryFlag' && value.deliveryFlag!==3)value.floorNo='';
  emit('update:modelValue',value);
}
</script>
<template>
  <fieldset :disabled="disabled" class="mt-5 grid gap-4 rounded-lg bg-slate-50 p-4 md:grid-cols-3">
    <legend class="text-sm font-semibold text-slate-700">壹米电子面单选项</legend>
    <p data-testid="yimi-service-product" class="self-center text-sm text-slate-600">运输产品：{{ productCode==='1'?'港到门':productCode==='2'?'港到港':'待选择报价模板' }}</p>
    <label class="text-sm text-slate-600">揽货方式 *<select data-testid="yimi-visit-delivery" :value="modelValue?.visitDelivery??''" class="control" @change="update('visitDelivery',$event)"><option value="">请选择揽货方式</option><option value="0">上门揽货</option><option value="1">自送</option></select></label>
    <label v-if="goodsType===179" class="text-sm text-slate-600">上楼条件 *<select data-testid="yimi-delivery-flag" :value="modelValue?.deliveryFlag??''" class="control" @change="update('deliveryFlag',$event)"><option value="">请选择电梯条件</option><option value="2">有电梯</option><option value="3">无电梯</option></select></label>
    <label v-if="goodsType===179 && modelValue?.deliveryFlag===3" class="text-sm text-slate-600">楼层（可选）<select data-testid="yimi-floor-no" :value="modelValue.floorNo" class="control" @change="update('floorNo',$event)"><option value="">不指定</option><option v-for="floor in ['-2','-1','2','3','4','5','6','7']" :key="floor" :value="floor">{{ floor }}层</option></select></label>
    <p class="text-xs leading-6 text-slate-500 md:col-span-3">下单前须明确选择揽货方式；普通送货对应港到门，自提对应港到港。送货上楼须确认电梯条件，额外费用以承运商账单为准。</p>
  </fieldset>
</template>
<style scoped>.control{@apply mt-2 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none focus:border-indigo-400;}</style>
