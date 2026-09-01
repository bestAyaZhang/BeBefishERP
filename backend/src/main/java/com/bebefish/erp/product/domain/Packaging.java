package com.bebefish.erp.product.domain;

import java.math.BigDecimal;

public record Packaging(
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal volumeCm3,
        BigDecimal innerLengthCm,
        BigDecimal innerWidthCm,
        BigDecimal innerHeightCm,
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
