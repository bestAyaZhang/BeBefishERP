package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.SessionRepository;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.authorization.application.AuthorizationResolver;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSessionRepository implements SessionRepository, TokenIssuer {
    private final JdbcTemplate jdbc;
    private final Sha256TokenHasher hasher;
    private final UserAccountRepository users;
    private final AuthorizationResolver authorizationResolver;
    private final Clock clock;
    private final Duration sessionTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public JdbcSessionRepository(
            JdbcTemplate jdbc,
            Sha256TokenHasher hasher,
            UserAccountRepository users,
            AuthorizationResolver authorizationResolver,
            Clock clock,
            @Value("${erp.auth.session-ttl:PT12H}") Duration sessionTtl
    ) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.users = users;
        this.authorizationResolver = authorizationResolver;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Override
    public LoginResult issue(AuthenticatedUser user, String loginMethod) {
        return issue(user, loginMethod, sessionTtl);
    }

    @Override
    public LoginResult issue(AuthenticatedUser user, String loginMethod, Duration ttl) {
        String token = randomToken();
        Instant now = Instant.now(clock);
        jdbc.update("""
                insert into sys_auth_session
                    (user_id, token_hash, login_method, created_at, expires_at)
                values (?, ?, ?, ?, ?)
                """,
                user.userId(), hasher.hash(token), loginMethod,
                Timestamp.from(now), Timestamp.from(now.plus(ttl))
        );
        return result(token, user, loginMethod);
    }

    @Override
    public LoginResult resolve(String accessToken) {
        try {
            var session = jdbc.queryForObject("""
                    select user_id, login_method, expires_at, revoked_at
                    from sys_auth_session
                    where token_hash = ?
                    """, (rs, rowNum) -> new StoredSession(
                    rs.getLong("user_id"),
                    rs.getString("login_method"),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
            ), hasher.hash(accessToken));
            Instant now = Instant.now(clock);
            if (session.revokedAt() != null || !session.expiresAt().isAfter(now)) {
                throw unauthorized();
            }
            var account = users.findById(session.userId()).orElseThrow(this::unauthorized);
            if (!account.enabled() || !account.employeeActive()) {
                throw unauthorized();
            }
            var authorization = authorizationResolver.resolve(account.id());
            var user = new AuthenticatedUser(
                    account.id(), account.employeeId(), account.mobile(), account.displayName(), account.avatarUrl(),
                    authorization.roles(), authorization.permissions()
            );
            return result(accessToken, user, session.loginMethod());
        } catch (EmptyResultDataAccessException exception) {
            throw unauthorized();
        }
    }

    @Override
    public void revoke(String accessToken) {
        jdbc.update("""
                update sys_auth_session
                set revoked_at = coalesce(revoked_at, ?)
                where token_hash = ?
                """, Timestamp.from(Instant.now(clock)), hasher.hash(accessToken));
    }

    private LoginResult result(String token, AuthenticatedUser user, String loginMethod) {
        return new LoginResult(
                token,
                user.employeeId(),
                user.mobile(),
                user.displayName(),
                user.avatarUrl(),
                user.roles(),
                user.permissions(),
                loginMethod,
                List.of()
        );
    }

    private AuthException unauthorized() {
        return new AuthException("UNAUTHORIZED", "未登录");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record StoredSession(long userId, String loginMethod, Instant expiresAt, Instant revokedAt) {
    }
}
