package com.bebefish.erp.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "erp.feishu.enabled=true",
        "erp.feishu.app-id=mock-app",
        "erp.feishu.app-secret=mock-secret",
        "erp.feishu.redirect-uri=http://127.0.0.1:5173/api/auth/feishu/callback",
        "erp.feishu.allowed-tenant-key=tenant-a",
        "erp.feishu.mock-enabled=true"
})
@Transactional
class FeishuLoginFlowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private long productRoleId;

    @BeforeEach
    void configureMapping() {
        jdbc.update("""
                insert ignore into sys_role
                    (code, name, description, kind, immutable, is_sensitive, status, data_scope,
                     updated_by, created_at, updated_at)
                values ('PRODUCT_OPERATOR', '商品运营', '集成测试角色', 'custom', false, false,
                        'enabled', 'DEPARTMENT', 'test', now(3), now(3))
                """);
        productRoleId = jdbc.queryForObject(
                "select id from sys_role where code = 'PRODUCT_OPERATOR'", Long.class);
        jdbc.update("""
                insert into sys_feishu_role_mapping
                    (tenant_key, feishu_role_id, feishu_role_name, erp_role_id, enabled,
                     member_count, created_at, updated_at)
                values ('tenant-a', 'mock-role-warehouse', '模拟仓库主管', ?, true, 0, now(3), now(3))
                on duplicate key update erp_role_id = values(erp_role_id), enabled = true
                """, productRoleId);
    }

    @Test
    void mockOauthFlowCreatesEmployeeMapsRolesAuthenticatesMeAndRevokesSession() throws Exception {
        JsonNode login = exchange(completeMockCallback("mock-no-mobile"));

        assertThat(login.path("loginMethod").asText()).isEqualTo("feishu");
        assertThat(textValues(login.path("roles")))
                .contains("BASIC_EMPLOYEE", "PRODUCT_OPERATOR");
        assertThat(login.path("mobile").isNull()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_feishu_identity where tenant_key = 'tenant-a' and open_id = 'ou_mock-no-mobile'",
                Integer.class)).isEqualTo(1);

        String token = login.path("accessToken").asText();
        JsonNode me = responseJson(mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(me.path("employeeId").asLong()).isEqualTo(login.path("employeeId").asLong());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnotherTenantBeforeWritingIdentityOrEmployee() throws Exception {
        int employeesBefore = jdbc.queryForObject("select count(*) from employee", Integer.class);
        int usersBefore = jdbc.queryForObject("select count(*) from sys_user", Integer.class);
        var callback = callback("mock-other-tenant")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=FEISHU_TENANT_NOT_ALLOWED")))
                .andReturn();

        assertThat(callback.getResponse().getHeader("Location")).doesNotContain("ticket=");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_feishu_identity where open_id = 'ou_mock-other-tenant'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from employee", Integer.class)).isEqualTo(employeesBefore);
        assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isEqualTo(usersBefore);
    }

    @Test
    void nextLoginRemovesStaleFeishuRoleButKeepsLocalRoleAndReportsDegradation() throws Exception {
        JsonNode first = exchange(completeMockCallback("mock-user"));
        long userId = jdbc.queryForObject(
                "select user_id from sys_feishu_identity where open_id = 'ou_mock-user'", Long.class);
        long permissionAdminId = jdbc.queryForObject(
                "select id from sys_role where code = 'PERMISSION_ADMIN'", Long.class);
        jdbc.update("insert ignore into sys_user_role (user_id, role_id, assignment_source, assigned_at) values (?, ?, 'LOCAL', now(3))",
                userId, permissionAdminId);
        jdbc.update("delete from sys_feishu_role_mapping where tenant_key = 'tenant-a' and feishu_role_id = 'mock-role-warehouse'");

        JsonNode second = exchange(completeMockCallback("mock-user"));
        assertThat(textValues(second.path("roles")))
                .contains("BASIC_EMPLOYEE", "PERMISSION_ADMIN")
                .doesNotContain("PRODUCT_OPERATOR");

        JsonNode degraded = exchange(completeMockCallback("mock-role-degraded"));
        assertThat(textValues(degraded.path("warnings")))
                .containsExactly("FEISHU_ROLE_SYNC_DEGRADED");
        assertThat(textValues(first.path("roles"))).contains("PRODUCT_OPERATOR");
    }

    private org.springframework.test.web.servlet.ResultActions callback(String code) throws Exception {
        var authorization = mvc.perform(get("/api/auth/feishu/authorize"))
                .andExpect(status().isFound()).andReturn();
        Cookie cookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");
        return mvc.perform(get("/api/auth/feishu/callback")
                .param("code", code).param("state", state).cookie(cookie));
    }

    private String completeMockCallback(String code) throws Exception {
        var result = callback(code).andExpect(status().isFound()).andReturn();
        return query(result.getResponse().getHeader("Location"), "ticket");
    }

    private JsonNode exchange(String ticket) throws Exception {
        String body = mvc.perform(post("/api/auth/feishu/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + ticket + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return responseJson(body).path("data");
    }

    private JsonNode responseJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private List<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private String query(String location, String name) {
        return Arrays.stream(URI.create(location).getQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair[0].equals(name))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }
}
