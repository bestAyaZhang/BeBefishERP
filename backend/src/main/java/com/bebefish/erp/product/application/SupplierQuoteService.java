package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.SupplierQuote;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierQuoteService {
    private final SupplierQuoteRepository repository;
    private final ProductRepository productRepository;

    public SupplierQuoteService(
            SupplierQuoteRepository repository,
            ProductRepository productRepository
    ) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    @Transactional
    public SupplierQuote saveQuote(long skuId, SaveSupplierQuoteCommand command) {
        requireSku(skuId);
        var value = normalize(command);
        if (repository.existsBySkuIdAndSupplierId(skuId, value.supplierId(), null)) {
            throw conflict("SUPPLIER_QUOTE_CONFLICT", "该供应商已有此 SKU 的采购报价");
        }
        if (value.defaultQuote()) {
            repository.clearDefault(skuId);
        }
        return repository.save(new SupplierQuote(
                null, skuId, value.supplierId(), value.supplierItemNo(), value.purchasePrice(),
                value.minPurchaseQuantity(), value.defaultQuote(), "enabled"
        ));
    }

    @Transactional
    public SupplierQuote setDefaultQuote(long skuId, long quoteId, boolean syncStandardCost) {
        requireSku(skuId);
        var quote = get(quoteId);
        if (quote.skuId() != skuId) {
            throw validation("报价不属于该 SKU");
        }
        if (!"enabled".equals(quote.status())) {
            throw conflict("SUPPLIER_QUOTE_DISABLED", "禁用报价不能设为默认报价");
        }
        repository.clearDefault(skuId);
        var saved = repository.save(quote.withDefault(true));
        if (syncStandardCost) {
            productRepository.updateSkuStandardCost(skuId, saved.purchasePrice());
        }
        return saved;
    }

    @Transactional
    public SupplierQuote updateQuote(
            long skuId,
            long quoteId,
            SaveSupplierQuoteCommand command,
            boolean syncStandardCost
    ) {
        requireSku(skuId);
        var existing = get(quoteId);
        if (existing.skuId() != skuId) {
            throw validation("报价不属于该 SKU");
        }
        var value = normalize(command);
        if (repository.existsBySkuIdAndSupplierId(skuId, value.supplierId(), quoteId)) {
            throw conflict("SUPPLIER_QUOTE_CONFLICT", "该供应商已有此 SKU 的采购报价");
        }
        if (value.defaultQuote()) {
            repository.clearDefault(skuId);
        }
        var saved = repository.save(new SupplierQuote(
                existing.id(), skuId, value.supplierId(), value.supplierItemNo(), value.purchasePrice(),
                value.minPurchaseQuantity(), value.defaultQuote(), existing.status()
        ));
        if (saved.isDefault() && syncStandardCost) {
            productRepository.updateSkuStandardCost(skuId, saved.purchasePrice());
        }
        return saved;
    }

    public List<SupplierQuote> listQuotes(long skuId) {
        requireSku(skuId);
        return repository.findBySkuId(skuId);
    }

    private void requireSku(long skuId) {
        if (skuId <= 0 || productRepository.findSku(skuId).isEmpty()) {
            throw new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在");
        }
    }

    private SupplierQuote get(long quoteId) {
        return repository.findById(quoteId).orElseThrow(() -> new BusinessException(
                "SUPPLIER_QUOTE_NOT_FOUND", HttpStatus.NOT_FOUND, "供应商报价不存在"
        ));
    }

    private SaveSupplierQuoteCommand normalize(SaveSupplierQuoteCommand command) {
        if (command == null || command.supplierId() == null || command.supplierId() <= 0) {
            throw validation("供应商不能为空");
        }
        var purchasePrice = nonNegative(command.purchasePrice(), "采购价不能小于 0");
        var minQuantity = positive(command.minPurchaseQuantity(), "最小采购量必须大于 0");
        return new SaveSupplierQuoteCommand(
                command.supplierId(), optional(command.supplierItemNo()), purchasePrice,
                minQuantity, command.defaultQuote()
        );
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

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(code, HttpStatus.CONFLICT, message);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
