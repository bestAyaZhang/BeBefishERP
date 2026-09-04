package com.bebefish.erp.authorization.domain;

import java.util.Set;

public record Role(
        long id,
        String code,
        String name,
        boolean system,
        boolean superAdministrator,
        boolean sensitive,
        boolean enabled,
        DataScope dataScope,
        Set<String> permissionCodes,
        Set<String> memberKeys
) {
    public Role(
            String code,
            String name,
            boolean system,
            boolean superAdministrator,
            boolean enabled,
            DataScope dataScope,
            Set<String> permissionCodes,
            Set<String> memberKeys
    ) {
        this(0, code, name, system, superAdministrator, superAdministrator, enabled,
                dataScope, permissionCodes, memberKeys);
    }

    public Role {
        if (superAdministrator != "SUPER_ADMIN".equals(code)) {
            throw new IllegalArgumentException("Super administrator flag must match the reserved SUPER_ADMIN code");
        }
        if (superAdministrator && (!system || !enabled || dataScope != DataScope.COMPANY)) {
            throw new IllegalArgumentException("Super administrator roles must be system, enabled, and company scoped");
        }
        permissionCodes = Set.copyOf(permissionCodes);
        memberKeys = Set.copyOf(memberKeys);
    }
}
