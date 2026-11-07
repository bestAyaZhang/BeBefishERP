package com.bebefish.erp.product.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        String status,
        Long productPhotoFileId
) {
    public Sku {
        specificationValues = List.copyOf(specificationValues);
    }

    public Sku(
            Long id, String code, String barcode, String name, String specText, List<String> specificationValues,
            String salesUnit, BigDecimal defaultSalePrice, BigDecimal standardCost, BigDecimal safetyStockQuantity,
            Packaging packaging, Long skuImageFileId, boolean isDefault, String status
    ) {
        this(id, code, barcode, name, specText, specificationValues, salesUnit, defaultSalePrice, standardCost,
                safetyStockQuantity, packaging, skuImageFileId, isDefault, status, null);
    }

    public Stream<Long> imageFileIds() {
        return Stream.of(
                skuImageFileId, productPhotoFileId,
                packaging == null ? null : packaging.packageImageFileId(),
                packaging == null ? null : packaging.cartonImageFileId()
        ).filter(Objects::nonNull);
    }

    public BigDecimal packageVolumeCm3() {
        return packaging == null ? null : packaging.volumeCm3();
    }

    public Sku withId(Long skuId) {
        return new Sku(
                skuId, code, barcode, name, specText, specificationValues,
                salesUnit, defaultSalePrice, standardCost, safetyStockQuantity, packaging,
                skuImageFileId, isDefault, status, productPhotoFileId
        );
    }

    public Sku withStandardCost(BigDecimal nextStandardCost) {
        return new Sku(
                id, code, barcode, name, specText, specificationValues,
                salesUnit, defaultSalePrice, nextStandardCost, safetyStockQuantity, packaging,
                skuImageFileId, isDefault, status, productPhotoFileId
        );
    }

    public Sku withState(boolean nextDefault, String nextStatus) {
        return new Sku(
                id, code, barcode, name, specText, specificationValues,
                salesUnit, defaultSalePrice, standardCost, safetyStockQuantity, packaging,
                skuImageFileId, nextDefault, nextStatus, productPhotoFileId
        );
    }
}
