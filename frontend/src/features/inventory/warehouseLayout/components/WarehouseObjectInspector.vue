<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, Info, LockKeyhole } from 'lucide-vue-next'
import { layoutObjectTypeLabels, type LayoutIssue, type LayoutObject, type LayoutObjectPatch } from '../types'

const props = defineProps<{
  object?: LayoutObject
  issues: LayoutIssue[]
}>()

const emit = defineEmits<{ update: [patch: LayoutObjectPatch] }>()

const objectIssues = computed(() => props.object ? props.issues.filter((issue) => issue.objectId === props.object?.id) : [])

function updateText(key: 'name' | 'code', event: Event) {
  emit('update', { [key]: (event.target as HTMLInputElement).value })
}

function updateNumber(key: 'x' | 'y' | 'width' | 'height' | 'rotation' | 'maxHeight' | 'maxWeight' | 'maxVolume' | 'maxSkuCount', event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  if (Number.isFinite(value)) emit('update', { [key]: value })
}
</script>

<template>
  <aside data-testid="warehouse-object-inspector" class="inspector-panel">
    <header><div><h2>对象属性</h2><p v-if="object">{{ layoutObjectTypeLabels[object.type] }} · {{ object.code }}</p><p v-else>未选择对象</p></div><LockKeyhole v-if="object?.locked" :size="15" aria-label="对象已锁定" /></header>
    <div v-if="object" class="inspector-body">
      <section class="field-section">
        <h3>基础信息</h3>
        <label><span>名称</span><input data-testid="layout-object-name" :value="object.name" :disabled="object.locked" @change="updateText('name', $event)" /></label>
        <label><span>编码</span><input data-testid="layout-object-code" :value="object.code" :disabled="object.locked || object.type === 'boundary'" @change="updateText('code', $event)" /></label>
      </section>
      <section class="field-section">
        <h3>位置与尺寸 <small>米</small></h3>
        <div class="field-grid">
          <label><span>X</span><input data-testid="layout-object-x" type="number" step="0.1" :value="object.x" :disabled="object.locked" @change="updateNumber('x', $event)" /></label>
          <label><span>Y</span><input data-testid="layout-object-y" type="number" step="0.1" :value="object.y" :disabled="object.locked" @change="updateNumber('y', $event)" /></label>
          <label><span>宽度</span><input data-testid="layout-object-width" type="number" step="0.1" min="0.1" :value="object.width" :disabled="object.locked" @change="updateNumber('width', $event)" /></label>
          <label><span>深度</span><input data-testid="layout-object-height" type="number" step="0.1" min="0.1" :value="object.height" :disabled="object.locked" @change="updateNumber('height', $event)" /></label>
          <label class="span-2"><span>旋转角度</span><input type="number" step="1" :value="object.rotation" :disabled="object.locked" @change="updateNumber('rotation', $event)" /></label>
        </div>
      </section>
      <section v-if="object.type === 'fixed-location'" class="field-section">
        <h3>容量约束</h3>
        <div class="field-grid">
          <label><span>最大高度 m</span><input type="number" :value="object.maxHeight" :disabled="object.locked" @change="updateNumber('maxHeight', $event)" /></label>
          <label><span>最大重量 kg</span><input type="number" :value="object.maxWeight" :disabled="object.locked" @change="updateNumber('maxWeight', $event)" /></label>
          <label><span>最大体积 m³</span><input type="number" :value="object.maxVolume" :disabled="object.locked" @change="updateNumber('maxVolume', $event)" /></label>
          <label><span>最大 SKU 数</span><input type="number" :value="object.maxSkuCount" :disabled="object.locked" @change="updateNumber('maxSkuCount', $event)" /></label>
        </div>
        <label class="checkbox-row"><input type="checkbox" :checked="object.singleSku" :disabled="object.locked" @change="emit('update', { singleSku: ($event.target as HTMLInputElement).checked })" /><span>仅允许单一 SKU</span></label>
      </section>

      <section v-if="objectIssues.length" class="issue-section">
        <article v-for="issue in objectIssues" :key="issue.id" :class="issue.severity">
          <AlertTriangle :size="15" /><div><strong>{{ issue.title }}</strong><p>{{ issue.description }}</p></div>
        </article>
      </section>
      <div class="published-note"><Info :size="14" /><p>修改保存在草稿中；发布前不会影响当前作业地图。</p></div>
    </div>
    <div v-else class="empty-inspector"><div class="empty-icon">⌖</div><strong>选择画布对象</strong><p>从左侧图层或画布中选择区域、通道、障碍物或库位后编辑属性。</p></div>
  </aside>
</template>

<style scoped>
.inspector-panel { min-width:0; height:100%; display:flex; flex-direction:column; border-left:1px solid #e2e8f0; background:#fff; }
.inspector-panel>header { min-height:58px; padding:13px 14px 9px; display:flex; justify-content:space-between; border-bottom:1px solid #eef2f7; color:#64748b; }
h2 { margin:0; color:#25314d; font-size:14px; font-weight:700; line-height:20px; }header p{margin:2px 0 0;color:#94a3b8;font-size:10px}
.inspector-body { min-height:0; overflow:auto; padding:12px; }.field-section+.field-section{margin-top:15px;padding-top:13px;border-top:1px solid #eef2f7}
h3 { margin:0 0 8px; display:flex; justify-content:space-between; color:#64748b; font-size:10px; font-weight:700; text-transform:uppercase; letter-spacing:.04em; }h3 small{font-weight:500;text-transform:none}
label { display:grid; gap:4px; color:#64748b; font-size:10px; }.field-section>label+label{margin-top:8px}.field-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.span-2{grid-column:1/-1}
input:not([type=checkbox]) { width:100%; min-width:0; height:31px; box-sizing:border-box; border:1px solid #e2e8f0; border-radius:5px; padding:0 8px; background:#fff; color:#25314d; font:500 11px Inter,"Noto Sans SC",sans-serif; outline:none; }
input:focus{border-color:#536dff;box-shadow:0 0 0 3px rgba(83,109,255,.1)}input:disabled{background:#f8fafc;color:#94a3b8}
.checkbox-row{margin-top:9px;display:flex;align-items:center;gap:7px}.checkbox-row input{accent-color:#536dff}
.issue-section{display:grid;gap:7px;margin-top:14px}.issue-section article{display:flex;gap:7px;padding:9px;border:1px solid;border-radius:6px}.issue-section article.error{border-color:#fecdd3;background:#fff1f4;color:#be123c}.issue-section article.warning{border-color:#fde68a;background:#fffbeb;color:#b45309}.issue-section strong{font-size:10px}.issue-section p{margin:2px 0 0;font-size:9px;line-height:14px}
.published-note{display:flex;gap:7px;margin-top:12px;padding:9px;border-radius:6px;background:#f8fafc;color:#64748b}.published-note p{margin:0;font-size:9px;line-height:14px}
.empty-inspector{margin:auto;padding:24px 18px;text-align:center}.empty-icon{margin:0 auto 10px;width:38px;height:38px;display:grid;place-items:center;border-radius:10px;background:#eef2ff;color:#536dff;font-size:22px}.empty-inspector strong{color:#334155;font-size:12px}.empty-inspector p{margin:6px 0 0;color:#94a3b8;font-size:10px;line-height:16px}
</style>
