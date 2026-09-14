package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseLayoutService {
    public record Layout(long revision, JsonNode document) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WarehouseService warehouses;

    public WarehouseLayoutService(JdbcTemplate jdbc, ObjectMapper mapper, WarehouseService warehouses) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public Layout load(long id) {
        warehouses.get(id);
        return read(id);
    }

    private Layout read(long id) {
        var rows = jdbc.query("SELECT revision, layout_json FROM warehouse_layout WHERE warehouse_id = ?",
            (rs, row) -> {
                try { return new Layout(rs.getLong(1), mapper.readTree(rs.getString(2))); }
                catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored warehouse layout", e); }
            }, id);
        return rows.isEmpty() ? new Layout(0, null) : rows.get(0);
    }

    @Transactional
    public Layout save(long id, Layout value) {
        warehouses.get(id);
        validate(value);
        // Serialize first writes as well as updates against the warehouse row.
        jdbc.queryForObject("SELECT id FROM warehouse WHERE id = ? FOR UPDATE", Long.class, id);
        var existing = read(id);
        if (existing.revision() != value.revision()) {
            throw new BusinessException("LAYOUT_CONFLICT", HttpStatus.CONFLICT, "规划已被其他人修改，请刷新后再保存");
        }
        long revision = existing.revision() + 1;
        if (existing.revision() == 0) {
            jdbc.update("INSERT INTO warehouse_layout (warehouse_id, revision, layout_json) VALUES (?, ?, ?)", id, revision, value.document().toString());
        } else {
            jdbc.update("UPDATE warehouse_layout SET revision = ?, layout_json = ?, updated_at = CURRENT_TIMESTAMP WHERE warehouse_id = ?", revision, value.document().toString(), id);
        }
        return new Layout(revision, value.document());
    }

    static void validate(Layout value) {
        var doc = value.document();
        if (value.revision() < 0 || doc == null || !doc.isObject() || doc.path("schemaVersion").asInt() != 1
            || !doc.path("structure").isObject() || !doc.path("structure").path("outline").path("nodes").isArray()
            || !doc.path("structure").path("partitions").isArray() || !doc.path("structure").path("doors").isArray()
            || !doc.path("palletGroups").isArray() || !doc.path("completed").isBoolean() || doc.toString().length() > 1_000_000) {
            throw new BusinessException("INVALID_LAYOUT", HttpStatus.BAD_REQUEST, "规划数据格式不正确或内容过大");
        }
    }
}
