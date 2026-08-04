package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Supplier;
import com.bebefish.erp.masterdata.domain.SupplierRepository;
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
public class SupplierService {
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");
    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Supplier create(SaveSupplierCommand command) {
        var value = normalize(command);
        var number = value.number() == null ? generateSupplierNumber() : value.number();
        ensureUnique(number, null);
        return repository.save(new Supplier(
                null, number, value.name(), value.contactPerson(), value.mobile(), value.telephone(),
                value.address(), "enabled", value.remark()
        ));
    }

    @Transactional
    public Supplier update(long id, SaveSupplierCommand command) {
        var existing = get(id);
        var value = normalize(command);
        var number = value.number() == null ? existing.number() : value.number();
        ensureUnique(number, id);
        return repository.save(new Supplier(
                id, number, value.name(), value.contactPerson(), value.mobile(), value.telephone(),
                value.address(), existing.status(), value.remark()
        ));
    }

    @Transactional
    public Supplier changeStatus(long id, String status) {
        var existing = get(id);
        var normalized = normalizeStatus(status);
        return repository.save(new Supplier(
                id, existing.number(), existing.name(), existing.contactPerson(), existing.mobile(),
                existing.telephone(), existing.address(), normalized, existing.remark()
        ));
    }

    @Transactional(readOnly = true)
    public Supplier get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "SUPPLIER_NOT_FOUND", HttpStatus.NOT_FOUND, "供应商不存在"
        ));
    }

    @Transactional(readOnly = true)
    public Page<Supplier> list(String keyword, String status, Pageable pageable) {
        return repository.findAll(optional(keyword), status == null || status.isBlank() ? null : normalizeStatus(status), pageable);
    }

    private SaveSupplierCommand normalize(SaveSupplierCommand command) {
        if (command == null) throw validation("供应商信息不能为空");
        return new SaveSupplierCommand(
                optional(command.number()), required(command.name(), "供应商名称不能为空"),
                optional(command.contactPerson()), optional(command.mobile()), optional(command.telephone()),
                optional(command.address()), optional(command.remark())
        );
    }

    private String generateSupplierNumber() {
        String candidate;
        do {
            candidate = "SUP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (repository.existsByNumber(candidate, null));
        return candidate;
    }

    private void ensureUnique(String number, Long excludedId) {
        if (repository.existsByNumber(number, excludedId)) {
            throw new BusinessException("DUPLICATE_SUPPLIER_NO", HttpStatus.CONFLICT, "供应商编号已存在");
        }
    }

    private String normalizeStatus(String status) {
        var value = required(status, "供应商状态不能为空").toLowerCase();
        if (!STATUSES.contains(value)) throw validation("供应商状态无效");
        return value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw validation(message);
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
