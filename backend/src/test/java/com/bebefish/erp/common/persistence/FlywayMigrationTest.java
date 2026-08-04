package com.bebefish.erp.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
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
        var databaseName = jdbc.queryForObject("select database()", String.class);
        assertThat(databaseName)
                .as("Flyway migration tests may only clean a dedicated *_test database")
                .endsWith("_test");

        var flyway = Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
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
