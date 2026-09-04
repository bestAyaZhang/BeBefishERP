package com.bebefish.erp.common.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class MigrationCompatibilityTest {

    private static final List<String> MIGRATIONS = List.of(
            "db/migration/V1__masterdata_product_schema.sql",
            "db/migration/V2__seed_walk_in_customer.sql",
            "db/migration/V3__business_code_sequence.sql",
            "db/migration/V4__inventory_schema.sql",
            "db/migration/V5__stock_adjustment_schema.sql",
            "db/migration/V6__sales_draft_schema.sql",
            "db/migration/V7__supplier_quote_integrity.sql",
            "db/migration/V10__identity_auth_rbac.sql");

    @Test
    void migrationsUseCollationSupportedByMySql8AndMariaDb() throws IOException {
        for (String migration : MIGRATIONS) {
            var resource = MigrationCompatibilityTest.class.getClassLoader().getResourceAsStream(migration);
            assert resource != null : "Missing migration resource: " + migration;
            var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(sql.contains("utf8mb4_0900_ai_ci"),
                    () -> migration + " must not require the MySQL 8-only utf8mb4_0900_ai_ci collation");
        }
    }
}
