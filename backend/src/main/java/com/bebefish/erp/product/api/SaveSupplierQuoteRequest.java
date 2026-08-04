package com.bebefish.erp.product.api;

import com.bebefish.erp.product.application.SaveSupplierQuoteCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SaveSupplierQuoteRequest(
        @NotNull @Positive Long supplierId,
        @Size(max = 100) String supplierItemNo,
        @NotNull @DecimalMin("0") BigDecimal purchasePrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal minPurchaseQuantity,
        boolean defaultQuote,
        boolean syncStandardCost
) {
    SaveSupplierQuoteCommand toCommand() {
        return new SaveSupplierQuoteCommand(
                supplierId, supplierItemNo, purchasePrice, minPurchaseQuantity, defaultQuote
        );
    }
}
