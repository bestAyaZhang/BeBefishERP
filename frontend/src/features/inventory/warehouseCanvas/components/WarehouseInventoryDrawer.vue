<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PackageOpen, X } from 'lucide-vue-next'
import type { PlannerPalletGroup, PlannerZone } from '../warehousePlannerScene'
import type { WarehouseInventoryAllocation, WarehouseInventoryLayout, WarehouseSkuCandidate } from '../warehouseInventoryService'

const props = withDefaults(defineProps<{
  inventory: WarehouseInventoryLayout | null
  pallet: PlannerPalletGroup | null
  zone: PlannerZone | null
  open: boolean
  zoneIds?: readonly string[]
  canEdit?: boolean
  saving?: boolean
  allocationError?: string
  allocationSuccessGeneration?: number
  lookupSkuCandidates?: (keyword: string) => Promise<WarehouseSkuCandidate[]>
}>(), {
  canEdit: false,
  saving: false,
  allocationError: '',
  allocationSuccessGeneration: 0,
})
const emit = defineEmits<{
  close: []
  allocate: [input: { palletId: string; skuId: number; units: number }]
}>()
const number = new Intl.NumberFormat('zh-CN')
const addingSku = ref(false)
const selectedSkuId = ref<number | null>(null)
const allocationUnits = ref('')
const linkingSku = ref(false)
const linkSearch = ref('')
const linkCandidates = ref<WarehouseSkuCandidate[]>([])
const linkSkuId = ref<number | null>(null)
const linkLoading = ref(false)
const linkError = ref('')
let linkRequestGeneration = 0
watch(
  [() => props.open, () => props.pallet?.id, () => props.inventory?.warehouseId, () => props.allocationSuccessGeneration],
  () => {
    addingSku.value = false
    selectedSkuId.value = null
    allocationUnits.value = ''
    linkingSku.value = false
    linkSearch.value = ''
    linkCandidates.value = []
    linkSkuId.value = null
    linkError.value = ''
    linkRequestGeneration += 1
  },
)

const title = computed(() => props.pallet?.name ?? props.zone?.label ?? (props.open ? '待分配库存' : '库存详情'))
const source = computed(() => {
  if (!props.inventory) return []
  if (props.pallet) return props.inventory.allocations.filter(item => item.palletId === props.pallet!.id)
  if (props.zone) return props.inventory.allocations.filter(item => item.zoneId === props.zone!.id && item.palletId !== 'UNALLOCATED')
  return props.inventory.allocations.filter(item => item.palletId === 'UNALLOCATED'
    || !item.zoneId
    || (props.zoneIds ? !props.zoneIds.includes(item.zoneId) : false))
})
const items = computed(() => {
  const grouped = new Map<number, WarehouseInventoryAllocation>()
  for (const item of source.value) {
    const previous = grouped.get(item.skuId)
    grouped.set(item.skuId, previous ? { ...previous, units: previous.units + item.units } : { ...item })
  }
  return [...grouped.values()].sort((a, b) => b.units - a.units)
})
const totalUnits = computed(() => items.value.reduce((sum, item) => sum + item.units, 0))
const pileCount = computed(() => new Set(source.value.filter(item => item.palletId !== 'UNALLOCATED').map(item => item.palletId)).size)
const unallocated = computed(() => props.inventory?.allocations
  .filter(item => item.palletId === 'UNALLOCATED' && item.units > 0) ?? [])
const selectedUnallocated = computed(() => unallocated.value.find(item => item.skuId === selectedSkuId.value) ?? null)
const selectedAvailable = computed(() => selectedUnallocated.value?.units ?? 0)
const allocationIsValid = computed(() => {
  const units = Number(allocationUnits.value)
  return Number.isInteger(units) && units > 0 && units <= selectedAvailable.value
})
const allocationValidationMessage = computed(() => allocationIsValid.value ? '' : '请输入不超过可分配库存的正整数个数')

function openAllocationForm() {
  if (!props.pallet || !props.canEdit || props.saving) return
  selectedSkuId.value = unallocated.value[0]?.skuId ?? null
  allocationUnits.value = ''
  addingSku.value = true
  if (!unallocated.value.length) openLinkForm()
}

