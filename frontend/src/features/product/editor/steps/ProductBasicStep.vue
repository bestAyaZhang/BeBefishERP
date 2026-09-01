<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next';
import type { Category } from '../../../masterdata/types';
import type { ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{
  categories: Category[];
  errors: Record<string, string>;
}>();

function addSpecification() {
  state.value.specifications.push({ name: '', values: [] });
}

function updateSpecificationValues(index: number, event: Event) {
  const specification = state.value.specifications[index];
  if (!specification) return;
  specification.values = (event.target as HTMLInputElement).value
    .split('/')
    .map((value) => value.trim())
    .filter(Boolean);
}
</script>

<template>
  <div class="space-y-7">
    <div class="grid gap-5 md:grid-cols-2">
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>货号 <em class="not-italic text-rose-500">*</em></span>
        <input data-testid="product-item-no" v-model="state.itemNo" maxlength="50" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.itemNo ? 'border-rose-400' : 'border-slate-300'" placeholder="例如：BBF-021" />
        <span v-if="errors.itemNo" class="block text-xs text-rose-600">{{ errors.itemNo }}</span>
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>商品名称 <em class="not-italic text-rose-500">*</em></span>
        <input data-testid="product-name" v-model="state.productName" maxlength="200" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.productName ? 'border-rose-400' : 'border-slate-300'" placeholder="请输入商品名称" />
        <span v-if="errors.productName" class="block text-xs text-rose-600">{{ errors.productName }}</span>
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>商品分类 <em class="not-italic text-rose-500">*</em></span>
        <select data-testid="product-category" v-model="state.categoryId" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" :class="errors.categoryId ? 'border-rose-400' : 'border-slate-300'">
          <option :value="null" disabled>请选择商品分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}{{ category.status === 'disabled' ? '（已停用）' : '' }}</option>
        </select>
        <span v-if="errors.categoryId" class="block text-xs text-rose-600">{{ errors.categoryId }}</span>
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>品牌</span>
        <input data-testid="product-brand" v-model="state.brand" maxlength="100" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="请输入品牌" />
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>商品类型</span>
        <select data-testid="product-type" v-model="state.productType" class="h-11 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-[#25314d] outline-none focus:border-[#536dff]" :class="errors.productType ? 'border-rose-400' : 'border-slate-300'">
          <option value="simple">单规格</option>
          <option value="variant">多规格</option>
        </select>
        <span v-if="errors.productType" class="block text-xs text-rose-600">{{ errors.productType }}</span>
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600">
        <span>商品状态</span>
        <select data-testid="product-status" v-model="state.status" class="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-[#25314d] outline-none focus:border-[#536dff]">
          <option value="enabled">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>
      <label class="space-y-2 text-sm font-bold text-slate-600 md:col-span-2">
        <span>备注</span>
        <textarea data-testid="product-remark" v-model="state.remark" maxlength="500" rows="3" class="w-full resize-y rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm font-semibold leading-6 text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/15" placeholder="记录商品内部备注" />
      </label>
    </div>

    <section class="border-t border-slate-200 pt-6">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-black text-[#25314d]">规格定义</h3>
          <p class="mt-1 text-xs font-medium text-slate-500">规格值使用 / 分隔，并与 SKU 中的规格值保持对应。</p>
        </div>
        <button type="button" class="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-300 px-3 text-xs font-bold text-slate-600 hover:border-[#536dff] hover:text-[#536dff]" @click="addSpecification">
          <Plus class="h-4 w-4" aria-hidden="true" />
          添加规格
        </button>
      </div>
      <div v-if="state.specifications.length" class="mt-4 space-y-3">
        <div v-for="(specification, index) in state.specifications" :key="index" class="grid gap-3 sm:grid-cols-[minmax(0,220px)_minmax(0,1fr)_40px]">
          <input v-model="specification.name" :data-testid="`specification-name-${index}`" maxlength="100" class="h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold outline-none focus:border-[#536dff]" placeholder="规格名称" />
          <input :value="specification.values.join(' / ')" :data-testid="`specification-values-${index}`" maxlength="500" class="h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold outline-none focus:border-[#536dff]" placeholder="透明 / 烟灰" @input="updateSpecificationValues(index, $event)" />
          <button type="button" class="grid h-10 w-10 place-items-center rounded-lg text-rose-600 hover:bg-rose-50" :aria-label="`删除规格 ${index + 1}`" @click="state.specifications.splice(index, 1)">
            <Trash2 class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>
      <p v-else class="mt-4 text-sm font-medium text-slate-400">当前商品没有规格定义。</p>
    </section>
  </div>
</template>
