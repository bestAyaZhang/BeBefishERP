import type { DashboardOverview, DashboardPeriod, DashboardService } from './types';

function trendPoint(period: DashboardPeriod): DashboardOverview['salesTrend'][number] {
  return period === 'year'
    ? { date: null, month: '2026-09', salesAmount: 18990.5, orderCount: 12 }
    : { date: '2026-09-01', month: null, salesAmount: 18990.5, orderCount: 12 };
}

export const mockDashboardService: DashboardService = {
  getOverview: async (period) => ({
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
    salesTrend: [trendPoint(period)],
    stockAlerts: [{
      productId: 11,
      productName: 'Sample glass',
      skuId: 101,
      skuCode: 'SKU-101',
      skuName: 'Clear',
      stockQuantity: 4,
      safetyStockQuantity: 12,
      shortageQuantity: 8
    }],
    recentOrders: [{
      orderNo: 'SO202609010001',
      customer: 'Sample customer',
      amount: 299,
      status: 'confirmed',
      businessDate: '2026-09-01'
    }]
  })
};
