package com.bebefish.erp.shipping.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AneOrderDraft(
        @Size(max = 32) String cargoName,
        @Size(max = 50) String packType,
        @DecimalMin(value = "0", message = "重量不能小于零")
        @Digits(integer = 9, fraction = 3, message = "重量最多保留三位小数") BigDecimal weight,
        @DecimalMin(value = "0", message = "体积不能小于零")
        @Digits(integer = 9, fraction = 2, message = "体积最多保留两位小数") BigDecimal volume,
        @Min(value = 1, message = "件数至少为1") @Max(value = 9999, message = "件数不能超过9999") Integer pieceAmount,
        Integer productTypeId,
        Integer goodsType,
        Integer payType,
        @Size(max = 200) String logisticsRemark
) {
    public AneOrderDraft normalized() {
        return new AneOrderDraft(clean(cargoName), clean(packType), weight, volume, pieceAmount,
                productTypeId, goodsType, payType, clean(logisticsRemark));
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
