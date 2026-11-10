package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentLogisticsFilterIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenIssuer tokens;
    @Autowired ObjectMapper mapper;

    @Test
    void exactCompanyFilterIncludesUnorderedAndCancelledShipmentsWithConsistentPageTotals() throws Exception {
        String group = group(), company = "物流%_'公司";
        long unordered = shipment(group, 1, company, "unfinished", null);
        long cancelled = shipment(group, 2, " " + company + " ", "unfinished", "cancelled");
        long legacyOrdered = shipment(group, 3, company, "completed", "succeeded");
        shipment(group, 4, company + "分部", "unfinished", null);
        shipment(group, 5, "其他物流", "unfinished", null);
        String token = token();

        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", " " + company + " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(legacyOrdered))
                .andExpect(jsonPath("$.data.records[1].id").value(cancelled))
                .andExpect(jsonPath("$.data.records[1].logisticsOrderState").value("cancelled"))
                .andExpect(jsonPath("$.data.records[2].id").value(unordered));
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", company).param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(cancelled));
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", company).param("status", "completed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(legacyOrdered));
    }

    @Test
    void exportIncludesEveryMatchingShipmentBeyondThePageLimitInListOrder() throws Exception {
        String group = group(), company = "导出物流%_", token = token();
        var matchingIds = new ArrayList<Long>();
        for (int index = 1; index <= 101; index++) {
            matchingIds.add(shipment(group, index, " " + company + " ", "unfinished", null));
        }
        shipment(group, 102, company + "分部", "unfinished", null);
        shipment(group, 103, company, "completed", null);
        shipment(group(), 1, company, "unfinished", null);

        var list = mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", company).param("status", "unfinished")
                        .param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(101))
                .andExpect(jsonPath("$.data.records.length()").value(100))
                .andReturn().getResponse().getContentAsByteArray();
        var export = mvc.perform(get("/api/shipments/export").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", company).param("status", "unfinished"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(101))
                .andReturn().getResponse().getContentAsByteArray();

        var listRows = mapper.readTree(list).at("/data/records");
        var exportedRows = mapper.readTree(export).at("/data");
        var exportedIds = new ArrayList<Long>();
        exportedRows.forEach(row -> exportedIds.add(row.get("id").asLong()));
        assertThat(exportedIds).containsExactlyElementsOf(matchingIds.reversed());
        for (int index = 0; index < listRows.size(); index++) {
            assertThat(exportedRows.get(index)).isEqualTo(listRows.get(index));
        }
    }

    @Test
    void blankAndMissingCompanyFiltersIncludeAllRecordsWhileUnknownExactNamesMatchNothing() throws Exception {
        String group = group(), token = token();
        shipment(group, 1, "安能物流", "unfinished", null);
        shipment(group, 2, "壹米滴答", "unfinished", null);
        shipment(group, 3, "", "unfinished", null);

        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token).param("keyword", group))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", "   "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", "安能"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)
                        .param("keyword", group).param("logisticsCompany", "物".repeat(100)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void optionsTrimDeduplicateAndSortAllShipmentNamesIncludingHistoryAndDisabledCompanies() throws Exception {
        String group = group(), historical = "FILTER-A-" + group, disabled = "FILTER-B-" + group;
        shipment(group, 1, " " + historical + " ", "unfinished", null);
        shipment(group, 2, historical, "completed", "cancelled");
        shipment(group, 3, disabled, "unfinished", null);
        shipment(group, 4, "", "unfinished", null);
        shipment(group, 5, "   ", "unfinished", null);
        jdbc.update("""
                insert into logistics_company(code,name,provider,status,created_at,updated_at)
                values(?,?,'MANUAL','disabled',now(3),now(3))
                """, "LC-" + group, disabled);

        var result = mvc.perform(get("/api/shipments/filter-options").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.logisticsCompanies").isArray())
                .andReturn().getResponse().getContentAsByteArray();
        List<String> names = mapper.convertValue(mapper.readTree(result).at("/data/logisticsCompanies"), new TypeReference<>() {});

        assertThat(names).doesNotContain("", "   ", " " + historical + " ");
        assertThat(names.stream().filter(name -> name.endsWith(group)).toList()).containsExactly(historical, disabled);
    }

    private String token() {
        return TestAuthTokens.issue(jdbc, tokens, "13900006621", "shipping:view");
    }

    private static String group() { return "LFLT" + UUID.randomUUID().toString().replace("-", ""); }

    private long shipment(String group, int index, String company, String preparationStatus, String orderState) {
        String number = group + "-" + index;
        jdbc.update("""
                insert into shipment(shipment_no,shipment_date,recipient_name,recipient_phone,recipient_address,
                    logistics_company,preparation_content,remark,status,preparer,preparers_json,orderer,
                    version_no,created_by,updated_by,created_at,updated_at)
                values(?,current_date,'筛选测试收件人','13800138000','测试地址',?,'测试物品','',?,'备货员',
                    json_array('备货员'),'下单人',0,'test','test',now(3),now(3))
                """, number, company, preparationStatus);
        long id = jdbc.queryForObject("select id from shipment where shipment_no=?", Long.class, number);
        if (orderState != null) {
            jdbc.update("""
                    insert into shipment_logistics_order(shipment_id,order_no,state,test_environment,tracking_no,
                        child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at)
                    values(?,?,?,true,'','','测试状态','{}','test',now(3),now(3))
                    """, id, "BF-" + id, orderState);
        }
        return id;
    }
}
