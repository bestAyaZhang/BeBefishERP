package com.bebefish.erp.inventory.application;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseInventoryLayoutQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final WarehouseInventoryLayoutAssembler assembler;

    public WarehouseInventoryLayoutQueryService(
            NamedParameterJdbcTemplate jdbc, WarehouseInventoryLayoutAssembler assembler
    ) {
        this.jdbc = jdbc;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public WarehouseInventoryLayoutView get(long warehouseId) {
        var parameters = Map.of("warehouseId", warehouseId);
        var balances = jdbc.query("""
                select b.sku_id, s.sku_code, p.product_name, s.sku_name, s.spec_text,
                       s.carton_quantity, b.quantity, b.updated_at
                from inventory_balance b
                left join product_sku s on s.id = b.sku_id
                left join product_spu p on p.id = s.product_id
                where b.warehouse_id = :warehouseId and b.quantity > 0
                order by b.sku_id
                """, parameters, (rs, rowNum) -> new WarehouseInventoryLayoutAssembler.BalanceRow(
                rs.getLong("sku_id"), rs.getString("sku_code"), rs.getString("product_name"),
                rs.getString("sku_name"), rs.getString("spec_text"),
                rs.getObject("carton_quantity", Integer.class), rs.getBigDecimal("quantity"),
                rs.getTimestamp("updated_at").toLocalDateTime()
        ));
        var locations = jdbc.query("""
                select l.zone_id, l.pallet_id, l.sku_id, s.sku_code, p.product_name,
                       s.sku_name, s.spec_text, s.carton_quantity, l.quantity, l.updated_at
                from inventory_location_balance l
                left join product_sku s on s.id = l.sku_id
                left join product_spu p on p.id = s.product_id
                where l.warehouse_id = :warehouseId and l.pallet_id <> 'UNALLOCATED' and l.quantity > 0
                order by l.pallet_id, l.sku_id
                """, parameters, (rs, rowNum) -> new WarehouseInventoryLayoutAssembler.LocationRow(
                rs.getString("zone_id"), rs.getString("pallet_id"), rs.getLong("sku_id"),
                rs.getString("sku_code"), rs.getString("product_name"), rs.getString("sku_name"),
                rs.getString("spec_text"), rs.getObject("carton_quantity", Integer.class),
                rs.getBigDecimal("quantity"), rs.getTimestamp("updated_at").toLocalDateTime()
        ));
        return assembler.assemble(warehouseId, balances, locations);
    }
}
