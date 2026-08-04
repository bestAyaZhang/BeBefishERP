package com.bebefish.erp.sales.api;

import com.bebefish.erp.sales.application.SaveSalesOrderCommand;
import com.bebefish.erp.sales.application.SaveSalesOrderLineCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveSalesOrderRequest(
        Long customerId,
        Long warehouseId,
        LocalDate orderDate,
        String transportMethod,
        String settlementCycle,
        String paymentMethod,
        String deliveryAddress,
        String logisticsCompany,
        String trackingNo,
        String packageNote,
        String remark,
        Boolean invoiceRequired,
        String invoiceStatus,
        BigDecimal freight,
        BigDecimal receivedAmount,
        List<LineRequest> lines
) {
    public SaveSalesOrderCommand toCommand() {
        return new SaveSalesOrderCommand(
                customerId, warehouseId, orderDate, transportMethod, settlementCycle, paymentMethod,
                deliveryAddress, logisticsCompany, trackingNo, packageNote, remark,
                Boolean.TRUE.equals(invoiceRequired), invoiceStatus, freight == null ? BigDecimal.ZERO : freight,
                receivedAmount == null ? BigDecimal.ZERO : receivedAmount,
                lines == null ? List.of() : lines.stream().map(LineRequest::toCommand).toList()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineRequest(
            long skuId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountRate
    ) {
        SaveSalesOrderLineCommand toCommand() {
            return new SaveSalesOrderLineCommand(skuId, quantity, unitPrice, discountRate);
        }
    }
}
