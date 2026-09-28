package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.domain.InventoryBalance;
import com.bebefish.erp.inventory.domain.InventoryLedgerEntry;
import com.bebefish.erp.inventory.domain.InventoryRepository;
import com.bebefish.erp.inventory.domain.StockAdjustment;
import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import com.bebefish.erp.inventory.domain.StockAdjustmentRepository;
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

class StockAdjustmentServiceTest {
    private FakeInventoryRepository inventoryRepository;
    private FakeStockAdjustmentRepository adjustmentRepository;
    private StockAdjustmentService service;

    @BeforeEach
    void setUp() {
        inventoryRepository = new FakeInventoryRepository();
        adjustmentRepository = new FakeStockAdjustmentRepository();
        service = new StockAdjustmentService(
                adjustmentRepository,
                new InventoryService(inventoryRepository)
        );
    }

    @Test
    void confirmingAdjustmentAppliesEachDeltaOnce() {
        inventoryRepository.seed(1L, 11L, "10");
        var draft = service.create(command(
                item(10L, "12"),
                item(11L, "-2")
        ));

        var confirmed = service.confirm(draft.id(), "13800138000");

        assertThat(confirmed.status()).isEqualTo("confirmed");
        assertThat(inventoryRepository.balance(1L, 10L)).isEqualByComparingTo("12");
        assertThat(inventoryRepository.balance(1L, 11L)).isEqualByComparingTo("8");
        assertThatThrownBy(() -> service.confirm(draft.id(), "13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("库存调整单已确认，请勿重复操作");
    }

    @Test
    void voidingConfirmedAdjustmentCreatesReverseLedger() {
        var confirmed = service.confirm(
                service.create(command(item(10L, "12"))).id(),
                "13800138000"
        );

        var voided = service.voidAdjustment(confirmed.id(), "录入错误", "13800138000");

        assertThat(voided.status()).isEqualTo("voided");
        assertThat(inventoryRepository.balance(1L, 10L)).isZero();
        assertThat(inventoryRepository.ledger()).extracting(InventoryLedgerEntry::sourceType)
                .containsExactly("adjustment", "adjustment_void");
    }

    @Test
    void confirmedAdjustmentCannotBeUpdatedOrDeleted() {
        var confirmed = service.confirm(
                service.create(command(item(10L, "12"))).id(),
                "13800138000"
        );

        assertThatThrownBy(() -> service.update(confirmed.id(), command(item(10L, "15"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已确认的库存调整单不可修改");
        assertThatThrownBy(() -> service.delete(confirmed.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已确认的库存调整单不可删除");
    }

    @Test
    void insufficientAdjustmentRemainsDraft() {
        inventoryRepository.seed(1L, 10L, "2");
        var draft = service.create(command(item(10L, "-3")));

        assertThatThrownBy(() -> service.confirm(draft.id(), "13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("库存不足");

        assertThat(adjustmentRepository.findById(draft.id()).orElseThrow().status()).isEqualTo("draft");
        assertThat(inventoryRepository.balance(1L, 10L)).isEqualByComparingTo("2");
        assertThat(inventoryRepository.ledger()).isEmpty();
    }

    private StockAdjustmentCommand command(StockAdjustmentItem... items) {
        return new StockAdjustmentCommand(1L, List.of(items), "盘点调整", null);
    }

    private StockAdjustmentItem item(long skuId, String delta) {
        return new StockAdjustmentItem(null, skuId, new BigDecimal(delta));
    }

    private static final class FakeStockAdjustmentRepository implements StockAdjustmentRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, StockAdjustment> values = new LinkedHashMap<>();

        @Override
        public StockAdjustment save(StockAdjustment adjustment) {
            var id = adjustment.id() == null ? sequence.incrementAndGet() : adjustment.id();
            var saved = new StockAdjustment(
                    id, adjustment.adjustmentNo(), adjustment.warehouseId(), adjustment.reason(),
                    adjustment.status(), adjustment.remark(), adjustment.items(),
                    adjustment.createdAt(), adjustment.updatedAt()
            );
            values.put(id, saved);
            return saved;
        }

        @Override
        public Optional<StockAdjustment> findById(long id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public void deleteById(long id) {
            values.remove(id);
        }
    }

    private static final class FakeInventoryRepository implements InventoryRepository {
        @Override
        public void lockWarehouse(long warehouseId) {
        }
        @Override
        public BigDecimal lockPlacedQuantity(long warehouseId, long skuId) {
            return BigDecimal.ZERO;
        }

        private final AtomicLong sequence = new AtomicLong();
        private final Map<String, InventoryBalance> balances = new LinkedHashMap<>();
        private final List<InventoryLedgerEntry> ledger = new ArrayList<>();

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
            var saved = new InventoryBalance(
                    balance.id() == null ? sequence.incrementAndGet() : balance.id(),
                    balance.warehouseId(), balance.skuId(), balance.quantity(), balance.versionNo()
            );
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
