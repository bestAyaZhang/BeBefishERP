package com.bebefish.erp.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateStocktakeRequest(
        @Positive long warehouseId,
        boolean blindCount,
        List<@NotBlank String> palletIds
) {
}
