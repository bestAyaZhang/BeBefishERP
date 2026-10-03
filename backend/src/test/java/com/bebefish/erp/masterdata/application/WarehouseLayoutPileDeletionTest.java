package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.WarehousePileAllocationService;
import com.bebefish.erp.inventory.application.WarehousePileReleaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WarehouseLayoutPileDeletionTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired private WarehouseLayoutService layouts;
    @Autowired private WarehousePileAllocationService pileAllocations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private long warehouseId;
    private long skuId;
    private Long secondSkuId;

    @BeforeEach
    void setUp() {
        warehouseId = IDS.incrementAndGet();
        skuId = IDS.incrementAndGet();
        secondSkuId = null;
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "planner", "unused", "warehouse:edit", "inventory:edit"));
        jdbc.update("""
                insert into warehouse (
                    id, warehouse_no, warehouse_name, address, is_default, status, remark,
                    created_at, updated_at
                ) values (?, ?, ?, null, false, 'enabled', null, current_timestamp, current_timestamp)
                """, warehouseId, "WH-DELETE-" + warehouseId, "Pile delete test " + warehouseId);
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
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                    created_at, updated_at
                ) values (?, null, 'UNALLOCATED', ?, 76, 0, current_timestamp, current_timestamp)
                """, warehouseId, skuId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from inventory_location_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from inventory_balance where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse_layout where warehouse_id = ?", warehouseId);
        jdbc.update("delete from warehouse where id = ?", warehouseId);
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletionWithoutConfirmationRollsBack() throws Exception {
        var changed = mapper.readTree(layoutJson(false));

        assertThatThrownBy(() -> layouts.save(warehouseId, new WarehouseLayoutService.Layout(1, changed)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("PILE_CONFIRMATION_REQUIRED"));

        assertThat(layouts.load(warehouseId).revision()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("24");
        assertThat(jdbc.queryForObject("select quantity from inventory_balance where warehouse_id = ? and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("100");
    }

    @Test
    void deletingAllocatedPileReleasesPositiveAndZeroLinksWithoutChangingBalance() throws Exception {
        secondSkuId = IDS.incrementAndGet();
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                    created_at, updated_at
                ) values (?, null, 'pallet-c018', ?, 0, 0, current_timestamp, current_timestamp)
                """, warehouseId, secondSkuId);
        var saved = layouts.save(warehouseId, deletionCommand(List.of(confirmation(
                new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")),
                new WarehousePileReleaseService.AllocationSnapshot(secondSkuId, BigDecimal.ZERO)))));

        assertThat(saved.revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018'", Integer.class, warehouseId))
                .isZero();
        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'UNALLOCATED' and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select quantity from inventory_balance where warehouse_id = ? and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("100");
    }

    @Test
    void newAllocationAfterConfirmationConflicts() throws Exception {
        var acknowledged = deletionCommand(List.of(confirmation(
                new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")))));
        jdbc.update("update inventory_location_balance set quantity = 25 where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?", warehouseId, skuId);
        jdbc.update("update inventory_location_balance set quantity = 75 where warehouse_id = ? and pallet_id = 'UNALLOCATED' and sku_id = ?", warehouseId, skuId);

        assertThatThrownBy(() -> layouts.save(warehouseId, acknowledged))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("PILE_ALLOCATIONS_CHANGED"));
        assertThat(layouts.load(warehouseId).revision()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("25");
    }

    @Test
    void missingInventoryEditPermissionRejectsLinkedPile() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "planner", "unused", "warehouse:edit"));
        assertThatThrownBy(() -> layouts.save(warehouseId, deletionCommand(List.of(confirmation(
                new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")))))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.status().value()).isEqualTo(403));
        assertThat(layouts.load(warehouseId).revision()).isEqualTo(1);
    }

    @Test
    void changedAllocationConflictsBeforePermissionCheck() throws Exception {
        var acknowledged = deletionCommand(List.of(confirmation(
                new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")))));
        jdbc.update("update inventory_location_balance set quantity = 25 where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?", warehouseId, skuId);
        jdbc.update("update inventory_location_balance set quantity = 75 where warehouse_id = ? and pallet_id = 'UNALLOCATED' and sku_id = ?", warehouseId, skuId);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "planner", "unused", "warehouse:edit"));

        assertThatThrownBy(() -> layouts.save(warehouseId, acknowledged))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("PILE_ALLOCATIONS_CHANGED"));
        assertThat(layouts.load(warehouseId).revision()).isEqualTo(1);
    }

    @Test
    void emptyPileNeedsOnlyWarehouseEdit() throws Exception {
        jdbc.update("delete from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018'", warehouseId);
        jdbc.update("update inventory_location_balance set quantity = 100 where warehouse_id = ? and pallet_id = 'UNALLOCATED' and sku_id = ?", warehouseId, skuId);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "planner", "unused", "warehouse:edit"));

        assertThat(layouts.save(warehouseId, new WarehouseLayoutService.Layout(1, mapper.readTree(layoutJson(false)))).revision())
                .isEqualTo(2);
    }

    @Test
    void duplicateConfirmationIsRejected() throws Exception {
        var item = confirmation(new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")));
        assertThatThrownBy(() -> layouts.save(warehouseId, deletionCommand(List.of(item, item))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_PILE_RELEASE_CONFIRMATION"));
        assertThat(layouts.load(warehouseId).revision()).isEqualTo(1);
    }

    @Test
    void staleRevisionCannotReleaseStock() throws Exception {
        var stale = new WarehouseLayoutService.SaveCommand(0, mapper.readTree(layoutJson(false)),
                List.of(confirmation(new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")))));
        assertThatThrownBy(() -> layouts.save(warehouseId, stale))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("LAYOUT_CONFLICT"));
        assertThat(jdbc.queryForObject("select quantity from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                .isEqualByComparingTo("24");
    }

    @Test
    void concurrentAllocationCannotLeaveOrphanLocation() throws Exception {
        var acknowledged = deletionCommand(List.of(confirmation(
                new WarehousePileReleaseService.AllocationSnapshot(skuId, new BigDecimal("24")))));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                        "planner", "unused", "warehouse:edit", "inventory:edit"));
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                    layouts.save(warehouseId, acknowledged);
                    return "saved";
                } catch (BusinessException error) {
                    return error.code();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var allocation = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                try {
                    pileAllocations.allocate(warehouseId,
                            new WarehousePileAllocationService.Command("pallet-c018", skuId, 1));
                    return "allocated";
                } catch (BusinessException error) {
                    return error.code();
                }
            });
            start.countDown();
            var deleteResult = deletion.get(15, TimeUnit.SECONDS);
            var allocationResult = allocation.get(15, TimeUnit.SECONDS);
            var pileExists = layouts.load(warehouseId).document().path("palletGroups").size() == 1;
            var placedCount = jdbc.queryForObject("select count(*) from inventory_location_balance where warehouse_id = ? and pallet_id = 'pallet-c018'", Integer.class, warehouseId);
            if (pileExists) {
                assertThat(deleteResult).isEqualTo("PILE_ALLOCATIONS_CHANGED");
                assertThat(allocationResult).isEqualTo("allocated");
                assertThat(placedCount).isEqualTo(1);
            } else {
                assertThat(deleteResult).isEqualTo("saved");
                assertThat(allocationResult).isEqualTo("PALLET_NOT_FOUND");
                assertThat(placedCount).isZero();
            }
            assertThat(jdbc.queryForObject("select quantity from inventory_balance where warehouse_id = ? and sku_id = ?", BigDecimal.class, warehouseId, skuId))
                    .isEqualByComparingTo("100");
        }
    }

    private WarehouseLayoutService.SaveCommand deletionCommand(
            List<WarehousePileReleaseService.ReleaseConfirmation> confirmations) throws Exception {
        return new WarehouseLayoutService.SaveCommand(1, mapper.readTree(layoutJson(false)), confirmations);
    }

    private WarehousePileReleaseService.ReleaseConfirmation confirmation(
            WarehousePileReleaseService.AllocationSnapshot... snapshots) {
        return new WarehousePileReleaseService.ReleaseConfirmation("pallet-c018", List.of(snapshots));
    }

    private String layoutJson(boolean includePile) {
        return """
                {"schemaVersion":1,"structure":{"outline":{"nodes":[]},"partitions":[],"doors":[],"zones":[]},
                "palletGroups":%s,"completed":true}
                """.formatted(includePile ? "[{\"id\":\"pallet-c018\",\"left\":20,\"top\":20,\"width\":4,\"height\":4}]" : "[]");
    }
}
