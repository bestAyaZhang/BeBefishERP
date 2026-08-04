package com.bebefish.erp.sales.api;

import com.bebefish.erp.sales.domain.SalesOrder;
import com.bebefish.erp.sales.domain.SalesOrderItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String orderNo,
        Long customerId,
        String customerName,
        Long warehouseId,
        String warehouseName,
        LocalDate orderDate,
        String salespersonMobile,
        String status,
        String transportMethod,
        String settlementCycle,
        String paymentMethod,
        String deliveryAddress,
        String logisticsCompany,
        String trackingNo,
        String packageNote,
        boolean invoiceRequired,
        String invoiceStatus,
        BigDecimal goodsAmount,
        BigDecimal discountAmount,
        BigDecimal freight,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ItemResponse> items
) {
    public static SalesOrderResponse from(SalesOrder order) {
        return new SalesOrderResponse(
                order.id(), order.salesNo(), order.customerId(), order.customerName(), order.warehouseId(),
                order.warehouseName(), order.salesDate(), order.salespersonMobile(), order.status(),
                order.transportMethod(), order.settlementCycle(), order.paymentMethod(), order.deliveryAddress(),
                order.logisticsCompany(), order.trackingNo(), order.packageNote(), order.invoiceRequired(),
                order.invoiceStatus(), order.goodsAmount(), order.discountAmount(), order.shippingFee(),
                order.totalAmount(), order.receivedAmount(), order.outstandingAmount(), order.remark(),
                order.createdAt(), order.updatedAt(), order.items().stream().map(ItemResponse::from).toList()
        );
    }

    public record ItemResponse(
            Long id,
            Long skuId,
            BigDecimal quantity,
            BigDecimal defaultUnitPrice,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal amount,
            BigDecimal standardCostSnapshot,
            String itemNoSnapshot,
            String productNameSnapshot,
            String skuCodeSnapshot,
            String skuNameSnapshot,
            String specificationSnapshot,
            String packagingSnapshot,
            Integer cartonQuantitySnapshot,
            String barcodeSnapshot,
            String salesUnitSnapshot
    ) {
        static ItemResponse from(SalesOrderItem item) {
            return new ItemResponse(
                    item.id(), item.skuId(), item.quantity(), item.defaultUnitPrice(), item.unitPrice(),
                    item.discountRate(), item.amount(), item.standardCostSnapshot(), item.itemNoSnapshot(),
                    item.productNameSnapshot(), item.skuCodeSnapshot(), item.skuNameSnapshot(),
                    item.specificationSnapshot(), item.packagingSnapshot(), item.cartonQuantitySnapshot(),
                    item.barcodeSnapshot(), item.salesUnitSnapshot()
            );
        }
    }
}
