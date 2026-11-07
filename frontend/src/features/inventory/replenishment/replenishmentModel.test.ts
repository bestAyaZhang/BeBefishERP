import { describe, expect, it } from 'vitest';
import { evaluate } from './replenishmentModel';

describe('evaluate replenishment', () => {
  it('keeps exact whole-unit thresholds for decimal demand and rounds each threshold separately', () => {
    const config = { mode: 'auto', bufferDays: 30_000, leadDays: 1, manualSafety: null, manualPoint: null } as const;
    expect(evaluate({ historyDays: 30, demandTotal: 0.017, stock: 17 }, config)).toMatchObject({
      safety: 17, point: 18, source: 'auto', status: 'critical'
    });
    expect(evaluate(
      { historyDays: 30, demandTotal: 34, stock: 3 },
      { ...config, bufferDays: 1 }
    )).toMatchObject({ average: 1.133333, safety: 2, point: 3, source: 'auto', status: 'warning' });
  });

  it('uses exact quantity strings when JSON numbers lose fractional digits', () => {
    const config = { mode: 'manual', bufferDays: 0, leadDays: 0, manualSafety: 10, manualPoint: 99_999_999_999_999 } as const;
    expect(evaluate(
      { historyDays: 30, demandTotal: 0, stock: 99_999_999_999_999, stockExact: '99999999999999.0001' },
      config
    ).status).toBe('normal');
    expect(evaluate(
      { historyDays: 30, demandTotal: 99_999_999_999_999, demandTotalExact: '99999999999999.0001', stock: 1 },
      { ...config, mode: 'auto', bufferDays: 30 }
    ).safety).toBe(100_000_000_000_000);
  });

  it('keeps the average but omits thresholds that exceed safe integer precision', () => {
    const config = { mode: 'auto', bufferDays: 36_500, leadDays: 36_500, manualSafety: null, manualPoint: null } as const;
    const result = evaluate({ historyDays: 30, demandTotal: 99_999_999_999_999, stock: 7 }, config);
    expect(result).toMatchObject({ safety: null, point: null, source: 'pending', status: 'pending' });
    expect(result.average).toBeGreaterThan(3_000_000_000_000);
  });
});
