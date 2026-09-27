package com.bebefish.erp.common.config;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks the database target before Flyway or JPA can connect to it. */
public final class DatabaseEnvironmentSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String jdbcUrl;
        try {
            jdbcUrl = environment.getProperty("spring.datasource.url");
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            // Web-only test slices have no datasource; a real runtime still fails when creating one.
            return;
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) return;
        validate(
                new HashSet<>(Arrays.asList(environment.getActiveProfiles())),
                jdbcUrl,
                environment.getProperty("ERP_PROD_DB_HOST"),
                environment.getProperty("ERP_PROD_DB_NAME")
        );
    }

    static void validate(Set<String> profiles, String jdbcUrl, String expectedProdHost, String expectedProdDatabase) {
        if (profiles.size() > 1 || (!profiles.isEmpty()
                && !Set.of("local", "test", "prod").contains(profiles.iterator().next()))) {
            throw new IllegalStateException("只能启用一个数据库 Profile：local、test 或 prod");
        }
        String profile = profiles.isEmpty() ? "local" : profiles.iterator().next();
        URI target;
        try {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException();
            }
            target = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("数据库必须使用显式 MySQL JDBC 地址");
        }
        String host = target.getHost();
        String path = target.getPath();
        if (host == null || path == null || path.length() < 2 || path.indexOf('/', 1) >= 0) {
            throw new IllegalStateException("数据库必须使用单一主机和明确的数据库名称");
        }
        String database = path.substring(1);
        boolean loopback = Set.of("localhost", "127.0.0.1", "::1").contains(host.toLowerCase(Locale.ROOT));

        if ("local".equals(profile)) {
            if (!loopback) throw new IllegalStateException("开发环境只能连接本地数据库");
            String name = database.toLowerCase(Locale.ROOT);
            if (name.endsWith("_test")) throw new IllegalStateException("开发环境不能连接测试数据库");
            if (name.endsWith("_prod") || name.endsWith("_production")) {
                throw new IllegalStateException("开发环境不能连接线上数据库");
            }
        } else if ("test".equals(profile)) {
            if (!loopback) throw new IllegalStateException("本地测试环境只能连接本地测试数据库");
            if (!database.toLowerCase(Locale.ROOT).endsWith("_test")) {
                throw new IllegalStateException("测试环境数据库名称必须以 _test 结尾");
            }
        } else {
            if (expectedProdHost == null || expectedProdHost.isBlank()
                    || expectedProdDatabase == null || expectedProdDatabase.isBlank()) {
                throw new IllegalStateException("线上环境必须配置 ERP_PROD_DB_HOST 和 ERP_PROD_DB_NAME");
            }
            if (loopback || !host.equalsIgnoreCase(expectedProdHost.trim())
                    || !database.equals(expectedProdDatabase.trim()) || database.toLowerCase(Locale.ROOT).endsWith("_test")) {
                throw new IllegalStateException("线上数据库地址与指定的线上数据库不一致");
            }
        }
    }
}
