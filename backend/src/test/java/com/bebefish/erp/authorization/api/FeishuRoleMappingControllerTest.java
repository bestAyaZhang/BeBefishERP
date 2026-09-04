package com.bebefish.erp.authorization.api;

import static org.hamcrest.Matchers.hasItem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
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
        "erp.feishu.mock-enabled=true",
        "erp.auth.local-admin.enabled=true",
        "erp.auth.local-admin.mobile=13800138000",
        "erp.auth.local-admin.password=Admin@123456"
})
@Transactional
class FeishuRoleMappingControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rejectsSensitiveTargetAndSupportsMappingCrud() throws Exception {
        String authorization = bearerToken();
        long superAdminId = jdbc.queryForObject("select id from sys_role where code = 'SUPER_ADMIN'", Long.class);
        long basicId = jdbc.queryForObject("select id from sys_role where code = 'BASIC_EMPLOYEE'", Long.class);

        mvc.perform(put("/api/permissions/feishu-role-mappings/fs-finance")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feishuRoleName\":\"财务\",\"erpRoleId\":" + superAdminId + ",\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SENSITIVE_ROLE_MAPPING_FORBIDDEN"));

        mvc.perform(put("/api/permissions/feishu-role-mappings/fs-basic")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feishuRoleName\":\"全员\",\"erpRoleId\":" + basicId + ",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feishuRoleId").value("fs-basic"));
        mvc.perform(get("/api/permissions/feishu-role-mappings")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.feishuRoleId == 'fs-basic')].erpRoleName")
                        .value(hasItem("基础员工")));
        mvc.perform(delete("/api/permissions/feishu-role-mappings/fs-basic")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
    }

    @Test
    void syncRefreshesFeishuRoleNameAndMemberCount() throws Exception {
        String authorization = bearerToken();
        long basicId = jdbc.queryForObject("select id from sys_role where code = 'BASIC_EMPLOYEE'", Long.class);
        mvc.perform(put("/api/permissions/feishu-role-mappings/mock-role-warehouse")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feishuRoleName\":\"旧名称\",\"erpRoleId\":" + basicId + ",\"enabled\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/permissions/feishu-role-mappings/sync")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.feishuRoleId == 'mock-role-warehouse')].feishuRoleName")
                        .value(hasItem("模拟仓库主管")))
                .andExpect(jsonPath("$.data[?(@.feishuRoleId == 'mock-role-warehouse')].memberCount")
                        .value(hasItem(4)));
    }

    private String bearerToken() throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13800138000\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
