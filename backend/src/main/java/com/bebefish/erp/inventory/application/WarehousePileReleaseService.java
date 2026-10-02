package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class WarehousePileReleaseService {
    private static final String UNALLOCATED = "UNALLOCATED";

    public record AllocationSnapshot(long skuId, BigDecimal units) {}
    public record ReleaseConfirmation(String palletId, List<AllocationSnapshot> allocations) {}

    private record Location(String palletId, BigDecimal units) {}

    private final NamedParameterJdbcTemplate jdbc;

    public WarehousePileReleaseService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void releaseRemovedPiles(
            long warehouseId,
            JsonNode before,
            JsonNode after,
            List<ReleaseConfirmation> confirmations
    ) {
        var removed = pileIds(before);
        removed.removeAll(pileIds(after));
        var expected = validateConfirmations(confirmations, removed);
        if (removed.isEmpty()) return;

        var affectedSkus = new TreeSet<Long>();
        for (var palletId : removed) {
            var parameters = Map.of("warehouseId", warehouseId, "palletId", palletId);
            affectedSkus.addAll(jdbc.query("""
                    select sku_id from inventory_location_balance
                    where warehouse_id = :warehouseId and pallet_id = :palletId
                    order by sku_id
                    """, parameters, (rs, row) -> rs.getLong("sku_id")));
        }

        var balances = new HashMap<Long, BigDecimal>();
        var locations = new HashMap<Long, List<Location>>();
        for (var skuId : affectedSkus) {
            var parameters = Map.of("warehouseId", warehouseId, "skuId", skuId);
            var balanceRows = jdbc.query("""
                    select quantity from inventory_balance
                    where warehouse_id = :warehouseId and sku_id = :skuId
                    for update
                    """, parameters, (rs, row) -> rs.getBigDecimal("quantity"));
            balances.put(skuId, balanceRows.isEmpty() ? BigDecimal.ZERO : balanceRows.getFirst());
            locations.put(skuId, jdbc.query("""
                    select pallet_id, quantity from inventory_location_balance
                    where warehouse_id = :warehouseId and sku_id = :skuId
                    order by pallet_id for update
                    """, parameters, (rs, row) -> new Location(
                    rs.getString("pallet_id"), rs.getBigDecimal("quantity"))));
        }

        var actual = new HashMap<String, Map<Long, BigDecimal>>();
        for (var palletId : removed) actual.put(palletId, new LinkedHashMap<>());
        for (var skuId : affectedSkus) {
            for (var location : locations.get(skuId)) {
                if (removed.contains(location.palletId())) {
                    actual.get(location.palletId()).put(skuId, location.units());
                }
            }
        }
        for (var palletId : removed) {
            var actualForPile = actual.get(palletId);
            if (!sameAllocations(actualForPile, expected.get(palletId))) {
                throw new BusinessException(expected.containsKey(palletId)
                        ? "PILE_ALLOCATIONS_CHANGED" : "PILE_CONFIRMATION_REQUIRED",
                        HttpStatus.CONFLICT, "货堆 SKU 分配已变化，请刷新库存后重新确认删除");
            }
            if (!actualForPile.isEmpty() && !canEditInventory()) {
                throw new BusinessException("PILE_RELEASE_FORBIDDEN", HttpStatus.FORBIDDEN,
                        "没有解除货堆 SKU 分配的权限");
            }
        }

        for (var palletId : removed) {
            if (actual.get(palletId).isEmpty()) continue;
            jdbc.update("""
                    delete from inventory_location_balance
                    where warehouse_id = :warehouseId and pallet_id = :palletId
                    """, Map.of("warehouseId", warehouseId, "palletId", palletId));
        }
        for (var skuId : affectedSkus) {
            var placed = locations.get(skuId).stream()
                    .filter(location -> !UNALLOCATED.equals(location.palletId()))
                    .filter(location -> !removed.contains(location.palletId()))
                    .map(Location::units)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var unallocated = balances.get(skuId).subtract(placed);
            if (unallocated.signum() < 0) {
                throw new BusinessException("INVENTORY_LOCATION_INCONSISTENT", HttpStatus.CONFLICT,
                        "库位数量超过仓库实际库存，请先核对库存");
            }
            var params = new MapSqlParameterSource()
                    .addValue("warehouseId", warehouseId)
                    .addValue("skuId", skuId)
                    .addValue("quantity", unallocated);
            var updated = jdbc.update("""
                    update inventory_location_balance
                    set quantity = :quantity, version_no = version_no + 1,
                        updated_at = current_timestamp
                    where warehouse_id = :warehouseId and pallet_id = 'UNALLOCATED' and sku_id = :skuId
                    """, params);
            if (updated == 0 && balances.get(skuId).signum() > 0) {
                jdbc.update("""
                        insert into inventory_location_balance (
                            warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                            created_at, updated_at
                        ) values (:warehouseId, null, 'UNALLOCATED', :skuId, :quantity, 0,
                            current_timestamp, current_timestamp)
                        """, params);
            }
        }
    }

    private Set<String> pileIds(JsonNode document) {
        var result = new HashSet<String>();
        if (document == null) return result;
        for (var pile : document.path("palletGroups")) {
            var id = pile.path("id").asText("");
            if (id.isBlank() || !result.add(id)) {
                throw new BusinessException("INVALID_LAYOUT", HttpStatus.BAD_REQUEST,
                        "货堆 ID 缺失或重复");
            }
        }
        return result;
    }

    private Map<String, Map<Long, BigDecimal>> validateConfirmations(
            List<ReleaseConfirmation> confirmations, Set<String> removed
    ) {
        var result = new HashMap<String, Map<Long, BigDecimal>>();
        if (confirmations == null) return result;
        for (var confirmation : confirmations) {
            if (confirmation == null || confirmation.palletId() == null
                    || !removed.contains(confirmation.palletId()) || confirmation.allocations() == null
                    || result.containsKey(confirmation.palletId())) throw invalidConfirmation();
            var allocations = new HashMap<Long, BigDecimal>();
            for (var item : confirmation.allocations()) {
                if (item == null || item.skuId() <= 0 || item.units() == null
                        || item.units().signum() < 0
                        || allocations.putIfAbsent(item.skuId(), item.units()) != null) {
                    throw invalidConfirmation();
                }
            }
            result.put(confirmation.palletId(), allocations);
        }
        return result;
    }

    private boolean sameAllocations(Map<Long, BigDecimal> actual, Map<Long, BigDecimal> expected) {
        if (expected == null) return actual.isEmpty();
        if (!actual.keySet().equals(expected.keySet())) return false;
        return actual.entrySet().stream().allMatch(item -> item.getValue().compareTo(expected.get(item.getKey())) == 0);
    }

    private boolean canEditInventory() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "inventory:edit".equals(authority.getAuthority()));
    }

    private BusinessException invalidConfirmation() {
        return new BusinessException("INVALID_PILE_RELEASE_CONFIRMATION", HttpStatus.BAD_REQUEST,
                "货堆删除确认数据无效");
    }
}
