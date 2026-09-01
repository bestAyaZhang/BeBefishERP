import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatDimensions,
  formatText,
  formatWithUnit
} from './productDetailFormatting';

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
});
