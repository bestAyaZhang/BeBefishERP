package com.bebefish.erp.identity.infrastructure;

import com.bebefish.erp.identity.domain.FeishuIdentity;
import com.bebefish.erp.identity.domain.FeishuIdentityRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFeishuIdentityRepository implements FeishuIdentityRepository {
    private final JdbcTemplate jdbc;

    public JdbcFeishuIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FeishuIdentity> find(String tenantKey, String unionId, String openId) {
        if (unionId != null && !unionId.isBlank()) {
            var byUnion = jdbc.query("""
                    select * from sys_feishu_identity where tenant_key = ? and union_id = ?
                    """, this::map, tenantKey, unionId).stream().findFirst();
            if (byUnion.isPresent()) {
                return byUnion;
            }
        }
        return jdbc.query("""
                select * from sys_feishu_identity where tenant_key = ? and open_id = ?
                """, this::map, tenantKey, openId).stream().findFirst();
    }

    @Override
    public Optional<FeishuIdentity> findByUserId(long userId) {
        return jdbc.query("select * from sys_feishu_identity where user_id = ?", this::map, userId)
                .stream().findFirst();
    }

    @Override
    public FeishuIdentity save(FeishuIdentity identity) {
        if (identity.id() > 0) {
            jdbc.update("""
                    update sys_feishu_identity
                    set open_id = ?, union_id = ?, display_name = ?, avatar_url = ?, last_verified_at = ?
                    where id = ?
                    """, identity.openId(), identity.unionId(), identity.displayName(), identity.avatarUrl(),
                    Timestamp.from(identity.lastVerifiedAt()), identity.id());
            return identity;
        }
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into sys_feishu_identity
                        (user_id, tenant_key, open_id, union_id, display_name, avatar_url,
                         bound_at, last_verified_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, identity.userId());
            statement.setString(2, identity.tenantKey());
            statement.setString(3, identity.openId());
            statement.setString(4, identity.unionId());
            statement.setString(5, identity.displayName());
            statement.setString(6, identity.avatarUrl());
            statement.setTimestamp(7, Timestamp.from(identity.boundAt()));
            statement.setTimestamp(8, Timestamp.from(identity.lastVerifiedAt()));
            return statement;
        }, keys);
        return new FeishuIdentity(
                keys.getKey().longValue(), identity.userId(), identity.tenantKey(), identity.openId(),
                identity.unionId(), identity.displayName(), identity.avatarUrl(),
                identity.boundAt(), identity.lastVerifiedAt()
        );
    }

    private FeishuIdentity map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FeishuIdentity(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("tenant_key"),
                rs.getString("open_id"), rs.getString("union_id"), rs.getString("display_name"),
                rs.getString("avatar_url"), rs.getTimestamp("bound_at").toInstant(),
                rs.getTimestamp("last_verified_at").toInstant()
        );
    }
}
