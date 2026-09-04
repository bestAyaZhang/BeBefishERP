package com.bebefish.erp.authorization.application;

import com.bebefish.erp.feishu.FeishuBusinessRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeishuRoleMappingSyncStatusWriter {
    private final JdbcTemplate jdbc;

    public FeishuRoleMappingSyncStatusWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void markSuccessful(String tenantKey, List<FeishuBusinessRole> roles, Instant syncedAt) {
        for (FeishuBusinessRole role : roles) {
            jdbc.update("""
                    update sys_feishu_role_mapping
                    set feishu_role_name = ?, member_count = ?, last_synced_at = ?,
                        last_error = null, updated_at = now(3)
                    where tenant_key = ? and feishu_role_id = ?
                    """, role.name(), role.memberCount(), Timestamp.from(syncedAt), tenantKey, role.id());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String tenantKey, String errorCode) {
        jdbc.update("""
                update sys_feishu_role_mapping
                set last_error = ?, updated_at = now(3)
                where tenant_key = ?
                """, errorCode, tenantKey);
    }
}
