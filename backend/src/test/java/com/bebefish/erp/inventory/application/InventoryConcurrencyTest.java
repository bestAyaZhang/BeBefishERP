package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {
    @Autowired
    private InventoryService service;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void firstReceiptWaitsForTheWarehouseLockUsedByStocktakeApproval() throws Exception {
        var warehouseId = 900_000_000L + Math.abs(System.nanoTime() % 100_000_000L);
        var skuId = warehouseId + 1;
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into warehouse (
                    id, warehouse_no, warehouse_name, address, is_default, status, remark,
                    created_at, updated_at
                ) values (?, ?, '并发测试仓', null, false, 'enabled', null, current_timestamp, current_timestamp)
                """, warehouseId, "WH-LOCK-" + warehouseId);
        var lockHeld = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from warehouse where id=? for update", Long.class, warehouseId);
                lockHeld.countDown();
                try {
                    if (!releaseLock.await(5, TimeUnit.SECONDS)) throw new AssertionError("仓库锁未释放");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return null;
            }));
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            var receiptStarted = new CountDownLatch(1);
            var receipt = executor.submit(() -> {
                receiptStarted.countDown();
                service.increase(warehouseId,
                        List.of(new InventoryChange(skuId, BigDecimal.ONE)),
                        new InventorySource("test", warehouseId, "INV-LOCK-" + warehouseId),
                        "13800138000");
                return null;
            });
            assertThat(receiptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> receipt.get(300, TimeUnit.MILLISECONDS), TimeoutException.class)).isNotNull();
            } finally {
                releaseLock.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
            receipt.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject(
                    "select quantity from inventory_balance where warehouse_id=? and sku_id=?",
                    BigDecimal.class, warehouseId, skuId
            )).isEqualByComparingTo("1");
        } finally {
            releaseLock.countDown();
            jdbc.update("delete from inventory_ledger where warehouse_id=?", warehouseId);
            jdbc.update("delete from inventory_balance where warehouse_id=?", warehouseId);
            jdbc.update("delete from warehouse where id=?", warehouseId);
        }
    }

    @Test
    void concurrentDecreasesCannotCreateNegativeInventory() throws Exception {
        var warehouseId = 900_000_000L + Math.abs(System.nanoTime() % 100_000_000L);
        var skuId = warehouseId + 1;
        service.increase(
                warehouseId,
                List.of(new InventoryChange(skuId, new BigDecimal("10"))),
                new InventorySource("test", warehouseId, "INV-CONCURRENT-" + warehouseId),
                "13800138000"
        );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var tasks = Stream.of("u1", "u2")
                    .map(operator -> (Future<Result>) executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            service.decrease(
                                    warehouseId,
                                    List.of(new InventoryChange(skuId, new BigDecimal("7"))),
                                    new InventorySource("test-sales", warehouseId, "SO-CONCURRENT-" + operator),
                                    operator
                            );
                            return Result.success();
                        } catch (RuntimeException exception) {
                            return Result.failure(exception.getMessage());
                        }
                    }))
                    .toList();

            ready.await();
            start.countDown();
            var results = tasks.stream().map(this::get).toList();

            assertThat(results.stream().filter(Result::successful).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> !result.successful()).map(Result::message))
                    .contains("库存不足");
        }

        var quantity = new JdbcTemplate(dataSource).queryForObject(
                "select quantity from inventory_balance where warehouse_id = ? and sku_id = ?",
                BigDecimal.class,
                warehouseId,
                skuId
        );
        assertThat(quantity).isEqualByComparingTo("3");
    }

    private Result get(Future<Result> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        }
    }

    private record Result(boolean successful, String message) {
        static Result success() {
            return new Result(true, null);
        }

        static Result failure(String message) {
            return new Result(false, message);
        }
    }
}
