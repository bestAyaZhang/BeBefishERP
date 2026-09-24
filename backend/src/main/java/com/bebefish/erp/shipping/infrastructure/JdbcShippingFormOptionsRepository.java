package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.shipping.api.ShippingFormOptions;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShippingFormOptionsRepository {
    private final JdbcTemplate jdbc;

    public JdbcShippingFormOptionsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShippingFormOptions.PreparerOption> findActivePreparers() {
        return jdbc.query("select id, name from employee where status='active' order by id",
                (rs, row) -> new ShippingFormOptions.PreparerOption(rs.getLong("id"), rs.getString("name")));
    }
}
