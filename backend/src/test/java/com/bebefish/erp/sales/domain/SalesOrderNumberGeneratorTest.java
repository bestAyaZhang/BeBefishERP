package com.bebefish.erp.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesOrderNumberGeneratorTest {
    @Test
    void generatesFourDigitSequencePerBusinessDate() {
        var generator = new SalesOrderNumberGenerator(new InMemorySequenceRepository());

        assertThat(generator.next(LocalDate.of(2026, 7, 10))).isEqualTo("SO202607100001");
        assertThat(generator.next(LocalDate.of(2026, 7, 10))).isEqualTo("SO202607100002");
        assertThat(generator.next(LocalDate.of(2026, 7, 11))).isEqualTo("SO202607110001");
    }

    private static final class InMemorySequenceRepository implements SalesOrderSequenceRepository {
        private final Map<LocalDate, Integer> values = new HashMap<>();

        @Override
        public int next(LocalDate businessDate) {
            var next = values.getOrDefault(businessDate, 0) + 1;
            values.put(businessDate, next);
            return next;
        }
    }
}
