package com.bebefish.erp.feishu;

public record FeishuDirectoryUser(
        String openId, String unionId, String avatarUrl, FeishuEmployeeProfile profile) {
    public FeishuOAuthIdentity identity(String tenantKey) {
        return new FeishuOAuthIdentity(
                tenantKey, openId, unionId, profile.displayName(), avatarUrl, profile.mobile());
    }
}
