package com.bebefish.erp.masterdata.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    private String editToken;
    private String viewToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from warehouse where warehouse_no like 'T5-%'");
        jdbc.update("delete from warehouse where warehouse_name = '自动编号仓库-T5'");
        editToken = token("masterdata:view", "masterdata:edit");
        viewToken = token("masterdata:view");
    }

    @Test
    void switchesDefaultWarehouseAndKeepsExactlyOneDefault() throws Exception {
        var firstId = create("T5-WH1", "一号仓", true);
        var secondId = create("T5-WH2", "二号仓", false);

        mvc.perform(post("/api/warehouses/{id}/default", secondId)
                        .header("Authorization", bearer(editToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultWarehouse").value(true));

        var defaults = jdbc.queryForObject(
                "select count(*) from warehouse where status='enabled' and is_default=true",
                Integer.class
        );
        var oldDefault = jdbc.queryForObject("select is_default from warehouse where id=?", Boolean.class, firstId);
        org.assertj.core.api.Assertions.assertThat(defaults).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(oldDefault).isFalse();
    }

    @Test
    void createsWarehouseWithGeneratedNumberWhenNumberIsOmitted() throws Exception {
        mvc.perform(post("/api/warehouses")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                        {"warehouseName":"自动编号仓库-T5","address":"杭州","defaultWarehouse":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseNo").value(org.hamcrest.Matchers.matchesRegex("WH-[0-9]{8}-[A-Z0-9]{6}")));
    }

    @Test
    void refusesToDisableDefaultWarehouse() throws Exception {
        var id = create("T5-MAIN", "主仓", true);

        mvc.perform(post("/api/warehouses/{id}/status", id)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEFAULT_WAREHOUSE_REQUIRED"));
    }

    @Test
    void forbidsViewOnlyUserFromSettingDefault() throws Exception {
        var id = create("T5-VIEW", "查看仓", false);
        mvc.perform(post("/api/warehouses/{id}/default", id)
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isForbidden());
    }

    private long create(String number, String name, boolean isDefault) throws Exception {
        var body = mvc.perform(post("/api/warehouses")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Input(number, name, "杭州", isDefault))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new UserAccount("13900000003", "unused", true, true,
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private record Input(String warehouseNo, String warehouseName, String address, boolean defaultWarehouse) {}
}
