package com.bebefish.erp.feishu.infrastructure;

import com.bebefish.erp.feishu.FeishuBusinessRole;
import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuDirectoryClient;
import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;
import com.bebefish.erp.feishu.FeishuProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "erp.feishu", name = "mock-enabled", havingValue = "true")
public class MockFeishuClient implements FeishuOAuthClient, FeishuDirectoryClient {
    private final FeishuProperties properties;
    private String scenario = "mock-user";

    public MockFeishuClient(FeishuProperties properties) {
        this.properties = properties;
    }

    @Override
    public URI authorizationUri(String state) {
        return URI.create(properties.getRedirectUri()
                + "?code=mock-user&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
    }

    @Override
    public FeishuOAuthIdentity exchangeCode(String code) {
        scenario = code;
        String mobile = mobileFor(code);
        return new FeishuOAuthIdentity(
                "mock-other-tenant".equals(code) ? "tenant-other" : properties.getAllowedTenantKey(),
                "ou_" + code,
                "on_" + code,
                "模拟飞书员工",
                null,
                mobile
        );
    }

    @Override
    public String currentTenantKey() {
        return properties.getAllowedTenantKey();
    }

    @Override
    public FeishuEmployeeProfile employeeProfile(String openId) {
        String mobile = mobileFor(scenario);
        return new FeishuEmployeeProfile(openId, null, mobile, null, "模拟飞书员工");
    }

    @Override
    public List<FeishuBusinessRole> businessRoles(String openId) {
        if ("mock-role-degraded".equals(scenario)) {
            throw new FeishuClientException("模拟业务角色接口不可用");
        }
        return List.of(new FeishuBusinessRole("mock-role-warehouse", "模拟仓库主管"));
    }

    @Override
    public List<FeishuBusinessRole> allBusinessRoles() {
        return List.of(
                new FeishuBusinessRole("mock-role-warehouse", "模拟仓库主管", 4),
                new FeishuBusinessRole("mock-role-finance", "模拟财务", 2)
        );
    }

    private String mobileFor(String code) {
        return switch (code) {
            case "mock-no-mobile" -> null;
            case "mock-existing-mobile" -> "13800138000";
            case "mock-role-degraded" -> "13900000002";
            case "mock-other-tenant" -> "13900000003";
            default -> "13900000001";
        };
    }
}
