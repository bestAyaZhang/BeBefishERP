package com.bebefish.erp.auth.application;

import jakarta.validation.constraints.NotBlank;

public record SmsLoginCommand(
        @NotBlank String mobile,
        @NotBlank String smsCode
) {
    public String normalizedMobile() {
        return mobile == null ? "" : mobile.trim();
    }
}
