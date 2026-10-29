package com.bebefish.erp.sales.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.startsWith;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.sales.application.SalesDraftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SalesDraftService salesService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long customerId;
    private long warehouseId;
    private long skuId;

    @BeforeEach
    void insertReferences() {
        var suffix = String.valueOf(System.nanoTime());
        jdbc.update(
                "insert into product_category (category_code, category_name, level_no, sort_order, status, "
                        + "created_at, updated_at) values (?, ?, 1, 0, 'enabled', now(3), now(3))",
                "API-CAT-" + suffix, "API销售分类-" + suffix
        );
        var categoryId = jdbc.queryForObject(
                "select id from product_category where category_code = ?", Long.class, "API-CAT-" + suffix
        );
        jdbc.update(
                "insert into customer (customer_no, customer_name, contact_person, mobile, province, city, district, "
                        + "detail_address, default_shipping_method, default_settlement_period, status, created_at, updated_at) "
                        + "values (?, 'API销售客户', '联系人', '13800138000', '浙江省', '杭州市', '西湖区', 'API地址', "
                        + "'delivery', 'monthly', 'enabled', now(3), now(3))",
                "API-CUS-" + suffix
        );
        customerId = jdbc.queryForObject(
                "select id from customer where customer_no = ?", Long.class, "API-CUS-" + suffix
        );
        jdbc.update(
                "insert into warehouse (warehouse_no, warehouse_name, is_default, status, created_at, updated_at) "
                        + "values (?, ?, false, 'enabled', now(3), now(3))",
                "API-WH-" + suffix, "API销售仓库-" + suffix
        );
        warehouseId = jdbc.queryForObject(
                "select id from warehouse where warehouse_no = ?", Long.class, "API-WH-" + suffix
        );
        jdbc.update(
                "insert into product_spu (product_code, item_no, product_name, category_id, product_type, status, "
                        + "created_at, updated_at) values (?, ?, 'API销售商品', ?, 'simple', 'enabled', now(3), now(3))",
                "API-PRD-" + suffix, "API-ITEM-" + suffix, categoryId
        );
        var productId = jdbc.queryForObject(
                "select id from product_spu where item_no = ?", Long.class, "API-ITEM-" + suffix
        );
        jdbc.update(
                "insert into product_sku (product_id, sku_code, sku_name, spec_text, sales_unit, default_sale_price, "
                        + "standard_cost, is_default, status, created_at, updated_at) values (?, ?, '默认规格', '默认规格', "
                        + "'只', 12, 3, true, 'enabled', now(3), now(3))",
                productId, "API-SKU-" + suffix
        );
        skuId = jdbc.queryForObject(
                "select id from product_sku where product_id = ?", Long.class, productId
        );
    }

    @Test
    void savesDraftWithBackendCalculatedAmountsAndReturnsSnapshot() throws Exception {
        var body = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "warehouseId", warehouseId,
                "orderDate", java.time.LocalDate.now().plusDays(3).toString(),
                "transportMethod", "delivery",
                "settlementCycle", "monthly",
                "freight", 10,
                "receivedAmount", 5,
                "lines", List.of(Map.of(
                        "skuId", skuId, "quantity", 2, "unitPrice", 12
                ))
        ));
        mvc.perform(post("/api/sales-orders")
                        .header("Authorization", bearerToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.totalAmount").value(34.00))
                .andExpect(jsonPath("$.data.outstandingAmount").value(29.00))
                .andExpect(jsonPath("$.data.items[0].skuId").value(skuId))
                .andExpect(jsonPath("$.data.items[0].skuCodeSnapshot").isNotEmpty())
                .andExpect(jsonPath("$.data.salespersonMobile").value(startsWith("13900000010-")));
    }

    @Test
    void savesDraftForAuthorizedEmployeeWithoutMobile() throws Exception {
        var body = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "warehouseId", warehouseId,
                "orderDate", java.time.LocalDate.now().plusDays(3).toString(),
                "transportMethod", "delivery",
                "settlementCycle", "monthly",
                "lines", List.of(Map.of("skuId", skuId, "quantity", 1, "unitPrice", 12))
        ));
        String token = com.bebefish.erp.support.TestAuthTokens.issueWithoutMobile(
                jdbc, tokenIssuer, "sales:create"
        );

        mvc.perform(post("/api/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salespersonMobile", startsWith("employee:")));
    }

    @Test
    void listsDraftsByStatus() throws Exception {
        mvc.perform(get("/api/sales-orders")
                        .header("Authorization", bearerToken())
                        .param("status", "draft")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void confirmsAndVoidsSalesOrderWithInventoryLedgerEffect() throws Exception {
        var body = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "warehouseId", warehouseId,
                "orderDate", java.time.LocalDate.now().toString(),
                "transportMethod", "delivery",
                "settlementCycle", "monthly",
                "lines", List.of(Map.of("skuId", skuId, "quantity", 1, "unitPrice", 12))
        ));
        MvcResult draftResult = mvc.perform(post("/api/sales-orders")
                        .header("Authorization", bearerToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        var draftId = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        jdbc.update(
                "insert into inventory_balance (warehouse_id, sku_id, quantity, version_no, created_at, updated_at) "
                        + "values (?, ?, 2, 0, now(3), now(3))",
                warehouseId, skuId
        );

        mvc.perform(post("/api/sales-orders/{id}/confirm", draftId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmed"));
        assertThatInventoryQuantityIs("1");

        mvc.perform(post("/api/sales-orders/{id}/void", draftId)
                        .header("Authorization", bearerToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "客户取消"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("void"))
                .andExpect(jsonPath("$.data.remark").value("客户取消"));
        assertThatInventoryQuantityIs("2");
    }

    @Test
    void concurrentConfirmationAndVoidChangeInventoryOnlyOnce() throws Exception {
        long draftId = createDraft(List.of(Map.of("skuId", skuId, "quantity", 2, "unitPrice", 12)));
        jdbc.update("insert into inventory_balance (warehouse_id, sku_id, quantity, version_no, created_at, updated_at) "
                + "values (?, ?, 10, 0, now(3), now(3))", warehouseId, skuId);
        assertThatInventoryQuantityIs("10");

        runConcurrentTransition(() -> salesService.confirm(draftId, "test"), "销售单已确认，请勿重复操作");
        assertThatInventoryQuantityIs("8");
        assertThat(jdbc.queryForObject("select count(*) from inventory_ledger where source_type='sales' and source_id=?",
                Long.class, draftId)).isEqualTo(1L);

        runConcurrentTransition(() -> salesService.voidOrder(draftId, "并发回归测试", "test"), "只有已确认销售单才能作废");
        assertThatInventoryQuantityIs("10");
        assertThat(jdbc.queryForObject("select count(*) from inventory_ledger where source_type='sales_void' and source_id=?",
                Long.class, draftId)).isEqualTo(1L);
    }

    @Test
    void insufficientStockOnOneLineLeavesAllStockAndTheOrderUnchanged() throws Exception {
        var secondSkuCode = "SECOND-SKU-" + System.nanoTime();
        jdbc.update("insert into product_sku (product_id, sku_code, sku_name, spec_text, sales_unit, default_sale_price, "
                + "standard_cost, is_default, status, created_at, updated_at) "
                + "select product_id, ?, '第二规格', '第二规格', sales_unit, default_sale_price, standard_cost, false, "
                + "status, now(3), now(3) from product_sku where id=?", secondSkuCode, skuId);
        long secondSkuId = jdbc.queryForObject("select id from product_sku where sku_code=?", Long.class, secondSkuCode);
        long draftId = createDraft(List.of(Map.of("skuId", skuId, "quantity", 2, "unitPrice", 12),
                Map.of("skuId", secondSkuId, "quantity", 1, "unitPrice", 12)));
        jdbc.update("insert into inventory_balance (warehouse_id, sku_id, quantity, version_no, created_at, updated_at) "
                + "values (?, ?, 10, 0, now(3), now(3))", warehouseId, skuId);

        mvc.perform(post("/api/sales-orders/{id}/confirm", draftId).header("Authorization", bearerToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("库存不足"));
        assertThatInventoryQuantityIs("10");
        assertThat(jdbc.queryForObject("select status from sales_order where id=?", String.class, draftId)).isEqualTo("draft");
        assertThat(jdbc.queryForObject("select count(*) from inventory_ledger where source_type='sales' and source_id=?",
                Long.class, draftId)).isZero();
    }

    private long createDraft(List<Map<String, Object>> lines) throws Exception {
        var result = mvc.perform(post("/api/sales-orders").header("Authorization", bearerToken())
                        .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId, "warehouseId", warehouseId,
                                "orderDate", java.time.LocalDate.now().toString(), "lines", lines))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void runConcurrentTransition(Runnable transition, String expectedRejection) throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from warehouse where id=? for update", Long.class, warehouseId);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("仓库锁等待超时");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.Callable<String> attempt = () -> {
                started.countDown();
                try { transition.run(); return "success"; }
                catch (BusinessException error) { return error.getMessage(); }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> first.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", expectedRejection);
        } finally { release.countDown(); }
    }

    private void assertThatInventoryQuantityIs(String expected) {
        var quantity = jdbc.queryForObject(
                "select quantity from inventory_balance where warehouse_id = ? and sku_id = ?",
                BigDecimal.class, warehouseId, skuId
        );
        org.assertj.core.api.Assertions.assertThat(quantity).isEqualByComparingTo(expected);
    }

    private String bearerToken() {
        return "Bearer " + com.bebefish.erp.support.TestAuthTokens.issue(
                jdbc, tokenIssuer, "13900000010", "sales:view", "sales:create");
    }
}
