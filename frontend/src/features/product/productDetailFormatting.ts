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
  hour12: false,
  timeZone: 'Asia/Shanghai'
});

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const LOCAL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/;

function parseDate(value: string): Date {
  const match = LOCAL_DATE_TIME_PATTERN.exec(value);
  if (!match) return new Date(value);

  const [, yearText, monthText, dayText, hourText, minuteText, secondText = '0', fractionText = ''] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const millisecond = Number(fractionText.padEnd(3, '0').slice(0, 3));
  const localClock = Date.UTC(year, month - 1, day, hour, minute, second, millisecond);
  const normalized = new Date(localClock);

  if (normalized.getUTCFullYear() !== year
    || normalized.getUTCMonth() !== month - 1
    || normalized.getUTCDate() !== day
    || normalized.getUTCHours() !== hour
    || normalized.getUTCMinutes() !== minute
    || normalized.getUTCSeconds() !== second) return new Date(Number.NaN);

  return new Date(localClock - SHANGHAI_OFFSET_MS);
}

export function formatText(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return EMPTY_VALUE;
  if (typeof value === 'number' && !Number.isFinite(value)) return EMPTY_VALUE;
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
  if (length === null || length === undefined || !Number.isFinite(length)
    || width === null || width === undefined || !Number.isFinite(width)
    || height === null || height === undefined || !Number.isFinite(height)) return EMPTY_VALUE;
  return `${length} × ${width} × ${height} cm`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY_VALUE;
  const date = parseDate(value);
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
