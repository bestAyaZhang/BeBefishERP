package com.bebefish.erp.inventory.api;

import com.bebefish.erp.inventory.domain.StockAdjustment;
import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockAdjustmentResponse(
        Long id,
        String adjustmentNo,
        long warehouseId,
        String reason,
        String status,
        String remark,
        List<StockAdjustmentItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StockAdjustmentResponse from(StockAdjustment adjustment) {
        return new StockAdjustmentResponse(
                adjustment.id(), adjustment.adjustmentNo(), adjustment.warehouseId(), adjustment.reason(),
                adjustment.status(), adjustment.remark(),
                adjustment.items().stream().map(StockAdjustmentItemResponse::from).toList(),
                adjustment.createdAt(), adjustment.updatedAt()
        );
    }

    public record StockAdjustmentItemResponse(Long id, long skuId, BigDecimal quantityDelta) {
        static StockAdjustmentItemResponse from(StockAdjustmentItem item) {
            return new StockAdjustmentItemResponse(item.id(), item.skuId(), item.quantityDelta());
        }
    }
}
