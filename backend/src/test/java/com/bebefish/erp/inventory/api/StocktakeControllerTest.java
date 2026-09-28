package com.bebefish.erp.inventory.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class StocktakeControllerTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private long warehouseId;
    private long firstSkuId;
    private long secondSkuId;

    @BeforeEach
    void setUp() {
        warehouseId = IDS.incrementAndGet();
        firstSkuId = IDS.incrementAndGet();
        secondSkuId = IDS.incrementAndGet();
        jdbc.update("""
                insert into warehouse (
                    id, warehouse_no, warehouse_name, address, is_default, status, remark,
                    created_at, updated_at
                ) values (?, ?, '杭州测试仓', null, false, 'enabled', null, current_timestamp, current_timestamp)
                """, warehouseId, "WH-STOCKTAKE-" + warehouseId);
        jdbc.update("""
                insert into inventory_balance (
                    warehouse_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, ?, 12, 0, current_timestamp, current_timestamp),
                         (?, ?, 8, 0, current_timestamp, current_timestamp)
                """, warehouseId, firstSkuId, warehouseId, secondSkuId);
        jdbc.update("insert into warehouse_layout (warehouse_id, revision, layout_json) values (?, 1, ?)",
                warehouseId, """
                        {"schemaVersion":1,"completed":true,
                         "structure":{"zones":[{"id":"zone-a","label":"A区"}]},
                         "palletGroups":[
                           {"id":"pile-a01","code":"P001","name":"地面货堆 P001"},
                           {"id":"pile-a02","code":"P002","name":"地面货堆 P002"}
                         ]}
                        """);
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, null, 'pile-a01', ?, 12, 0, current_timestamp, current_timestamp),
                         (?, 'zone-a', 'pile-a02', ?, 8, 0, current_timestamp, current_timestamp)
                """, warehouseId, firstSkuId, warehouseId, secondSkuId);
    }

    @AfterEach
    void tearDown() {
        if (tableExists("inventory_stocktake_item")) {
            jdbc.update("delete from inventory_stocktake_item where task_id in (select id from inventory_stocktake_task where warehouse_id = ?)", warehouseId);
            jdbc.update("delete from inventory_stocktake_task where warehouse_id = ?", warehouseId);
        }
        jdbc.update("delete from inventory_ledger where warehouse_id = ?", warehouseId);
        jdbc.update("delete from inventory_location_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from inventory_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse_layout where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse where id = ?", warehouseId);
    }

    @Test
    void createsWarehouseSnapshotSavesDraftAndSubmitsInitialCount() throws Exception {
        var editor = bearer(token("inventory:view", "inventory:edit", "warehouse:view"));
        var createResponse = mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"assigneeName":"张敏","blindCount":true}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("not_started"))
                .andExpect(jsonPath("$.data.warehouseName").value("杭州测试仓"))
                .andExpect(jsonPath("$.data.assigneeName").value("接口测试用户"))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a01')].zoneName").value(hasItem("全仓")))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a01')].palletLabel").value(hasItem("P001")))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a02')].zoneName").value(hasItem("A区")))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a02')].palletLabel").value(hasItem("P002")))
                .andExpect(jsonPath("$.data.items[0].bookQuantity").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        var task = objectMapper.readTree(createResponse).path("data");
        var taskId = task.path("id").asLong();
        var firstItemId = java.util.stream.StreamSupport.stream(task.path("items").spliterator(), false)
                .filter(item -> "pile-a01".equals(item.path("palletId").asText()))
                .findFirst().orElseThrow().path("id").asLong();
        var secondItemId = java.util.stream.StreamSupport.stream(task.path("items").spliterator(), false)
                .filter(item -> "pile-a02".equals(item.path("palletId").asText()))
                .findFirst().orElseThrow().path("id").asLong();

        mvc.perform(get("/api/inventory/stocktakes/{id}", taskId)
                        .header("Authorization", bearer(token("inventory:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].bookQuantity").value(nullValue()));

        mvc.perform(put("/api/inventory/stocktakes/{id}/draft", taskId)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"counts":[{"itemId":%d,"quantity":12}]}
                                """.formatted(firstItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.countedItems").value(1));

        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-initial", taskId)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"counts":[{"itemId":%d,"quantity":12},{"itemId":%d,"quantity":7}]}
                                """.formatted(firstItemId, secondItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_recount"))
                .andExpect(jsonPath("$.data.countedItems").value(2))
                .andExpect(jsonPath("$.data.differenceItems").value(1))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a02')].difference").value(hasItem(-1.0)));

        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-recount", taskId)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"counts":[{"itemId":%d,"quantity":7}]}
                                """.formatted(secondItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_approval"))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'pile-a02')].recountQuantity").value(hasItem(7.0)));

        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", taskId)
                        .header("Authorization", editor))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", taskId)
                        .header("Authorization", bearer(token("inventory:view", "inventory:approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"));

        var adjustedQuantity = jdbc.queryForObject(
                "select quantity from inventory_balance where warehouse_id=? and sku_id=?",
                java.math.BigDecimal.class,
                warehouseId,
                secondSkuId
        );
        org.assertj.core.api.Assertions.assertThat(adjustedQuantity).isEqualByComparingTo("7");
        var ledgerSource = jdbc.queryForObject(
                "select source_type from inventory_ledger where warehouse_id=? and sku_id=? order by id desc limit 1",
                String.class,
                warehouseId,
                secondSkuId
        );
        org.assertj.core.api.Assertions.assertThat(ledgerSource).isEqualTo("stocktake");

        mvc.perform(get("/api/inventory/stocktakes")
                        .header("Authorization", bearer(token("inventory:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.completedThisMonth").value(1))
                .andExpect(jsonPath("$.data.tasks[0].taskNo").exists());
    }

    @Test
    void createsStocktakeForOnlyTheSelectedPiles() throws Exception {
        mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", bearer(token("inventory:view", "inventory:edit", "warehouse:view")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"blindCount":true,
                                 "palletIds":["pile-a02"]}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeLabel").value("指定货物堆 · 1堆"))
                .andExpect(jsonPath("$.data.assigneeName").value("接口测试用户"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].palletId").value("pile-a02"))
                .andExpect(jsonPath("$.data.items[0].palletLabel").value("P002"));
    }

    @Test
    void fullWarehouseSnapshotIncludesActualUnallocatedBalance() throws Exception {
        jdbc.update("update inventory_balance set quantity=15 where warehouse_id=? and sku_id=?",
                warehouseId, firstSkuId);

        mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", bearer(token("inventory:view", "inventory:edit", "warehouse:view")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"blindCount":false}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items[?(@.palletId == 'UNALLOCATED')].bookQuantity")
                        .value(hasItem(3.0)));
    }

    @Test
    void approvesCountedUnallocatedStockWithoutAStoredLocationRow() throws Exception {
        var editor = bearer(token("inventory:view", "inventory:edit", "warehouse:view"));
        jdbc.update("update inventory_balance set quantity=15 where warehouse_id=? and sku_id=?",
                warehouseId, firstSkuId);
        var response = mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"blindCount":false}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var task = objectMapper.readTree(response).path("data");
        var id = task.path("id").asLong();
        var counts = new java.util.ArrayList<String>();
        for (var item : task.path("items")) {
            var quantity = "UNALLOCATED".equals(item.path("palletId").asText()) ? 4
                    : item.path("bookQuantity").asInt();
            counts.add("{\"itemId\":" + item.path("id").asLong() + ",\"quantity\":" + quantity + "}");
        }
        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-initial", id)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("{\"counts\":[" + String.join(",", counts) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_recount"));
        var unallocatedId = java.util.stream.StreamSupport.stream(task.path("items").spliterator(), false)
                .filter(item -> "UNALLOCATED".equals(item.path("palletId").asText()))
                .findFirst().orElseThrow().path("id").asLong();
        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-recount", id)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("{\"counts\":[{\"itemId\":" + unallocatedId + ",\"quantity\":4}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", id)
                        .header("Authorization", bearer(token("inventory:view", "inventory:approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select quantity from inventory_balance where warehouse_id=? and sku_id=?",
                java.math.BigDecimal.class, warehouseId, firstSkuId
        )).isEqualByComparingTo("16");
    }

    @Test
    void rejectsApprovalWhenAnAdditionalSkuAppearsInSelectedPile() throws Exception {
        var editor = bearer(token("inventory:view", "inventory:edit", "warehouse:view"));
        var response = mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"blindCount":false,"palletIds":["pile-a01"]}
                                """.formatted(warehouseId)))
                .andReturn().getResponse().getContentAsString();
        var task = objectMapper.readTree(response).path("data");
        var id = task.path("id").asLong();
        var itemId = task.path("items").get(0).path("id").asLong();
        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-initial", id)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("{\"counts\":[{\"itemId\":" + itemId + ",\"quantity\":12}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_approval"));
        jdbc.update("update inventory_location_balance set quantity=7 where warehouse_id=? and pallet_id='pile-a02' and sku_id=?",
                warehouseId, secondSkuId);
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, null, 'pile-a01', ?, 1, 0, current_timestamp, current_timestamp)
                """, warehouseId, secondSkuId);
        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", id)
                        .header("Authorization", bearer(token("inventory:view", "inventory:approve"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCKTAKE_SNAPSHOT_STALE"));
    }

    @Test
    void rejectsApprovalWhenUnallocatedBalanceChangesAfterSnapshot() throws Exception {
        var editor = bearer(token("inventory:view", "inventory:edit", "warehouse:view"));
        jdbc.update("update inventory_balance set quantity=15 where warehouse_id=? and sku_id=?",
                warehouseId, firstSkuId);
        var response = mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("{\"warehouseId\":" + warehouseId + ",\"blindCount\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var task = objectMapper.readTree(response).path("data");
        var counts = new java.util.ArrayList<String>();
        for (var item : task.path("items")) {
            counts.add("{\"itemId\":" + item.path("id").asLong()
                    + ",\"quantity\":" + item.path("bookQuantity").asInt() + "}");
        }
        var id = task.path("id").asLong();
        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-initial", id)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("{\"counts\":[" + String.join(",", counts) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_approval"));

        jdbc.update("update inventory_balance set quantity=16 where warehouse_id=? and sku_id=?",
                warehouseId, firstSkuId);
        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", id)
                        .header("Authorization", bearer(token("inventory:view", "inventory:approve"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCKTAKE_SNAPSHOT_STALE"));
    }

    @Test
    void approvesSelectedPileWhenTheSameSkuAlsoExistsOnAnotherPile() throws Exception {
        var editor = bearer(token("inventory:view", "inventory:edit", "warehouse:view"));
        jdbc.update("update inventory_balance set quantity=13 where warehouse_id=? and sku_id=?",
                warehouseId, secondSkuId);
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no, created_at, updated_at
                ) values (?, 'zone-a', 'pile-a03', ?, 5, 0, current_timestamp, current_timestamp)
                """, warehouseId, secondSkuId);

        var createResponse = mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"blindCount":true,"palletIds":["pile-a02"]}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var task = objectMapper.readTree(createResponse).path("data");
        var taskId = task.path("id").asLong();
        var itemId = task.path("items").get(0).path("id").asLong();

        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-initial", taskId)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"counts":[{"itemId":%d,"quantity":7}]}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_recount"));

        mvc.perform(post("/api/inventory/stocktakes/{id}/submit-recount", taskId)
                        .header("Authorization", editor)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"counts":[{"itemId":%d,"quantity":7}]}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("awaiting_approval"));

        mvc.perform(post("/api/inventory/stocktakes/{id}/approve", taskId)
                        .header("Authorization", bearer(token("inventory:view", "inventory:approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select quantity from inventory_balance where warehouse_id=? and sku_id=?",
                java.math.BigDecimal.class, warehouseId, secondSkuId
        )).isEqualByComparingTo("12");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select quantity from inventory_location_balance where warehouse_id=? and pallet_id='pile-a02' and sku_id=?",
                java.math.BigDecimal.class, warehouseId, secondSkuId
        )).isEqualByComparingTo("7");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select quantity from inventory_location_balance where warehouse_id=? and pallet_id='pile-a03' and sku_id=?",
                java.math.BigDecimal.class, warehouseId, secondSkuId
        )).isEqualByComparingTo("5");
    }

    @Test
    void viewOnlyUserCannotCreateOrCountStocktakes() throws Exception {
        mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", bearer(token("inventory:view")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"warehouseId":%d,"assigneeName":"张敏","blindCount":true}
                                """.formatted(warehouseId)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/inventory/stocktakes")
                        .header("Authorization", bearer(token("inventory:view", "inventory:edit")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"warehouseId\":" + warehouseId + ",\"blindCount\":true}"))
                .andExpect(status().isForbidden());
    }

    private boolean tableExists(String name) {
        var count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name)=lower(?)",
                Integer.class,
                name
        );
        return count != null && count > 0;
    }

    private String token(String... permissions) {
        return com.bebefish.erp.support.TestAuthTokens.issue(jdbc, tokenIssuer, "13900000014", permissions);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
