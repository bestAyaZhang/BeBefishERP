package com.bebefish.erp.support;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern MYSQL_DATABASE = Pattern.compile(
            "(?i)^jdbc:mysql://[^/]+/([^?;]+)(?:[?;].*)?$"
    );

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String url = applicationContext.getEnvironment().getProperty("spring.datasource.url", "").trim();
        var matcher = MYSQL_DATABASE.matcher(url);
        if (!matcher.matches() || !matcher.group(1).toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalStateException("集成测试只能连接名称包含 test 的专用测试数据库");
        }
    }
}
