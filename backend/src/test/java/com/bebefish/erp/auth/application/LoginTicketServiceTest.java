package com.bebefish.erp.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoginTicketServiceTest {
    @Autowired
    private LoginTicketService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ticketCanOnlyBeConsumedOnceAndPlaintextIsNeverStored() {
        long userId = jdbc.queryForObject(
                "select id from sys_user where mobile = '13800138000'",
                Long.class
        );
        var ticket = service.issue(userId, List.of("FEISHU_ROLE_SYNC_DEGRADED"));

        assertThat(jdbc.queryForObject(
                "select count(*) from sys_login_ticket where ticket_hash = ?",
                Integer.class,
                ticket
        )).isZero();
        var consumed = service.consume(ticket);
        assertThat(consumed.userId()).isEqualTo(userId);
        assertThat(consumed.warnings()).containsExactly("FEISHU_ROLE_SYNC_DEGRADED");
        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_CALLBACK_EXPIRED");
    }

    @Test
    void expiredTicketCannotBeConsumed() {
        long userId = jdbc.queryForObject(
                "select id from sys_user where mobile = '13800138000'",
                Long.class
        );
        var ticket = service.issue(userId, List.of());
        jdbc.update("""
                update sys_login_ticket
                set created_at = date_sub(now(3), interval 2 minute),
                    expires_at = date_sub(now(3), interval 1 minute)
                where id = (select id from (
                    select max(id) id from sys_login_ticket
                ) latest)
                """);

        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_CALLBACK_EXPIRED");
    }
}
