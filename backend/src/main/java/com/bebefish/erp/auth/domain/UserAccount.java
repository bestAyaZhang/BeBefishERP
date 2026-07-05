package com.bebefish.erp.auth.domain;

import java.util.List;

public record UserAccount(
        String mobile,
        String passwordHash,
        boolean enabled,
        boolean employeeActive,
        List<String> roles,
        List<String> permissions
) {
}
