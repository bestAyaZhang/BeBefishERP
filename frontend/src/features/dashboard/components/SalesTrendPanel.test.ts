import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { DashboardSalesTrendPoint } from '../types';
import SalesTrendPanel from './SalesTrendPanel.vue';

const validPoints: DashboardSalesTrendPoint[] = [
  { date: '2026-08-31', month: null, salesAmount: 1250.5, orderCount: 2 },
  { date: '2026-09-01', month: null, salesAmount: 2500, orderCount: 4 }
];

describe('SalesTrendPanel', () => {
  it('renders visible dual-axis units and scales from the current maxima', () => {
    const wrapper = mount(SalesTrendPanel, { props: { points: validPoints } });

    expect(wrapper.get('[data-testid="left-axis-unit"]').text()).toBe('销售额（CNY）');
    expect(wrapper.get('[data-testid="right-axis-unit"]').text()).toBe('订单数（单）');

    const salesTicks = wrapper.findAll('[data-testid="left-axis-tick"]').map((tick) => tick.text());
    const orderTicks = wrapper.findAll('[data-testid="right-axis-tick"]').map((tick) => tick.text());
    expect(salesTicks).toContain('2,500');
    expect(salesTicks).toContain('0');
    expect(orderTicks).toContain('4');
    expect(orderTicks).toContain('0');
  });

  it('uses whole-number tick intervals for order counts', () => {
    const wrapper = mount(SalesTrendPanel, {
      props: {
        points: [{ date: '2026-09-01', month: null, salesAmount: 100, orderCount: 12 }]
      }
    });

    const orderTicks = wrapper.findAll('[data-testid="right-axis-tick"]').map((tick) => tick.text());
    expect(orderTicks).toContain('12');
    expect(orderTicks.every((tick) => /^\d+$/.test(tick))).toBe(true);
  });

  it('describes the svg and exposes every bucket in an assistive data table', () => {
    const wrapper = mount(SalesTrendPanel, { props: { points: validPoints } });
    const svg = wrapper.get('[data-testid="sales-trend-svg"]');

    expect(svg.attributes('aria-labelledby')).toBeTruthy();
    expect(svg.get('title').text()).toBe('销售额（CNY）与订单数（单）趋势图');
    expect(svg.get('desc').text()).toContain('共 2 个时间桶');
    expect(svg.attributes('preserveAspectRatio')).toBe('xMidYMid meet');
    expect(wrapper.get('[data-testid="sales-trend-chart"]').classes()).not.toContain('min-h-[240px]');

    const table = wrapper.get('[data-testid="sales-trend-data-table"]');
    expect(table.classes()).toContain('sr-only');
    expect(table.get('caption').text()).toBe('销售与订单趋势数据');
    expect(table.findAll('thead th').map((cell) => cell.text())).toEqual(['日期/月', '销售额（CNY）', '订单数（单）']);
    const rows = table.findAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('2026-08-31');
    expect(rows[0].text()).toContain('¥1,250.50');
    expect(rows[0].text()).toContain('2');
    expect(rows[1].text()).toContain('2026-09-01');
    expect(rows[1].text()).toContain('¥2,500.00');
    expect(rows[1].text()).toContain('4');
  });

  it('keeps invalid values missing, breaks the order line, and preserves legitimate zeroes', () => {
    const points = [
      { date: '2026-09-01', month: null, salesAmount: 100, orderCount: 1 },
      { date: '2026-09-02', month: null, salesAmount: null, orderCount: Number.NaN },
      { date: '2026-09-03', month: null, salesAmount: -5, orderCount: Number.POSITIVE_INFINITY },
      { date: '2026-09-04', month: null, salesAmount: Number.POSITIVE_INFINITY, orderCount: -2 },
      { date: '2026-09-05', month: null, salesAmount: 0, orderCount: 0 },
      { date: '2026-09-06', month: null, salesAmount: 200, orderCount: 3 }
    ] as unknown as DashboardSalesTrendPoint[];
    const wrapper = mount(SalesTrendPanel, { props: { points } });

    expect(wrapper.findAll('[data-testid="sales-bar"]')).toHaveLength(2);
    const orderPoints = wrapper.findAll('[data-testid="order-point"]');
    expect(orderPoints).toHaveLength(3);
    const orderLines = wrapper.findAll('[data-testid="order-line"]');
    expect(orderLines).toHaveLength(1);
    expect(orderLines[0].attributes('d')).not.toContain(orderPoints[0].attributes('cx'));
    expect(orderLines[0].attributes('d')).toContain(orderPoints[1].attributes('cx'));
    expect(orderLines[0].attributes('d')).toContain(orderPoints[2].attributes('cx'));

    const rows = wrapper.get('[data-testid="sales-trend-data-table"]').findAll('tbody tr');
    expect(rows[1].findAll('td')[0].text()).toBe('--');
    expect(rows[1].findAll('td')[1].text()).toBe('--');
    expect(rows[2].findAll('td')[0].text()).toBe('--');
    expect(rows[2].findAll('td')[1].text()).toBe('--');
    expect(rows[3].findAll('td')[0].text()).toBe('--');
    expect(rows[3].findAll('td')[1].text()).toBe('--');
    expect(rows[4].findAll('td')[0].text()).toBe('¥0.00');
    expect(rows[4].findAll('td')[1].text()).toBe('0');
  });
});
