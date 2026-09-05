package com.bebefish.erp.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.identity.domain.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JdbcEmployeeRepositoryTest {
    private static final String EMPLOYEE_NO = "MOBILE-NORMALIZATION";

    @Autowired private EmployeeRepository employees;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanFixture() {
        jdbc.update("delete from employee where employee_no = ?", EMPLOYEE_NO);
    }

    @Test
    void findsMobileAfterCountryPrefixAndPunctuationNormalization() {
        jdbc.update(
                """
insert into employee
    (employee_no, name, mobile, employment_type, status, source,
     profile_complete, status_source, created_at, updated_at)
values (?, '兼容性测试员工', '+86 139-0000(7771)', 'formal', 'active',
        'manual', false, 'manual', now(3), now(3))
""",
                EMPLOYEE_NO);

        assertThat(employees.findByMobile("13900007771"))
                .singleElement()
                .extracting(employee -> employee.employeeNo())
                .isEqualTo(EMPLOYEE_NO);
    }
}
