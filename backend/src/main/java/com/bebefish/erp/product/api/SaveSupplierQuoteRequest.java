package com.bebefish.erp.product.api;

import com.bebefish.erp.product.application.SaveSupplierQuoteCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SaveSupplierQuoteRequest(
        @NotNull @Positive Long supplierId,
        @Size(max = 100) String supplierItemNo,
        @NotNull @DecimalMin(value = "0", message = "采购价不能小于 0")
        @Digits(integer = 15, fraction = 4, message = "采购价最多允许 15 位整数和 4 位小数")
        BigDecimal purchasePrice,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "最小采购量必须大于 0")
        @Digits(integer = 15, fraction = 4, message = "最小采购量最多允许 15 位整数和 4 位小数")
        BigDecimal minPurchaseQuantity,
        boolean defaultQuote,
        boolean syncStandardCost
) {
    SaveSupplierQuoteCommand toCommand() {
        return new SaveSupplierQuoteCommand(
                supplierId, supplierItemNo, purchasePrice, minPurchaseQuantity, defaultQuote
        );
    }
}
