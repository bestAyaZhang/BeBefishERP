<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  cameraX: number
  cameraY: number
  scale: number
  viewportWidth: number
  viewportHeight: number
  sceneWidth: number
  sceneHeight: number
}>()

type Tick = { value: number; position: number; major: boolean }
const majorIntervals = [1, 2, 5, 10, 20, 50, 100, 200, 500]
function createTicks(origin: number, pixelsPerMeter: number, length: number): Tick[] {
  if (length <= 0 || pixelsPerMeter <= 0) return []
  const majorInterval = majorIntervals.find((interval) => interval * pixelsPerMeter >= 64) ?? 500
  const minorInterval = majorInterval / 10
  const first = Math.ceil((0 - origin) / (minorInterval * pixelsPerMeter))
  const last = Math.floor((length - origin) / (minorInterval * pixelsPerMeter))
  const ticks: Tick[] = []
  for (let index = first; index <= last; index++) {
    const value = Math.round(index * minorInterval * 100) / 100
    ticks.push({
      value: Object.is(value, -0) ? 0 : value,
      position: Math.round((origin + index * minorInterval * pixelsPerMeter) * 100) / 100,
      major: index % 10 === 0,
    })
  }
  return ticks
}
const horizontalTicks = computed(() => createTicks(
  props.cameraX + 48 * props.scale,
  (props.sceneWidth - 76) / 60 * props.scale,
  props.viewportWidth,
))
const verticalTicks = computed(() => createTicks(
  props.cameraY + 36 * props.scale,
  (props.sceneHeight - 64) / 40 * props.scale,
  props.viewportHeight,
))
</script>

<template>
  <div class="viewport-rulers" aria-label="随画布缩放和平移的米制标尺">
    <div data-testid="planner-viewport-ruler-x" class="viewport-ruler ruler-x" role="img" aria-label="横向米制标尺">
      <i v-for="tick in horizontalTicks" :key="`x-tick-${tick.value}`" class="viewport-ruler-tick" :class="{ major: tick.major }" :style="{ left: `${tick.position}px` }" aria-hidden="true" />
      <span v-for="tick in horizontalTicks.filter((item) => item.major)" :key="`x-label-${tick.value}`" :data-testid="`planner-viewport-ruler-x-label-${tick.value}`" class="viewport-ruler-label" :style="{ left: `${tick.position}px` }">{{ tick.value }}</span>
      <span class="ruler-unit">m</span>
    </div>
    <div data-testid="planner-viewport-ruler-y" class="viewport-ruler ruler-y" role="img" aria-label="纵向米制标尺">
      <i v-for="tick in verticalTicks" :key="`y-tick-${tick.value}`" class="viewport-ruler-tick" :class="{ major: tick.major }" :style="{ top: `${tick.position}px` }" aria-hidden="true" />
      <span v-for="tick in verticalTicks.filter((item) => item.major)" :key="`y-label-${tick.value}`" :data-testid="`planner-viewport-ruler-y-label-${tick.value}`" class="viewport-ruler-label" :style="{ top: `${tick.position}px` }">{{ tick.value }}</span>
    </div>
    <span class="ruler-corner" aria-hidden="true" />
  </div>
</template>

<style scoped>
.viewport-rulers {position:absolute;inset:0;z-index:30;pointer-events:none;color:#526176;font:10px/1 Inter,sans-serif;}
.viewport-ruler {position:absolute;background:rgba(255,255,255,.95);overflow:hidden;}
.ruler-x {top:0;left:0;right:0;height:25px;border-bottom:1px solid #9ba8b8;}
.ruler-y {top:0;bottom:0;left:0;width:28px;border-right:1px solid #9ba8b8;}
.viewport-ruler-tick {position:absolute;display:block;background:#8d99a9;}
.ruler-x .viewport-ruler-tick {bottom:0;width:1px;height:5px;}
.ruler-x .viewport-ruler-tick.major {height:11px;background:#637187;}
.ruler-y .viewport-ruler-tick {right:0;width:5px;height:1px;}
.ruler-y .viewport-ruler-tick.major {width:11px;background:#637187;}
.viewport-ruler-label {position:absolute;color:#34445d;font-variant-numeric:tabular-nums;white-space:nowrap;}
.ruler-x .viewport-ruler-label {top:3px;transform:translateX(-50%);}
.ruler-y .viewport-ruler-label {right:12px;transform:translateY(-50%);}
.ruler-unit {position:absolute;top:3px;right:6px;}
.ruler-corner {position:absolute;top:0;left:0;width:28px;height:25px;border-right:1px solid #9ba8b8;border-bottom:1px solid #9ba8b8;background:#fff;}
</style>
