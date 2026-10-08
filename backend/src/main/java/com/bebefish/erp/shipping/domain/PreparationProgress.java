package com.bebefish.erp.shipping.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PreparationProgress(BigDecimal actualWeight, String updatedBy, Long employeeId, LocalDateTime updatedAt) {
    public static PreparationProgress empty() { return new PreparationProgress(null, null, null, null); }
}
