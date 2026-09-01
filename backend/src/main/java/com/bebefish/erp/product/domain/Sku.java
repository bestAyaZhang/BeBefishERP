package com.bebefish.erp.product.domain;

import java.math.BigDecimal;
import java.util.List;

public record Sku(
        Long id,
        String code,
        String barcode,
        String name,
        String specText,
        List<String> specificationValues,
        String salesUnit,
        BigDecimal defaultSalePrice,
        BigDecimal standardCost,
        BigDecimal safetyStockQuantity,
        Packaging packaging,
        Long skuImageFileId,
        boolean isDefault,
        String status
) {
    public Sku {
        specificationValues = List.copyOf(specificationValues);
    }

    public BigDecimal packageVolumeCm3() {
        return packaging == null ? null : packaging.volumeCm3();
    }

    public Sku withId(Long skuId) {
        return new Sku(
                skuId, code, barcode, name, specText, specificationValues,
                salesUnit, defaultSalePrice, standardCost, safetyStockQuantity, packaging,
                skuImageFileId, isDefault, status
        );
    }

    public Sku withStandardCost(BigDecimal nextStandardCost) {
        return new Sku(
                id, code, barcode, name, specText, specificationValues,
                salesUnit, defaultSalePrice, nextStandardCost, safetyStockQuantity, packaging,
                skuImageFileId, isDefault, status
        );
    }
}
