<script setup lang="ts">
import { MoreHorizontal, X } from 'lucide-vue-next'
import { formatCaseBreakdown } from '../warehouseCanvasModel'
import type { PlannerPalletGroup } from '../warehousePlannerScene'

defineProps<{ pallet: PlannerPalletGroup }>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <aside class="planner-inspector" :aria-label="`${pallet.name} 属性`">
    <header>
      <strong>{{ pallet.name }}</strong>
      <span class="header-actions">
        <button type="button" aria-label="更多货堆操作"><MoreHorizontal :size="17" /></button>
        <button data-testid="planner-inspector-close" type="button" aria-label="关闭货堆属性" @click="emit('close')"><X :size="15" /></button>
      </span>
    </header>
    <dl class="geometry-grid">
      <div><dt>X</dt><dd>{{ pallet.xMeters }}m</dd></div>
      <div><dt>Y</dt><dd>{{ pallet.yMeters }}m</dd></div>
      <div><dt>长</dt><dd>{{ pallet.lengthMeters }}m</dd></div>
      <div><dt>宽</dt><dd>{{ pallet.widthMeters }}m</dd></div>
      <div><dt>旋转</dt><dd>{{ pallet.rotation }}°</dd></div>
    </dl>
    <div class="inventory-readout">
      <span>{{ pallet.skuCode }}</span>
      <strong data-testid="planner-inspector-units">{{ pallet.units }} 个</strong>
      <small data-testid="planner-inspector-cases">{{ formatCaseBreakdown(pallet.units, pallet.unitsPerCase) }}</small>
      <em>{{ pallet.unitsPerCase }} 个/件 · 自动换算</em>
    </div>
  </aside>
</template>

<style scoped>
.planner-inspector { width: 210px; border: 1px solid #e1e6ec; border-radius: 8px; padding: 13px 14px 14px; background: rgba(255,255,255,.98); color: #25314d; box-shadow: 0 10px 28px rgba(37,49,77,.15); }
header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid #edf1f5; }
header strong { min-width: 0; overflow: hidden; font-size: 13px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.header-actions { display: flex; align-items: center; }
button { display: grid; place-items: center; width: 25px; height: 25px; border: 0; border-radius: 5px; padding: 0; background: transparent; color: #64748b; cursor: pointer; }
button:hover { background: #f1f5f9; color: #25314d; } button:focus-visible { outline: 2px solid #536dff; outline-offset: 1px; }
.geometry-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px 13px; margin: 11px 0 0; }
.geometry-grid div { display: grid; grid-template-columns: 22px 1fr; align-items: baseline; }
dt { color: #94a3b8; font-size: 11px; } dd { margin: 0; font: 12px/1.4 Inter,sans-serif; color: #354159; }
.inventory-readout { display: grid; grid-template-columns: 1fr auto; gap: 3px 10px; margin-top: 10px; padding-top: 10px; border-top: 1px solid #edf1f5; }
.inventory-readout > span { color: #64748b; font: 10px/1.5 Inter,sans-serif; }
.inventory-readout > strong { color: #25314d; font: 650 14px/1.2 Inter,sans-serif; }
.inventory-readout > small { color: #08a0a3; font-size: 11px; font-weight: 600; }
.inventory-readout > em { color: #94a3b8; font-size: 9px; font-style: normal; text-align: right; }
</style>
