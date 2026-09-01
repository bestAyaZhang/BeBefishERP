import { toProductFormPayload } from '../productFormMapper';
import type {
  Product,
  ProductFormPayload,
  ProductSpecification,
  ProductSupplierQuoteInput,
  ProductType,
  SkuForm
} from '../types';

export const productEditorSteps = ['basic', 'sku', 'procurement', 'packaging', 'images', 'confirm'] as const;
export const MAX_CARTON_QUANTITY = 2_147_483_647;
export const CARTON_QUANTITY_ERROR = '装箱数必须为 1 到 2147483647 之间的整数';

export type ProductEditorStep = typeof productEditorSteps[number];
export type PackagingMode = 'unified' | 'perSku';

export type PackagingForm = Pick<SkuForm,
  | 'packageLengthCm'
  | 'packageWidthCm'
  | 'packageHeightCm'
  | 'packageVolumeCm3'
  | 'innerPackageLengthCm'
  | 'innerPackageWidthCm'
  | 'innerPackageHeightCm'
  | 'netWeightKg'
  | 'grossWeightKg'
  | 'gramWeightG'
  | 'innerPackageWeightKg'
  | 'packagingMethod'
  | 'cartonQuantity'
  | 'packageImageFileId'
  | 'cartonImageFileId'
>;

export interface ProductEditorImagePreviews {
  main: string;
  sku: string[];
  package: string[];
  carton: string[];
}

export interface ProductEditorImagePreviewFileIds {
  main: number | null;
  sku: Array<number | null>;
  package: Array<number | null>;
  carton: Array<number | null>;
}

export type ProductEditorImageKind = 'main' | 'sku' | 'package' | 'carton';

export interface ProductEditorValidationContext {
  validSupplierIds?: ReadonlySet<number>;
}

export interface ProductEditorState {
  productId: number | null;
  loadedProductType: ProductType | null;
  itemNo: string;
  productName: string;
  categoryId: number | null;
  brand: string;
  mainImageFileId: number | null;
  remark: string;
  specifications: ProductSpecification[];
  skus: SkuForm[];
  packagingMode: PackagingMode;
  unifiedPackaging: PackagingForm;
  imagePreviews: ProductEditorImagePreviews;
  imagePreviewFileIds: ProductEditorImagePreviewFileIds;
}

export function isValidCartonQuantity(value: number | null) {
  return value === null || (
    Number.isFinite(value)
    && Number.isInteger(value)
    && value >= 1
    && value <= MAX_CARTON_QUANTITY
  );
}

const packagingFields: ReadonlyArray<keyof PackagingForm> = [
  'packageLengthCm',
  'packageWidthCm',
  'packageHeightCm',
  'packageVolumeCm3',
  'innerPackageLengthCm',
  'innerPackageWidthCm',
  'innerPackageHeightCm',
  'netWeightKg',
  'grossWeightKg',
  'gramWeightG',
  'innerPackageWeightKg',
  'packagingMethod',
  'cartonQuantity',
  'packageImageFileId',
  'cartonImageFileId'
];

export function createBlankSku(): SkuForm {
  return {
    skuCode: '',
    barcode: '',
    skuName: '',
    specificationValues: [],
    salesUnit: '只',
    defaultSalePrice: null,
    standardCost: null,
    safetyStockQuantity: null,
    packageLengthCm: null,
    packageWidthCm: null,
    packageHeightCm: null,
    packageVolumeCm3: null,
    innerPackageLengthCm: null,
    innerPackageWidthCm: null,
    innerPackageHeightCm: null,
    netWeightKg: null,
    grossWeightKg: null,
    gramWeightG: null,
    innerPackageWeightKg: null,
    packagingMethod: '',
    cartonQuantity: null,
    skuImageFileId: null,
    packageImageFileId: null,
    cartonImageFileId: null,
    supplierQuotes: []
  };
}

