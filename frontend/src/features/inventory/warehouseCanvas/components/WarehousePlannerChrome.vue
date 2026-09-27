<script setup lang="ts">
import {
  Box,
  Grid2X2,
  Map,
  PackageOpen,
  Redo2,
  Ruler,
  Undo2,
} from 'lucide-vue-next'

export type PlannerUiTool = 'structure' | 'zone' | 'goods' | 'measure'

defineProps<{
  warehouseName: string
  contextNotice?: string
  activeTool: PlannerUiTool
  measurementEnabled: boolean
  gridSnapping: boolean
  canUndo: boolean
  canRedo: boolean
  completed?: boolean
  completionBlocked?: boolean
  completionReason?: string
}>()

const emit = defineEmits<{
  'change-tool': [tool: PlannerUiTool]
  'toggle-measurement': []
  'toggle-grid': []
  undo: []
  redo: []
  complete: []
}>()

const tools: Array<{
  id: PlannerUiTool
  label: string
  icon: typeof Map
}> = [
  { id: 'structure', label: '结构', icon: Map },
  { id: 'zone', label: '区域', icon: Box },
  { id: 'goods', label: '货物', icon: PackageOpen },
  { id: 'measure', label: '测量', icon: Ruler },
]
</script>

<template>
  <div class="planner-chrome">
    <header class="planner-header">
      <div class="planner-heading">
        <h1 data-testid="planner-title">{{ warehouseName }} · {{ completed ? '画布查看' : '平面规划' }}</h1>
        <p v-if="contextNotice" data-testid="planner-warehouse-notice" role="status">{{ contextNotice }}</p>
      </div>
      <nav v-show="!completed" class="planner-tool-rail" aria-label="仓库规划工具">
        <button v-for="tool in tools" :key="tool.id" :data-testid="`planner-tool-${tool.id}`" type="button" :aria-pressed="activeTool === tool.id" :disabled="completed" @click="emit('change-tool', tool.id)">
          <component :is="tool.icon" :size="18" stroke-width="1.8" />
          <span>{{ tool.label }}</span>
        </button>
      </nav>
      <div class="planner-actions" aria-label="规划操作">
        <button data-testid="planner-undo" type="button" aria-label="撤销" :disabled="!canUndo" @click="emit('undo')">
          <Undo2 :size="18" />
        </button>
        <button data-testid="planner-redo" type="button" aria-label="重做" :disabled="!canRedo" @click="emit('redo')">
          <Redo2 :size="18" />
        </button>
        <span class="action-divider" aria-hidden="true" />
        <button
          data-testid="planner-measure-toggle"
          type="button"
          class="label-action"
          :aria-pressed="measurementEnabled"
          @click="emit('toggle-measurement')"
        >
          <Ruler :size="17" />测量
          <span class="switch" aria-hidden="true"><span /></span>
        </button>
        <button
          data-testid="planner-grid-toggle"
          type="button"
          class="label-action"
          :aria-pressed="gridSnapping"
          @click="emit('toggle-grid')"
        >
          <Grid2X2 :size="17" />网格吸附
          <span class="switch" aria-hidden="true"><span /></span>
        </button>
        <button data-testid="planner-complete" type="button" class="complete-button" :disabled="completionBlocked" :title="completionBlocked ? completionReason : undefined" @click="emit('complete')">
          {{ completed ? '修改规划' : '完成规划' }}
        </button>
      </div>
    </header>

  </div>
</template>

<style scoped>
.planner-chrome { display: contents; }
.planner-header {
  position: relative;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  gap: 16px;
  overflow-x: auto;
  padding: 0 18px 0 22px;
  border-bottom: 1px solid #e2e8f0;
  background: rgba(255,255,255,.96);
  box-shadow: 0 1px 7px rgba(37,49,77,.06);
}
.planner-heading { display: flex; min-width: 0; align-items: center; gap: 12px; }
.planner-header h1 { margin: 0; color: #202b43; font-size: 18px; line-height: 1; font-weight: 650; letter-spacing: .01em; white-space: nowrap; }
.planner-heading p { max-width: 420px; margin: 0; overflow: hidden; border: 1px solid #f4d486; border-radius: 999px; padding: 4px 9px; background: #fff8df; color: #8a5a08; font-size: 11px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.planner-actions { display: flex; flex-shrink: 0; align-items: center; gap: 7px; margin-left:auto; }
.planner-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  border: 0;
  border-radius: 7px;
  padding: 0 9px;
  background: transparent;
  color: #536176;
  font: inherit;
  cursor: pointer;
}
.planner-actions button:hover:not(:disabled) { background: #f1f5f9; color: #25314d; }
.planner-actions button:focus-visible,.planner-tool-rail button:focus-visible { outline: 2px solid #536dff; outline-offset: 2px; }
.planner-actions button:disabled { color: #cbd5e1; cursor: not-allowed; }
.action-divider { width: 1px; height: 24px; margin: 0 3px; background: #e2e8f0; }
.planner-actions .label-action { gap: 7px; padding-inline: 8px; font-size: 13px; }
.switch { position: relative; width: 32px; height: 18px; border-radius: 999px; background: #cbd5e1; transition: background .18s ease; }
.switch span { position: absolute; top: 3px; left: 3px; width: 12px; height: 12px; border-radius: 50%; background: white; box-shadow: 0 1px 3px rgba(37,49,77,.22); transition: transform .18s ease; }
button[aria-pressed="true"] .switch { background: #536dff; }
button[aria-pressed="true"] .switch span { transform: translateX(14px); }
.planner-actions .complete-button { min-width: 98px; margin-left: 5px; padding-inline: 17px; background: #536dff; color: white; font-weight: 600; box-shadow: 0 4px 11px rgba(83,109,255,.22); }
.planner-actions .complete-button:hover { background: #465eea; color: white; }
.planner-actions .complete-button:disabled {background:#cbd5e1;color:#64748b;box-shadow:none;cursor:not-allowed;}
.planner-tool-rail button:disabled {opacity:.45;cursor:default;}
.planner-tool-rail {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 7px;
  background: #f6f8fb;
}
.planner-tool-rail button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #536176;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.planner-tool-rail button:hover { background: #f5f7ff; color: #3f5df2; }
.planner-tool-rail button[aria-pressed="true"] { background: #eef2ff; color: #536dff; }
@media (max-width: 1160px) {
  .planner-actions .label-action { font-size: 0; gap: 4px; }
}
</style>
