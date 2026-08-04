package com.bebefish.erp.inventory.domain;

import java.math.BigDecimal;

public record InventoryBalance(
        Long id,
        long warehouseId,
        long skuId,
        BigDecimal quantity,
        long versionNo
) {
}
