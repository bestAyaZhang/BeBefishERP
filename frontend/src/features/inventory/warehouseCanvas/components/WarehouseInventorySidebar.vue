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
      <input v-model="searchQuery" data-testid="inventory-pile-search" type="search" placeholder="搜索货物堆或 SKU">
    </label>

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
.inventory-sidebar { display:flex; width:320px; min-width:320px; min-height:0; flex-direction:column; gap:14px; padding:16px; overflow:hidden; border:1px solid #e5eaf2; border-radius:8px; background:#fff; box-shadow:0 2px 10px rgba(15,35,65,.04); }
.sidebar-header h2 { margin:0; color:#14213d; font-size:16px; font-weight:650; line-height:24px; }
.sidebar-header p { margin:3px 0 0; color:#8491a7; font-size:12px; line-height:18px; }
.search-field { display:flex; height:38px; flex:0 0 auto; align-items:center; gap:8px; padding:0 11px; border:1px solid #dfe5ee; border-radius:7px; color:#8794a8; background:#fff; transition:border-color .16s ease,box-shadow .16s ease; }
.search-field:focus-within { border-color:#6f98f3; box-shadow:0 0 0 3px rgba(47,103,232,.1); }
.search-field input { width:100%; border:0; outline:0; color:#23324f; font:inherit; font-size:13px; background:transparent; }
.search-field input::placeholder { color:#a5afbe; }
.pile-list { display:flex; min-height:0; flex:1; flex-direction:column; gap:10px; padding-right:3px; overflow-y:auto; scrollbar-width:thin; scrollbar-color:#d5dce7 transparent; }
.pile-card { display:flex; width:100%; min-height:126px; flex:0 0 auto; flex-direction:column; padding:12px; border:1px solid #e1e7f0; border-radius:8px; color:inherit; text-align:left; background:#fff; cursor:pointer; transition:border-color .16s ease,box-shadow .16s ease,transform .16s ease; }
.pile-card:hover { border-color:#a9c0f5; box-shadow:0 4px 12px rgba(35,77,162,.08); transform:translateY(-1px); }
.pile-card.is-selected { border-color:#2f67e8; box-shadow:0 0 0 2px rgba(47,103,232,.12); }
.pile-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.pile-identity { display:flex; min-width:0; flex-direction:column; }
.pile-identity strong { color:#183257; font-size:14px; font-weight:700; line-height:20px; }
.pile-identity>span { overflow:hidden; color:#75839a; font-size:11px; line-height:17px; text-overflow:ellipsis; white-space:nowrap; }
.pile-count { flex:0 0 auto; padding:3px 7px; border-radius:999px; color:#426189; font-size:11px; font-weight:600; line-height:17px; background:#f1f5fb; }
.pile-divider { height:1px; margin:9px 0 8px; background:#edf0f5; }
.sku-preview { display:flex; flex-direction:column; gap:6px; }
.sku-row { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.sku-copy { display:flex; min-width:0; flex-direction:column; }
.sku-name { overflow:hidden; color:#384860; font-size:11px; line-height:16px; text-overflow:ellipsis; white-space:nowrap; }
.sku-spec { overflow:hidden; color:#9aa5b5; font-size:10px; line-height:14px; text-overflow:ellipsis; white-space:nowrap; }
.sku-row>strong { flex:0 0 auto; color:#223553; font-size:11px; font-weight:650; line-height:16px; }
.more-skus,.empty-pile { color:#99a5b6; font-size:11px; line-height:18px; }
.empty-pile { display:flex; flex:1; align-items:center; justify-content:center; color:#a2acba; }
.sidebar-state { display:flex; min-height:128px; flex:1; flex-direction:column; align-items:center; justify-content:center; gap:9px; color:#8290a6; font-size:12px; text-align:center; }
.compact-state { min-height:110px; }
.state-icon { color:#9ca8b9; }
.error-state { color:#b3484e; }
.error-state .state-icon { color:#d35d63; }
.error-state button { padding:5px 12px; border:1px solid #dbe2ed; border-radius:6px; color:#405574; background:#fff; cursor:pointer; }
.is-spinning { animation:spin 1s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
@media (max-width:1180px) { .inventory-sidebar { width:292px; min-width:292px; } }
</style>
