<script setup lang="ts">
import { ArrowLeft, FileText, Trash2 } from 'lucide-vue-next';
import { onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { message } from '../../components/feedback/message';
import { salesOrderService } from './salesOrderService';
import type { SalesOrderDetail, SalesOrderService, SalesOrderStatus } from './types';

const props = defineProps<{ orderId: number; service?: SalesOrderService }>();
const router = useRouter();
const service = props.service ?? salesOrderService;
const order = ref<SalesOrderDetail | null>(null);
const loading = ref(true);
const deleting = ref(false);
const voiding = ref(false);
const voidDialogOpen = ref(false);
const voidReason = ref('');
const errorMessage = ref('');

const statusLabels: Record<SalesOrderStatus, string> = {
  draft: '草稿',
  confirmed: '已确认',
  void: '已作废'
};

const transportLabels: Record<string, string> = {
  pickup: '自提',
  delivery: '送货上门',
  consignment: '托运',
  express: '快递'
};

const settlementLabels: Record<string, string> = {
  monthly: '月结',
  daily: '日结',
  quarterly: '季度',
  annual: '年结'
};

function formatMoney(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function statusClass(status: SalesOrderStatus) {
  return status === 'confirmed' ? 'bg-emerald-50 text-emerald-600' : status === 'draft' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500';
}

async function load() {
  if (!service.getOrder) {
    errorMessage.value = '销售单详情接口未接入';
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    order.value = await service.getOrder(props.orderId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '销售单详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function deleteDraft() {
  if (!order.value?.id || !service.deleteOrder) return;
  deleting.value = true;
  try {
    await service.deleteOrder(order.value.id);
    message.success('销售单已删除');
    await router.push({ name: 'sales-orders', query: { deleted: order.value.orderNo } });
  } catch (error) {
    message.error(error instanceof Error ? error.message : '销售单删除失败');
  } finally {
    deleting.value = false;
  }
}

function openVoidDialog() {
  voidReason.value = '';
  voidDialogOpen.value = true;
}

function closeVoidDialog() {
  if (!voiding.value) voidDialogOpen.value = false;
}

async function voidConfirmedOrder() {
  if (!order.value?.id || !service.voidOrder) return;
  const reason = voidReason.value.trim();
  if (!reason) {
    message.error('请填写作废原因');
    return;
  }
  voiding.value = true;
  try {
    order.value = await service.voidOrder(order.value.id, reason);
    voidDialogOpen.value = false;
    message.success('销售单已作废');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '销售单作废失败');
  } finally {
    voiding.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section data-testid="sales-order-detail-page" class="mx-auto max-w-[1200px] space-y-5">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <RouterLink :to="{ name: 'sales-orders' }" class="inline-flex items-center gap-2 text-sm font-bold text-slate-400 transition hover:text-[#536dff]"><ArrowLeft class="h-4 w-4" aria-hidden="true" />返回销售单据</RouterLink>
        <p class="mt-5 text-[11px] font-black tracking-[0.22em] text-[#536dff]">SALES / ORDER DETAIL</p>
        <h1 class="mt-2 text-2xl font-black text-[#25314d]">销售单详情</h1>
        <p class="mt-1 text-sm font-medium text-slate-400">查看客户、商品明细、金额和交付信息。</p>
      </div>
      <div v-if="order" class="flex items-center gap-3">
        <span class="rounded-full px-3 py-1 text-xs font-black" :class="statusClass(order.status)">{{ statusLabels[order.status] }}</span>
        <button v-if="order.status === 'draft' && service.deleteOrder" data-testid="sales-order-detail-delete" type="button" :disabled="deleting" class="inline-flex h-10 items-center gap-2 rounded-xl border border-rose-200 px-4 text-sm font-black text-rose-500 transition hover:bg-rose-50 disabled:cursor-wait disabled:opacity-60" @click="deleteDraft"><Trash2 class="h-4 w-4" aria-hidden="true" />{{ deleting ? '删除中...' : '删除草稿' }}</button>
        <button v-if="order.status === 'confirmed' && service.voidOrder" data-testid="sales-order-detail-void" type="button" class="inline-flex h-10 items-center gap-2 rounded-xl border border-rose-200 px-4 text-sm font-black text-rose-500 transition hover:bg-rose-50" @click="openVoidDialog"><Trash2 class="h-4 w-4" aria-hidden="true" />作废单据</button>
      </div>
    </header>

    <div v-if="loading" data-testid="sales-order-detail-loading" class="rounded-[22px] border border-slate-200 bg-white px-6 py-16 text-center text-sm font-bold text-slate-400">销售单详情加载中...</div>
    <div v-else-if="errorMessage" data-testid="sales-order-detail-error" class="rounded-[22px] border border-rose-200 bg-rose-50 px-6 py-10 text-center text-sm font-bold text-rose-600">{{ errorMessage }}</div>
    <template v-else-if="order">
      <section class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-5"><div class="flex items-center gap-3"><span class="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-[#536dff]"><FileText class="h-5 w-5" aria-hidden="true" /></span><div><p class="text-xs font-black text-slate-400">销售单号</p><h2 class="mt-1 text-lg font-black text-[#25314d]">{{ order.orderNo }}</h2></div></div><p class="text-sm font-bold text-slate-400">开单日期：<span class="text-[#25314d]">{{ order.orderDate }}</span></p></div>
        <div class="mt-5 grid gap-4 md:grid-cols-2">
          <div class="rounded-xl bg-slate-50 p-4"><h3 class="text-sm font-black text-[#25314d]">客户信息</h3><dl class="mt-3 grid gap-2 text-sm"><div class="flex justify-between gap-4"><dt class="text-slate-400">客户名称</dt><dd class="font-bold text-[#25314d]">{{ order.customerName }}</dd></div><div class="flex justify-between gap-4"><dt class="text-slate-400">送货地址</dt><dd class="text-right font-bold text-[#25314d]">{{ order.deliveryAddress || '—' }}</dd></div></dl></div>
          <div class="rounded-xl bg-slate-50 p-4"><h3 class="text-sm font-black text-[#25314d]">发货信息</h3><dl class="mt-3 grid gap-2 text-sm"><div class="flex justify-between gap-4"><dt class="text-slate-400">发货仓库</dt><dd class="font-bold text-[#25314d]">{{ order.warehouseName }}</dd></div><div class="flex justify-between gap-4"><dt class="text-slate-400">业务代表</dt><dd class="font-bold text-[#25314d]">{{ order.salespersonMobile }}</dd></div></dl></div>
        </div>
        <div class="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-4"><div><p class="text-xs font-bold text-slate-400">运输方式</p><p class="mt-1 text-sm font-black text-[#25314d]">{{ transportLabels[order.transportMethod] ?? order.transportMethod }}</p></div><div><p class="text-xs font-bold text-slate-400">结算周期</p><p class="mt-1 text-sm font-black text-[#25314d]">{{ settlementLabels[order.settlementCycle] ?? order.settlementCycle }}</p></div><div><p class="text-xs font-bold text-slate-400">收款金额</p><p class="mt-1 text-sm font-black text-[#25314d]">{{ formatMoney(order.receivedAmount) }}</p></div><div><p class="text-xs font-bold text-slate-400">欠款应收</p><p class="mt-1 text-sm font-black text-rose-500">{{ formatMoney(order.outstandingAmount) }}</p></div></div>
      </section>

      <section class="overflow-hidden rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><div class="flex items-center justify-between border-b border-slate-100 px-6 py-5"><div><h2 class="text-lg font-black text-[#25314d]">商品明细</h2><p class="mt-1 text-sm font-medium text-slate-400">共 {{ order.items.length }} 项商品</p></div><span class="text-sm font-black text-[#25314d]">{{ formatMoney(order.totalAmount) }}</span></div><div class="overflow-x-auto"><table data-testid="sales-order-detail-items" class="w-full min-w-[760px] border-collapse text-left text-sm"><thead class="bg-slate-50 text-xs font-black text-slate-400"><tr><th class="px-6 py-4">商品信息</th><th class="px-6 py-4">规格</th><th class="px-6 py-4">包装</th><th class="px-6 py-4">数量</th><th class="px-6 py-4 text-right">单价</th><th class="px-6 py-4 text-right">金额</th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-for="item in order.items" :key="item.id ?? item.skuId"><td class="px-6 py-4"><p class="font-black text-[#25314d]">{{ item.productNameSnapshot }} · {{ item.skuNameSnapshot || '默认规格' }}</p><p class="mt-1 text-xs font-bold text-slate-400">{{ item.itemNoSnapshot || '—' }} · {{ item.skuCodeSnapshot }}</p></td><td class="px-6 py-4 font-bold text-slate-600">{{ item.specificationSnapshot || '默认规格' }}</td><td class="px-6 py-4 font-bold text-slate-600">{{ item.packagingSnapshot || '—' }}<span v-if="item.cartonQuantitySnapshot"> · {{ item.cartonQuantitySnapshot }} 件/箱</span></td><td class="px-6 py-4 font-bold text-slate-600">{{ item.quantity }} {{ item.salesUnitSnapshot }}</td><td class="whitespace-nowrap px-6 py-4 text-right font-bold text-slate-600">{{ formatMoney(item.unitPrice) }}</td><td class="whitespace-nowrap px-6 py-4 text-right font-black text-[#25314d]">{{ formatMoney(item.amount) }}</td></tr><tr v-if="!order.items.length"><td colspan="6" class="px-6 py-14 text-center font-bold text-slate-400">暂无商品明细</td></tr></tbody></table></div></section>

      <section class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]"><div class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><h2 class="text-lg font-black text-[#25314d]">交付说明</h2><dl class="mt-5 grid gap-4 text-sm sm:grid-cols-2"><div><dt class="font-bold text-slate-400">包装备注</dt><dd class="mt-1 font-bold text-[#25314d]">{{ order.packageNote || '—' }}</dd></div><div><dt class="font-bold text-slate-400">物流信息</dt><dd class="mt-1 font-bold text-[#25314d]">{{ order.logisticsCompany || '—' }}{{ order.trackingNo ? ` · ${order.trackingNo}` : '' }}</dd></div><div class="sm:col-span-2"><dt class="font-bold text-slate-400">备注</dt><dd class="mt-1 whitespace-pre-wrap font-bold text-[#25314d]">{{ order.remark || '—' }}</dd></div></dl></div><div class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]"><h2 class="text-lg font-black text-[#25314d]">金额汇总</h2><dl class="mt-5 grid gap-3 text-sm"><div class="flex justify-between gap-4"><dt class="font-bold text-slate-400">商品金额</dt><dd class="font-black text-[#25314d]">{{ formatMoney(order.goodsAmount) }}</dd></div><div class="flex justify-between gap-4"><dt class="font-bold text-slate-400">折扣金额</dt><dd class="font-bold text-slate-600">{{ formatMoney(order.discountAmount) }}</dd></div><div class="flex justify-between gap-4"><dt class="font-bold text-slate-400">运费</dt><dd class="font-bold text-slate-600">{{ formatMoney(order.freight) }}</dd></div><div class="flex justify-between gap-4 border-t border-slate-100 pt-3"><dt class="font-black text-[#25314d]">应收合计</dt><dd data-testid="sales-order-detail-total" class="text-xl font-black text-[#25314d]">{{ formatMoney(order.totalAmount) }}</dd></div></dl></div></section>
    </template>

    <Teleport to="body">
      <div v-if="voidDialogOpen" data-testid="sales-order-detail-void-dialog" class="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/30 px-4" @click.self="closeVoidDialog">
        <section class="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true" aria-labelledby="sales-order-detail-void-title">
          <div class="flex items-start justify-between gap-4">
            <div><h2 id="sales-order-detail-void-title" class="text-lg font-black text-[#25314d]">作废销售单</h2><p class="mt-1 text-sm font-medium text-slate-400">作废后会恢复本单已扣减的库存，请填写原因。</p></div>
            <button type="button" class="text-sm font-bold text-slate-400 hover:text-slate-700" @click="closeVoidDialog">关闭</button>
          </div>
          <label class="mt-5 block text-sm font-bold text-[#25314d]">作废原因<textarea data-testid="sales-order-detail-void-reason" v-model="voidReason" rows="4" class="mt-2 w-full resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-[#25314d] outline-none focus:border-[#536dff]" placeholder="请输入作废原因" /></label>
          <div class="mt-5 flex justify-end gap-3"><button type="button" class="h-10 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" @click="closeVoidDialog">取消</button><button data-testid="sales-order-detail-void-submit" type="button" :disabled="voiding" class="h-10 rounded-xl bg-rose-500 px-4 text-sm font-black text-white hover:bg-rose-600 disabled:cursor-wait disabled:opacity-60" @click="voidConfirmedOrder">{{ voiding ? '作废中...' : '确认作废' }}</button></div>
        </section>
      </div>
    </Teleport>
  </section>
</template>
