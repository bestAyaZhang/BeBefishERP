import type { ProductType } from './types';

export const EMPTY_VALUE = '--';

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
});

export function formatText(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return EMPTY_VALUE;
  const text = String(value).trim();
  return text || EMPTY_VALUE;
}

export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined || !Number.isFinite(value)
    ? EMPTY_VALUE
    : moneyFormatter.format(value);
}

export function formatWithUnit(value: number | null | undefined, unit: string): string {
  return value === null || value === undefined || !Number.isFinite(value)
    ? EMPTY_VALUE
    : `${value} ${unit}`;
}

export function formatDimensions(
  length: number | null | undefined,
  width: number | null | undefined,
  height: number | null | undefined
): string {
  if (length === null || length === undefined
    || width === null || width === undefined
    || height === null || height === undefined) return EMPTY_VALUE;
  return `${length} × ${width} × ${height} cm`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY_VALUE;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? EMPTY_VALUE : dateFormatter.format(date);
}

export function formatRecordStatus(status: string | null | undefined): string {
  if (status === 'enabled') return '启用';
  if (status === 'disabled') return '停用';
  return formatText(status);
}

export function formatProductStatus(status: string | null | undefined): string {
  if (status === 'enabled') return '在售';
  if (status === 'disabled') return '已停用';
  return formatText(status);
}

export function formatProductType(type: ProductType): string {
  return type === 'variant' ? '多规格商品' : '单规格商品';
}
