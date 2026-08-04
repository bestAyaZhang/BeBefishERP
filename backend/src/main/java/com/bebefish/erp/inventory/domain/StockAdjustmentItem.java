package com.bebefish.erp.inventory.domain;

import java.math.BigDecimal;

public record StockAdjustmentItem(
        Long id,
        long skuId,
        BigDecimal quantityDelta
) {
}
