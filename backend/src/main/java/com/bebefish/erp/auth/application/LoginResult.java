package com.bebefish.erp.auth.application;

import java.util.List;

public record LoginResult(
        String accessToken,
        long employeeId,
        String mobile,
        String displayName,
        String avatarUrl,
        List<String> roles,
        List<String> permissions,
        String loginMethod,
        List<String> warnings
) {
    public LoginResult(
            String accessToken,
            String mobile,
            List<String> roles,
            List<String> permissions,
            String loginMethod
    ) {
        this(accessToken, 0, mobile, mobile, null, roles, permissions, loginMethod, List.of());
    }

    public LoginResult {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
        warnings = List.copyOf(warnings);
    }
}
