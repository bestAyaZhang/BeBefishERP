package com.bebefish.erp.dashboard.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.dashboard.application.DashboardOverview.RecentOrder;
import com.bebefish.erp.dashboard.application.DashboardOverview.SalesTrendPoint;
import com.bebefish.erp.dashboard.application.DashboardOverview.StockAlert;
import com.bebefish.erp.dashboard.application.DashboardOverview.Summary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DashboardQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardOverview overview(String requestedPeriod) {
        var period = Period.parse(requestedPeriod);
        var range = range(period, LocalDate.now(clock.withZone(SHANGHAI)));
        var catalog = catalogSummary();
        var inventory = inventorySummary();
        var orders = orderSummary(range);
        return new DashboardOverview(
                new Summary(
                        catalog.productCount(), catalog.enabledProductCount(), catalog.skuCount(),
                        catalog.enabledSupplierCount(), inventory.zeroStockSkuCount(),
                        inventory.lowStockSkuCount(), orders.orderCount(), orders.salesAmount(),
                        orders.outstandingAmount(), orders.draftOrderCount()
                ),
                salesTrend(period, range),
                stockAlerts(),
                recentOrders()
        );
    }

    private CatalogSummary catalogSummary() {
        return jdbc.queryForObject("""
                select
                    (select count(*) from product_spu) as product_count,
                    (select count(*) from product_spu where status = 'enabled') as enabled_product_count,
                    (select count(*) from product_sku) as sku_count,
                    (select count(*) from supplier where status = 'enabled') as enabled_supplier_count
                """, new MapSqlParameterSource(), (rs, rowNum) -> new CatalogSummary(
                rs.getLong("product_count"),
                rs.getLong("enabled_product_count"),
                rs.getLong("sku_count"),
                rs.getLong("enabled_supplier_count")
        ));
    }

    private InventorySummary inventorySummary() {
        return jdbc.queryForObject("""
                select
                    coalesce(sum(case when stock_quantity = 0 then 1 else 0 end), 0) as zero_stock_sku_count,
                    coalesce(sum(case when stock_quantity > 0 and stock_quantity <= safety_stock_quantity
                                      then 1 else 0 end), 0) as low_stock_sku_count
                from (
                    select s.id, s.safety_stock_quantity, coalesce(sum(b.quantity), 0) as stock_quantity
                    from product_sku s
                    left join inventory_balance b on b.sku_id = s.id
                    group by s.id, s.safety_stock_quantity
                ) sku_stock
                """, new MapSqlParameterSource(), (rs, rowNum) -> new InventorySummary(
                rs.getLong("zero_stock_sku_count"),
                rs.getLong("low_stock_sku_count")
        ));
    }

    private OrderSummary orderSummary(DateRange range) {
        return jdbc.queryForObject("""
                select
                    coalesce(sum(case when status = 'confirmed' then 1 else 0 end), 0) as order_count,
                    coalesce(sum(case when status = 'confirmed' then total_amount else 0 end), 0) as sales_amount,
                    coalesce(sum(case when status = 'confirmed' then outstanding_amount else 0 end), 0)
                        as outstanding_amount,
                    coalesce(sum(case when status = 'draft' then 1 else 0 end), 0) as draft_order_count
                from sales_order
                where sales_date between :startDate and :endDate
                """, range.parameters(), (rs, rowNum) -> new OrderSummary(
                rs.getLong("order_count"),
                rs.getBigDecimal("sales_amount"),
                rs.getBigDecimal("outstanding_amount"),
                rs.getLong("draft_order_count")
        ));
    }

    private List<SalesTrendPoint> salesTrend(Period period, DateRange range) {
        return period == Period.YEAR ? monthlySalesTrend(range) : dailySalesTrend(range);
    }

    private List<SalesTrendPoint> dailySalesTrend(DateRange range) {
        var values = new LinkedHashMap<LocalDate, TrendValue>();
        jdbc.query("""
                select sales_date, sum(total_amount) as sales_amount, count(*) as order_count
                from sales_order
                where status = 'confirmed' and sales_date between :startDate and :endDate
                group by sales_date
                order by sales_date
                """, range.parameters(), (rs, rowNum) -> new DailyTrendValue(
                rs.getObject("sales_date", LocalDate.class),
                new TrendValue(rs.getBigDecimal("sales_amount"), rs.getLong("order_count"))
        )).forEach(value -> values.put(value.date(), value.trend()));
        return range.startDate().datesUntil(range.endDate().plusDays(1))
                .map(date -> {
                    var value = values.getOrDefault(date, TrendValue.ZERO);
                    return new SalesTrendPoint(date.toString(), null, value.salesAmount(), value.orderCount());
                })
                .toList();
    }

    private List<SalesTrendPoint> monthlySalesTrend(DateRange range) {
        var values = new LinkedHashMap<Integer, TrendValue>();
        jdbc.query("""
                select month(sales_date) as sales_month, sum(total_amount) as sales_amount, count(*) as order_count
                from sales_order
                where status = 'confirmed' and sales_date between :startDate and :endDate
                group by month(sales_date)
                order by sales_month
                """, range.parameters(), (rs, rowNum) -> new MonthlyTrendValue(
                rs.getInt("sales_month"),
                new TrendValue(rs.getBigDecimal("sales_amount"), rs.getLong("order_count"))
        )).forEach(value -> values.put(value.month(), value.trend()));
        var year = range.startDate().getYear();
        return java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(month -> {
                    var value = values.getOrDefault(month, TrendValue.ZERO);
                    return new SalesTrendPoint(
                            null, YearMonth.of(year, month).toString(), value.salesAmount(), value.orderCount()
                    );
                })
                .toList();
    }

    private List<StockAlert> stockAlerts() {
        return jdbc.query("""
                select p.id as product_id, p.product_name, s.id as sku_id, s.sku_code, s.sku_name,
                       coalesce(sum(b.quantity), 0) as stock_quantity, s.safety_stock_quantity,
                       s.safety_stock_quantity - coalesce(sum(b.quantity), 0) as shortage_quantity
                from product_sku s
                join product_spu p on p.id = s.product_id
                left join inventory_balance b on b.sku_id = s.id
                group by p.id, p.product_name, s.id, s.sku_code, s.sku_name, s.safety_stock_quantity
                having stock_quantity < s.safety_stock_quantity
                order by shortage_quantity desc, s.id asc
                limit 8
                """, new MapSqlParameterSource(), (rs, rowNum) -> new StockAlert(
                rs.getLong("product_id"), rs.getString("product_name"), rs.getLong("sku_id"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getBigDecimal("stock_quantity"),
                rs.getBigDecimal("safety_stock_quantity"), rs.getBigDecimal("shortage_quantity")
        ));
    }

    private List<RecentOrder> recentOrders() {
        return jdbc.query("""
                select sales_no, customer_name, total_amount, status, sales_date
                from sales_order
                order by sales_date desc, id desc
                limit 6
                """, new MapSqlParameterSource(), (rs, rowNum) -> new RecentOrder(
                rs.getString("sales_no"), rs.getString("customer_name"), rs.getBigDecimal("total_amount"),
                rs.getString("status"), rs.getObject("sales_date", LocalDate.class)
        ));
    }

    private DateRange range(Period period, LocalDate today) {
        return switch (period) {
            case WEEK -> new DateRange(today.minusDays(6), today);
            case MONTH -> {
                var month = YearMonth.from(today);
                yield new DateRange(month.atDay(1), month.atEndOfMonth());
            }
            case YEAR -> new DateRange(today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31));
        };
    }

    private enum Period {
        WEEK,
        MONTH,
        YEAR;

        private static Period parse(String value) {
            if (value == null) {
                return WEEK;
            }
            return switch (value) {
                case "week" -> WEEK;
                case "month" -> MONTH;
                case "year" -> YEAR;
                default -> throw new BusinessException(
                        "VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "period 仅支持 week、month 或 year"
                );
            };
        }
    }

    private record CatalogSummary(
            long productCount,
            long enabledProductCount,
            long skuCount,
            long enabledSupplierCount
    ) {}

    private record InventorySummary(long zeroStockSkuCount, long lowStockSkuCount) {}

    private record OrderSummary(
            long orderCount,
            BigDecimal salesAmount,
            BigDecimal outstandingAmount,
            long draftOrderCount
    ) {}

    private record TrendValue(BigDecimal salesAmount, long orderCount) {
        private static final TrendValue ZERO = new TrendValue(BigDecimal.ZERO, 0);
    }

    private record DailyTrendValue(LocalDate date, TrendValue trend) {}

    private record MonthlyTrendValue(int month, TrendValue trend) {}

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        private MapSqlParameterSource parameters() {
            return new MapSqlParameterSource(Map.of("startDate", startDate, "endDate", endDate));
        }
    }
}
