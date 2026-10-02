<script setup lang="ts">
import { X } from 'lucide-vue-next';
import { computed } from 'vue';
import type { Category } from '../../../masterdata/types';
import type { ProductService } from '../../types';
import ProductImageUpload from '../ProductImageUpload.vue';
import {
  setEditorImageFileId,
  setEditorImagePreview,
  type ProductEditorState
} from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{
  draft?: boolean;
  categories: Category[];
  errors: Record<string, string>;
  service: ProductService;
}>();
const emit = defineEmits<{ 'update:uploading': [value: boolean] }>();

const defaultSalesUnit = computed(() => {
  const defaultSku = state.value.skus.find((sku) => sku.defaultSku) ?? state.value.skus[0];
  return defaultSku?.salesUnit || '下一步维护';
});
</script>

<template>
  <div class="grid h-[490px] grid-cols-[260px_minmax(0,1fr)] gap-6">
    <section class="min-w-0">
      <p class="mb-2 text-xs font-medium leading-[18px] text-[#25314d]">商品主图 <em class="not-italic text-[#ef476f]">*</em></p>
      <ProductImageUpload
        :file-id="state.mainImageFileId"
        :preview="state.imagePreviews.main"
        :service="service"
        label="商品主图"
        test-id="product-main-image"
        variant="wizard-main"
        @update:file-id="setEditorImageFileId(state, 'main', null, $event)"
        @update:preview="setEditorImagePreview(state, 'main', null, $event)"
        @update:uploading="emit('update:uploading', $event)"
      />
      <div class="mt-2 h-[92px] rounded-lg bg-[#f6f8fc] px-3 py-3 text-xs font-normal leading-[18px] text-[#8292ae]">
        <strong class="block font-medium text-[#64748b]">图片建议</strong>
        <p class="mt-1">建议使用白底正面图，尺寸不低于 800 × 800 px。</p>
      </div>
    </section>

    <section class="grid content-start grid-cols-3 gap-x-4 gap-y-3.5">
      <label class="wizard-field">
        <span>商品名称 <em>*</em></span>
        <input data-testid="product-name" v-model="state.productName" maxlength="200" :class="errors.productName ? 'border-[#ef476f]' : 'border-[#dbe4f1]'" placeholder="请输入商品名称" />
        <small v-if="errors.productName">{{ errors.productName }}</small>
      </label>
      <label class="wizard-field">
        <span>货号 <em>*</em></span>
        <input data-testid="product-item-no" v-model="state.itemNo" maxlength="50" :class="errors.itemNo ? 'border-[#ef476f]' : 'border-[#dbe4f1]'" placeholder="例如：BBF-PUMP-021" />
        <small v-if="errors.itemNo">{{ errors.itemNo }}</small>
      </label>
      <label class="wizard-field">
        <span>商品类型 <em>*</em></span>
        <select data-testid="product-type" v-model="state.productType" :class="errors.productType ? 'border-[#ef476f]' : 'border-[#dbe4f1]'">
          <option value="simple">单规格</option>
          <option value="variant">多规格</option>
        </select>
        <small v-if="errors.productType">{{ errors.productType }}</small>
      </label>

      <label class="wizard-field">
        <span>品牌</span>
        <input data-testid="product-brand" v-model="state.brand" maxlength="100" class="border-[#dbe4f1]" placeholder="请选择或输入品牌" />
      </label>
      <label class="wizard-field">
        <span>分类 <em>*</em></span>
        <select data-testid="product-category" v-model="state.categoryId" :class="errors.categoryId ? 'border-[#ef476f]' : 'border-[#dbe4f1]'">
          <option :value="null" disabled>请选择分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}{{ category.status === 'disabled' ? '（已停用）' : '' }}</option>
        </select>
        <small v-if="errors.categoryId">{{ errors.categoryId }}</small>
      </label>
      <label class="wizard-field">
        <span>{{ draft ? '提交后状态' : '商品状态' }}</span>
        <select data-testid="product-status" v-model="state.status" class="border-[#dbe4f1]">
          <option value="enabled">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>

      <label class="wizard-field">
        <span>SKU 数量</span>
        <input :value="`${state.skus.length} 个`" class="border-[#dbe4f1] bg-[#f8fafc] text-[#64748b]" readonly />
      </label>
      <label class="wizard-field">
        <span>计量单位</span>
        <input :value="defaultSalesUnit" class="border-[#dbe4f1] bg-[#f8fafc] text-[#64748b]" readonly />
      </label>
      <label class="wizard-field">
        <span>内部备注</span>
        <input data-testid="product-remark" v-model="state.remark" maxlength="500" class="border-[#dbe4f1]" placeholder="记录商品内部备注" />
      </label>

      <div class="col-span-3 mt-0.5 flex h-16 items-center gap-3 overflow-hidden rounded-lg bg-[#f1f4ff] px-4 text-xs font-normal leading-5 text-[#64748b]">
        <span class="h-2 w-2 shrink-0 rounded-full bg-[#536dff]"></span>
        <span v-if="state.specifications.length" class="shrink-0 font-medium text-[#536dff]">规格定义</span>
        <div v-if="state.specifications.length" class="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
          <span v-for="(specification, index) in state.specifications" :key="index" class="inline-flex h-8 shrink-0 items-center gap-2 rounded-lg bg-white px-3 text-[#25314d]">
            {{ specification.name || `规格 ${index + 1}` }}：{{ specification.values.join(' / ') || '未填写' }}
            <button type="button" class="grid h-5 w-5 place-items-center text-[#8292ae] hover:text-[#ef476f]" :aria-label="`删除规格 ${index + 1}`" @click="state.specifications.splice(index, 1)">
              <X class="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </span>
        </div>
        <span v-else>货号创建后不可重复；商品规格、SKU 图片与条码将在下一步维护。</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.wizard-field {
  display: flex;
  min-width: 0;
  height: 80px;
  flex-direction: column;
  gap: 6px;
  color: #25314d;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
}

.wizard-field > span em {
  color: #ef476f;
  font-style: normal;
}

.wizard-field > input,
.wizard-field > select {
  width: 100%;
  height: 44px;
  border-width: 1px;
  border-radius: 8px;
  background-color: #fff;
  padding: 0 12px;
  color: #25314d;
  font-size: 14px;
  font-weight: 400;
  line-height: 22px;
  outline: none;
}

.wizard-field > input:focus,
.wizard-field > select:focus {
  border-color: #536dff;
  box-shadow: 0 0 0 2px rgb(83 109 255 / 12%);
}

.wizard-field > small {
  color: #ef476f;
  font-size: 12px;
  line-height: 18px;
}
</style>
