<script setup lang="ts">
import { computed } from 'vue'
import { warehousePlannerScene } from '../warehousePlannerScene'
import type { PlannerPalletGroup, PlannerRect } from '../warehousePlannerScene'
import WarehousePlannerInspector from './WarehousePlannerInspector.vue'

const props = defineProps<{
  gridSnapping: boolean
  measurementEnabled: boolean
  selectedPalletId: string | null
}>()

const emit = defineEmits<{
  'select-pallet': [id: string | null]
}>()

type RulerTickKind = 'minor' | 'meter' | 'major'
type RulerTick = {
  edge: 'start' | 'middle' | 'end'
  kind: RulerTickKind
  position: number
  value: number
}

function createRulerTicks(maxMeters: number): RulerTick[] {
  return Array.from({ length: maxMeters * 2 + 1 }, (_, index) => ({
    edge: index === 0 ? 'start' : index === maxMeters * 2 ? 'end' : 'middle',
    kind: index % 10 === 0 ? 'major' : index % 2 === 0 ? 'meter' : 'minor',
    position: index / (maxMeters * 2) * 100,
    value: index / 2,
  }))
}

const horizontalRulerTicks = createRulerTicks(60)
const verticalRulerTicks = createRulerTicks(40)
const horizontalRulerLabels = horizontalRulerTicks.filter((tick) => tick.kind === 'major')
const verticalRulerLabels = verticalRulerTicks.filter((tick) => tick.kind === 'major')
const selectedPallet = computed(() => warehousePlannerScene.palletGroups.find((item) => item.id === props.selectedPalletId))

function rectStyle(rect: PlannerRect) {
  return {
    left: `${rect.left}%`,
    top: `${rect.top}%`,
    width: `${rect.width}%`,
    height: `${rect.height}%`,
  }
}

function palletStyle(pallet: PlannerPalletGroup) {
  return {
    ...rectStyle(pallet),
    '--pallet-columns': String(pallet.columns),
    '--pallet-rows': String(pallet.rows),
  }
}

function palletTotalUnits(pallet: PlannerPalletGroup) {
  return pallet.contents.reduce((total, item) => total + item.units, 0)
}

function inspectorStyle(pallet: PlannerPalletGroup) {
  const left = Math.min(pallet.left + pallet.width + 2, 72)
  const top = Math.max(14, pallet.top - 3)
  return { left: `${left}%`, top: `${top}%` }
}
</script>

