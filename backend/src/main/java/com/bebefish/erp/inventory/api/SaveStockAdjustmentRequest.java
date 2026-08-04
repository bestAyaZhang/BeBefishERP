package com.bebefish.erp.inventory.api;

import com.bebefish.erp.inventory.application.StockAdjustmentCommand;
import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveStockAdjustmentRequest(
        @NotNull Long warehouseId,
        @NotEmpty List<@Valid StockAdjustmentItemRequest> items,
        @NotBlank @Size(max = 200) String reason,
        @Size(max = 500) String remark
) {
    StockAdjustmentCommand toCommand() {
        return new StockAdjustmentCommand(
                warehouseId,
                items.stream().map(item -> new StockAdjustmentItem(null, item.skuId(), item.quantityDelta())).toList(),
                reason,
                remark
        );
    }
}
