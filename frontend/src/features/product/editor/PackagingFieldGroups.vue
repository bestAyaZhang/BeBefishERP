<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import PackagingNumberField from './PackagingNumberField.vue';
import ProductImageUpload from './ProductImageUpload.vue';
import type { ProductService } from '../types';
import { packagingImageFields, type PackagingForm, type PackagingImageKind } from './productEditorState';

const props = defineProps<{
  packaging: PackagingForm;
  salesUnit: string;
  context: 'unified' | 'dialog';
  service: ProductService;
  previews: Record<PackagingImageKind, string>;
  errors?: Record<string, string | undefined>;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  update: [packaging: PackagingForm];
  'update:fileId': [kind: PackagingImageKind, fileId: number | null];
  'update:preview': [kind: PackagingImageKind, url: string];
  'update:uploading': [uploading: boolean];
}>();

type NumberField = Exclude<keyof PackagingForm, 'packagingMethod' | 'productPhotoFileId' | 'cartonImageFileId' | 'packageImageFileId'>;
interface Measure { key: NumberField; label: string; id: string; unit: string }
interface Group { kind: PackagingImageKind; title: string; dimensionId?: string; dimensions: Measure[]; measures: Measure[] }
const photos = {
  product: { label: '产品实拍图', id: 'product-photo' },
  carton: { label: '外箱图片', id: 'carton-image' },
  package: { label: '内盒包装图', id: 'package-image' }
};
const uploads = new Set<PackagingImageKind>();

function uploadStatus(kind: PackagingImageKind, uploading: boolean) {
  if (uploading) uploads.add(kind);
  else uploads.delete(kind);
  emit('update:uploading', uploads.size > 0);
}

const shape = ref<'mouth' | 'lwh'>(
  props.packaging.productMouthDiameterCm != null || (props.packaging.productLengthCm == null && props.packaging.productWidthCm == null)
    ? 'mouth' : 'lwh'
);
watch(() => props.errors, (errors) => {
  if (errors?.productMouthDiameterCm) shape.value = 'mouth';
  else if (errors?.productLengthCm || errors?.productWidthCm) shape.value = 'lwh';
}, { immediate: true });
const groups = computed<Group[]>(() => [
  {
    kind: 'product', title: '产品尺寸', dimensions: [
      { key: 'productMouthDiameterCm', label: '口径', id: 'product-mouth', unit: 'cm' },
      { key: 'productLengthCm', label: '长', id: 'product-length', unit: 'cm' },
      { key: 'productWidthCm', label: '宽', id: 'product-width', unit: 'cm' },
      { key: 'productHeightCm', label: '高', id: 'product-height', unit: 'cm' }
    ], measures: [
      { key: 'capacityMl', label: '容量', id: 'capacity', unit: 'ml' },
      { key: 'gramWeightG', label: '单只克重', id: 'gram-weight', unit: 'g' }
    ]
  },
  {
    kind: 'carton', title: '外箱资料', dimensionId: 'package-dimensions', dimensions: [
      { key: 'packageLengthCm', label: '长', id: 'package-length', unit: 'cm' },
      { key: 'packageWidthCm', label: '宽', id: 'package-width', unit: 'cm' },
      { key: 'packageHeightCm', label: '高', id: 'package-height', unit: 'cm' }
    ], measures: [
      { key: 'cartonQuantity', label: '单箱数量', id: 'carton-quantity', unit: props.salesUnit },
      { key: 'packageVolumeCm3', label: '箱体积', id: 'package-volume', unit: 'cm³' },
      { key: 'netWeightKg', label: '整箱净重', id: 'net-weight', unit: 'kg' },
      { key: 'grossWeightKg', label: '整箱毛重', id: 'gross-weight', unit: 'kg' }
    ]
  },
  {
    kind: 'package', title: '内盒包装', dimensionId: 'inner-package-dimensions', dimensions: [
      { key: 'innerPackageLengthCm', label: '长', id: 'inner-package-length', unit: 'cm' },
      { key: 'innerPackageWidthCm', label: '宽', id: 'inner-package-width', unit: 'cm' },
      { key: 'innerPackageHeightCm', label: '高', id: 'inner-package-height', unit: 'cm' }
    ], measures: [{ key: 'innerPackageWeightKg', label: '内盒重量', id: 'inner-package-weight', unit: 'kg' }]
  }
]);

function testId(id: string) {
  if (props.context === 'dialog') return `packaging-dialog-${id}`;
  return `${id.startsWith('product-') || id === 'capacity' ? 'packaging' : 'unified'}-${id}`;
}

function showDimension(key: NumberField) {
  if (key === 'productMouthDiameterCm') return shape.value === 'mouth';
  if (key === 'productLengthCm' || key === 'productWidthCm') return shape.value === 'lwh';
  return true;
}

