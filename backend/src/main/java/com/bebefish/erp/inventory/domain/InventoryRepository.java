package com.bebefish.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.List;

public interface InventoryRepository {
    void lockWarehouse(long warehouseId);

    void ensureBalances(long warehouseId, List<Long> sortedSkuIds);

    List<InventoryBalance> lockBalances(long warehouseId, List<Long> sortedSkuIds);

    // Call only after locking the authoritative warehouse/SKU balance.
    BigDecimal lockPlacedQuantity(long warehouseId, long skuId);

    InventoryBalance saveBalance(InventoryBalance balance);

    InventoryLedgerEntry appendLedger(InventoryLedgerEntry entry);
}
