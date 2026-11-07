import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearMessages } from '../../../components/feedback/message';
import { currentUser } from '../../../services/authSession';
import type { LoginResult } from '../../../types/auth';
import ReplenishmentAlerts from './ReplenishmentAlerts.vue';
import type { ReplenishmentConfig, ReplenishmentPage, ReplenishmentQuery, ReplenishmentRow } from './replenishmentModel';

const { list, saveBatch } = vi.hoisted(() => ({ list: vi.fn(), saveBatch: vi.fn() }));
vi.mock('./replenishmentService', () => ({ replenishmentService: { list, saveBatch } }));

const config: ReplenishmentConfig = { mode: 'manual', bufferDays: 0, leadDays: 0, manualSafety: 5, manualPoint: 15 };
const warehouse = { id: 8, name: '华东仓' };
const baseRow: ReplenishmentRow = {
  skuId: 11, sku: 'SKU-11', name: '玻璃杯', warehouses: [warehouse], warehouseId: null,
  stock: 10, historyDays: 30, demandTotal: 60, windowStart: '2026-10-09', windowEnd: '2026-11-07',
  config, average: 2, safety: 5, point: 15, source: 'manual', status: 'warning'
};
const independentRow = { ...baseRow, warehouseId: warehouse.id };
const Settings = defineComponent({
  props: { bulkCount: Number, saveError: String },
  emits: ['save'],
  setup(props, { emit }) {
    return () => h('div', [
      h('span', `批量配置 ${props.bulkCount} 条`),
      h('button', { onClick: () => emit('save', config) }, '确认测试设置'),
      h('span', props.saveError)
    ]);
  }
});

function result(query: ReplenishmentQuery, items: ReplenishmentRow[], total = 41): ReplenishmentPage {
  return {
    items, page: query.page, size: query.size, total, warehouses: [warehouse],
    counts: { critical: 0, warning: total, normal: 0, pending: 0 }
  };
}

function setup() {
  return mount(ReplenishmentAlerts, { global: { stubs: { ReplenishmentSettings: Settings } } });
}

describe('replenishment list selection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser.value = { permissions: ['inventory:edit'] } as LoginResult;
    list.mockImplementation(async (query: ReplenishmentQuery) => result(
      query, query.size === 50 ? [baseRow, independentRow] : query.page === 1 ? [baseRow] : [independentRow]
    ));
  });
  afterEach(() => { currentUser.value = null; clearMessages(); });

  it('saves distinct shared and independent scopes selected on different pages after page size changes', async () => {
    saveBatch.mockResolvedValue(undefined);
    const wrapper = setup();
    await flushPromises();
    await wrapper.get('input[aria-label="全选本页"]').setValue(true);
    await wrapper.get('button[aria-label="第 2 页"]').trigger('click');
    await flushPromises();
    await wrapper.get('input[data-selection-key="11:8"]').setValue(true);
    await wrapper.get('select[aria-label="每页条数"]').setValue('50');
    await flushPromises();
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 50 }));
    expect((wrapper.get('input[aria-label="全选本页"]').element as HTMLInputElement).checked).toBe(true);
    expect(wrapper.find('select[aria-label="每页条数"]').exists()).toBe(true);

    await wrapper.get('[data-testid="replenishment-bulk-settings"]').trigger('click');
    expect(wrapper.text()).toContain('批量配置 2 条');
    await wrapper.findAll('button').find((button) => button.text() === '确认测试设置')!.trigger('click');
    await flushPromises();

    expect(saveBatch).toHaveBeenCalledWith([{ skuId: 11, warehouseId: null }, { skuId: 11, warehouseId: 8 }], config);
    expect(wrapper.text()).toContain('已选 0 条');
    wrapper.unmount();
  });

  it('clears the selection when a warehouse filter changes', async () => {
    const wrapper = setup();
    await flushPromises();
    await wrapper.get('input[data-selection-key="11:shared"]').setValue(true);
    expect(wrapper.text()).toContain('已选 1 条');
    await wrapper.get('select[aria-label="仓库"]').setValue('8');
    await flushPromises();

    expect(wrapper.text()).toContain('已选 0 条');
    expect((wrapper.get('[data-testid="replenishment-bulk-settings"]').element as HTMLButtonElement).disabled).toBe(true);
    wrapper.unmount();
  });

  it('rejects selecting the whole page when only some rows fit under the batch limit', async () => {
    const rows = Array.from({ length: 102 }, (_, index) => ({ ...baseRow, skuId: index + 1, sku: `SKU-${index + 1}` }));
    list.mockImplementation(async (query: ReplenishmentQuery) => result(
      query, rows.slice((query.page - 1) * query.size, query.page * query.size), rows.length
    ));
    const wrapper = setup();
    await flushPromises();
    await wrapper.get('select[aria-label="每页条数"]').setValue('100');
    await flushPromises();
    await wrapper.get('input[aria-label="全选本页"]').setValue(true);
    await wrapper.get('input[data-selection-key="1:shared"]').setValue(false);
    await wrapper.get('button[aria-label="第 2 页"]').trigger('click');
    await flushPromises();
    await wrapper.get('input[aria-label="全选本页"]').setValue(true);

    expect(wrapper.get('[role="alert"]').text()).toContain('每次最多选择 100 条');
    expect(wrapper.text()).toContain('已选 99 条');
    expect(wrapper.findAll('input[data-selection-key]').map((input) => (input.element as HTMLInputElement).checked))
      .toEqual([false, false]);
    wrapper.unmount();
  });

  it('keeps the batch and selection open when the server rejects saving', async () => {
    saveBatch.mockRejectedValue(new Error('仓库范围已经变化'));
    const wrapper = setup();
    await flushPromises();
    await wrapper.get('input[aria-label="全选本页"]').setValue(true);
    await wrapper.get('[data-testid="replenishment-bulk-settings"]').trigger('click');
    await wrapper.findAll('button').find((button) => button.text() === '确认测试设置')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('仓库范围已经变化');
    expect(wrapper.text()).toContain('批量配置 1 条');
    expect(wrapper.text()).toContain('已选 1 条');
    expect(list).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
});
