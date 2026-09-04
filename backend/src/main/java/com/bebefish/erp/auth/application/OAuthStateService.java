package com.bebefish.erp.auth.application;

import com.bebefish.erp.auth.infrastructure.Sha256TokenHasher;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthStateService {
    private final JdbcTemplate jdbc;
    private final Sha256TokenHasher hasher;
    private final Clock clock;
    private final Duration stateTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateService(
            JdbcTemplate jdbc,
            Sha256TokenHasher hasher,
            Clock clock,
            @Value("${erp.auth.oauth-state-ttl:PT5M}") Duration stateTtl
    ) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.clock = clock;
        this.stateTtl = stateTtl;
    }

    public String issue() {
        String state = randomToken();
        Instant now = Instant.now(clock);
        jdbc.update("""
                insert into sys_oauth_state (state_hash, created_at, expires_at)
                values (?, ?, ?)
                """, hasher.hash(state), Timestamp.from(now), Timestamp.from(now.plus(stateTtl)));
        return state;
    }

    @Transactional
    public void consume(String requestState, String cookieState) {
        if (requestState == null || cookieState == null
                || !MessageDigest.isEqual(
                requestState.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                cookieState.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw invalid();
        }
        var rows = jdbc.query("""
                select id, expires_at, consumed_at
                from sys_oauth_state where state_hash = ? for update
                """, (rs, rowNum) -> new StoredState(
                rs.getLong("id"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
        ), hasher.hash(requestState));
        Instant now = Instant.now(clock);
        if (rows.size() != 1 || rows.get(0).consumedAt() != null || !rows.get(0).expiresAt().isAfter(now)) {
            throw invalid();
        }
        if (jdbc.update(
                "update sys_oauth_state set consumed_at = ? where id = ? and consumed_at is null",
                Timestamp.from(now), rows.get(0).id()
        ) != 1) {
            throw invalid();
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthException invalid() {
        return new AuthException("FEISHU_STATE_INVALID", "飞书登录状态无效，请重新扫码");
    }

    private record StoredState(long id, Instant expiresAt, Instant consumedAt) {
    }
}