export function packagingFromSku(sku?: SkuForm): PackagingForm {
  return {
    packageLengthCm: sku?.packageLengthCm ?? null,
    packageWidthCm: sku?.packageWidthCm ?? null,
    packageHeightCm: sku?.packageHeightCm ?? null,
    packageVolumeCm3: sku?.packageVolumeCm3 ?? null,
    innerPackageLengthCm: sku?.innerPackageLengthCm ?? null,
    innerPackageWidthCm: sku?.innerPackageWidthCm ?? null,
    innerPackageHeightCm: sku?.innerPackageHeightCm ?? null,
    netWeightKg: sku?.netWeightKg ?? null,
    grossWeightKg: sku?.grossWeightKg ?? null,
    gramWeightG: sku?.gramWeightG ?? null,
    innerPackageWeightKg: sku?.innerPackageWeightKg ?? null,
    packagingMethod: sku?.packagingMethod ?? '',
    cartonQuantity: sku?.cartonQuantity ?? null,
    packageImageFileId: sku?.packageImageFileId ?? null,
    cartonImageFileId: sku?.cartonImageFileId ?? null
  };
}

function cloneQuote(quote: ProductSupplierQuoteInput): ProductSupplierQuoteInput {
  return { ...quote };
}

export function cloneSku(sku: SkuForm): SkuForm {
  return {
    ...sku,
    specificationValues: [...sku.specificationValues],
    supplierQuotes: sku.supplierQuotes?.map(cloneQuote)
  };
}

export function detectPackagingMode(skus: SkuForm[]): PackagingMode {
  const first = skus[0];
  if (!first) return 'unified';
  return skus.slice(1).some((sku) => packagingFields.some((field) => !Object.is(sku[field], first[field])))
    ? 'perSku'
    : 'unified';
}

export function applyUnifiedPackaging(skus: SkuForm[], packaging: PackagingForm): SkuForm[] {
  return skus.map((sku) => ({
    ...cloneSku(sku),
    ...packaging
  }));
}

function ensureImageSlots(state: ProductEditorState) {
  for (const field of ['sku', 'package', 'carton'] as const) {
    while (state.imagePreviews[field].length < state.skus.length) state.imagePreviews[field].push('');
    while (state.imagePreviewFileIds[field].length < state.skus.length) state.imagePreviewFileIds[field].push(null);
    state.imagePreviews[field].length = state.skus.length;
    state.imagePreviewFileIds[field].length = state.skus.length;
  }
}

function matchingPreview(
  state: ProductEditorState,
  kind: 'package' | 'carton',
  index: number,
  fileId: number | null
) {
  return state.imagePreviewFileIds[kind][index] === fileId
    ? state.imagePreviews[kind][index] ?? ''
    : '';
}

export function insertSku(state: ProductEditorState, sku: SkuForm) {
  ensureImageSlots(state);
  const packagePreview = matchingPreview(state, 'package', 0, state.unifiedPackaging.packageImageFileId);
  const cartonPreview = matchingPreview(state, 'carton', 0, state.unifiedPackaging.cartonImageFileId);
  const inserted = state.packagingMode === 'unified'
    ? { ...cloneSku(sku), ...state.unifiedPackaging }
    : cloneSku(sku);
  state.skus.push(inserted);
  state.imagePreviews.sku.push('');
  state.imagePreviewFileIds.sku.push(inserted.skuImageFileId);
  state.imagePreviews.package.push(state.packagingMode === 'unified' ? packagePreview : '');
  state.imagePreviewFileIds.package.push(inserted.packageImageFileId);
  state.imagePreviews.carton.push(state.packagingMode === 'unified' ? cartonPreview : '');
  state.imagePreviewFileIds.carton.push(inserted.cartonImageFileId);
}

export function replaceSku(state: ProductEditorState, index: number, sku: SkuForm) {
  if (!state.skus[index]) return;
  const replacement = state.packagingMode === 'unified'
    ? { ...cloneSku(sku), ...state.unifiedPackaging }
    : cloneSku(sku);
  state.skus.splice(index, 1, replacement);
}

