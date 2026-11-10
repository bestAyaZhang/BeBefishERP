import { afterEach, describe, expect, it, vi } from 'vitest';
import { httpShippingService } from './httpShippingService';
import { emptyShipmentForm } from './types';

afterEach(() => vi.unstubAllGlobals());

function ok(data: unknown) {
  return new Response(JSON.stringify({ code: 'SUCCESS', data }));
}

describe('shipping HTTP contract', () => {
  it('sends the exact logistics company with combined filters and omits an empty company', async () => {
    const fetcher = vi.fn().mockImplementation(async () => ok({ records: [], total: 0 })); vi.stubGlobal('fetch', fetcher);
    await httpShippingService.list({ page: 2, size: 20, logisticsCompany: ' 壹米滴答-汇森速运 ', status: 'unfinished',
      dateFrom: '2026-11-01', platformId: 1, shopId: 2 });
    const params = new URL(fetcher.mock.calls[0][0], 'http://localhost').searchParams;
    expect(params.get('logisticsCompany')).toBe('壹米滴答-汇森速运');
    expect(params.get('page')).toBe('2');
    expect(params.get('status')).toBe('unfinished');
    expect(params.get('dateFrom')).toBe('2026-11-01');
    expect(params.get('platformId')).toBe('1');
    expect(params.get('shopId')).toBe('2');
    await httpShippingService.list({ page: 1, size: 20, logisticsCompany: '   ' });
    expect(new URL(fetcher.mock.calls[1][0], 'http://localhost').searchParams.has('logisticsCompany')).toBe(false);
  });

  it('requests every export record using the same filters without pagination', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok([{ id: 41 }]));
    vi.stubGlobal('fetch', fetcher);
    await expect(httpShippingService.listForExport({ keyword: ' 水族箱 ', logisticsCompany: ' 安能物流 ',
      dateFrom: '2026-11-01', dateTo: '2026-11-10', platformId: 2, shopId: 4, incompleteOnly: true }))
      .resolves.toEqual([{ id: 41 }]);
    const url = new URL(fetcher.mock.calls[0][0], 'http://localhost');
    expect(url.pathname).toBe('/api/shipments/export');
    expect(Object.fromEntries(url.searchParams)).toEqual({ keyword: '水族箱', logisticsCompany: '安能物流',
      dateFrom: '2026-11-01', dateTo: '2026-11-10', platformId: '2', shopId: '4', incompleteOnly: 'true' });
  });

  it('reads logistics trace through the dedicated GET route',async()=>{
    const trace={status:'empty',message:'无轨迹信息',events:[]};
    const fetcher=vi.fn().mockResolvedValue(ok(trace));vi.stubGlobal('fetch',fetcher);
    await expect(httpShippingService.getLogisticsTrace(8)).resolves.toEqual(trace);
    expect(fetcher.mock.calls[0][0]).toBe('/api/shipments/8/logistics-trace');
    expect(fetcher.mock.calls[0][1].method??'GET').toBe('GET');
    expect(fetcher.mock.calls[0][1].body).toBeUndefined();
  });
  it('keeps the chosen quote ID outside form and sends no quote price as an authorization', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({})); vi.stubGlobal('fetch', fetcher);
    const fields = { ...emptyShipmentForm(), logisticsChoice: { quoteId: 'chosen-quote', preferredCompanyId: 7, clear: false } };
    await httpShippingService.create(fields);
    const payload = JSON.parse(fetcher.mock.calls[0][1].body);
    expect(payload.logisticsChoice).toEqual(fields.logisticsChoice);
    expect(payload.form).not.toHaveProperty('logisticsChoice');
    expect(payload.logisticsChoice).not.toHaveProperty('amount');
  });
  it('patches only preparation items, remark and the saved version', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetcher);
    await httpShippingService.updatePreparationContent(8, { preparationContent: '商品3件', remark: '', version: 3 });
    expect(fetcher).toHaveBeenCalledWith('/api/shipments/8/preparation-content', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ preparationContent: '商品3件', remark: '', version: 3 })
    }));
  });
  it('writes preparation feedback through a dedicated route without sending the ANE draft', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetcher);
    await httpShippingService.updatePreparation(8, { status: 'completed', actualWeight: 12.345, version: 3 });
    expect(fetcher).toHaveBeenCalledWith('/api/shipments/8/preparation', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ status: 'completed', actualWeight: 12.345, version: 3 })
    }));
  });
  it('cancels through the dedicated route with only the saved version', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({ state: 'cancelled' }));
    vi.stubGlobal('fetch', fetcher);
    await httpShippingService.cancelLogisticsOrder(8, { version: 3 });
    expect(fetcher).toHaveBeenCalledWith('/api/shipments/8/logistics-order/cancel', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ version: 3 })
    }));
  });
  it('loads summary and form options from the dedicated routes', async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(ok({})));
    vi.stubGlobal('fetch', fetcher);
    await httpShippingService.summary('2026-09-24');
    await httpShippingService.formOptions();
    expect(fetcher.mock.calls.map(call => [call[0]])).toContainEqual(['/api/shipments/summary?date=2026-09-24']);
    expect(fetcher.mock.calls.map(call => [call[0]])).toContainEqual(['/api/shipments/form-options']);
  });

  it('sends the structured form and explicit status/version on update', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetcher);
    const form = emptyShipmentForm();
    await httpShippingService.update(7, form, 'partially_shipped', 3);
    expect(fetcher.mock.calls[0][0]).toBe('/api/shipments/7');
    const { preparerEmployeeIds, ...fields } = form;
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ form: fields, preparerEmployeeIds, status: 'partially_shipped', version: 3 });
  });

  it('submits only the saved shipment version to one-click ordering', async () => {
    const fetcher = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetcher);
    await httpShippingService.placeLogisticsOrder(8, { version: 3 });
    expect(fetcher).toHaveBeenCalledWith('/api/shipments/8/logistics-order', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ version: 3 })
    }));
  });
});
