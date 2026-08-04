package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.bebefish.erp.masterdata.domain.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");
    private final WarehouseRepository repository;

    public WarehouseService(WarehouseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Warehouse create(SaveWarehouseCommand command) {
        var value = normalize(command);
        var number = value.number() == null ? generateWarehouseNumber() : value.number();
        ensureUnique(number, value.name(), null);
        if (value.isDefault()) repository.clearDefault();
        return repository.save(new Warehouse(null, number, value.name(), value.address(),
                value.isDefault(), "enabled", value.remark()));
    }

    @Transactional
    public Warehouse update(long id, SaveWarehouseCommand command) {
        var existing = get(id);
        var value = normalize(command);
        var number = value.number() == null ? existing.number() : value.number();
        ensureUnique(number, value.name(), id);
        if (existing.isDefault() && !value.isDefault()) {
            throw new BusinessException("DEFAULT_WAREHOUSE_REQUIRED", HttpStatus.CONFLICT, "请先指定另一个默认仓库");
        }
        if (value.isDefault()) repository.clearDefault();
        return repository.save(new Warehouse(id, number, value.name(), value.address(),
                value.isDefault(), existing.status(), value.remark()));
    }

    @Transactional
    public Warehouse setDefault(long id) {
        var target = get(id);
        if (!"enabled".equals(target.status())) {
            throw new BusinessException("WAREHOUSE_DISABLED", HttpStatus.CONFLICT, "禁用仓库不能设为默认仓库");
        }
        repository.clearDefault();
        return repository.save(new Warehouse(target.id(), target.number(), target.name(), target.address(),
                true, target.status(), target.remark()));
    }

    @Transactional
    public Warehouse changeStatus(long id, String status) {
        var existing = get(id);
        var normalized = normalizeStatus(status);
        if (existing.isDefault() && "disabled".equals(normalized)) {
            throw new BusinessException("DEFAULT_WAREHOUSE_REQUIRED", HttpStatus.CONFLICT, "请先指定另一个默认仓库");
        }
        return repository.save(new Warehouse(existing.id(), existing.number(), existing.name(), existing.address(),
                existing.isDefault(), normalized, existing.remark()));
    }

    @Transactional(readOnly = true)
    public Warehouse get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "WAREHOUSE_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在"));
    }

    @Transactional(readOnly = true)
    public Warehouse getDefaultWarehouse() {
        return repository.findDefault().orElseThrow(() -> new BusinessException(
                "DEFAULT_WAREHOUSE_REQUIRED", HttpStatus.CONFLICT, "未设置默认仓库"));
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> list(String keyword, String status, Pageable pageable) {
        var normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        return repository.findAll(optional(keyword), normalizedStatus, pageable);
    }

    private SaveWarehouseCommand normalize(SaveWarehouseCommand command) {
        if (command == null) throw validation("仓库信息不能为空");
        return new SaveWarehouseCommand(optional(command.number()),
                required(command.name(), "仓库名称不能为空"), optional(command.address()),
                command.isDefault(), optional(command.remark()));
    }
    private String generateWarehouseNumber() {
        String candidate;
        do {
            candidate = "WH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (repository.existsByNumber(candidate, null));
        return candidate;
    }
    private void ensureUnique(String number, String name, Long excludedId) {
        if (repository.existsByNumber(number, excludedId)) throw new BusinessException("DUPLICATE_WAREHOUSE_NO", HttpStatus.CONFLICT, "仓库编号已存在");
        if (repository.existsByName(name, excludedId)) throw new BusinessException("DUPLICATE_WAREHOUSE_NAME", HttpStatus.CONFLICT, "仓库名称已存在");
    }
    private String normalizeStatus(String status) {
        var value = required(status, "仓库状态不能为空").toLowerCase();
        if (!STATUSES.contains(value)) throw validation("仓库状态无效");
        return value;
    }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw validation(message); return value.trim(); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException validation(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
}
