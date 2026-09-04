package com.bebefish.erp.masterdata.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
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
class CustomerSupplierControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired JdbcTemplate jdbc;
    private String editToken;
    private String viewToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from customer where customer_no like 'T4-%'");
        jdbc.update("delete from supplier where supplier_no like 'T4-%'");
        editToken = token("masterdata:view", "masterdata:edit");
        viewToken = token("masterdata:view");
    }

    @Test
    void createsAndListsCustomerWithDeliveryDefaults() throws Exception {
        mvc.perform(post("/api/customers").header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("""
                        {"customerNo":"T4-C001","customerName":"杭州客户","contactPerson":"张经理",
                         "mobile":"13800138000","province":"浙江省","city":"杭州市","district":"余杭区",
                         "detailAddress":"良渚街道88号","transportMethod":"delivery","settlementCycle":"monthly"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transportMethod").value("delivery"))
                .andExpect(jsonPath("$.data.settlementCycle").value("monthly"));

        mvc.perform(get("/api/customers").header("Authorization", bearer(viewToken))
                        .param("keyword", "T4-C001").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].customerNo").value("T4-C001"));
    }

    @Test
    void rejectsCustomerCreationWhenRequiredContactOrAddressIsMissing() throws Exception {
        mvc.perform(post("/api/customers").header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("""
                        {"customerName":"资料不完整客户","transportMethod":"delivery","settlementCycle":"monthly"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsCustomerWithGeneratedNumberWhenNumberIsOmitted() throws Exception {
                        mvc.perform(post("/api/customers").header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("""
                        {"customerName":"自动编号客户","contactPerson":"张经理","mobile":"13800138000",
                         "province":"浙江省","city":"杭州市","district":"余杭区","detailAddress":"良渚街道88号",
                         "transportMethod":"delivery","settlementCycle":"monthly"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerNo").value(org.hamcrest.Matchers.matchesRegex("CUS-[0-9]{8}-[A-Z0-9]{6}")));
    }

    @Test
    void createsAndDisablesSupplier() throws Exception {
        var body = mvc.perform(post("/api/suppliers").header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("""
                        {"supplierNo":"T4-S001","supplierName":"义乌玻璃厂","contactPerson":"李经理",
                         "mobile":"13900139000","address":"义乌市"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("enabled"))
                .andReturn().getResponse().getContentAsString();
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data").path("id").asLong();

        mvc.perform(post("/api/suppliers/{id}/status", id).header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("disabled"));
    }

    @Test
    void createsSupplierWithGeneratedNumberWhenNumberIsOmitted() throws Exception {
        mvc.perform(post("/api/suppliers").header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("""
                        {"supplierName":"自动编号供应商","contactPerson":"李经理",
                         "mobile":"13900139000","address":"义乌市"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierNo").value(org.hamcrest.Matchers.matchesRegex("SUP-[0-9]{8}-[A-Z0-9]{6}")));
    }

    @Test
    void refusesToDisableWalkInCustomer() throws Exception {
        var id = jdbc.queryForObject("select id from customer where customer_no='WALK_IN'", Long.class);
        mvc.perform(post("/api/customers/{id}/status", id).header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON).content("{\"status\":\"disabled\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALK_IN_CUSTOMER_REQUIRED"));
    }

    @Test
    void forbidsViewOnlyUserFromCreatingSupplier() throws Exception {
        mvc.perform(post("/api/suppliers").header("Authorization", bearer(viewToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"supplierNo\":\"T4-NOPE\",\"supplierName\":\"无权限\"}"))
                .andExpect(status().isForbidden());
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new AuthenticatedUser("13900000002",
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }
    private String bearer(String token) { return "Bearer " + token; }
}
