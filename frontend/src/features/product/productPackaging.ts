import type { ProductSku } from './types';

export type PackagingMode = 'uniform' | 'per-sku';

type PackagingField =
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
  | 'cartonQuantity'
  | 'packagingMethod'
  | 'packageImageFileId'
  | 'cartonImageFileId';

export type PackagingComparableSku = Pick<ProductSku, PackagingField>
  & Partial<Pick<ProductSku, 'packageImageUrl' | 'cartonImageUrl'>>;

const PACKAGING_FIELDS: readonly PackagingField[] = [
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
  'cartonQuantity',
  'packagingMethod',
  'packageImageFileId',
  'cartonImageFileId'
];

export function getProductPackagingMode(skus: readonly PackagingComparableSku[]): PackagingMode {
  if (skus.length <= 1) return 'uniform';
  const first = skus[0];
  return skus.slice(1).every((sku) => (
    PACKAGING_FIELDS.every((field) => sku[field] === first[field])
  )) ? 'uniform' : 'per-sku';
}
