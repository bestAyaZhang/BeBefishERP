package com.bebefish.erp.auth.domain;

public interface PasswordHasher {
    boolean matches(String rawPassword, String passwordHash);
}