function update(field: keyof PackagingForm, value: number | string | null) {
  emit('update', { ...props.packaging, [field]: value });
}

function updateMethod(event: Event) {
  update('packagingMethod', (event.target as HTMLInputElement).value);
}
</script>

<template>
  <div class="pack-group-layout">
    <section v-for="group in groups" :key="group.kind" class="pack-group" :aria-label="group.title">
      <header class="pack-group-heading">
        <h3>{{ group.title }}</h3>
        <div v-if="group.kind === 'product'" class="pack-shape" role="group" aria-label="产品尺寸形式">
          <button type="button" :aria-pressed="shape === 'mouth'" @click="shape = 'mouth'">口径 / 高度</button>
          <button type="button" :aria-pressed="shape === 'lwh'" @click="shape = 'lwh'">长 × 宽 × 高</button>
        </div>
      </header>
      <div class="pack-group-body">
        <div class="pack-fields">
          <div
            :data-testid="group.dimensionId ? testId(group.dimensionId) : undefined"
            class="pack-dimensions"
            :class="{ 'pack-round': group.kind === 'product' && shape === 'mouth' }"
          >
            <template v-for="(field, index) in group.dimensions" :key="field.key">
              <span
                v-if="index > (group.kind === 'product' ? 1 : 0)"
                v-show="group.kind !== 'product' || shape === 'lwh'"
                data-dimension-separator
                :data-testid="group.kind === 'product' ? testId('product-dimension-separator') : undefined"
                class="pack-multiply"
                aria-hidden="true"
              >×</span>
              <PackagingNumberField
                v-show="showDimension(field.key)"
                :id="testId(field.id)" :label="field.label" :value="packaging[field.key]" :unit="field.unit"
                :error="errors?.[field.key]" dimension
                @change="update(field.key, $event)"
              />
            </template>
          </div>
          <div class="pack-measures">
            <label v-if="group.kind === 'package'" class="pack-method">
              <span>包装方式</span>
              <input :data-testid="testId('method')" :value="packaging.packagingMethod" maxlength="100" placeholder="例如：6 只彩盒" @input="updateMethod" />
            </label>
            <PackagingNumberField
              v-for="field in group.measures" :key="field.key"
              :id="testId(field.id)" :label="field.label" :value="packaging[field.key]" :unit="field.unit"
              :error="errors?.[field.key]" :quantity="field.key === 'cartonQuantity'" :volume="field.key === 'packageVolumeCm3'"
              @change="update(field.key, $event)"
            />
          </div>
        </div>
        <div class="pack-photo">
          <ProductImageUpload
            :service="service"
            :disabled="disabled"
            :label="photos[group.kind].label"
            :test-id="context === 'dialog' ? testId(photos[group.kind].id) : photos[group.kind].id + '-unified'"
            :file-id="packaging[packagingImageFields[group.kind]] ?? null"
            :preview="previews[group.kind]"
            @update:file-id="emit('update:fileId', group.kind, $event)"
            @update:preview="emit('update:preview', group.kind, $event)"
            @update:uploading="uploadStatus(group.kind, $event)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pack-group-layout { display: grid; min-width: 0; gap: 14px; container-type: inline-size; }
.pack-group { min-width: 0; padding: 16px; border: 1px solid #dfe7f0; border-radius: 8px; background: #fff; }
.pack-group-heading { display: flex; min-height: 34px; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.pack-group-heading h3 { margin: 0; color: #24364b; font-size: 15px; font-weight: 600; }
.pack-shape { display: inline-flex; flex-wrap: wrap; gap: 4px; color: #52647a; font-size: 12px; }
.pack-shape button { min-height: 34px; padding: 0 9px; border-radius: 5px; }
.pack-shape button[aria-pressed="true"] { color: #0968b6; background: #edf5fc; }
.pack-group-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(150px, 28%); align-items: start; gap: 18px; }
.pack-fields, .pack-photo { min-width: 0; }
.pack-dimensions { display: grid; grid-template-columns: minmax(0, 1fr) 12px minmax(0, 1fr) 12px minmax(0, 1fr); gap: 4px; align-items: start; }
.pack-dimensions.pack-round, .pack-measures { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.pack-multiply { align-self: start; padding-top: 32px; color: #65758a; text-align: center; }
.pack-measures { display: grid; margin-top: 12px; }
.pack-method { display: flex; min-width: 0; flex-direction: column; gap: 6px; font-size: 13px; color: #42556b; }
.pack-method input { width: 100%; height: 40px; padding: 0 10px; border: 1px solid #d7e0eb; border-radius: 6px; color: #20334b; }
.pack-method input:focus { outline: 2px solid #bfdaf0; outline-offset: 1px; }
@container (max-width: 510px) {
  .pack-group { padding: 13px; }
  .pack-group-body { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .pack-photo { border-top: 1px solid #e4e9ef; padding-top: 12px; }
}
</style>