export function removeSku(state: ProductEditorState, index: number) {
  state.skus.splice(index, 1);
  for (const field of ['sku', 'package', 'carton'] as const) {
    state.imagePreviews[field].splice(index, 1);
    state.imagePreviewFileIds[field].splice(index, 1);
  }
}

export function updateUnifiedPackaging(state: ProductEditorState, packaging: PackagingForm) {
  const packageImageChanged = state.unifiedPackaging.packageImageFileId !== packaging.packageImageFileId;
  const cartonImageChanged = state.unifiedPackaging.cartonImageFileId !== packaging.cartonImageFileId;
  state.unifiedPackaging = { ...packaging };
  state.skus = applyUnifiedPackaging(state.skus, state.unifiedPackaging);
  if (packageImageChanged) setEditorImageFileId(state, 'package', null, packaging.packageImageFileId);
  if (cartonImageChanged) setEditorImageFileId(state, 'carton', null, packaging.cartonImageFileId);
}

export function setPackagingMode(state: ProductEditorState, mode: PackagingMode) {
  if (mode === state.packagingMode) return;
  ensureImageSlots(state);
  if (mode === 'unified') {
    const source = packagingFromSku(state.skus[0]);
    const packagePreview = matchingPreview(state, 'package', 0, source.packageImageFileId);
    const cartonPreview = matchingPreview(state, 'carton', 0, source.cartonImageFileId);
    state.unifiedPackaging = source;
    state.skus = applyUnifiedPackaging(state.skus, source);
    state.imagePreviews.package = state.skus.map(() => packagePreview);
    state.imagePreviewFileIds.package = state.skus.map(() => source.packageImageFileId);
    state.imagePreviews.carton = state.skus.map(() => cartonPreview);
    state.imagePreviewFileIds.carton = state.skus.map(() => source.cartonImageFileId);
  }
  state.packagingMode = mode;
}

export function setEditorImageFileId(
  state: ProductEditorState,
  kind: ProductEditorImageKind,
  index: number | null,
  fileId: number | null
) {
  if (kind === 'main') {
    if (state.imagePreviewFileIds.main !== fileId) state.imagePreviews.main = '';
    state.mainImageFileId = fileId;
    state.imagePreviewFileIds.main = fileId;
    return;
  }
  ensureImageSlots(state);
  const indexes = index === null ? state.skus.map((_, skuIndex) => skuIndex) : [index];
  for (const skuIndex of indexes) {
    const sku = state.skus[skuIndex];
    if (!sku) continue;
    if (state.imagePreviewFileIds[kind][skuIndex] !== fileId) state.imagePreviews[kind][skuIndex] = '';
    state.imagePreviewFileIds[kind][skuIndex] = fileId;
    if (kind === 'sku') sku.skuImageFileId = fileId;
    if (kind === 'package') sku.packageImageFileId = fileId;
    if (kind === 'carton') sku.cartonImageFileId = fileId;
  }
  if (index === null && kind === 'package') state.unifiedPackaging.packageImageFileId = fileId;
  if (index === null && kind === 'carton') state.unifiedPackaging.cartonImageFileId = fileId;
}

export function setEditorImagePreview(
  state: ProductEditorState,
  kind: ProductEditorImageKind,
  index: number | null,
  preview: string
) {
  if (kind === 'main') {
    state.imagePreviews.main = preview;
    state.imagePreviewFileIds.main = state.mainImageFileId;
    return;
  }
  ensureImageSlots(state);
  const indexes = index === null ? state.skus.map((_, skuIndex) => skuIndex) : [index];
  for (const skuIndex of indexes) {
    const sku = state.skus[skuIndex];
    if (!sku) continue;
    state.imagePreviews[kind][skuIndex] = preview;
    state.imagePreviewFileIds[kind][skuIndex] = kind === 'sku'
      ? sku.skuImageFileId
      : kind === 'package'
        ? sku.packageImageFileId
        : sku.cartonImageFileId;
  }
}