function openLinkForm() {
  linkingSku.value = true
  void loadSkuCandidates()
}

async function loadSkuCandidates() {
  if (!props.lookupSkuCandidates) return
  const generation = ++linkRequestGeneration
  linkLoading.value = true
  linkError.value = ''
  linkCandidates.value = []
  linkSkuId.value = null
  try {
    const candidates = await props.lookupSkuCandidates(linkSearch.value.trim())
    if (generation !== linkRequestGeneration) return
    linkCandidates.value = candidates.filter(candidate => !props.inventory?.allocations.some(item => (
      item.palletId === props.pallet?.id && item.skuId === candidate.skuId
    )))
    linkSkuId.value = linkCandidates.value[0]?.skuId ?? null
  } catch (cause) {
    if (generation === linkRequestGeneration) linkError.value = cause instanceof Error ? cause.message : 'SKU 加载失败'
  } finally {
    if (generation === linkRequestGeneration) linkLoading.value = false
  }
}

function submitLink() {
  if (!props.pallet || !props.canEdit || props.saving || linkLoading.value || linkError.value || linkSkuId.value === null) return
  emit('allocate', { palletId: props.pallet.id, skuId: linkSkuId.value, units: 0 })
}

function submitAllocation() {
  if (!props.pallet || !allocationIsValid.value || props.saving || selectedSkuId.value === null) return
  emit('allocate', { palletId: props.pallet.id, skuId: selectedSkuId.value, units: Number(allocationUnits.value) })
}

function caseText(item: WarehouseInventoryAllocation) {
  if (!item.unitsPerCase) return null
  const cases = Math.floor(item.units / item.unitsPerCase)
  const loose = item.units % item.unitsPerCase
  return loose ? `${cases} 箱 + ${number.format(loose)} 个` : `${cases} 箱`
}
</script>

