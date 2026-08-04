package com.bebefish.erp.sales.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.sales.domain.SalesOrder;
import com.bebefish.erp.sales.domain.SalesOrderItem;
import com.bebefish.erp.sales.domain.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SalesOrderRepositoryTest {
    @Autowired
    private SalesOrderRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long customerId;
    private long warehouseId;
    private long firstSkuId;
    private long secondSkuId;
    private String salesNo;

    @BeforeEach
    void insertReferences() {
        var suffix = String.valueOf(System.nanoTime());
        salesNo = "SO-REPOSITORY-" + suffix;
        jdbc.update(
                "insert into product_category "
                        + "(category_code, category_name, level_no, sort_order, status, created_at, updated_at) "
                        + "values (?, ?, 1, 0, 'enabled', now(3), now(3))",
                "SALES-CAT-" + suffix, "销售测试分类-" + suffix
        );
        var categoryId = jdbc.queryForObject(
                "select id from product_category where category_code = ?", Long.class, "SALES-CAT-" + suffix
        );
        jdbc.update(
                "insert into customer "
                        + "(customer_no, customer_name, contact_person, mobile, province, city, district, "
                        + "detail_address, default_shipping_method, default_settlement_period, status, "
                        + "created_at, updated_at) values (?, ?, '测试联系人', '13800138000', '浙江省', '杭州市', "
                        + "'西湖区', '文三路88号', 'delivery', 'monthly', 'enabled', now(3), now(3))",
                "SALES-CUS-" + suffix, "销售测试客户-" + suffix
        );
        customerId = jdbc.queryForObject(
                "select id from customer where customer_no = ?", Long.class, "SALES-CUS-" + suffix
        );
        jdbc.update(
                "insert into warehouse "
                        + "(warehouse_no, warehouse_name, is_default, status, created_at, updated_at) "
                        + "values (?, ?, false, 'enabled', now(3), now(3))",
                "SALES-WH-" + suffix, "销售测试仓库-" + suffix
        );
        warehouseId = jdbc.queryForObject(
                "select id from warehouse where warehouse_no = ?", Long.class, "SALES-WH-" + suffix
        );
        jdbc.update(
                "insert into product_spu "
                        + "(product_code, item_no, product_name, category_id, product_type, status, "
                        + "created_at, updated_at) values (?, ?, '销售测试商品', ?, 'variant', 'enabled', now(3), now(3))",
                "SALES-PRD-" + suffix, "SALES-ITEM-" + suffix, categoryId
        );
        var productId = jdbc.queryForObject(
                "select id from product_spu where item_no = ?", Long.class, "SALES-ITEM-" + suffix
        );
        firstSkuId = insertSku(productId, "SALES-SKU-A-" + suffix, "红色");
        secondSkuId = insertSku(productId, "SALES-SKU-B-" + suffix, "蓝色");
    }

    @Test
    void savesAndLoadsDraftWithAllDeliveryAndLineFields() {
        var order = new SalesOrder(
                null, salesNo, customerId, "销售测试客户", warehouseId, "销售测试仓库",
                LocalDate.of(2026, 7, 10), "13800138000", "draft", "delivery", "monthly", null,
                "浙江省杭州市西湖区文三路88号", null, null, "普盒", false, "not_required",
                new BigDecimal("3200.00"), new BigDecimal("50.00"), new BigDecimal("100.00"),
                new BigDecimal("3300.00"), new BigDecimal("1000.00"), new BigDecimal("2300.00"),
                "客户备注", null, null,
                List.of(
                        item(firstSkuId, "红色", "100.00", "220.00", "22000.00"),
                        item(secondSkuId, "蓝色", "100.00", "10.00", "1000.00")
                )
        );

        var saved = repository.save(order);
        var loaded = repository.findById(saved.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo("draft");
        assertThat(loaded.transportMethod()).isEqualTo("delivery");
        assertThat(loaded.deliveryAddress()).contains("文三路88号");
        assertThat(loaded.items()).hasSize(2);
        assertThat(loaded.items().getFirst().skuId()).isEqualTo(firstSkuId);
        assertThat(loaded.items().getFirst().specificationSnapshot()).isEqualTo("红色");
        assertThat(loaded.totalAmount()).isEqualByComparingTo("3300.00");
    }

    private long insertSku(long productId, String code, String specification) {
        jdbc.update(
                "insert into product_sku "
                        + "(product_id, sku_code, sku_name, spec_text, sales_unit, default_sale_price, "
                        + "standard_cost, is_default, status, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'piece', 10, 3, false, 'enabled', now(3), now(3))",
                productId, code, specification, specification
        );
        return jdbc.queryForObject("select id from product_sku where sku_code = ?", Long.class, code);
    }

    private SalesOrderItem item(long skuId, String specification, String quantity, String price, String amount) {
        return new SalesOrderItem(
                null, skuId, new BigDecimal(quantity), new BigDecimal("12.00"), new BigDecimal(price),
                BigDecimal.ZERO, new BigDecimal(amount), null, "ITEM-" + skuId, "销售测试商品",
                "SKU-" + skuId, specification, specification, "普盒", 48, "BAR-" + skuId, "只"
        );
    }
}
