package com.bebefish.erp.authorization.infrastructure;

import com.bebefish.erp.authorization.domain.FeishuRoleMappingRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcFeishuRoleMappingRepository implements FeishuRoleMappingRepository {
    private final JdbcTemplate jdbc;

    public JdbcFeishuRoleMappingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Long> findEnabledErpRoleIds(String tenantKey, Set<String> feishuRoleIds) {
        if (feishuRoleIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(feishuRoleIds.size(), "?"));
        var arguments = new java.util.ArrayList<Object>();
        arguments.add(tenantKey);
        arguments.addAll(feishuRoleIds);
        return new LinkedHashSet<>(jdbc.queryForList("""
                select distinct m.erp_role_id
                from sys_feishu_role_mapping m
                join sys_role r on r.id = m.erp_role_id
                where m.tenant_key = ? and m.enabled = true
                  and r.status = 'enabled' and r.is_sensitive = false
                  and m.feishu_role_id in (%s)
                """.formatted(placeholders), Long.class, arguments.toArray()));
    }

    @Override
    public long basicEmployeeRoleId() {
        return jdbc.queryForObject(
                "select id from sys_role where code = 'BASIC_EMPLOYEE' and status = 'enabled'",
                Long.class
        );
    }

    @Override
    @Transactional
    public void replaceFeishuAssignments(long userId, Set<Long> roleIds) {
        jdbc.update("delete from sys_user_role where user_id = ? and assignment_source = 'FEISHU'", userId);
        for (Long roleId : roleIds) {
            jdbc.update("""
                    insert into sys_user_role (user_id, role_id, assignment_source, assigned_at)
                    values (?, ?, 'FEISHU', now(3))
                    """, userId, roleId);
        }
    }
}
