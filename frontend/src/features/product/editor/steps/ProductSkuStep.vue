<script setup lang="ts">
import { Pencil, Plus, Trash2 } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { SkuForm } from '../../types';
import SkuEditorDialog from '../SkuEditorDialog.vue';
import { insertSku, removeSku as removeSkuFromState, replaceSku, type ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{ errors: Record<string, string> }>();

const dialogOpen = ref(false);
const editingIndex = ref<number | null>(null);
const editingSku = computed(() => editingIndex.value === null ? undefined : state.value.skus[editingIndex.value]);

function openCreate() {
  editingIndex.value = null;
  dialogOpen.value = true;
}

function openEdit(index: number) {
  editingIndex.value = index;
  dialogOpen.value = true;
}

function saveSku(sku: SkuForm) {
  if (editingIndex.value === null) {
    insertSku(state.value, sku);
  } else {
    replaceSku(state.value, editingIndex.value, sku);
  }
  dialogOpen.value = false;
}

function removeSku(index: number) {
  removeSkuFromState(state.value, index);
}

function skuError(index: number) {
  const prefix = `skus.${index}.`;
  return Object.entries(props.errors).find(([field]) => field.startsWith(prefix))?.[1] ?? '';
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-4">
      <p class="text-sm font-medium leading-6 text-slate-500">每个 SKU 在宽弹窗中独立维护，保存后才写入商品草稿。</p>
      <button data-testid="add-sku" type="button" class="inline-flex h-10 items-center gap-2 rounded-lg bg-[#536dff] px-4 text-sm font-bold text-white hover:bg-[#435be0]" @click="openCreate">
        <Plus class="h-4 w-4" aria-hidden="true" />
        添加 SKU
      </button>
    </div>

    <p v-if="errors.skus" class="mt-4 rounded-lg bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">{{ errors.skus }}</p>
    <div v-if="errors.productType" role="alert" class="mt-4 rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-700">
      <p class="font-bold">{{ errors.productType }}</p>
      <p class="mt-1 font-semibold">请删除多余 SKU，并编辑保留的 SKU 清空 SKU 规格值；若仍有 SKU 规格定义，请返回基础信息删除。</p>
    </div>

    <div v-if="state.skus.length" class="mt-5 overflow-x-auto border-y border-slate-200">
      <table class="w-full min-w-[760px] border-collapse text-left text-sm">
        <thead class="bg-slate-50 text-xs font-black text-slate-500">
          <tr>
            <th class="px-4 py-3">SKU</th>
            <th class="px-4 py-3">SKU 货号 / 条码</th>
            <th class="px-4 py-3">规格值</th>
            <th class="px-4 py-3">销售单位</th>
            <th class="px-4 py-3 text-right">默认售价</th>
            <th class="w-28 px-4 py-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" :data-testid="`sku-row-${index}`" class="bg-white">
            <td class="px-4 py-4">
              <p class="font-black text-[#25314d]">{{ sku.skuName || '--' }}</p>
              <p class="mt-1 text-xs font-semibold text-slate-400">
                {{ sku.status === 'enabled' ? '启用' : '已停用' }}<span v-if="sku.defaultSku"> · 默认 SKU</span>
              </p>
              <p v-if="skuError(index)" class="mt-1 text-xs font-bold text-rose-600">{{ skuError(index) }}</p>
            </td>
            <td class="break-all px-4 py-4 font-semibold text-slate-600">
              <p>{{ sku.skuCode || '保存后生成' }}</p>
              <p class="mt-1 text-xs text-slate-400">{{ sku.barcode || '无条码' }}</p>
            </td>
            <td class="px-4 py-4 font-semibold text-slate-600">{{ sku.specificationValues.join(' / ') || '--' }}</td>
            <td class="px-4 py-4 font-semibold text-slate-600">{{ sku.salesUnit || '--' }}</td>
            <td class="px-4 py-4 text-right font-bold tabular-nums text-slate-700">{{ sku.defaultSalePrice ?? '--' }}</td>
            <td class="px-4 py-4">
              <div class="flex justify-end gap-1">
                <button :data-testid="`edit-sku-${index}`" type="button" class="grid h-9 w-9 place-items-center rounded-lg text-slate-500 hover:bg-blue-50 hover:text-[#536dff]" :aria-label="`编辑 ${sku.skuName}`" @click="openEdit(index)">
                  <Pencil class="h-4 w-4" aria-hidden="true" />
                </button>
                <button :data-testid="`remove-sku-${index}`" type="button" class="grid h-9 w-9 place-items-center rounded-lg text-slate-500 hover:bg-rose-50 hover:text-rose-600" :aria-label="`删除 ${sku.skuName}`" @click="removeSku(index)">
                  <Trash2 class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="mt-5 border-y border-dashed border-slate-300 py-12 text-center text-sm font-semibold text-slate-400">暂无 SKU，请先添加商品规格。</div>

    <SkuEditorDialog
      :open="dialogOpen"
      :sku="editingSku"
      :title="editingIndex === null ? '添加 SKU' : '编辑 SKU'"
      @cancel="dialogOpen = false"
      @save="saveSku"
    />
  </div>
</template>
