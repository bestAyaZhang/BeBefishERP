package com.bebefish.erp.auth.domain;

public record UserAccount(
        String mobile,
        String passwordHash,
        boolean enabled,
        boolean employeeActive
) {
}
