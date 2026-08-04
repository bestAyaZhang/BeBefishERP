package com.bebefish.erp.inventory.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.assertj.core.api.Assertions;
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
class StockAdjustmentControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndConfirmsAdjustmentOnlyOnce() throws Exception {
        var warehouseId = 930_000_000L + Math.abs(System.nanoTime() % 100_000_000L);
        var skuId = warehouseId + 1;
        var token = bearer(token("inventory:view", "inventory:edit"));

        var createResponse = mvc.perform(post("/api/inventory/adjustments")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"reason":"期初盘点","items":[{"skuId":%d,"quantityDelta":12}]}
                                """.formatted(warehouseId, skuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
        var adjustmentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mvc.perform(post("/api/inventory/adjustments/{id}/confirm", adjustmentId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmed"));

        mvc.perform(post("/api/inventory/adjustments/{id}/confirm", adjustmentId)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADJUSTMENT_NOT_DRAFT"));

        var quantity = jdbc.queryForObject(
                "select quantity from inventory_balance where warehouse_id=? and sku_id=?",
                java.math.BigDecimal.class,
                warehouseId,
                skuId
        );
        Assertions.assertThat(quantity).isEqualByComparingTo("12");
    }

    @Test
    void viewOnlyUserCannotConfirmAdjustment() throws Exception {
        var token = bearer(token("inventory:view"));

        mvc.perform(post("/api/inventory/adjustments/1/confirm")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new UserAccount(
                "13900000003", "unused", true, true, List.of("TESTER"), List.of(permissions)
        ), "inventory-test").accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
