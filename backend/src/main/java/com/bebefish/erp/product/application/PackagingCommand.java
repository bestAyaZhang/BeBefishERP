package com.bebefish.erp.product.application;

import java.math.BigDecimal;

public record PackagingCommand(
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal volumeCm3,
        BigDecimal netWeightKg,
        BigDecimal grossWeightKg,
        BigDecimal gramWeightG,
        String method,
        Integer cartonQuantity,
        Long packageImageFileId,
        Long cartonImageFileId
) {
}
