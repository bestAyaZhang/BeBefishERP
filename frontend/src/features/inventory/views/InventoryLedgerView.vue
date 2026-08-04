<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import { Search } from 'lucide-vue-next';
import { inventoryService } from '../inventoryService';
import type { InventoryLedger, InventoryService, PageResult } from '../types';
import { masterdataService } from '../../masterdata/masterdataService';
import type { MasterdataService, Warehouse } from '../../masterdata/types';

const service = inject<InventoryService>('inventoryService', inventoryService);
const masterdata = inject<MasterdataService>('masterdataService', masterdataService);
const result = ref<PageResult<InventoryLedger>>({ records: [], page: 1, pageSize: 20, total: 0 });
const warehouses = ref<Warehouse[]>([]);
const warehouseId = ref('');
const skuId = ref('');
const direction = ref<'' | 'increase' | 'decrease'>('');
const loading = ref(false);
const errorMessage = ref('');

function numberOrUndefined(value: string) {
  return value ? Number(value) : undefined;
}

async function load(page = 1) {
  loading.value = true;
  errorMessage.value = '';
  try {
    result.value = await service.listLedger({
      page,
      size: result.value.pageSize,
      warehouseId: numberOrUndefined(warehouseId.value),
      skuId: numberOrUndefined(skuId.value),
      direction: direction.value || undefined
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '库存流水加载失败';
  } finally {
    loading.value = false;
  }
}

function warehouseName(id: number) {
  return warehouses.value.find((warehouse) => warehouse.id === id)?.warehouseName ?? `仓库 ${id}`;
}

async function initialize() {
  try {
    warehouses.value = await masterdata.listActiveWarehouses();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '仓库加载失败';
  }
  await load();
}

onMounted(() => void initialize());
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5">
    <header>
      <p class="text-xs font-black uppercase text-[#536dff]">Inventory</p>
      <h1 class="mt-1 text-2xl font-black text-[#25314d]">库存流水</h1>
      <p class="mt-2 text-sm font-medium text-slate-400">记录每一次库存变化的前后数量、来源单据和操作人。</p>
    </header>

    <div class="flex flex-col gap-3 rounded-[18px] border border-slate-200 bg-white p-4 shadow-[0_10px_30px_rgba(31,45,74,0.03)] sm:flex-row sm:items-center">
      <input v-model="skuId" inputmode="numeric" class="h-11 min-w-0 flex-1 rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" placeholder="SKU ID" @keyup.enter="load(1)" />
      <select v-model="warehouseId" data-testid="inventory-ledger-warehouse" class="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50">
        <option value="">全部仓库</option>
        <option v-for="warehouse in warehouses" :key="warehouse.id" :value="String(warehouse.id)">{{ warehouse.warehouseName }}</option>
      </select>
      <select v-model="direction" class="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50">
        <option value="">全部方向</option>
        <option value="increase">增加</option>
        <option value="decrease">减少</option>
      </select>
      <button data-testid="inventory-ledger-search" type="button" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-md shadow-blue-100 transition hover:bg-[#435be3]" @click="load(1)">
        <Search class="h-4 w-4" aria-hidden="true" />
        查询
      </button>
    </div>

    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>

    <section class="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[1100px] border-collapse text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black text-slate-400">
            <tr>
              <th class="border-b border-slate-100 px-5 py-3">发生时间</th>
              <th class="border-b border-slate-100 px-5 py-3">SKU</th>
              <th class="border-b border-slate-100 px-5 py-3">变动</th>
              <th class="border-b border-slate-100 px-5 py-3">数量变化</th>
              <th class="border-b border-slate-100 px-5 py-3">来源单据</th>
              <th class="border-b border-slate-100 px-5 py-3">操作人</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-slate-700">
            <tr v-if="loading"><td colspan="6" class="h-40 px-5 text-center font-bold text-slate-400">正在加载...</td></tr>
            <tr v-else-if="result.records.length === 0"><td colspan="6" class="h-40 px-5 text-center font-bold text-slate-400">暂无库存流水</td></tr>
            <tr v-for="record in result.records" v-else :key="record.id" class="bg-white transition hover:bg-slate-50">
              <td class="whitespace-nowrap px-5 py-4 font-bold text-slate-500">{{ record.occurredAt.replace('T', ' ') }}</td>
              <td class="px-5 py-4 font-black text-[#25314d]">SKU {{ record.skuId }}</td>
              <td class="px-5 py-4"><span class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="record.direction === 'increase' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'">{{ record.direction === 'increase' ? '增加' : '减少' }}</span></td>
              <td class="px-5 py-4 font-bold text-[#25314d]">{{ record.beforeQuantity }} → {{ record.afterQuantity }} <span class="text-slate-400">({{ record.direction === 'increase' ? '+' : '-' }}{{ record.quantity }})</span></td>
              <td class="px-5 py-4 font-bold text-slate-600">{{ record.sourceNo || '-' }}</td>
              <td class="px-5 py-4 font-bold text-slate-500">{{ record.operatorMobile || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-sm font-bold text-slate-500">
        <span>共 {{ result.total }} 条</span>
        <span>{{ result.page }} / {{ Math.max(1, Math.ceil(result.total / result.pageSize)) }}</span>
      </footer>
    </section>
  </section>
</template>
