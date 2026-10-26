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

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LogisticsAuditTest {
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions; @Autowired com.bebefish.erp.logistics.LogisticsCatalogService catalog; @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired TokenIssuer tokens; @Autowired ObjectMapper mapper;
    @Test void configurationReadsAndMutationsRetainAuthenticatedActorsWithoutSecretPayloads() throws Exception {
        String token=TestAuthTokens.issue(jdbc,tokens,"13900008699","logistics:view","logistics:edit","logistics:configure","logistics:template");
        long actor=tokens.resolve(token).employeeId();
        var created=mvc.perform(post("/api/logistics/companies").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON)
            .content("{\"code\":\"AUDITED\",\"name\":\"审计物流\",\"provider\":\"ANE\"}"))
            .andExpect(status().isOk()).andReturn();
        long id=mapper.readTree(created.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        mvc.perform(put("/api/logistics/companies/"+id+"/account").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON)
            .content("{\"version\":0,\"code\":\"C\",\"customerCode\":\"CUSTOMER\",\"dictId\":\"D\",\"appKey\":\"AUDIT-APP-SECRET\",\"customerPass\":\"AUDIT-PASS-SECRET\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/logistics/companies/"+id+"/account").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/logistics/companies/"+id+"/accounts").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        var template=mvc.perform(post("/api/logistics/companies/"+id+"/templates").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON)
            .content("""
                {"name":"审计模板","productCode":"P","productName":"产品","originProvince":"浙江省","originCity":"杭州市","originCounty":"余杭区","status":"enabled","version":0,
                 "rules":{"mode":"weight","volumetric":false,"defaultBands":[{"lowerExclusive":0,"upperInclusive":null,"firstQuantity":20,"firstFee":12,"additionalQuantity":1,"additionalFee":1}],"regionGroups":[]}}
                """
            )).andExpect(status().isOk()).andReturn();
        long templateId=mapper.readTree(template.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        mvc.perform(get("/api/logistics/templates/"+templateId).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/logistics/companies/"+id+"/templates").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/logistics/companies/"+id+"/status").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON)
            .content("{\"version\":1,\"status\":\"disabled\"}")).andExpect(status().isOk());
        var entries=jdbc.queryForList("select * from logistics_audit where company_id=? order by id",id);
        assertThat(entries).hasSizeGreaterThanOrEqualTo(4);
        assertThat(entries).allSatisfy(e->{ assertThat(((Number)e.get("actor_employee_id")).longValue()).isEqualTo(actor); assertThat(e.get("occurred_at")).isNotNull(); assertThat(e.get("environment")).isEqualTo("test"); });
        assertThat(entries.stream().map(e->e.get("action")).toList()).contains("ACCOUNT_SAVED","ACCOUNT_READ","ACCOUNT_HISTORY_READ","COMPANY_STATUS_CHANGED","TEMPLATE_PUBLISHED","TEMPLATE_READ","TEMPLATE_LIST_READ");
        assertThat(entries.toString()).doesNotContain("AUDIT-APP-SECRET","AUDIT-PASS-SECRET","secret_cipher","appKey","customerPass");
    }
    @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void auditRowsRollBackAtomicallyWithConfigurationMutations() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        long id=tx.execute(s->{
            var actor=new com.bebefish.erp.common.security.ErpPrincipal(77,"","审计测试",java.util.List.of(),java.util.List.of());
            var saved=catalog.createCompany(new LogisticsDtos.CompanyInput("AUDIT_ROLLBACK","审计回滚","MANUAL","enabled","","",0,"",null),actor);
            catalog.companyStatus(saved.id(),new LogisticsDtos.StatusInput("disabled",saved.version()),actor);
            assertThat(jdbc.queryForObject("select count(*) from logistics_audit where company_id=?",Long.class,saved.id())).isEqualTo(2);
            s.setRollbackOnly(); return saved.id();
        });
        assertThat(jdbc.queryForObject("select count(*) from logistics_company where id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from logistics_audit where company_id=?",Long.class,id)).isZero();
    }}
