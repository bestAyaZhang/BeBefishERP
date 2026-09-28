package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.domain.InventoryBalance;
import com.bebefish.erp.inventory.domain.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<InventoryBalance> increase(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile
    ) {
        return apply(warehouseId, changes, source, operatorMobile, 1);
    }

    @Transactional
    public List<InventoryBalance> decrease(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile
    ) {
        return apply(warehouseId, changes, source, operatorMobile, -1);
    }

    @Transactional
    public List<InventoryBalance> reverse(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile
    ) {
        return apply(warehouseId, changes, source, operatorMobile, -1);
    }

    @Transactional
    public List<InventoryBalance> adjust(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile
    ) {
        return apply(warehouseId, changes, source, operatorMobile, 1, true);
    }

    private List<InventoryBalance> apply(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile,
            int operationSign
    ) {
        return apply(warehouseId, changes, source, operatorMobile, operationSign, false);
    }

    private List<InventoryBalance> apply(
            long warehouseId,
            List<InventoryChange> changes,
            InventorySource source,
            String operatorMobile,
            int operationSign,
            boolean allowSignedChanges
    ) {
        if (warehouseId <= 0) {
            throw validation("仓库不能为空");
        }
        if (changes == null || changes.isEmpty()) {
            throw validation("库存变动明细不能为空");
        }
        if (source == null || source.type() == null || source.type().isBlank()
                || source.id() <= 0 || source.number() == null || source.number().isBlank()) {
            throw validation("库存来源不能为空");
        }
        if (operatorMobile == null || operatorMobile.isBlank()) {
            throw validation("操作人不能为空");
        }

        var signedQuantities = aggregate(changes, operationSign, allowSignedChanges);
        var skuIds = new ArrayList<>(signedQuantities.keySet());
        repository.lockWarehouse(warehouseId);
        repository.ensureBalances(warehouseId, skuIds);
        var balances = repository.lockBalances(warehouseId, skuIds);
        if (balances.size() != skuIds.size()) {
            throw validation("库存余额初始化失败");
        }
        var bySkuId = new LinkedHashMap<Long, InventoryBalance>();
        balances.forEach(balance -> bySkuId.put(balance.skuId(), balance));

        var pendingChanges = new ArrayList<PendingBalanceChange>();
        for (var entry : signedQuantities.entrySet()) {
            var balance = bySkuId.get(entry.getKey());
            var delta = entry.getValue();
            var after = balance.quantity().add(delta);
            if (after.signum() < 0) {
                throw new BusinessException("INVENTORY_NOT_ENOUGH", HttpStatus.BAD_REQUEST, "库存不足");
            }
            if (delta.signum() < 0 && after.compareTo(repository.lockPlacedQuantity(warehouseId, entry.getKey())) < 0) {
                throw new BusinessException("INVENTORY_ALLOCATED_TO_PILES", HttpStatus.CONFLICT,
                        "库存已分配到货物堆，本次扣减将低于已分配数量，请先确认出库堆位");
            }
            pendingChanges.add(new PendingBalanceChange(balance, delta, after));
        }

        var updated = new ArrayList<InventoryBalance>();
        for (var pending : pendingChanges) {
            var balance = pending.balance();
            var delta = pending.delta();
            var after = pending.after();
            var saved = repository.saveBalance(new InventoryBalance(
                    balance.id(), balance.warehouseId(), balance.skuId(), after, balance.versionNo() + 1
            ));
            repository.appendLedger(newLedger(
                    saved, delta, source, operatorMobile, balance.quantity(), after
            ));
            updated.add(saved);
        }
        return List.copyOf(updated);
    }

    private record PendingBalanceChange(
            InventoryBalance balance,
            BigDecimal delta,
            BigDecimal after
    ) {
    }

    private Map<Long, BigDecimal> aggregate(
            List<InventoryChange> changes,
            int operationSign,
            boolean allowSignedChanges
    ) {
        var result = new LinkedHashMap<Long, BigDecimal>();
        for (var change : changes) {
            if (change == null || change.skuId() <= 0 || change.quantity() == null
                    || change.quantity().signum() == 0
                    || (!allowSignedChanges && change.quantity().signum() < 0)) {
                throw validation("库存变动数量必须大于 0");
            }
            result.merge(change.skuId(), change.quantity().multiply(BigDecimal.valueOf(operationSign)), BigDecimal::add);
        }
        return result;
    }

    private com.bebefish.erp.inventory.domain.InventoryLedgerEntry newLedger(
            InventoryBalance balance,
            BigDecimal delta,
            InventorySource source,
            String operatorMobile,
            BigDecimal before,
            BigDecimal after
    ) {
        var direction = delta.signum() > 0 ? "increase" : "decrease";
        return new com.bebefish.erp.inventory.domain.InventoryLedgerEntry(
                null,
                balance.warehouseId(),
                balance.skuId(),
                direction,
                delta.abs(),
                before,
                after,
                source.type(),
                source.id(),
                source.number(),
                LocalDateTime.now(),
                operatorMobile.trim()
        );
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
