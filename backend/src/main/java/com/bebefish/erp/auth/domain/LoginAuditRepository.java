package com.bebefish.erp.auth.domain;

public interface LoginAuditRepository {
    void record(LoginAuditEvent event);
}
