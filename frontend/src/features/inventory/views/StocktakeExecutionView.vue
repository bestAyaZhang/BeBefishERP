<script setup lang="ts">
import { ArrowLeft, Search } from 'lucide-vue-next';
import { computed, inject, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import FigmaSelect from '../../masterdata/components/FigmaSelect.vue';
import { stocktakeService as defaultService } from '../stocktake/stocktakeService';
import type { StocktakeCountInput, StocktakeService, StocktakeTaskDetails } from '../stocktake/types';

const props = defineProps<{ taskId: number | string }>();
const service = inject<StocktakeService>('stocktakeService', defaultService);
const router = useRouter();
const task = ref<StocktakeTaskDetails | null>(null);
const counts = ref<Record<number, string>>({});
const keyword = ref('');
const area = ref('');
const pile = ref('');
const onlyDifferences = ref(false);
const loading = ref(true);
const saving = ref(false);
const notice = ref('');
const error = ref('');

const filteredItems = computed(() => (task.value?.items ?? []).filter((item) => {
  if (area.value && item.zoneName !== area.value) return false;
  if (pile.value && item.palletId !== pile.value) return false;
  if (onlyDifferences.value && item.difference === 0) return false;
  const value = keyword.value.trim().toLowerCase();
  return !value || [item.zoneName, item.skuCode, item.palletLabel, item.productName, item.skuName]
    .filter(Boolean)
    .some((text) => text!.toLowerCase().includes(value));
}));
const areas = computed(() => Array.from(new Set((task.value?.items ?? []).map((item) => item.zoneName).filter(Boolean))));
const areaOptions = computed(() => [
  { value: '', label: '全部区域' },
  ...areas.value.map((name) => ({ value: name!, label: name! }))
]);
const pileOptions = computed(() => [
  { value: '', label: '全部货物堆' },
  ...Array.from(new Map((task.value?.items ?? []).map((item) => [item.palletId, item.palletLabel])))
    .map(([value, label]) => ({ value, label }))
]);
const initialMode = computed(() => task.value?.status === 'not_started' || task.value?.status === 'in_progress');
const recountMode = computed(() => task.value?.status === 'awaiting_recount');
const approvalMode = computed(() => task.value?.status === 'awaiting_approval');
const actionableItems = computed(() => recountMode.value
  ? (task.value?.items ?? []).filter((item) => item.difference !== 0)
  : (task.value?.items ?? []));
const allCounted = computed(() => !!actionableItems.value.length && actionableItems.value.every((item) => {
  const value = counts.value[item.id];
  return value !== undefined && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0;
}));
const progress = computed(() => task.value?.totalItems ? Math.round(task.value.countedItems / task.value.totalItems * 100) : 0);

onMounted(load);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    task.value = await service.getTask(Number(props.taskId));
    counts.value = Object.fromEntries(task.value.items.map((item) => [
      item.id,
      (task.value?.status === 'awaiting_recount' ? item.recountQuantity : item.firstCountQuantity)?.toString() ?? ''
    ]));
    if (task.value.status === 'awaiting_recount') onlyDifferences.value = true;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '盘点任务加载失败';
  } finally {
    loading.value = false;
  }
}

function payload(): StocktakeCountInput[] {
  return (task.value?.items ?? []).flatMap((item) => {
    const value = counts.value[item.id];
    return value === undefined || value === '' ? [] : [{ itemId: item.id, quantity: Number(value) }];
  });
}

async function saveDraft() {
  if (!task.value) return;
  saving.value = true;
  error.value = '';
  try {
    task.value = await service.saveDraft(task.value.id, payload());
    notice.value = '盘点草稿已保存';
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '草稿保存失败';
  } finally {
    saving.value = false;
  }
}

async function submitInitial() {
  if (!task.value || !allCounted.value) return;
  saving.value = true;
  error.value = '';
  try {
    task.value = await service.submitInitial(task.value.id, payload());
    notice.value = task.value.status === 'awaiting_recount' ? '初盘已提交，差异项已进入复盘' : '初盘已提交，任务已进入审批';
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '初盘提交失败';
  } finally {
    saving.value = false;
  }
}

