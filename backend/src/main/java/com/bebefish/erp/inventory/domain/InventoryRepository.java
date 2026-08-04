package com.bebefish.erp.inventory.domain;

import java.util.List;

public interface InventoryRepository {
    void ensureBalances(long warehouseId, List<Long> sortedSkuIds);

    List<InventoryBalance> lockBalances(long warehouseId, List<Long> sortedSkuIds);

    InventoryBalance saveBalance(InventoryBalance balance);

    InventoryLedgerEntry appendLedger(InventoryLedgerEntry entry);
}
