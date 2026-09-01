<script setup lang="ts">
import type { Component } from 'vue';
import {
  Banknote,
  Boxes,
  Building2,
  CircleDollarSign,
  FileText,
  Package,
  PackageCheck,
  ShoppingCart,
  TriangleAlert,
  Warehouse
} from 'lucide-vue-next';
import type { DashboardSummary } from '../types';

type SummaryKey = keyof DashboardSummary;

interface MetricDefinition {
  key: SummaryKey;
  label: string;
  icon: Component;
  kind: 'count' | 'money';
  tone: string;
}

interface MetricGroup {
  title: string;
  metrics: MetricDefinition[];
}

const props = defineProps<{
  summary?: DashboardSummary | null;
}>();

const groups: MetricGroup[] = [
  {
    title: '商品与供应链',
    metrics: [
      { key: 'productCount', label: '商品', icon: Package, kind: 'count', tone: 'text-blue-600 bg-blue-50' },
      { key: 'enabledProductCount', label: '启用商品', icon: PackageCheck, kind: 'count', tone: 'text-emerald-600 bg-emerald-50' },
      { key: 'skuCount', label: 'SKU', icon: Boxes, kind: 'count', tone: 'text-cyan-600 bg-cyan-50' },
      { key: 'enabledSupplierCount', label: '启用供应商', icon: Building2, kind: 'count', tone: 'text-slate-600 bg-slate-100' }
    ]
  },
  {
    title: '库存风险',
    metrics: [
      { key: 'zeroStockSkuCount', label: '零库存 SKU', icon: Warehouse, kind: 'count', tone: 'text-rose-600 bg-rose-50' },
      { key: 'lowStockSkuCount', label: '低库存 SKU', icon: TriangleAlert, kind: 'count', tone: 'text-orange-600 bg-orange-50' }
    ]
  },
  {
    title: '周期经营',
    metrics: [
      { key: 'orderCount', label: '订单', icon: ShoppingCart, kind: 'count', tone: 'text-blue-600 bg-blue-50' },
      { key: 'salesAmount', label: '销售额', icon: Banknote, kind: 'money', tone: 'text-emerald-600 bg-emerald-50' },
      { key: 'outstandingAmount', label: '未收金额', icon: CircleDollarSign, kind: 'money', tone: 'text-orange-600 bg-orange-50' },
      { key: 'draftOrderCount', label: '草稿订单', icon: FileText, kind: 'count', tone: 'text-slate-600 bg-slate-100' }
    ]
  }
];

const countFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 0
});

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

function metricValue(key: SummaryKey, kind: MetricDefinition['kind']) {
  const value = props.summary?.[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '--';
  }
  return kind === 'money' ? `¥${moneyFormatter.format(value)}` : countFormatter.format(value);
}
</script>

<template>
  <div class="grid min-w-0 gap-4 lg:grid-cols-3">
    <section
      v-for="group in groups"
      :key="group.title"
      class="flex min-w-0 flex-col overflow-hidden rounded-lg border border-slate-200 bg-white"
    >
      <header class="border-b border-slate-100 px-4 py-3">
        <h2 class="text-sm font-semibold text-slate-700">{{ group.title }}</h2>
      </header>
      <div class="grid flex-1 grid-cols-2">
        <article
          v-for="(metric, index) in group.metrics"
          :key="metric.key"
          :data-testid="`summary-${metric.key}`"
          class="min-w-0 border-slate-100 px-4 py-3"
          :class="[
            index % 2 === 1 ? 'border-l' : '',
            index > 1 ? 'border-t' : ''
          ]"
        >
          <div class="flex min-w-0 items-center gap-2">
            <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md" :class="metric.tone">
              <component :is="metric.icon" class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="min-w-0 text-xs font-medium text-slate-500">{{ metric.label }}</span>
          </div>
          <p class="mt-2 truncate text-xl font-semibold text-slate-900" :title="metricValue(metric.key, metric.kind)">
            {{ metricValue(metric.key, metric.kind) }}
          </p>
        </article>
      </div>
    </section>
  </div>
</template>
