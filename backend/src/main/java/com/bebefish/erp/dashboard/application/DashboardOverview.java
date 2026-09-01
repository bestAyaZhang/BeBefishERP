package com.bebefish.erp.dashboard.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardOverview(
        Summary summary,
        List<SalesTrendPoint> salesTrend,
        List<StockAlert> stockAlerts,
        List<RecentOrder> recentOrders
) {
    public DashboardOverview {
        salesTrend = List.copyOf(salesTrend);
        stockAlerts = List.copyOf(stockAlerts);
        recentOrders = List.copyOf(recentOrders);
    }

    public record Summary(
            long productCount,
            long enabledProductCount,
            long skuCount,
            long enabledSupplierCount,
            long zeroStockSkuCount,
            long lowStockSkuCount,
            long orderCount,
            BigDecimal salesAmount,
            BigDecimal outstandingAmount,
            long draftOrderCount
    ) {}

    public record SalesTrendPoint(
            String date,
            String month,
            BigDecimal salesAmount,
            long orderCount
    ) {}

    public record StockAlert(
            long productId,
            String productName,
            long skuId,
            String skuCode,
            String skuName,
            BigDecimal stockQuantity,
            BigDecimal safetyStockQuantity,
            BigDecimal shortageQuantity
    ) {}

    public record RecentOrder(
            String orderNo,
            String customer,
            BigDecimal amount,
            String status,
            LocalDate businessDate
    ) {}
}
