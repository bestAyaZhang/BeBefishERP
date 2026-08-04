package com.bebefish.erp.inventory.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public InventoryQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<InventoryBalanceView> listBalances(
            Long warehouseId,
            String keyword,
            Pageable pageable
    ) {
        var parameters = new MapSqlParameterSource();
        var conditions = new ArrayList<String>();
        if (warehouseId != null) {
            conditions.add("b.warehouse_id = :warehouseId");
            parameters.addValue("warehouseId", warehouseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(cast(b.sku_id as char) like :keyword "
                    + "or coalesce(s.sku_code, '') like :keyword "
                    + "or coalesce(s.barcode, '') like :keyword "
                    + "or coalesce(s.sku_name, '') like :keyword "
                    + "or coalesce(p.item_no, '') like :keyword "
                    + "or coalesce(p.product_name, '') like :keyword)");
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        var where = where(conditions);
        parameters.addValue("limit", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());
        var records = jdbc.query("""
                select b.warehouse_id, b.sku_id, s.sku_code, s.barcode, p.item_no,
                       p.product_name, s.sku_name, s.spec_text, b.quantity
                from inventory_balance b
                left join product_sku s on s.id = b.sku_id
                left join product_spu p on p.id = s.product_id
                """ + where + " order by b.warehouse_id asc, b.sku_id asc limit :limit offset :offset",
                parameters,
                (rs, rowNum) -> new InventoryBalanceView(
                        rs.getLong("warehouse_id"), rs.getLong("sku_id"), rs.getString("sku_code"),
                        rs.getString("barcode"), rs.getString("item_no"), rs.getString("product_name"),
                        rs.getString("sku_name"), rs.getString("spec_text"), rs.getBigDecimal("quantity")
                )
        );
        var total = jdbc.queryForObject(
                "select count(*) from inventory_balance b "
                        + "left join product_sku s on s.id=b.sku_id "
                        + "left join product_spu p on p.id=s.product_id " + where,
                parameters,
                Long.class
        );
        return new PageImpl<>(records, pageable, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Page<InventoryLedgerView> listLedger(
            Long warehouseId,
            Long skuId,
            String direction,
            String sourceType,
            Pageable pageable
    ) {
        var parameters = new MapSqlParameterSource();
        var conditions = new ArrayList<String>();
        if (warehouseId != null) {
            conditions.add("l.warehouse_id = :warehouseId");
            parameters.addValue("warehouseId", warehouseId);
        }
        if (skuId != null) {
            conditions.add("l.sku_id = :skuId");
            parameters.addValue("skuId", skuId);
        }
        if (direction != null && !direction.isBlank()) {
            conditions.add("l.direction = :direction");
            parameters.addValue("direction", direction.trim());
        }
        if (sourceType != null && !sourceType.isBlank()) {
            conditions.add("l.source_type = :sourceType");
            parameters.addValue("sourceType", sourceType.trim());
        }
        var where = where(conditions);
        parameters.addValue("limit", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());
        var records = jdbc.query("""
                select l.id, l.warehouse_id, l.sku_id, l.direction, l.quantity,
                       l.before_quantity, l.after_quantity, l.source_type, l.source_id,
                       l.source_no, l.occurred_at, l.operator_mobile
                from inventory_ledger l
                """ + where + " order by l.occurred_at desc, l.id desc limit :limit offset :offset",
                parameters,
                (rs, rowNum) -> new InventoryLedgerView(
                        rs.getLong("id"), rs.getLong("warehouse_id"), rs.getLong("sku_id"),
                        rs.getString("direction"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
                        rs.getString("source_type"), rs.getLong("source_id"), rs.getString("source_no"),
                        rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getString("operator_mobile")
                )
        );
        var total = jdbc.queryForObject(
                "select count(*) from inventory_ledger l " + where,
                parameters,
                Long.class
        );
        return new PageImpl<>(records, pageable, total == null ? 0 : total);
    }

    private String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }
}
