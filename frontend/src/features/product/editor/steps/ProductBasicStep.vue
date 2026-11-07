<script setup lang="ts">
import { ArrowRight, Info, X } from 'lucide-vue-next';
import { computed } from 'vue';
import type { Category } from '../../../masterdata/types';
import type { ProductEditorState } from '../productEditorState';

const state = defineModel<ProductEditorState>({ required: true });
defineProps<{
  draft?: boolean;
  categories: Category[];
  errors: Record<string, string>;
}>();
const emit = defineEmits<{ 'edit-skus': [] }>();

const defaultSalesUnit = computed(() => {
  const defaultSku = state.value.skus.find((sku) => sku.defaultSku) ?? state.value.skus[0];
  return defaultSku?.salesUnit ?? '';
});
</script>

<template>
  <div class="product-basic-fields">
    <label class="product-editor-field product-basic-full">
      <span>商品名称 <em>*</em></span>
      <input
        data-testid="product-name"
        v-model="state.productName"
        maxlength="200"
        aria-required="true"
        :aria-invalid="Boolean(errors.productName)"
        :aria-describedby="errors.productName ? 'product-name-error' : undefined"
        placeholder="请输入商品名称"
      />
      <small v-if="errors.productName" id="product-name-error" class="product-editor-field-error">{{ errors.productName }}</small>
    </label>
    <label class="product-editor-field">
      <span>商品货号 <em>*</em></span>
      <input
        data-testid="product-item-no"
        v-model="state.itemNo"
        maxlength="50"
        aria-required="true"
        :aria-invalid="Boolean(errors.itemNo)"
        aria-describedby="product-item-no-hint"
        placeholder="例如 TZ401"
      />
      <small id="product-item-no-hint" :class="{ 'product-editor-field-error': errors.itemNo }">{{ errors.itemNo || '用于内部识别，商品货号不可重复。' }}</small>
    </label>
    <label class="product-editor-field">
      <span>商品分类 <em>*</em></span>
      <select
        data-testid="product-category"
        v-model="state.categoryId"
        aria-required="true"
        :aria-invalid="Boolean(errors.categoryId)"
        :aria-describedby="errors.categoryId ? 'product-category-error' : undefined"
      >
        <option :value="null" disabled>请选择商品分类</option>
        <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}{{ category.status === 'disabled' ? '（已停用）' : '' }}</option>
      </select>
      <small v-if="errors.categoryId" id="product-category-error" class="product-editor-field-error">{{ errors.categoryId }}</small>
    </label>

    <label class="product-editor-field">
      <span>品牌 <small>选填</small></span>
      <input data-testid="product-brand" v-model="state.brand" maxlength="100" placeholder="请输入品牌名称" />
    </label>
    <label class="product-editor-field">
      <span>商品类型 <em>*</em></span>
      <select data-testid="product-type" v-model="state.productType">
        <option value="simple">单规格</option>
        <option value="variant">多规格</option>
      </select>
    </label>

    <label class="product-editor-field">
      <span>{{ draft ? '提交后状态' : '商品状态' }}</span>
      <select data-testid="product-status" v-model="state.status">
        <option value="enabled">启用</option>
        <option value="disabled">停用</option>
      </select>
    </label>
    <div class="product-editor-field">
      <span>SKU 维护</span>
      <div class="product-basic-sku">
        <span class="product-editor-badge">{{ state.skus.length }} 个 SKU</span>
        <button type="button" class="product-editor-text-button" @click="emit('edit-skus')">
          前往维护 <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
      <small>{{ defaultSalesUnit ? `默认销售单位：${defaultSalesUnit}` : '可在 SKU 信息中维护规格、单位与价格。' }}</small>
    </div>

    <label class="product-editor-field product-basic-full">
      <span>内部备注 <small>选填</small></span>
      <textarea data-testid="product-remark" v-model="state.remark" maxlength="500" placeholder="补充来源说明、包装约定等内部信息"></textarea>
    </label>

    <div v-if="state.specifications.length" class="product-basic-specs product-basic-full">
      <h3>规格定义</h3>
      <ul>
        <li v-for="(specification, index) in state.specifications" :key="index">
          <span>{{ specification.name || `规格 ${index + 1}` }}：{{ specification.values.join(' / ') || '未填写' }}</span>
          <button type="button" :aria-label="`删除规格 ${index + 1}`" @click="state.specifications.splice(index, 1)">
            <X class="h-4 w-4" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </div>

    <p class="product-editor-note product-basic-full">
      <Info class="h-4 w-4 shrink-0" aria-hidden="true" />
      库存通过库存业务独立维护。保存商品草稿后，可在商品列表中继续编辑。
    </p>
  </div>
</template>
