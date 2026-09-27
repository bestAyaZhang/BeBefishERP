<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Box, CheckCircle2, CircleOff, DoorOpen, Grid2X2, Hand, MapPinned, MousePointer2,
  Redo2, Route as RouteIcon, Save, ShieldCheck, SquareDashedMousePointer, Undo2,
} from 'lucide-vue-next'
import WarehouseLayerTree from '../warehouseLayout/components/WarehouseLayerTree.vue'
import WarehouseLayoutCanvas from '../warehouseLayout/components/WarehouseLayoutCanvas.vue'
import WarehouseObjectInspector from '../warehouseLayout/components/WarehouseObjectInspector.vue'
import { useWarehouseLayout } from '../warehouseLayout/useWarehouseLayout'
import type { LayoutObjectPatch, LayoutObjectType, LayoutTool } from '../warehouseLayout/types'

const route = useRoute()
const editor = useWarehouseLayout()
const {
  state, selectedObjectId, selectedObject, activeTool, loading, saving, error, notice,
  issues, dirty, canUndo, canRedo,
} = editor
const validationOpen = ref(false)
let nextObjectId = 1

const blockingCount = computed(() => issues.value.filter((issue) => issue.severity === 'error').length)
const warningCount = computed(() => issues.value.filter((issue) => issue.severity === 'warning').length)
const formattedSavedAt = computed(() => {
  if (!state.value.savedAt) return '尚未保存'
  const date = new Date(state.value.savedAt)
  return Number.isNaN(date.getTime()) ? state.value.savedAt : date.toLocaleString('zh-CN', { hour12: false })
})

const tools: Array<{ id: string; tool: LayoutTool; label: string; icon: typeof MousePointer2 }> = [
  { id: 'select', tool: 'select', label: '选择', icon: MousePointer2 },
  { id: 'pan', tool: 'pan', label: '平移', icon: Hand },
  { id: 'fixed-zone', tool: 'draw-fixed-zone', label: '固定区', icon: Grid2X2 },
  { id: 'free-zone', tool: 'draw-free-zone', label: '自由区', icon: SquareDashedMousePointer },
  { id: 'aisle', tool: 'draw-aisle', label: '通道', icon: RouteIcon },
  { id: 'obstacle', tool: 'draw-obstacle', label: '障碍', icon: Box },
  { id: 'location', tool: 'draw-location', label: '库位', icon: MapPinned },
]

watch(() => route.query.warehouseId, (raw) => {
  const warehouseId = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!Number.isInteger(warehouseId) || warehouseId <= 0) {
    error.value = '缺少有效的仓库 ID，请从仓库管理进入规划画布。'
    return
  }
  void editor.load(warehouseId).catch(() => undefined)
}, { immediate: true })

function createAt(position: { x: number; y: number }) {
  const definition = drawingDefinition(activeTool.value)
  if (!definition) return
  const id = `${definition.type}-created-${nextObjectId++}`
  const number = String(state.value.objects.filter((object) => object.type === definition.type).length + 1).padStart(2, '0')
  editor.createObject({
    id,
    type: definition.type,
    code: `${definition.prefix}-${number}`,
    name: `${definition.label} ${number}`,
    x: position.x,
    y: position.y,
    width: definition.width,
    height: definition.height,
    rotation: 0,
    visible: true,
    locked: false,
    ...(definition.type === 'fixed-location' ? {
      maxHeight: 4, maxWeight: 1200, maxVolume: 38, maxSkuCount: 1, singleSku: true,
    } : {}),
  })
}

function drawingDefinition(tool: LayoutTool): { type: LayoutObjectType; prefix: string; label: string; width: number; height: number } | null {
  return ({
    'draw-fixed-zone': { type: 'fixed-zone', prefix: 'ZONE', label: '固定区', width: 26, height: 18 },
    'draw-free-zone': { type: 'free-zone', prefix: 'FREE', label: '自由区', width: 26, height: 18 },
    'draw-aisle': { type: 'aisle', prefix: 'AISLE', label: '通道', width: 34, height: 8 },
    'draw-obstacle': { type: 'obstacle', prefix: 'OBS', label: '障碍物', width: 6, height: 6 },
    'draw-location': { type: 'fixed-location', prefix: 'A', label: '固定库位', width: 12, height: 8 },
  } as Partial<Record<LayoutTool, { type: LayoutObjectType; prefix: string; label: string; width: number; height: number }>>)[tool] ?? null
}

function updateSelected(patch: LayoutObjectPatch) {
  try { editor.updateSelectedObject(patch) } catch (cause) { error.value = cause instanceof Error ? cause.message : '对象更新失败' }
}