<template>
  <Transition name="drawer">
    <aside v-if="open" class="inventory-drawer" aria-label="实际库存详情">
      <header>
        <div><span>{{ pallet ? '货物堆实际库存' : zone ? '区域实际库存' : '待分配实际库存' }}</span><h2 data-testid="inventory-detail-title">{{ title }}</h2></div>
        <button type="button" aria-label="关闭库存详情" @click="emit('close')"><X :size="19" /></button>
      </header>

      <section class="drawer-summary">
        <div><span>SKU 种类</span><strong>{{ items.length }}</strong></div>
        <div><span>实际库存</span><strong>{{ number.format(totalUnits) }} 个</strong></div>
        <div v-if="zone"><span>货物堆</span><strong>{{ pileCount }} 个</strong></div>
      </section>

      <section v-if="pallet && canEdit" class="allocation-panel">
        <div class="allocation-heading">
          <div><span>商品与库存分配</span><small>有库存可分配数量；零库存可先关联 SKU</small></div>
          <button data-testid="inventory-add-sku" type="button" :disabled="saving" @click="openAllocationForm">添加 SKU</button>
        </div>
        <p v-if="!unallocated.length" class="allocation-hint">暂无待分配库存，可关联零库存 SKU</p>
        <form v-if="addingSku && !linkingSku" data-testid="inventory-allocation-form" class="allocation-form" @submit.prevent="submitAllocation">
          <label>SKU
            <select v-model.number="selectedSkuId" data-testid="inventory-allocation-sku" :disabled="saving">
              <option v-for="item in unallocated" :key="item.skuId" :value="item.skuId">{{ item.skuCode }} · {{ item.productName }}</option>
            </select>
          </label>
          <div class="allocation-meta">
            <span>可分配 {{ number.format(selectedAvailable) }} 个</span>
            <span>{{ selectedUnallocated?.unitsPerCase ? `${number.format(selectedUnallocated.unitsPerCase)} 个/箱（仅供参考）` : '无整箱规格（仅供参考）' }}</span>
          </div>
          <label>分配数量（个）
            <input v-model="allocationUnits" data-testid="inventory-allocation-units" type="number" min="1" step="1" :max="selectedAvailable" inputmode="numeric" :disabled="saving">
          </label>
          <p v-if="allocationValidationMessage" data-testid="inventory-allocation-validation" class="allocation-validation">{{ allocationValidationMessage }}</p>
          <p v-if="allocationError" class="allocation-error" role="alert">{{ allocationError }}</p>
          <button data-testid="inventory-allocation-submit" type="submit" :disabled="saving || !allocationIsValid">{{ saving ? '分配中…' : '确认分配' }}</button>
          <button v-if="lookupSkuCandidates" data-testid="inventory-link-mode" type="button" :disabled="saving" @click="openLinkForm">关联零库存 SKU</button>
        </form>
        <div v-if="addingSku && linkingSku" class="allocation-form">
          <form class="allocation-form" @submit.prevent="loadSkuCandidates">
            <label>搜索商品或 SKU
              <input v-model="linkSearch" type="search" placeholder="输入商品名称或 SKU 编码" :disabled="saving || linkLoading">
            </label>
            <button type="submit" :disabled="saving || linkLoading">搜索</button>
          </form>
          <label>选择 SKU
            <select v-model.number="linkSkuId" data-testid="inventory-link-sku" :disabled="saving || linkLoading || !linkCandidates.length">
              <option v-for="candidate in linkCandidates" :key="candidate.skuId" :value="candidate.skuId">{{ candidate.skuCode }} · {{ candidate.productName }}</option>
            </select>
          </label>
          <p class="allocation-hint">仅关联商品位置，实际库存仍为 0 个；入库后需另行分配库存。</p>
          <p v-if="linkLoading" class="allocation-hint">正在加载 SKU…</p>
          <p v-else-if="!linkCandidates.length && !linkError" class="allocation-hint">未找到可关联的 SKU，可输入更具体的名称或编码重试</p>
          <p v-if="linkError" class="allocation-error" role="alert">{{ linkError }}</p>
          <p v-if="allocationError" class="allocation-error" role="alert">{{ allocationError }}</p>
          <button data-testid="inventory-link-submit" type="button" :disabled="saving || linkLoading || !!linkError || linkSkuId === null" @click="submitLink">{{ saving ? '关联中…' : '关联到货物堆' }}</button>
        </div>
      </section>

      <div class="sku-heading"><span>SKU 明细</span><small>库存单位：个</small></div>
      <div v-if="items.length" class="sku-list">
        <article v-for="item in items" :key="item.skuId" data-testid="inventory-detail-sku" class="sku-card">
          <div class="sku-copy"><strong>{{ item.productName }}</strong><code>{{ item.skuCode }}</code><small v-if="item.skuName || item.specification">{{ [item.skuName, item.specification].filter(Boolean).join(' · ') }}</small></div>
          <div class="sku-quantity"><strong>{{ number.format(item.units) }} 个</strong><small v-if="caseText(item)">{{ caseText(item) }}</small></div>
        </article>
      </div>
      <div v-else class="drawer-empty"><PackageOpen :size="28" /><strong>暂无实际库存</strong><span>该{{ pallet ? '货物堆' : zone ? '区域' : '分类' }}还没有已记录的 SKU</span></div>
      <footer>数据来自库存台账与位置库存记录</footer>
    </aside>
  </Transition>
</template>

