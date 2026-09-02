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
        Long cartonImageFileId
) {
}
