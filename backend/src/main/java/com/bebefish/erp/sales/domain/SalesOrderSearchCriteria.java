package com.bebefish.erp.sales.domain;

import java.time.LocalDate;

public record SalesOrderSearchCriteria(
        String keyword,
        String status,
        LocalDate salesDateFrom,
        LocalDate salesDateTo
) {
}
