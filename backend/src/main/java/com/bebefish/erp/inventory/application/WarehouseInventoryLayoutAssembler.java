package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WarehouseInventoryLayoutAssembler {
    private static final String UNALLOCATED = "UNALLOCATED";

    public record BalanceRow(
            long skuId, String skuCode, String productName, String skuName, String specification,
            Integer unitsPerCase, BigDecimal units, LocalDateTime updatedAt
    ) {
    }

    public record LocationRow(
            String zoneId, String palletId, long skuId, String skuCode, String productName,
            String skuName, String specification, Integer unitsPerCase, BigDecimal units,
            LocalDateTime updatedAt
    ) {
    }

    public WarehouseInventoryLayoutView assemble(
            long warehouseId, List<BalanceRow> balances, List<LocationRow> locations
    ) {
        var balanceBySku = new LinkedHashMap<Long, BalanceRow>();
        for (var balance : balances) {
            if (balance.units().signum() > 0) {
                balanceBySku.put(balance.skuId(), balance);
            }
        }

        var placedBySku = new LinkedHashMap<Long, BigDecimal>();
        var allocations = new ArrayList<WarehouseInventoryLayoutView.Allocation>();
        var updatedAt = balances.stream().map(BalanceRow::updatedAt).filter(value -> value != null)
                .max(Comparator.naturalOrder()).orElse(null);
        for (var location : locations) {
            if (location.units().signum() < 0 || UNALLOCATED.equals(location.palletId())) {
                continue;
            }
            if (location.units().signum() > 0) {
                placedBySku.merge(location.skuId(), location.units(), BigDecimal::add);
            }
            allocations.add(toAllocation(location));
            if (location.updatedAt() != null && (updatedAt == null || location.updatedAt().isAfter(updatedAt))) {
                updatedAt = location.updatedAt();
            }
        }

        var totalUnits = balanceBySku.values().stream().map(BalanceRow::units)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var placedUnits = BigDecimal.ZERO;
        var unallocatedUnits = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : placedBySku.entrySet()) {
            var balance = balanceBySku.get(entry.getKey());
            if (balance == null || entry.getValue().compareTo(balance.units()) > 0) {
                var code = balance == null
                        ? locations.stream().filter(item -> item.skuId() == entry.getKey()).map(LocationRow::skuCode).findFirst().orElse(String.valueOf(entry.getKey()))
                        : balance.skuCode();
                throw inconsistent(code);
            }
            placedUnits = placedUnits.add(entry.getValue());
        }
        for (var balance : balanceBySku.values()) {
            var remainder = balance.units().subtract(placedBySku.getOrDefault(balance.skuId(), BigDecimal.ZERO));
            if (remainder.signum() < 0) {
                throw inconsistent(balance.skuCode());
            }
            if (remainder.signum() > 0) {
                unallocatedUnits = unallocatedUnits.add(remainder);
                allocations.add(new WarehouseInventoryLayoutView.Allocation(
                        null, UNALLOCATED, balance.skuId(), balance.skuCode(), balance.productName(),
                        balance.skuName(), balance.specification(), balance.unitsPerCase(), remainder
                ));
            }
        }
        allocations.sort(Comparator.comparing(WarehouseInventoryLayoutView.Allocation::palletId)
                .thenComparing(WarehouseInventoryLayoutView.Allocation::skuCode, Comparator.nullsLast(String::compareTo)));
        return new WarehouseInventoryLayoutView(
                warehouseId, totalUnits, balanceBySku.size(), placedUnits, unallocatedUnits,
                updatedAt, List.copyOf(allocations)
        );
    }

    private WarehouseInventoryLayoutView.Allocation toAllocation(LocationRow row) {
        return new WarehouseInventoryLayoutView.Allocation(
                row.zoneId(), row.palletId(), row.skuId(), row.skuCode(), row.productName(),
                row.skuName(), row.specification(), row.unitsPerCase(), row.units()
        );
    }

    private BusinessException inconsistent(String skuCode) {
        return new BusinessException(
                "INVENTORY_LOCATION_INCONSISTENT", HttpStatus.CONFLICT,
                "位置库存超过仓库实际库存：" + skuCode
        );
    }
}
