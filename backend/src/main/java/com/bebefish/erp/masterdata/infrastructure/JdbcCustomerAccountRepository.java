package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.masterdata.application.CustomerAccountViews.*;
import com.bebefish.erp.masterdata.domain.CustomerPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCustomerAccountRepository implements CustomerPriceRepository {
    private final JdbcTemplate jdbc;
    public JdbcCustomerAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<PlatformOption> platforms() {
        return jdbc.query("select id,platform_name,status from sales_platform order by sort_order,id",
                (rs, n) -> new PlatformOption(rs.getLong("id"), rs.getString("platform_name"), rs.getString("status")));
    }

    public Optional<BigDecimal> findPrice(long customerId, long skuId) {
        return jdbc.query("select unit_price from customer_sku_price where customer_id=? and sku_id=?",
                (rs, n) -> rs.getBigDecimal(1), customerId, skuId).stream().findFirst();
    }

    public List<Price> prices(long customerId) {
        return jdbc.query("""
                select s.id,s.sku_code,s.sku_name,s.sales_unit,s.default_sale_price,p.item_no,p.product_name,
                       case when s.status='enabled' and p.status='enabled' then 'enabled' else 'disabled' end as status,
                       c.unit_price
                from customer_sku_price c join product_sku s on s.id=c.sku_id
                join product_spu p on p.id=s.product_id where c.customer_id=? order by p.item_no,s.id
                """, (rs, n) -> new Price(rs.getLong("id"), rs.getString("item_no"), rs.getString("product_name"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getString("sales_unit"), rs.getString("status"),
                rs.getBigDecimal("default_sale_price"), rs.getBigDecimal("unit_price")), customerId);
    }

    public void savePrice(long customerId, long skuId, BigDecimal price, String operator) {
        jdbc.update("""
                insert into customer_sku_price(customer_id,sku_id,unit_price,updated_by,updated_at)
                values(?,?,?,?,now(3)) on duplicate key update
                unit_price=values(unit_price),updated_by=values(updated_by),updated_at=values(updated_at)
                """, customerId, skuId, price, operator);
    }

    public void deletePrice(long customerId, long skuId) {
        jdbc.update("delete from customer_sku_price where customer_id=? and sku_id=?", customerId, skuId);
    }

    public Bills bills(long customerId, LocalDate from, LocalDate to, int page, int size) {
        var params = new ArrayList<Object>();
        params.add(customerId);
        var where = " where customer_id=? and status='confirmed'";
        if (from != null) { where += " and sales_date>=?"; params.add(from); }
        if (to != null) { where += " and sales_date<=?"; params.add(to); }
        var summary = jdbc.queryForObject("select count(*) as cnt,coalesce(sum(total_amount),0) as total,"
                + "coalesce(sum(received_amount),0) as received,coalesce(sum(outstanding_amount),0) as outstanding"
                + " from sales_order" + where, (rs, n) -> new Summary(rs.getLong("cnt"), rs.getBigDecimal("total"),
                rs.getBigDecimal("received"), rs.getBigDecimal("outstanding")), params.toArray());
        params.add(size); params.add((long) (page - 1) * size);
        var records = jdbc.query("select id,sales_no,sales_date,total_amount,received_amount,outstanding_amount"
                + " from sales_order" + where + " order by sales_date desc,id desc limit ? offset ?",
                (rs, n) -> new Bill(rs.getLong("id"), rs.getString("sales_no"), rs.getObject("sales_date", LocalDate.class),
                        rs.getBigDecimal("total_amount"), rs.getBigDecimal("received_amount"), rs.getBigDecimal("outstanding_amount")),
                params.toArray());
        return new Bills(records, page, size, summary.orderCount(), summary);
    }
}
