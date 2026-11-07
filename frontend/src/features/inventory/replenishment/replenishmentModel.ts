export const demandHistoryDays = 30;
export const replenishmentDayLimit = 36_500;
export const replenishmentThresholdLimit = 99_999_999_999_999;
const maximumCalculatedThreshold = BigInt(Number.MAX_SAFE_INTEGER);
const quantityScale = 10_000n;
const averageScale = 1_000_000n;
const historyLength = BigInt(demandHistoryDays);

export type ReplenishmentStatus = 'critical' | 'warning' | 'normal' | 'pending';
export type ReplenishmentSource = 'auto' | 'manual' | 'fallback' | 'pending';

export interface ReplenishmentConfig {
  mode: 'auto' | 'manual';
  bufferDays: number;
  leadDays: number;
  manualSafety: number | null;
  manualPoint: number | null;
}

export interface ReplenishmentWarehouse {
  id: number;
  name: string;
}

export interface ReplenishmentResult {
  average: number | null;
  safety: number | null;
  point: number | null;
  source: ReplenishmentSource;
  status: ReplenishmentStatus;
}

export interface ReplenishmentRow extends ReplenishmentResult {
  skuId: number;
  sku: string;
  name: string;
  warehouses: ReplenishmentWarehouse[];
  warehouseId: number | null;
  stock: number;
  stockExact?: string;
  historyDays: number;
  demandTotal: number;
  demandTotalExact?: string;
  windowStart: string;
  windowEnd: string;
  config: ReplenishmentConfig;
}

export interface ReplenishmentQuery {
  page: number;
  size: number;
  keyword?: string;
  warehouseId?: number;
  status?: ReplenishmentStatus;
}

export interface ReplenishmentPage {
  items: ReplenishmentRow[];
  total: number;
  page: number;
  size: number;
  warehouses: ReplenishmentWarehouse[];
  counts: Record<ReplenishmentStatus, number>;
}

function isBoundedInteger(value: number | null, limit: number): value is number {
  return value !== null && Number.isSafeInteger(value) && value >= 0 && value <= limit;
}

export function isReplenishmentDays(value: number | null): value is number {
  return isBoundedInteger(value, replenishmentDayLimit);
}

export function isReplenishmentThreshold(value: number | null): value is number {
  return isBoundedInteger(value, replenishmentThresholdLimit);
}

function scaledQuantity(value: number, exact?: string): bigint | null {
  const quantity = /^(-?)(\d+)(?:\.(\d{1,4}))?$/.exec(exact ?? String(value));
  if (!quantity) return null;
  const [, sign, whole, fraction = ''] = quantity;
  return BigInt(sign + whole + fraction.padEnd(4, '0'));
}

function calculateThreshold(scaledDemand: bigint, days: number) {
  // Apply the ceiling only after computing the full threshold in decimal units.
  const denominator = historyLength * quantityScale;
  return (scaledDemand * BigInt(days) + denominator - 1n) / denominator;
}

function calculateAverage(scaledDemand: bigint) {
  const unitsAtAverageScale = scaledDemand * (averageScale / quantityScale);
  const halfWindow = historyLength / 2n;
  const rounded = unitsAtAverageScale < 0n
    ? (unitsAtAverageScale - halfWindow) / historyLength
    : (unitsAtAverageScale + halfWindow) / historyLength;
  return Number(rounded) / Number(averageScale);
}

type DemandSnapshot = Pick<ReplenishmentRow, 'historyDays' | 'demandTotal' | 'demandTotalExact' | 'stock' | 'stockExact'>;

function statusFor(row: DemandSnapshot, safety: number, point: number): ReplenishmentStatus {
  const stock = scaledQuantity(row.stock, row.stockExact);
  const atOrBelow = (threshold: number) => stock === null
    ? row.stock <= threshold
    : stock <= BigInt(threshold) * quantityScale;
  if (atOrBelow(safety)) return 'critical';
  if (atOrBelow(point)) return 'warning';
  return 'normal';
}

export function evaluate(row: DemandSnapshot, config: ReplenishmentConfig): ReplenishmentResult {
  const demand = row.historyDays >= demandHistoryDays ? scaledQuantity(row.demandTotal, row.demandTotalExact) : null;
  const average = demand === null ? null : calculateAverage(demand);
  const pending: ReplenishmentResult = { average, safety: null, point: null, source: 'pending', status: 'pending' };

  if (config.mode === 'auto' && row.historyDays >= demandHistoryDays) {
    if (!isReplenishmentDays(config.bufferDays) || !isReplenishmentDays(config.leadDays)) return pending;
    if (demand === null || demand < 0n) return pending;
    const calculatedPoint = calculateThreshold(demand, config.bufferDays + config.leadDays);
    if (calculatedPoint > maximumCalculatedThreshold) return pending;
    const point = Number(calculatedPoint);
    const safety = Number(calculateThreshold(demand, config.bufferDays));
    return { average, safety, point, source: 'auto', status: statusFor(row, safety, point) };
  }

  const { manualSafety: safety, manualPoint: point } = config;
  if (!isReplenishmentThreshold(safety) || !isReplenishmentThreshold(point) || point < safety) return pending;
  const source = config.mode === 'auto' ? 'fallback' : 'manual';
  return { average, safety, point, source, status: statusFor(row, safety, point) };
}
