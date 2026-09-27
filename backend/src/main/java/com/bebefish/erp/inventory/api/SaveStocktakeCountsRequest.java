package com.bebefish.erp.inventory.api;

import com.bebefish.erp.inventory.application.StocktakeViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record SaveStocktakeCountsRequest(@NotNull List<@Valid Count> counts) {
    public List<StocktakeViews.Count> toCounts() {
        return counts.stream().map(value -> new StocktakeViews.Count(value.itemId(), value.quantity())).toList();
    }

    public record Count(
            @Positive long itemId,
            @NotNull @PositiveOrZero BigDecimal quantity
    ) {
    }
}
