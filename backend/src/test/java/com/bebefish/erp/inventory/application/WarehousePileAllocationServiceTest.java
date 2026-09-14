package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WarehousePileAllocationServiceTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private WarehousePileAllocationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private NamedParameterJdbcTemplate namedJdbc;

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
                """, warehouseId, "WH-ALLOC-" + warehouseId, "Allocation test " + warehouseId);
        insertLayout(true, true, true);
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
    void allocatesWarehouseStockToACompletedPileWithoutChangingTotalInventory() {
        var result = service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 24));

        assertThat(result.totalUnits()).isEqualByComparingTo("100");
        assertThat(result.placedUnits()).isEqualByComparingTo("24");
        assertThat(result.unallocatedUnits()).isEqualByComparingTo("76");
        assertThat(result.allocations()).anySatisfy(item -> {
            assertThat(item.zoneId()).isEqualTo("zone-storage");
            assertThat(item.palletId()).isEqualTo("pallet-c018");
            assertThat(item.skuId()).isEqualTo(skuId);
            assertThat(item.units()).isEqualByComparingTo("24");
        });
        assertThat(quantity("pallet-c018")).isEqualByComparingTo("24");
        assertThat(quantity("UNALLOCATED")).isEqualByComparingTo("76");
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void incrementsAnExistingPileAllocationAndReconcilesTheUnallocatedRow() {
        service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 24));

        var result = service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 6));

        assertThat(result.totalUnits()).isEqualByComparingTo("100");
        assertThat(result.placedUnits()).isEqualByComparingTo("30");
        assertThat(result.unallocatedUnits()).isEqualByComparingTo("70");
        assertThat(quantity("pallet-c018")).isEqualByComparingTo("30");
        assertThat(quantity("UNALLOCATED")).isEqualByComparingTo("70");
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveAllocationUnits(long units) {
        assertThatThrownBy(() -> service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, units)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_PILE_ALLOCATION"));
    }

    @Test
    void rejectsAnAllocationLargerThanAvailableInventory() {
        assertThatThrownBy(() -> service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 101)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("INSUFFICIENT_UNALLOCATED_INVENTORY"));

        assertThat(quantity("UNALLOCATED")).isEqualByComparingTo("100");
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void requiresACompletedWarehousePlan() {
        replaceLayout(false, true, true);

        assertThatThrownBy(() -> service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("PLANNING_REQUIRED"));
    }

    @Test
    void rejectsAnUnknownPile() {
        assertThatThrownBy(() -> service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-missing", skuId, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("PALLET_NOT_FOUND"));
    }

    @Test
    void rejectsASkuWithoutAPositiveWarehouseBalance() {
        jdbc.update("delete from inventory_location_balance where warehouse_id = ? and sku_id = ?",
                warehouseId, skuId);
        jdbc.update("delete from inventory_balance where warehouse_id = ? and sku_id = ?",
                warehouseId, skuId);

        assertThatThrownBy(() -> service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SKU_BALANCE_NOT_FOUND"));
    }

    @Test
    void storesNoZoneWhenThePileCenterIsOutsideEveryOrdinaryZone() {
        replaceLayout(true, true, false);

        var result = service.allocate(warehouseId,
                new WarehousePileAllocationService.Command("pallet-c018", skuId, 1));

        assertThat(result.allocations())
                .filteredOn(item -> item.palletId().equals("pallet-c018"))
                .singleElement()
                .satisfies(item -> assertThat(item.zoneId()).isNull());
        assertThat(jdbc.queryForObject("""
                select zone_id from inventory_location_balance
                where warehouse_id = ? and pallet_id = 'pallet-c018' and sku_id = ?
                """, String.class, warehouseId, skuId)).isNull();
    }

    @Test
    void concurrentAllocationsCannotExceedTheAuthoritativeWarehouseBalance() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AllocationResult> first = executor.submit(() -> allocateConcurrently(ready, start));
            Future<AllocationResult> second = executor.submit(() -> allocateConcurrently(ready, start));
            ready.await();
            start.countDown();

            var results = java.util.List.of(get(first), get(second));
            assertThat(results).filteredOn(AllocationResult::successful).hasSize(1);
            assertThat(results).filteredOn(result -> !result.successful())
                    .extracting(AllocationResult::errorCode)
                    .containsExactly("INSUFFICIENT_UNALLOCATED_INVENTORY");
        }

        var placed = jdbc.queryForObject("""
                select coalesce(sum(quantity), 0) from inventory_location_balance
                where warehouse_id = ? and sku_id = ? and pallet_id <> 'UNALLOCATED'
                """, BigDecimal.class, warehouseId, skuId);
        assertThat(placed).isLessThanOrEqualTo(balance());
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void laterConcurrentResponseIncludesTheAllocationCommittedWhileWaitingForTheBalanceLock()
            throws Exception {
        var layoutReads = new CountDownLatch(2);
        synchronizeLayoutReads(layoutReads);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<WarehouseInventoryLayoutView> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.allocate(warehouseId,
                        new WarehousePileAllocationService.Command("pallet-c018", skuId, 30));
            });
            Future<WarehouseInventoryLayoutView> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.allocate(warehouseId,
                        new WarehousePileAllocationService.Command("pallet-c019", skuId, 40));
            });
            ready.await();
            start.countDown();

            assertThat(java.util.List.of(getView(first), getView(second)))
                    .anySatisfy(view -> {
                        assertThat(view.totalUnits()).isEqualByComparingTo("100");
                        assertThat(view.placedUnits()).isEqualByComparingTo("70");
                        assertThat(view.unallocatedUnits()).isEqualByComparingTo("30");
                        assertThat(view.allocations()).anySatisfy(item -> {
                            assertThat(item.palletId()).isEqualTo("pallet-c018");
                            assertThat(item.units()).isEqualByComparingTo("30");
                        });
                        assertThat(view.allocations()).anySatisfy(item -> {
                            assertThat(item.palletId()).isEqualTo("pallet-c019");
                            assertThat(item.units()).isEqualByComparingTo("40");
                        });
                    });
        }

        assertThat(balance()).isEqualByComparingTo("100");
        assertThat(placed()).isEqualByComparingTo("70");
        assertThat(quantity("UNALLOCATED")).isEqualByComparingTo("30");
    }

    private AllocationResult allocateConcurrently(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            service.allocate(warehouseId,
                    new WarehousePileAllocationService.Command("pallet-c018", skuId, 60));
            return AllocationResult.success();
        } catch (BusinessException error) {
            return AllocationResult.failure(error.code());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private AllocationResult get(Future<AllocationResult> future) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } catch (ExecutionException error) {
            throw new AssertionError(error.getCause());
        }
    }

    private WarehouseInventoryLayoutView getView(Future<WarehouseInventoryLayoutView> future) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } catch (ExecutionException error) {
            throw new AssertionError(error.getCause());
        }
    }

    private void synchronizeLayoutReads(CountDownLatch layoutReads) {
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (invocation.<String>getArgument(0).contains("select layout_json")) {
                layoutReads.countDown();
                if (!layoutReads.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both allocation transactions did not read the layout");
                }
            }
            return result;
        }).when(namedJdbc).query(
                anyString(), anyMap(), ArgumentMatchers.<RowMapper<Object>>any()
        );
    }

    private BigDecimal quantity(String palletId) {
        return jdbc.queryForObject("""
                select quantity from inventory_location_balance
                where warehouse_id = ? and pallet_id = ? and sku_id = ?
                """, BigDecimal.class, warehouseId, palletId, skuId);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("""
                select quantity from inventory_balance where warehouse_id = ? and sku_id = ?
                """, BigDecimal.class, warehouseId, skuId);
    }

    private BigDecimal placed() {
        return jdbc.queryForObject("""
                select coalesce(sum(quantity), 0) from inventory_location_balance
                where warehouse_id = ? and sku_id = ? and pallet_id <> 'UNALLOCATED'
                """, BigDecimal.class, warehouseId, skuId);
    }

    private void insertLayout(boolean completed, boolean includePile, boolean includeOrdinaryZone) {
        jdbc.update("insert into warehouse_layout (warehouse_id, revision, layout_json) values (?, 1, ?)",
                warehouseId, layoutJson(completed, includePile, includeOrdinaryZone));
    }

    private void replaceLayout(boolean completed, boolean includePile, boolean includeOrdinaryZone) {
        jdbc.update("update warehouse_layout set layout_json = ? where warehouse_id = ?",
                layoutJson(completed, includePile, includeOrdinaryZone), warehouseId);
    }

    private String layoutJson(boolean completed, boolean includePile, boolean includeOrdinaryZone) {
        var ordinaryZone = includeOrdinaryZone
                ? ",{\"id\":\"zone-storage\",\"left\":10,\"top\":10,\"width\":30,\"height\":30}"
                : "";
        var pile = includePile
                ? """
                  {"id":"pallet-c018","left":20,"top":20,"width":4,"height":4},
                  {"id":"pallet-c019","left":30,"top":20,"width":4,"height":4}
                  """
                : "";
        return """
                {"schemaVersion":1,"structure":{"outline":{"nodes":[]},"partitions":[],"doors":[],
                "zones":[{"id":"zone-forklift","kind":"forklift","left":0,"top":0,"width":100,"height":100}%s]},
                "palletGroups":[%s],"completed":%s}
                """.formatted(ordinaryZone, pile, completed);
    }

    private record AllocationResult(boolean successful, String errorCode) {
        static AllocationResult success() {
            return new AllocationResult(true, null);
        }

        static AllocationResult failure(String errorCode) {
            return new AllocationResult(false, errorCode);
        }
    }
}
