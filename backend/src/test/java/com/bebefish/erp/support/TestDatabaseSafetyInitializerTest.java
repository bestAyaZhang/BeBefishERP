package com.bebefish.erp.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class TestDatabaseSafetyInitializerTest {
    @Test
    void rejectsDatasourceWhoseDatabaseNameIsNotMarkedForTests() {
        var context = context("jdbc:mysql://db.internal:3306/bebefish_erp?useSSL=false");

        assertThatThrownBy(() -> new TestDatabaseSafetyInitializer().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("测试数据库");
    }

    @Test
    void acceptsExplicitTestDatabaseName() {
        var context = context("jdbc:mysql://127.0.0.1:33306/bebefish_test?useSSL=false");

        assertThatCode(() -> new TestDatabaseSafetyInitializer().initialize(context))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDatabaseNamesThatOnlyContainTestAsPartOfAnotherWord() {
        for (String database : java.util.List.of("contest", "latest")) {
            var context = context("jdbc:mysql://127.0.0.1:33306/" + database + "?useSSL=false");

            assertThatThrownBy(() -> new TestDatabaseSafetyInitializer().initialize(context))
                    .as(database)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private GenericApplicationContext context(String datasourceUrl) {
        var context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", datasourceUrl));
        return context;
    }
}
