package com.bebefish.erp.identity.domain;

import java.time.Instant;

public record FeishuIdentity(
        long id,
        long userId,
        String tenantKey,
        String openId,
        String unionId,
        String displayName,
        String avatarUrl,
        Instant boundAt,
        Instant lastVerifiedAt
) {
}
