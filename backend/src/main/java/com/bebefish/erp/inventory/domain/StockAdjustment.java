package com.bebefish.erp.inventory.domain;

import java.time.LocalDateTime;
import java.util.List;

public record StockAdjustment(
        Long id,
        String adjustmentNo,
        long warehouseId,
        String reason,
        String status,
        String remark,
        List<StockAdjustmentItem> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public StockAdjustment {
        items = List.copyOf(items);
    }
}
