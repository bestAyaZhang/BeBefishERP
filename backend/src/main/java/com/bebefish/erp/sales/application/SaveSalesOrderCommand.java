package com.bebefish.erp.sales.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaveSalesOrderCommand(
        Long customerId,
        Long warehouseId,
        LocalDate salesDate,
        String transportMethod,
        String settlementCycle,
        String paymentMethod,
        String deliveryAddress,
        String logisticsCompany,
        String trackingNo,
        String packageNote,
        String remark,
        boolean invoiceRequired,
        String invoiceStatus,
        BigDecimal shippingFee,
        BigDecimal receivedAmount,
        List<SaveSalesOrderLineCommand> lines
) {
    public SaveSalesOrderCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
