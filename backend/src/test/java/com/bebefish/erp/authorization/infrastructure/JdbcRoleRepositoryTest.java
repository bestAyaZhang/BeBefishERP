package com.bebefish.erp.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JdbcRoleRepositoryTest {
    @Autowired
    private RoleRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsUnionOfLocalAndFeishuAssignmentsForUser() {
        long userId = id("select id from sys_user where mobile = '13800138000'");
        long basicRoleId = id("select id from sys_role where code = 'BASIC_EMPLOYEE'");
        jdbc.update("""
                insert into sys_role
                    (code, name, description, kind, immutable, is_sensitive, status,
                     data_scope, updated_by, created_at, updated_at)
                values ('PRODUCT_OPERATOR', '商品操作员', '', 'custom', false, false, 'enabled',
                        'DEPARTMENT', '测试', now(3), now(3))
                """);
        long productRoleId = id("select id from sys_role where code = 'PRODUCT_OPERATOR'");
        long productViewId = id("select id from sys_permission where code = 'product:view'");
        jdbc.update("insert into sys_role_permission values (?, ?, now(3))", productRoleId, productViewId);
        jdbc.update("insert into sys_user_role (user_id, role_id, assignment_source, assigned_at) values (?, ?, 'FEISHU', now(3))",
                userId, basicRoleId);
        jdbc.update("insert into sys_user_role (user_id, role_id, assignment_source, assigned_at) values (?, ?, 'LOCAL', now(3))",
                userId, productRoleId);

        assertThat(repository.findEnabledByUserId(userId))
                .extracting(Role::code)
                .containsExactlyInAnyOrder("SUPER_ADMIN", "BASIC_EMPLOYEE", "PRODUCT_OPERATOR");
        assertThat(repository.findEnabledByUserId(userId).stream()
                .filter(role -> role.code().equals("PRODUCT_OPERATOR"))
                .findFirst().orElseThrow().permissionCodes())
                .containsExactly("product:view");
    }

    @Test
    void exposesPersistedRoleSafetyFlagsAndCompletePermissionCatalog() {
        var superAdmin = repository.findEnabledByMemberKey("13800138000").stream()
                .filter(role -> role.code().equals("SUPER_ADMIN"))
                .findFirst().orElseThrow();

        assertThat(superAdmin.id()).isPositive();
        assertThat(superAdmin.system()).isTrue();
        assertThat(superAdmin.superAdministrator()).isTrue();
        assertThat(superAdmin.sensitive()).isTrue();
        assertThat(repository.findAllPermissions())
                .extracting("code")
                .contains("dashboard:view", "system:role:manage", "organization:view");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
