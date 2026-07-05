package com.bebefish.erp.auth.application;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginCommand(
        @NotBlank String mobile,
        @NotBlank String password
) {
    public String normalizedMobile() {
        return mobile == null ? "" : mobile.trim();
    }
}
