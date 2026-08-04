package com.bebefish.erp.product.domain;

import java.math.BigDecimal;

public record SupplierQuote(
        Long id,
        Long skuId,
        Long supplierId,
        String supplierItemNo,
        BigDecimal purchasePrice,
        BigDecimal minPurchaseQuantity,
        boolean isDefault,
        String status
) {
    public SupplierQuote withDefault(boolean nextDefault) {
        return new SupplierQuote(
                id, skuId, supplierId, supplierItemNo, purchasePrice,
                minPurchaseQuantity, nextDefault, status
        );
    }
}
