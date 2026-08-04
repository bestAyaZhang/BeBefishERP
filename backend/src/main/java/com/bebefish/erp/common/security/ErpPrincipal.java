package com.bebefish.erp.common.security;

import java.util.List;

public record ErpPrincipal(String mobile, List<String> roles, List<String> permissions) {
    public ErpPrincipal {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
