package com.bebefish.erp.feishu;

public record FeishuOAuthIdentity(
        String tenantKey,
        String openId,
        String unionId,
        String displayName,
        String avatarUrl,
        String mobile
) {
}
