<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, Boxes, Eye, EyeOff, Lock, Map, Route, Square, Unlock } from 'lucide-vue-next'
import { layoutObjectTypeLabels, type LayoutIssue, type LayoutObject } from '../types'

const props = defineProps<{
  objects: LayoutObject[]
  selectedObjectId: string | null
  issues: LayoutIssue[]
}>()

const emit = defineEmits<{
  select: [objectId: string]
  toggleVisibility: [objectId: string]
  toggleLock: [objectId: string]
}>()

const groups = computed(() => [
  { id: 'boundary', label: '仓库边界', objects: props.objects.filter((object) => object.type === 'boundary') },
  { id: 'zones', label: '区域', objects: props.objects.filter((object) => object.type === 'fixed-zone' || object.type === 'free-zone') },
  { id: 'structures', label: '通道与障碍', objects: props.objects.filter((object) => object.type === 'aisle' || object.type === 'obstacle') },
  { id: 'locations', label: '固定库位', objects: props.objects.filter((object) => object.type === 'fixed-location') },
].filter((group) => group.objects.length > 0))

function issueCount(objectId: string) {
  return props.issues.filter((issue) => issue.objectId === objectId).length
}

function iconFor(object: LayoutObject) {
  if (object.type === 'boundary') return Map
  if (object.type === 'aisle') return Route
  if (object.type === 'fixed-location') return Boxes
  return Square
}
</script>

<template>
  <aside data-testid="warehouse-layer-tree" class="layer-panel">
    <header>
      <div>
        <h2>可编辑图层</h2>
        <p>{{ objects.length }} 个对象</p>
      </div>
      <span class="live-dot" aria-label="草稿图层已加载"></span>
    </header>

    <div class="layer-groups">
      <section v-for="group in groups" :key="group.id" class="layer-group">
        <div class="group-title"><span>{{ group.label }}</span><strong>{{ group.objects.length }}</strong></div>
        <div class="layer-list">
          <div
            v-for="object in group.objects"
            :key="object.id"
            class="layer-row"
            :class="{ selected: selectedObjectId === object.id, muted: !object.visible }"
          >
            <button
              :data-testid="`layout-layer-${object.id}`"
              type="button"
              class="layer-main"
              :aria-pressed="selectedObjectId === object.id"
              @click="emit('select', object.id)"
            >
              <component :is="iconFor(object)" :size="14" aria-hidden="true" />
              <span><b>{{ object.code }}</b><small>{{ layoutObjectTypeLabels[object.type] }}</small></span>
              <AlertTriangle v-if="issueCount(object.id)" :size="13" class="issue-icon" aria-label="存在布局问题" />
            </button>
            <button
              :data-testid="`layout-layer-visibility-${object.id}`"
              type="button"
              class="layer-action"
              :aria-label="`${object.visible ? '隐藏' : '显示'} ${object.code}`"
              :aria-pressed="!object.visible"
              @click="emit('toggleVisibility', object.id)"
            ><Eye v-if="object.visible" :size="13" /><EyeOff v-else :size="13" /></button>
            <button
              :data-testid="`layout-layer-lock-${object.id}`"
              type="button"
              class="layer-action"
              :aria-label="`${object.locked ? '解锁' : '锁定'} ${object.code}`"
              :aria-pressed="object.locked"
              @click="emit('toggleLock', object.id)"
            ><Lock v-if="object.locked" :size="13" /><Unlock v-else :size="13" /></button>
          </div>
        </div>
      </section>
    </div>
    <p class="layer-hint">未发布的修改只影响当前草稿，不会改变作业地图。</p>
  </aside>
</template>

<style scoped>
.layer-panel { min-width:0; height:100%; border-right:1px solid #e2e8f0; background:#fff; display:flex; flex-direction:column; }
.layer-panel>header { min-height:58px; padding:14px 14px 10px; display:flex; align-items:flex-start; justify-content:space-between; border-bottom:1px solid #eef2f7; }
h2 { margin:0; color:#25314d; font-size:14px; font-weight:700; line-height:20px; }
header p { margin:2px 0 0; color:#94a3b8; font-size:11px; }
.live-dot { width:8px; height:8px; margin-top:6px; border-radius:50%; background:#16a36a; box-shadow:0 0 0 4px #dcfce7; }
.layer-groups { min-height:0; flex:1; overflow:auto; padding:8px; }
.layer-group+.layer-group { margin-top:8px; }
.group-title { display:flex; align-items:center; justify-content:space-between; padding:5px 6px; color:#64748b; font-size:11px; font-weight:600; }
.group-title strong { min-width:20px; padding:1px 6px; border-radius:999px; background:#f1f5f9; color:#64748b; text-align:center; font-size:10px; }
.layer-list { display:grid; gap:3px; }
.layer-row { display:grid; grid-template-columns:minmax(0,1fr) 25px 25px; min-height:39px; border:1px solid transparent; border-radius:6px; transition:120ms ease; }
.layer-row:hover { background:#f8fafc; }
.layer-row.selected { border-color:#7c8fff; background:#f0f3ff; }
.layer-row.muted { opacity:.52; }
.layer-main,.layer-action { border:0; background:transparent; color:#64748b; cursor:pointer; }
.layer-main { min-width:0; display:flex; align-items:center; gap:7px; padding:5px 4px 5px 7px; text-align:left; }
.layer-main>span { min-width:0; display:grid; }
.layer-main b { overflow:hidden; color:#334155; font-size:11px; font-weight:650; text-overflow:ellipsis; white-space:nowrap; }
.layer-main small { color:#94a3b8; font-size:9px; }
.layer-action { display:grid; place-items:center; padding:0; border-radius:4px; }
.layer-action:hover { color:#536dff; background:#e8edff; }
.issue-icon { margin-left:auto; color:#ef476f; }
.layer-hint { margin:0; padding:10px 12px 12px; border-top:1px solid #eef2f7; color:#94a3b8; font-size:10px; line-height:16px; }
</style>
