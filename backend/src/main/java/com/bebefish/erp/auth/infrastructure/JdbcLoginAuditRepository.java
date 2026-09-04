package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.domain.LoginAuditEvent;
import com.bebefish.erp.auth.domain.LoginAuditRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoginAuditRepository implements LoginAuditRepository {
    private final JdbcTemplate jdbc;

    public JdbcLoginAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(LoginAuditEvent event) {
        jdbc.update("""
                insert into sys_login_audit
                    (user_id, identity_method, result, error_code, tenant_key,
                     ip_address, user_agent, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.userId(), event.identityMethod(), event.result(), event.errorCode(), event.tenantKey(),
                event.ipAddress(), event.userAgent(), Timestamp.from(event.occurredAt())
        );
    }
}
