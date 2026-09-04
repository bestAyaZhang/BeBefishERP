package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.domain.LoginTicket;
import com.bebefish.erp.auth.domain.LoginTicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcLoginTicketRepository implements LoginTicketRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLoginTicketRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String ticketHash, long userId, List<String> warnings, Instant createdAt, Instant expiresAt) {
        jdbc.update("""
                insert into sys_login_ticket
                    (ticket_hash, user_id, warnings_json, created_at, expires_at)
                values (?, ?, ?, ?, ?)
                """, ticketHash, userId, writeWarnings(warnings), Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    @Override
    @Transactional
    public Optional<LoginTicket> consume(String ticketHash, Instant now) {
        try {
            var row = jdbc.queryForObject("""
                    select id, user_id, warnings_json, expires_at, consumed_at
                    from sys_login_ticket
                    where ticket_hash = ?
                    for update
                    """, (rs, rowNum) -> new StoredTicket(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("warnings_json"),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
            ), ticketHash);
            if (row.consumedAt() != null || !row.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            int updated = jdbc.update(
                    "update sys_login_ticket set consumed_at = ? where id = ? and consumed_at is null",
                    Timestamp.from(now), row.id()
            );
            return updated == 1
                    ? Optional.of(new LoginTicket(row.userId(), readWarnings(row.warningsJson())))
                    : Optional.empty();
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private String writeWarnings(List<String> warnings) {
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化登录提示", exception);
        }
    }

    private List<String> readWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(warningsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取登录提示", exception);
        }
    }

    private record StoredTicket(long id, long userId, String warningsJson, Instant expiresAt, Instant consumedAt) {
    }
}
