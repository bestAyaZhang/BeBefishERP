import { toProductFormPayload } from '../productFormMapper';
import type {
  Product,
  ProductFormPayload,
  ProductSpecification,
  ProductSupplierQuoteInput,
  ProductType,
  SkuForm
} from '../types';
import type { RecordStatus } from '../../masterdata/types';

export const productEditorSteps = ['basic', 'sku', 'procurement', 'packaging', 'images', 'confirm'] as const;
export const MAX_CARTON_QUANTITY = 2_147_483_647;
export const CARTON_QUANTITY_ERROR = '装箱数必须为 1 到 2147483647 之间的整数';
export const MAX_SAFE_MONEY = '900719925474.0991';
export const MAX_SAFE_DIMENSION = '999999999.999';
export const MAX_SAFE_VOLUME = '9007199254740.991';
const PRODUCT_DIMENSION_NAMES = new Set(['口径', '高度', '容量', '重量']);

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
  | 'productLengthCm'
  | 'productWidthCm'
  | 'productHeightCm'
  | 'capacityMl'
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
  productType: ProductType;
  status: RecordStatus;
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
  'productLengthCm',
  'productWidthCm',
  'productHeightCm',
  'capacityMl',
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
    productLengthCm: null,
    productWidthCm: null,
    productHeightCm: null,
    capacityMl: null,
    netWeightKg: null,
    grossWeightKg: null,
    gramWeightG: null,
    innerPackageWeightKg: null,
    packagingMethod: '',
    cartonQuantity: null,
    skuImageFileId: null,
    packageImageFileId: null,
    cartonImageFileId: null,
    supplierQuotes: [],
    defaultSku: false,
    status: 'enabled'
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
    productLengthCm: sku?.productLengthCm ?? null,
    productWidthCm: sku?.productWidthCm ?? null,
    productHeightCm: sku?.productHeightCm ?? null,
    capacityMl: sku?.capacityMl ?? null,
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
  state.skus = reconcileSkuDefaults(state.skus, inserted.defaultSku ? state.skus.length - 1 : undefined);
}

export function replaceSku(state: ProductEditorState, index: number, sku: SkuForm) {
  if (!state.skus[index]) return;
  const replacedWasDefault = state.skus[index].defaultSku;
  const replacement = state.packagingMode === 'unified'
    ? { ...cloneSku(sku), ...state.unifiedPackaging }
    : cloneSku(sku);
  state.skus.splice(index, 1, replacement);
  const preferredIndex = replacement.defaultSku && replacement.status === 'enabled'
    ? index
    : replacedWasDefault
      ? undefined
      : state.skus.findIndex((candidate) => candidate.defaultSku && candidate.status === 'enabled');
  state.skus = reconcileSkuDefaults(
    state.skus,
    preferredIndex !== undefined && preferredIndex >= 0 ? preferredIndex : undefined
  );
}

export function removeSku(state: ProductEditorState, index: number) {
  state.skus.splice(index, 1);
  for (const field of ['sku', 'package', 'carton'] as const) {
    state.imagePreviews[field].splice(index, 1);
    state.imagePreviewFileIds[field].splice(index, 1);
  }
  state.skus = reconcileSkuDefaults(state.skus);
}

