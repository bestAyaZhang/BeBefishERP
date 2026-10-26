package com.bebefish.erp.logistics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LogisticsApiTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired TokenIssuer tokens; @Autowired ObjectMapper mapper;
    String token(String... p) { return TestAuthTokens.issue(jdbc,tokens,"13900008600",p); }
    String admin() { return token("logistics:view","logistics:edit","logistics:configure","logistics:template","shipping:view"); }
    long company(String t) throws Exception {
        var r=mvc.perform(post("/api/logistics/companies").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"code\":\" test_carrier \",\"name\":\"测试承运商\",\"provider\":\"MANUAL\",\"status\":\"enabled\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("TEST_CARRIER")).andReturn();
        return mapper.readTree(r.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
    }
    @Test void companyCrudNormalizesCodesRejectsDuplicatesAndChecksVersions() throws Exception {
        String t=admin(); long id=company(t);
        mvc.perform(get("/api/logistics/companies?size=10").header("Authorization","Bearer "+t)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].provider").value("MANUAL"));
        mvc.perform(post("/api/logistics/companies").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"code\":\"test_carrier\",\"name\":\"重复\",\"provider\":\"MANUAL\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/logistics/companies/"+id+"/status").header("Authorization","Bearer "+t)
            .contentType(APPLICATION_JSON).content("{\"status\":\"disabled\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/logistics/companies/"+id+"/status").header("Authorization","Bearer "+t)
            .contentType(APPLICATION_JSON).content("{\"status\":\"enabled\",\"version\":0}"))
            .andExpect(status().isConflict());
    }
    @Test void independentPermissionsGuardEveryManagementBoundary() throws Exception {
        mvc.perform(get("/api/logistics/companies")).andExpect(status().isUnauthorized());
        String shipping=token("shipping:view"),view=token("logistics:view"),edit=token("logistics:edit");
        mvc.perform(get("/api/logistics/companies").header("Authorization","Bearer "+shipping)).andExpect(status().isForbidden());
        mvc.perform(get("/api/logistics/companies").header("Authorization","Bearer "+view)).andExpect(status().isOk());
        mvc.perform(post("/api/logistics/companies").header("Authorization","Bearer "+view).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        long id=company(edit);
        mvc.perform(put("/api/logistics/companies/"+id+"/account").header("Authorization","Bearer "+edit)
            .contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/logistics/companies/"+id+"/templates").header("Authorization","Bearer "+edit)
            .contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/logistics/quotes").header("Authorization","Bearer "+view).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/logistics/provinces").header("Authorization","Bearer "+shipping)).andExpect(status().isOk());
    }
    @Test void missingMasterKeyRejectsApiSecretsButAllowsManualConfigurationAndReads() throws Exception {
        String t=admin();
        var result=mvc.perform(post("/api/logistics/companies").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"code\":\"NO_MASTER\",\"name\":\"安能\",\"provider\":\"ANE\"}"))
            .andExpect(status().isOk()).andReturn();
        long id=mapper.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        mvc.perform(put("/api/logistics/companies/"+id+"/account").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"version\":0,\"code\":\"C\",\"customerCode\":\"CUSTOMER\",\"dictId\":\"D\",\"appKey\":\"TOP-SECRET\",\"customerPass\":\"PASS-SECRET\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGISTICS_MASTER_KEY_MISSING"));
        assertThat(jdbc.queryForObject("select count(*) from logistics_account where company_id=?",Long.class,id)).isZero();
        mvc.perform(get("/api/logistics/companies/"+id+"/account").header("Authorization","Bearer "+t)).andExpect(status().isOk());
        long manual=company(t);
        mvc.perform(put("/api/logistics/companies/"+manual+"/account").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"version\":0,\"customerCode\":\"OFFLINE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appKey").doesNotExist()).andExpect(jsonPath("$.data.customerPass").doesNotExist());
    }}
