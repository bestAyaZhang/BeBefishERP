package com.bebefish.erp.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.Role;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcRoleRepositorySingleConnectionTest {
    private HikariDataSource dataSource;
    private JdbcRoleRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:role-single-connection-" + UUID.randomUUID());
        dataSource.setMaximumPoolSize(1);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(250);
        var jdbc = new JdbcTemplate(dataSource);
        for (String sql : List.of(
                "create table sys_role (id bigint primary key, code varchar(50), name varchar(50), kind varchar(20), is_sensitive boolean, status varchar(20), data_scope varchar(40))",
                "create table sys_user (id bigint primary key, mobile varchar(20))",
                "create table sys_user_role (user_id bigint, role_id bigint, assignment_source varchar(20))",
                "create table sys_permission (id bigint primary key, code varchar(50))",
                "create table sys_role_permission (role_id bigint, permission_id bigint)",
                "insert into sys_role values (1, 'SUPER_ADMIN', '管理员', 'system', true, 'enabled', 'COMPANY'), (2, 'BASIC_EMPLOYEE', '员工', 'system', false, 'enabled', 'SELF'), (3, 'DISABLED_ROLE', '禁用角色', 'custom', false, 'disabled', 'DEPARTMENT')",
                "insert into sys_user values (10, '19900002222')",
                "insert into sys_user_role values (10, 1, 'LOCAL'), (10, 2, 'LOCAL'), (10, 2, 'FEISHU'), (10, 3, 'LOCAL')",
                "insert into sys_permission values (1, 'shipping:view'), (2, 'logistics:view'), (3, 'system:role:manage')",
                "insert into sys_role_permission values (1, 3), (1, 1), (2, 2)")) {
            jdbc.execute(sql);
        }
        repository = new JdbcRoleRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void loadsUserRolesAndPermissionsWithOnlyOneAvailableConnection() {
        var roles = assertDoesNotThrow(() -> repository.findEnabledByUserId(10),
                "Loading role permissions must not require a second JDBC connection");

        assertThat(roles).containsExactly(
                new Role(2, "BASIC_EMPLOYEE", "员工", true, false, false, true, DataScope.SELF,
                        Set.of("logistics:view"), Set.of()),
                new Role(1, "SUPER_ADMIN", "管理员", true, true, true, true, DataScope.COMPANY,
                        Set.of("shipping:view", "system:role:manage"), Set.of()));
    }

    @Test
    void loadsMemberRolesAndPermissionsWithOnlyOneAvailableConnection() {
        var roles = assertDoesNotThrow(() -> repository.findEnabledByMemberKey("19900002222"),
                "Loading member role permissions must not require a second JDBC connection");

        assertThat(roles).containsExactly(
                new Role(2, "BASIC_EMPLOYEE", "员工", true, false, false, true, DataScope.SELF,
                        Set.of("logistics:view"), Set.of("19900002222")),
                new Role(1, "SUPER_ADMIN", "管理员", true, true, true, true, DataScope.COMPANY,
                        Set.of("shipping:view", "system:role:manage"), Set.of("19900002222")));
    }
}
