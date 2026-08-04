<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue';
import MasterdataFilterBar from '../components/MasterdataFilterBar.vue';
import MasterdataFormDrawer from '../components/MasterdataFormDrawer.vue';
import MasterdataPageHeader from '../components/MasterdataPageHeader.vue';
import MasterdataTable from '../components/MasterdataTable.vue';
import { masterdataService } from '../masterdataService';
import type { Category, MasterdataRow, MasterdataService, PageResult, RecordStatus, SaveCategoryPayload } from '../types';

const service = inject<MasterdataService>('masterdataService', masterdataService);
const result = ref<PageResult<Category>>({ records: [], page: 1, pageSize: 20, total: 0 });
const keyword = ref('');
const status = ref<RecordStatus | ''>('');
const loading = ref(false);
const errorMessage = ref('');
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const form = ref<SaveCategoryPayload>({ categoryName: '', sortOrder: 0, remark: '' });
const columns = [
  { key: 'categoryName', label: '分类名称' },
  { key: 'sortOrder', label: '排序号' },
  { key: 'status', label: '状态' },
  { key: 'remark', label: '备注' }
];
const formTitle = computed(() => (editingId.value === null ? '新增分类' : '编辑分类'));

function asCategory(row: MasterdataRow) {
  return row as Category;
}

function resetForm() {
  editingId.value = null;
  form.value = { categoryName: '', sortOrder: 0, remark: '' };
}

async function load(page = result.value.page) {
  loading.value = true;
  errorMessage.value = '';
  try {
    result.value = await service.listCategories({ page, size: result.value.pageSize, keyword: keyword.value, status: status.value || undefined });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '分类加载失败';
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  resetForm();
  formOpen.value = true;
}

function edit(category: Category) {
  editingId.value = category.id;
  form.value = { categoryName: category.categoryName, sortOrder: category.sortOrder, remark: category.remark };
  formOpen.value = true;
}

async function save() {
  try {
    if (editingId.value === null) await service.createCategory(form.value);
    else await service.updateCategory(editingId.value, form.value);
    formOpen.value = false;
    resetForm();
    await load(1);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存分类失败';
  }
}

async function toggleStatus(category: Category) {
  try {
    await service.changeCategoryStatus(category.id, category.status === 'enabled' ? 'disabled' : 'enabled');
    await load();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新状态失败';
  }
}

onMounted(() => void load(1));
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5">
    <MasterdataPageHeader title="分类管理" description="维护商品一级分类，为产品资料、库存和销售开单提供统一分类。" button-label="新增分类" button-test-id="add-category" @create="openCreate" />

    <MasterdataFilterBar :keyword="keyword" :status="status" placeholder="搜索分类名称" @update:keyword="keyword = $event" @update:status="status = $event" @search="load(1)" />

    <MasterdataFormDrawer v-if="formOpen" :title="formTitle" description="分类信息较少，使用右侧抽屉快速录入。" @close="formOpen = false" @submit="save">
      <div class="grid gap-4">
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>分类名称</span><input data-testid="category-name" v-model="form.categoryName" required class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>排序号</span><input v-model.number="form.sortOrder" min="0" type="number" class="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
        <label class="space-y-2 text-sm font-bold text-slate-500"><span>备注</span><textarea v-model="form.remark" rows="4" class="w-full resize-y rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"></textarea></label>
      </div>
      <template #actions><button type="button" class="h-11 rounded-xl border border-slate-200 px-5 text-sm font-black text-slate-500 transition hover:border-slate-300 hover:text-[#25314d]" @click="formOpen = false">取消</button><button data-testid="save-category" type="button" class="h-11 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-[0_10px_22px_rgba(83,109,255,0.22)] transition hover:bg-[#4560eb]" @click="save">保存</button></template>
    </MasterdataFormDrawer>

    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>
    <MasterdataTable :records="result.records" :columns="columns" :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @page="load">
      <template #actions="{ record }"><div class="flex min-w-max justify-end gap-3 whitespace-nowrap text-sm"><button type="button" class="shrink-0 whitespace-nowrap text-slate-700 hover:underline" @click="edit(asCategory(record))">编辑</button><button :data-testid="`category-status-${record.id}`" type="button" class="shrink-0 whitespace-nowrap text-slate-500 hover:underline" @click="toggleStatus(asCategory(record))">{{ record.status === 'enabled' ? '停用' : '启用' }}</button></div></template>
    </MasterdataTable>
  </section>
</template>
