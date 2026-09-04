package com.bebefish.erp.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.TokenIssuer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JdbcSessionRepositoryTest {
    @Autowired
    private TokenIssuer sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesOnlyAHashAndResolvesCurrentAccountAuthorization() {
        var user = seededAdministrator();

        var login = sessions.issue(user, "password");

        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.employeeId()).isEqualTo(user.employeeId());
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_auth_session where token_hash = ?",
                Integer.class,
                login.accessToken()
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select char_length(token_hash) from sys_auth_session where user_id = ? order by id desc limit 1",
                Integer.class,
                user.userId()
        )).isEqualTo(64);
        assertThat(sessions.resolve(login.accessToken()).roles()).contains("SUPER_ADMIN");
    }

    @Test
    void rejectsRevokedAndExpiredSessions() {
        var revoked = sessions.issue(seededAdministrator(), "password");
        sessions.revoke(revoked.accessToken());
        assertUnauthorized(() -> sessions.resolve(revoked.accessToken()));

        var expired = sessions.issue(seededAdministrator(), "password");
        jdbc.update("""
                update sys_auth_session
                set created_at = date_sub(now(3), interval 2 hour),
                    expires_at = date_sub(now(3), interval 1 hour)
                where id = (select id from (
                    select max(id) id from sys_auth_session
                ) latest)
                """);
        assertUnauthorized(() -> sessions.resolve(expired.accessToken()));
    }

    private AuthenticatedUser seededAdministrator() {
        return jdbc.queryForObject("""
                select u.id user_id, e.id employee_id, u.mobile, e.name, e.avatar_url
                from sys_user u join employee e on e.id = u.employee_id
                where u.mobile = '13800138000'
                """, (rs, rowNum) -> new AuthenticatedUser(
                rs.getLong("user_id"),
                rs.getLong("employee_id"),
                rs.getString("mobile"),
                rs.getString("name"),
                rs.getString("avatar_url"),
                List.of("SUPER_ADMIN"),
                List.of("system:role:manage")
        ));
    }

    private void assertUnauthorized(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("UNAUTHORIZED");
    }
}
