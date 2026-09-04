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
import java.util.List;
import java.util.Map;
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
        JsonNode data = data(post("/open-apis/authen/v2/oauth/token", Map.of(
                "grant_type", "authorization_code",
                "client_id", properties.getAppId(),
                "client_secret", properties.getAppSecret(),
                "code", code,
                "redirect_uri", properties.getRedirectUri()
        ), null));
        return new FeishuOAuthIdentity(
                text(data, "tenant_key"),
                text(data, "open_id"),
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
                "/open-apis/contact/v3/users/" + encodePath(openId) + "?user_id_type=open_id",
                tenantAccessToken()
        ));
        JsonNode user = data.path("user");
        if (user.isMissingNode()) {
            user = data;
        }
        JsonNode departments = user.path("department_ids");
        String primaryDepartment = departments.isArray() && !departments.isEmpty()
                ? departments.get(0).asText(null)
                : null;
        return new FeishuEmployeeProfile(
                firstText(user, "open_id", "user_id"),
                firstText(user, "employee_no", "employee_number"),
                text(user, "mobile"),
                primaryDepartment,
                firstText(user, "name", "display_name")
        );
    }

    @Override
    public List<FeishuBusinessRole> businessRoles(String openId) {
        JsonNode data = data(get(
                "/open-apis/contact/v3/users/" + encodePath(openId)
                        + "/functional_roles?user_id_type=open_id",
                tenantAccessToken()
        ));
        JsonNode items = data.path("items");
        List<FeishuBusinessRole> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(item -> result.add(new FeishuBusinessRole(
                    firstText(item, "role_id", "id"),
                    firstText(item, "role_name", "name")
            )));
        }
        return List.copyOf(result);
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
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }
}
