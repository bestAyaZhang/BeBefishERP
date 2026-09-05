package com.bebefish.erp.common.security;

import java.util.List;

public record ErpPrincipal(
        long employeeId,
        String mobile,
        String displayName,
        List<String> roles,
        List<String> permissions
) {
    public ErpPrincipal {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    public String operatorIdentifier() {
        if (mobile != null && !mobile.isBlank()) {
            return mobile;
        }
        if (employeeId > 0) {
            return "employee:" + employeeId;
        }
        throw new IllegalStateException("Authenticated principal has no stable operator identifier");
    }
}