<template>
  <section
    data-testid="warehouse-blueprint-scene"
    class="blueprint-scene"
    :class="{ 'grid-muted': !gridSnapping }"
    :data-measuring="measurementEnabled ? 'true' : 'false'"
    :data-grid-snapping="gridSnapping ? 'true' : 'false'"
    aria-label="一号仓平面规划画布"
  >
    <div data-testid="planner-ruler-x" class="ruler ruler-x" role="img" aria-label="横向标尺，0 至 60 米，最小刻度 0.5 米">
      <i
        v-for="tick in horizontalRulerTicks"
        :key="tick.value"
        data-testid="planner-ruler-x-tick"
        class="ruler-tick"
        :class="`tick-${tick.kind}`"
        :data-tick-kind="tick.kind"
        :style="{ left: `${tick.position}%` }"
        aria-hidden="true"
      />
      <span
        v-for="tick in horizontalRulerLabels"
        :key="`x-label-${tick.value}`"
        class="ruler-label"
        :data-edge="tick.edge"
        :style="{ left: `${tick.position}%` }"
      >{{ tick.value }}</span>
      <span class="ruler-unit">(m)</span>
    </div>
    <div data-testid="planner-ruler-y" class="ruler ruler-y" role="img" aria-label="纵向标尺，0 至 40 米，最小刻度 0.5 米">
      <i
        v-for="tick in verticalRulerTicks"
        :key="tick.value"
        data-testid="planner-ruler-y-tick"
        class="ruler-tick"
        :class="`tick-${tick.kind}`"
        :data-tick-kind="tick.kind"
        :style="{ top: `${tick.position}%` }"
        aria-hidden="true"
      />
      <span
        v-for="tick in verticalRulerLabels"
        :key="`y-label-${tick.value}`"
        class="ruler-label"
        :data-edge="tick.edge"
        :style="{ top: `${tick.position}%` }"
      >{{ tick.value }}</span>
      <span class="ruler-unit">(m)</span>
    </div>

    <div class="drawing-board">
      <div class="warehouse-shell" aria-hidden="true"><div class="warehouse-interior" /></div>

      <article
        v-for="door in warehousePlannerScene.doors"
        :key="door.id"
        :data-testid="`planner-loading-door-${door.id}`"
        class="loading-door"
        :style="rectStyle(door)"
      >
        <strong>{{ door.label }}</strong>
        <span>宽 {{ door.widthMeters.toFixed(1) }}m</span>
        <i aria-hidden="true" />
      </article>

      <article
        v-for="zone in warehousePlannerScene.zones"
        :key="zone.id"
        class="operation-zone"
        :class="`tone-${zone.tone}`"
        :style="rectStyle(zone)"
      >
        <strong>{{ zone.label }}</strong>
        <span>{{ zone.id === 'zone-buffer' ? '待分配货物' : zone.id === 'zone-receiving' ? '入库交接' : '出库集货' }}</span>
      </article>

      <div
        v-for="aisle in warehousePlannerScene.aisles"
        :key="aisle.id"
        class="forklift-aisle"
        :class="aisle.direction"
        :style="rectStyle(aisle)"
      >
        <span class="aisle-arrow" aria-hidden="true">←</span>
        <strong>{{ aisle.label }}</strong>
        <span class="aisle-arrow" aria-hidden="true">→</span>
      </div>

      <article v-for="room in warehousePlannerScene.rooms" :key="room.id" class="utility-room" :style="rectStyle(room)">
        <strong>{{ room.label }}</strong><span>{{ room.detail }}</span>
      </article>

      <div
        v-for="lane in warehousePlannerScene.fireLanes"
        :key="lane.id"
        class="fire-lane"
        :style="{ ...rectStyle(lane), transform: `rotate(${lane.rotation}deg)` }"
      ><span>消防留空区<br />宽 2.0m</span></div>

      <i
        v-for="column in warehousePlannerScene.columns"
        :key="column.id"
        class="structure-column"
        :style="{ left: `${column.left}%`, top: `${column.top}%` }"
        aria-hidden="true"
      />

      <button
        v-for="pallet in warehousePlannerScene.palletGroups"
        :key="pallet.id"
        :data-testid="`planner-pallet-${pallet.id}`"
        class="pallet-group"
        :class="{ selected: selectedPalletId === pallet.id }"
        :data-selected="selectedPalletId === pallet.id ? 'true' : 'false'"
        :aria-pressed="selectedPalletId === pallet.id"
        :style="palletStyle(pallet)"
        type="button"
        :aria-label="`${pallet.name}，地面箱子堆砌，${pallet.contents.length} 种商品，共 ${palletTotalUnits(pallet)} 个`"
        @click="emit('select-pallet', pallet.id)"
      >
        <span class="sr-only">{{ pallet.name }} · {{ pallet.contents.length }} 种商品 · 共 {{ palletTotalUnits(pallet) }} 个</span>
        <template v-if="selectedPalletId === pallet.id">
          <i v-for="handle in 8" :key="handle" class="selection-handle" :class="`handle-${handle}`" aria-hidden="true" />
          <i class="rotation-handle" aria-hidden="true">↻</i>
        </template>
      </button>

      <template v-if="measurementEnabled && selectedPallet">
        <div data-testid="planner-measurement-width" class="measurement width-measure" :style="{ left: `${selectedPallet.left}%`, top: `${selectedPallet.top - 2}%`, width: `${selectedPallet.width}%` }">
          <span>{{ selectedPallet.lengthMeters }}m</span>
        </div>
        <div data-testid="planner-measurement-height" class="measurement height-measure" :style="{ left: `${selectedPallet.left + selectedPallet.width + 1}%`, top: `${selectedPallet.top}%`, height: `${selectedPallet.height}%` }">
          <span>{{ selectedPallet.widthMeters }}m</span>
        </div>
      </template>

      <WarehousePlannerInspector
        v-if="selectedPallet"
        class="scene-inspector"
        :style="inspectorStyle(selectedPallet)"
        :pallet="selectedPallet"
        @close="emit('select-pallet', null)"
      />

      <aside data-testid="planner-minimap" class="planner-minimap" aria-label="仓库小地图">
        <div class="minimap-shell">
          <i v-for="pallet in warehousePlannerScene.palletGroups.slice(0, 18)" :key="pallet.id" :style="rectStyle(pallet)" />
          <span aria-hidden="true" />
        </div>
      </aside>
    </div>
    <footer data-testid="planner-coordinate-status" class="coordinate-status">
      <span>X&nbsp; {{ selectedPallet?.xMeters ?? 32.4 }}m</span>
      <span>Y&nbsp; {{ selectedPallet?.yMeters ?? 21.8 }}m</span>
      <span class="status-divider" aria-hidden="true" />
      <span>比例&nbsp; 1:100</span>
      <span>缩放&nbsp; 100%</span>
      <strong class="north-indicator" aria-label="北向">N<span>▲</span></strong>
    </footer>
  </section>
