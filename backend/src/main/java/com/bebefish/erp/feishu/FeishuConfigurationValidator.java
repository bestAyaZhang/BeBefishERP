package com.bebefish.erp.feishu;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FeishuConfigurationValidator {
    public FeishuAvailability validate(
            FeishuProperties properties,
            Set<String> activeProfiles,
            FeishuDirectoryClient directory
    ) {
        if (!properties.isEnabled()) {
            return FeishuAvailability.unavailable("FEISHU_NOT_CONFIGURED", "飞书登录未启用");
        }
        if (properties.isMockEnabled() && activeProfiles.contains("prod")) {
            throw new IllegalStateException("生产环境禁止启用飞书模拟服务");
        }
        if (properties.isMockEnabled()
                && !activeProfiles.contains("local")
                && !activeProfiles.contains("test")) {
            throw new IllegalStateException("飞书模拟服务只允许在 local 或 test 环境启用");
        }
        if (blank(properties.getAppId())
                || blank(properties.getAppSecret())
                || blank(properties.getRedirectUri())
                || blank(properties.getAllowedTenantKey())) {
            return FeishuAvailability.unavailable("FEISHU_NOT_CONFIGURED", "飞书登录配置不完整");
        }
        try {
            if (!properties.getAllowedTenantKey().equals(directory.currentTenantKey())) {
                return FeishuAvailability.unavailable("FEISHU_TENANT_NOT_ALLOWED", "飞书应用企业与允许企业不一致");
            }
            return FeishuAvailability.ready();
        } catch (RuntimeException exception) {
            return FeishuAvailability.unavailable("FEISHU_NOT_CONFIGURED", "无法验证飞书应用企业");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
