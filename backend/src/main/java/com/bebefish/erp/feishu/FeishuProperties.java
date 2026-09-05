package com.bebefish.erp.feishu;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.feishu")
public class FeishuProperties {
    private boolean enabled;
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private String allowedTenantKey = "";
    private String businessRoles = "";
    private boolean mockEnabled;
    private URI apiBaseUri = URI.create("https://open.feishu.cn");
    private URI authorizationUri = URI.create("https://accounts.feishu.cn/open-apis/authen/v1/authorize");
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAllowedTenantKey() {
        return allowedTenantKey;
    }

    public void setAllowedTenantKey(String allowedTenantKey) {
        this.allowedTenantKey = allowedTenantKey;
    }

    public String getBusinessRoles() {
        return businessRoles;
    }

    public void setBusinessRoles(String businessRoles) {
        this.businessRoles = businessRoles;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public URI getApiBaseUri() {
        return apiBaseUri;
    }

    public void setApiBaseUri(URI apiBaseUri) {
        this.apiBaseUri = apiBaseUri;
    }

    public URI getAuthorizationUri() {
        return authorizationUri;
    }

    public void setAuthorizationUri(URI authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
