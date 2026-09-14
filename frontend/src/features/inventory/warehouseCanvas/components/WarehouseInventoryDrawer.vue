<script setup lang="ts">
import { computed, ref } from 'vue'
import { Box, Layers3, MapPin, PackageOpen, X } from 'lucide-vue-next'
import type { PlannerPalletGroup, PlannerZone } from '../warehousePlannerScene'
import type { WarehouseInventoryAllocation, WarehouseInventoryLayout } from '../warehouseInventoryService'

const props = withDefaults(defineProps<{
  inventory: WarehouseInventoryLayout | null
  pallet: PlannerPalletGroup | null
  zone: PlannerZone | null
  open: boolean
  zoneIds?: readonly string[]
  canEdit?: boolean
  saving?: boolean
  allocationError?: string
}>(), {
  canEdit: false,
  saving: false,
  allocationError: '',
})
const emit = defineEmits<{
  close: []
  allocate: [input: { palletId: string; skuId: number; units: number }]
}>()
const number = new Intl.NumberFormat('zh-CN')
const addingSku = ref(false)
const selectedSkuId = ref<number | null>(null)
const allocationUnits = ref('')

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
  if (!props.pallet || !props.canEdit || !unallocated.value.length || props.saving) return
  selectedSkuId.value = unallocated.value[0]!.skuId
  allocationUnits.value = ''
  addingSku.value = true
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
        <div><Layers3 :size="17" /><span>SKU 种类</span><strong>{{ items.length }}</strong></div>
        <div><Box :size="17" /><span>实际库存</span><strong>{{ number.format(totalUnits) }} 个</strong></div>
        <div v-if="zone"><MapPin :size="17" /><span>货物堆</span><strong>{{ pileCount }} 个</strong></div>
      </section>

      <section v-if="pallet && canEdit" class="allocation-panel">
        <div class="allocation-heading">
          <div><span>库存分配</span><small>从待分配库存添加到当前货物堆</small></div>
          <button data-testid="inventory-add-sku" type="button" :disabled="saving || !unallocated.length" @click="openAllocationForm">添加 SKU</button>
        </div>
        <p v-if="!unallocated.length" class="allocation-hint">暂无待分配库存</p>
        <form v-if="addingSku" data-testid="inventory-allocation-form" class="allocation-form" @submit.prevent="submitAllocation">
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
        </form>
      </section>

      <div class="sku-heading"><span>SKU 明细</span><small>库存单位：个</small></div>
      <div v-if="items.length" class="sku-list">
        <article v-for="item in items" :key="item.skuId" data-testid="inventory-detail-sku" class="sku-card">
          <div class="sku-icon"><PackageOpen :size="19" /></div>
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
.inventory-drawer{position:absolute;z-index:60;top:14px;right:14px;bottom:14px;width:360px;display:flex;flex-direction:column;border:1px solid #e2e7ef;border-radius:12px;background:#fff;color:#26354d;box-shadow:0 14px 40px #25314d24;overflow:hidden}.inventory-drawer>header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 18px 15px;border-bottom:1px solid #edf0f5}.inventory-drawer>header span{color:#8592a5;font-size:10px;letter-spacing:.08em}.inventory-drawer h2{margin:4px 0 0;font-size:17px}.inventory-drawer>header button{display:grid;place-items:center;width:31px;height:31px;border:0;border-radius:7px;background:#f4f6fa;color:#66748a}.drawer-summary{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;padding:14px 16px}.drawer-summary div{display:grid;grid-template-columns:20px 1fr;align-items:center;padding:10px;border-radius:8px;background:#f7f9fc;color:#718097}.drawer-summary span{font-size:10px}.drawer-summary strong{grid-column:2;font-size:14px;color:#273750}.allocation-panel{margin:0 16px 12px;padding:11px;border:1px solid #e5ebf3;border-radius:9px;background:#fafcff}.allocation-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.allocation-heading>div{display:grid;gap:2px}.allocation-heading span{font-size:12px;font-weight:700}.allocation-heading small,.allocation-hint{color:#8b97a8;font-size:10px}.allocation-heading button,.allocation-form>button{border:0;border-radius:6px;background:#5e78b8;color:#fff;font-size:11px;font-weight:700;cursor:pointer}.allocation-heading button{padding:7px 9px}.allocation-hint{margin:8px 0 0}.allocation-form{display:grid;gap:8px;margin-top:10px;padding-top:10px;border-top:1px solid #e8edf4}.allocation-form label{display:grid;gap:4px;color:#718097;font-size:10px}.allocation-form select,.allocation-form input{width:100%;box-sizing:border-box;border:1px solid #dce3ed;border-radius:6px;background:#fff;padding:7px 8px;color:#31415a;font:11px Inter,sans-serif}.allocation-meta{display:flex;justify-content:space-between;gap:6px;color:#6d7b90;font-size:10px}.allocation-form>button{padding:8px}.allocation-heading button:disabled,.allocation-form :disabled{cursor:not-allowed;opacity:.52}.allocation-validation,.allocation-error{margin:0;font-size:10px}.allocation-validation{color:#bc6b2c}.allocation-error{color:#b44949}.sku-heading{display:flex;justify-content:space-between;padding:6px 18px 10px;color:#69778b;font-size:12px}.sku-heading small{color:#99a3b2}.sku-list{padding:0 12px 14px;overflow:auto}.sku-card{display:grid;grid-template-columns:36px 1fr auto;gap:10px;align-items:center;padding:12px 7px;border-top:1px solid #eef1f5}.sku-icon{display:grid;place-items:center;width:34px;height:34px;border-radius:8px;background:#eef2fb;color:#5e78b8}.sku-copy{min-width:0}.sku-copy strong{display:block;max-width:170px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.sku-copy code{display:block;margin-top:4px;color:#6d7b90;font:10px/1.2 Inter,sans-serif}.sku-copy small{display:block;margin-top:4px;color:#9aa4b3;font-size:9px}.sku-quantity{text-align:right}.sku-quantity strong{display:block;font-size:12px}.sku-quantity small{display:block;margin-top:5px;color:#7f8b9e;font-size:10px}.drawer-empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;color:#8a96a8}.drawer-empty strong{font-size:13px}.drawer-empty span{font-size:11px}.inventory-drawer>footer{margin-top:auto;padding:12px 18px;border-top:1px solid #eef1f5;color:#9ba5b4;font-size:10px}.drawer-enter-active,.drawer-leave-active{transition:transform .18s ease,opacity .18s ease}.drawer-enter-from,.drawer-leave-to{transform:translateX(20px);opacity:0}
</style>
