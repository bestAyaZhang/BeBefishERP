package com.bebefish.erp.shipping.logistics;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelAneOrderRequest(@NotNull @Min(0) Long version) {}
