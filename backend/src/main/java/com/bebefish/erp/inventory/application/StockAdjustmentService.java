package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.domain.StockAdjustment;
import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import com.bebefish.erp.inventory.domain.StockAdjustmentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentService {
    private static final String DRAFT = "draft";
    private static final String CONFIRMED = "confirmed";
    private static final String VOIDED = "voided";
    private final StockAdjustmentRepository repository;
    private final InventoryService inventoryService;

    public StockAdjustmentService(
            StockAdjustmentRepository repository,
            InventoryService inventoryService
    ) {
        this.repository = repository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public StockAdjustment create(StockAdjustmentCommand command) {
        var now = LocalDateTime.now();
        var normalized = normalize(command);
        return repository.save(new StockAdjustment(
                null,
                generateAdjustmentNo(),
                normalized.warehouseId(),
                normalized.reason(),
                DRAFT,
                normalized.remark(),
                normalized.items(),
                now,
                now
        ));
    }

    @Transactional
    public StockAdjustment update(long id, StockAdjustmentCommand command) {
        var existing = get(id);
        ensureDraft(existing, "已确认的库存调整单不可修改");
        var normalized = normalize(command);
        return repository.save(new StockAdjustment(
                existing.id(), existing.adjustmentNo(), normalized.warehouseId(), normalized.reason(),
                DRAFT, normalized.remark(), normalized.items(), existing.createdAt(), LocalDateTime.now()
        ));
    }

    @Transactional
    public void delete(long id) {
        var existing = get(id);
        ensureDraft(existing, "已确认的库存调整单不可删除");
        repository.deleteById(id);
    }

    @Transactional
    public StockAdjustment confirm(long id, String operatorMobile) {
        var existing = get(id);
        ensureDraft(existing, "库存调整单已确认，请勿重复操作");
        inventoryService.adjust(
                existing.warehouseId(),
                existing.items().stream()
                        .map(item -> new InventoryChange(item.skuId(), item.quantityDelta()))
                        .toList(),
                new InventorySource("adjustment", existing.id(), existing.adjustmentNo()),
                operatorMobile
        );
        return repository.save(withStatus(existing, CONFIRMED, existing.remark()));
    }

    @Transactional
    public StockAdjustment voidAdjustment(long id, String reason, String operatorMobile) {
        var existing = get(id);
        if (!CONFIRMED.equals(existing.status())) {
            throw new BusinessException("ADJUSTMENT_NOT_CONFIRMED", HttpStatus.BAD_REQUEST, "只有已确认的库存调整单才能作废");
        }
        inventoryService.adjust(
                existing.warehouseId(),
                existing.items().stream()
                        .map(item -> new InventoryChange(item.skuId(), item.quantityDelta().negate()))
                        .toList(),
                new InventorySource("adjustment_void", existing.id(), existing.adjustmentNo()),
                operatorMobile
        );
        return repository.save(withStatus(existing, VOIDED, reason));
    }

    @Transactional(readOnly = true)
    public StockAdjustment get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "ADJUSTMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "库存调整单不存在"
        ));
    }

    private StockAdjustmentCommand normalize(StockAdjustmentCommand command) {
        if (command == null || command.warehouseId() <= 0) {
            throw validation("仓库不能为空");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw validation("库存调整明细不能为空");
        }
        var skuIds = new HashSet<Long>();
        for (var item : command.items()) {
            if (item == null || item.skuId() <= 0 || item.quantityDelta() == null
                    || item.quantityDelta().signum() == 0 || !skuIds.add(item.skuId())) {
                throw validation("库存调整明细无效或 SKU 重复");
            }
        }
        return new StockAdjustmentCommand(
                command.warehouseId(),
                List.copyOf(command.items()),
                required(command.reason(), "调整原因不能为空"),
                optional(command.remark())
        );
    }

    private void ensureDraft(StockAdjustment adjustment, String message) {
        if (!DRAFT.equals(adjustment.status())) {
            throw new BusinessException("ADJUSTMENT_NOT_DRAFT", HttpStatus.BAD_REQUEST, message);
        }
    }

    private StockAdjustment withStatus(StockAdjustment adjustment, String status, String remark) {
        return new StockAdjustment(
                adjustment.id(), adjustment.adjustmentNo(), adjustment.warehouseId(), adjustment.reason(),
                status, optional(remark), adjustment.items(), adjustment.createdAt(), LocalDateTime.now()
        );
    }

    private String generateAdjustmentNo() {
        return "ADJ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String required(String value, String message) {
        var normalized = optional(value);
        if (normalized == null) {
            throw validation(message);
        }
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