function openValidation() {
  validationOpen.value = true
  const first = issues.value.find((issue) => issue.severity === 'error')
  if (first?.objectId) editor.selectObject(first.objectId)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && activeTool.value !== 'select') editor.setTool('select')
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <main data-testid="warehouse-layout-view" class="warehouse-layout-page">
    <div v-if="loading" class="page-state"><span class="spinner"></span>正在加载仓库布局…</div>
    <div v-else-if="error && !state.warehouseId" role="alert" class="page-state error-state"><CircleOff :size="28" /><strong>仓库布局无法加载</strong><p>{{ error }}</p></div>
    <template v-else>
      <header class="layout-page-header">
        <div class="title-block">
          <div class="title-line"><h1>仓库布局编辑</h1><span class="version-tag">草稿 v{{ state.version }}</span></div>
          <p>{{ state.warehouseName }} · {{ state.warehouseCode }} · {{ state.warehouseAddress }}</p>
        </div>
        <div class="header-actions">
          <div class="save-copy"><strong data-testid="layout-dirty-state" :class="{ dirty }">{{ dirty ? '未保存更改' : '草稿已保存' }}</strong><small>{{ formattedSavedAt }}</small></div>
          <button data-testid="discard-layout-draft" type="button" class="button secondary" :disabled="!dirty || saving" @click="editor.discard"><DoorOpen :size="15" />放弃更改</button>
          <button data-testid="save-layout-draft" type="button" class="button secondary" :disabled="!dirty || saving" @click="editor.saveDraft"><Save :size="15" />{{ saving ? '保存中…' : '保存草稿' }}</button>
          <button data-testid="validate-layout" type="button" class="button primary" @click="openValidation"><ShieldCheck :size="15" />校验并发布</button>
        </div>
      </header>

      <div class="layout-toolbar" role="toolbar" aria-label="仓库布局编辑工具">
        <div class="tool-buttons">
          <button
            v-for="tool in tools"
            :key="tool.tool"
            :data-testid="`layout-tool-${tool.id}`"
            type="button"
            class="tool-button"
            :class="{ active: activeTool === tool.tool }"
            :aria-pressed="activeTool === tool.tool"
            :title="tool.label"
            @click="editor.setTool(tool.tool)"
          ><component :is="tool.icon" :size="15" /><span>{{ tool.label }}</span></button>
        </div>
        <div class="toolbar-divider"></div>
        <label class="snap-control"><input type="checkbox" checked />吸附</label>
        <span class="zoom-copy">100%</span>
        <div class="toolbar-spacer"></div>
        <div class="issue-summary" :class="{ clear: blockingCount === 0 }">
          <CheckCircle2 v-if="blockingCount === 0" :size="14" />
          <ShieldCheck v-else :size="14" />
          <span>{{ blockingCount }} 阻断 · {{ warningCount }} 警告</span>
        </div>
        <button data-testid="layout-undo" type="button" class="icon-button" title="撤销" :disabled="!canUndo" @click="editor.undo"><Undo2 :size="15" /></button>
        <button data-testid="layout-redo" type="button" class="icon-button" title="重做" :disabled="!canRedo" @click="editor.redo"><Redo2 :size="15" /></button>
      </div>

      <p v-if="notice" role="status" class="notice-bar">{{ notice }}</p>
      <p v-if="error" role="alert" class="notice-bar error-bar">{{ error }}</p>

      <div class="warehouse-layout-workspace">
        <WarehouseLayerTree
          :objects="state.objects"
          :selected-object-id="selectedObjectId"
          :issues="issues"
          @select="editor.selectObject"
          @toggle-visibility="editor.toggleObjectVisibility"
          @toggle-lock="editor.toggleObjectLock"
        />
        <WarehouseLayoutCanvas
          :state="state"
          :selected-object-id="selectedObjectId"
          :active-tool="activeTool"
          :issues="issues"
          @select="editor.selectObject"
          @create-at="createAt"
        />
        <WarehouseObjectInspector :object="selectedObject" :issues="issues" @update="updateSelected" />
      </div>

      <div v-if="validationOpen" class="validation-placeholder" aria-live="polite">
        <div><ShieldCheck :size="16" /><span>校验结果：{{ blockingCount }} 个阻断，{{ warningCount }} 个警告</span></div>
        <button type="button" @click="validationOpen = false">返回修改</button>
      </div>
    </template>
  </main>
</template>

