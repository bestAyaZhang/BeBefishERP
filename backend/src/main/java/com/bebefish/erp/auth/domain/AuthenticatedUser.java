package com.bebefish.erp.auth.domain;

import java.util.List;

public record AuthenticatedUser(
        long userId,
        long employeeId,
        String mobile,
        String displayName,
        String avatarUrl,
        List<String> roles,
        List<String> permissions
) {
    public AuthenticatedUser(String mobile, List<String> roles, List<String> permissions) {
        this(0, 0, mobile, mobile, null, roles, permissions);
    }

    public AuthenticatedUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
