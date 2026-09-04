package com.bebefish.erp.inventory.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.inventory.application.InventoryChange;
import com.bebefish.erp.inventory.application.InventoryService;
import com.bebefish.erp.inventory.application.InventorySource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryQueryControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void queriesBalanceAndLedgerByWarehouseAndSku() throws Exception {
        var warehouseId = 920_000_000L + Math.abs(System.nanoTime() % 100_000_000L);
        var skuId = warehouseId + 1;
        inventoryService.increase(
                warehouseId,
                List.of(new InventoryChange(skuId, new BigDecimal("12"))),
                new InventorySource("purchase", warehouseId, "PI-QUERY-" + warehouseId),
                "13800138000"
        );
        var token = "Bearer " + tokenIssuer.issue(new AuthenticatedUser(
                "13900000003", List.of("TESTER"), List.of("inventory:view")
        ), "inventory-query-test").accessToken();

        mvc.perform(get("/api/inventory/balances")
                        .header("Authorization", token)
                        .accept(APPLICATION_JSON)
                        .param("warehouseId", String.valueOf(warehouseId))
                        .param("keyword", String.valueOf(skuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].skuId").value(skuId))
                .andExpect(jsonPath("$.data.records[0].quantity").value(12));

        mvc.perform(get("/api/inventory/ledger")
                        .header("Authorization", token)
                        .accept(APPLICATION_JSON)
                        .param("warehouseId", String.valueOf(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].skuId").value(skuId))
                .andExpect(jsonPath("$.data.records[0].sourceNo").value("PI-QUERY-" + warehouseId))
                .andExpect(jsonPath("$.data.records[0].afterQuantity").value(12));
    }
}
