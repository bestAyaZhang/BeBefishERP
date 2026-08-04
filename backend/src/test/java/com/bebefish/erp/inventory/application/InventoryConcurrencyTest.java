package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {
    @Autowired
    private InventoryService service;

    @Autowired
    private DataSource dataSource;

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
