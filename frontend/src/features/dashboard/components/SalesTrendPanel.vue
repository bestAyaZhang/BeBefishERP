<script setup lang="ts">
import { computed } from 'vue';
import { ChartNoAxesCombined } from 'lucide-vue-next';
import type { DashboardSalesTrendPoint } from '../types';

const props = withDefaults(defineProps<{
  points?: DashboardSalesTrendPoint[] | null;
}>(), {
  points: () => []
});

const chart = {
  width: 800,
  height: 350,
  left: 62,
  right: 30,
  top: 28,
  bottom: 46
};

const plotWidth = chart.width - chart.left - chart.right;
const plotHeight = chart.height - chart.top - chart.bottom;

function safeNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, value) : 0;
}

const safePoints = computed(() => Array.isArray(props.points) ? props.points : []);
const maxSales = computed(() => Math.max(0, ...safePoints.value.map((point) => safeNumber(point.salesAmount))));
const maxOrders = computed(() => Math.max(0, ...safePoints.value.map((point) => safeNumber(point.orderCount))));
const hasSales = computed(() => maxSales.value > 0);
const hasOrders = computed(() => maxOrders.value > 0);
const barWidth = computed(() => Math.max(4, Math.min(34, (plotWidth / Math.max(safePoints.value.length, 1)) * 0.46)));

const chartPoints = computed(() => safePoints.value.map((point, index) => {
  const slotWidth = plotWidth / Math.max(safePoints.value.length, 1);
  const x = chart.left + slotWidth * (index + 0.5);
  const salesAmount = safeNumber(point.salesAmount);
  const orderCount = safeNumber(point.orderCount);
  const barHeight = hasSales.value ? (salesAmount / maxSales.value) * plotHeight : 0;
  const orderY = hasOrders.value
    ? chart.top + plotHeight - (orderCount / maxOrders.value) * plotHeight
    : chart.top + plotHeight;
  return {
    source: point,
    x,
    salesAmount,
    orderCount,
    barHeight,
    barY: chart.top + plotHeight - barHeight,
    orderY
  };
}));

const orderPath = computed(() => chartPoints.value
  .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.orderY}`)
  .join(' '));

const gridLines = Array.from({ length: 5 }, (_, index) => chart.top + (plotHeight / 4) * index);

const labelStep = computed(() => Math.max(1, Math.ceil(safePoints.value.length / 7)));

function showDateLabel(index: number) {
  return index === safePoints.value.length - 1 || index % labelStep.value === 0;
}

function formatPeriodLabel(point: DashboardSalesTrendPoint) {
  if (typeof point.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(point.date)) {
    return point.date.slice(5).replace('-', '/');
  }
  if (typeof point.month === 'string' && /^\d{4}-\d{2}$/.test(point.month)) {
    return point.month.replace('-', '/');
  }
  return '--';
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value);
}
</script>

<template>
  <section class="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white">
    <header class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
      <div class="flex items-center gap-2">
        <ChartNoAxesCombined class="h-4 w-4 text-blue-600" aria-hidden="true" />
        <h2 class="text-sm font-semibold text-slate-800">销售与订单趋势</h2>
      </div>
      <div class="flex items-center gap-4 text-xs text-slate-500" aria-label="图例">
        <span class="flex items-center gap-1.5"><span class="h-2.5 w-2.5 bg-cyan-500"></span>销售额</span>
        <span class="flex items-center gap-1.5"><span class="h-0.5 w-4 bg-blue-600"></span>订单数</span>
      </div>
    </header>

    <div v-if="safePoints.length === 0" class="flex min-h-[260px] items-center justify-center px-4 text-sm text-slate-400">
      暂无销售趋势
    </div>
    <div
      v-else
      data-testid="sales-trend-chart"
      class="aspect-[16/7] min-h-[240px] w-full overflow-hidden px-2 py-3"
    >
      <svg
        class="h-full w-full"
        :viewBox="`0 0 ${chart.width} ${chart.height}`"
        role="img"
        aria-label="销售额柱状图与订单数折线图"
        preserveAspectRatio="none"
      >
        <g aria-hidden="true">
          <line
            v-for="lineY in gridLines"
            :key="lineY"
            :x1="chart.left"
            :x2="chart.width - chart.right"
            :y1="lineY"
            :y2="lineY"
            stroke="#e2e8f0"
            stroke-width="1"
          />
        </g>

        <g v-if="hasSales">
          <rect
            v-for="(point, index) in chartPoints"
            v-show="point.salesAmount > 0"
            :key="`bar-${index}`"
            data-testid="sales-bar"
            :x="point.x - barWidth / 2"
            :y="point.barY"
            :width="barWidth"
            :height="point.barHeight"
            rx="2"
            fill="#06b6d4"
            opacity="0.82"
          >
            <title>{{ formatPeriodLabel(point.source) }} 销售额 ¥{{ formatMoney(point.salesAmount) }}</title>
          </rect>
        </g>

        <path
          v-if="hasOrders"
          data-testid="order-line"
          :d="orderPath"
          fill="none"
          stroke="#2563eb"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
        <g v-if="hasOrders">
          <circle
            v-for="(point, index) in chartPoints"
            :key="`point-${index}`"
            :cx="point.x"
            :cy="point.orderY"
            r="4"
            fill="#ffffff"
            stroke="#2563eb"
            stroke-width="2"
            vector-effect="non-scaling-stroke"
          >
            <title>{{ formatPeriodLabel(point.source) }} 订单数 {{ formatCount(point.orderCount) }}</title>
          </circle>
        </g>

        <text
          v-if="!hasSales && !hasOrders"
          :x="chart.left + plotWidth / 2"
          :y="chart.top + plotHeight / 2"
          fill="#94a3b8"
          font-size="14"
          text-anchor="middle"
        >本期无销售或订单</text>

        <g fill="#64748b" font-size="12" aria-hidden="true">
          <text
            v-for="(point, index) in chartPoints"
            v-show="showDateLabel(index)"
            :key="`label-${index}`"
            :x="point.x"
            :y="chart.height - 15"
            text-anchor="middle"
          >{{ formatPeriodLabel(point.source) }}</text>
        </g>
      </svg>
    </div>
  </section>
</template>
