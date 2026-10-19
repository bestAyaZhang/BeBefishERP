package com.bebefish.erp.masterdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCustomerAccountRepositoryTest {
    private JdbcTemplate jdbc;
    private JdbcCustomerAccountRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:customer_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        repository = new JdbcCustomerAccountRepository(jdbc);
        jdbc.execute("create table sales_order(id bigint primary key,customer_id bigint,customer_name varchar(100),sales_no varchar(50),sales_date date,status varchar(20),total_amount decimal(18,2),received_amount decimal(18,2),outstanding_amount decimal(18,2))");
        jdbc.execute("create table customer_sku_price(customer_id bigint,sku_id bigint,unit_price decimal(18,2),updated_by varchar(100),updated_at timestamp,unique(customer_id,sku_id))");
    }

    @Test
    void billsUseCustomerIdentityInclusiveDatesAndWholeRangeTotals() {
        order(1, 1, "2026-10-01", "confirmed", "10.10", "2.00");
        order(2, 1, "2026-10-14", "confirmed", "20.20", "10.00");
        order(3, 2, "2026-10-14", "confirmed", "999.00", "0"); // Same name, another ID.
        order(4, 1, "2026-10-14", "draft", "999.00", "0");
        order(5, 1, "2026-10-14", "void", "999.00", "0");
        order(6, 1, "2026-09-30", "confirmed", "999.00", "0");
        var bills = repository.bills(1, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-14"), 1, 1);
        assertThat(bills.records()).hasSize(1);
        assertThat(bills.records().getFirst().id()).isEqualTo(2);
        assertThat(bills.total()).isEqualTo(2);
        assertThat(bills.summary().totalAmount()).isEqualByComparingTo("30.30");
        assertThat(bills.summary().receivedAmount()).isEqualByComparingTo("12.00");
        assertThat(bills.summary().outstandingAmount()).isEqualByComparingTo("18.30");
        assertThat(repository.bills(1, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-14"), 2, 1).records().getFirst().id()).isEqualTo(1);
        assertThat(repository.bills(9, null, null, 1, 20).summary().totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void pricesAreUniquePerCustomerSkuAndZeroIsRetained() {
        repository.savePrice(1, 20, new BigDecimal("8.50"), "test");
        repository.savePrice(2, 20, new BigDecimal("9.50"), "test");
        repository.savePrice(1, 20, BigDecimal.ZERO, "test");
        assertThat(repository.findPrice(1, 20)).contains(new BigDecimal("0.00"));
        assertThat(repository.findPrice(2, 20)).contains(new BigDecimal("9.50"));
        assertThat(jdbc.queryForObject("select count(*) from customer_sku_price", Integer.class)).isEqualTo(2);
        repository.deletePrice(1, 20);
        assertThat(repository.findPrice(1, 20)).isEmpty();
    }

    private void order(long id, long customer, String date, String status, String total, String received) {
        var amount = new BigDecimal(total); var paid = new BigDecimal(received);
        jdbc.update("insert into sales_order values(?,?,'同名客户',?,?,?,?,?,?)", id, customer, "SO" + id, LocalDate.parse(date), status, amount, paid, amount.subtract(paid));
    }
}
