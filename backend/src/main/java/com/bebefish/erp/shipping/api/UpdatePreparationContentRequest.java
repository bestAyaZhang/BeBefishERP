package com.bebefish.erp.shipping.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePreparationContentRequest(
        @NotBlank(message = "请填写备货物品") @Size(max = 10000) String preparationContent,
        @Size(max = 5000) String remark,
        @NotNull @PositiveOrZero Long version
) {}
