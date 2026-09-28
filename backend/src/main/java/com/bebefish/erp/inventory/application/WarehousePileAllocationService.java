package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehousePileAllocationService {
    private static final String UNALLOCATED = "UNALLOCATED";

    public record Command(String palletId, long skuId, long units) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final WarehouseInventoryLayoutQueryService query;
    private final ObjectMapper objectMapper;

    public WarehousePileAllocationService(
            NamedParameterJdbcTemplate jdbc,
            WarehouseInventoryLayoutQueryService query,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.query = query;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WarehouseInventoryLayoutView allocate(long warehouseId, Command command) {
        validateCommand(warehouseId, command);
        var target = loadCompletedPile(warehouseId, command.palletId());
        if (command.units() == 0) {
            ensureSkuEnabled(command.skuId());
            upsertTarget(warehouseId, target.zoneId(), command);
            return query.get(warehouseId);
        }
        var balance = lockBalance(warehouseId, command.skuId());
        var placed = lockPlacedLocations(warehouseId, command.skuId());
        var available = balance.subtract(placed);
        var requested = BigDecimal.valueOf(command.units());
        if (requested.compareTo(available) > 0) {
            throw conflict(
                    "INSUFFICIENT_UNALLOCATED_INVENTORY",
                    "库存已变化，请重新确认可分配数量"
            );
        }
        upsertTarget(warehouseId, target.zoneId(), command);
        reconcileUnallocated(warehouseId, command.skuId(), available.subtract(requested));
        return query.get(warehouseId);
    }

    private void validateCommand(long warehouseId, Command command) {
        if (warehouseId <= 0 || command == null || command.palletId() == null
                || command.palletId().isBlank() || command.skuId() <= 0 || command.units() < 0) {
            throw new BusinessException(
                    "INVALID_PILE_ALLOCATION", HttpStatus.BAD_REQUEST, "堆位分配参数无效"
            );
        }
    }

    private PileTarget loadCompletedPile(long warehouseId, String palletId) {
        // Match WarehouseLayoutService.save: warehouse first, then layout, then inventory.
        // The warehouse row also serializes the first layout insert.
        var warehouses = jdbc.query("select id from warehouse where id = :warehouseId for update",
                Map.of("warehouseId", warehouseId), (rs, rowNum) -> rs.getLong("id"));
        if (warehouses.isEmpty()) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在");
        }
        var layouts = jdbc.query(
                "select layout_json from warehouse_layout where warehouse_id = :warehouseId for update",
                Map.of("warehouseId", warehouseId),
                (rs, rowNum) -> rs.getString("layout_json")
        );
        if (layouts.isEmpty()) {
            throw planningRequired();
        }

        var document = parseLayout(layouts.getFirst());
        if (!document.path("completed").asBoolean(false)) {
            throw planningRequired();
        }

        JsonNode pallet = null;
        for (var candidate : document.path("palletGroups")) {
            if (candidate.path("id").isTextual() && palletId.equals(candidate.path("id").textValue())) {
                pallet = candidate;
                break;
            }
        }
        if (pallet == null) {
            throw new BusinessException("PALLET_NOT_FOUND", HttpStatus.NOT_FOUND, "规划堆位不存在");
        }

        var centerX = pallet.path("left").asDouble() + pallet.path("width").asDouble() / 2;
        var centerY = pallet.path("top").asDouble() + pallet.path("height").asDouble() / 2;
        for (var zone : document.path("structure").path("zones")) {
            var kind = zone.get("kind");
            if ((kind == null || kind.isNull()) && contains(zone, centerX, centerY)) {
                return new PileTarget(zone.path("id").textValue());
            }
        }
        return new PileTarget(null);
    }

    private JsonNode parseLayout(String layoutJson) {
        try {
            return objectMapper.readTree(layoutJson);
        } catch (JsonProcessingException error) {
            throw planningRequired();
        }
    }

    private boolean contains(JsonNode zone, double x, double y) {
        var left = zone.path("left").asDouble();
        var top = zone.path("top").asDouble();
        return x >= left && x <= left + zone.path("width").asDouble()
                && y >= top && y <= top + zone.path("height").asDouble();
    }

    private BigDecimal lockBalance(long warehouseId, long skuId) {
        var parameters = Map.of("warehouseId", warehouseId, "skuId", skuId);
        var balances = jdbc.query("""
                select quantity from inventory_balance
                where warehouse_id = :warehouseId and sku_id = :skuId and quantity > 0
                for update
                """, parameters, (rs, rowNum) -> rs.getBigDecimal("quantity"));
        if (balances.isEmpty()) {
            throw new BusinessException(
                    "SKU_BALANCE_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库中没有可分配的 SKU 库存"
            );
        }
        return balances.getFirst();
    }

    private void ensureSkuEnabled(long skuId) {
        var skus = jdbc.query("""
                select s.id from product_sku s
                join product_spu p on p.id = s.product_id
                where s.id = :skuId and s.status = 'enabled' and p.status = 'enabled'
                """, Map.of("skuId", skuId), (rs, rowNum) -> rs.getLong("id"));
        if (skus.isEmpty()) {
            throw new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "可用 SKU 不存在");
        }
    }

    private BigDecimal lockPlacedLocations(long warehouseId, long skuId) {
        var parameters = Map.of("warehouseId", warehouseId, "skuId", skuId);
        return jdbc.query("""
                select pallet_id, quantity from inventory_location_balance
                where warehouse_id = :warehouseId and sku_id = :skuId
                order by pallet_id
                for update
                """, parameters, (rs, rowNum) -> new LocationBalance(
                rs.getString("pallet_id"), rs.getBigDecimal("quantity")
        )).stream()
                .filter(location -> !UNALLOCATED.equals(location.palletId()))
                .map(LocationBalance::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void upsertTarget(long warehouseId, String zoneId, Command command) {
        var parameters = locationParameters(
                warehouseId, zoneId, command.palletId(), command.skuId(),
                BigDecimal.valueOf(command.units())
        );
        var updated = jdbc.update("""
                update inventory_location_balance
                set zone_id = :zoneId, quantity = quantity + :quantity,
                    version_no = version_no + 1, updated_at = current_timestamp
                where warehouse_id = :warehouseId and pallet_id = :palletId and sku_id = :skuId
                """, parameters);
        if (updated == 0) {
            insertLocation(parameters);
        }
    }

    private void reconcileUnallocated(long warehouseId, long skuId, BigDecimal quantity) {
        var parameters = locationParameters(warehouseId, null, UNALLOCATED, skuId, quantity);
        var updated = jdbc.update("""
                update inventory_location_balance
                set zone_id = null, quantity = :quantity, version_no = version_no + 1,
                    updated_at = current_timestamp
                where warehouse_id = :warehouseId and pallet_id = :palletId and sku_id = :skuId
                """, parameters);
        if (updated == 0) {
            insertLocation(parameters);
        }
    }

    private void insertLocation(MapSqlParameterSource parameters) {
        jdbc.update("""
                insert into inventory_location_balance (
                    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no,
                    created_at, updated_at
                ) values (
                    :warehouseId, :zoneId, :palletId, :skuId, :quantity, 0,
                    current_timestamp, current_timestamp
                )
                """, parameters);
    }

    private MapSqlParameterSource locationParameters(
            long warehouseId, String zoneId, String palletId, long skuId, BigDecimal quantity
    ) {
        return new MapSqlParameterSource()
                .addValue("warehouseId", warehouseId)
                .addValue("zoneId", zoneId, Types.VARCHAR)
                .addValue("palletId", palletId)
                .addValue("skuId", skuId)
                .addValue("quantity", quantity);
    }

    private BusinessException planningRequired() {
        return conflict("PLANNING_REQUIRED", "请先完成仓库平面规划");
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(code, HttpStatus.CONFLICT, message);
    }

    private record PileTarget(String zoneId) {
    }

    private record LocationBalance(String palletId, BigDecimal quantity) {
    }
}
