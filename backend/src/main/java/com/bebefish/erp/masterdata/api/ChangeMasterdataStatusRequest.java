package com.bebefish.erp.masterdata.api;

import jakarta.validation.constraints.NotBlank;

public record ChangeMasterdataStatusRequest(@NotBlank String status) {}
