<script setup lang="ts">
import { computed } from 'vue'
import { MoreHorizontal, X } from 'lucide-vue-next'
import { formatCaseBreakdown } from '../warehouseCanvasModel'
import type { PlannerPalletGroup } from '../warehousePlannerScene'

const props = defineProps<{ pallet: PlannerPalletGroup }>()

const emit = defineEmits<{ close: [] }>()

const totalUnits = computed(() => props.pallet.contents.reduce((total, item) => total + item.units, 0))
</script>

<template>
  <aside class="planner-inspector" :aria-label="`${pallet.name} 库存内容`">
    <header>
      <strong>{{ pallet.name }}</strong>
      <span class="header-actions">
        <button data-testid="planner-inspector-more" type="button" aria-label="更多货堆操作，UI 预览暂未开放" title="UI 预览暂未开放" disabled><MoreHorizontal :size="17" /></button>
        <button data-testid="planner-inspector-close" type="button" aria-label="关闭货堆库存内容" @click="emit('close')"><X :size="15" /></button>
      </span>
    </header>
    <div data-testid="planner-inspector-summary" class="inventory-summary">
      <span><strong>{{ pallet.contents.length }}</strong> 种商品</span>
      <span>共 <strong>{{ totalUnits }}</strong> 个</span>
    </div>
    <ul class="product-list" aria-label="货堆商品清单">
      <li v-for="item in pallet.contents" :key="item.skuCode" data-testid="planner-inspector-product">
        <div class="product-heading">
          <strong>{{ item.productName }}</strong>
          <b>{{ item.units }} 个</b>
        </div>
        <div class="product-meta">
          <span>{{ item.skuCode }}</span>
          <small>{{ formatCaseBreakdown(item.units, item.unitsPerCase) }}</small>
        </div>
        <em>{{ item.unitsPerCase }} 个/件 · 按 SKU 换算</em>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.planner-inspector { width: 310px; border: 1px solid #e1e6ec; border-radius: 8px; padding: 13px 14px 14px; background: rgba(255,255,255,.98); color: #25314d; box-shadow: 0 10px 28px rgba(37,49,77,.15); }
header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid #edf1f5; }
header strong { min-width: 0; overflow: hidden; font-size: 13px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.header-actions { display: flex; align-items: center; }
button { display: grid; place-items: center; width: 25px; height: 25px; border: 0; border-radius: 5px; padding: 0; background: transparent; color: #64748b; cursor: pointer; }
button:hover { background: #f1f5f9; color: #25314d; } button:focus-visible { outline: 2px solid #536dff; outline-offset: 1px; }
button:disabled { color: #cbd5e1; cursor: not-allowed; }
.inventory-summary { display: flex; align-items: center; justify-content: space-between; margin: 10px 0 0; padding: 9px 10px; border-radius: 6px; background: #f4f7fa; color: #64748b; font-size: 11px; }
.inventory-summary strong { color: #25314d; font: 650 14px/1 Inter,sans-serif; }
.product-list { display: grid; max-height: 228px; margin: 0; padding: 0; overflow-y: auto; list-style: none; }
.product-list li { display: grid; gap: 4px; padding: 10px 2px; border-bottom: 1px solid #edf1f5; }
.product-list li:last-child { padding-bottom: 1px; border-bottom: 0; }
.product-heading, .product-meta { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.product-heading strong { min-width: 0; overflow: hidden; color: #25314d; font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.product-heading b { flex: 0 0 auto; color: #25314d; font: 650 12px/1.3 Inter,sans-serif; }
.product-meta span { min-width: 0; overflow: hidden; color: #64748b; font: 9px/1.4 Inter,sans-serif; text-overflow: ellipsis; white-space: nowrap; }
.product-meta small { flex: 0 0 auto; color: #08a0a3; font-size: 10px; font-weight: 600; }
.product-list em { color: #94a3b8; font-size: 9px; font-style: normal; }
</style>
