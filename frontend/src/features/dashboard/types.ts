export type DashboardPeriod = 'week' | 'month' | 'year';

export interface DashboardSummary {
  productCount: number;
  enabledProductCount: number;
  skuCount: number;
  enabledSupplierCount: number;
  zeroStockSkuCount: number;
  lowStockSkuCount: number;
  orderCount: number;
  salesAmount: number;
  outstandingAmount: number;
  draftOrderCount: number;
}

export interface DashboardSalesTrendPoint {
  date: string | null;
  month: string | null;
  salesAmount: number;
  orderCount: number;
}

export interface DashboardStockAlert {
  productId: number;
  productName: string;
  skuId: number;
  skuCode: string;
  skuName: string;
  stockQuantity: number;
  safetyStockQuantity: number;
  shortageQuantity: number;
}

export type DashboardOrderStatus = 'draft' | 'confirmed' | 'void';

export interface DashboardRecentOrder {
  orderNo: string;
  customer: string;
  amount: number;
  status: DashboardOrderStatus;
  businessDate: string;
}

export interface DashboardOverview {
  summary: DashboardSummary;
  salesTrend: DashboardSalesTrendPoint[];
  stockAlerts: DashboardStockAlert[];
  recentOrders: DashboardRecentOrder[];
}

export interface DashboardService {
  getOverview(period: DashboardPeriod): Promise<DashboardOverview>;
}
