package com.bebefish.erp.product.application;

import java.math.BigDecimal;

public record ProductSupplierQuoteCommand(
        Long id,
        Long supplierId,
        String supplierItemNo,
        BigDecimal purchasePrice,
        BigDecimal minPurchaseQuantity,
        boolean defaultQuote,
        String status
) {
}
