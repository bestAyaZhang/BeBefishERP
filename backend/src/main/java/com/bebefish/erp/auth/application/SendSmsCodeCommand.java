package com.bebefish.erp.auth.application;

import jakarta.validation.constraints.NotBlank;

public record SendSmsCodeCommand(@NotBlank String mobile) {
    public String normalizedMobile() {
        return mobile == null ? "" : mobile.trim();
    }
}
