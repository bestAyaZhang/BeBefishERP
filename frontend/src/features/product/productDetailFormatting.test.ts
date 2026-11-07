import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatDimensions,
  formatProductDimensions,
  formatText,
  formatWithUnit
} from './productDetailFormatting';
import type { ProductSku } from './types';

describe('product detail formatting', () => {
  it('preserves valid zero and rejects non-finite numeric text', () => {
    expect(formatText(0)).toBe('0');
    expect(formatText(Number.NaN)).toBe('--');
    expect(formatText(Number.POSITIVE_INFINITY)).toBe('--');
  });

  it('keeps dimensions and units finite without turning missing values into zero', () => {
    expect(formatDimensions(0, 12, 3)).toBe('0 × 12 × 3 cm');
    expect(formatDimensions(null, 12, 3)).toBe('--');
    expect(formatDimensions(Number.NaN, 12, 3)).toBe('--');
    expect(formatDimensions(12, Number.POSITIVE_INFINITY, 3)).toBe('--');
    expect(formatWithUnit(0, 'cm³')).toBe('0 cm³');
    expect(formatWithUnit(null, 'cm³')).toBe('--');
    expect(formatWithUnit(Number.NaN, 'cm³')).toBe('--');
  });

  it('interprets backend LocalDateTime values in Asia/Shanghai and fails invalid dates safely', () => {
    expect(formatDate('2026-09-01T08:30:00')).toBe('2026/09/01 08:30');
    expect(formatDate('2026-09-01T00:30:00Z')).toBe('2026/09/01 08:30');
    expect(formatDate('not-a-date')).toBe('--');
  });

  it.each([
    [{ productMouthDiameterCm: 8.2, productHeightCm: 8.9 }, '口径 8.2 × 高 8.9 cm'],
    [{ productMouthDiameterCm: 0, productHeightCm: 0 }, '口径 0 × 高 0 cm'],
    [{ productMouthDiameterCm: 8.2, productLengthCm: 10, productWidthCm: 11, productHeightCm: 8.9 }, '口径 8.2 × 高 8.9 cm'],
    [{ productMouthDiameterCm: null, productLengthCm: 12, productWidthCm: 9, productHeightCm: 8 }, '12 × 9 × 8 cm'],
    [{ productMouthDiameterCm: Number.NaN, productLengthCm: 12, productWidthCm: 9, productHeightCm: 8 }, '12 × 9 × 8 cm'],
    [{ productMouthDiameterCm: 8.2, productHeightCm: null }, '--'],
    [{ productLengthCm: 8.2, productHeightCm: 8.9 }, '--'],
    [{ productMouthDiameterCm: Number.POSITIVE_INFINITY, productHeightCm: 8.9 }, '--'],
    [{ productMouthDiameterCm: 8.2, productHeightCm: Number.NaN }, '--']
  ])('formats actual product measurements without inferring a mouth diameter (%j)', (measurements, expected) => {
    expect(formatProductDimensions(measurements as ProductSku)).toBe(expected);
  });

  it('returns an empty value for an unavailable SKU', () => {
    expect(formatProductDimensions()).toBe('--');
    expect(formatProductDimensions(null)).toBe('--');
  });
});
