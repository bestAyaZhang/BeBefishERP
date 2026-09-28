package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.domain.InventoryBalance;
import com.bebefish.erp.inventory.domain.InventoryLedgerEntry;
import com.bebefish.erp.inventory.domain.InventoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryServiceTest {
    private FakeInventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeInventoryRepository();
        service = new InventoryService(repository);
    }

    @Test
    void increaseWritesBalanceAndLedgerTogether() {
        repository.seed(1L, 10L, "12");

        service.increase(1L, List.of(change(10L, "5")), source("purchase", 99L, "PI001"), "13800138000");

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("17");
        assertThat(repository.ledger()).singleElement().satisfies(entry -> {
            assertThat(entry.direction()).isEqualTo("increase");
            assertThat(entry.quantity()).isEqualByComparingTo("5");
            assertThat(entry.beforeQuantity()).isEqualByComparingTo("12");
            assertThat(entry.afterQuantity()).isEqualByComparingTo("17");
        });
    }

    @Test
    void decreaseWritesBalanceAndLedgerTogether() {
        repository.seed(1L, 10L, "12");

        service.decrease(1L, List.of(change(10L, "5")), source("sales", 99L, "SO001"), "13800138000");

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("7");
        assertThat(repository.ledger()).singleElement().satisfies(entry -> {
            assertThat(entry.direction()).isEqualTo("decrease");
            assertThat(entry.beforeQuantity()).isEqualByComparingTo("12");
            assertThat(entry.afterQuantity()).isEqualByComparingTo("7");
        });
    }

    @Test
    void insufficientInventoryChangesNothing() {
        repository.seed(1L, 10L, "3");

        assertThatThrownBy(() -> service.decrease(
                1L, List.of(change(10L, "5")), source("sales", 99L, "SO001"), "13800138000"
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.code()).isEqualTo("INVENTORY_NOT_ENOUGH");
            assertThat(exception.getMessage()).isEqualTo("库存不足");
        });

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("3");
        assertThat(repository.ledger()).isEmpty();
    }

    @Test
    void insufficientInventoryInOneLineDoesNotPartiallyApplyOtherLines() {
        repository.seed(1L, 10L, "10");
        repository.seed(1L, 20L, "3");

        assertThatThrownBy(() -> service.decrease(
                1L,
                List.of(change(10L, "5"), change(20L, "5")),
                source("sales", 99L, "SO001"),
                "13800138000"
        )).isInstanceOf(BusinessException.class).hasMessage("库存不足");

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("10");
        assertThat(repository.balance(1L, 20L)).isEqualByComparingTo("3");
        assertThat(repository.ledger()).isEmpty();
    }

    @Test
    void reverseRestoresTheBalanceAndWritesOppositeDirection() {
        repository.seed(1L, 10L, "12");
        service.increase(1L, List.of(change(10L, "5")), source("purchase", 99L, "PI001"), "13800138000");

        service.reverse(1L, List.of(change(10L, "5")), source("purchase_void", 99L, "PI001"), "13800138000");

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("12");
        assertThat(repository.ledger()).extracting(InventoryLedgerEntry::direction)
                .containsExactly("increase", "decrease");
    }

    @Test
    void missingBalanceIsCreatedWhenIncreasing() {
        service.increase(1L, List.of(change(10L, "8")), source("adjustment", 1L, "ADJ001"), "13800138000");

        assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("8");
    }

    @Test
    void rejectsZeroOrNegativeChange() {
        assertThatThrownBy(() -> service.increase(
                1L, List.of(change(10L, "0")), source("adjustment", 1L, "ADJ001"), "13800138000"
        )).isInstanceOf(BusinessException.class).hasMessage("库存变动数量必须大于 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sales", "adjustment", "reverse"})
    void rejectsDecreasesBelowPlacedStockBeforeWritingAnyBalanceOrLedger(String operation) {
        repository.seed(1L, 10L, "100");
        repository.seed(1L, 20L, "100");
        repository.placed.put(20L, new BigDecimal("80"));
        var changes = List.of(change(10L, "10"), change(20L, "21"));
        assertThatThrownBy(() -> {
            if (operation.equals("adjustment")) service.adjust(1, List.of(change(10, "-10"), change(20, "-21")), source(operation, 1, "ADJ001"), "13800138000");
            else if (operation.equals("reverse")) service.reverse(1, changes, source(operation, 1, "VOID001"), "13800138000");
            else service.decrease(1, changes, source(operation, 1, "SO001"), "13800138000");
        }).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("INVENTORY_ALLOCATED_TO_PILES"));
        assertThat(repository.balance(1, 10)).isEqualByComparingTo("100");
        assertThat(repository.balance(1, 20)).isEqualByComparingTo("100");
        assertThat(repository.ledger()).isEmpty();
    }

    @Test
    void permitsDecreasesDownToPlacedStock() {
        repository.seed(1, 10, "100");
        repository.placed.put(10L, new BigDecimal("80"));
        service.decrease(1, List.of(change(10, "20")), source("sales", 1, "SO001"), "13800138000");
        assertThat(repository.balance(1, 10)).isEqualByComparingTo("80");
        assertThat(repository.ledger()).hasSize(1);
    }

    private InventoryChange change(long skuId, String quantity) {
        return new InventoryChange(skuId, new BigDecimal(quantity));
    }

    private InventorySource source(String type, long id, String number) {
        return new InventorySource(type, id, number);
    }

    private static final class FakeInventoryRepository implements InventoryRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<String, InventoryBalance> balances = new LinkedHashMap<>();
        private final List<InventoryLedgerEntry> ledger = new ArrayList<>();
        private final Map<Long, BigDecimal> placed = new LinkedHashMap<>();

        @Override
        public void lockWarehouse(long warehouseId) {
        }

        @Override
        public BigDecimal lockPlacedQuantity(long warehouseId, long skuId) {
            return placed.getOrDefault(skuId, BigDecimal.ZERO);
        }

        void seed(long warehouseId, long skuId, String quantity) {
            var id = sequence.incrementAndGet();
            balances.put(key(warehouseId, skuId), new InventoryBalance(
                    id, warehouseId, skuId, new BigDecimal(quantity), 0L
            ));
        }

        BigDecimal balance(long warehouseId, long skuId) {
            return Optional.ofNullable(balances.get(key(warehouseId, skuId)))
                    .map(InventoryBalance::quantity)
                    .orElse(BigDecimal.ZERO);
        }

        List<InventoryLedgerEntry> ledger() {
            return List.copyOf(ledger);
        }

        @Override
        public void ensureBalances(long warehouseId, List<Long> sortedSkuIds) {
            for (var skuId : sortedSkuIds) {
                balances.putIfAbsent(key(warehouseId, skuId), new InventoryBalance(
                        sequence.incrementAndGet(), warehouseId, skuId, BigDecimal.ZERO, 0L
                ));
            }
        }

        @Override
        public List<InventoryBalance> lockBalances(long warehouseId, List<Long> sortedSkuIds) {
            return sortedSkuIds.stream()
                    .map(skuId -> balances.get(key(warehouseId, skuId)))
                    .filter(value -> value != null)
                    .sorted(Comparator.comparingLong(InventoryBalance::skuId))
                    .toList();
        }

        @Override
        public InventoryBalance saveBalance(InventoryBalance balance) {
            var id = balance.id() == null ? sequence.incrementAndGet() : balance.id();
            var saved = new InventoryBalance(id, balance.warehouseId(), balance.skuId(), balance.quantity(), balance.versionNo());
            balances.put(key(balance.warehouseId(), balance.skuId()), saved);
            return saved;
        }

        @Override
        public InventoryLedgerEntry appendLedger(InventoryLedgerEntry entry) {
            var saved = new InventoryLedgerEntry(
                    (long) (ledger.size() + 1), entry.warehouseId(), entry.skuId(), entry.direction(),
                    entry.quantity(), entry.beforeQuantity(), entry.afterQuantity(), entry.sourceType(),
                    entry.sourceId(), entry.sourceNo(), entry.occurredAt(), entry.operatorMobile()
            );
            ledger.add(saved);
            return saved;
        }

        private String key(long warehouseId, long skuId) {
            return warehouseId + ":" + skuId;
        }
    }
}
