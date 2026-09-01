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
        Long skuImageFileId
) {
    public SaveSkuCommand {
        specificationValues = specificationValues == null ? List.of() : List.copyOf(specificationValues);
    }
}
