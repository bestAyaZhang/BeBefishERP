package com.bebefish.erp.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DashboardControllerTest.FixedClockConfiguration.class)
@Transactional
class DashboardControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    private String viewToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from sales_order_item");
        jdbc.update("delete from sales_order");
        jdbc.update("delete from inventory_balance");
        jdbc.update("delete from sku_supplier_quote");
        jdbc.update("delete from product_sku_spec_value");
        jdbc.update("delete from product_spec_value");
        jdbc.update("delete from product_spec");
        jdbc.update("delete from product_sku");
        jdbc.update("delete from product_spu");
        jdbc.update("delete from supplier");
        viewToken = tokenIssuer.issue(new UserAccount(
                "13900000008", "unused", true, true, List.of("TESTER"), List.of("dashboard:view")
        ), "dashboard-controller-test").accessToken();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsAuthenticatedUserWithoutDashboardPermission() throws Exception {
        var token = tokenIssuer.issue(new UserAccount(
                "13900000009", "unused", true, true, List.of("TESTER"), List.of("product:view")
        ), "dashboard-controller-test-without-permission").accessToken();

        mvc.perform(get("/api/dashboard/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsUnsupportedPeriod() throws Exception {
        mvc.perform(get("/api/dashboard/overview")
                        .header("Authorization", bearer())
                        .param("period", "quarter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void defaultsToWeekAndReturnsRealOverviewMetricsWithCompleteBuckets() throws Exception {
        var references = insertOverviewReferences();
        insertOrder(references, "DASH-C-OLD", "confirmed", "2026-08-25", "777.00", "77.00");
        insertOrder(references, "DASH-C-AUG30", "confirmed", "2026-08-30", "2434.23", "340.00");
        insertOrder(references, "DASH-C-SEP01", "confirmed", "2026-09-01", "100.00", "20.00");
        insertOrder(references, "DASH-D-SEP01", "draft", "2026-09-01", "999.00", "999.00");
        insertOrder(references, "DASH-V-SEP01", "void", "2026-09-01", "888.00", "888.00");
        insertOrder(references, "DASH-V-AUG31", "void", "2026-08-31", "1.00", "1.00");
        insertOrder(references, "DASH-V-AUG29", "void", "2026-08-29", "1.00", "1.00");
        insertOrder(references, "DASH-V-AUG28", "void", "2026-08-28", "1.00", "1.00");

        var data = responseData(null);

        assertThat(data.path("summary").path("productCount").asLong()).isEqualTo(2);
        assertThat(data.path("summary").path("enabledProductCount").asLong()).isEqualTo(1);
        assertThat(data.path("summary").path("skuCount").asLong()).isEqualTo(3);
        assertThat(data.path("summary").path("enabledSupplierCount").asLong()).isEqualTo(1);
        assertThat(data.path("summary").path("zeroStockSkuCount").asLong()).isEqualTo(1);
        assertThat(data.path("summary").path("lowStockSkuCount").asLong()).isEqualTo(2);
        assertThat(data.path("summary").path("orderCount").asLong()).isEqualTo(2);
        assertThat(data.path("summary").path("salesAmount").decimalValue())
                .isEqualByComparingTo("2534.23");
        assertThat(data.path("summary").path("outstandingAmount").decimalValue())
                .isEqualByComparingTo("360.00");
        assertThat(data.path("summary").path("draftOrderCount").asLong()).isEqualTo(1);

        var trend = data.path("salesTrend");
        assertThat(trend).hasSize(7);
        assertTrendPoint(trend.get(0), "2026-08-26", null, "0", 0);
        assertTrendPoint(trend.get(4), "2026-08-30", null, "2434.23", 1);
        assertTrendPoint(trend.get(6), "2026-09-01", null, "100.00", 1);

        var alerts = data.path("stockAlerts");
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).path("productName").asText()).isEqualTo("仪表盘商品甲");
        assertThat(alerts.get(0).path("skuCode").asText()).isEqualTo("DASH-SKU-A");
        assertThat(alerts.get(0).path("stockQuantity").decimalValue()).isEqualByComparingTo("4.0");
        assertThat(alerts.get(0).path("safetyStockQuantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(alerts.get(0).path("shortageQuantity").decimalValue()).isEqualByComparingTo("6");
        assertThat(alerts.get(1).path("skuCode").asText()).isEqualTo("DASH-SKU-B");
        assertThat(alerts.get(1).path("shortageQuantity").decimalValue()).isEqualByComparingTo("4");

        var recentOrders = data.path("recentOrders");
        assertThat(recentOrders).hasSize(6);
        assertThat(recentOrders.get(0).path("orderNo").asText()).isEqualTo("DASH-V-SEP01");
        assertThat(recentOrders.get(0).path("customer").asText()).isEqualTo("星海贸易");
        assertThat(recentOrders.get(0).path("amount").decimalValue()).isEqualByComparingTo("888.00");
        assertThat(recentOrders.get(0).path("status").asText()).isEqualTo("void");
        assertThat(recentOrders.get(0).path("businessDate").asText()).isEqualTo("2026-09-01");
        assertThat(recentOrders.get(1).path("orderNo").asText()).isEqualTo("DASH-D-SEP01");
        assertThat(recentOrders.get(2).path("orderNo").asText()).isEqualTo("DASH-C-SEP01");
        assertThat(recentOrders.get(5).path("orderNo").asText()).isEqualTo("DASH-V-AUG29");
    }

    @Test
    void returnsCompleteNaturalMonthAndYearBucketsUsingShanghaiCalendar() throws Exception {
        var references = insertOrderReferences();
        insertOrder(references, "DASH-AUG", "confirmed", "2026-08-31", "99.00", "9.00");
        insertOrder(references, "DASH-SEP-FIRST", "confirmed", "2026-09-01", "10.00", "1.00");
        insertOrder(references, "DASH-SEP-LAST", "confirmed", "2026-09-30", "30.00", "3.00");

        var monthData = responseData("month");
        assertThat(monthData.path("summary").path("orderCount").asLong()).isEqualTo(2);
        assertThat(monthData.path("summary").path("salesAmount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(monthData.path("salesTrend")).hasSize(30);
        assertTrendPoint(monthData.path("salesTrend").get(0), "2026-09-01", null, "10.00", 1);
        assertTrendPoint(monthData.path("salesTrend").get(29), "2026-09-30", null, "30.00", 1);

        var yearData = responseData("year");
        assertThat(yearData.path("summary").path("orderCount").asLong()).isEqualTo(3);
        assertThat(yearData.path("summary").path("salesAmount").decimalValue()).isEqualByComparingTo("139.00");
        assertThat(yearData.path("salesTrend")).hasSize(12);
        assertTrendPoint(yearData.path("salesTrend").get(7), null, "2026-08", "99.00", 1);
        assertTrendPoint(yearData.path("salesTrend").get(8), null, "2026-09", "40.00", 2);
        assertTrendPoint(yearData.path("salesTrend").get(11), null, "2026-12", "0", 0);
    }

    @Test
    void returnsZeroValuesAndEmptyListsWhenDataSourcesAreEmpty() throws Exception {
        var data = responseData("month");

        var summary = data.path("summary");
        assertThat(summary.path("productCount").asLong()).isZero();
        assertThat(summary.path("enabledProductCount").asLong()).isZero();
        assertThat(summary.path("skuCount").asLong()).isZero();
        assertThat(summary.path("enabledSupplierCount").asLong()).isZero();
        assertThat(summary.path("zeroStockSkuCount").asLong()).isZero();
        assertThat(summary.path("lowStockSkuCount").asLong()).isZero();
        assertThat(summary.path("orderCount").asLong()).isZero();
        assertThat(summary.path("salesAmount").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.path("outstandingAmount").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.path("draftOrderCount").asLong()).isZero();
        assertThat(data.path("salesTrend")).hasSize(30);
        assertThat(data.path("salesTrend").get(15).path("salesAmount").decimalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.path("stockAlerts")).isEmpty();
        assertThat(data.path("recentOrders")).isEmpty();
    }

    @Test
    void limitsStockAlertsToEightAndOrdersByShortageDescending() throws Exception {
        var suffix = String.valueOf(System.nanoTime());
        var categoryId = insertCategory("DASH-LIMIT-CAT-" + suffix);
        var productId = insertProduct(categoryId, "DASH-LIMIT-P-" + suffix, "库存预警排序", "enabled");
        for (int index = 1; index <= 9; index++) {
            insertSku(productId, "DASH-LIMIT-SKU-" + index + "-" + suffix, "预警 SKU " + index,
                    String.valueOf(9 + index), index == 1, "enabled");
        }

        var alerts = responseData("week").path("stockAlerts");

        assertThat(alerts).hasSize(8);
        assertThat(alerts.get(0).path("skuCode").asText()).startsWith("DASH-LIMIT-SKU-9-");
        assertThat(alerts.get(0).path("shortageQuantity").decimalValue()).isEqualByComparingTo("18");
        assertThat(alerts.get(7).path("skuCode").asText()).startsWith("DASH-LIMIT-SKU-2-");
        assertThat(alerts.get(7).path("shortageQuantity").decimalValue()).isEqualByComparingTo("11");
    }

    private JsonNode responseData(String period) throws Exception {
        var request = get("/api/dashboard/overview").header("Authorization", bearer());
        if (period != null) {
            request.param("period", period);
        }
        var response = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private OverviewReferences insertOverviewReferences() {
        var suffix = String.valueOf(System.nanoTime());
        var categoryId = insertCategory("DASH-CAT-" + suffix);
        var customerId = insertCustomer("DASH-CUS-" + suffix, "星海贸易");
        var firstWarehouseId = insertWarehouse("DASH-WH-A-" + suffix, "工作台仓一");
        var secondWarehouseId = insertWarehouse("DASH-WH-B-" + suffix, "工作台仓二");
        insertSupplier("DASH-SUP-A-" + suffix, "enabled");
        insertSupplier("DASH-SUP-B-" + suffix, "disabled");

        var firstProductId = insertProduct(categoryId, "DASH-P-A-" + suffix, "仪表盘商品甲", "enabled");
        var firstSkuId = insertSku(firstProductId, "DASH-SKU-A", "低库存 SKU", "10", true, "enabled");
        insertSku(firstProductId, "DASH-SKU-B", "零库存 SKU", "4", false, "enabled");
        var secondProductId = insertProduct(categoryId, "DASH-P-B-" + suffix, "仪表盘商品乙", "disabled");
        var equalSafetySkuId = insertSku(
                secondProductId, "DASH-SKU-C", "等于安全库存 SKU", "5", true, "disabled"
        );

        insertBalance(firstWarehouseId, firstSkuId, "1.5");
        insertBalance(secondWarehouseId, firstSkuId, "2.5");
        insertBalance(firstWarehouseId, equalSafetySkuId, "2");
        insertBalance(secondWarehouseId, equalSafetySkuId, "3");
        return new OverviewReferences(customerId, firstWarehouseId, "工作台仓一");
    }

    private OverviewReferences insertOrderReferences() {
        var suffix = String.valueOf(System.nanoTime());
        var customerId = insertCustomer("DASH-ORDER-CUS-" + suffix, "星海贸易");
        var warehouseId = insertWarehouse("DASH-ORDER-WH-" + suffix, "工作台订单仓");
        return new OverviewReferences(customerId, warehouseId, "工作台订单仓");
    }

    private long insertCategory(String code) {
        jdbc.update("insert into product_category (category_code, category_name, level_no, sort_order, status, "
                        + "created_at, updated_at) values (?, ?, 1, 0, 'enabled', now(3), now(3))",
                code, "仪表盘分类-" + code);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    private long insertCustomer(String customerNo, String customerName) {
        jdbc.update("insert into customer (customer_no, customer_name, status, created_at, updated_at) "
                        + "values (?, ?, 'enabled', now(3), now(3))",
                customerNo, customerName);
        return jdbc.queryForObject("select id from customer where customer_no = ?", Long.class, customerNo);
    }

    private long insertWarehouse(String warehouseNo, String warehouseName) {
        jdbc.update("insert into warehouse (warehouse_no, warehouse_name, is_default, status, created_at, updated_at) "
                        + "values (?, ?, false, 'enabled', now(3), now(3))",
                warehouseNo, warehouseName);
        return jdbc.queryForObject("select id from warehouse where warehouse_no = ?", Long.class, warehouseNo);
    }

    private void insertSupplier(String supplierNo, String status) {
        jdbc.update("insert into supplier (supplier_no, supplier_name, status, created_at, updated_at) "
                        + "values (?, ?, ?, now(3), now(3))",
                supplierNo, "仪表盘供应商-" + supplierNo, status);
    }

    private long insertProduct(long categoryId, String productCode, String productName, String status) {
        jdbc.update("insert into product_spu (product_code, item_no, product_name, category_id, product_type, "
                        + "status, created_at, updated_at) values (?, ?, ?, ?, 'variant', ?, now(3), now(3))",
                productCode, "ITEM-" + productCode, productName, categoryId, status);
        return jdbc.queryForObject("select id from product_spu where product_code = ?", Long.class, productCode);
    }

    private long insertSku(
            long productId,
            String skuCode,
            String skuName,
            String safetyStock,
            boolean defaultSku,
            String status
    ) {
        jdbc.update("insert into product_sku (product_id, sku_code, sku_name, sales_unit, default_sale_price, "
                        + "standard_cost, safety_stock_quantity, is_default, status, created_at, updated_at) "
                        + "values (?, ?, ?, '件', 10, 2, ?, ?, ?, now(3), now(3))",
                productId, skuCode, skuName, new BigDecimal(safetyStock), defaultSku, status);
        return jdbc.queryForObject("select id from product_sku where sku_code = ?", Long.class, skuCode);
    }

    private void insertBalance(long warehouseId, long skuId, String quantity) {
        jdbc.update("insert into inventory_balance "
                        + "(warehouse_id, sku_id, quantity, version_no, created_at, updated_at) "
                        + "values (?, ?, ?, 0, now(3), now(3))",
                warehouseId, skuId, new BigDecimal(quantity));
    }

    private void insertOrder(
            OverviewReferences references,
            String orderNo,
            String status,
            String businessDate,
            String amount,
            String outstandingAmount
    ) {
        var total = new BigDecimal(amount);
        var outstanding = new BigDecimal(outstandingAmount);
        jdbc.update("insert into sales_order (sales_no, customer_id, customer_name, warehouse_id, warehouse_name, "
                        + "sales_date, salesperson_mobile, status, transport_method, settlement_cycle, "
                        + "invoice_required, invoice_status, goods_amount, discount_amount, shipping_fee, "
                        + "total_amount, received_amount, outstanding_amount, created_at, updated_at) "
                        + "values (?, ?, '星海贸易', ?, ?, ?, '13900000008', ?, 'delivery', 'monthly', false, "
                        + "'not_required', ?, 0, 0, ?, ?, ?, now(3), now(3))",
                orderNo, references.customerId(), references.warehouseId(), references.warehouseName(), businessDate,
                status, total, total, total.subtract(outstanding), outstanding);
    }

    private void assertTrendPoint(
            JsonNode point,
            String expectedDate,
            String expectedMonth,
            String expectedSalesAmount,
            long expectedOrderCount
    ) {
        if (expectedDate == null) {
            assertThat(point.path("date").isNull() || point.path("date").isMissingNode()).isTrue();
        } else {
            assertThat(point.path("date").asText()).isEqualTo(expectedDate);
        }
        if (expectedMonth == null) {
            assertThat(point.path("month").isNull() || point.path("month").isMissingNode()).isTrue();
        } else {
            assertThat(point.path("month").asText()).isEqualTo(expectedMonth);
        }
        assertThat(point.path("salesAmount").decimalValue()).isEqualByComparingTo(expectedSalesAmount);
        assertThat(point.path("orderCount").asLong()).isEqualTo(expectedOrderCount);
    }

    private String bearer() {
        return "Bearer " + viewToken;
    }

    private record OverviewReferences(long customerId, long warehouseId, String warehouseName) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedDashboardClock() {
            return Clock.fixed(Instant.parse("2026-08-31T16:30:00Z"), ZoneId.of("UTC"));
        }
    }
}
