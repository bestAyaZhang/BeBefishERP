package com.bebefish.erp.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.auth.domain.EmployeeStatus;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JpaUserAccountRepositoryTest {
    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsAccountWithEmployeeLoginPolicy() {
        var account = repository.findByMobile("13800138000").orElseThrow();

        assertThat(account.id()).isPositive();
        assertThat(account.employeeId()).isPositive();
        assertThat(account.employmentType()).isEqualTo(EmploymentType.TEMPORARY);
        assertThat(account.userStatus()).isEqualTo(com.bebefish.erp.auth.domain.UserStatus.ENABLED);
        assertThat(account.employeeStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(account.displayName()).isEqualTo("本地系统管理员");
        assertThat(account.passwordHash()).isNotBlank();
    }

    @Test
    void savesAnAccountForAnExistingEmployeeAndRecordsLogin() {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into employee
                        (employee_no, name, mobile, employment_type, status, source,
                         profile_complete, created_at, updated_at)
                    values ('TMP-001', '临时员工', '13900000001', 'temporary', 'active', 'manual',
                            true, now(3), now(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            return statement;
        }, keyHolder);
        long employeeId = keyHolder.getKey().longValue();

        var saved = repository.save(new UserAccount(
                0, employeeId, "13900000001", "encoded-password",
                EmploymentType.TEMPORARY,
                com.bebefish.erp.auth.domain.UserStatus.ENABLED,
                EmployeeStatus.ACTIVE,
                "临时员工", null
        ));
        repository.recordLogin(saved.id(), "password");

        assertThat(saved.id()).isPositive();
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(jdbc.queryForObject(
                "select last_login_method from sys_user where id = ?",
                String.class,
                saved.id()
        )).isEqualTo("password");
        assertThat(jdbc.queryForObject(
                "select last_login_at is not null from sys_user where id = ?",
                Boolean.class,
                saved.id()
        )).isTrue();
    }
}
