package com.bebefish.erp.authorization.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public enum DataScope {
    SELF, DEPARTMENT, DEPARTMENT_AND_DESCENDANTS, COMPANY;

    public static Optional<DataScope> widest(Collection<DataScope> scopes) {
        return scopes.stream().max(Comparator.comparingInt(Enum::ordinal));
    }
}
