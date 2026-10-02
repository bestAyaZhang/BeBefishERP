package com.bebefish.erp.masterdata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
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
class WarehouseLayoutDeletionControllerTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenIssuer tokenIssuer;
    @Autowired private ObjectMapper mapper;

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
                """, warehouseId, "WH-DELETE-API-" + warehouseId,
                "Pile delete API test " + warehouseId);
        jdbc.update("insert into warehouse_layout (warehouse_id, revision, layout_json) values (?, 1, ?)",
                warehouseId, layoutJson(true));
        jdbc.update("""
                insert into inventory_balance (
                    warehouse_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, ?, 100, 0, current_timestamp, current_timestamp)
                """, warehouseId, skuId);
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                    created_at, updated_at
                ) values (?, null, 'pallet-c018', ?, 24, 0, current_timestamp, current_timestamp)
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
    void rejectsReleaseWhenUserCanEditLayoutButNotInventory() throws Exception {
        mvc.perform(put("/api/warehouses/{id}/layout", warehouseId)
                        .header("Authorization", bearer("warehouse:edit"))
                        .contentType(APPLICATION_JSON)
                        .content(deleteRequest()))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018'", BigDecimal.class, warehouseId))
                .isEqualByComparingTo("24");
        assertThat(jdbc.queryForObject("select revision from warehouse_layout where warehouse_id = ?", Long.class, warehouseId))
                .isEqualTo(1);
    }

    @Test
    void releasesConfirmedPileWithBothPermissions() throws Exception {
        mvc.perform(put("/api/warehouses/{id}/layout", warehouseId)
                        .header("Authorization", bearer("warehouse:edit", "inventory:edit"))
                        .contentType(APPLICATION_JSON)
                        .content(deleteRequest()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018'", Integer.class, warehouseId))
                .isZero();
        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'UNALLOCATED'", BigDecimal.class, warehouseId))
                .isEqualByComparingTo("100");
    }

    @Test
    void rejectsLayoutSaveWithoutWarehouseEdit() throws Exception {
        mvc.perform(put("/api/warehouses/{id}/layout", warehouseId)
                        .header("Authorization", bearer("inventory:edit"))
                        .contentType(APPLICATION_JSON)
                        .content(deleteRequest()))
                .andExpect(status().isForbidden());
    }

    private String deleteRequest() throws Exception {
        var request = mapper.createObjectNode();
        request.put("revision", 1);
        request.set("document", mapper.readTree(layoutJson(false)));
        var released = request.putArray("releasedPileAllocations").addObject();
        released.put("palletId", "pallet-c018");
        var allocation = released.putArray("allocations").addObject();
        allocation.put("skuId", skuId);
        allocation.put("units", 24);
        return mapper.writeValueAsString(request);
    }

    private String bearer(String... permissions) {
        return "Bearer " + com.bebefish.erp.support.TestAuthTokens.issue(
                jdbc, tokenIssuer, "13900000029", permissions);
    }

    private String layoutJson(boolean includePile) {
        return """
                {"schemaVersion":1,"structure":{"outline":{"nodes":[]},"partitions":[],"doors":[],"zones":[]},
                "palletGroups":%s,"completed":true}
                """.formatted(includePile ? "[{\"id\":\"pallet-c018\",\"left\":20,\"top\":20,\"width\":4,\"height\":4}]" : "[]");
    }
}
