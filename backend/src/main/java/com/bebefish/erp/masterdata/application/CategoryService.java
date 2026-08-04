package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Category;
import com.bebefish.erp.masterdata.domain.CategoryRepository;
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
public class CategoryService {
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Category create(SaveCategoryCommand command) {
        var normalized = normalize(command);
        var code = normalized.code() == null ? generateCategoryCode() : normalized.code();
        ensureUnique(code, normalized.name(), null);
        return repository.save(new Category(
                null,
                code,
                normalized.name(),
                null,
                1,
                normalized.sortOrder(),
                "enabled",
                normalized.remark()
        ));
    }

    @Transactional
    public Category update(long id, SaveCategoryCommand command) {
        var existing = get(id);
        var normalized = normalize(command);
        var code = normalized.code() == null ? existing.code() : normalized.code();
        ensureUnique(code, normalized.name(), id);
        return repository.save(new Category(
                existing.id(),
                code,
                normalized.name(),
                null,
                1,
                normalized.sortOrder(),
                existing.status(),
                normalized.remark()
        ));
    }

    @Transactional
    public Category changeStatus(long id, String status) {
        var existing = get(id);
        var normalizedStatus = normalizeStatus(status);
        return repository.save(new Category(
                existing.id(), existing.code(), existing.name(), null, 1,
                existing.sortOrder(), normalizedStatus, existing.remark()
        ));
    }

    @Transactional(readOnly = true)
    public Category get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "分类不存在"
        ));
    }

    @Transactional(readOnly = true)
    public Page<Category> list(String keyword, String status, Pageable pageable) {
        var normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        var normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        return repository.findAll(normalizedKeyword, normalizedStatus, pageable);
    }

    private SaveCategoryCommand normalize(SaveCategoryCommand command) {
        if (command == null) {
            throw validationError("分类信息不能为空");
        }
        var code = optional(command.code());
        var name = required(command.name(), "分类名称不能为空");
        if (command.sortOrder() < 0) {
            throw validationError("排序号不能小于 0");
        }
        var remark = command.remark() == null || command.remark().isBlank()
                ? null
                : command.remark().trim();
        return new SaveCategoryCommand(code, name, command.sortOrder(), remark);
    }

    private String generateCategoryCode() {
        String candidate;
        do {
            candidate = "CAT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (repository.existsByCode(candidate, null));
        return candidate;
    }

    private void ensureUnique(String code, String name, Long excludedId) {
        if (repository.existsByCode(code, excludedId)) {
            throw new BusinessException(
                    "DUPLICATE_CATEGORY_CODE", HttpStatus.CONFLICT, "分类编码已存在"
            );
        }
        if (repository.existsByName(name, excludedId)) {
            throw new BusinessException(
                    "DUPLICATE_CATEGORY_NAME", HttpStatus.CONFLICT, "分类名称已存在"
            );
        }
    }

    private String normalizeStatus(String status) {
        var normalized = required(status, "分类状态不能为空").toLowerCase();
        if (!STATUSES.contains(normalized)) {
            throw validationError("分类状态无效");
        }
        return normalized;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw validationError(message);
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException validationError(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
