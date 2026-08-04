package com.bebefish.erp.inventory.application;

import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import java.util.List;

public record StockAdjustmentCommand(
        long warehouseId,
        List<StockAdjustmentItem> items,
        String reason,
        String remark
) {
}
