package com.bebefish.erp.sales.domain;

import java.time.LocalDate;

public interface SalesOrderSequenceRepository {
    int next(LocalDate businessDate);
}
