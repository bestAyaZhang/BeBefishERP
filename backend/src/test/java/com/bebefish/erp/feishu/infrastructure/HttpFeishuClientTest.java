package com.bebefish.erp.feishu.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpFeishuClientTest {
    private HttpServer server;
    private FeishuProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new FeishuProperties();
        properties.setEnabled(true);
        properties.setAppId("cli_a");
        properties.setAppSecret("secret-a");
        properties.setRedirectUri("http://127.0.0.1:5173/api/auth/feishu/callback");
        properties.setAllowedTenantKey("tenant-a");
        properties.setApiBaseUri(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setAuthorizationUri(URI.create("https://accounts.feishu.cn/open-apis/authen/v1/authorize"));
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(1));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsAuthorizationUriAndMapsOAuthIdentity() {
        var userInfoAuthorization = new AtomicReference<String>();
        server.createContext("/open-apis/authen/v2/oauth/token", exchange -> json(exchange, """
                {"code":0,"data":{"access_token":"u-token","refresh_token":"r-token","expires_in":7200}}
                """));
        server.createContext("/open-apis/authen/v1/user_info", exchange -> {
            userInfoAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            json(exchange, """
                {"code":0,"data":{"tenant_key":"tenant-a","open_id":"ou_1","union_id":"on_1",
                "name":"张三","avatar_url":"https://avatar/a.png","mobile":"13800000001"}}
                """);
        });
        var client = client();

        assertThat(client.authorizationUri("state value").toString())
                .contains("app_id=cli_a", "state=state+value", "redirect_uri=http%3A%2F%2F127.0.0.1");
        var identity = client.exchangeCode("code-a");
        assertThat(identity.tenantKey()).isEqualTo("tenant-a");
        assertThat(identity.openId()).isEqualTo("ou_1");
        assertThat(identity.unionId()).isEqualTo("on_1");
        assertThat(identity.displayName()).isEqualTo("张三");
        assertThat(identity.mobile()).isEqualTo("13800000001");
        assertThat(userInfoAuthorization).hasValue("Bearer u-token");
    }

    @Test
    void mapsTenantAndEmployeeProfile() {
        var userQuery = new AtomicReference<String>();
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> json(exchange,
                "{\"code\":0,\"tenant_access_token\":\"t-token\",\"expire\":7200}"));
        server.createContext("/open-apis/tenant/v2/tenant/query", exchange -> json(exchange,
                "{\"code\":0,\"data\":{\"tenant\":{\"tenant_key\":\"tenant-a\"}}}"));
        server.createContext("/open-apis/contact/v3/users/ou_1", exchange -> {
            userQuery.set(exchange.getRequestURI().getRawQuery());
            json(exchange, """
                    {"code":0,"data":{"user":{"open_id":"ou_1","employee_no":"E001",
                    "mobile":"13800000001","department_ids":["od_1","od_2"],
                    "orders":[{"department_id":"od_2","is_primary_dept":false},
                    {"department_id":"od_1","is_primary_dept":true}],"name":"张三"}}}
                    """);
        });
        var client = client();

        assertThat(client.currentTenantKey()).isEqualTo("tenant-a");
        assertThat(client.employeeProfile("ou_1").employeeNo()).isEqualTo("E001");
        assertThat(client.employeeProfile("ou_1").primaryDepartmentId()).isEqualTo("od_1");
        assertThat(userQuery.get()).contains("department_id_type=open_department_id");
    }

    @Test
    void leavesPrimaryDepartmentUnsetWhenFeishuReturnsMultipleDepartments() {
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> json(exchange,
                "{\"code\":0,\"tenant_access_token\":\"t-token\",\"expire\":7200}"));
        server.createContext("/open-apis/contact/v3/users/ou_1", exchange -> json(exchange, """
                {"code":0,"data":{"user":{"open_id":"ou_1","employee_no":"E001",
                "department_ids":["od_1","od_2"],
                "orders":[{"department_id":"od_1","is_primary_dept":false},
                {"department_id":"od_2","is_primary_dept":false}],"name":"张三"}}}
                """));

        assertThat(client().employeeProfile("ou_1").primaryDepartmentId()).isNull();
    }

    @Test
    void usesSingleMemberLookupForLoginAndPaginatesMemberListsForManagement() {
        properties.setBusinessRoles("role-1|仓库主管,role-2|财务");
        var listCalls = new AtomicInteger();
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> json(exchange,
                "{\"code\":0,\"tenant_access_token\":\"t-token\",\"expire\":7200}"));
        server.createContext("/open-apis/contact/v3/functional_roles/role-1/members/ou_1", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery())
                    .contains("user_id_type=open_id", "department_id_type=open_department_id");
            json(exchange, "{\"code\":0,\"data\":{\"member\":{\"user_id\":\"ou_1\"}}}");
        });
        server.createContext("/open-apis/contact/v3/functional_roles/role-2/members/ou_1", exchange ->
                json(exchange, 404, "{\"code\":41203,\"msg\":\"member id is not exist\"}"));
        server.createContext("/open-apis/contact/v3/functional_roles/role-1/members", exchange -> {
            listCalls.incrementAndGet();
            if (exchange.getRequestURI().getRawQuery().contains("page_token=next-1")) {
                json(exchange, """
                        {"code":0,"data":{"members":[{"user_id":"ou_3"}],"has_more":false}}
                        """);
                return;
            }
            json(exchange, """
                    {"code":0,"data":{"members":[{"user_id":"ou_1"},{"user_id":"ou_2"}],
                    "has_more":true,"page_token":"next-1"}}
                    """);
        });
        server.createContext("/open-apis/contact/v3/functional_roles/role-2/members", exchange -> {
            listCalls.incrementAndGet();
            json(exchange, """
                    {"code":0,"data":{"members":[{"user_id":"ou_9"}],"has_more":false}}
                    """);
        });
        var client = client();

        assertThat(client.businessRoles("ou_1"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.id()).isEqualTo("role-1");
                    assertThat(role.name()).isEqualTo("仓库主管");
                });
        assertThat(listCalls).hasValue(0);
        assertThat(client.allBusinessRoles())
                .satisfiesExactly(
                        role -> {
                            assertThat(role.id()).isEqualTo("role-1");
                            assertThat(role.memberCount()).isEqualTo(3);
                        },
                        role -> {
                            assertThat(role.id()).isEqualTo("role-2");
                            assertThat(role.memberCount()).isEqualTo(1);
                        }
                );
        assertThat(listCalls).hasValue(3);
    }

    @Test
    void rejectsMissingConfiguredBusinessRoleInsteadOfTreatingItAsNonMembership() {
        properties.setBusinessRoles("missing-role|已删除角色");
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> json(exchange,
                "{\"code\":0,\"tenant_access_token\":\"t-token\",\"expire\":7200}"));
        server.createContext("/open-apis/contact/v3/functional_roles/missing-role/members/ou_1", exchange ->
                json(exchange, 404, "{\"code\":41202,\"msg\":\"role id is not exist\"}"));

        assertThatThrownBy(() -> client().businessRoles("ou_1"))
                .isInstanceOf(FeishuClientException.class);
    }

    @Test
    void rejectsRoleMemberResponseWithoutBusinessCode() {
        configureRoleMemberResponse("{}");

        assertThatThrownBy(() -> client().businessRoles("ou_1"))
                .isInstanceOf(FeishuClientException.class);
    }

    @Test
    void rejectsRoleMemberResponseWithoutMember() {
        configureRoleMemberResponse("{\"code\":0,\"data\":{}}");

        assertThatThrownBy(() -> client().businessRoles("ou_1"))
                .isInstanceOf(FeishuClientException.class);
    }

    @Test
    void rejectsRoleMemberResponseForAnotherUser() {
        configureRoleMemberResponse("{\"code\":0,\"data\":{\"member\":{\"user_id\":\"ou_2\"}}}");

        assertThatThrownBy(() -> client().businessRoles("ou_1"))
                .isInstanceOf(FeishuClientException.class);
    }

    @Test
    void rejectsOAuthIdentityWithoutTenantOrOpenId() {
        var requests = new AtomicInteger();
        server.createContext("/open-apis/authen/v2/oauth/token", exchange -> json(exchange, """
                {"code":0,"data":{"access_token":"u-token","expires_in":7200}}
                """));
        server.createContext("/open-apis/authen/v1/user_info", exchange -> {
            if (requests.getAndIncrement() == 0) {
                json(exchange, "{\"code\":0,\"data\":{\"open_id\":\"ou_1\"}}");
            } else {
                json(exchange, "{\"code\":0,\"data\":{\"tenant_key\":\"tenant-a\"}}");
            }
        });

        assertThatThrownBy(() -> client().exchangeCode("missing-tenant"))
                .isInstanceOf(FeishuClientException.class);
        assertThatThrownBy(() -> client().exchangeCode("missing-open-id"))
                .isInstanceOf(FeishuClientException.class);
    }

    @Test
    void requestTimeoutIsFiniteAndDoesNotLeakCredentials() {
        server.createContext("/open-apis/authen/v2/oauth/token", exchange -> {
            try {
                Thread.sleep(250);
                json(exchange, "{\"code\":0}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        properties.setRequestTimeout(Duration.ofMillis(50));

        assertThatThrownBy(() -> client().exchangeCode("code-a"))
                .isInstanceOf(FeishuClientException.class)
                .hasMessageNotContaining("secret-a")
                .hasMessageNotContaining("code-a");
    }

    private HttpFeishuClient client() {
        return new HttpFeishuClient(
                properties,
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build()
        );
    }

    private void configureRoleMemberResponse(String responseBody) {
        properties.setBusinessRoles("role-1|仓库主管");
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> json(exchange,
                "{\"code\":0,\"tenant_access_token\":\"t-token\",\"expire\":7200}"));
        server.createContext("/open-apis/contact/v3/functional_roles/role-1/members/ou_1", exchange ->
                json(exchange, responseBody));
    }

    private void json(HttpExchange exchange, String body) throws IOException {
        json(exchange, 200, body);
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