export function setDefaultQuote(
  quotes: ProductSupplierQuoteInput[],
  defaultIndex: number
): ProductSupplierQuoteInput[] {
  if (quotes[defaultIndex]?.status !== 'enabled') return reconcileQuoteDefaults(quotes);
  return quotes.map((quote, index) => ({
    ...quote,
    defaultQuote: index === defaultIndex
  }));
}

export function reconcileQuoteDefaults(quotes: ProductSupplierQuoteInput[]): ProductSupplierQuoteInput[] {
  const currentDefault = quotes.findIndex((quote) => quote.defaultQuote && quote.status === 'enabled');
  const fallback = quotes.findIndex((quote) => quote.status === 'enabled');
  const defaultIndex = currentDefault >= 0 ? currentDefault : fallback;
  return quotes.map((quote, index) => ({
    ...quote,
    defaultQuote: index === defaultIndex
  }));
}

export function createEditorState(product?: Product): ProductEditorState {
  const payload = product ? toProductFormPayload(product) : undefined;
  const skus = payload?.skus.map(cloneSku) ?? [];
  const packagingMode = detectPackagingMode(skus);
  return {
    productId: product?.id ?? null,
    loadedProductType: product?.productType ?? null,
    itemNo: payload?.itemNo ?? '',
    productName: payload?.productName ?? '',
    categoryId: payload?.categoryId ?? null,
    brand: payload?.brand ?? '',
    mainImageFileId: payload?.mainImageFileId ?? null,
    remark: payload?.remark ?? '',
    specifications: payload?.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })) ?? [],
    skus,
    packagingMode,
    unifiedPackaging: packagingFromSku(skus[0]),
    imagePreviews: {
      main: product?.mainImageUrl ?? '',
      sku: product?.skus.map((sku) => sku.skuImageUrl ?? '') ?? [],
      package: product?.skus.map((sku) => sku.packageImageUrl ?? '') ?? [],
      carton: product?.skus.map((sku) => sku.cartonImageUrl ?? '') ?? []
    },
    imagePreviewFileIds: {
      main: payload?.mainImageFileId ?? null,
      sku: skus.map((sku) => sku.skuImageFileId),
      package: skus.map((sku) => sku.packageImageFileId),
      carton: skus.map((sku) => sku.cartonImageFileId)
    }
  };
}

function isBlank(value: string | null | undefined) {
  return !value || value.trim().length === 0;
}

function isNegative(value: number | null) {
  return value !== null && value < 0;
}

