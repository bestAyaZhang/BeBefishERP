package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.WarehousePileReleaseService;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class WarehouseLayoutServiceConcurrencyTest {
    private static final long WAREHOUSE_ID = 7L;

    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private WarehouseLayoutService service;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE warehouse (id BIGINT NOT NULL PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE warehouse_layout (
                    warehouse_id BIGINT NOT NULL PRIMARY KEY,
                    revision BIGINT NOT NULL,
                    layout_json MEDIUMTEXT NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_layout_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
                )
                """);
        jdbc.update("INSERT INTO warehouse (id) VALUES (?)", WAREHOUSE_ID);
        jdbc.update("INSERT INTO warehouse_layout (warehouse_id, revision, layout_json) VALUES (?, 1, ?)",
                WAREHOUSE_ID, document("initial").toString());

        var bothSnapshotsEstablished = new CountDownLatch(2);
        var warehouses = mock(WarehouseService.class);
        when(warehouses.get(WAREHOUSE_ID)).thenAnswer(invocation -> {
            jdbc.queryForObject("SELECT id FROM warehouse WHERE id = ?", Long.class, WAREHOUSE_ID);
            bothSnapshotsEstablished.countDown();
            if (!bothSnapshotsEstablished.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent layout saves did not establish their snapshots");
            }
            return new Warehouse(WAREHOUSE_ID, "WH-007", "Test warehouse", null,
                    false, "enabled", null);
        });

        service = new WarehouseLayoutService(jdbc, mapper, warehouses,
                mock(WarehousePileReleaseService.class));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Test
    void concurrentSavesFromTheSameRevisionAllowOnlyOneWriter() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SaveResult> first = executor.submit(() -> save("first", ready, start));
            Future<SaveResult> second = executor.submit(() -> save("second", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var results = List.of(get(first), get(second));
            assertThat(results).filteredOn(SaveResult::successful).hasSize(1);
            assertThat(results).filteredOn(result -> !result.successful())
                    .extracting(SaveResult::errorCode)
                    .containsExactly("LAYOUT_CONFLICT");
        }

        assertThat(jdbc.queryForObject(
                "SELECT revision FROM warehouse_layout WHERE warehouse_id = ?", Long.class, WAREHOUSE_ID))
                .isEqualTo(2L);
    }

    private SaveResult save(String marker, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return transactions.execute(status -> {
                try {
                    return SaveResult.success(service.save(
                            WAREHOUSE_ID,
                            new WarehouseLayoutService.Layout(1, document(marker))));
                } catch (BusinessException error) {
                    return SaveResult.failure(error.code());
                }
            });
        } catch (RuntimeException error) {
            throw error;
        }
    }

    private DataSource dataSource() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:warehouse-layout-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private com.fasterxml.jackson.databind.JsonNode document(String marker) {
        var structure = mapper.createObjectNode();
        structure.set("outline", mapper.createObjectNode().set("nodes", mapper.createArrayNode()));
        structure.set("partitions", mapper.createArrayNode());
        structure.set("doors", mapper.createArrayNode());
        var document = mapper.createObjectNode();
        document.put("schemaVersion", 1);
        document.put("marker", marker);
        document.set("structure", structure);
        document.set("palletGroups", mapper.createArrayNode());
        document.put("completed", false);
        return document;
    }

    private SaveResult get(Future<SaveResult> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } catch (ExecutionException | java.util.concurrent.TimeoutException error) {
            throw new AssertionError(error);
        }
    }

    private record SaveResult(boolean successful, String errorCode) {
        static SaveResult success(WarehouseLayoutService.Layout ignored) {
            return new SaveResult(true, null);
        }

        static SaveResult failure(String errorCode) {
            return new SaveResult(false, errorCode);
        }
    }
}
