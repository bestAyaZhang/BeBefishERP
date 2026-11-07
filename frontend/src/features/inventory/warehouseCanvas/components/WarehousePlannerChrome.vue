<script setup lang="ts">
import {
  Box,
  Grid2X2,
  Map,
  PackageOpen,
  Redo2,
  Ruler,
  Undo2,
  Warehouse,
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
  inventoryView?: boolean
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
        <Warehouse class="warehouse-mark" :size="22" stroke-width="1.6" aria-hidden="true" />
        <div class="planner-heading-copy">
        <h1 data-testid="planner-title">{{ warehouseName }} · {{ inventoryView ? '库存查看' : completed ? '画布查看' : '平面规划' }}</h1>
        <p v-if="contextNotice" data-testid="planner-warehouse-notice" role="status">{{ contextNotice }}</p>
        </div>
      </div>
      <div v-if="inventoryView" class="planner-header-controls">
        <slot name="inventory-controls" />
      </div>
      <nav v-if="!inventoryView" v-show="!completed" class="planner-tool-rail" aria-label="仓库规划工具">
        <button v-for="tool in tools" :key="tool.id" :data-testid="`planner-tool-${tool.id}`" type="button" :aria-pressed="activeTool === tool.id" :disabled="completed" @click="emit('change-tool', tool.id)">
          <component :is="tool.icon" :size="18" stroke-width="1.8" />
          <span>{{ tool.label }}</span>
        </button>
      </nav>
      <div v-if="!inventoryView" class="planner-actions" aria-label="规划操作">
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
          aria-label="显示尺寸测量"
          title="显示尺寸测量"
          :aria-pressed="measurementEnabled"
          @click="emit('toggle-measurement')"
        >
          <Ruler :size="17" /><span class="action-label">测量</span>
          <span class="switch" aria-hidden="true"><span /></span>
        </button>
        <button
          data-testid="planner-grid-toggle"
          type="button"
          class="label-action"
          aria-label="网格吸附"
          title="网格吸附"
          :aria-pressed="gridSnapping"
          @click="emit('toggle-grid')"
        >
          <Grid2X2 :size="17" /><span class="action-label">网格吸附</span>
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
.planner-header { position:relative; z-index:40; display:flex; align-items:center; gap:24px; flex-wrap:wrap; min-height:72px; padding:14px 24px; background:var(--wh-ground); }
.planner-heading { display:flex; flex:1 1 220px; min-width:0; align-items:center; gap:12px; }
.warehouse-mark { flex:none; color:var(--wh-text); }
.planner-heading-copy { min-width:0; }
.planner-header h1 { margin:0; color:var(--wh-text); font-size:18px; font-weight:600; line-height:26px; letter-spacing:-.02em; }
.planner-heading p { margin:3px 0 0; max-width:420px; color:var(--wh-muted); font-size:12px; line-height:18px; }
.planner-header-controls { display:flex; min-width:0; flex:0 1 360px; align-items:center; margin-left:auto; }
.planner-tool-rail { display:flex; flex:none; align-items:center; gap:4px; }
.planner-tool-rail button { position:relative; display:inline-flex; align-items:center; justify-content:center; gap:7px; min-height:36px; padding:0 11px; border:0; border-radius:6px; background:transparent; color:var(--wh-muted); font:inherit; font-size:13px; white-space:nowrap; cursor:pointer; }
.planner-tool-rail button[aria-pressed="true"] { background:var(--wh-active); color:var(--wh-text); font-weight:600; }
.planner-tool-rail button[aria-pressed="true"]::after { position:absolute; right:11px; bottom:0; left:11px; height:2px; background:var(--wh-accent); content:''; }
.planner-actions { display:flex; flex:none; align-items:center; gap:4px; margin-left:auto; }
.planner-actions button { display:inline-flex; align-items:center; justify-content:center; min-width:34px; min-height:36px; border:0; border-radius:6px; padding:0 8px; background:transparent; color:var(--wh-muted); font:inherit; cursor:pointer; }
.planner-actions button:hover:not(:disabled),.planner-tool-rail button:hover:not(:disabled) { background:var(--wh-active); color:var(--wh-text); }
.planner-actions button:focus-visible,.planner-tool-rail button:focus-visible { outline:2px solid var(--wh-accent); outline-offset:3px; }
.planner-actions button:disabled,.planner-tool-rail button:disabled { color:var(--wh-muted); opacity:.55; cursor:not-allowed; }
.action-divider { width:1px; height:20px; margin:0 7px; background:var(--wh-border); }
.planner-actions .label-action { gap:6px; font-size:12px; }
.switch { position:relative; width:26px; height:16px; flex:none; border-radius:999px; background:var(--wh-control); }
.switch span { position:absolute; top:3px; left:3px; width:10px; height:10px; border-radius:50%; background:var(--wh-surface); transition:transform .16s ease-out; }
button[aria-pressed="true"] .switch { background:var(--wh-accent); }
button[aria-pressed="true"] .switch span { transform:translateX(10px); }
.planner-actions .complete-button { min-width:96px; margin-left:12px; padding-inline:16px; background:var(--wh-accent); color:var(--wh-surface); font-size:13px; font-weight:500; }
.planner-actions .complete-button:hover:not(:disabled) { background:var(--wh-accent-hover); color:var(--wh-surface); }
.planner-actions .complete-button:disabled { background:var(--wh-active); color:var(--wh-muted); opacity:1; }
@media (max-width:1250px) { .planner-header { gap:12px; } .planner-heading { flex-basis:200px; } .action-label { display:none; } }
@media (max-width:800px) { .planner-header { padding:16px; gap:12px 8px; } .planner-heading { flex-basis:calc(100% - 120px); } .planner-header h1 { font-size:18px; } .planner-tool-rail { order:3; flex:1; } .planner-heading { padding-right:108px; } .planner-actions { order:4; flex:1 1 100%; margin:0; } .planner-actions .label-action { gap:6px; } .action-label { display:inline; } .action-divider { margin-inline:4px; } .planner-actions .complete-button { position:absolute; top:16px; right:16px; margin:0; } .planner-header-controls { flex:1 1 100%; } }
@media (max-width:480px) { .planner-heading { flex-basis:calc(100% - 106px); gap:8px; } .warehouse-mark { display:none; } .planner-tool-rail { justify-content:space-between; } .planner-tool-rail button { min-height:44px; padding:0 12px; font-size:14px; } .planner-actions .complete-button { min-height:44px; min-width:96px; padding-inline:10px; } }
@media (prefers-reduced-motion:reduce) { .switch span { transition:none; } }
</style>
