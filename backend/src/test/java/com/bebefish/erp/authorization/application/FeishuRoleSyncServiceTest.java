package com.bebefish.erp.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.feishu.FeishuBusinessRole;
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
class FeishuRoleSyncServiceTest {
    @Autowired
    private FeishuRoleSyncService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void replacesOnlyFeishuAssignmentsAndAlwaysKeepsBasicRole() {
        long userId = id("select id from sys_user where mobile = '13800138000'");
        jdbc.update("""
                insert into sys_role
                    (code, name, description, kind, immutable, is_sensitive, status,
                     data_scope, updated_by, created_at, updated_at)
                values ('WAREHOUSE_MANAGER', '仓库主管', '', 'custom', false, false, 'enabled',
                        'DEPARTMENT', '测试', now(3), now(3))
                """);
        long warehouseRoleId = id("select id from sys_role where code = 'WAREHOUSE_MANAGER'");
        jdbc.update("""
                insert into sys_feishu_role_mapping
                    (tenant_key, feishu_role_id, feishu_role_name, erp_role_id, enabled,
                     member_count, created_at, updated_at)
                values ('tenant-a', 'fs-warehouse', '飞书仓库主管', ?, true, 1, now(3), now(3))
                """, warehouseRoleId);

        assertThat(service.sync(userId, "tenant-a", List.of(
                new FeishuBusinessRole("fs-warehouse", "飞书仓库主管")
        ))).isEmpty();
        assertThat(assignments(userId, "FEISHU"))
                .containsExactlyInAnyOrder("BASIC_EMPLOYEE", "WAREHOUSE_MANAGER");
        assertThat(assignments(userId, "LOCAL")).containsExactly("SUPER_ADMIN");

        service.sync(userId, "tenant-a", List.of());
        assertThat(assignments(userId, "FEISHU")).containsExactly("BASIC_EMPLOYEE");
        assertThat(assignments(userId, "LOCAL")).containsExactly("SUPER_ADMIN");
    }

    @Test
    void degradedSyncRemovesMappedRolesButRetainsBasicAndLocalRoles() {
        long userId = id("select id from sys_user where mobile = '13800138000'");

        assertThat(service.syncDegraded(userId))
                .containsExactly("FEISHU_ROLE_SYNC_DEGRADED");
        assertThat(assignments(userId, "FEISHU")).containsExactly("BASIC_EMPLOYEE");
        assertThat(assignments(userId, "LOCAL")).containsExactly("SUPER_ADMIN");
    }

    private List<String> assignments(long userId, String source) {
        return jdbc.queryForList("""
                select r.code from sys_user_role ur
                join sys_role r on r.id = ur.role_id
                where ur.user_id = ? and ur.assignment_source = ?
                order by r.code
                """, String.class, userId, source);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
