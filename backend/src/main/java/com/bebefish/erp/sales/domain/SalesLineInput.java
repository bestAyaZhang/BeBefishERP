package com.bebefish.erp.sales.domain;

import java.math.BigDecimal;

public record SalesLineInput(
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountRate
) {
}
