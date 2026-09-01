import { describe, expect, it } from 'vitest';
import {
  getProductPackagingMode,
  type PackagingComparableSku
} from './productPackaging';

const basePackaging: PackagingComparableSku = {
  packageLengthCm: 42,
  packageWidthCm: 28,
  packageHeightCm: 24,
  packageVolumeCm3: 28224,
  innerPackageLengthCm: 10,
  innerPackageWidthCm: 10,
  innerPackageHeightCm: null,
  netWeightKg: 8.4,
  grossWeightKg: 9.1,
  gramWeightG: 210,
  innerPackageWeightKg: null,
  cartonQuantity: 40,
  packagingMethod: '彩盒',
  packageImageFileId: 202,
  cartonImageFileId: 203
};

describe('getProductPackagingMode', () => {
  it('treats equal packaging as uniform without comparing temporary URLs', () => {
    expect(getProductPackagingMode([
      { ...basePackaging, packageImageUrl: '/uploads/first.png', cartonImageUrl: '/uploads/carton-a.png' },
      { ...basePackaging, packageImageUrl: '/temporary/second.png', cartonImageUrl: '/temporary/carton-b.png' }
    ])).toBe('uniform');
  });

  it('switches to per-sku when one exact numeric field differs', () => {
    expect(getProductPackagingMode([
      basePackaging,
      { ...basePackaging, grossWeightKg: 9.1001 }
    ])).toBe('per-sku');
  });

  it('switches to per-sku when only the package volume differs', () => {
    expect(getProductPackagingMode([
      basePackaging,
      { ...basePackaging, packageVolumeCm3: 28224.01 }
    ])).toBe('per-sku');
  });

  it('switches to per-sku when an image file id differs', () => {
    expect(getProductPackagingMode([
      basePackaging,
      { ...basePackaging, packageImageFileId: 999 }
    ])).toBe('per-sku');
  });

  it('ignores object field order and treats matching null values as equal', () => {
    const reordered: PackagingComparableSku = {
      cartonImageFileId: 203,
      packageImageFileId: 202,
      packagingMethod: '彩盒',
      cartonQuantity: 40,
      innerPackageWeightKg: null,
      gramWeightG: 210,
      grossWeightKg: 9.1,
      netWeightKg: 8.4,
      innerPackageHeightCm: null,
      innerPackageWidthCm: 10,
      innerPackageLengthCm: 10,
      packageHeightCm: 24,
      packageWidthCm: 28,
      packageLengthCm: 42,
      packageVolumeCm3: 28224
    };

    expect(getProductPackagingMode([basePackaging, reordered])).toBe('uniform');
  });

  it('treats zero or one SKU as uniform', () => {
    expect(getProductPackagingMode([])).toBe('uniform');
    expect(getProductPackagingMode([basePackaging])).toBe('uniform');
  });
});
