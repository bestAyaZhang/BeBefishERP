package com.bebefish.erp.sales.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOrder(
        Long id,
        String salesNo,
        Long customerId,
        String customerName,
        Long warehouseId,
        String warehouseName,
        LocalDate salesDate,
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
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SalesOrderItem> items
) {
    public SalesOrder {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public SalesOrder withIdentity(Long orderId, LocalDateTime created, LocalDateTime updated) {
        return new SalesOrder(
                orderId, salesNo, customerId, customerName, warehouseId, warehouseName, salesDate,
                salespersonMobile, status, transportMethod, settlementCycle, paymentMethod, deliveryAddress,
                logisticsCompany, trackingNo, packageNote, invoiceRequired, invoiceStatus, goodsAmount,
                discountAmount, shippingFee, totalAmount, receivedAmount, outstandingAmount, remark,
                created, updated, items
        );
    }
}
