package com.bebefish.erp.inventory.application;

import java.math.BigDecimal;

public record InventoryBalanceView(
        long warehouseId,
        long skuId,
        String skuCode,
        String barcode,
        String itemNo,
        String productName,
        String skuName,
        String specification,
        BigDecimal quantity
) {
}
