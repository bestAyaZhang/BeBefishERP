package com.bebefish.erp.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import java.math.BigDecimal;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
class FlywayMigrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void migrateFromEmptyTestDatabase() {
        assertDedicatedTestDatabase();
        var flyway = flyway();
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void leaveDedicatedTestDatabaseAtLatestVersion() {
        assertDedicatedTestDatabase();
        flyway().migrate();
    }

    private void assertDedicatedTestDatabase() {
        var databaseName = jdbc.queryForObject("select database()", String.class);
        assertThat(databaseName)
                .as("Flyway migration tests may only clean a dedicated *_test database")
                .endsWith("_test");
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load();
    }

    @Test
    void createsMasterdataAndProductTables() {
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = database() and table_name <> 'flyway_schema_history'",
                String.class
        );

        assertThat(tables).containsExactlyInAnyOrder(
                "product_category", "customer", "supplier", "warehouse",
                "product_spu", "product_sku", "product_spec", "product_spec_value",
                "product_sku_spec_value", "sku_supplier_quote", "supplier_quote_allowed_state",
                "file_asset", "business_code_sequence",
                "inventory_balance", "inventory_ledger", "stock_adjustment", "stock_adjustment_item",
                "sales_order_sequence", "sales_order", "sales_order_item"
        );
    }

    @Test
    void seedsRequiredWalkInCustomer() {
        var customers = jdbc.queryForList(
                "select customer_name from customer where customer_no = 'WALK_IN' and is_system = true",
                String.class
        );

        assertThat(customers).containsExactly("散客");
    }

    @Test
    void rejectsSecondEnabledDefaultWarehouse() {
        insertWarehouse("WH01", true, "enabled");

        assertThatThrownBy(() -> insertWarehouse("WH02", true, "enabled"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDisabledDefaultSupplierQuote() {
        var productId = insertProduct("P100", "ITEM100");
        var skuId = insertSku(productId, "SKU100");
        var supplierId = insertSupplier("SUP100");

        assertThatThrownBy(() -> jdbc.update(
                "insert into sku_supplier_quote "
                        + "(sku_id, supplier_id, purchase_price, min_purchase_quantity, is_default, status, "
                        + "created_at, updated_at) values (?, ?, 2.20, 1, true, 'disabled', now(3), now(3))",
                skuId, supplierId
        )).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_sku_supplier_quote_default_enabled");
    }

    @Test
    void rejectsSpecValueThatBelongsToAnotherSpec() {
        var productId = insertProduct("P200", "ITEM200");
        var skuId = insertSku(productId, "SKU200");
        var colorSpecId = insertSpec(productId, "Color");
        var patternSpecId = insertSpec(productId, "Pattern");
        var patternValueId = insertSpecValue(patternSpecId, "Stripe");

        assertThatThrownBy(() -> insertSkuSpecValue(
                productId, skuId, colorSpecId, patternValueId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSpecThatBelongsToAnotherProduct() {
        var firstProductId = insertProduct("P300", "ITEM300");
        var secondProductId = insertProduct("P301", "ITEM301");
        var firstProductSkuId = insertSku(firstProductId, "SKU300");
        var secondProductSpecId = insertSpec(secondProductId, "Color");
        var secondProductValueId = insertSpecValue(secondProductSpecId, "Clear");

        assertThatThrownBy(() -> insertSkuSpecValue(
                firstProductId, firstProductSkuId, secondProductSpecId, secondProductValueId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void addsSafetyStockAndInnerPackagingColumns() {
        var columns = jdbc.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = database() and table_name = 'product_sku'",
                String.class
        );
        assertThat(columns).contains(
                "safety_stock_quantity", "inner_package_length_cm", "inner_package_width_cm",
                "inner_package_height_cm", "inner_package_weight_kg"
        );
        var productId = insertProduct("P800", "ITEM800");
        var skuId = insertSku(productId, "SKU800");
        assertThat(jdbc.queryForObject(
                "select safety_stock_quantity from product_sku where id = ?",
                BigDecimal.class, skuId
        )).isEqualByComparingTo("0");
    }

    @Test
    void upgradesRepresentativeLegacyRowsFromV7ToV8WithoutDataLoss() {
        var v7 = Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .target("7")
                .load();
        v7.clean();
        v7.migrate();

        var categoryId = insertCategory("CAT-V7-UPGRADE");
        jdbc.update(
                "insert into product_spu "
                        + "(product_code, item_no, product_name, category_id, brand, product_type, status, remark, "
                        + "created_at, updated_at) values "
                        + "('P-V7-UPGRADE', 'ITEM-V7-UPGRADE', 'V7 历史商品', ?, '历史品牌', 'variant', "
                        + "'disabled', '保留备注', '2026-01-02 03:04:05.123', '2026-02-03 04:05:06.123')",
                categoryId
        );
        var productId = requiredId("product_spu", "product_code", "P-V7-UPGRADE");
        jdbc.update(
                "insert into product_sku "
                        + "(product_id, sku_code, barcode, sku_name, spec_text, sales_unit, default_sale_price, "
                        + "standard_cost, package_length_cm, package_width_cm, package_height_cm, package_volume_cm3, "
                        + "net_weight_kg, gross_weight_kg, gram_weight_g, packaging_method, carton_quantity, "
                        + "is_default, status, created_at, updated_at) values "
                        + "(?, 'SKU-V7-UPGRADE', '6970000007001', 'V7 历史 SKU', '透明 / 500ml', '只', "
                        + "19.9000, 8.6000, 42.000, 31.000, 28.000, 36456.000, 8.500, 9.200, 350.000, "
                        + "'彩盒', 12, true, 'disabled', '2026-01-02 03:04:05.123', '2026-02-03 04:05:06.123')",
                productId
        );

        flyway().migrate();

        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                String.class
        )).isEqualTo("8");
        var product = jdbc.queryForMap(
                "select item_no, product_name, brand, product_type, status, remark from product_spu where id = ?",
                productId
        );
        assertThat(product).containsEntry("item_no", "ITEM-V7-UPGRADE")
                .containsEntry("product_name", "V7 历史商品")
                .containsEntry("brand", "历史品牌")
                .containsEntry("product_type", "variant")
                .containsEntry("status", "disabled")
                .containsEntry("remark", "保留备注");
        var sku = jdbc.queryForMap(
                "select barcode, default_sale_price, standard_cost, package_length_cm, net_weight_kg, "
                        + "is_default, status, safety_stock_quantity, inner_package_length_cm, "
                        + "inner_package_width_cm, inner_package_height_cm, inner_package_weight_kg "
                        + "from product_sku where sku_code = 'SKU-V7-UPGRADE'"
        );
        assertThat((BigDecimal) sku.get("default_sale_price")).isEqualByComparingTo("19.9000");
        assertThat((BigDecimal) sku.get("standard_cost")).isEqualByComparingTo("8.6000");
        assertThat((BigDecimal) sku.get("package_length_cm")).isEqualByComparingTo("42.000");
        assertThat((BigDecimal) sku.get("net_weight_kg")).isEqualByComparingTo("8.500");
        assertThat((BigDecimal) sku.get("safety_stock_quantity")).isEqualByComparingTo("0");
        assertThat(sku).containsEntry("barcode", "6970000007001")
                .containsEntry("is_default", true)
                .containsEntry("status", "disabled")
                .containsEntry("inner_package_length_cm", null)
                .containsEntry("inner_package_width_cm", null)
                .containsEntry("inner_package_height_cm", null)
                .containsEntry("inner_package_weight_kg", null);

        assertThatThrownBy(() -> jdbc.update(
                "update product_sku set safety_stock_quantity = -0.0001 where sku_code = 'SKU-V7-UPGRADE'"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update product_sku set inner_package_weight_kg = -0.001 where sku_code = 'SKU-V7-UPGRADE'"
        )).isInstanceOf(DataAccessException.class);
    }

    private void insertWarehouse(String number, boolean isDefault, String status) {
        jdbc.update(
                "insert into warehouse "
                        + "(warehouse_no, warehouse_name, is_default, status, created_at, updated_at) "
                        + "values (?, ?, ?, ?, now(3), now(3))",
                number, number, isDefault, status
        );
    }

    private long insertProduct(String productCode, String itemNo) {
        var categoryId = insertCategory("CAT-" + productCode);
        jdbc.update(
                "insert into product_spu "
                        + "(product_code, item_no, product_name, category_id, product_type, status, "
                        + "created_at, updated_at) values (?, ?, ?, ?, 'simple', 'enabled', now(3), now(3))",
                productCode, itemNo, productCode, categoryId
        );
        return requiredId("product_spu", "product_code", productCode);
    }

    private long insertCategory(String code) {
        jdbc.update(
                "insert into product_category "
                        + "(category_code, category_name, level_no, sort_order, status, created_at, updated_at) "
                        + "values (?, ?, 1, 0, 'enabled', now(3), now(3))",
                code, code
        );
        return requiredId("product_category", "category_code", code);
    }

    private long insertSku(long productId, String skuCode) {
        jdbc.update(
                "insert into product_sku "
                        + "(product_id, sku_code, sku_name, sales_unit, default_sale_price, standard_cost, "
                        + "is_default, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'piece', 0, 0, true, 'enabled', now(3), now(3))",
                productId, skuCode, skuCode
        );
        return requiredId("product_sku", "sku_code", skuCode);
    }

    private long insertSupplier(String supplierNo) {
        jdbc.update(
                "insert into supplier "
                        + "(supplier_no, supplier_name, status, created_at, updated_at) "
                        + "values (?, ?, 'enabled', now(3), now(3))",
                supplierNo, supplierNo
        );
        return requiredId("supplier", "supplier_no", supplierNo);
    }

    private long insertSpec(long productId, String name) {
        jdbc.update(
                "insert into product_spec "
                        + "(product_id, spec_name, sort_order, status, created_at, updated_at) "
                        + "values (?, ?, 0, 'enabled', now(3), now(3))",
                productId, name
        );
        return jdbc.queryForObject(
                "select id from product_spec where product_id = ? and spec_name = ?",
                Long.class, productId, name
        );
    }

    private long insertSpecValue(long specId, String name) {
        jdbc.update(
                "insert into product_spec_value "
                        + "(spec_id, value_name, sort_order, status, created_at, updated_at) "
                        + "values (?, ?, 0, 'enabled', now(3), now(3))",
                specId, name
        );
        return jdbc.queryForObject(
                "select id from product_spec_value where spec_id = ? and value_name = ?",
                Long.class, specId, name
        );
    }

    private void insertSkuSpecValue(long productId, long skuId, long specId, long specValueId) {
        if (hasColumn("product_sku_spec_value", "product_id")) {
            jdbc.update(
                    "insert into product_sku_spec_value "
                            + "(product_id, sku_id, spec_id, spec_value_id, created_at, updated_at) "
                            + "values (?, ?, ?, ?, now(3), now(3))",
                    productId, skuId, specId, specValueId
            );
            return;
        }

        jdbc.update(
                "insert into product_sku_spec_value "
                        + "(sku_id, spec_id, spec_value_id, created_at, updated_at) "
                        + "values (?, ?, ?, now(3), now(3))",
                skuId, specId, specValueId
        );
    }

    private boolean hasColumn(String tableName, String columnName) {
        var count = jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() and table_name = ? and column_name = ?",
                Integer.class, tableName, columnName
        );
        return count != null && count > 0;
    }

    private long requiredId(String tableName, String keyColumn, String keyValue) {
        var allowedTables = java.util.Set.of("product_category", "product_spu", "product_sku", "supplier");
        var allowedColumns = java.util.Set.of("category_code", "product_code", "sku_code", "supplier_no");
        if (!allowedTables.contains(tableName) || !allowedColumns.contains(keyColumn)) {
            throw new IllegalArgumentException("Unsupported identifier lookup");
        }
        return jdbc.queryForObject(
                "select id from " + tableName + " where " + keyColumn + " = ?",
                Long.class, keyValue
        );
    }
}
