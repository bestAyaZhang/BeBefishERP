package com.bebefish.erp.product.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class SupplierQuoteControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    private String editToken;
    private String viewToken;
    private long skuId;
    private long firstSupplierId;
    private long secondSupplierId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete quote from sku_supplier_quote quote "
                + "join product_sku sku on sku.id = quote.sku_id "
                + "join product_spu product on product.id = sku.product_id "
                + "where product.product_code = 'T8-P100'");
        jdbc.update("delete sku from product_sku sku join product_spu product on product.id = sku.product_id "
                + "where product.product_code = 'T8-P100'");
        jdbc.update("delete from product_spu where product_code = 'T8-P100'");
        jdbc.update("delete from supplier where supplier_no in ('T8-SUP-1', 'T8-SUP-2')");
        jdbc.update("delete from product_category where category_code = 'T8-CAT'");
        jdbc.update("insert into product_category (category_code, category_name, level_no, sort_order, status, created_at, updated_at) "
                + "values ('T8-CAT', '报价测试分类', 1, 0, 'enabled', now(3), now(3))");
        var categoryId = jdbc.queryForObject("select id from product_category where category_code = 'T8-CAT'", Long.class);
        jdbc.update("insert into product_spu (product_code, item_no, product_name, category_id, product_type, status, created_at, updated_at) "
                + "values ('T8-P100', 'T8-ITEM', '报价测试产品', ?, 'simple', 'enabled', now(3), now(3))", categoryId);
        var productId = jdbc.queryForObject("select id from product_spu where product_code = 'T8-P100'", Long.class);
        jdbc.update("insert into product_sku (product_id, sku_code, sku_name, sales_unit, default_sale_price, standard_cost, is_default, status, created_at, updated_at) "
                + "values (?, 'T8-SKU-100', '报价测试 SKU', '只', 9.90, 1.00, true, 'enabled', now(3), now(3))", productId);
        skuId = jdbc.queryForObject("select id from product_sku where sku_code = 'T8-SKU-100'", Long.class);
        firstSupplierId = supplier("T8-SUP-1", "供应商一");
        secondSupplierId = supplier("T8-SUP-2", "供应商二");
        editToken = token("product:view", "product:edit");
        viewToken = token("product:view");
    }

    @Test
    void managesSupplierQuotesAndSynchronizesStandardCostOnlyWhenRequested() throws Exception {
        var first = createQuote(firstSupplierId, "2.20", true);
        var second = createQuote(secondSupplierId, "2.05", false);

        mvc.perform(get("/api/skus/{skuId}/supplier-quotes", skuId)
                        .header("Authorization", bearer(editToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(first.path("id").asLong()));

        mvc.perform(post("/api/skus/{skuId}/supplier-quotes/{quoteId}/default", skuId, second.path("id").asLong())
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"syncStandardCost\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultQuote").value(true));
        assertThatStandardCost("2.05");
        var firstIsDefault = jdbc.queryForObject(
                "select is_default from sku_supplier_quote where id = ?", Boolean.class, first.path("id").asLong()
        );
        org.assertj.core.api.Assertions.assertThat(firstIsDefault).isFalse();

        mvc.perform(put("/api/skus/{skuId}/supplier-quotes/{quoteId}", skuId, second.path("id").asLong())
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteBody(secondSupplierId, "2.00", true, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchasePrice").value(2.00));
        assertThatStandardCost("2.05");

        mvc.perform(put("/api/skus/{skuId}/supplier-quotes/{quoteId}", skuId, second.path("id").asLong())
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteBody(secondSupplierId, "1.90", true, true))))
                .andExpect(status().isOk());
        assertThatStandardCost("1.90");
    }

    @Test
    void rejectsQuoteCreationForViewOnlyUser() throws Exception {
        mvc.perform(post("/api/skus/{skuId}/supplier-quotes", skuId)
                        .header("Authorization", bearer(viewToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteBody(firstSupplierId, "2.20", false, false))))
                .andExpect(status().isForbidden());
    }

    private JsonNode createQuote(long supplierId, String purchasePrice, boolean defaultQuote) throws Exception {
        var response = mvc.perform(post("/api/skus/{skuId}/supplier-quotes", skuId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteBody(supplierId, purchasePrice, defaultQuote, false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private Map<String, Object> quoteBody(
            long supplierId,
            String purchasePrice,
            boolean defaultQuote,
            boolean syncStandardCost
    ) {
        var body = new LinkedHashMap<String, Object>();
        body.put("supplierId", supplierId);
        body.put("supplierItemNo", "SUP-ITEM");
        body.put("purchasePrice", purchasePrice);
        body.put("minPurchaseQuantity", "1");
        body.put("defaultQuote", defaultQuote);
        body.put("syncStandardCost", syncStandardCost);
        return body;
    }

    private long supplier(String no, String name) {
        jdbc.update("insert into supplier (supplier_no, supplier_name, status, created_at, updated_at) "
                + "values (?, ?, 'enabled', now(3), now(3))", no, name);
        return jdbc.queryForObject("select id from supplier where supplier_no = ?", Long.class, no);
    }

    private void assertThatStandardCost(String expected) {
        var cost = jdbc.queryForObject("select standard_cost from product_sku where id = ?", java.math.BigDecimal.class, skuId);
        org.assertj.core.api.Assertions.assertThat(cost).isEqualByComparingTo(expected);
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new UserAccount("13900000005", "unused", true, true,
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
