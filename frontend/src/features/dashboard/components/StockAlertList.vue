<script setup lang="ts">
import { computed } from 'vue';
import { PackageSearch, TriangleAlert } from 'lucide-vue-next';
import type { DashboardStockAlert } from '../types';

const props = withDefaults(defineProps<{
  alerts?: DashboardStockAlert[] | null;
}>(), {
  alerts: () => []
});

const safeAlerts = computed(() => Array.isArray(props.alerts) ? props.alerts : []);

const quantityFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
});

function formatQuantity(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? quantityFormatter.format(value) : '--';
}

function safeText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : '--';
}
</script>

<template>
  <section class="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white">
    <header class="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
      <div class="flex items-center gap-2">
        <TriangleAlert class="h-4 w-4 text-orange-600" aria-hidden="true" />
        <h2 class="text-sm font-semibold text-slate-800">库存预警</h2>
      </div>
      <span class="text-xs text-slate-500">{{ safeAlerts.length }} 项</span>
    </header>

    <div v-if="safeAlerts.length === 0" class="flex min-h-[260px] flex-col items-center justify-center gap-2 px-4 text-sm text-slate-400">
      <PackageSearch class="h-6 w-6" aria-hidden="true" />
      <span>暂无库存预警</span>
    </div>
    <ul v-else class="max-h-[340px] divide-y divide-slate-100 overflow-y-auto" data-testid="stock-alert-list">
      <li v-for="(alert, index) in safeAlerts" :key="alert.skuId ?? alert.skuCode ?? index" class="px-4 py-3 text-xs text-slate-700">
        <div class="flex min-w-0 items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="truncate font-medium text-slate-900" :title="safeText(alert.productName)">{{ safeText(alert.productName) }}</p>
            <p class="mt-1 flex min-w-0 items-center gap-2 text-slate-500">
              <span class="truncate" :title="safeText(alert.skuName)">{{ safeText(alert.skuName) }}</span>
              <span class="shrink-0 font-mono text-slate-600">{{ safeText(alert.skuCode) }}</span>
            </p>
          </div>
          <span
            class="inline-flex shrink-0 whitespace-nowrap rounded-md px-2 py-1 font-medium"
            :class="alert.stockQuantity === 0 ? 'bg-rose-50 text-rose-700' : 'bg-orange-50 text-orange-700'"
          >缺口 {{ formatQuantity(alert.shortageQuantity) }}</span>
        </div>
        <dl class="mt-2 flex items-center gap-4 border-t border-slate-100 pt-2 text-slate-500">
          <div class="flex items-center gap-1.5">
            <dt>当前</dt>
            <dd class="font-medium tabular-nums text-slate-800">{{ formatQuantity(alert.stockQuantity) }}</dd>
          </div>
          <div class="flex items-center gap-1.5">
            <dt>安全</dt>
            <dd class="font-medium tabular-nums text-slate-800">{{ formatQuantity(alert.safetyStockQuantity) }}</dd>
          </div>
        </dl>
      </li>
    </ul>
  </section>
</template>
