package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.SupplierQuote;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductSupplierQuoteSynchronizer {
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");
    private final SupplierQuoteRepository repository;

    public ProductSupplierQuoteSynchronizer(SupplierQuoteRepository repository) {
        this.repository = repository;
    }

    public void synchronize(long skuId, List<ProductSupplierQuoteCommand> commands) {
        var normalized = normalize(commands);
        var existingById = repository.findBySkuId(skuId).stream()
                .collect(Collectors.toMap(SupplierQuote::id, quote -> quote));
        validateQuoteOwnership(existingById, normalized);
        var retainedQuoteIds = normalized.stream()
                .filter(command -> command.id() != null)
                .filter(command -> !supplierChanged(existingById.get(command.id()), command))
                .map(ProductSupplierQuoteCommand::id)
                .toList();
        repository.deleteBySkuIdExcept(skuId, retainedQuoteIds);
        if (normalized.stream().anyMatch(ProductSupplierQuoteCommand::defaultQuote)) {
            repository.clearDefault(skuId);
        }
        for (var command : normalized) {
            var quoteId = supplierChanged(existingById.get(command.id()), command) ? null : command.id();
            repository.save(new SupplierQuote(
                    quoteId, skuId, command.supplierId(), command.supplierItemNo(),
                    command.purchasePrice(), command.minPurchaseQuantity(), command.defaultQuote(), command.status()
            ));
        }
    }

    private boolean supplierChanged(SupplierQuote existing, ProductSupplierQuoteCommand command) {
        return existing != null && !existing.supplierId().equals(command.supplierId());
    }

    private List<ProductSupplierQuoteCommand> normalize(List<ProductSupplierQuoteCommand> commands) {
        if (commands == null) {
            throw validation("供应商报价列表不能为空");
        }
        var suppliers = new HashSet<Long>();
        var quoteIds = new HashSet<Long>();
        var result = new ArrayList<ProductSupplierQuoteCommand>();
        var enabledDefaultCount = 0;
        for (var command : commands) {
            if (command == null || command.supplierId() == null || command.supplierId() <= 0) {
                throw validation("供应商不能为空");
            }
            if (!suppliers.add(command.supplierId())) {
                throw validation("同一 SKU 的供应商报价不能重复");
            }
            if (command.id() != null && !quoteIds.add(command.id())) {
                throw validation("供应商报价 ID 不能重复");
            }
            var purchasePrice = DecimalConstraints.requireFits(
                    nonNegative(command.purchasePrice(), "采购价不能小于 0"), 15, 4, "采购价"
            );
            var minQuantity = DecimalConstraints.requireFits(
                    positive(command.minPurchaseQuantity(), "最小采购量必须大于 0"),
                    15, 4, "最小采购量"
            );
            var status = normalizeStatus(command.status());
            if (command.defaultQuote() && !"enabled".equals(status)) {
                throw validation("禁用报价不能设为默认报价");
            }
            if (command.defaultQuote() && ++enabledDefaultCount > 1) {
                throw validation("同一 SKU 最多只能有一个启用的默认报价");
            }
            result.add(new ProductSupplierQuoteCommand(
                    command.id(), command.supplierId(), optional(command.supplierItemNo()), purchasePrice,
                    minQuantity, command.defaultQuote(), status
            ));
        }
        return List.copyOf(result);
    }

    private void validateQuoteOwnership(
            Map<Long, SupplierQuote> existingById,
            List<ProductSupplierQuoteCommand> commands
    ) {
        for (var command : commands) {
            if (command.id() != null && !existingById.containsKey(command.id())) {
                throw validation("供应商报价不属于该 SKU");
            }
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw validation(message);
        }
        return value;
    }

    private BigDecimal positive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw validation(message);
        }
        return value;
    }

    private String normalizeStatus(String status) {
        var value = status == null || status.isBlank() ? "enabled" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw validation("供应商报价状态无效");
        }
        return value;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
