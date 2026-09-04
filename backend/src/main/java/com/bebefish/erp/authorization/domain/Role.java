package com.bebefish.erp.authorization.domain;

import java.util.Set;

public record Role(
        String code,
        String name,
        boolean system,
        boolean superAdministrator,
        boolean enabled,
        DataScope dataScope,
        Set<String> permissionCodes,
        Set<String> memberKeys
) {
    public Role {
        permissionCodes = Set.copyOf(permissionCodes);
        memberKeys = Set.copyOf(memberKeys);
    }
}
