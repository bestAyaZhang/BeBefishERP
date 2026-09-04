package com.bebefish.erp.auth.domain;

import java.time.Instant;

public record LoginAuditEvent(
        Long userId,
        String identityMethod,
        String result,
        String errorCode,
        String tenantKey,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {
}
