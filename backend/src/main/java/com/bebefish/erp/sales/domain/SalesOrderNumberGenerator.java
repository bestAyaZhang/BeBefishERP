package com.bebefish.erp.sales.domain;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SalesOrderNumberGenerator {
    private final SalesOrderSequenceRepository sequenceRepository;

    public SalesOrderNumberGenerator(SalesOrderSequenceRepository sequenceRepository) {
        this.sequenceRepository = Objects.requireNonNull(sequenceRepository);
    }

    public String next(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "业务日期不能为空");
        var sequence = sequenceRepository.next(businessDate);
        if (sequence <= 0 || sequence > 9999) {
            throw new IllegalStateException("销售单号当日序号已超过 9999");
        }
        return "SO%s%04d".formatted(businessDate.toString().replace("-", ""), sequence);
    }
}
