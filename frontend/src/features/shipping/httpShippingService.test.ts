import { afterEach, describe, expect, it, vi } from 'vitest';
import { httpShippingService } from './httpShippingService';
import { emptyShipmentForm } from './types';

afterEach(() => vi.unstubAllGlobals());

function ok(data: unknown) {
  return new Response(JSON.stringify({ code: 'SUCCESS', data }));
}

describe('shipping HTTP contract', () => {
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
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ form, status: 'partially_shipped', version: 3 });
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
