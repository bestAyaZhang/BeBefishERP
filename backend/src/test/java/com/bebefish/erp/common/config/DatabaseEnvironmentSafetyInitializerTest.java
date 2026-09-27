package com.bebefish.erp.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

class DatabaseEnvironmentSafetyInitializerTest {
    private static final String LOCAL = "jdbc:mysql://127.0.0.1:3306/bebefish_erp?useSSL=false";
    private static final String TEST = "jdbc:mysql://localhost:3306/bebefish_test?useSSL=false";
    private static final String PROD = "jdbc:mysql://mysql.production.internal:3306/bebefish_prod?useSSL=true";

    @Test
    void isRegisteredForEarlyStartupBeforeDatabaseMigration() {
        assertThat(SpringFactoriesLoader.loadFactories(ApplicationContextInitializer.class,
                getClass().getClassLoader()))
                .anyMatch(initializer -> initializer instanceof DatabaseEnvironmentSafetyInitializer);
    }

    @Test
    void permitsWebOnlyContextsWithoutAConfiguredDatasource() {
        var context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment()
                .withProperty("spring.datasource.url", "${ERP_DB_URL}"));

        assertThatCode(() -> new DatabaseEnvironmentSafetyInitializer().initialize(context))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultsToLocalAndRejectsRemoteOrTestDatabases() {
        assertThatCode(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of(), LOCAL, null, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("local"), PROD, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("本地");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("local"), TEST, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("测试");
    }

    @Test
    void testProfileOnlyAcceptsLocalDedicatedTestDatabase() {
        assertThatCode(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("test"), TEST, null, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("test"), LOCAL, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("_test");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("test"),
                "jdbc:mysql://mysql.production.internal:3306/bebefish_test", null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("本地");
    }

    @Test
    void prodProfileRequiresExplicitMatchingOnlineDatabase() {
        assertThatCode(() -> DatabaseEnvironmentSafetyInitializer.validate(
                Set.of("prod"), PROD, "mysql.production.internal", "bebefish_prod"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("prod"), PROD, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ERP_PROD_DB_HOST");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(
                Set.of("prod"), PROD, "other.internal", "bebefish_prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("线上数据库");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(
                Set.of("prod"), LOCAL, "127.0.0.1", "bebefish_erp"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("线上数据库");
    }

    @Test
    void rejectsAmbiguousProfilesAndUnsupportedJdbcUrl() {
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("local", "test"), LOCAL, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Profile");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("staging"), LOCAL, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Profile");
        assertThatThrownBy(() -> DatabaseEnvironmentSafetyInitializer.validate(Set.of("local"), "jdbc:h2:mem:test", null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MySQL");
    }
}
