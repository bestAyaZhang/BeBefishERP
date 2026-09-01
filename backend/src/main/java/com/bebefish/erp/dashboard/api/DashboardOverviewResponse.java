package com.bebefish.erp.dashboard.api;

import com.bebefish.erp.dashboard.application.DashboardOverview;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardOverviewResponse(
        SummaryResponse summary,
        List<SalesTrendPointResponse> salesTrend,
        List<StockAlertResponse> stockAlerts,
        List<RecentOrderResponse> recentOrders
) {
    static DashboardOverviewResponse from(DashboardOverview overview) {
        return new DashboardOverviewResponse(
                SummaryResponse.from(overview.summary()),
                overview.salesTrend().stream().map(SalesTrendPointResponse::from).toList(),
                overview.stockAlerts().stream().map(StockAlertResponse::from).toList(),
                overview.recentOrders().stream().map(RecentOrderResponse::from).toList()
        );
    }

    public record SummaryResponse(
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
    ) {
        private static SummaryResponse from(DashboardOverview.Summary summary) {
            return new SummaryResponse(
                    summary.productCount(), summary.enabledProductCount(), summary.skuCount(),
                    summary.enabledSupplierCount(), summary.zeroStockSkuCount(), summary.lowStockSkuCount(),
                    summary.orderCount(), summary.salesAmount(), summary.outstandingAmount(),
                    summary.draftOrderCount()
            );
        }
    }

    public record SalesTrendPointResponse(
            String date,
            String month,
            BigDecimal salesAmount,
            long orderCount
    ) {
        private static SalesTrendPointResponse from(DashboardOverview.SalesTrendPoint point) {
            return new SalesTrendPointResponse(
                    point.date(), point.month(), point.salesAmount(), point.orderCount()
            );
        }
    }

    public record StockAlertResponse(
            long productId,
            String productName,
            long skuId,
            String skuCode,
            String skuName,
            BigDecimal stockQuantity,
            BigDecimal safetyStockQuantity,
            BigDecimal shortageQuantity
    ) {
        private static StockAlertResponse from(DashboardOverview.StockAlert alert) {
            return new StockAlertResponse(
                    alert.productId(), alert.productName(), alert.skuId(), alert.skuCode(), alert.skuName(),
                    alert.stockQuantity(), alert.safetyStockQuantity(), alert.shortageQuantity()
            );
        }
    }

    public record RecentOrderResponse(
            String orderNo,
            String customer,
            BigDecimal amount,
            String status,
            LocalDate businessDate
    ) {
        private static RecentOrderResponse from(DashboardOverview.RecentOrder order) {
            return new RecentOrderResponse(
                    order.orderNo(), order.customer(), order.amount(), order.status(), order.businessDate()
            );
        }
    }
}
