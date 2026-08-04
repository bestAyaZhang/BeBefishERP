package com.bebefish.erp.inventory.api;

import jakarta.validation.constraints.Size;

public record VoidStockAdjustmentRequest(@Size(max = 500) String reason) {
}
