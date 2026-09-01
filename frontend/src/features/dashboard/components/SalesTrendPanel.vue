<script setup lang="ts">
import { computed, useId } from 'vue';
import { ChartNoAxesCombined } from 'lucide-vue-next';
import type { DashboardSalesTrendPoint } from '../types';

interface MetricValue {
  valid: boolean;
  value: number;
}

interface AxisTick {
  label: string;
  value: number;
  y: number;
}

const props = withDefaults(defineProps<{
  points?: DashboardSalesTrendPoint[] | null;
}>(), {
  points: () => []
});

const chart = {
  width: 800,
  height: 350,
  left: 78,
  right: 66,
  top: 32,
  bottom: 46
};

const plotWidth = chart.width - chart.left - chart.right;
const plotHeight = chart.height - chart.top - chart.bottom;
const chartUid = useId();
const titleId = `${chartUid}-sales-trend-title`;
const descriptionId = `${chartUid}-sales-trend-description`;

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const axisMoneyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
});

const countFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
});

function parseMetric(value: unknown): MetricValue {
  const valid = typeof value === 'number' && Number.isFinite(value) && value >= 0;
  return { valid, value: valid ? value : 0 };
}

const safePoints = computed(() => Array.isArray(props.points) ? props.points : []);
const parsedPoints = computed(() => safePoints.value.map((point) => ({
  source: point,
  sales: parseMetric(point?.salesAmount),
  orders: parseMetric(point?.orderCount)
})));

function metricMax(metric: 'sales' | 'orders') {
  return Math.max(0, ...parsedPoints.value
    .filter((point) => point[metric].valid)
    .map((point) => point[metric].value));
}

const maxSales = computed(() => metricMax('sales'));
const maxOrders = computed(() => metricMax('orders'));

