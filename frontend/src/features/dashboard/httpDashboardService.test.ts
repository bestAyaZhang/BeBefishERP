import { afterEach, describe, expect, it, vi } from 'vitest';
import { httpDashboardService } from './httpDashboardService';

const overview = {
  summary: {
    productCount: 24,
    enabledProductCount: 21,
    skuCount: 38,
    enabledSupplierCount: 6,
    zeroStockSkuCount: 2,
    lowStockSkuCount: 5,
    orderCount: 12,
    salesAmount: 18990.5,
    outstandingAmount: 2300,
    draftOrderCount: 3
  },
  salesTrend: [{ date: '2026-09-01', month: null, salesAmount: 18990.5, orderCount: 12 }],
  stockAlerts: [{
    productId: 11,
    productName: '高脚玻璃杯',
    skuId: 101,
    skuCode: 'SKU-101',
    skuName: '透明款',
    stockQuantity: 4,
    safetyStockQuantity: 12,
    shortageQuantity: 8
  }],
  recentOrders: [{
    orderNo: 'SO202609010001',
    customer: '星海贸易',
    amount: 299,
    status: 'confirmed',
    businessDate: '2026-09-01'
  }]
};

function mockFetchApi(data: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
    JSON.stringify({ code: 'SUCCESS', message: '操作成功', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )));
}

describe('http dashboard service', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('requests and unwraps dashboard overview for the selected period', async () => {
    mockFetchApi(overview);

    const result = await httpDashboardService.getOverview('month');

    expect(result).toEqual(overview);
    expect(fetch).toHaveBeenCalledWith('/api/dashboard/overview?period=month', expect.any(Object));
  });
});