<style scoped>
.inventory-drawer { position:absolute; z-index:60; top:12px; right:12px; bottom:12px; width:360px; display:flex; flex-direction:column; overflow-y:auto; border:1px solid var(--wh-border); border-radius:12px; background:var(--wh-surface); color:var(--wh-text); box-shadow:0 12px 32px #1c2d5012; }
.inventory-drawer>header { display:flex; flex:none; align-items:flex-start; justify-content:space-between; gap:16px; padding:20px; border-bottom:1px solid var(--wh-border); }
.inventory-drawer>header span { color:var(--wh-muted); font-size:12px; }
.inventory-drawer h2 { margin:5px 0 0; font-size:18px; font-weight:600; }
.inventory-drawer>header button { display:grid; flex:none; place-items:center; width:32px; height:32px; border:0; border-radius:6px; background:var(--wh-raised); color:var(--wh-muted); cursor:pointer; }
.drawer-summary { display:flex; flex:none; gap:24px; padding:20px; }
.drawer-summary div { display:grid; gap:6px; color:var(--wh-muted); }
.drawer-summary svg { display:none; }
.drawer-summary span { font-size:12px; }
.drawer-summary strong { font-size:22px; line-height:30px; color:var(--wh-text); font-weight:500; font-variant-numeric:tabular-nums; }
.allocation-panel { flex:none; margin:0 20px 16px; padding:16px; border-radius:6px; background:var(--wh-raised); }
.allocation-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.allocation-heading>div { display:grid; gap:4px; }
.allocation-heading span { font-size:13px; font-weight:600; }
.allocation-heading small,.allocation-hint { color:var(--wh-muted); font-size:12px; line-height:19px; }
.allocation-heading button,.allocation-form>button { flex:none; min-height:34px; border:0; border-radius:6px; background:var(--wh-accent); color:var(--wh-surface); font-size:12px; cursor:pointer; padding:6px 10px; }
.allocation-hint { margin:8px 0 0; }
.allocation-form { display:grid; gap:12px; margin-top:14px; padding-top:14px; border-top:1px solid var(--wh-border); }
.allocation-form label { display:grid; gap:6px; color:var(--wh-muted); font-size:12px; }
.allocation-form select,.allocation-form input { width:100%; min-height:38px; box-sizing:border-box; border:1px solid var(--wh-control); border-radius:6px; background:var(--wh-surface); padding:8px; color:var(--wh-text); font:inherit; font-size:13px; }
.allocation-form select:focus-visible,.allocation-form input:focus-visible { outline:2px solid var(--wh-accent); outline-offset:2px; }
.allocation-meta { display:flex; flex-wrap:wrap; justify-content:space-between; gap:6px; color:var(--wh-muted); font-size:11px; }
.allocation-heading button:disabled,.allocation-form :disabled { cursor:not-allowed; opacity:.55; }
.allocation-validation,.allocation-error { margin:0; font-size:12px; line-height:19px; }
.allocation-validation { color:#875a1f; }
.allocation-error { color:var(--wh-danger); }
.sku-heading { display:flex; flex:none; justify-content:space-between; padding:0 20px 8px; color:var(--wh-muted); font-size:12px; }
.sku-heading small { font-size:12px; }
.sku-list { flex:1 0 auto; padding:0 20px 16px; }
.sku-card { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:12px; align-items:start; padding:16px 0; border-top:1px solid var(--wh-border); }
.sku-copy { min-width:0; }
.sku-copy strong { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:13px; font-weight:500; }
.sku-copy code { display:block; margin-top:5px; color:var(--wh-muted); font:12px/1.5 var(--wh-font); }
.sku-copy small { display:block; margin-top:4px; color:var(--wh-muted); font-size:12px; line-height:18px; }
.sku-quantity { text-align:right; white-space:nowrap; font-variant-numeric:tabular-nums; }
.sku-quantity strong { display:block; font-size:14px; font-weight:600; }
.sku-quantity small { display:block; margin-top:6px; color:var(--wh-muted); font-size:12px; }
.drawer-empty { flex:1; min-height:160px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; padding:20px; color:var(--wh-muted); }
.drawer-empty strong { color:var(--wh-text); font-size:14px; font-weight:500; }
.drawer-empty span { font-size:12px; }
.inventory-drawer>footer { flex:none; margin-top:auto; padding:14px 20px; border-top:1px solid var(--wh-border); color:var(--wh-muted); font-size:11px; }
.drawer-enter-active,.drawer-leave-active { transition:transform .18s ease-out,opacity .18s ease-out; }
.drawer-enter-from,.drawer-leave-to { transform:translateX(16px); opacity:0; }
@media (max-width:800px) { .inventory-drawer { inset:8px; width:auto; max-width:420px; margin-left:auto; } }
@media (max-width:480px) { .inventory-drawer>header button,.allocation-heading button,.allocation-form>button { min-height:44px; } }
@media (prefers-reduced-motion:reduce) { .drawer-enter-active,.drawer-leave-active { transition:none; } }
</style>
