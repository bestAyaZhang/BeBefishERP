package com.bebefish.erp.authorization.infrastructure;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.PermissionDefinition;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoleRepository implements RoleRepository {
    private static final String ROLE_COLUMNS = """
            select distinct r.id, r.code, r.name, r.kind, r.is_sensitive, r.status, r.data_scope
            from sys_role r
            join sys_user_role ur on ur.role_id = r.id
            """;

    private final JdbcTemplate jdbc;

    public JdbcRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Role> findEnabledByUserId(long userId) {
        return loadRoles(ROLE_COLUMNS + " where ur.user_id = ? and r.status = 'enabled' order by r.code", userId, null);
    }

    @Override
    public List<Role> findEnabledByMemberKey(String memberKey) {
        return loadRoles(ROLE_COLUMNS + " join sys_user u on u.id = ur.user_id "
                + "where u.mobile = ? and r.status = 'enabled' order by r.code", memberKey, memberKey);
    }

    @Override
    public List<PermissionDefinition> findAllPermissions() {
        return jdbc.query("""
                select code, module_key, action_key, display_name
                from sys_permission
                order by id
                """, (rs, rowNum) -> new PermissionDefinition(
                rs.getString("code"),
                rs.getString("module_key"),
                rs.getString("action_key"),
                rs.getString("display_name")
        ));
    }

    private List<Role> loadRoles(String sql, Object argument, String memberKey) {
        var rows = jdbc.query(sql, (rs, rowNum) -> new RoleRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getBoolean("is_sensitive"),
                rs.getString("status"),
                rs.getString("data_scope")
        ), argument);
        // Release the role query's connection before loading each role's permissions.
        return rows.stream().map(row -> {
            Set<String> permissions = new LinkedHashSet<>(jdbc.queryForList("""
                    select p.code
                    from sys_permission p
                    join sys_role_permission rp on rp.permission_id = p.id
                    where rp.role_id = ?
                    order by p.code
                    """, String.class, row.id()));
            return new Role(
                    row.id(),
                    row.code(),
                    row.name(),
                    "system".equals(row.kind()),
                    "SUPER_ADMIN".equals(row.code()),
                    row.sensitive(),
                    "enabled".equals(row.status()),
                    DataScope.valueOf(row.dataScope()),
                    permissions,
                    memberKey == null ? Set.of() : Set.of(memberKey)
            );
        }).toList();
    }

    private record RoleRow(long id, String code, String name, String kind,
                           boolean sensitive, String status, String dataScope) {
    }
}
