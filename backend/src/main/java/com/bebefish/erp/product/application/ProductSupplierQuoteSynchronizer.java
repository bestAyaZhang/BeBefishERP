package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.SupplierQuote;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        var retainedQuoteIds = normalized.stream()
                .map(ProductSupplierQuoteCommand::id)
                .filter(id -> id != null)
                .toList();
        repository.deleteBySkuIdExcept(skuId, retainedQuoteIds);
        if (normalized.stream().anyMatch(ProductSupplierQuoteCommand::defaultQuote)) {
            repository.clearDefault(skuId);
        }
        for (var command : normalized) {
            repository.save(new SupplierQuote(
                    command.id(), skuId, command.supplierId(), command.supplierItemNo(),
                    command.purchasePrice(), command.minPurchaseQuantity(), command.defaultQuote(), command.status()
            ));
        }
    }

    private List<ProductSupplierQuoteCommand> normalize(List<ProductSupplierQuoteCommand> commands) {
        if (commands == null) {
            throw validation("供应商报价列表不能为空");
        }
        var suppliers = new HashSet<Long>();
        var result = new ArrayList<ProductSupplierQuoteCommand>();
        var enabledDefaultCount = 0;
        for (var command : commands) {
            if (command == null || command.supplierId() == null || command.supplierId() <= 0) {
                throw validation("供应商不能为空");
            }
            if (!suppliers.add(command.supplierId())) {
                throw validation("同一 SKU 的供应商报价不能重复");
            }
            var purchasePrice = nonNegative(command.purchasePrice(), "采购价不能小于 0");
            var minQuantity = positive(command.minPurchaseQuantity(), "最小采购量必须大于 0");
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
