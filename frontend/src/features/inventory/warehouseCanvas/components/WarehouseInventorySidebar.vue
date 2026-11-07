<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleAlert, PackageOpen, RefreshCw, Search } from 'lucide-vue-next'
import type { PlannerPalletGroup } from '../warehousePlannerScene'
import type { WarehouseInventoryAllocation, WarehouseInventoryLayout } from '../warehouseInventoryService'

const props = defineProps<{
  inventory: WarehouseInventoryLayout | null
  palletGroups: readonly PlannerPalletGroup[]
  loading: boolean
  error: string
  selectedPalletId: string | null
}>()

const emit = defineEmits<{
  'select-pallet': [id: string]
  retry: []
}>()

type PileSkuRow = WarehouseInventoryAllocation & { units: number }

const number = new Intl.NumberFormat('zh-CN')
const searchQuery = ref('')

function aggregateSkus(items: WarehouseInventoryAllocation[]): PileSkuRow[] {
  const totals = new Map<number, PileSkuRow>()
  for (const item of items) {
    const existing = totals.get(item.skuId)
    if (existing) existing.units += item.units
    else totals.set(item.skuId, { ...item })
  }
  return [...totals.values()].sort((left, right) => right.units - left.units)
}

const pileRows = computed(() => props.palletGroups.map((pallet) => {
  const allocations = props.inventory?.allocations.filter((item) => item.palletId === pallet.id) ?? []
  const skus = aggregateSkus(allocations)
  return {
    pallet,
    skus,
    skuCount: skus.length,
    units: skus.reduce((sum, item) => sum + item.units, 0),
  }
}))

const filteredPileRows = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return pileRows.value
  return pileRows.value.filter((row) => (
    [row.pallet.code, row.pallet.name].some((value) => value.toLocaleLowerCase('zh-CN').includes(query))
    || row.skus.some((sku) => (
      [sku.productName, sku.skuCode, sku.skuName, sku.specification]
        .some((value) => value?.toLocaleLowerCase('zh-CN').includes(query))
    ))
  ))
})
</script>

<template>
  <aside class="inventory-sidebar">
    <header class="sidebar-header">
      <h2>货物堆与实际库存</h2>
      <p>点击货物堆定位布局，数量来自库存台账</p>
    </header>

    <label class="search-field">
      <Search :size="16" aria-hidden="true" />
      <input v-model="searchQuery" data-testid="inventory-pile-search" type="search" aria-label="搜索货物堆或 SKU" placeholder="搜索货物堆或 SKU">
    </label>

    <div v-if="!loading && !error && palletGroups.length" class="pile-list-heading">
      <span>货堆清单</span><span>{{ filteredPileRows.length }} / {{ palletGroups.length }} 个</span>
    </div>

    <div v-if="loading" class="sidebar-state">
      <RefreshCw class="state-icon is-spinning" :size="22" aria-hidden="true" />
      <span>正在读取实际库存…</span>
    </div>
    <div v-else-if="error" class="sidebar-state error-state">
      <CircleAlert class="state-icon" :size="22" aria-hidden="true" />
      <span>{{ error }}</span>
      <button type="button" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else-if="!palletGroups.length" class="sidebar-state">
      <PackageOpen class="state-icon" :size="22" aria-hidden="true" />
      <span>当前规划中还没有货物堆</span>
    </div>

    <div v-else class="pile-list">
      <button
        v-for="row in filteredPileRows"
        :key="row.pallet.id"
        type="button"
        class="pile-card"
        :class="{ 'is-selected': selectedPalletId === row.pallet.id }"
        :aria-pressed="selectedPalletId === row.pallet.id"
        :data-testid="`inventory-pile-${row.pallet.id}`"
        @click="emit('select-pallet', row.pallet.id)"
      >
        <span class="pile-heading">
          <span class="pile-identity">
            <strong>{{ row.pallet.code }}</strong>
            <span>{{ row.pallet.name }}</span>
          </span>
          <span class="pile-count">{{ row.skuCount }}种 SKU · {{ number.format(row.units) }}个</span>
        </span>
        <span class="pile-divider" />
        <span v-if="row.skus.length" class="sku-preview">
          <span v-for="sku in row.skus.slice(0, 2)" :key="sku.skuId" class="sku-row">
            <span class="sku-copy">
              <span class="sku-name">{{ sku.productName }} / {{ sku.skuCode }}</span>
              <span v-if="sku.specification" class="sku-spec">{{ sku.specification }}</span>
            </span>
            <strong>{{ number.format(sku.units) }}个</strong>
          </span>
          <span v-if="row.skus.length > 2" class="more-skus">另有 {{ row.skus.length - 2 }} 种 SKU</span>
        </span>
        <span v-else class="empty-pile">暂无实际库存</span>
      </button>

      <div v-if="!filteredPileRows.length" class="sidebar-state compact-state">
        <Search class="state-icon" :size="20" aria-hidden="true" />
        <span>没有匹配的货物堆或 SKU</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.inventory-sidebar { display:flex; width:300px; min-width:300px; min-height:0; flex-direction:column; gap:16px; padding:24px 16px 16px; overflow:hidden; border-right:1px solid var(--wh-border); background:var(--wh-surface); }
