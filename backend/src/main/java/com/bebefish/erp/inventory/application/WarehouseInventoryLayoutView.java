package com.bebefish.erp.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WarehouseInventoryLayoutView(
        long warehouseId,
        BigDecimal totalUnits,
        int skuCount,
        BigDecimal placedUnits,
        BigDecimal unallocatedUnits,
        LocalDateTime updatedAt,
        List<Allocation> allocations
) {
    public record Allocation(
            String zoneId,
            String palletId,
            long skuId,
            String skuCode,
            String productName,
            String skuName,
            String specification,
            Integer unitsPerCase,
            BigDecimal units
    ) {
    }
}
