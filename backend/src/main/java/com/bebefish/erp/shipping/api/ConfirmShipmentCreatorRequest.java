package com.bebefish.erp.shipping.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ConfirmShipmentCreatorRequest(@NotNull @Positive Long employeeId, @NotNull @PositiveOrZero Long version) {}
