package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReplenishmentServiceTest {
    @Test
    void rollsFromFallbackToActualDemandAndKeepsWarehouseScopesSeparate() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table warehouse (id bigint, warehouse_name varchar(50), status varchar(20), independent_replenishment boolean, created_at timestamp)");
        jdbc.execute("create table product_spu (id bigint, product_name varchar(50), status varchar(20))");
        jdbc.execute("create table product_sku (id bigint, product_id bigint, sku_code varchar(50), sku_name varchar(50), status varchar(20), created_at timestamp)");
        jdbc.execute("create table inventory_balance (sku_id bigint, warehouse_id bigint, quantity decimal(18,4))");
        jdbc.execute("create table sales_order (id bigint, warehouse_id bigint, status varchar(20), sales_date date)");
        jdbc.execute("create table sales_order_item (sales_order_id bigint, sku_id bigint, quantity decimal(18,4))");
        jdbc.execute("""
                create table inventory_replenishment_policy (sku_id bigint, scope_warehouse_id bigint,
                    mode varchar(10), buffer_days int, lead_days int, manual_safety bigint, manual_point bigint,
                    primary key (sku_id, scope_warehouse_id))
                """);
        jdbc.update("""
                insert into warehouse values
                    (1,'杭州主仓','enabled',false,'2026-01-01 00:00:00'),
                    (2,'义乌备货仓','enabled',false,'2026-01-01 00:00:00'),
                    (3,'宁波成品仓','enabled',true,'2026-01-01 00:00:00')
                """);
        jdbc.update("insert into product_spu values (4,'玻璃杯','enabled')");
        jdbc.update("insert into product_sku values (5,4,'GLASS-NEW','竖纹款','enabled','2026-10-08 11:00:00')");
        jdbc.update("insert into inventory_balance values (5,1,14),(5,2,26),(5,3,10)");
        jdbc.update("""
                insert into sales_order values
                    (1,1,'confirmed','2026-10-08'), (2,1,'confirmed','2026-10-09'),
                    (3,2,'confirmed','2026-11-07'), (4,2,'confirmed','2026-11-08'),
                    (5,2,'void','2026-11-06'), (6,1,'draft','2026-11-06'),
                    (7,3,'confirmed','2026-11-05')
                """);
        jdbc.update("insert into sales_order_item values (1,5,500),(2,5,90),(3,5,90),(4,5,500),(5,5,500),(6,5,500),(7,5,30)");

        var template = new NamedParameterJdbcTemplate(source);
        // 2026-11-07 23:59 in Shanghai: only 29 complete calendar days since creation.
        var before = new ReplenishmentService(template, clock("2026-11-07T15:59:59Z"));
        assertThat(before.list(null, 1L, "GLASS-NEW", 1, 20).items().getFirst().source()).isEqualTo("pending");
        before.save(5, null, new ReplenishmentCalculator.Config("auto", 5, 8, 30L, 78L));
        before.save(5, null, new ReplenishmentCalculator.Config("auto", 5, 8, 30L, 79L));
        var fallback = before.list("warning", 2L, "glass-new", 1, 20).items().getFirst();
        assertThat(fallback.historyDays()).isEqualTo(29);
        assertThat(fallback.stock()).isEqualByComparingTo("40");
        assertThat(fallback.warehouses()).extracting(ReplenishmentService.Warehouse::id).containsExactly(1L, 2L);
        assertThat(fallback.source()).isEqualTo("fallback");
        assertThat(fallback.point()).isEqualTo(79);

        // Midnight Shanghai: the launch date, today, draft and void sales all stay outside demand.
        var after = new ReplenishmentService(template, clock("2026-11-07T16:00:00Z"));
        var shared = after.list("warning", 2L, "GLASS-NEW", 1, 20).items().getFirst();
        assertThat(shared.historyDays()).isEqualTo(30);
        assertThat(shared.demandTotal()).isEqualByComparingTo(new BigDecimal("180"));
        assertThat(shared.source()).isEqualTo("auto");
        assertThat(shared.safety()).isEqualTo(30);
        assertThat(shared.point()).isEqualTo(78);
        var independent = after.list(null, 3L, "GLASS-NEW", 1, 20).items().getFirst();
        assertThat(independent.warehouseId()).isEqualTo(3L);
        assertThat(independent.demandTotal()).isEqualByComparingTo("30");
        assertThat(independent.point()).isEqualTo(13);
        assertThatThrownBy(() -> after.save(5, 2L, shared.config())).isInstanceOf(BusinessException.class);

        jdbc.update("insert into product_sku values (6,4,'UNSTOCKED','未入库','enabled','2026-11-07 11:00:00')");
        assertThat(after.list(null, null, "UNSTOCKED", 1, 20).items()).hasSize(1);
        assertThat(after.list(null, 3L, "UNSTOCKED", 1, 20).items()).singleElement()
                .satisfies(row -> {
                    assertThat(row.warehouseId()).isEqualTo(3L);
                    assertThat(row.source()).isEqualTo("pending");
                });
        after.save(6, 3L, new ReplenishmentCalculator.Config("auto", 5, 8, 30L, 78L));
        assertThat(after.list(null, 3L, null, 1, 20).items()).anySatisfy(row -> {
            assertThat(row.skuId()).isEqualTo(6L);
            assertThat(row.source()).isEqualTo("fallback");
        });
    }

    private Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
