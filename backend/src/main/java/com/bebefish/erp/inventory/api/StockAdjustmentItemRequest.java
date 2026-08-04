package com.bebefish.erp.inventory.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StockAdjustmentItemRequest(
        @NotNull Long skuId,
        @NotNull BigDecimal quantityDelta
) {
}