.sidebar-header h2 { margin:0; color:var(--wh-text); font-size:16px; font-weight:600; line-height:24px; }
.sidebar-header p { margin:6px 0 0; color:var(--wh-muted); font-size:12px; line-height:20px; }
.search-field { display:flex; height:38px; flex:none; align-items:center; gap:8px; padding:0 10px; border:1px solid var(--wh-border); border-radius:6px; color:var(--wh-muted); background:var(--wh-raised); }
.search-field:focus-within { outline:2px solid var(--wh-accent); outline-offset:2px; }
.search-field input { width:100%; min-width:0; border:0; outline:0; color:var(--wh-text); font:inherit; font-size:13px; background:transparent; }
.search-field input::placeholder { color:var(--wh-muted); }
.pile-list-heading { display:flex; justify-content:space-between; color:var(--wh-muted); font-size:12px; }
.pile-list { display:flex; min-height:0; flex:1; flex-direction:column; gap:4px; overflow-y:auto; scrollbar-width:thin; scrollbar-color:var(--wh-control) transparent; }
.pile-card { display:flex; width:100%; flex:none; flex-direction:column; padding:12px; border:1px solid transparent; border-radius:6px; color:var(--wh-text); text-align:left; background:transparent; cursor:pointer; }
.pile-card:hover { border-color:var(--wh-border); }
.pile-card.is-selected { border-color:var(--wh-control); background:var(--wh-active); }
.pile-card:focus-visible { outline:2px solid var(--wh-accent); outline-offset:-2px; }
.pile-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.pile-identity { display:flex; min-width:0; flex-direction:column; gap:2px; }
.pile-identity strong { color:var(--wh-text); font-size:14px; font-weight:600; line-height:20px; }
.pile-identity>span { overflow:hidden; color:var(--wh-muted); font-size:12px; line-height:18px; text-overflow:ellipsis; white-space:nowrap; }
.pile-count { flex:none; color:var(--wh-muted); font-size:11px; line-height:20px; font-variant-numeric:tabular-nums; text-align:right; white-space:nowrap; }
.pile-divider { height:1px; margin:10px 0; background:var(--wh-border); }
.sku-preview { display:flex; flex-direction:column; gap:8px; }
.sku-row { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.sku-copy { display:flex; min-width:0; flex-direction:column; gap:2px; }
.sku-name { overflow:hidden; color:var(--wh-text); font-size:12px; line-height:18px; text-overflow:ellipsis; white-space:nowrap; }
.sku-spec { overflow:hidden; color:var(--wh-muted); font-size:11px; line-height:18px; text-overflow:ellipsis; white-space:nowrap; }
.sku-row>strong { flex:none; color:var(--wh-text); font-size:12px; font-weight:500; line-height:18px; font-variant-numeric:tabular-nums; text-align:right; }
.more-skus,.empty-pile { color:var(--wh-muted); font-size:12px; line-height:20px; }
.sidebar-state { display:flex; min-height:128px; flex:1; flex-direction:column; align-items:center; justify-content:center; gap:12px; padding:16px; color:var(--wh-muted); font-size:13px; line-height:20px; text-align:center; }
.compact-state { min-height:110px; }
.state-icon { color:var(--wh-muted); }
.error-state { color:var(--wh-danger); }
.error-state button { min-height:36px; padding:6px 12px; border:1px solid var(--wh-control); border-radius:6px; color:var(--wh-text); background:var(--wh-surface); cursor:pointer; }
.is-spinning { animation:spin 1s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
@media (max-width:1180px) { .inventory-sidebar { width:272px; min-width:272px; } }
@media (max-width:800px) { .inventory-sidebar { width:100%; min-width:0; height:240px; flex:none; padding:16px; gap:10px; border-right:0; border-bottom:1px solid var(--wh-border); } .sidebar-header p { display:none; } .pile-list { flex-direction:row; } .pile-card { width:252px; min-height:88px; } .pile-divider,.sku-preview,.empty-pile { display:none; } .search-field { height:40px; } }
@media (prefers-reduced-motion:reduce) { .is-spinning { animation:none; } }
</style>
