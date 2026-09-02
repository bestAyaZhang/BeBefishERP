<script setup lang="ts">
import { ImageIcon, Plus } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { ProductService, SkuForm } from '../../types';
import SkuEditorDialog from '../SkuEditorDialog.vue';
import { insertSku, removeSku as removeSkuFromState, replaceSku, setEditorImagePreview, type ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
const props = defineProps<{ errors: Record<string, string>; service: ProductService }>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();

const dialogOpen = ref(false);
const editingIndex = ref<number | null>(null);
const editingSku = computed(() => editingIndex.value === null ? undefined : state.value.skus[editingIndex.value]);
const editingPreview = computed(() => editingIndex.value === null ? '' : state.value.imagePreviews.sku[editingIndex.value] ?? '');

function openCreate() {
  editingIndex.value = null;
  dialogOpen.value = true;
}

function openEdit(index: number) {
  editingIndex.value = index;
  dialogOpen.value = true;
}

function saveSku(sku: SkuForm, preview: string) {
  const targetIndex = editingIndex.value ?? state.value.skus.length;
  if (editingIndex.value === null) insertSku(state.value, sku);
  else replaceSku(state.value, editingIndex.value, sku);
  setEditorImagePreview(state.value, 'sku', targetIndex, preview);
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
  <div class="h-[490px]">
    <button data-testid="add-sku" type="button" class="absolute right-6 top-6 inline-flex h-10 w-28 items-center justify-center gap-2 rounded-lg bg-[#536dff] text-sm font-medium text-white hover:bg-[#465eea]" @click="openCreate">
      <Plus class="h-4 w-4" aria-hidden="true" />
      新增 SKU
    </button>

    <p v-if="errors.skus" class="mb-3 rounded-lg bg-[#fff1f2] px-4 py-2 text-xs font-medium text-[#ef476f]">{{ errors.skus }}</p>
    <div v-if="errors.productType" role="alert" class="mb-3 rounded-lg bg-[#fff1f2] px-4 py-2 text-xs text-[#ef476f]">
      <p class="font-medium">{{ errors.productType }}</p>
      <p class="mt-1">请删除多余 SKU，并编辑保留的 SKU 清空 SKU 规格值；若仍有 SKU 规格定义，请返回基础信息删除。</p>
    </div>

    <div v-if="state.skus.length" class="overflow-hidden rounded-lg border border-[#dbe4f1]">
      <table class="w-full table-fixed border-collapse text-left text-sm">
        <colgroup>
          <col class="w-[72px]" />
          <col class="w-[174px]" />
          <col class="w-[248px]" />
          <col class="w-[184px]" />
          <col class="w-[124px]" />
          <col class="w-[104px]" />
          <col class="w-[176px]" />
        </colgroup>
        <thead class="h-11 bg-[#f6f8fc] text-xs font-medium text-[#64748b]">
          <tr>
            <th class="px-3">SKU 图片</th>
            <th class="px-3">SKU 货号</th>
            <th class="px-3">规格组合</th>
            <th class="px-3">单杯条码</th>
            <th class="px-3">销售价</th>
            <th class="px-3">状态</th>
            <th class="px-3">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-[#edf1f6]">
          <tr v-for="(sku, index) in state.skus" :key="sku.id ?? `sku-${index}`" :data-testid="`sku-row-${index}`" class="h-[104px] odd:bg-white even:bg-[#fbfcfe]">
            <td class="px-4">
              <div class="grid h-11 w-11 place-items-center overflow-hidden rounded-lg bg-[#eef2ff] text-[#536dff]">
                <img v-if="state.imagePreviews.sku[index]" :src="state.imagePreviews.sku[index]" :alt="`${sku.skuName} SKU 图片`" class="h-full w-full object-cover" />
                <ImageIcon v-else class="h-4 w-4" aria-hidden="true" />
              </div>
            </td>
            <td class="px-3">
              <p class="truncate font-medium text-[#536dff]">{{ sku.skuCode || '保存后生成' }}</p>
              <span v-if="sku.defaultSku" class="mt-1 inline-flex h-5 items-center rounded bg-[#eef2ff] px-1.5 text-[10px] font-medium text-[#536dff]">默认 SKU</span>
              <p v-if="skuError(index)" class="mt-1 truncate text-xs text-[#ef476f]">{{ skuError(index) }}</p>
            </td>
            <td class="px-3 font-normal text-[#25314d]">{{ sku.specificationValues.join(' / ') || sku.skuName || '--' }}</td>
            <td class="px-3 font-normal text-[#64748b]">{{ sku.barcode || '--' }}</td>
            <td class="px-3 font-medium tabular-nums text-[#25314d]">{{ sku.defaultSalePrice === null ? '--' : `¥${sku.defaultSalePrice.toFixed(2)}` }}</td>
            <td class="px-3">
              <span class="inline-flex h-7 items-center rounded-lg px-3 text-xs font-medium" :class="sku.status === 'enabled' ? 'bg-[#e8f8f2] text-[#16a36a]' : 'bg-[#f1f5f9] text-[#64748b]'">{{ sku.status === 'enabled' ? '启用' : '已停用' }}</span>
            </td>
            <td class="px-3">
              <div class="flex items-center gap-4 text-xs font-medium">
                <button :data-testid="`edit-sku-${index}`" type="button" class="text-[#536dff] hover:text-[#465eea]" :aria-label="`编辑 ${sku.skuName}`" @click="openEdit(index)">编辑</button>
                <button :data-testid="`remove-sku-${index}`" type="button" class="text-[#ef476f] hover:text-[#d93662]" :aria-label="`删除 ${sku.skuName}`" @click="removeSku(index)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="grid h-[252px] place-items-center rounded-lg border border-dashed border-[#b8c9e3] text-sm font-normal text-[#8292ae]">暂无 SKU，请先新增商品规格。</div>

    <div class="mt-3 flex h-[66px] items-center gap-3 rounded-lg bg-[#f1f4ff] px-4 text-xs font-normal text-[#64748b]">
      <span class="h-2 w-2 rounded-full bg-[#536dff]"></span>
      已添加 {{ state.skus.length }} 个 SKU。规格组合、SKU 货号及单杯条码不可重复。
    </div>

    <SkuEditorDialog
      :open="dialogOpen"
      :sku="editingSku"
      :preview="editingPreview"
      :service="service"
      :title="editingIndex === null ? '新增 SKU' : '维护 SKU'"
      @cancel="dialogOpen = false"
      @save="saveSku"
      @update:uploading="emit('update:uploading', $event)"
    />
  </div>
</template>
