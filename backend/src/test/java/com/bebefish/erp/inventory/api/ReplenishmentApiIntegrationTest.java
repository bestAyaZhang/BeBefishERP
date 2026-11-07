package com.bebefish.erp.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReplenishmentApiIntegrationTest {
    private static final String API = "/api/inventory/replenishment";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenIssuer tokens;

    @Test
    void viewerCanReadButOnlyEditorCanUpsertSharedFallbackPolicy() throws Exception {
        var prefix = "REPL-" + UUID.randomUUID().toString().substring(0, 12);
        var skuCode = prefix + "-SKU";
        long skuId = insertSku(prefix, skuCode);
        long warehouseId = insertWarehouse(prefix, false);
        jdbc.update("""
                insert into inventory_balance (warehouse_id, sku_id, quantity, created_at, updated_at)
                values (?, ?, 30, now(3), now(3))
                """, warehouseId, skuId);
        var viewer = "Bearer " + TestAuthTokens.issue(jdbc, tokens, "13909004031", "inventory:view");
        var editor = "Bearer " + TestAuthTokens.issue(jdbc, tokens, "13909004032", "inventory:view", "inventory:edit");

        mvc.perform(get(API).header(AUTHORIZATION, viewer)
                        .param("keyword", skuCode).param("warehouseId", Long.toString(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].source").value("pending"));

        mvc.perform(put(API + "/{skuId}", skuId).header(AUTHORIZATION, viewer)
                        .contentType(APPLICATION_JSON).content(fallback(47)))
                .andExpect(status().isForbidden());
        for (int point : new int[]{48, 49}) {
            mvc.perform(put(API + "/{skuId}", skuId).header(AUTHORIZATION, editor)
                            .contentType(APPLICATION_JSON).content(fallback(point)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get(API).header(AUTHORIZATION, viewer).param("keyword", skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].source").value("fallback"))
                .andExpect(jsonPath("$.data.items[0].safety").value(10))
                .andExpect(jsonPath("$.data.items[0].point").value(49))
                .andExpect(jsonPath("$.data.items[0].status").value("warning"));
    }

    @Test
    void savesMixedScopesAndLeavesAllPoliciesUnchangedIfALaterScopeIsStale() throws Exception {
        var prefix = "REPL-" + UUID.randomUUID().toString().substring(0, 12);
        long skuId = insertSku(prefix, prefix + "-SKU");
        long sharedWarehouseId = insertWarehouse(prefix + "-SH", false);
        long independentWarehouseId = insertWarehouse(prefix + "-IN", true);
        var viewer = "Bearer " + TestAuthTokens.issue(jdbc, tokens, "13909004033", "inventory:view");
        var editor = "Bearer " + TestAuthTokens.issue(jdbc, tokens, "13909004034", "inventory:view", "inventory:edit");
        var mixedTargets = """
                [{"skuId":%d,"warehouseId":%d},{"skuId":%d,"warehouseId":null}]
                """.formatted(skuId, independentWarehouseId, skuId);

        mvc.perform(put(API + "/batch").header(AUTHORIZATION, viewer)
                        .contentType(APPLICATION_JSON).content(batch(mixedTargets, 48)))
                .andExpect(status().isForbidden());
        mvc.perform(put(API + "/batch").header(AUTHORIZATION, editor)
                        .contentType(APPLICATION_JSON).content(batch(mixedTargets, 48)))
                .andExpect(status().isOk());
        assertThat(savedPoints(skuId)).containsExactly(48L, 48L);

        var staleTargets = """
                [{"skuId":%d,"warehouseId":null},{"skuId":%d,"warehouseId":%d}]
                """.formatted(skuId, skuId, sharedWarehouseId);
        mvc.perform(put(API + "/batch").header(AUTHORIZATION, editor)
                        .contentType(APPLICATION_JSON).content(batch(staleTargets, 72)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLENISHMENT_SCOPE_CHANGED"));
        assertThat(savedPoints(skuId)).containsExactly(48L, 48L);
    }

    private List<Long> savedPoints(long skuId) {
        return jdbc.queryForList("""
                select manual_point from inventory_replenishment_policy
                where sku_id = ? order by scope_warehouse_id
                """, Long.class, skuId);
    }

    private long insertWarehouse(String code, boolean independent) {
        jdbc.update("""
                insert into warehouse (warehouse_no, warehouse_name, status, independent_replenishment, created_at, updated_at)
                values (?, ?, 'enabled', ?, now(3), now(3))
                """, code, code, independent);
        return jdbc.queryForObject("select id from warehouse where warehouse_no = ?", Long.class, code);
    }

    private long insertSku(String prefix, String skuCode) {
        jdbc.update("""
                insert into product_category (category_code, category_name, level_no, sort_order, status, created_at, updated_at)
                values (?, ?, 1, 0, 'enabled', now(3), now(3))
                """, prefix, prefix);
        long categoryId = jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, prefix);
        jdbc.update("""
                insert into product_spu (product_code, item_no, product_name, category_id, product_type, status, created_at, updated_at)
                values (?, ?, ?, ?, 'simple', 'enabled', now(3), now(3))
                """, prefix, prefix, prefix, categoryId);
        long productId = jdbc.queryForObject("select id from product_spu where product_code = ?", Long.class, prefix);
        jdbc.update("""
                insert into product_sku (product_id, sku_code, sku_name, sales_unit, is_default, status, created_at, updated_at)
                values (?, ?, '透明款', '只', true, 'enabled', now(3), now(3))
                """, productId, skuCode);
        return jdbc.queryForObject("select id from product_sku where sku_code = ?", Long.class, skuCode);
    }

    private String fallback(int point) {
        return """
                {"mode":"auto","bufferDays":5,"leadDays":8,"manualSafety":10,"manualPoint":%d}
                """.formatted(point);
    }

    private String batch(String targets, int point) {
        return """
                {"targets":%s,"config":%s}
                """.formatted(targets, fallback(point));
    }
}
