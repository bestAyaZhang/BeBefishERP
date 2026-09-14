<script setup lang="ts">
import { computed, useId } from 'vue'
import type { PlannerZone } from '../warehousePlannerScene'
import { passageAxis, passageWidthMeters } from '../warehousePassages'
const props = defineProps<{ zone: PlannerZone }>()
const id = useId().replace(/:/g, '-')
const vertical = computed(() => passageAxis(props.zone) === 'vertical')
const length = computed(() => vertical.value ? props.zone.height * 4 : props.zone.width * 6)
const breadth = computed(() => vertical.value ? props.zone.width * 6 : props.zone.height * 4)
const label = computed(() => `${props.zone.label} ${passageWidthMeters(props.zone).toFixed(1)}m`)
</script>

<template>
  <g class="passage-mark" aria-hidden="true">
    <defs>
      <pattern :id="`fire-hatch-${id}`" width="2.5" height="2.5" patternUnits="userSpaceOnUse"><path d="M-.5 2 L.5 3 M0 0 L2.5 2.5 M2 -.5 L3 .5" fill="none" stroke="#f08c80" stroke-width=".22" /></pattern>
      <clipPath :id="`passage-clip-${id}`"><rect :width="zone.width * 6" :height="zone.height * 4" /></clipPath>
    </defs>
    <rect v-if="zone.kind === 'fire'" data-testid="passage-fire-hatch" :width="zone.width * 6" :height="zone.height * 4" :fill="`url(#fire-hatch-${id})`" opacity=".65" />
    <g :clip-path="`url(#passage-clip-${id})`">
      <g :transform="`translate(${zone.width * 3},${zone.height * 2}) rotate(${vertical ? 90 : 0})`">
        <g v-if="zone.kind !== 'fire'" data-testid="passage-arrows" class="arrows">
          <text v-if="zone.traffic !== 'forward'" :x="-length / 2 + 4" y="0">←</text>
          <text v-if="zone.traffic !== 'backward'" :x="length / 2 - 4" y="0">→</text>
        </g>
        <foreignObject :x="-length / 2 + (zone.kind === 'fire' ? 2 : 10)" :y="-breadth / 2" :width="Math.max(0, length - (zone.kind === 'fire' ? 4 : 20))" :height="breadth">
          <div xmlns="http://www.w3.org/1999/xhtml" class="passage-label"><span data-testid="passage-label">{{ label }}</span></div>
        </foreignObject>
      </g>
    </g>
  </g>
</template>

<style scoped>
.passage-mark {pointer-events:none;}
.arrows text {fill:currentColor;font-size:8px;text-anchor:middle;dominant-baseline:middle;}
.passage-label {height:100%;display:flex;align-items:center;justify-content:center;overflow:hidden;}
.passage-label span {font-size:6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
</style>
