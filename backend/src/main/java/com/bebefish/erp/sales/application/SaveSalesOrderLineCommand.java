package com.bebefish.erp.sales.application;

import java.math.BigDecimal;

public record SaveSalesOrderLineCommand(
        long skuId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountRate
) {
}