</template>

<style scoped>
.blueprint-scene { position: relative; width: 100%; min-width: 980px; height: 100%; min-height: 720px; padding: 36px 28px 28px 48px; background: #f9fbfd; color: #25314d; }
.drawing-board { position: relative; width: 100%; height: 100%; overflow: hidden; border: 1px solid #eef2f6; background-color: #fbfcfd; background-image: linear-gradient(#e8edf3 1px, transparent 1px), linear-gradient(90deg, #e8edf3 1px, transparent 1px), linear-gradient(#f1f4f8 1px, transparent 1px), linear-gradient(90deg, #f1f4f8 1px, transparent 1px); background-size: 40px 40px, 40px 40px, 8px 8px, 8px 8px; }
.grid-muted .drawing-board { background-image: linear-gradient(#eef2f6 1px, transparent 1px), linear-gradient(90deg, #eef2f6 1px, transparent 1px); background-size: 40px 40px; }
.ruler { position: absolute; z-index: 2; color: #536176; font: 10px/1 Inter,sans-serif; }
.ruler-x { top: 7px; left: 48px; right: 28px; height: 28px; border-bottom: 1px solid #8f9baa; }
.ruler-y { top: 36px; bottom: 28px; left: 8px; width: 39px; border-right: 1px solid #8f9baa; }
.ruler-tick { position: absolute; display: block; background: #98a4b2; pointer-events: none; }
.ruler-x .ruler-tick { bottom: 0; width: 1px; height: 4px; }
.ruler-x .tick-meter { height: 8px; background: #7f8b9a; }
.ruler-x .tick-major { height: 14px; background: #667384; }
.ruler-y .ruler-tick { right: 0; width: 4px; height: 1px; }
.ruler-y .tick-meter { width: 8px; background: #7f8b9a; }
.ruler-y .tick-major { width: 14px; background: #667384; }
.ruler-label { position: absolute; color: #4b586b; font-variant-numeric: tabular-nums; white-space: nowrap; }
.ruler-x .ruler-label { top: 0; transform: translateX(-50%); }
.ruler-x .ruler-label[data-edge='start'] { transform: none; }
.ruler-x .ruler-label[data-edge='end'] { transform: translateX(-100%); }
.ruler-y .ruler-label { right: 20px; transform: translateY(-50%); }
.ruler-y .ruler-label[data-edge='start'] { transform: none; }
.ruler-y .ruler-label[data-edge='end'] { transform: translateY(-100%); }
.ruler-unit { position: absolute; color: #64748b; }
.ruler-x .ruler-unit { right: -25px; top: 0; }.ruler-y .ruler-unit { bottom: -18px; right: 5px; }
.warehouse-shell { position: absolute; clip-path: polygon(5% 8%,89% 8%,98% 41%,98% 61%,89% 92%,5% 92%); }
.warehouse-shell { inset: 2.5% 2% 2.5% 2%; background: #566271; filter: drop-shadow(0 2px 2px rgba(37,49,77,.14)); }
.warehouse-interior { position: absolute; inset: 0; clip-path: polygon(5.7% 8.9%,88.4% 8.9%,97.1% 41.3%,97.1% 60.7%,88.4% 91.1%,5.7% 91.1%); background: rgba(255,255,255,.92); }
.loading-door { position: absolute; z-index: 5; display: grid; justify-items: center; color: #202b43; font-size: 11px; line-height: 15px; }
.loading-door strong { margin-top: -29px; font-size: 13px; font-weight: 600; white-space: nowrap; }.loading-door span { margin-top: -14px; white-space: nowrap; }
.loading-door i { position: absolute; inset: 0; border: 2px solid #607080; border-top: 0; background: repeating-linear-gradient(0deg,#e9eef3 0 4px,#f7f9fb 4px 8px); box-shadow: inset 0 -5px #dbe2e9; }
.operation-zone { position: absolute; z-index: 3; display: grid; place-content: center; gap: 4px; border: 1px dashed; text-align: center; }
.operation-zone strong { font-size: 15px; font-weight: 650; }.operation-zone span { font-size: 10px; opacity: .7; }
.tone-green { border-color: #91c5ad; background: rgba(226,244,234,.72); color: #17624b; }.tone-blue { border-color: #93b4df; background: rgba(228,238,252,.72); color: #315a91; }
.forklift-aisle { position: absolute; z-index: 4; display: flex; align-items: center; justify-content: center; gap: 22px; border-block: 1px dashed #c6ced8; color: #536176; white-space: nowrap; }
.forklift-aisle strong { font-size: 11px; font-weight: 500; }.aisle-arrow { font-size: 17px; color: #718096; }
.forklift-aisle.vertical { flex-direction: column; gap: 12px; border: 0; border-inline: 1px dashed #c6ced8; writing-mode: vertical-rl; }
.forklift-aisle.vertical .aisle-arrow { transform: rotate(90deg); }
.utility-room { position: absolute; z-index: 4; display: grid; place-content: center; gap: 4px; border: 2px solid #687585; background: rgba(245,247,249,.92); text-align: center; }.utility-room strong { font-size: 12px; }.utility-room span { color: #64748b; font-size: 10px; }
.fire-lane { position: absolute; z-index: 4; border-inline: 1px solid #ef7868; background: repeating-linear-gradient(45deg,rgba(245,102,82,.3) 0 2px,transparent 2px 6px); transform-origin: center; }.fire-lane span { position: absolute; left: 125%; top: 45%; color: #d54734; font-size: 10px; line-height: 15px; white-space: nowrap; transform: rotate(0deg); }
.structure-column { position: absolute; z-index: 6; width: 14px; height: 14px; border: 1px solid #46515f; background: #6b7786; box-shadow: inset 2px 2px rgba(255,255,255,.35); }
.pallet-group { position: absolute; z-index: 8; min-height: 0; border: 1px solid #a6966b; border-radius: 1px; padding: 0; background-color: #d9c797; background-image: linear-gradient(90deg,transparent calc(100% / var(--pallet-columns) - 1px),#aa9a70 calc(100% / var(--pallet-columns) - 1px)),linear-gradient(transparent calc(100% / var(--pallet-rows) - 1px),#aa9a70 calc(100% / var(--pallet-rows) - 1px)); background-size: calc(100% / var(--pallet-columns)) 100%,100% calc(100% / var(--pallet-rows)); box-shadow: inset 0 0 0 2px rgba(255,255,255,.2),0 1px 2px rgba(37,49,77,.12); cursor: pointer; }
.pallet-group:hover { border-color: #7c6c45; filter: brightness(1.02); }.pallet-group:focus-visible { outline: 2px solid #536dff; outline-offset: 2px; }
.pallet-group.selected { z-index: 15; border: 2px solid #12a9ac; box-shadow: 0 0 0 1px rgba(18,169,172,.18); }
.selection-handle { position: absolute; width: 7px; height: 7px; border: 1px solid white; background: #12a9ac; box-shadow: 0 0 0 1px #12a9ac; }
.handle-1{left:-4px;top:-4px}.handle-2{left:50%;top:-4px}.handle-3{right:-4px;top:-4px}.handle-4{right:-4px;top:50%}.handle-5{right:-4px;bottom:-4px}.handle-6{left:50%;bottom:-4px}.handle-7{left:-4px;bottom:-4px}.handle-8{left:-4px;top:50%}
.rotation-handle { position: absolute; left: 50%; top: -25px; display: grid; place-items: center; width: 17px; height: 17px; border: 1px solid #12a9ac; border-radius: 50%; background: white; color: #12a9ac; font-size: 12px; font-style: normal; transform: translateX(-50%); }
.rotation-handle::after { content: ''; position: absolute; top: 16px; width: 1px; height: 8px; background: #12a9ac; }
.measurement { position: absolute; z-index: 18; color: #03989c; font: 11px/1 Inter,sans-serif; pointer-events: none; }
.width-measure { border-top: 1px solid #12a9ac; }.height-measure { border-left: 1px solid #12a9ac; }
.measurement span { position: absolute; padding: 2px 4px; border-radius: 3px; background: #f9ffff; white-space: nowrap; }
.width-measure span { left: 50%; top: -17px; transform: translateX(-50%); }.height-measure span { left: 4px; top: 50%; transform: translateY(-50%); }
.scene-inspector { position: absolute; z-index: 24; }
.planner-minimap { position: absolute; z-index: 20; right: 16px; bottom: 17px; width: 150px; height: 112px; border: 1px solid #e1e6ec; border-radius: 7px; padding: 9px; background: rgba(255,255,255,.96); box-shadow: 0 7px 20px rgba(37,49,77,.12); }
.minimap-shell { position: relative; width: 100%; height: 100%; clip-path: polygon(3% 5%,88% 5%,98% 36%,98% 62%,89% 95%,3% 95%); background: #f5f7f9; box-shadow: inset 0 0 0 2px #8994a1; }
.minimap-shell i { position: absolute; display: block; background: #d8ca9f; opacity: .8; transform: scale(.9); }
.minimap-shell > span { position: absolute; left: 7%; top: 8%; width: 82%; height: 80%; border: 2px solid #536dff; background: rgba(83,109,255,.04); }
.coordinate-status { position: absolute; z-index: 25; right: 0; bottom: 0; left: 0; display: flex; align-items: center; gap: 22px; height: 28px; padding: 0 22px; border-top: 1px solid #e6ebf1; background: rgba(255,255,255,.96); color: #536176; font: 11px/1 Inter,sans-serif; }
.status-divider { width: 1px; height: 14px; margin-inline: -8px; background: #d6dde5; }.north-indicator { display: flex; align-items: center; gap: 4px; margin-left: auto; color: #354159; font-size: 10px; }.north-indicator span { font-size: 15px; }
@media (max-width: 1280px) { .blueprint-scene { min-width: 1060px; } }
</style>
