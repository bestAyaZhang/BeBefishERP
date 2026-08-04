<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import MasterdataFilterBar from '../components/MasterdataFilterBar.vue';
import MasterdataFormDrawer from '../components/MasterdataFormDrawer.vue';
import MasterdataPageHeader from '../components/MasterdataPageHeader.vue';
import MasterdataTable from '../components/MasterdataTable.vue';
import { masterdataService } from '../masterdataService';
import type { MasterdataRow, MasterdataService, PageResult, RecordStatus, SaveSupplierPayload, Supplier } from '../types';

const service = inject<MasterdataService>('masterdataService', masterdataService);
const result = ref<PageResult<Supplier>>({ records: [], page: 1, pageSize: 20, total: 0 });
const keyword = ref('');
const status = ref<RecordStatus | ''>('');
const loading = ref(false);
const errorMessage = ref('');
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const blankForm = (): SaveSupplierPayload => ({ supplierName: '', contactPerson: '', mobile: '', telephone: '', address: '', remark: '' });
const form = ref<SaveSupplierPayload>(blankForm());
const columns = [{ key: 'supplierName', label: '供应商名称' }, { key: 'contactPerson', label: '联系人' }, { key: 'mobile', label: '手机' }, { key: 'address', label: '地址' }, { key: 'status', label: '状态' }];

function asSupplier(row: MasterdataRow) { return row as Supplier; }
function resetForm() { editingId.value = null; form.value = blankForm(); }
async function load(page = result.value.page) { loading.value = true; errorMessage.value = ''; try { result.value = await service.listSuppliers({ page, size: result.value.pageSize, keyword: keyword.value, status: status.value || undefined }); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '供应商加载失败'; } finally { loading.value = false; } }
function openCreate() { resetForm(); formOpen.value = true; }
function edit(supplier: Supplier) { editingId.value = supplier.id; form.value = { supplierName: supplier.supplierName, contactPerson: supplier.contactPerson, mobile: supplier.mobile, telephone: supplier.telephone, address: supplier.address, remark: supplier.remark }; formOpen.value = true; }
async function save() { try { if (editingId.value === null) await service.createSupplier(form.value); else await service.updateSupplier(editingId.value, form.value); formOpen.value = false; resetForm(); await load(1); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '保存供应商失败'; } }
async function toggleStatus(supplier: Supplier) { try { await service.changeSupplierStatus(supplier.id, supplier.status === 'enabled' ? 'disabled' : 'enabled'); await load(); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '更新状态失败'; } }
onMounted(() => void load(1));
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5">
    <MasterdataPageHeader title="供应商管理" description="维护供应商档案与采购协作信息，为不同供应商维护独立采购报价。" button-label="新增供应商" button-test-id="add-supplier" @create="openCreate" />
    <MasterdataFilterBar :keyword="keyword" :status="status" placeholder="搜索供应商名称" @update:keyword="keyword = $event" @update:status="status = $event" @search="load(1)" />
    <MasterdataFormDrawer v-if="formOpen" :title="editingId === null ? '新增供应商' : '编辑供应商'" description="供应商档案字段较少，使用右侧抽屉快速录入。" @close="formOpen = false" @submit="save">
      <div class="grid gap-4">
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>供应商名称</span><input data-testid="supplier-name" v-model="form.supplierName" required class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>联系人</span><input v-model="form.contactPerson" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>手机号</span><input v-model="form.mobile" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>联系电话</span><input v-model="form.telephone" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>地址</span><input v-model="form.address" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>备注</span><textarea v-model="form.remark" rows="4" class="w-full resize-y rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"></textarea></label>
      </div>
      <template #actions><button type="button" class="h-11 rounded-xl border border-slate-200 px-5 text-sm font-black text-slate-500 transition hover:border-slate-300 hover:text-[#25314d]" @click="formOpen = false">取消</button><button data-testid="save-supplier" type="button" class="h-11 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-[0_10px_22px_rgba(83,109,255,0.22)] transition hover:bg-[#4560eb]" @click="save">保存</button></template>
    </MasterdataFormDrawer>
    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>
    <MasterdataTable :records="result.records" :columns="columns" :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @page="load"><template #actions="{ record }"><div class="flex min-w-max justify-end gap-3 whitespace-nowrap text-sm"><button type="button" class="shrink-0 whitespace-nowrap text-slate-700 hover:underline" @click="edit(asSupplier(record))">编辑</button><button :data-testid="`supplier-status-${record.id}`" type="button" class="shrink-0 whitespace-nowrap text-slate-500 hover:underline" @click="toggleStatus(asSupplier(record))">{{ record.status === 'enabled' ? '停用' : '启用' }}</button></div></template></MasterdataTable>
  </section>
</template>
