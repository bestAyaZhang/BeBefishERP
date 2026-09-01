<script setup lang="ts">
import { computed } from 'vue';
import { ClipboardList, ReceiptText } from 'lucide-vue-next';
import type { DashboardOrderStatus, DashboardRecentOrder } from '../types';

const props = withDefaults(defineProps<{
  orders?: DashboardRecentOrder[] | null;
}>(), {
  orders: () => []
});

const safeOrders = computed(() => Array.isArray(props.orders) ? props.orders : []);

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const statusMeta: Record<DashboardOrderStatus, { label: string; className: string }> = {
  draft: { label: '草稿', className: 'bg-slate-100 text-slate-700' },
  confirmed: { label: '已确认', className: 'bg-emerald-50 text-emerald-700' },
  void: { label: '已作废', className: 'bg-rose-50 text-rose-700' }
};

function formatMoney(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? `¥${moneyFormatter.format(value)}` : '--';
}

function safeText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : '--';
}

function formatDate(value: unknown) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? value.replaceAll('-', '/')
    : '--';
}

function orderStatus(value: DashboardOrderStatus) {
  return statusMeta[value] ?? { label: '未知', className: 'bg-slate-100 text-slate-600' };
}
</script>

<template>
  <section class="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white">
    <header class="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
      <div class="flex items-center gap-2">
        <ReceiptText class="h-4 w-4 text-emerald-600" aria-hidden="true" />
        <h2 class="text-sm font-semibold text-slate-800">最近订单</h2>
      </div>
      <span class="text-xs text-slate-500">{{ safeOrders.length }} 笔</span>
    </header>

    <div v-if="safeOrders.length === 0" class="flex min-h-[180px] flex-col items-center justify-center gap-2 px-4 text-sm text-slate-400">
      <ClipboardList class="h-6 w-6" aria-hidden="true" />
      <span>暂无最近订单</span>
    </div>
    <div v-else class="overflow-x-auto" data-testid="recent-order-list">
      <table class="w-full min-w-[680px] table-fixed text-left text-xs">
        <thead class="bg-slate-50 text-slate-500">
          <tr>
            <th class="w-[26%] px-4 py-2 font-medium" scope="col">订单号</th>
            <th class="w-[24%] px-3 py-2 font-medium" scope="col">客户</th>
            <th class="w-[18%] px-3 py-2 font-medium" scope="col">业务日期</th>
            <th class="w-[14%] px-3 py-2 font-medium" scope="col">状态</th>
            <th class="px-4 py-2 text-right font-medium" scope="col">金额</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="(order, index) in safeOrders" :key="order.orderNo || index" class="text-slate-700">
            <td class="truncate px-4 py-2.5 font-mono font-medium text-slate-900" :title="safeText(order.orderNo)">{{ safeText(order.orderNo) }}</td>
            <td class="truncate px-3 py-2.5" :title="safeText(order.customer)">{{ safeText(order.customer) }}</td>
            <td class="px-3 py-2.5 tabular-nums text-slate-600">{{ formatDate(order.businessDate) }}</td>
            <td class="px-3 py-2.5">
              <span class="inline-flex whitespace-nowrap rounded-md px-2 py-1 font-medium" :class="orderStatus(order.status).className">
                {{ orderStatus(order.status).label }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-right font-semibold tabular-nums text-slate-900">{{ formatMoney(order.amount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
