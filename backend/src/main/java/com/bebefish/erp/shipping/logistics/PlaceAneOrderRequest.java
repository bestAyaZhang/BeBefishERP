package com.bebefish.erp.shipping.logistics;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PlaceAneOrderRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 30) String province,
        @NotBlank @Size(max = 30) String city,
        @NotBlank @Size(max = 30) String county,
        @NotBlank @Size(max = 100) String address,
        @NotNull @DecimalMin("1") @Digits(integer = 9, fraction = 2) BigDecimal weight,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal volume,
        @NotNull @Min(1) @Max(9999) Integer pieceAmount,
        @NotBlank @Size(max = 32) String cargoName,
        @NotBlank @Size(max = 50) String packType,
        @NotNull Integer productTypeId,
        @NotNull Integer goodsType,
        @NotNull Integer payType,
        @Size(max = 200) String remark
) {}
