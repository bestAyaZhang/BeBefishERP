package com.bebefish.erp.authorization.domain;

public record PermissionDefinition(
        String code,
        String module,
        String action,
        String displayName
) {
}