export function reconcileSkuDefaults(skus: SkuForm[], preferredIndex?: number): SkuForm[] {
  const enabledIndexes = skus
    .map((sku, index) => sku.status === 'enabled' ? index : -1)
    .filter((index) => index >= 0);
  if (enabledIndexes.length === 0) {
    return skus.map((sku) => ({ ...cloneSku(sku), defaultSku: false }));
  }
  const currentDefault = skus.findIndex((sku) => sku.defaultSku && sku.status === 'enabled');
  const defaultIndex = preferredIndex !== undefined && enabledIndexes.includes(preferredIndex)
    ? preferredIndex
    : currentDefault >= 0
      ? currentDefault
      : enabledIndexes[0];
  return skus.map((sku, index) => ({
    ...cloneSku(sku),
    defaultSku: index === defaultIndex
  }));
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
    productType: payload?.productType ?? 'simple',
    status: payload?.status === 'draft' ? 'enabled' : payload?.status ?? 'enabled',
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

export function isValidDecimal(value: number | null, integerDigits: number, fractionDigits: number) {
  if (value === null) return true;
  if (!Number.isFinite(value) || value < 0) return false;
  const match = /^(\d+)(?:\.(\d+))?$/.exec(String(value));
  if (!match) return false;
  const integerPart = match[1] ?? '';
  const fractionPart = match[2] ?? '';
  if (integerPart.length > integerDigits || fractionPart.length > fractionDigits) return false;
  const scaled = BigInt(`${integerPart}${fractionPart.padEnd(fractionDigits, '0')}`);
  return scaled <= BigInt(Number.MAX_SAFE_INTEGER);
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
    if (state.skus.length === 0) errors.skus = '请至少添加一个 SKU';
    if (
      state.productType === 'simple'
      && (state.skus.length > 1
        || state.specifications.some((specification) => !PRODUCT_DIMENSION_NAMES.has(specification.name.trim()))
        || state.skus.some((sku) => sku.specificationValues.some((value) => !isBlank(value))))
    ) {
      errors.productType = '单规格商品不能包含规格维度或规格值';
    }
    if (state.skus.length > 0) {
      const enabledSkus = state.skus.filter((sku) => sku.status === 'enabled');
      const defaultSkus = state.skus.filter((sku) => sku.defaultSku);
      if (enabledSkus.length === 0) errors.skus = '至少保留一个启用的 SKU';
      else if (defaultSkus.length !== 1 || defaultSkus[0].status !== 'enabled') errors.skus = '请选择一个启用的默认 SKU';
      state.skus.forEach((sku, index) => {
        if (isBlank(sku.skuName)) errors[`skus.${index}.skuName`] = '请输入 SKU 名称';
        if (isBlank(sku.salesUnit)) errors[`skus.${index}.salesUnit`] = '请输入销售单位';
        if (isNegative(sku.defaultSalePrice)) errors[`skus.${index}.defaultSalePrice`] = '默认售价不能小于 0';
        if (isNegative(sku.standardCost)) errors[`skus.${index}.standardCost`] = '标准成本不能小于 0';
        if (isNegative(sku.safetyStockQuantity)) errors[`skus.${index}.safetyStockQuantity`] = '安全库存不能小于 0';
        if (!isNegative(sku.defaultSalePrice) && !isValidDecimal(sku.defaultSalePrice, 15, 4)) {
          errors[`skus.${index}.defaultSalePrice`] = '默认售价最多允许 15 位整数和 4 位小数';
        }
        if (!isNegative(sku.standardCost) && !isValidDecimal(sku.standardCost, 15, 4)) {
          errors[`skus.${index}.standardCost`] = '标准成本最多允许 15 位整数和 4 位小数';
        }
        if (!isNegative(sku.safetyStockQuantity) && !isValidDecimal(sku.safetyStockQuantity, 14, 4)) {
          errors[`skus.${index}.safetyStockQuantity`] = '安全库存最多允许 14 位整数和 4 位小数';
        }
      });
    }
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
        } else if (!isValidDecimal(quote.purchasePrice, 15, 4)) {
          errors[`${prefix}.purchasePrice`] = '采购价最多允许 15 位整数和 4 位小数';
        }
        if (!Number.isFinite(quote.minPurchaseQuantity) || quote.minPurchaseQuantity <= 0) {
          errors[`${prefix}.minPurchaseQuantity`] = '最小采购量必须大于 0';
        } else if (!isValidDecimal(quote.minPurchaseQuantity, 15, 4)) {
          errors[`${prefix}.minPurchaseQuantity`] = '最小采购量最多允许 15 位整数和 4 位小数';
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
      productLengthCm: '产品长不能小于 0',
      productWidthCm: '产品宽不能小于 0',
      productHeightCm: '产品高不能小于 0',
      capacityMl: '容量不能小于 0',
      netWeightKg: '净重不能小于 0',
      grossWeightKg: '毛重不能小于 0',
      gramWeightG: '克重不能小于 0',
      innerPackageWeightKg: '内包装重量不能小于 0'
    };
    const packagingDecimalLimits: Partial<Record<keyof PackagingForm, [number, number, string]>> = {
      packageLengthCm: [9, 3, '包装长最多允许 9 位整数和 3 位小数'],
      packageWidthCm: [9, 3, '包装宽最多允许 9 位整数和 3 位小数'],
      packageHeightCm: [9, 3, '包装高最多允许 9 位整数和 3 位小数'],
      packageVolumeCm3: [15, 3, '包装体积最多允许 15 位整数和 3 位小数'],
      innerPackageLengthCm: [9, 3, '内盒长最多允许 9 位整数和 3 位小数'],
      innerPackageWidthCm: [9, 3, '内盒宽最多允许 9 位整数和 3 位小数'],
      innerPackageHeightCm: [9, 3, '内盒高最多允许 9 位整数和 3 位小数'],
      productLengthCm: [9, 3, '产品长最多允许 9 位整数和 3 位小数'],
      productWidthCm: [9, 3, '产品宽最多允许 9 位整数和 3 位小数'],
      productHeightCm: [9, 3, '产品高最多允许 9 位整数和 3 位小数'],
      capacityMl: [9, 3, '容量最多允许 9 位整数和 3 位小数'],
      netWeightKg: [9, 3, '净重最多允许 9 位整数和 3 位小数'],
      grossWeightKg: [9, 3, '毛重最多允许 9 位整数和 3 位小数'],
      gramWeightG: [9, 3, '克重最多允许 9 位整数和 3 位小数'],
      innerPackageWeightKg: [9, 3, '内包装重量最多允许 9 位整数和 3 位小数']
    };
    state.skus.forEach((sku, index) => {
      for (const field of packagingFields) {
        if (!(field in packagingMessages)) continue;
        const value = sku[field];
        if (typeof value === 'number' && value < 0) {
          errors[`skus.${index}.${field}`] = packagingMessages[field] ?? '数值不能小于 0';
        } else if (typeof value === 'number' && packagingDecimalLimits[field]) {
          const [integerDigits, fractionDigits, message] = packagingDecimalLimits[field]!;
          if (!isValidDecimal(value, integerDigits, fractionDigits)) {
            errors[`skus.${index}.${field}`] = message;
          }
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
  return {
    itemNo: state.itemNo,
    productName: state.productName,
    categoryId: state.categoryId,
    brand: state.brand,
    productType: state.productType,
    status: state.status,
    mainImageFileId: state.mainImageFileId,
    remark: state.remark,
    specifications: state.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })),
    skus
  };
}
