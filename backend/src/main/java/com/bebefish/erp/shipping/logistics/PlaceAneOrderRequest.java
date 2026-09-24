package com.bebefish.erp.shipping.logistics;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceAneOrderRequest(
        @NotNull @Min(0) Long version
) {}
