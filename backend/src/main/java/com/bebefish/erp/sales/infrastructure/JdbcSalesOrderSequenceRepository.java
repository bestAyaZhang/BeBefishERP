package com.bebefish.erp.sales.infrastructure;

import com.bebefish.erp.sales.domain.SalesOrderSequenceRepository;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSalesOrderSequenceRepository implements SalesOrderSequenceRepository {
    private final JdbcTemplate jdbc;

    public JdbcSalesOrderSequenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int next(LocalDate businessDate) {
        jdbc.update(
                "insert into sales_order_sequence (business_date, current_value) values (?, last_insert_id(1)) "
                        + "on duplicate key update current_value = last_insert_id(current_value + 1)",
                businessDate
        );
        var value = jdbc.queryForObject("select last_insert_id()", Long.class);
        if (value == null) {
            throw new IllegalStateException("销售单号序列生成失败");
        }
        return Math.toIntExact(value);
    }
}
