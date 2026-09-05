package com.bebefish.erp.organization;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    String token;

    @BeforeEach
    void login() throws Exception {
        var result =
                mvc.perform(
                                post("/api/auth/login/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"mobile\":\"13800138000\",\"password\":\"Admin@123456\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        token =
                "Bearer "
                        + mapper.readTree(result.getResponse().getContentAsString())
                                .path("data")
                                .path("accessToken")
                                .asText();
    }

    JsonNode save(String path, String json) throws Exception {
        var result =
                mvc.perform(
                                post("/api/organization/" + path)
                                        .header("Authorization", token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void migrationAndSyncPermissionExist() {
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from information_schema.columns where"
                                        + " table_schema=database() and ((table_name='employee' and"
                                        + " column_name in"
                                        + " ('hire_date','feishu_job_title','status_source')) or"
                                        + " (table_name='department' and"
                                        + " column_name='manager_employee_id') or"
                                        + " (table_name='position' and"
                                        + " column_name='responsibilities'))",
                                Integer.class))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForList(
                                "select r.code from sys_role r join sys_role_permission rp on"
                                        + " rp.role_id=r.id join sys_permission p on"
                                        + " p.id=rp.permission_id where p.code='organization:sync'",
                                String.class))
                .containsExactly("SUPER_ADMIN");
    }

    @Test
    void emptyFiltersAndPageBounds() throws Exception {
        mvc.perform(
                        get("/api/organization/employees")
                                .header("Authorization", token)
                                .param("keyword", "missing-unique-name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(
                        get("/api/organization/employees")
                                .header("Authorization", token)
                                .param("page", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/organization/employees")
                                .header("Authorization", token)
                                .param("size", "501"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/organization/departments")).andExpect(status().isUnauthorized());
    }

    @Test
    void crudDescendantsOccupancyUniquenessAndManualStatus() throws Exception {
        long parent =
                save(
                                "departments",
                                "{\"departmentCode\":\"ORG-P\",\"departmentName\":\"Parent\",\"sortOrder\":0,\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        long child =
                save(
                                "departments",
                                "{\"departmentCode\":\"ORG-C\",\"departmentName\":\"Child\",\"parentId\":"
                                        + parent
                                        + ",\"sortOrder\":0,\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        long position =
                save(
                                "positions",
                                "{\"positionCode\":\"ORG-J\",\"positionName\":\"Job\",\"departmentId\":"
                                        + child
                                        + ",\"responsibilities\":\"Work\",\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        String employee =
                "{\"employeeNo\":\"ORG-E\",\"employeeName\":\"Test"
                        + " Employee\",\"mobile\":\"13911112222\",\"departmentId\":"
                        + child
                        + ",\"positionId\":"
                        + position
                        + ",\"employmentType\":\"temporary\",\"status\":\"active\",\"hireDate\":\"2026-01-01\",\"passwordLoginEnabled\":true,\"password\":\"Test@123456\"}";
        long eid = save("employees", employee).path("id").asLong();
        mvc.perform(
                        get("/api/organization/employees")
                                .header("Authorization", token)
                                .param("departmentId", "" + parent)
                                .param("employmentType", "temporary")
                                .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(eid))
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
        mvc.perform(
                        post("/api/organization/employees")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(employee))
                .andExpect(status().isConflict());
        for (String path : new String[] {"departments/" + child, "positions/" + position})
            mvc.perform(
                            patch("/api/organization/" + path + "/status")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"disabled\"}"))
                    .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/organization/departments/" + parent)
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"departmentCode\":\"ORG-P\",\"departmentName\":\"Parent\",\"parentId\":"
                                                + child
                                                + ",\"sortOrder\":0,\"status\":\"enabled\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        patch("/api/organization/employees/" + eid + "/status")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select status_source from employee where id=?", String.class, eid))
                .isEqualTo("manual");
        assertThat(
                        jdbc.queryForObject(
                                "select status from sys_user where employee_id=?",
                                String.class,
                                eid))
                .isEqualTo("disabled");
        assertThat(
                        jdbc.queryForObject(
                                "select password_hash from sys_user where employee_id=?",
                                String.class,
                                eid))
                .startsWith("$2");
    }

    @Test
    void updateAndAllEndpointsPreservePasswordAndEnforcePermissions() throws Exception {
        var employee =
                save(
                        "employees",
                        """
{"employeeNo":"ORG-EDIT","employeeName":"Edit Me","mobile":"13922223333",
 "employmentType":"temporary","status":"active","passwordLoginEnabled":true,
 "password":"Test@123456"}
""");
        long id = employee.path("id").asLong();
        String hash =
                jdbc.queryForObject(
                        "select password_hash from sys_user where employee_id=?", String.class, id);
        mvc.perform(
                        put("/api/organization/employees/" + id)
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"employeeNo":"ORG-EDIT","employeeName":"Edited","mobile":"13922223333",
 "employmentType":"temporary","status":"active","passwordLoginEnabled":true}
"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeName").value("Edited"));
        assertThat(
                        jdbc.queryForObject(
                                "select password_hash from sys_user where employee_id=?",
                                String.class,
                                id))
                .isEqualTo(hash);
        for (String path :
                new String[] {
                    "employees/all",
                    "departments/all",
                    "positions/all",
                    "departments",
                    "positions",
                    "departments/employee-counts",
                    "employees/summary",
                    "employees/" + id
                }) {
            mvc.perform(get("/api/organization/" + path).header("Authorization", token))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/organization/employees/999999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        var login =
                mvc.perform(
                                post("/api/auth/login/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"mobile\":\"13922223333\",\"password\":\"Test@123456\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String basicToken =
                "Bearer "
                        + mapper.readTree(login.getResponse().getContentAsString())
                                .path("data")
                                .path("accessToken")
                                .asText();
        mvc.perform(get("/api/organization/employees").header("Authorization", basicToken))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/organization/departments")
                                .header("Authorization", basicToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"departmentCode\":\"DENIED\",\"departmentName\":\"Denied\",\"sortOrder\":0,\"status\":\"enabled\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/organization/employees/" + id + "/status")
                                .header("Authorization", basicToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentAndPositionUpdatesValidateReferencesAndUniqueness() throws Exception {
        long department =
                save(
                                "departments",
                                "{\"departmentCode\":\"ORG-UP-D\",\"departmentName\":\"Before\",\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        long position =
                save(
                                "positions",
                                "{\"positionCode\":\"ORG-UP-P\",\"positionName\":\"Before\",\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        mvc.perform(
                        put("/api/organization/departments/" + department)
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"departmentCode\":\"ORG-UP-D\",\"departmentName\":\"After\",\"status\":\"enabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentName").value("After"));
        mvc.perform(
                        put("/api/organization/positions/" + position)
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"positionCode\":\"ORG-UP-P\",\"positionName\":\"After\",\"responsibilities\":\"New"
                                            + " duties\",\"status\":\"enabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibilities").value("New duties"));
        mvc.perform(
                        post("/api/organization/departments")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"departmentCode\":\"ORG-UP-D\",\"departmentName\":\"Duplicate\",\"status\":\"enabled\"}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/organization/positions")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"positionCode\":\"ORG-UP-P\",\"positionName\":\"Duplicate\",\"status\":\"enabled\"}"))
                .andExpect(status().isConflict());
        for (String path : new String[] {"departments/" + department, "positions/" + position}) {
            mvc.perform(
                            patch("/api/organization/" + path + "/status")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"disabled\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(
                        post("/api/organization/employees")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"employeeNo\":\"INVALID-DEPT\",\"employeeName\":\"Invalid\",\"departmentId\":"
                                                + department
                                                + ",\"employmentType\":\"formal\",\"status\":\"active\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enabledPositionsPreventDepartmentDisableWithoutEmployees() throws Exception {
        long department =
                save(
                                "departments",
                                "{\"departmentCode\":\"OCCUPIED\",\"departmentName\":\"Occupied\",\"status\":\"enabled\"}")
                        .path("id")
                        .asLong();
        save(
                "positions",
                "{\"positionCode\":\"OCCUPIED\",\"positionName\":\"Occupied\",\"departmentId\":"
                        + department
                        + ",\"status\":\"enabled\"}");
        mvc.perform(
                        patch("/api/organization/departments/" + department + "/status")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void paginatesFilteredRowsAndRejectsDuplicateMobileIndependentlyOfEmployeeNumber()
            throws Exception {
        for (int index = 0; index < 3; index++) {
            save(
                    "employees",
                    "{\"employeeNo\":\"PAGE-"
                            + index
                            + "\",\"employeeName\":\"Paging Employee\",\"mobile\":\"1394444000"
                            + index
                            + "\",\"employmentType\":\"temporary\",\"status\":\"active\"}");
        }
        mvc.perform(
                        get("/api/organization/employees")
                                .header("Authorization", token)
                                .param("keyword", "PAGE-")
                                .param("page", "2")
                                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].employeeNo").value("PAGE-2"));
        mvc.perform(
                        post("/api/organization/employees")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"employeeNo\":\"UNIQUE-NO\",\"employeeName\":\"Duplicate"
                                            + " Mobile\",\"mobile\":\"13944440000\",\"employmentType\":\"temporary\",\"status\":\"active\"}"))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from employee where employee_no='UNIQUE-NO'",
                                Integer.class))
                .isZero();
    }
}
