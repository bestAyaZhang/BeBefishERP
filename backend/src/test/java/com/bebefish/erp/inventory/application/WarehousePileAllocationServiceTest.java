package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.application.WarehouseLayoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class WarehousePileAllocationServiceTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private WarehousePileAllocationService service;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private WarehouseLayoutService layoutService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        jdbc.update("delete from inventory_ledger where warehouse_id = ?", warehouseId);
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
    @ValueSource(booleans = {false, true})
    void stockDecreaseConsumesOnlyUnallocatedAvailability(boolean adjustment) {
        service.allocate(warehouseId, new WarehousePileAllocationService.Command("pallet-c018", skuId, 80));
        var source = new InventorySource(adjustment ? "adjustment" : "sales", warehouseId, "OUT-" + warehouseId);
        if (adjustment) inventoryService.adjust(warehouseId,
                java.util.List.of(new InventoryChange(skuId, new BigDecimal("-20"))), source, "13800138000");
        else inventoryService.decrease(warehouseId,
                java.util.List.of(new InventoryChange(skuId, new BigDecimal("20"))), source, "13800138000");
        assertThat(balance()).isEqualByComparingTo("80");
        assertThat(placed()).isEqualByComparingTo("80");

        assertThatThrownBy(() -> {
            if (adjustment) inventoryService.adjust(warehouseId,
                    java.util.List.of(new InventoryChange(skuId, BigDecimal.ONE.negate())), source, "13800138000");
            else inventoryService.decrease(warehouseId,
                    java.util.List.of(new InventoryChange(skuId, BigDecimal.ONE)), source, "13800138000");
        }).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("INVENTORY_ALLOCATED_TO_PILES"));
        assertThat(balance()).isEqualByComparingTo("80");
        assertThat(quantity("pallet-c018")).isEqualByComparingTo("80");
        assertThat(jdbc.queryForObject("select count(*) from inventory_ledger where warehouse_id = ?",
                Long.class, warehouseId)).isEqualTo(1);
    }

    @Test
    void decreasesOrdinaryStockWithoutLocationRows() {
        jdbc.update("delete from inventory_location_balance where warehouse_id = ?", warehouseId);
        inventoryService.decrease(warehouseId, java.util.List.of(new InventoryChange(skuId, new BigDecimal("100"))),
                new InventorySource("sales", warehouseId, "OUT-" + warehouseId), "13800138000");
        assertThat(balance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allocationWaitsForLayoutSaveAndValidatesItsCommittedGeometry(boolean removePile) throws Exception {
        var savedButUncommitted = new CountDownLatch(1);
        var commitLayout = new CountDownLatch(1);
        var allocationStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            var sql = invocation.<String>getArgument(0);
            if (sql.contains("from warehouse ") || sql.contains("select layout_json")) allocationStarted.countDown();
            return invocation.callRealMethod();
        }).when(namedJdbc).query(anyString(), anyMap(), ArgumentMatchers.<RowMapper<Object>>any());
        var changedDocument = mapper.readTree(layoutJson(true, !removePile, true));
        if (!removePile) ((com.fasterxml.jackson.databind.node.ObjectNode) changedDocument.path("palletGroups").get(0)).put("left", 200);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var save = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                layoutService.save(warehouseId, new WarehouseLayoutService.Layout(1, changedDocument));
                savedButUncommitted.countDown();
                try {
                    if (!commitLayout.await(10, TimeUnit.SECONDS)) throw new AssertionError("Layout transaction not released");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return null;
            }));
            assertThat(savedButUncommitted.await(10, TimeUnit.SECONDS)).isTrue();
            var allocation = executor.submit(() -> service.allocate(warehouseId,
                    new WarehousePileAllocationService.Command("pallet-c018", skuId, 24)));
            assertThat(allocationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> allocation.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            commitLayout.countDown();
            save.get(10, TimeUnit.SECONDS);
            if (removePile) {
                assertThatThrownBy(() -> allocation.get(10, TimeUnit.SECONDS))
                        .isInstanceOfSatisfying(ExecutionException.class, error ->
                                assertThat(error.getCause()).isInstanceOfSatisfying(BusinessException.class,
                                        business -> assertThat(business.code()).isEqualTo("PALLET_NOT_FOUND")));
                assertThat(placed()).isEqualByComparingTo("0");
            } else {
                assertThat(allocation.get(10, TimeUnit.SECONDS).allocations())
                        .filteredOn(item -> item.palletId().equals("pallet-c018"))
                        .singleElement().satisfies(item -> assertThat(item.zoneId()).isNull());
                assertThat(placed()).isEqualByComparingTo("24");
            }
        } finally {
            commitLayout.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1})
    void rejectsNegativeAllocationUnits(long units) {
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
        var warehouseLockAttempts = new CountDownLatch(2);
        synchronizeWarehouseLockAttempts(warehouseLockAttempts);
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

    private void synchronizeWarehouseLockAttempts(CountDownLatch warehouseLockAttempts) {
        doAnswer(invocation -> {
            if (invocation.<String>getArgument(0).contains("from warehouse ")) {
                warehouseLockAttempts.countDown();
                if (!warehouseLockAttempts.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both allocation transactions did not attempt the warehouse lock");
                }
            }
            return invocation.callRealMethod();
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
