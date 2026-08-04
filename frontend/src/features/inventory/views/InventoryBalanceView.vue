<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import { Search } from 'lucide-vue-next';
import { inventoryService } from '../inventoryService';
import type { InventoryBalance, InventoryService, PageResult } from '../types';
import { masterdataService } from '../../masterdata/masterdataService';
import type { MasterdataService, Warehouse } from '../../masterdata/types';

const service = inject<InventoryService>('inventoryService', inventoryService);
const masterdata = inject<MasterdataService>('masterdataService', masterdataService);
const result = ref<PageResult<InventoryBalance>>({ records: [], page: 1, pageSize: 20, total: 0 });
const warehouses = ref<Warehouse[]>([]);
const keyword = ref('');
const warehouseId = ref('');
const loading = ref(false);
const errorMessage = ref('');

function numberOrUndefined(value: string) {
  return value ? Number(value) : undefined;
}

async function load(page = 1) {
  loading.value = true;
  errorMessage.value = '';
  try {
    result.value = await service.listBalances({
      page,
      size: result.value.pageSize,
      keyword: keyword.value.trim() || undefined,
      warehouseId: numberOrUndefined(warehouseId.value)
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '库存余额加载失败';
  } finally {
    loading.value = false;
  }
}

function changePage(page: number) {
  if (page >= 1 && page <= Math.max(1, Math.ceil(result.value.total / result.value.pageSize))) void load(page);
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
    <header class="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div class="min-w-0">
        <p class="text-xs font-black uppercase text-[#536dff]">Inventory</p>
        <h1 class="mt-1 text-2xl font-black text-[#25314d]">库存余额</h1>
        <p class="mt-2 text-sm font-medium text-slate-400">按仓库和商品查看当前可用库存，数量只读。</p>
      </div>
    </header>

    <div class="flex flex-col gap-3 rounded-[18px] border border-slate-200 bg-white p-4 shadow-[0_10px_30px_rgba(31,45,74,0.03)] sm:flex-row sm:items-center">
      <label class="flex h-11 min-w-0 flex-1 items-center gap-2 rounded-xl border border-slate-200 px-3 focus-within:border-[#536dff] focus-within:ring-4 focus-within:ring-blue-50">
        <Search class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
        <input v-model="keyword" class="h-full min-w-0 flex-1 border-0 bg-transparent text-sm font-bold text-[#25314d] outline-none placeholder:text-slate-400" placeholder="搜索货号、SKU、商品名称" @keyup.enter="load(1)" />
      </label>
      <select v-model="warehouseId" data-testid="inventory-balance-warehouse" class="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none focus:border-[#536dff] focus:ring-4 focus:ring-blue-50">
        <option value="">全部仓库</option>
        <option v-for="warehouse in warehouses" :key="warehouse.id" :value="String(warehouse.id)">{{ warehouse.warehouseName }}</option>
      </select>
      <button data-testid="inventory-balance-search" type="button" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-md shadow-blue-100 transition hover:bg-[#435be3]" @click="load(1)">
        <Search class="h-4 w-4" aria-hidden="true" />
        查询
      </button>
    </div>

    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>

    <section class="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[980px] border-collapse text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black text-slate-400">
            <tr>
              <th class="border-b border-slate-100 px-5 py-3">商品信息</th>
              <th class="border-b border-slate-100 px-5 py-3">规格</th>
              <th class="border-b border-slate-100 px-5 py-3">仓库</th>
              <th class="border-b border-slate-100 px-5 py-3 text-right">库存数量</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-slate-700">
            <tr v-if="loading"><td colspan="4" class="h-40 px-5 text-center font-bold text-slate-400">正在加载...</td></tr>
            <tr v-else-if="result.records.length === 0"><td colspan="4" class="h-40 px-5 text-center font-bold text-slate-400">暂无库存数据</td></tr>
            <tr v-for="record in result.records" v-else :key="`${record.warehouseId}-${record.skuId}`" class="bg-white transition hover:bg-slate-50">
              <td class="px-5 py-4 align-middle">
                <div class="font-black text-[#25314d]">{{ record.productName || '-' }}</div>
                <div class="mt-1 text-xs font-bold text-slate-400">{{ record.itemNo || '-' }} · {{ record.skuName || record.skuCode || '-' }}</div>
              </td>
              <td class="max-w-72 px-5 py-4 align-middle font-bold text-[#25314d]">{{ record.specification || '-' }}</td>
              <td class="px-5 py-4 align-middle font-bold text-slate-600">{{ warehouseName(record.warehouseId) }}</td>
              <td class="px-5 py-4 text-right align-middle text-base font-black text-[#25314d]">{{ record.quantity }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-sm font-bold text-slate-500">
        <span>共 {{ result.total }} 条</span>
        <div class="flex items-center gap-3">
          <button type="button" class="h-8 rounded-lg border border-slate-200 px-3 disabled:cursor-not-allowed disabled:text-slate-300" :disabled="result.page <= 1" @click="changePage(result.page - 1)">上一页</button>
          <span>{{ result.page }} / {{ Math.max(1, Math.ceil(result.total / result.pageSize)) }}</span>
          <button type="button" class="h-8 rounded-lg border border-slate-200 px-3 disabled:cursor-not-allowed disabled:text-slate-300" :disabled="result.page >= Math.max(1, Math.ceil(result.total / result.pageSize))" @click="changePage(result.page + 1)">下一页</button>
        </div>
      </footer>
    </section>
  </section>
</template>
