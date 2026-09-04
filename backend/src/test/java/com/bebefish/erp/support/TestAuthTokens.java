package com.bebefish.erp.support;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.TokenIssuer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/** Creates a real persisted account/role/session for controller integration tests. */
public final class TestAuthTokens {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestAuthTokens() {
    }

    public static String issue(
            JdbcTemplate jdbc,
            TokenIssuer tokens,
            String mobile,
            String... permissions
    ) {
        String suffix = mobile.replaceAll("\\D", "") + "_" + SEQUENCE.incrementAndGet();
        String actualMobile = mobile + "-" + suffix.substring(suffix.lastIndexOf('_') + 1);
        String employeeNo = "AUTH-" + suffix;
        String roleCode = "TEST_AUTH_" + suffix;
        jdbc.update("""
                insert ignore into employee
                    (employee_no, name, mobile, employment_type, status, source,
                     profile_complete, created_at, updated_at)
                values (?, '接口测试用户', ?, 'formal', 'active', 'manual', true, now(3), now(3))
                """, employeeNo, actualMobile);
        long employeeId = jdbc.queryForObject(
                "select id from employee where mobile = ?", Long.class, actualMobile);
        jdbc.update("""
                insert ignore into sys_user
                    (employee_id, mobile, password_hash, status, created_at, updated_at)
                values (?, ?, null, 'enabled', now(3), now(3))
                """, employeeId, actualMobile);
        long userId = jdbc.queryForObject(
                "select id from sys_user where employee_id = ?", Long.class, employeeId);
        jdbc.update("""
                insert ignore into sys_role
                    (code, name, description, kind, immutable, is_sensitive, status,
                     data_scope, updated_by, created_at, updated_at)
                values (?, '接口测试角色', '', 'custom', false, false, 'enabled',
                        'COMPANY', 'test', now(3), now(3))
                """, roleCode);
        long roleId = jdbc.queryForObject("select id from sys_role where code = ?", Long.class, roleCode);
        jdbc.update("delete from sys_role_permission where role_id = ?", roleId);
        for (String permission : permissions) {
            jdbc.update("""
                    insert into sys_role_permission (role_id, permission_id, assigned_at)
                    select ?, id, now(3) from sys_permission where code = ?
                    """, roleId, permission);
        }
        jdbc.update("""
                insert ignore into sys_user_role (user_id, role_id, assignment_source, assigned_at)
                values (?, ?, 'LOCAL', now(3))
                """, userId, roleId);
        String token = tokens.issue(new AuthenticatedUser(
                userId, employeeId, actualMobile, "接口测试用户", null,
                List.of(roleCode), List.of(permissions)
        ), "password").accessToken();
        var resolved = tokens.resolve(token);
        if (!resolved.permissions().containsAll(List.of(permissions))) {
            throw new IllegalStateException("Test session lost permissions: " + resolved.permissions());
        }
        return token;
    }

    public static String issueWithoutMobile(
            JdbcTemplate jdbc,
            TokenIssuer tokens,
            String... permissions
    ) {
        String suffix = "NO_MOBILE_" + System.nanoTime() + "_" + SEQUENCE.incrementAndGet();
        String employeeNo = "AUTH-" + suffix;
        String roleCode = "TEST_AUTH_" + suffix;
        jdbc.update("""
                insert into employee
                    (employee_no, name, mobile, employment_type, status, source,
                     profile_complete, created_at, updated_at)
                values (?, '无手机号接口测试用户', null, 'formal', 'active', 'feishu',
                        false, now(3), now(3))
                """, employeeNo);
        long employeeId = jdbc.queryForObject(
                "select id from employee where employee_no = ?", Long.class, employeeNo);
        jdbc.update("""
                insert into sys_user
                    (employee_id, mobile, password_hash, status, created_at, updated_at)
                values (?, null, null, 'enabled', now(3), now(3))
                """, employeeId);
        long userId = jdbc.queryForObject(
                "select id from sys_user where employee_id = ?", Long.class, employeeId);
        jdbc.update("""
                insert into sys_role
                    (code, name, description, kind, immutable, is_sensitive, status,
                     data_scope, updated_by, created_at, updated_at)
                values (?, '无手机号接口测试角色', '', 'custom', false, false, 'enabled',
                        'COMPANY', 'test', now(3), now(3))
                """, roleCode);
        long roleId = jdbc.queryForObject("select id from sys_role where code = ?", Long.class, roleCode);
        for (String permission : permissions) {
            jdbc.update("""
                    insert into sys_role_permission (role_id, permission_id, assigned_at)
                    select ?, id, now(3) from sys_permission where code = ?
                    """, roleId, permission);
        }
        jdbc.update("""
                insert into sys_user_role (user_id, role_id, assignment_source, assigned_at)
                values (?, ?, 'FEISHU', now(3))
                """, userId, roleId);
        return tokens.issue(new AuthenticatedUser(
                userId, employeeId, null, "无手机号接口测试用户", null,
                List.of(roleCode), List.of(permissions)
        ), "feishu").accessToken();
    }
}
