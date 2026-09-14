package com.bebefish.erp.inventory.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseInventoryLayoutControllerTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    private long warehouseId;
    private long skuId;

    @BeforeEach
    void setUp() {
        warehouseId = IDS.incrementAndGet();
        skuId = IDS.incrementAndGet();
        jdbc.update("""
                insert into warehouse (
                    id, warehouse_no, warehouse_name, address, is_default, status, remark,
                    created_at, updated_at
                ) values (?, ?, ?, null, false, 'enabled', null, current_timestamp, current_timestamp)
                """, warehouseId, "WH-API-ALLOC-" + warehouseId,
                "Allocation endpoint test " + warehouseId);
        jdbc.update("insert into warehouse_layout (warehouse_id, revision, layout_json) values (?, 1, ?)",
                warehouseId, layoutJson());
        jdbc.update("""
                insert into inventory_balance (
                    warehouse_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, ?, 100, 0, current_timestamp, current_timestamp)
                """, warehouseId, skuId);
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                    created_at, updated_at
                ) values (?, null, 'UNALLOCATED', ?, 100, 0, current_timestamp, current_timestamp)
                """, warehouseId, skuId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from inventory_location_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from inventory_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse_layout where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse where id = ?", warehouseId);
    }

    @Test
    void rejectsPileAllocationForAViewOnlyUser() throws Exception {
        mvc.perform(post("/api/warehouses/{id}/inventory-layout/pile-allocations", warehouseId)
                        .header("Authorization", bearer(token("inventory:view")))
                        .contentType(APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allocatesWarehouseStockForAnInventoryEditor() throws Exception {
        mvc.perform(post("/api/warehouses/{id}/inventory-layout/pile-allocations", warehouseId)
                        .header("Authorization", bearer(token("inventory:edit")))
                        .contentType(APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnits").value(100))
                .andExpect(jsonPath("$.data.placedUnits").value(24))
                .andExpect(jsonPath("$.data.allocations[?(@.palletId == 'pallet-c018' && @.skuId == %d)].units"
                        .formatted(skuId), hasItem(24)));
    }

    @ParameterizedTest(name = "rejects invalid pile allocation request: {0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidPileAllocationRequests(String scenario, String request) throws Exception {
        mvc.perform(post("/api/warehouses/{id}/inventory-layout/pile-allocations", warehouseId)
                        .header("Authorization", bearer(token("inventory:edit")))
                        .contentType(APPLICATION_JSON)
                        .content(request.formatted(skuId)))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("fractional sku id",
                        "{\"palletId\":\"pallet-c018\",\"skuId\":%d.5,\"units\":24}"),
                Arguments.of("fractional units",
                        "{\"palletId\":\"pallet-c018\",\"skuId\":%d,\"units\":1.5}"),
                Arguments.of("zero units",
                        "{\"palletId\":\"pallet-c018\",\"skuId\":%d,\"units\":0}"),
                Arguments.of("blank pallet id",
                        "{\"palletId\":\"   \",\"skuId\":%d,\"units\":24}"),
                Arguments.of("missing sku id",
                        "{\"palletId\":\"pallet-c018\",\"units\":24}")
        );
    }

    private String validRequest() {
        return "{\"palletId\":\"pallet-c018\",\"skuId\":%d,\"units\":24}".formatted(skuId);
    }

    private String token(String... permissions) {
        return com.bebefish.erp.support.TestAuthTokens.issue(
                jdbc, tokenIssuer, "13900000014", permissions);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String layoutJson() {
        return """
                {"schemaVersion":1,"structure":{"outline":{"nodes":[]},"partitions":[],"doors":[],
                "zones":[{"id":"zone-storage","left":10,"top":10,"width":30,"height":30}]},
                "palletGroups":[{"id":"pallet-c018","left":20,"top":20,"width":4,"height":4}],
                "completed":true}
                """;
    }
}
