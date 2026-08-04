package com.bebefish.erp.inventory.application;

import java.math.BigDecimal;

public record InventoryChange(long skuId, BigDecimal quantity) {
}
