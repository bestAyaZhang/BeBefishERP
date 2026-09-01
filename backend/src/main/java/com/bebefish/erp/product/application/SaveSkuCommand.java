package com.bebefish.erp.product.application;

import java.math.BigDecimal;
import java.util.List;

public record SaveSkuCommand(
        Long id,
        String skuCode,
        String barcode,
        String skuName,
        List<String> specificationValues,
        String salesUnit,
        BigDecimal defaultSalePrice,
        BigDecimal standardCost,
        BigDecimal safetyStockQuantity,
        PackagingCommand packaging,
        Long skuImageFileId,
        List<ProductSupplierQuoteCommand> supplierQuotes
) {
    public SaveSkuCommand {
        specificationValues = specificationValues == null ? List.of() : List.copyOf(specificationValues);
        supplierQuotes = supplierQuotes == null ? null : List.copyOf(supplierQuotes);
    }

    public SaveSkuCommand(
            Long id,
            String skuCode,
            String barcode,
            String skuName,
            List<String> specificationValues,
            String salesUnit,
            BigDecimal defaultSalePrice,
            BigDecimal standardCost,
            BigDecimal safetyStockQuantity,
            PackagingCommand packaging,
            Long skuImageFileId
    ) {
        this(
                id, skuCode, barcode, skuName, specificationValues, salesUnit, defaultSalePrice,
                standardCost, safetyStockQuantity, packaging, skuImageFileId, null
        );
    }
}