<style scoped>
.warehouse-layout-page { position:relative; display:flex; flex-direction:column; min-width:0; min-height:calc(100vh - 64px); height:calc(100vh - 64px); overflow:hidden; margin:-24px; background:#f6f7fb; color:#25314d; }
.page-state { min-height:460px; display:flex; align-items:center; justify-content:center; gap:10px; color:#64748b; font-size:14px; }.error-state{flex-direction:column;text-align:center}.error-state strong{color:#25314d}.error-state p{margin:0;max-width:480px}.spinner{width:22px;height:22px;border:2px solid #dbe2f0;border-top-color:#536dff;border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{rotate:360deg}}
.layout-page-header { min-height:82px; box-sizing:border-box; padding:13px 20px 11px; display:flex; align-items:center; justify-content:space-between; gap:18px; border-bottom:1px solid #e2e8f0; background:#f6f7fb; }
.title-block{min-width:0}.title-line{display:flex;align-items:center;gap:9px}h1{margin:0;font-size:20px;line-height:28px;font-weight:700;color:#25314d}.version-tag{padding:3px 8px;border-radius:999px;background:#fff7e6;color:#b7791f;font-size:10px;font-weight:650}.title-block p{margin:3px 0 0;color:#64748b;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.header-actions{display:flex;align-items:center;gap:7px}.save-copy{min-width:112px;display:grid;text-align:right}.save-copy strong{color:#16a36a;font-size:10px}.save-copy strong.dirty{color:#f59e0b}.save-copy small{color:#94a3b8;font-size:8px}
.button{height:34px;display:inline-flex;align-items:center;justify-content:center;gap:5px;border:1px solid;border-radius:6px;padding:0 12px;font-size:11px;font-weight:650;cursor:pointer}.button:disabled{cursor:not-allowed;opacity:.45}.button.secondary{border-color:#dbe2ea;background:#fff;color:#475569}.button.secondary:hover:not(:disabled){border-color:#b8c3d4;color:#25314d}.button.primary{border-color:#536dff;background:#536dff;color:#fff;box-shadow:0 5px 12px rgba(83,109,255,.18)}.button.primary:hover{background:#465eea}
.layout-toolbar{min-height:45px;box-sizing:border-box;padding:5px 14px;display:flex;align-items:center;gap:6px;border-bottom:1px solid #e2e8f0;background:#fff}.tool-buttons{display:flex;gap:3px}.tool-button{height:33px;display:flex;align-items:center;gap:4px;border:1px solid transparent;border-radius:5px;padding:0 7px;background:transparent;color:#64748b;font-size:10px;cursor:pointer}.tool-button:hover{background:#f1f5f9;color:#334155}.tool-button.active{border-color:#8da0ff;background:#eef1ff;color:#536dff}.toolbar-divider{width:1px;height:22px;background:#e2e8f0;margin:0 4px}.snap-control{display:flex;align-items:center;gap:5px;color:#64748b;font-size:10px}.snap-control input{accent-color:#536dff}.zoom-copy{padding:4px 7px;border-radius:5px;background:#f8fafc;color:#475569;font:600 10px Inter,sans-serif}.toolbar-spacer{flex:1}.issue-summary{height:27px;display:flex;align-items:center;gap:5px;padding:0 8px;border-radius:5px;background:#fff1f4;color:#be123c;font-size:10px}.issue-summary.clear{background:#ecfdf5;color:#047857}.icon-button{width:29px;height:29px;display:grid;place-items:center;border:1px solid #e2e8f0;border-radius:5px;background:#fff;color:#64748b;cursor:pointer}.icon-button:disabled{opacity:.35;cursor:not-allowed}.icon-button:hover:not(:disabled){border-color:#aab7ca;color:#536dff}
.notice-bar{z-index:20;margin:0;padding:5px 14px;border-bottom:1px solid #bbf7d0;background:#ecfdf5;color:#047857;font-size:10px}.error-bar{border-color:#fecdd3;background:#fff1f4;color:#be123c}
.warehouse-layout-workspace{min-height:0;flex:1;display:grid;grid-template-columns:196px minmax(520px,1fr) 260px;overflow:hidden;background:#fff}
.validation-placeholder{position:absolute;z-index:30;right:270px;top:135px;display:flex;align-items:center;gap:12px;padding:9px 12px;border:1px solid #fecdd3;border-radius:7px;background:#fff;color:#be123c;box-shadow:0 8px 28px rgba(37,49,77,.16);font-size:11px}.validation-placeholder>div{display:flex;align-items:center;gap:6px}.validation-placeholder button{border:0;background:transparent;color:#536dff;font-size:10px;font-weight:650;cursor:pointer}
@media (max-width:1180px){.warehouse-layout-workspace{grid-template-columns:176px minmax(500px,1fr) 238px}.tool-button span{display:none}.save-copy{display:none}}
@media (max-width:980px){.warehouse-layout-page{overflow:auto;height:auto;min-height:calc(100vh - 64px)}.layout-page-header{align-items:flex-start;flex-direction:column}.header-actions{width:100%;justify-content:flex-end}.warehouse-layout-workspace{min-height:700px;width:980px}.layout-toolbar{min-width:980px}}
</style>
