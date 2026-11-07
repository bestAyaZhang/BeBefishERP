import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import ReplenishmentSettings from './ReplenishmentSettings.vue';
import type { ReplenishmentRow } from './replenishmentModel';

const row = {
  skuId: 1,
  sku: 'BF-GC-350',
  name: '竖纹玻璃杯',
  warehouses: [{ id: 9, name: '杭州主仓' }],
  warehouseId: null,
  stock: 168,
  historyDays: 30,
  demandTotal: 720,
  windowStart: '2026-10-09',
  windowEnd: '2026-11-07',
  average: 24,
  safety: 120,
  point: 312,
  source: 'auto',
  status: 'warning',
  config: { mode: 'auto', bufferDays: 5, leadDays: 8, manualSafety: null, manualPoint: null }
} satisfies ReplenishmentRow;

describe('ReplenishmentSettings', () => {
  let wrapper: VueWrapper | undefined;
  const browserDialog = window.HTMLDialogElement;

  beforeEach(() => {
    Object.defineProperties(browserDialog.prototype, {
      showModal: { configurable: true, value(this: HTMLDialogElement) { this.open = true; } },
      close: { configurable: true, value(this: HTMLDialogElement) { this.open = false; } }
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    Reflect.deleteProperty(browserDialog.prototype, 'showModal');
    Reflect.deleteProperty(browserDialog.prototype, 'close');
  });

  it('preserves the draft through a save failure and prevents dismissal during a save', async () => {
    const drawer = mount(ReplenishmentSettings, { props: { row } });
    wrapper = drawer;
    await drawer.get('[data-stock-field=bufferDays]').setValue('7');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toEqual([[{ mode: 'auto', bufferDays: 7, leadDays: 8, manualSafety: null, manualPoint: null }]]);
    expect(drawer.get('dialog').element.open).toBe(true);

    await drawer.setProps({ saving: true });
    await drawer.get('form').trigger('submit');
    await drawer.get('dialog').trigger('cancel');
    expect(drawer.emitted('save')).toHaveLength(1);
    expect(drawer.emitted('close')).toBeUndefined();

    await drawer.setProps({ saving: false, saveError: '保存失败，请重试' });
    expect(drawer.get('[role=alert]').text()).toContain('保存失败');
    expect(drawer.get<HTMLInputElement>('[data-stock-field=bufferDays]').element.value).toBe('7');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toHaveLength(2);
  });

  it('saves zero as a fallback threshold and rejects a partial pair', async () => {
    const drawer = mount(ReplenishmentSettings, { props: { row: { ...row, historyDays: 10 } } });
    wrapper = drawer;
    await drawer.get('[data-stock-field=manualSafety]').setValue('0');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toBeUndefined();

    await drawer.get('[data-stock-field=manualPoint]').setValue('0');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toEqual([[{ mode: 'auto', bufferDays: 5, leadDays: 8, manualSafety: 0, manualPoint: 0 }]]);
  });

  it('starts a batch with common defaults and applies a fallback only when both thresholds are entered', async () => {
    const drawer = mount(ReplenishmentSettings, {
      props: {
        row: { ...row, config: { mode: 'manual', bufferDays: 12, leadDays: 18, manualSafety: 100, manualPoint: 200 } },
        bulkCount: 3
      }
    });
    wrapper = drawer;
    expect(drawer.text()).toContain('3 条库存记录');
    expect(drawer.text()).not.toContain(row.sku);
    expect(drawer.find('[data-stock-result]').exists()).toBe(false);

    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toEqual([[{ mode: 'auto', bufferDays: 5, leadDays: 8, manualSafety: null, manualPoint: null }]]);

    await drawer.get('[data-stock-field=manualSafety]').setValue('15');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toHaveLength(1);

    await drawer.get('[data-stock-field=manualPoint]').setValue('35');
    await drawer.get('form').trigger('submit');
    expect(drawer.emitted('save')).toHaveLength(2);
    expect(drawer.emitted('save')?.[1]).toEqual([{ mode: 'auto', bufferDays: 5, leadDays: 8, manualSafety: 15, manualPoint: 35 }]);
  });
});
