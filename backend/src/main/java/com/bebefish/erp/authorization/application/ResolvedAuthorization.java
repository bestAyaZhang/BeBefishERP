package com.bebefish.erp.authorization.application;

import com.bebefish.erp.authorization.domain.DataScope;
import java.util.List;
import java.util.Optional;

public record ResolvedAuthorization(
        List<String> roles,
        List<String> permissions,
        Optional<DataScope> dataScope
) {
    public ResolvedAuthorization {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
        dataScope = Optional.ofNullable(dataScope).orElseGet(Optional::empty);
    }
}
