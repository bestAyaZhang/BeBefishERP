package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.WarehousePileReleaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseLayoutService {
    public record Layout(long revision, JsonNode document) {}
    public record SaveCommand(long revision, JsonNode document,
                              List<WarehousePileReleaseService.ReleaseConfirmation> releasedPileAllocations) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WarehouseService warehouses;
    private final WarehousePileReleaseService pileRelease;

    public WarehouseLayoutService(JdbcTemplate jdbc, ObjectMapper mapper, WarehouseService warehouses,
                                  WarehousePileReleaseService pileRelease) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.warehouses = warehouses;
        this.pileRelease = pileRelease;
    }

    @Transactional(readOnly = true)
    public Layout load(long id) {
        warehouses.get(id);
        return read(id, false);
    }

    private Layout read(long id, boolean forUpdate) {
        var sql = "SELECT revision, layout_json FROM warehouse_layout WHERE warehouse_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        var rows = jdbc.query(sql,
            (rs, row) -> {
                try { return new Layout(rs.getLong(1), mapper.readTree(rs.getString(2))); }
                catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored warehouse layout", e); }
            }, id);
        return rows.isEmpty() ? new Layout(0, null) : rows.get(0);
    }

    @Transactional
    public Layout save(long id, Layout value) {
        return save(id, new SaveCommand(value.revision(), value.document(), List.of()));
    }

    @Transactional
    public Layout save(long id, SaveCommand command) {
        var value = new Layout(command.revision(), command.document());
        warehouses.get(id);
        validate(value);
        // Serialize first writes as well as updates against the warehouse row.
        jdbc.queryForObject("SELECT id FROM warehouse WHERE id = ? FOR UPDATE", Long.class, id);
        Layout existing;
        try {
            existing = read(id, true);
        } catch (CannotAcquireLockException error) {
            throw layoutConflict();
        }
        if (existing.revision() != value.revision()) {
            throw layoutConflict();
        }
        pileRelease.releaseRemovedPiles(id, existing.document(), value.document(),
                command.releasedPileAllocations());
        long revision = existing.revision() + 1;
        if (existing.revision() == 0) {
            jdbc.update("INSERT INTO warehouse_layout (warehouse_id, revision, layout_json) VALUES (?, ?, ?)", id, revision, value.document().toString());
        } else {
            jdbc.update("UPDATE warehouse_layout SET revision = ?, layout_json = ?, updated_at = CURRENT_TIMESTAMP WHERE warehouse_id = ?", revision, value.document().toString(), id);
        }
        return new Layout(revision, value.document());
    }

    private BusinessException layoutConflict() {
        return new BusinessException("LAYOUT_CONFLICT", HttpStatus.CONFLICT, "规划已被其他人修改，请刷新后再保存");
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
