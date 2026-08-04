package com.bebefish.erp.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryLedgerView(
        long id,
        long warehouseId,
        long skuId,
        String direction,
        BigDecimal quantity,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        String sourceType,
        long sourceId,
        String sourceNo,
        LocalDateTime occurredAt,
        String operatorMobile
) {
}
