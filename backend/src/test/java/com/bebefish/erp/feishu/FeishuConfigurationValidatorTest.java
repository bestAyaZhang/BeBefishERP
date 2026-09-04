package com.bebefish.erp.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeishuConfigurationValidatorTest {
    private final FeishuConfigurationValidator validator = new FeishuConfigurationValidator();

    @Test
    void productionRejectsEnabledMockClient() {
        var properties = enabledProperties(true);

        assertThatThrownBy(() -> validator.validate(properties, Set.of("prod"), directory("tenant-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止启用飞书模拟服务");
    }

    @Test
    void appTenantMustMatchAllowedTenant() {
        var availability = validator.validate(enabledProperties(false), Set.of("test"), directory("tenant-other"));

        assertThat(availability.available()).isFalse();
        assertThat(availability.errorCode()).isEqualTo("FEISHU_TENANT_NOT_ALLOWED");
    }

    @Test
    void disabledOrIncompleteConfigurationIsReportedWithoutSecrets() {
        var disabled = validator.validate(new FeishuProperties(), Set.of("local"), directory("tenant-a"));
        assertThat(disabled.available()).isFalse();
        assertThat(disabled.errorCode()).isEqualTo("FEISHU_NOT_CONFIGURED");

        var incomplete = enabledProperties(false);
        incomplete.setAppSecret(" ");
        var availability = validator.validate(incomplete, Set.of("local"), directory("tenant-a"));
        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).doesNotContain("secret-a");
    }

    private FeishuProperties enabledProperties(boolean mock) {
        var properties = new FeishuProperties();
        properties.setEnabled(true);
        properties.setAppId("app-a");
        properties.setAppSecret("secret-a");
        properties.setRedirectUri("http://127.0.0.1:5173/api/auth/feishu/callback");
        properties.setAllowedTenantKey("tenant-a");
        properties.setMockEnabled(mock);
        properties.setApiBaseUri(URI.create("https://open.feishu.cn"));
        properties.setAuthorizationUri(URI.create("https://accounts.feishu.cn/open-apis/authen/v1/authorize"));
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setRequestTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private FeishuDirectoryClient directory(String tenantKey) {
        return new FeishuDirectoryClient() {
            @Override
            public String currentTenantKey() {
                return tenantKey;
            }

            @Override
            public FeishuEmployeeProfile employeeProfile(String openId) {
                return new FeishuEmployeeProfile(openId, null, null, null, null);
            }

            @Override
            public List<FeishuBusinessRole> businessRoles(String openId) {
                return List.of();
            }
        };
    }
}
