package com.bebefish.erp.organization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bebefish.erp.organization.application.FeishuDirectorySyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeishuSyncControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean FeishuDirectorySyncTaskService tasks;

    @Test
    void returnsAcceptedTaskAndNullableLatestAndRequiresSyncPermission() throws Exception {
        var login =
                mvc.perform(
                                post("/api/auth/login/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"mobile\":\"13800138000\",\"password\":\"Admin@123456\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String token =
                "Bearer "
                        + mapper.readTree(login.getResponse().getContentAsString())
                                .path("data")
                                .path("accessToken")
                                .asText();
        var task =
                new FeishuDirectorySyncTaskService.Task(
                        123,
                        "manual",
                        "pending",
                        java.time.Instant.now(),
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null);
        when(tasks.startManual(anyLong())).thenReturn(task);
        when(tasks.get(123)).thenReturn(task);
        mvc.perform(get("/api/organization/feishu-syncs/latest").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/organization/feishu-syncs").header("Authorization", token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(123));
        long userId =
                jdbc.queryForObject(
                        "select id from sys_user where mobile='13800138000'", Long.class);
        verify(tasks).startManual(userId);
        mvc.perform(get("/api/organization/feishu-syncs/123").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"));
        mvc.perform(post("/api/organization/feishu-syncs")).andExpect(status().isUnauthorized());
        jdbc.update("delete from sys_user_role where user_id=?", userId);
        jdbc.update(
                "insert into sys_user_role(user_id,role_id,assignment_source,assigned_at) select"
                    + " ?,id,'LOCAL',now(3) from sys_role where code='BASIC_EMPLOYEE'",
                userId);
        jdbc.update(
                "insert into sys_role_permission(role_id,permission_id,assigned_at) select"
                    + " r.id,p.id,now(3) from sys_role r join sys_permission p on"
                    + " p.code='organization:view' where r.code='BASIC_EMPLOYEE'");
        mvc.perform(post("/api/organization/feishu-syncs").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/organization/feishu-syncs/latest").header("Authorization", token))
                .andExpect(status().isOk());
    }
}