export function validateStep(
  state: ProductEditorState,
  step: ProductEditorStep,
  context: ProductEditorValidationContext = {}
): Record<string, string> {
  const errors: Record<string, string> = {};
  if (step === 'basic') {
    if (isBlank(state.itemNo)) errors.itemNo = '请输入货号';
    if (isBlank(state.productName)) errors.productName = '请输入商品名称';
    if (state.categoryId === null) errors.categoryId = '请选择商品分类';
  }

  if (step === 'sku') {
    if (state.skus.length === 0) return { skus: '请至少添加一个 SKU' };
    state.skus.forEach((sku, index) => {
      if (isBlank(sku.skuName)) errors[`skus.${index}.skuName`] = '请输入 SKU 名称';
      if (isBlank(sku.salesUnit)) errors[`skus.${index}.salesUnit`] = '请输入销售单位';
      if (isNegative(sku.defaultSalePrice)) errors[`skus.${index}.defaultSalePrice`] = '默认售价不能小于 0';
      if (isNegative(sku.standardCost)) errors[`skus.${index}.standardCost`] = '标准成本不能小于 0';
      if (isNegative(sku.safetyStockQuantity)) errors[`skus.${index}.safetyStockQuantity`] = '安全库存不能小于 0';
    });
  }

  if (step === 'procurement') {
    state.skus.forEach((sku, skuIndex) => {
      const quotes = sku.supplierQuotes ?? [];
      const supplierIds = new Set<number>();
      quotes.forEach((quote, quoteIndex) => {
        const prefix = `skus.${skuIndex}.supplierQuotes.${quoteIndex}`;
        if (!Number.isFinite(quote.supplierId) || quote.supplierId <= 0) {
          errors[`${prefix}.supplierId`] = '请选择供应商';
        } else if (supplierIds.has(quote.supplierId)) {
          errors[`${prefix}.supplierId`] = '同一 SKU 的供应商报价不能重复';
        } else if (context.validSupplierIds && !context.validSupplierIds.has(quote.supplierId)) {
          errors[`${prefix}.supplierId`] = '请选择有效供应商';
        }
        supplierIds.add(quote.supplierId);
        if (!Number.isFinite(quote.purchasePrice) || quote.purchasePrice < 0) {
          errors[`${prefix}.purchasePrice`] = '采购价不能小于 0';
        }
        if (!Number.isFinite(quote.minPurchaseQuantity) || quote.minPurchaseQuantity <= 0) {
          errors[`${prefix}.minPurchaseQuantity`] = '最小采购量必须大于 0';
        }
        if (quote.defaultQuote && quote.status !== 'enabled') {
          errors[`${prefix}.defaultQuote`] = '禁用报价不能设为默认报价';
        }
      });
      if (quotes.filter((quote) => quote.defaultQuote).length > 1) {
        errors[`skus.${skuIndex}.supplierQuotes`] = '每个 SKU 最多只能有一个默认报价';
      }
    });
  }

  if (step === 'packaging') {
    const packagingMessages: Partial<Record<keyof PackagingForm, string>> = {
      packageLengthCm: '包装长不能小于 0',
      packageWidthCm: '包装宽不能小于 0',
      packageHeightCm: '包装高不能小于 0',
      packageVolumeCm3: '包装体积不能小于 0',
      innerPackageLengthCm: '内盒长不能小于 0',
      innerPackageWidthCm: '内盒宽不能小于 0',
      innerPackageHeightCm: '内盒高不能小于 0',
      netWeightKg: '净重不能小于 0',
      grossWeightKg: '毛重不能小于 0',
      gramWeightG: '克重不能小于 0',
      innerPackageWeightKg: '内包装重量不能小于 0'
    };
    state.skus.forEach((sku, index) => {
      for (const field of packagingFields) {
        if (!(field in packagingMessages)) continue;
        const value = sku[field];
        if (typeof value === 'number' && value < 0) {
          errors[`skus.${index}.${field}`] = packagingMessages[field] ?? '数值不能小于 0';
        }
      }
      if (!isValidCartonQuantity(sku.cartonQuantity)) {
        errors[`skus.${index}.cartonQuantity`] = CARTON_QUANTITY_ERROR;
      }
      if (
        !errors[`skus.${index}.grossWeightKg`]
        && sku.netWeightKg !== null
        && sku.grossWeightKg !== null
        && sku.grossWeightKg < sku.netWeightKg
      ) {
        errors[`skus.${index}.grossWeightKg`] = '毛重不能小于净重';
      }
    });
  }

  return errors;
}

export function toProductPayload(state: ProductEditorState): ProductFormPayload {
  const skus = state.skus.map(cloneSku);
  const derivedType: ProductType = skus.length > 1
    || skus.some((sku) => sku.specificationValues.some((value) => !isBlank(value)))
    ? 'variant'
    : 'simple';
  const productType = state.loadedProductType === 'variant'
    ? 'variant'
    : state.loadedProductType === 'simple' && derivedType === 'simple'
      ? 'simple'
      : derivedType;
  return {
    itemNo: state.itemNo,
    productName: state.productName,
    categoryId: state.categoryId,
    brand: state.brand,
    productType,
    mainImageFileId: state.mainImageFileId,
    remark: state.remark,
    specifications: state.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })),
    skus
  };
}
