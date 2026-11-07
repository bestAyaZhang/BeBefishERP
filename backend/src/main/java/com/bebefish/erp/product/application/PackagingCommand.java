package com.bebefish.erp.product.application;

import java.math.BigDecimal;

public record PackagingCommand(
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal volumeCm3,
        BigDecimal innerLengthCm,
        BigDecimal innerWidthCm,
        BigDecimal innerHeightCm,
        BigDecimal productLengthCm,
        BigDecimal productWidthCm,
        BigDecimal productHeightCm,
        BigDecimal capacityMl,
        BigDecimal netWeightKg,
        BigDecimal grossWeightKg,
        BigDecimal gramWeightG,
        BigDecimal innerWeightKg,
        String method,
        Integer cartonQuantity,
        Long packageImageFileId,
        Long cartonImageFileId,
        BigDecimal productMouthDiameterCm
) {
    public PackagingCommand(
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal volumeCm3,
            BigDecimal innerLengthCm,
            BigDecimal innerWidthCm,
            BigDecimal innerHeightCm,
            BigDecimal productLengthCm,
            BigDecimal productWidthCm,
            BigDecimal productHeightCm,
            BigDecimal capacityMl,
            BigDecimal netWeightKg,
            BigDecimal grossWeightKg,
            BigDecimal gramWeightG,
            BigDecimal innerWeightKg,
            String method,
            Integer cartonQuantity,
            Long packageImageFileId,
            Long cartonImageFileId
    ) {
        this(
                lengthCm, widthCm, heightCm, volumeCm3, innerLengthCm,
                innerWidthCm, innerHeightCm, productLengthCm, productWidthCm, productHeightCm,
                capacityMl, netWeightKg, grossWeightKg, gramWeightG, innerWeightKg,
                method, cartonQuantity, packageImageFileId, cartonImageFileId, null
        );
    }
}
