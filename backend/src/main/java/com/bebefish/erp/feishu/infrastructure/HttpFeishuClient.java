package com.bebefish.erp.feishu.infrastructure;

import com.bebefish.erp.feishu.FeishuBusinessRole;
import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuDirectoryClient;
import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;
import com.bebefish.erp.feishu.FeishuProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "erp.feishu", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class HttpFeishuClient implements FeishuOAuthClient, FeishuDirectoryClient {
    private final FeishuProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public HttpFeishuClient(FeishuProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build()
        );
    }

    public HttpFeishuClient(FeishuProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public URI authorizationUri(String state) {
        String query = "app_id=" + encode(properties.getAppId())
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&state=" + encode(state);
        return URI.create(properties.getAuthorizationUri() + "?" + query);
    }

    @Override
    public FeishuOAuthIdentity exchangeCode(String code) {
        JsonNode tokenData = data(post("/open-apis/authen/v2/oauth/token", Map.of(
                "grant_type", "authorization_code",
                "client_id", properties.getAppId(),
                "client_secret", properties.getAppSecret(),
                "code", code,
                "redirect_uri", properties.getRedirectUri()
        ), null));
        String userAccessToken = text(tokenData, "access_token");
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new FeishuClientException("飞书接口未返回用户访问凭据");
        }
        JsonNode data = data(get("/open-apis/authen/v1/user_info", userAccessToken));
        String tenantKey = requiredIdentityField(data, "tenant_key");
        String openId = requiredIdentityField(data, "open_id");
        return new FeishuOAuthIdentity(
                tenantKey,
                openId,
                text(data, "union_id"),
                firstText(data, "name", "display_name"),
                text(data, "avatar_url"),
                text(data, "mobile")
        );
    }

    @Override
    public String currentTenantKey() {
        JsonNode data = data(get("/open-apis/tenant/v2/tenant/query", tenantAccessToken()));
        JsonNode tenant = data.path("tenant");
        return text(tenant.isMissingNode() ? data : tenant, "tenant_key");
    }

    @Override
    public FeishuEmployeeProfile employeeProfile(String openId) {
        JsonNode data = data(get(
                "/open-apis/contact/v3/users/" + encodePath(openId)
                        + "?user_id_type=open_id&department_id_type=open_department_id",
                tenantAccessToken()
        ));
        JsonNode user = data.path("user");
        if (user.isMissingNode()) {
            user = data;
        }
        return new FeishuEmployeeProfile(
                firstText(user, "open_id", "user_id"),
                firstText(user, "employee_no", "employee_number"),
                text(user, "mobile"),
                primaryDepartmentId(user),
                firstText(user, "name", "display_name")
        );
    }

    @Override
    public List<FeishuBusinessRole> businessRoles(String openId) {
        List<FeishuBusinessRole> result = new ArrayList<>();
        List<ConfiguredBusinessRole> configuredRoles = configuredBusinessRoles();
        if (configuredRoles.isEmpty()) {
            return List.of();
        }
        String accessToken = tenantAccessToken();
        for (ConfiguredBusinessRole role : configuredRoles) {
            if (functionalRoleMemberExists(role.id(), openId, accessToken)) {
                result.add(new FeishuBusinessRole(role.id(), role.name()));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<FeishuBusinessRole> allBusinessRoles() {
        List<FeishuBusinessRole> result = new ArrayList<>();
        List<ConfiguredBusinessRole> configuredRoles = configuredBusinessRoles();
        if (configuredRoles.isEmpty()) {
            return List.of();
        }
        String accessToken = tenantAccessToken();
        for (ConfiguredBusinessRole role : configuredRoles) {
            result.add(new FeishuBusinessRole(
                    role.id(), role.name(), functionalRoleMembers(role.id(), accessToken).size()
            ));
        }
        return List.copyOf(result);
    }

    private List<ConfiguredBusinessRole> configuredBusinessRoles() {
        if (properties.getBusinessRoles() == null || properties.getBusinessRoles().isBlank()) {
            return List.of();
        }
        List<ConfiguredBusinessRole> roles = new ArrayList<>();
        for (String value : properties.getBusinessRoles().split(",")) {
            String[] parts = value.trim().split("\\|", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new FeishuClientException("飞书业务角色配置无效");
            }
            roles.add(new ConfiguredBusinessRole(parts[0].trim(), parts[1].trim()));
        }
        return List.copyOf(roles);
    }

    private Set<String> functionalRoleMembers(String roleId, String accessToken) {
        Set<String> members = new HashSet<>();
        Set<String> visitedPageTokens = new HashSet<>();
        String pageToken = null;
        do {
            String path = "/open-apis/contact/v3/functional_roles/" + encodePath(roleId)
                    + "/members?page_size=100&user_id_type=open_id";
            if (pageToken != null) {
                path += "&page_token=" + encode(pageToken);
            }
            JsonNode page = data(get(path, accessToken));
            JsonNode items = page.path("members");
            if (items.isArray()) {
                items.forEach(item -> {
                    String userId = text(item, "user_id");
                    if (userId != null && !userId.isBlank()) {
                        members.add(userId);
                    }
                });
            }
            if (!page.path("has_more").asBoolean(false)) {
                pageToken = null;
            } else {
                pageToken = text(page, "page_token");
                if (pageToken == null || pageToken.isBlank() || !visitedPageTokens.add(pageToken)) {
                    throw new FeishuClientException("飞书业务角色分页响应无效");
                }
            }
        } while (pageToken != null);
        return Set.copyOf(members);
    }

    private boolean functionalRoleMemberExists(String roleId, String openId, String accessToken) {
        String path = "/open-apis/contact/v3/functional_roles/" + encodePath(roleId)
                + "/members/" + encodePath(openId)
                + "?user_id_type=open_id&department_id_type=open_department_id";
        var request = HttpRequest.newBuilder(uri(path))
                .timeout(properties.getRequestTimeout())
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return send(request, true) != null;
    }

    private String primaryDepartmentId(JsonNode user) {
        JsonNode orders = user.path("orders");
        if (!orders.isArray()) {
            return null;
        }
        for (JsonNode order : orders) {
            if (order.path("is_primary_dept").asBoolean(false)) {
                String departmentId = text(order, "department_id");
                if (departmentId != null && !departmentId.isBlank()) {
                    return departmentId;
                }
            }
        }
        return null;
    }

    private String tenantAccessToken() {
        JsonNode root = post("/open-apis/auth/v3/tenant_access_token/internal", Map.of(
                "app_id", properties.getAppId(),
                "app_secret", properties.getAppSecret()
        ), null);
        String token = text(root, "tenant_access_token");
        if (token == null && root.has("data")) {
            token = text(root.get("data"), "tenant_access_token");
        }
        if (token == null || token.isBlank()) {
            throw new FeishuClientException("飞书接口未返回企业访问凭据");
        }
        return token;
    }

    private JsonNode get(String path, String bearerToken) {
        var builder = HttpRequest.newBuilder(uri(path))
                .timeout(properties.getRequestTimeout())
                .GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return send(builder.build());
    }

    private JsonNode post(String path, Map<String, String> body, String bearerToken) {
        try {
            var builder = HttpRequest.newBuilder(uri(path))
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return send(builder.build());
        } catch (JsonProcessingException exception) {
            throw new FeishuClientException("无法创建飞书接口请求", exception);
        }
    }

    private JsonNode send(HttpRequest request) {
        return send(request, false);
    }

    private JsonNode send(HttpRequest request, boolean notFoundAsEmpty) {
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (notFoundAsEmpty && response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FeishuClientException("飞书接口返回非成功状态");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(0) != 0) {
                throw new FeishuClientException("飞书接口返回业务错误");
            }
            return root;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FeishuClientException("飞书接口请求被中断", exception);
        } catch (IOException exception) {
            throw new FeishuClientException("飞书接口请求失败", exception);
        }
    }

    private JsonNode data(JsonNode root) {
        return root.has("data") ? root.get("data") : root;
    }

    private URI uri(String path) {
        return URI.create(properties.getApiBaseUri().toString().replaceFirst("/$", "") + path);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null || value.isBlank() ? text(node, second) : value;
    }

    private String requiredIdentityField(JsonNode data, String field) {
        String value = text(data, field);
        if (value == null || value.isBlank()) {
            throw new FeishuClientException("飞书接口返回的用户身份不完整");
        }
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private record ConfiguredBusinessRole(String id, String name) {
    }
}