function nearestNiceStep(rawStep: number) {
  if (rawStep <= 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const normalized = rawStep / magnitude;
  const candidates = [1, 2, 2.5, 5, 10];
  const nearest = candidates.reduce((best, candidate) => (
    Math.abs(candidate - normalized) < Math.abs(best - normalized) ? candidate : best
  ));
  return nearest * magnitude;
}

function buildAxis(maxValue: number, kind: 'money' | 'count') {
  if (maxValue <= 0) {
    return {
      max: 0,
      ticks: [{ value: 0, label: '0', y: chart.top + plotHeight }] satisfies AxisTick[]
    };
  }

  const rawStep = maxValue / 4;
  const step = kind === 'count'
    ? Math.max(1, Math.ceil(nearestNiceStep(rawStep)))
    : nearestNiceStep(rawStep);
  const axisMax = Math.ceil(maxValue / step) * step;
  const intervalCount = Math.round(axisMax / step);
  const formatter = kind === 'money' ? axisMoneyFormatter : countFormatter;
  const ticks: AxisTick[] = Array.from({ length: intervalCount + 1 }, (_, index) => {
    const value = axisMax - step * index;
    return {
      value,
      label: formatter.format(value),
      y: chart.top + (plotHeight * index) / intervalCount
    };
  });
  return { max: axisMax, ticks };
}

const salesAxis = computed(() => buildAxis(maxSales.value, 'money'));
const orderAxis = computed(() => buildAxis(maxOrders.value, 'count'));
const gridTicks = computed(() => salesAxis.value.ticks.length > 1 ? salesAxis.value.ticks : orderAxis.value.ticks);
const hasPositiveSales = computed(() => maxSales.value > 0);
const hasPositiveOrders = computed(() => maxOrders.value > 0);
const barWidth = computed(() => Math.max(4, Math.min(34, (plotWidth / Math.max(parsedPoints.value.length, 1)) * 0.46)));

const chartPoints = computed(() => parsedPoints.value.map((point, index) => {
  const slotWidth = plotWidth / Math.max(parsedPoints.value.length, 1);
  const x = chart.left + slotWidth * (index + 0.5);
  const barHeight = point.sales.valid && salesAxis.value.max > 0
    ? (point.sales.value / salesAxis.value.max) * plotHeight
    : 0;
  const orderY = point.orders.valid && orderAxis.value.max > 0
    ? chart.top + plotHeight - (point.orders.value / orderAxis.value.max) * plotHeight
    : chart.top + plotHeight;
  return {
    ...point,
    index,
    x,
    barHeight,
    barY: chart.top + plotHeight - barHeight,
    orderY
  };
}));

const orderSegments = computed(() => {
  const segments: Array<{ endIndex: number; path: string; startIndex: number }> = [];
  let current: typeof chartPoints.value = [];

  function flushSegment() {
    if (current.length >= 2) {
      segments.push({
        startIndex: current[0].index,
        endIndex: current[current.length - 1].index,
        path: current.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.orderY}`).join(' ')
      });
    }
    current = [];
  }

  for (const point of chartPoints.value) {
    if (point.orders.valid) {
      current.push(point);
    } else {
      flushSegment();
    }
  }
  flushSegment();
  return segments;
});

const labelStep = computed(() => Math.max(1, Math.ceil(safePoints.value.length / 7)));
const chartDescription = computed(() => `共 ${safePoints.value.length} 个时间桶。青色柱按左轴展示销售额，蓝色折线按右轴展示订单数；下方数据表提供每个时间桶的精确值。`);

function showDateLabel(index: number) {
  return index === safePoints.value.length - 1 || index % labelStep.value === 0;
}

function formatPeriodLabel(point: DashboardSalesTrendPoint) {
  if (typeof point?.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(point.date)) {
    return point.date.slice(5).replace('-', '/');
  }
  if (typeof point?.month === 'string' && /^\d{4}-\d{2}$/.test(point.month)) {
    return point.month.replace('-', '/');
  }
  return '--';
}

function formatAccessiblePeriod(point: DashboardSalesTrendPoint) {
  if (typeof point?.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(point.date)) {
    return point.date;
  }
  if (typeof point?.month === 'string' && /^\d{4}-\d{2}$/.test(point.month)) {
    return point.month;
  }
  return '--';
}

function formatMoneyMetric(metric: MetricValue) {
  return metric.valid ? `¥${moneyFormatter.format(metric.value)}` : '--';
}

function formatCountMetric(metric: MetricValue) {
  return metric.valid ? countFormatter.format(metric.value) : '--';
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
      class="relative aspect-[16/7] w-full overflow-hidden"
    >
      <svg
        data-testid="sales-trend-svg"
        class="block h-full w-full"
        :viewBox="`0 0 ${chart.width} ${chart.height}`"
        role="img"
        :aria-labelledby="`${titleId} ${descriptionId}`"
        preserveAspectRatio="xMidYMid meet"
      >
        <title :id="titleId">销售额（CNY）与订单数（单）趋势图</title>
        <desc :id="descriptionId">{{ chartDescription }}</desc>

        <g fill="#475569" font-size="12">
          <text data-testid="left-axis-unit" :x="chart.left" y="19">销售额（CNY）</text>
          <text data-testid="right-axis-unit" :x="chart.width - chart.right" y="19" text-anchor="end">订单数（单）</text>
        </g>

        <g aria-hidden="true">
          <line
            v-for="tick in gridTicks"
            :key="`grid-${tick.value}`"
            :x1="chart.left"
            :x2="chart.width - chart.right"
            :y1="tick.y"
            :y2="tick.y"
            stroke="#e2e8f0"
            stroke-width="1"
          />
          <line :x1="chart.left" :x2="chart.left" :y1="chart.top" :y2="chart.top + plotHeight" stroke="#cbd5e1" stroke-width="1" />
          <line :x1="chart.width - chart.right" :x2="chart.width - chart.right" :y1="chart.top" :y2="chart.top + plotHeight" stroke="#cbd5e1" stroke-width="1" />
        </g>

        <g fill="#64748b" font-size="11" aria-hidden="true">
          <text
            v-for="tick in salesAxis.ticks"
            :key="`sales-tick-${tick.value}`"
            data-testid="left-axis-tick"
            :x="chart.left - 9"
            :y="tick.y + 4"
            text-anchor="end"
          >{{ tick.label }}</text>
          <text
            v-for="tick in orderAxis.ticks"
            :key="`order-tick-${tick.value}`"
            data-testid="right-axis-tick"
            :x="chart.width - chart.right + 9"
            :y="tick.y + 4"
            text-anchor="start"
          >{{ tick.label }}</text>
        </g>

        <g v-if="hasPositiveSales">
          <template v-for="point in chartPoints" :key="`bar-${point.index}`">
            <rect
              v-if="point.sales.valid && point.sales.value > 0"
              data-testid="sales-bar"
              :x="point.x - barWidth / 2"
              :y="point.barY"
              :width="barWidth"
              :height="point.barHeight"
              rx="2"
              fill="#06b6d4"
              opacity="0.82"
            >
              <title>{{ formatPeriodLabel(point.source) }} 销售额 {{ formatMoneyMetric(point.sales) }}</title>
            </rect>
          </template>
        </g>

        <g v-if="hasPositiveOrders">
          <path
            v-for="segment in orderSegments"
            :key="`line-${segment.startIndex}-${segment.endIndex}`"
            data-testid="order-line"
            :d="segment.path"
            fill="none"
            stroke="#2563eb"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
            vector-effect="non-scaling-stroke"
          />
          <template v-for="point in chartPoints" :key="`point-${point.index}`">
            <circle
              v-if="point.orders.valid"
              data-testid="order-point"
              :cx="point.x"
              :cy="point.orderY"
              r="4"
              fill="#ffffff"
              stroke="#2563eb"
              stroke-width="2"
              vector-effect="non-scaling-stroke"
            >
              <title>{{ formatPeriodLabel(point.source) }} 订单数 {{ formatCountMetric(point.orders) }}</title>
            </circle>
          </template>
        </g>

        <text
          v-if="!hasPositiveSales && !hasPositiveOrders"
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

      <table data-testid="sales-trend-data-table" class="sr-only">
        <caption>销售与订单趋势数据</caption>
        <thead>
          <tr>
            <th scope="col">日期/月</th>
            <th scope="col">销售额（CNY）</th>
            <th scope="col">订单数（单）</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="point in chartPoints" :key="`data-${point.index}`">
            <th scope="row">{{ formatAccessiblePeriod(point.source) }}</th>
            <td>{{ formatMoneyMetric(point.sales) }}</td>
            <td>{{ formatCountMetric(point.orders) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
