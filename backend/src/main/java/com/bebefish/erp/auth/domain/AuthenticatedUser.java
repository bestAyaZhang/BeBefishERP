package com.bebefish.erp.auth.domain;

import java.util.List;

public record AuthenticatedUser(
        String mobile,
        List<String> roles,
        List<String> permissions
) {
    public AuthenticatedUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
