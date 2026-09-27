<script setup lang="ts">
import { ClipboardCheck, Plus, RotateCcw, Search, X } from 'lucide-vue-next';
import { computed, inject, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { masterdataService as defaultMasterdataService } from '../../masterdata/masterdataService';
import type { MasterdataService, Warehouse } from '../../masterdata/types';
import { currentUser } from '../../../services/authSession';
import { stocktakeService as defaultService } from '../stocktake/stocktakeService';
import type { StocktakeService, StocktakeStatus, StocktakeTaskListResult } from '../stocktake/types';
import { warehouseInventoryService as defaultInventoryService } from '../warehouseCanvas/warehouseInventoryService';
import type { WarehouseInventoryService } from '../warehouseCanvas/warehouseInventoryService';
import { warehouseLayoutService as defaultLayoutService } from '../warehouseCanvas/warehouseLayoutService';
import type { WarehouseLayoutService } from '../warehouseCanvas/warehouseLayoutService';

const service = inject<StocktakeService>('stocktakeService', defaultService);
const masterdata = inject<MasterdataService>('masterdataService', defaultMasterdataService);
const layouts = inject<WarehouseLayoutService>('warehouseLayoutService', defaultLayoutService);
const inventory = inject<WarehouseInventoryService>('warehouseInventoryService', defaultInventoryService);
const router = useRouter();
const loading = ref(true);
const error = ref('');
const warehouseId = ref('');
const status = ref('');
const keyword = ref('');
const enabledWarehouses = ref<Warehouse[]>([]);
const createOpen = ref(false);
const createWarehouseId = ref('');
const createScopeMode = ref<'all' | 'selected'>('all');
const selectedPileIds = ref<string[]>([]);
const availablePiles = ref<Array<{ id: string; code: string; name: string; skuCount: number; units: number }>>([]);
const pilesLoading = ref(false);
const pilesError = ref('');
const creating = ref(false);
const data = ref<StocktakeTaskListResult>({
  summary: { inProgress: 0, awaitingRecount: 0, awaitingApproval: 0, completedThisMonth: 0, discrepancyItems: 0, accuracyRate: 0 },
  tasks: []
});

const statusLabels: Record<StocktakeStatus, string> = {
  not_started: '未开始',
  in_progress: '盘点中',
  awaiting_recount: '待复盘',
  awaiting_approval: '待审批',
  completed: '已完成'
};

const statusClasses: Record<StocktakeStatus, string> = {
  not_started: 'bg-slate-100 text-slate-500',
  in_progress: 'bg-indigo-50 text-[#536dff]',
  awaiting_recount: 'bg-amber-50 text-amber-700',
  awaiting_approval: 'bg-rose-50 text-rose-600',
  completed: 'bg-emerald-50 text-emerald-700'
};

const actionLabels: Record<StocktakeStatus, string> = {
  not_started: '开始',
  in_progress: '查看',
  awaiting_recount: '复盘',
  awaiting_approval: '审批',
  completed: '详情'
};

const warehouses = computed(() => enabledWarehouses.value.length
  ? enabledWarehouses.value.map((warehouse) => [warehouse.id, warehouse.warehouseName] as const)
  : Array.from(new Map(data.value.tasks.map((task) => [task.warehouseId, task.warehouseName]))));
const canCreate = computed(() => !!createWarehouseId.value
  && !pilesLoading.value
  && (createScopeMode.value === 'all' || selectedPileIds.value.length > 0));
const createOwnerName = computed(() => currentUser.value?.displayName?.trim()
  || currentUser.value?.mobile
  || '当前登录用户');

watch(createWarehouseId, async (value) => {
  selectedPileIds.value = [];
  availablePiles.value = [];
  pilesError.value = '';
  if (!value) return;
  const warehouseId = Number(value);
  pilesLoading.value = true;
  try {
    const [layout, inventoryLayout] = await Promise.all([
      layouts.load(warehouseId),
      inventory.load(warehouseId)
    ]);
    if (Number(createWarehouseId.value) !== warehouseId) return;
    const inventoryByPile = new Map<string, { skuIds: Set<number>; units: number }>();
    for (const allocation of inventoryLayout.allocations) {
      if (allocation.palletId === 'UNALLOCATED' || allocation.units <= 0) continue;
      const current = inventoryByPile.get(allocation.palletId) ?? { skuIds: new Set<number>(), units: 0 };
      current.skuIds.add(allocation.skuId);
      current.units += allocation.units;
      inventoryByPile.set(allocation.palletId, current);
    }
    availablePiles.value = (layout.document?.palletGroups ?? [])
      .filter((pallet) => inventoryByPile.has(pallet.id))
      .map((pallet) => ({
        id: pallet.id,
        code: pallet.code,
        name: pallet.name,
        skuCount: inventoryByPile.get(pallet.id)!.skuIds.size,
        units: inventoryByPile.get(pallet.id)!.units
      }));
  } catch (reason) {
    if (Number(createWarehouseId.value) === warehouseId) {
      pilesError.value = reason instanceof Error ? reason.message : '货物堆加载失败';
    }
  } finally {
    if (Number(createWarehouseId.value) === warehouseId) pilesLoading.value = false;
  }
});

onMounted(async () => {
  await Promise.all([
    load(),
    masterdata.listActiveWarehouses().then((values) => {
      enabledWarehouses.value = values.filter((warehouse) => warehouse.status === 'enabled');
    }).catch(() => undefined)
  ]);
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await service.listTasks({
      warehouseId: warehouseId.value ? Number(warehouseId.value) : undefined,
      status: (status.value || undefined) as StocktakeStatus | undefined,
      keyword: keyword.value || undefined
    });
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '盘点任务加载失败';
  } finally {
    loading.value = false;
  }
}

function reset() {
  warehouseId.value = '';
  status.value = '';
  keyword.value = '';
  void load();
}

function openTask(id: number) {
  void router.push({ name: 'inventory-stocktake-execution', params: { id } });
}

async function createTask() {
  if (!canCreate.value) return;
  creating.value = true;
  error.value = '';
  try {
    const task = await service.createTask({
      warehouseId: Number(createWarehouseId.value),
      blindCount: true,
      ...(createScopeMode.value === 'selected' ? { palletIds: [...selectedPileIds.value] } : {})
    });
    createOpen.value = false;
    await router.push({ name: 'inventory-stocktake-execution', params: { id: task.id } });
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '盘点任务创建失败';
  } finally {
    creating.value = false;
  }
}
</script>

<template>
  <section data-testid="stocktake-task-list" class="min-h-full bg-[#f6f7fb] px-5 py-5 text-[#25314d] lg:px-6">
    <div class="mx-auto flex max-w-[1440px] flex-col gap-4">
      <header class="flex items-start justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-black tracking-tight">库存盘点</h1>
            <span class="rounded-full border border-slate-200 bg-white px-2 py-1 text-xs font-semibold text-slate-400">{{ data.tasks.length }} 个任务</span>
          </div>
          <p class="mt-1 text-sm text-slate-500">创建盘点任务，按仓库、区域和货物堆核对实际库存。</p>
        </div>
        <button data-testid="create-stocktake" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-[#445ee8]" @click="createOpen = true">
          <Plus class="h-4 w-4" aria-hidden="true" />新建盘点
        </button>
      </header>

      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <article data-testid="stocktake-summary-in-progress" class="rounded-lg border border-slate-200 bg-white p-4">
          <div class="flex items-center gap-2 text-xs font-medium text-slate-500">进行中<span class="h-2 w-2 rounded-full bg-[#536dff]"></span></div>
          <strong class="mt-3 block text-2xl">{{ data.summary.inProgress }}</strong><span class="text-xs text-slate-400">覆盖多个仓库</span>
        </article>
        <article data-testid="stocktake-summary-recount" class="rounded-lg border border-slate-200 bg-white p-4">
          <div class="flex items-center gap-2 text-xs font-medium text-slate-500">待复盘<span class="h-2 w-2 rounded-full bg-amber-400"></span></div>
          <strong class="mt-3 block text-2xl">{{ data.summary.awaitingRecount }}</strong><span class="text-xs text-slate-400">存在 {{ data.summary.discrepancyItems }} 个差异项</span>
        </article>
        <article data-testid="stocktake-summary-approval" class="rounded-lg border border-slate-200 bg-white p-4">
          <div class="flex items-center gap-2 text-xs font-medium text-slate-500">待审批<span class="h-2 w-2 rounded-full bg-rose-400"></span></div>
          <strong class="mt-3 block text-2xl">{{ data.summary.awaitingApproval }}</strong><span class="text-xs text-slate-400">等待仓库负责人确认</span>
        </article>
        <article class="rounded-lg border border-slate-200 bg-white p-4">
          <div class="flex items-center gap-2 text-xs font-medium text-slate-500">本月已完成<span class="h-2 w-2 rounded-full bg-emerald-500"></span></div>
          <strong class="mt-3 block text-2xl">{{ data.summary.completedThisMonth }}</strong><span class="text-xs text-slate-400">账实相符率 {{ data.summary.accuracyRate }}%</span>
        </article>
      </div>

      <form class="grid items-end gap-3 rounded-lg border border-slate-200 bg-white p-3 md:grid-cols-[minmax(160px,1fr)_minmax(160px,1fr)_minmax(220px,1.4fr)_auto]" @submit.prevent="load">
        <label class="grid gap-1 text-xs font-medium text-slate-400">仓库
          <select v-model="warehouseId" class="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff]">
            <option value="">全部启用仓库</option><option v-for="([id, name]) in warehouses" :key="id" :value="id">{{ name }}</option>
          </select>
        </label>
        <label class="grid gap-1 text-xs font-medium text-slate-400">状态
          <select v-model="status" class="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm text-[#25314d] outline-none focus:border-[#536dff]">
            <option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option>
          </select>
        </label>
        <label class="grid gap-1 text-xs font-medium text-slate-400">搜索
          <span class="flex h-10 items-center gap-2 rounded-md border border-slate-200 px-3 focus-within:border-[#536dff]"><Search class="h-4 w-4" aria-hidden="true" /><input v-model="keyword" class="min-w-0 flex-1 border-0 bg-transparent text-sm outline-none" placeholder="盘点单号 / 负责人" /></span>
        </label>
        <button type="button" class="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-50 px-4 text-sm font-semibold text-slate-500 hover:bg-slate-100" @click="reset"><RotateCcw class="h-4 w-4" />重置</button>
      </form>

      <div class="min-h-[420px] overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <p v-if="error" class="p-6 text-sm text-rose-600">{{ error }}</p>
        <div v-else-if="loading" class="flex min-h-[320px] items-center justify-center text-sm text-slate-400">正在加载盘点任务…</div>
        <table v-else class="w-full min-w-[980px] border-collapse text-left text-sm">
          <thead class="h-12 bg-slate-50 text-xs font-semibold text-slate-500"><tr><th class="px-4">盘点单号</th><th>仓库</th><th>盘点范围</th><th>负责人</th><th>盘点进度</th><th>状态</th><th>创建时间</th><th class="pr-4">操作</th></tr></thead>
          <tbody>
            <tr v-for="task in data.tasks" :key="task.id" class="h-[68px] border-t border-slate-100 text-[13px]">
              <td class="px-4 font-medium">{{ task.taskNo }}</td><td>{{ task.warehouseName }}</td><td>{{ task.scopeLabel }}</td><td>{{ task.assigneeName }}</td>
              <td>{{ task.countedItems }} / {{ task.totalItems }}（{{ task.totalItems ? Math.round(task.countedItems / task.totalItems * 100) : 0 }}%）</td>
              <td><span class="inline-flex min-w-[76px] justify-center rounded-full px-3 py-1.5 text-xs font-semibold" :class="statusClasses[task.status]">{{ statusLabels[task.status] }}</span></td>
              <td>{{ task.createdAt.replace('T', ' ').slice(0, 16) }}</td>
              <td class="pr-4"><button :data-testid="`open-stocktake-${task.id}`" type="button" class="font-semibold text-[#536dff] hover:underline" @click="openTask(task.id)">{{ actionLabels[task.status] }}</button></td>
            </tr>
            <tr v-if="!data.tasks.length"><td colspan="8" class="py-16 text-center text-slate-400"><ClipboardCheck class="mx-auto mb-3 h-8 w-8" />暂无盘点任务</td></tr>
          </tbody>
        </table>
      </div>

      <div v-if="createOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" @click.self="createOpen = false">
        <form data-testid="stocktake-create-dialog" class="w-full max-w-2xl rounded-xl bg-white p-5 shadow-2xl" @submit.prevent="createTask">
          <div class="mb-5 flex items-start justify-between"><div><h2 class="text-lg font-black">新建盘点任务</h2><p class="mt-1 text-sm text-slate-500">系统将按当前货物堆实际库存生成盲盘快照。</p></div><button type="button" class="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="关闭" @click="createOpen = false"><X class="h-5 w-5" /></button></div>
          <div class="grid gap-4">
            <label class="grid gap-1.5 text-sm font-semibold">盘点仓库<select data-testid="stocktake-create-warehouse" v-model="createWarehouseId" required class="h-10 rounded-md border border-slate-200 bg-white px-3 font-normal outline-none focus:border-[#536dff]"><option value="" disabled>请选择启用仓库</option><option v-for="warehouse in enabledWarehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.warehouseName }}</option></select></label>
            <fieldset class="grid gap-2">
              <legend class="text-sm font-semibold">盘点货物堆</legend>
              <div class="grid grid-cols-2 gap-2 rounded-lg bg-slate-50 p-1">
                <label class="flex h-10 cursor-pointer items-center justify-center gap-2 rounded-md text-sm font-semibold" :class="createScopeMode === 'all' ? 'bg-white text-[#536dff] shadow-sm' : 'text-slate-500'"><input data-testid="stocktake-scope-all" v-model="createScopeMode" type="radio" value="all" class="sr-only" />全部货物堆</label>
                <label class="flex h-10 cursor-pointer items-center justify-center gap-2 rounded-md text-sm font-semibold" :class="createScopeMode === 'selected' ? 'bg-white text-[#536dff] shadow-sm' : 'text-slate-500'"><input data-testid="stocktake-scope-selected" v-model="createScopeMode" type="radio" value="selected" class="sr-only" />指定货物堆</label>
              </div>
              <p v-if="pilesLoading" class="rounded-lg border border-slate-200 px-3 py-4 text-center text-sm text-slate-400">正在加载有库存的货物堆…</p>
              <p v-else-if="pilesError" class="rounded-lg border border-rose-200 bg-rose-50 px-3 py-3 text-sm text-rose-600">{{ pilesError }}</p>
              <div v-else-if="createScopeMode === 'selected'" class="max-h-56 space-y-2 overflow-y-auto rounded-lg border border-slate-200 p-2">
                <label v-for="pallet in availablePiles" :key="pallet.id" class="flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2.5 transition" :class="selectedPileIds.includes(pallet.id) ? 'border-[#536dff] bg-indigo-50/60' : 'border-slate-100 hover:bg-slate-50'">
                  <input :data-testid="`stocktake-create-pile-${pallet.id}`" v-model="selectedPileIds" type="checkbox" :value="pallet.id" class="h-4 w-4 accent-[#536dff]" />
                  <span class="min-w-0 flex-1"><strong class="block text-sm">{{ pallet.code }}</strong><small class="block truncate text-xs text-slate-400">{{ pallet.name }}</small></span>
                  <span class="text-right text-xs text-slate-500"><strong class="block text-[#25314d]">{{ pallet.units }} 件</strong>{{ pallet.skuCount }} 种 SKU</span>
                </label>
                <p v-if="!availablePiles.length" class="py-4 text-center text-sm text-slate-400">该仓库暂无有库存的货物堆</p>
              </div>
              <p v-else class="text-xs text-slate-400">将盘点该仓库全部有库存的货物堆，共 {{ availablePiles.length }} 个。</p>
            </fieldset>
            <div data-testid="stocktake-create-owner" class="grid gap-1.5 text-sm font-semibold">盘点负责人<div class="flex h-10 items-center rounded-md border border-slate-200 bg-slate-50 px-3 font-normal text-[#25314d]">{{ createOwnerName }}<span class="ml-2 text-xs text-slate-400">谁创建，谁负责</span></div></div>
            <label class="flex items-center gap-2 rounded-md bg-indigo-50 px-3 py-2.5 text-sm text-indigo-700"><input checked disabled type="checkbox" class="h-4 w-4 accent-[#536dff]" />启用盲盘，初盘人员不可查看账面数量</label>
          </div>
          <div class="mt-6 flex justify-end gap-3"><button type="button" class="h-10 rounded-md px-4 text-sm font-semibold text-slate-500 hover:bg-slate-50" @click="createOpen = false">取消</button><button data-testid="stocktake-create-submit" type="button" class="h-10 rounded-md bg-[#536dff] px-5 text-sm font-semibold text-white disabled:opacity-40" :disabled="creating || !canCreate" @click="createTask">{{ creating ? '创建中…' : '创建任务' }}</button></div>
        </form>
      </div>
    </div>
  </section>
</template>
