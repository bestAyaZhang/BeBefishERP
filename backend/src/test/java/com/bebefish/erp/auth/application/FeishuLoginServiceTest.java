package com.bebefish.erp.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "erp.feishu.enabled=true",
        "erp.feishu.app-id=mock-app",
        "erp.feishu.app-secret=mock-secret",
        "erp.feishu.redirect-uri=http://127.0.0.1:5173/api/auth/feishu/callback",
        "erp.feishu.allowed-tenant-key=tenant-a",
        "erp.feishu.mock-enabled=true"
})
@Transactional
class FeishuLoginServiceTest {
    @Autowired
    private FeishuLoginService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void mockLoginProvisionsUserSyncsRolesAndExchangesOneTimeTicket() {
        String ticket = service.login("mock-no-mobile", "127.0.0.1", "test-agent");

        var result = service.exchange(ticket);

        assertThat(result.loginMethod()).isEqualTo("feishu");
        assertThat(result.displayName()).isEqualTo("模拟飞书员工");
        assertThat(result.roles()).containsExactly("BASIC_EMPLOYEE");
        assertThat(result.warnings()).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_login_ticket where ticket_hash = ?",
                Integer.class,
                ticket
        )).isZero();
    }

    @Test
    void unavailableRoleDirectoryReturnsStableDegradedWarning() {
        String ticket = service.login("mock-role-degraded", "127.0.0.1", "test-agent");

        assertThat(service.exchange(ticket).warnings())
                .containsExactly("FEISHU_ROLE_SYNC_DEGRADED");
    }
}
