package com.bebefish.erp.product.api;

import com.bebefish.erp.product.domain.SupplierQuote;
import java.math.BigDecimal;

public record SupplierQuoteResponse(
        Long id,
        Long skuId,
        Long supplierId,
        String supplierItemNo,
        BigDecimal purchasePrice,
        BigDecimal minPurchaseQuantity,
        boolean defaultQuote,
        String status
) {
    static SupplierQuoteResponse from(SupplierQuote quote) {
        return new SupplierQuoteResponse(
                quote.id(), quote.skuId(), quote.supplierId(), quote.supplierItemNo(),
                quote.purchasePrice(), quote.minPurchaseQuantity(), quote.isDefault(), quote.status()
        );
    }
}
