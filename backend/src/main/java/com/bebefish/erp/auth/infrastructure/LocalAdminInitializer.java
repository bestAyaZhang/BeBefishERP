package com.bebefish.erp.auth.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod & (local | test)")
@ConditionalOnProperty(prefix = "erp.auth.local-admin", name = "enabled", havingValue = "true")
public class LocalAdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String mobile;
    private final String password;

    public LocalAdminInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${erp.auth.local-admin.mobile:}") String mobile,
            @Value("${erp.auth.local-admin.password:}") String password
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.mobile = mobile == null ? "" : mobile.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mobile.isBlank() || password.isBlank()) {
            throw new IllegalStateException("启用本地管理员时必须配置手机号和密码");
        }
        jdbc.update("""
                insert into employee
                    (employee_no, name, mobile, employment_type, status, source,
                     profile_complete, created_at, updated_at)
                values ('SYS-LOCAL-ADMIN', '本地系统管理员', ?, 'temporary', 'active', 'manual',
                        true, now(3), now(3))
                on duplicate key update name = values(name), mobile = values(mobile),
                    employment_type = 'temporary', status = 'active', source = 'manual',
                    profile_complete = true, updated_at = now(3)
                """, mobile);
        long employeeId = jdbc.queryForObject(
                "select id from employee where employee_no = 'SYS-LOCAL-ADMIN'",
                Long.class
        );
        jdbc.update("""
                insert into sys_user
                    (employee_id, mobile, password_hash, status, created_at, updated_at)
                values (?, ?, ?, 'enabled', now(3), now(3))
                on duplicate key update mobile = values(mobile), password_hash = values(password_hash),
                    status = 'enabled', updated_at = now(3)
                """, employeeId, mobile, passwordEncoder.encode(password));
        jdbc.update("""
                insert ignore into sys_user_role (user_id, role_id, assignment_source, assigned_at)
                select u.id, r.id, 'LOCAL', now(3)
                from sys_user u join sys_role r on r.code = 'SUPER_ADMIN'
                where u.employee_id = ?
                """, employeeId);
    }
}
