package com.bebefish.erp.sales.domain;

import java.math.BigDecimal;

public record SalesAmountSummary(
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal goodsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount
) {
}
