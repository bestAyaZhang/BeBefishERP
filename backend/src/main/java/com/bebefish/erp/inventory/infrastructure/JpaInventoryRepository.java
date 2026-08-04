package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.InventoryBalance;
import com.bebefish.erp.inventory.domain.InventoryLedgerEntry;
import com.bebefish.erp.inventory.domain.InventoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInventoryRepository implements InventoryRepository {
    private final SpringDataInventoryBalanceRepository balanceRepository;
    private final SpringDataInventoryLedgerRepository ledgerRepository;

    public JpaInventoryRepository(
            SpringDataInventoryBalanceRepository balanceRepository,
            SpringDataInventoryLedgerRepository ledgerRepository
    ) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public void ensureBalances(long warehouseId, List<Long> sortedSkuIds) {
        var skuIds = new ArrayList<>(sortedSkuIds);
        skuIds.sort(Comparator.naturalOrder());
        if (skuIds.isEmpty()) {
            return;
        }
        var existingSkuIds = new HashSet<>(balanceRepository.findExistingSkuIds(warehouseId, skuIds));
        skuIds.stream()
                .filter(skuId -> !existingSkuIds.contains(skuId))
                .forEach(skuId -> balanceRepository.insertIfAbsent(warehouseId, skuId));
    }

    @Override
    public List<InventoryBalance> lockBalances(long warehouseId, List<Long> sortedSkuIds) {
        if (sortedSkuIds.isEmpty()) {
            return List.of();
        }
        var skuIds = new ArrayList<>(sortedSkuIds);
        skuIds.sort(Comparator.naturalOrder());
        return balanceRepository.findLockedByWarehouseIdAndSkuIds(warehouseId, skuIds)
                .stream()
                .map(InventoryBalanceJpaEntity::toDomain)
                .toList();
    }

    @Override
    public InventoryBalance saveBalance(InventoryBalance balance) {
        InventoryBalanceJpaEntity entity;
        if (balance.id() == null) {
            entity = new InventoryBalanceJpaEntity(balance);
        } else {
            entity = balanceRepository.findById(balance.id()).orElseThrow();
            entity.apply(balance);
        }
        return balanceRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public InventoryLedgerEntry appendLedger(InventoryLedgerEntry entry) {
        return ledgerRepository.saveAndFlush(new InventoryLedgerJpaEntity(entry)).toDomain();
    }
}
