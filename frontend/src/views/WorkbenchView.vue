<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { CircleAlert, RefreshCw } from 'lucide-vue-next';
import DashboardSummaryGrid from '../features/dashboard/components/DashboardSummaryGrid.vue';
import RecentOrderList from '../features/dashboard/components/RecentOrderList.vue';
import SalesTrendPanel from '../features/dashboard/components/SalesTrendPanel.vue';
import StockAlertList from '../features/dashboard/components/StockAlertList.vue';
import { dashboardService } from '../features/dashboard/dashboardService';
import type { DashboardOverview, DashboardPeriod } from '../features/dashboard/types';

const periodOptions: Array<{ value: DashboardPeriod; label: string }> = [
  { value: 'week', label: '周' },
  { value: 'month', label: '月' },
  { value: 'year', label: '年' }
];

const period = ref<DashboardPeriod>('week');
const loading = ref(false);
const error = ref('');
const overview = ref<DashboardOverview | null>(null);
let latestRequestId = 0;

async function loadOverview() {
  const requestId = ++latestRequestId;
  loading.value = true;
  error.value = '';

  try {
    const result = await dashboardService.getOverview(period.value);
    if (requestId !== latestRequestId) {
      return;
    }
    overview.value = result;
  } catch {
    if (requestId !== latestRequestId) {
      return;
    }
    error.value = overview.value
      ? '数据更新失败，已保留上次数据。'
      : '工作台数据加载失败，请稍后重试。';
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false;
    }
  }
}

function selectPeriod(nextPeriod: DashboardPeriod) {
  if (period.value === nextPeriod) {
    return;
  }
  period.value = nextPeriod;
  void loadOverview();
}

onMounted(() => {
  void loadOverview();
});

onBeforeUnmount(() => {
  latestRequestId += 1;
});
</script>

<template>
  <section class="min-w-0 space-y-4">
    <div class="flex min-h-9 items-center justify-end">
      <div
        class="inline-flex items-center rounded-md border border-slate-200 bg-white p-0.5"
        role="group"
        aria-label="经营数据周期"
      >
        <button
          v-for="option in periodOptions"
          :key="option.value"
          :data-testid="`period-${option.value}`"
          type="button"
          class="flex h-7 min-w-12 items-center justify-center rounded px-3 text-xs font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          :class="period === option.value ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'"
          :aria-pressed="period === option.value"
          @click="selectPeriod(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <div class="h-1" aria-hidden="true">
      <div v-if="loading" data-testid="dashboard-loading" class="grid h-full grid-cols-3 gap-1">
        <span class="animate-pulse bg-blue-500"></span>
        <span class="animate-pulse bg-cyan-500 [animation-delay:120ms]"></span>
        <span class="animate-pulse bg-emerald-500 [animation-delay:240ms]"></span>
      </div>
    </div>

    <div
      v-if="error"
      data-testid="dashboard-error"
      class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
      role="alert"
    >
      <span class="flex items-center gap-2">
        <CircleAlert class="h-4 w-4 shrink-0" aria-hidden="true" />
        {{ error }}
      </span>
      <button
        data-testid="dashboard-retry"
        type="button"
        class="inline-flex h-8 items-center gap-1.5 rounded-md border border-rose-300 bg-white px-3 text-xs font-semibold text-rose-700 transition hover:bg-rose-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-600"
        @click="loadOverview"
      >
        <RefreshCw class="h-3.5 w-3.5" aria-hidden="true" />
        重新加载
      </button>
    </div>

    <div v-if="overview" data-testid="dashboard-content" class="min-w-0 space-y-4" :aria-busy="loading">
      <DashboardSummaryGrid :summary="overview.summary" />

      <div class="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(360px,0.8fr)]">
        <SalesTrendPanel :points="overview.salesTrend" />
        <StockAlertList :alerts="overview.stockAlerts" />
      </div>

      <RecentOrderList :orders="overview.recentOrders" />
    </div>

    <div v-else-if="loading" class="grid min-w-0 gap-4" aria-label="正在加载工作台数据">
      <div class="grid gap-4 lg:grid-cols-3">
        <div v-for="index in 3" :key="index" class="h-36 animate-pulse rounded-lg border border-slate-200 bg-slate-100"></div>
      </div>
      <div class="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(360px,0.8fr)]">
        <div class="min-h-[300px] animate-pulse rounded-lg border border-slate-200 bg-slate-100"></div>
        <div class="min-h-[300px] animate-pulse rounded-lg border border-slate-200 bg-slate-100"></div>
      </div>
    </div>
  </section>
</template>
