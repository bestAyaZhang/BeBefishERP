package com.bebefish.erp.auth.api;

import jakarta.validation.constraints.NotBlank;

public record FeishuExchangeRequest(@NotBlank String ticket) {
}
