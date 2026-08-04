<script setup lang="ts">
import { FileText, Plus, Search } from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { message } from '../../components/feedback/message';
import { salesOrderService } from './salesOrderService';
import { listMockSalesOrders } from './mockSalesOrderStore';
import type { SalesOrderListItem, SalesOrderService, SalesOrderStatus } from './types';

const props = defineProps<{ service?: SalesOrderService }>();
const route = useRoute();
const service = props.service ?? salesOrderService;
const orders = ref<SalesOrderListItem[]>(listMockSalesOrders());
const searchQuery = ref('');
const statusFilter = ref<'all' | SalesOrderStatus>('all');
const loading = ref(false);
const statusTabs: Array<{ value: 'all' | SalesOrderStatus; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'draft', label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'void', label: '已作废' }
];
const successMessage = computed(() => {
  const deletedOrderNo = route.query.deleted;
  if (typeof deletedOrderNo === 'string' && deletedOrderNo) return `销售单 ${deletedOrderNo} 已删除`;
  const orderNo = route.query.created;
  if (typeof orderNo !== 'string' || !orderNo) return '';
  return route.query.type === 'draft' ? `销售单 ${orderNo} 草稿已保存` : `销售单 ${orderNo} 已创建成功`;
});

async function loadOrders() {
  if (!service.listOrders) return;
  loading.value = true;
  try {
    const result = await service.listOrders({ page: 1, size: 100 });
    orders.value = result.records;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '销售单据加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadOrders();
  if (successMessage.value) message.success(successMessage.value);
});

const filteredOrders = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return orders.value.filter((order) => {
    const matchesKeyword = !keyword || [order.orderNo, order.customerName, order.warehouseName].some((value) => value.toLowerCase().includes(keyword));
    const matchesStatus = statusFilter.value === 'all' || order.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`;
}

function statusLabel(status: SalesOrderStatus) {
  return { draft: '草稿', confirmed: '已确认', void: '已作废' }[status];
}
</script>

<template>
  <section data-testid="sales-orders-page" class="mx-auto max-w-[1440px] space-y-5">
    <header data-testid="sales-orders-header" class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-[11px] font-black tracking-[0.22em] text-[#536dff]">SALES / ORDERS</p>
        <h1 class="mt-2 text-2xl font-black text-[#25314d]">销售单据</h1>
        <p class="mt-1 text-sm font-medium text-slate-400">集中查看线下销售订单，新增单据后进入销售开单流程。</p>
      </div>
      <RouterLink data-testid="add-sales-order" :to="{ name: 'sales-create' }" class="inline-flex h-11 items-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-lg shadow-blue-200 transition hover:bg-[#465eea]"><Plus class="h-4 w-4" aria-hidden="true" />新增销售单</RouterLink>
    </header>

    <section class="overflow-hidden rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
      <div data-testid="sales-orders-filter" class="flex flex-col gap-3 border-b border-slate-100 bg-white p-5 sm:flex-row sm:items-center">
        <label class="relative min-w-0 flex-1"><Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" /><input data-testid="sales-orders-search" v-model="searchQuery" class="h-11 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff]" placeholder="搜索单号或客户" /></label>
        <div data-testid="sales-status-tabs" role="tablist" aria-label="销售单据状态" class="flex shrink-0 items-center gap-1 rounded-xl bg-slate-50 p-1">
          <button v-for="tab in statusTabs" :key="tab.value" :data-testid="`sales-status-tab-${tab.value}`" type="button" role="tab" :aria-selected="statusFilter === tab.value" class="h-9 rounded-lg px-3 text-sm font-black transition" :class="statusFilter === tab.value ? 'bg-white text-[#536dff] shadow-sm' : 'text-slate-400 hover:text-[#25314d]'" @click="statusFilter = tab.value">{{ tab.label }}</button>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table v-if="filteredOrders.length" data-testid="sales-orders-table" class="w-full min-w-[900px] border-collapse text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black text-slate-400"><tr><th class="whitespace-nowrap px-5 py-4">销售单号</th><th class="whitespace-nowrap px-5 py-4">客户</th><th class="whitespace-nowrap px-5 py-4">仓库</th><th class="whitespace-nowrap px-5 py-4">开单日期</th><th class="whitespace-nowrap px-5 py-4 text-right">订单金额</th><th class="whitespace-nowrap px-5 py-4 text-right">已收款</th><th class="whitespace-nowrap px-5 py-4">状态</th><th class="whitespace-nowrap px-5 py-4 text-right">操作</th></tr></thead>
          <tbody class="divide-y divide-slate-100"><tr v-for="order in filteredOrders" :key="order.id" class="min-h-[76px] bg-white"><td class="whitespace-nowrap px-5 py-5 font-black text-[#25314d]">{{ order.orderNo }}</td><td class="whitespace-nowrap px-5 py-5 font-bold text-slate-700">{{ order.customerName }}</td><td class="whitespace-nowrap px-5 py-5 font-medium text-slate-500">{{ order.warehouseName }}</td><td class="whitespace-nowrap px-5 py-5 font-medium text-slate-500">{{ order.orderDate }}</td><td class="whitespace-nowrap px-5 py-5 text-right font-black text-[#25314d]">{{ formatMoney(order.totalAmount) }}</td><td class="whitespace-nowrap px-5 py-5 text-right font-bold text-slate-600">{{ formatMoney(order.receivedAmount) }}</td><td class="whitespace-nowrap px-5 py-5"><span class="inline-flex rounded-full px-3 py-1 text-xs font-black" :class="order.status === 'confirmed' ? 'bg-emerald-50 text-emerald-600' : order.status === 'draft' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500'">{{ statusLabel(order.status) }}</span></td><td class="whitespace-nowrap px-5 py-5 text-right"><RouterLink :to="{ name: 'sales-order-detail', params: { id: order.id } }" class="text-sm font-bold text-[#536dff] hover:text-[#465eea]">查看</RouterLink></td></tr></tbody>
        </table>
        <div v-else data-testid="sales-orders-empty" class="px-6 py-16 text-center"><FileText class="mx-auto h-8 w-8 text-slate-300" aria-hidden="true" /><p class="mt-3 text-sm font-black text-slate-500">暂无销售单据</p><p class="mt-1 text-xs font-medium text-slate-400">调整筛选条件或新增一张销售单。</p></div>
      </div>

      <footer class="flex items-center justify-between border-t border-slate-100 px-5 py-4 text-sm font-bold text-slate-400"><span data-testid="sales-orders-footer-count">共 {{ filteredOrders.length }} 条</span><span>1 / 1</span></footer>
    </section>
  </section>
</template>
