package com.bebefish.erp.shipping.api;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record UpdatePreparationRequest(
        @NotNull @Pattern(regexp="unfinished|completed|out_of_stock|partially_shipped", message="请选择有效的备货状态") String status,
        @DecimalMin(value="0", inclusive=false, message="实际重量必须大于零")
        @Digits(integer=9, fraction=3, message="实际重量最多9位整数、3位小数") BigDecimal actualWeight,
        @NotNull @Min(0) Long version) {}
