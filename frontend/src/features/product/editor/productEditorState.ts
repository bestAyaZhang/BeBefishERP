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

export interface ProductEditorState {
  productId: number | null;
  itemNo: string;
  productName: string;
  categoryId: number | null;
  brand: string;
  productType: ProductType;
  mainImageFileId: number | null;
  remark: string;
  specifications: ProductSpecification[];
  skus: SkuForm[];
  packagingMode: PackagingMode;
  unifiedPackaging: PackagingForm;
  imagePreviews: ProductEditorImagePreviews;
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

export function setDefaultQuote(
  quotes: ProductSupplierQuoteInput[],
  defaultIndex: number
): ProductSupplierQuoteInput[] {
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
    itemNo: payload?.itemNo ?? '',
    productName: payload?.productName ?? '',
    categoryId: payload?.categoryId ?? null,
    brand: payload?.brand ?? '',
    productType: payload?.productType ?? 'simple',
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
    }
  };
}

function isBlank(value: string | null | undefined) {
  return !value || value.trim().length === 0;
}

function isNegative(value: number | null) {
  return value !== null && value < 0;
}

export function validateStep(state: ProductEditorState, step: ProductEditorStep): Record<string, string> {
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
    });
  }

  if (step === 'procurement') {
    state.skus.forEach((sku, skuIndex) => {
      const quotes = sku.supplierQuotes ?? [];
      quotes.forEach((quote, quoteIndex) => {
        const prefix = `skus.${skuIndex}.supplierQuotes.${quoteIndex}`;
        if (!quote.supplierId) errors[`${prefix}.supplierId`] = '请选择供应商';
        if (isNegative(quote.purchasePrice)) errors[`${prefix}.purchasePrice`] = '采购价不能小于 0';
        if (isNegative(quote.minPurchaseQuantity)) errors[`${prefix}.minPurchaseQuantity`] = '最小采购量不能小于 0';
      });
      if (quotes.filter((quote) => quote.defaultQuote).length > 1) {
        errors[`skus.${skuIndex}.supplierQuotes`] = '每个 SKU 只能有一个默认报价';
      }
    });
  }

  if (step === 'packaging') {
    state.skus.forEach((sku, index) => {
      if (sku.netWeightKg !== null && sku.grossWeightKg !== null && sku.grossWeightKg < sku.netWeightKg) {
        errors[`skus.${index}.grossWeightKg`] = '毛重不能小于净重';
      }
      for (const field of packagingFields) {
        if (field === 'packagingMethod' || field === 'packageImageFileId' || field === 'cartonImageFileId') continue;
        const value = sku[field];
        if (typeof value === 'number' && value < 0) errors[`skus.${index}.${field}`] = '数值不能小于 0';
      }
    });
  }

  return errors;
}

export function toProductPayload(state: ProductEditorState): ProductFormPayload {
  const skus = state.skus.map(cloneSku);
  const hasVariants = state.productType === 'variant'
    || skus.length > 1;
  return {
    itemNo: state.itemNo,
    productName: state.productName,
    categoryId: state.categoryId,
    brand: state.brand,
    productType: hasVariants ? 'variant' : 'simple',
    mainImageFileId: state.mainImageFileId,
    remark: state.remark,
    specifications: state.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })),
    skus
  };
}
