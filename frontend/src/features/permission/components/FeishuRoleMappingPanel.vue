<script setup lang="ts">
import { computed, reactive } from 'vue';
import type {
  FeishuRoleMapping,
  FeishuRoleMappingCandidate,
  FeishuRoleOption,
  SaveFeishuRoleMappingPayload
} from '../types';

const props = defineProps<{
  mappings: FeishuRoleMapping[];
  feishuRoles: FeishuRoleOption[];
  candidateRoles: FeishuRoleMappingCandidate[];
  canManage: boolean;
  loading: boolean;
  saving: boolean;
  error: string;
}>();
const emit = defineEmits<{
  retry: [];
  sync: [];
  save: [feishuRoleId: string, payload: SaveFeishuRoleMappingPayload];
  delete: [feishuRoleId: string];
}>();

type Row = FeishuRoleMapping & { mapped: boolean };
const editing = reactive<Record<string, boolean>>({});
const drafts = reactive<Record<string, { erpRoleId: number | null; enabled: boolean }>>({});
const safeCandidates = computed(() => props.candidateRoles.filter((role) => !role.sensitive));
const rows = computed<Row[]>(() => {
  const byId = new Map(props.mappings.map((mapping) => [mapping.feishuRoleId, mapping]));
  const result = props.feishuRoles.map((role): Row => {
    const mapped = byId.get(role.id);
    if (mapped) return { ...mapped, feishuRoleName: role.name, mapped: true };
    return {
      feishuRoleId: role.id, feishuRoleName: role.name, erpRoleId: 0, erpRoleName: '',
      enabled: true, memberCount: 0, lastSyncedAt: null, lastError: null, mapped: false
    };
  });
  for (const mapping of props.mappings) {
    if (!result.some((row) => row.feishuRoleId === mapping.feishuRoleId)) result.push({ ...mapping, mapped: true });
  }
  return result;
});

function beginEdit(row: Row) {
  if (!props.canManage || props.saving) return;
  drafts[row.feishuRoleId] = { erpRoleId: row.erpRoleId || safeCandidates.value[0]?.id || null, enabled: row.enabled };
  editing[row.feishuRoleId] = true;
}

function cancelEdit(id: string) {
  editing[id] = false;
  delete drafts[id];
}

function save(row: Row) {
  const draft = drafts[row.feishuRoleId];
  if (!draft?.erpRoleId || props.saving) return;
  emit('save', row.feishuRoleId, {
    feishuRoleName: row.feishuRoleName,
    erpRoleId: draft.erpRoleId,
    enabled: draft.enabled
  });
}

function remove(row: Row) {
  if (!row.mapped || props.saving || !window.confirm(`确认删除“${row.feishuRoleName}”的角色映射？`)) return;
  emit('delete', row.feishuRoleId);
}

function formatTime(value: string | null) {
  if (!value) return '尚未同步';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
</script>

<template>
  <section data-testid="feishu-role-mapping-panel" class="space-y-4">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 class="font-semibold text-[#25314d]">飞书业务角色映射</h3>
        <p class="mt-1 text-sm text-slate-500">登录时按飞书业务角色自动授予 ERP 角色；本地附加角色不受影响。</p>
      </div>
      <button
        v-if="canManage"
        data-testid="sync-feishu-mappings"
        type="button"
        class="rounded-[6px] border border-[#536dff] px-3 py-2 text-sm text-[#536dff] disabled:opacity-50"
        :disabled="loading || saving"
        @click="emit('sync')"
      >{{ saving ? '处理中…' : '从飞书同步' }}</button>
    </div>

    <p v-if="error" data-testid="feishu-mapping-error" class="rounded-[6px] border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
      {{ error }} <button type="button" class="ml-2 text-[#536dff] underline" @click="emit('retry')">重试</button>
    </p>
    <p v-if="loading" data-testid="feishu-mapping-loading" class="py-8 text-center text-sm text-slate-400">正在加载飞书角色…</p>
    <p v-else-if="rows.length === 0" data-testid="feishu-mapping-empty" class="rounded-[6px] border border-dashed border-slate-200 py-10 text-center text-sm text-slate-500">暂无可配置的飞书业务角色</p>
    <div v-else class="overflow-x-auto rounded-[6px] border border-slate-200">
      <table class="w-full min-w-[920px] text-left text-sm">
        <thead class="bg-slate-50 text-xs text-slate-500"><tr><th class="px-4 py-3">飞书角色</th><th class="px-4 py-3">ERP 角色</th><th class="px-4 py-3">状态</th><th class="px-4 py-3">成员</th><th class="px-4 py-3">最近同步</th><th class="px-4 py-3">最近异常</th><th class="px-4 py-3">操作</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="row.feishuRoleId" class="border-t border-slate-200 align-top">
            <td class="px-4 py-3"><p class="font-medium text-[#25314d]">{{ row.feishuRoleName }}</p><p class="mt-1 font-mono text-xs text-slate-400">{{ row.feishuRoleId }}</p></td>
            <td class="px-4 py-3">
              <select v-if="editing[row.feishuRoleId]" v-model.number="drafts[row.feishuRoleId].erpRoleId" :data-testid="`feishu-target-${row.feishuRoleId}`" class="h-9 rounded border border-slate-300 px-2" :disabled="saving">
                <option :value="null" disabled>请选择角色</option>
                <option v-for="candidate in safeCandidates" :key="candidate.id" :value="candidate.id">{{ candidate.name }} · {{ candidate.code }}</option>
              </select>
              <span v-else>{{ row.mapped ? row.erpRoleName : '未映射' }}</span>
            </td>
            <td class="px-4 py-3">
              <label v-if="editing[row.feishuRoleId]" class="inline-flex items-center gap-2"><input v-model="drafts[row.feishuRoleId].enabled" type="checkbox" :disabled="saving">启用</label>
              <span v-else class="rounded px-2 py-1 text-xs" :class="row.enabled && row.mapped ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ row.enabled && row.mapped ? '已启用' : row.mapped ? '已停用' : '未配置' }}</span>
            </td>
            <td class="px-4 py-3 text-slate-600">{{ row.memberCount }} 名成员</td>
            <td class="px-4 py-3 text-slate-500">{{ formatTime(row.lastSyncedAt) }}</td>
            <td class="max-w-[220px] px-4 py-3 text-rose-600">{{ row.lastError || '—' }}</td>
            <td class="px-4 py-3">
              <div v-if="editing[row.feishuRoleId]" class="flex gap-2">
                <button :data-testid="`save-feishu-mapping-${row.feishuRoleId}`" type="button" class="text-[#536dff] disabled:opacity-50" :disabled="saving || !drafts[row.feishuRoleId].erpRoleId" @click="save(row)">保存</button>
                <button type="button" class="text-slate-500" :disabled="saving" @click="cancelEdit(row.feishuRoleId)">取消</button>
              </div>
              <div v-else-if="canManage" class="flex gap-3">
                <button :data-testid="`edit-feishu-mapping-${row.feishuRoleId}`" type="button" class="text-[#536dff]" :disabled="saving" @click="beginEdit(row)">{{ row.mapped ? '编辑' : '配置' }}</button>
                <button v-if="row.mapped" :data-testid="`delete-feishu-mapping-${row.feishuRoleId}`" type="button" class="text-rose-600" :disabled="saving" @click="remove(row)">删除</button>
              </div>
              <span v-else class="text-slate-400">只读</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