async function submitRecount() {
  if (!task.value || !allCounted.value) return;
  saving.value = true;
  error.value = '';
  try {
    const differenceIds = new Set(actionableItems.value.map((item) => item.id));
    task.value = await service.submitRecount(task.value.id, payload().filter((entry) => differenceIds.has(entry.itemId)));
    notice.value = '复盘已提交，任务已进入审批';
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '复盘提交失败';
  } finally {
    saving.value = false;
  }
}

async function approve() {
  if (!task.value) return;
  saving.value = true;
  error.value = '';
  try {
    task.value = await service.approve(task.value.id);
    notice.value = '盘点已批准，库存调整与流水已生成';
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '盘点审批失败';
  } finally {
    saving.value = false;
  }
}

function stateClass(item: StocktakeTaskDetails['items'][number]) {
  if (item.status === 'matched' || item.status === 'approved') return 'border-emerald-400 bg-emerald-50/30';
  if (item.status === 'recount_required') return 'border-rose-400 bg-rose-50/30';
  if (item.difference !== null && item.difference !== 0) return 'border-amber-400 bg-amber-50/30';
  if (item.firstCountQuantity !== null) return 'border-[#536dff] bg-indigo-50/25';
  return 'border-slate-300 bg-white';
}
</script>

<template>
  <section class="min-h-full bg-[#f6f7fb] px-5 py-5 text-[#25314d] lg:px-6">
    <div v-if="loading" class="flex min-h-[420px] items-center justify-center text-sm text-slate-400">正在加载盘点任务…</div>
    <div v-else-if="error && !task" class="mx-auto max-w-5xl rounded-lg border border-rose-200 bg-white p-6 text-rose-600">{{ error }}</div>
    <div v-else-if="task" class="mx-auto flex max-w-[1440px] flex-col gap-4">
      <header class="flex items-start justify-between gap-4">
        <div class="flex items-start gap-3">
          <button type="button" class="mt-1 flex h-8 w-8 items-center justify-center rounded-md text-slate-500 hover:bg-white" aria-label="返回盘点列表" @click="router.push({ name: 'inventory-stocktakes' })"><ArrowLeft class="h-5 w-5" /></button>
          <div><h1 class="text-2xl font-black">盘点执行 · {{ task.taskNo }}</h1><p class="mt-1 text-sm text-slate-500">{{ task.warehouseName }} · {{ task.scopeLabel }} · <span data-testid="stocktake-mode">{{ task.blindCount ? '盲盘模式' : '明盘模式' }}</span></p></div>
        </div>
        <span class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-500">{{ task.countedItems }} / {{ task.totalItems }} 已盘</span>
      </header>

      <article class="rounded-lg border border-slate-200 bg-white p-4">
        <div class="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
          <div><h2 class="font-bold">{{ task.taskNo }} · {{ task.warehouseName }}</h2><p class="mt-2 text-xs text-slate-500">盘点范围：{{ task.scopeLabel }}　负责人：{{ task.assigneeName }}</p><p class="mt-1 text-xs text-slate-400">{{ initialMode ? '盲盘开启：初盘人员不可查看账面数量，提交后自动计算差异。' : recountMode ? '复盘阶段：仅对初盘差异项重新录入实际数量。' : approvalMode ? '审批阶段：确认复盘结果后生成库存调整与流水。' : '盘点已经完成并归档。' }}</p></div>
          <div><div class="mb-2 flex justify-between text-xs"><span class="text-slate-500">盘点进度</span><strong>{{ task.countedItems }} / {{ task.totalItems }} · {{ progress }}%</strong></div><div class="h-2 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-[#536dff]" :style="{ width: `${progress}%` }"></div></div></div>
        </div>
      </article>

      <div class="grid gap-3 rounded-lg border border-slate-200 bg-white p-3 md:grid-cols-[180px_180px_minmax(240px,1fr)_160px_auto]">
        <FigmaSelect v-model="area" class="z-30" :options="areaOptions" label="" accessible-label="筛选盘点区域" test-id-prefix="stocktake-area-filter" trigger-test-id="stocktake-area-filter-trigger" inline />
        <FigmaSelect v-model="pile" class="z-20" :options="pileOptions" label="" accessible-label="筛选盘点货物堆" test-id-prefix="stocktake-pile-filter" trigger-test-id="stocktake-pile-filter-trigger" inline />
        <label class="flex h-10 items-center gap-2 rounded-md border border-slate-200 px-3 focus-within:border-[#536dff]"><Search class="h-4 w-4 text-slate-400" /><input v-model="keyword" class="min-w-0 flex-1 border-0 bg-transparent text-sm outline-none" placeholder="SKU / 库区 / 货物堆" /></label>
        <label class="flex h-10 items-center gap-2 rounded-md border border-slate-200 px-3 text-sm"><input v-model="onlyDifferences" type="checkbox" class="h-4 w-4 accent-[#536dff]" />仅看差异项</label>
        <span class="self-center text-right text-xs text-slate-500">剩余 {{ Math.max(0, task.totalItems - task.countedItems) }} 项 · 差异 {{ task.differenceItems }} 项</span>
      </div>

      <article class="rounded-lg border border-slate-200 bg-white p-4">
        <div class="mb-3 flex items-center justify-between"><h2 class="font-bold">盘点明细</h2><span class="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-[#536dff]">{{ initialMode && task.blindCount ? '盲盘 · 账面数量已隐藏' : recountMode ? '差异复盘' : approvalMode ? '等待审批' : '已完成' }}</span></div>
        <div class="grid grid-cols-[88px_110px_minmax(160px,1.4fr)_110px_110px_110px_110px] gap-2 px-3 pb-2 text-[11px] font-medium text-slate-400"><span>库区</span><span>货堆</span><span>SKU</span><span>包装层级</span><span>账面数量</span><span>{{ recountMode ? '复盘数量' : '初盘' }}</span><span>差异</span></div>
        <div class="space-y-2">
          <div v-for="item in filteredItems" :key="item.id" class="grid min-h-[58px] grid-cols-[88px_110px_minmax(160px,1.4fr)_110px_110px_110px_110px] items-center gap-2 rounded-md border-2 border-dashed px-3 text-xs" :class="stateClass(item)">
            <span>{{ item.zoneName || '全仓' }}</span><span class="font-semibold">{{ item.palletLabel }}</span><span><strong class="block">{{ item.skuCode }}</strong><small class="text-slate-400">{{ item.productName }} · {{ item.skuName }}</small></span>
            <span>{{ item.unitsPerCase ? `箱装 / ${item.unitsPerCase}件` : '按个' }}</span>
            <span class="font-semibold">{{ initialMode && task.blindCount ? '盲盘隐藏' : item.bookQuantity }}</span>
            <input v-model="counts[item.id]" :data-testid="`count-input-${item.id}`" type="number" min="0" step="1" class="h-9 w-24 rounded-md border border-slate-200 bg-white px-2 font-semibold outline-none focus:border-[#536dff] disabled:bg-slate-50 disabled:text-slate-500" :disabled="approvalMode || task.status === 'completed' || (recountMode && item.difference === 0)" placeholder="未录入" />
            <span :class="item.difference === null || item.difference === 0 ? 'text-slate-400' : item.difference > 0 ? 'text-emerald-600' : 'text-rose-600'">{{ item.difference === null ? '—' : item.difference > 0 ? `+${item.difference}` : item.difference }}</span>
          </div>
        </div>
        <div class="mt-4 flex items-center justify-between border-t border-slate-100 pt-4"><div><p v-if="notice" class="text-sm font-medium text-emerald-600">{{ notice }}</p><p v-if="error" class="text-sm font-medium text-rose-600">{{ error }}</p></div><div class="flex gap-3"><template v-if="initialMode"><button data-testid="save-stocktake-draft" type="button" class="h-10 rounded-md bg-slate-50 px-5 text-sm font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-50" :disabled="saving" @click="saveDraft">保存草稿</button><button data-testid="submit-initial" type="button" class="h-10 rounded-md bg-[#536dff] px-5 text-sm font-semibold text-white hover:bg-[#445ee8] disabled:cursor-not-allowed disabled:opacity-40" :disabled="saving || !allCounted" @click="submitInitial">提交初盘</button></template><button v-else-if="recountMode" data-testid="submit-recount" type="button" class="h-10 rounded-md bg-[#536dff] px-5 text-sm font-semibold text-white disabled:opacity-40" :disabled="saving || !allCounted" @click="submitRecount">提交复盘</button><button v-else-if="approvalMode" data-testid="approve-stocktake" type="button" class="h-10 rounded-md bg-emerald-600 px-5 text-sm font-semibold text-white disabled:opacity-40" :disabled="saving" @click="approve">批准并调整库存</button></div></div>
      </article>
    </div>
  </section>
</template>
