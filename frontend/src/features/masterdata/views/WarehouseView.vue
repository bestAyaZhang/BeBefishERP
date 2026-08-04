<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import MasterdataFilterBar from '../components/MasterdataFilterBar.vue';
import MasterdataFormDrawer from '../components/MasterdataFormDrawer.vue';
import MasterdataPageHeader from '../components/MasterdataPageHeader.vue';
import MasterdataTable from '../components/MasterdataTable.vue';
import { masterdataService } from '../masterdataService';
import type { MasterdataRow, MasterdataService, PageResult, RecordStatus, SaveWarehousePayload, Warehouse } from '../types';

const service = inject<MasterdataService>('masterdataService', masterdataService);
const result = ref<PageResult<Warehouse>>({ records: [], page: 1, pageSize: 20, total: 0 });
const keyword = ref('');
const status = ref<RecordStatus | ''>('');
const loading = ref(false);
const errorMessage = ref('');
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const blankForm = (): SaveWarehousePayload => ({ warehouseName: '', address: '', defaultWarehouse: false, remark: '' });
const form = ref<SaveWarehousePayload>(blankForm());
const columns = [{ key: 'warehouseName', label: '仓库名称' }, { key: 'address', label: '地址' }, { key: 'defaultWarehouse', label: '默认仓' }, { key: 'status', label: '状态' }];

function asWarehouse(row: MasterdataRow) { return row as Warehouse; }
function isDefaultWarehouse(row: MasterdataRow) { return asWarehouse(row).defaultWarehouse; }
function resetForm() { editingId.value = null; form.value = blankForm(); }
async function load(page = result.value.page) { loading.value = true; errorMessage.value = ''; try { result.value = await service.listWarehouses({ page, size: result.value.pageSize, keyword: keyword.value, status: status.value || undefined }); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '仓库加载失败'; } finally { loading.value = false; } }
function openCreate() { resetForm(); formOpen.value = true; }
function edit(warehouse: Warehouse) { editingId.value = warehouse.id; form.value = { warehouseName: warehouse.warehouseName, address: warehouse.address, defaultWarehouse: warehouse.defaultWarehouse, remark: warehouse.remark }; formOpen.value = true; }
async function save() { try { if (editingId.value === null) await service.createWarehouse(form.value); else await service.updateWarehouse(editingId.value, form.value); formOpen.value = false; resetForm(); await load(1); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '保存仓库失败'; } }
async function toggleStatus(warehouse: Warehouse) { try { await service.changeWarehouseStatus(warehouse.id, warehouse.status === 'enabled' ? 'disabled' : 'enabled'); await load(); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '更新状态失败'; } }
async function setDefault(warehouse: Warehouse) { try { await service.setDefaultWarehouse(warehouse.id); await load(); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '设置默认仓失败'; } }
onMounted(() => void load(1));
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5">
    <MasterdataPageHeader title="仓库管理" description="维护仓库档案、默认仓和启停状态，支撑采购入库与销售出库。" button-label="新增仓库" button-test-id="add-warehouse" @create="openCreate" />
    <MasterdataFilterBar :keyword="keyword" :status="status" placeholder="搜索仓库名称" @update:keyword="keyword = $event" @update:status="status = $event" @search="load(1)" />
    <MasterdataFormDrawer v-if="formOpen" :title="editingId === null ? '新增仓库' : '编辑仓库'" description="仓库档案字段较少，使用右侧抽屉快速录入。" @close="formOpen = false" @submit="save">
      <div class="grid gap-4">
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>仓库名称</span><input data-testid="warehouse-name" v-model="form.warehouseName" required class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>地址</span><input v-model="form.address" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="flex items-center gap-3 rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold text-slate-500"><input v-model="form.defaultWarehouse" type="checkbox" class="h-4 w-4 accent-[#536dff]" /><span>设为默认仓库</span></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>备注</span><textarea v-model="form.remark" rows="4" class="w-full resize-y rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"></textarea></label>
      </div>
      <template #actions><button type="button" class="h-11 rounded-xl border border-slate-200 px-5 text-sm font-black text-slate-500 transition hover:border-slate-300 hover:text-[#25314d]" @click="formOpen = false">取消</button><button data-testid="save-warehouse" type="button" class="h-11 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-[0_10px_22px_rgba(83,109,255,0.22)] transition hover:bg-[#4560eb]" @click="save">保存</button></template>
    </MasterdataFormDrawer>
    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>
    <MasterdataTable :records="result.records" :columns="columns" :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @page="load"><template #cell-defaultWarehouse="{ record }"><span :class="isDefaultWarehouse(record) ? 'text-emerald-700' : 'text-slate-400'">{{ isDefaultWarehouse(record) ? '默认仓' : '-' }}</span></template><template #actions="{ record }"><div class="flex min-w-max justify-end gap-3 whitespace-nowrap text-sm"><button v-if="!isDefaultWarehouse(record) && record.status === 'enabled'" :data-testid="`set-default-warehouse-${record.id}`" type="button" class="shrink-0 whitespace-nowrap text-slate-700 hover:underline" @click="setDefault(asWarehouse(record))">设为默认</button><button type="button" class="shrink-0 whitespace-nowrap text-slate-700 hover:underline" @click="edit(asWarehouse(record))">编辑</button><button :disabled="isDefaultWarehouse(record)" :data-testid="`warehouse-status-${record.id}`" type="button" class="shrink-0 whitespace-nowrap text-slate-500 hover:underline disabled:cursor-not-allowed disabled:opacity-40" @click="toggleStatus(asWarehouse(record))">{{ record.status === 'enabled' ? '停用' : '启用' }}</button></div></template></MasterdataTable>
  </section>
</template>
