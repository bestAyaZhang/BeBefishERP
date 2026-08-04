import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../types/auth';
import { httpSalesOrderService } from './httpSalesOrderService';

describe('http sales order service', () => {
  beforeEach(() => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'sales-token');
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response(
      JSON.stringify({ code: 'SUCCESS', message: '操作成功', data: { records: [], page: 1, pageSize: 20, total: 0 } }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    ))));
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('uses the sales-orders draft and list endpoints', async () => {
    await httpSalesOrderService.saveDraft({
      customerId: 1, customerName: '客户', warehouseId: 2, warehouseName: '仓库',
      orderDate: '2026-07-19', transportMethod: 'delivery', settlementCycle: 'monthly',
      freight: 0, totalAmount: 10, receivedAmount: 0, receivableAmount: 10, remark: '',
      lines: [{ skuId: 3, quantity: 1, unitPrice: 10, amount: 10 }]
    });
    await httpSalesOrderService.listOrders?.({ page: 1, size: 20, status: 'draft' });

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/sales-orders', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/sales-orders?page=1&size=20&status=draft', expect.anything());
  });

  it('uses standalone detail and draft deletion endpoints', async () => {
    await httpSalesOrderService.getOrder?.(99);
    await httpSalesOrderService.deleteOrder?.(99);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/sales-orders/99', expect.anything());
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/sales-orders/99', expect.objectContaining({ method: 'DELETE' }));
  });

  it('uses the void endpoint with an explicit reason', async () => {
    await httpSalesOrderService.voidOrder?.(99, '客户取消订单');

    expect(fetch).toHaveBeenCalledWith('/api/sales-orders/99/void', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ reason: '客户取消订单' })
    }));
  });

  it('creates a draft before confirming it through the confirmation endpoint', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 'SUCCESS', message: '操作成功', data: { id: 77, orderNo: 'SO202607190001', status: 'draft' } }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      ))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 'SUCCESS', message: '操作成功', data: { orderNo: 'SO202607190001', status: 'confirmed' } }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )));

    const result = await httpSalesOrderService.confirmOrder({
      customerId: 1, customerName: '客户', warehouseId: 2, warehouseName: '仓库',
      orderDate: '2026-07-19', transportMethod: 'delivery', settlementCycle: 'monthly',
      freight: 0, totalAmount: 10, receivedAmount: 0, receivableAmount: 10, remark: '',
      lines: [{ skuId: 3, quantity: 1, unitPrice: 10, amount: 10 }]
    });

    expect(result).toEqual({ orderNo: 'SO202607190001', status: 'confirmed' });
    expect(fetch).toHaveBeenNthCalledWith(1, '/api/sales-orders', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/sales-orders/77/confirm', expect.objectContaining({ method: 'POST' }));
  });
});
