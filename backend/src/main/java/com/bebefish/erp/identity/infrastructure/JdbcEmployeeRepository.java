package com.bebefish.erp.identity.infrastructure;

import com.bebefish.erp.auth.domain.EmployeeStatus;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.identity.domain.Employee;
import com.bebefish.erp.identity.domain.EmployeeRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmployeeRepository implements EmployeeRepository {
    private final JdbcTemplate jdbc;

    public JdbcEmployeeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Employee> findById(long id) {
        return jdbc.query("select * from employee where id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public List<Employee> findByMobile(String mobile) {
        return jdbc.query("select * from employee where mobile = ?", this::map, mobile);
    }

    @Override
    public Optional<Long> findDepartmentIdByFeishuId(String feishuDepartmentId) {
        if (feishuDepartmentId == null || feishuDepartmentId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.queryForList(
                "select id from department where feishu_department_id = ? and status = 'enabled'",
                Long.class,
                feishuDepartmentId
        ).stream().findFirst();
    }

    @Override
    public Employee save(Employee employee) {
        if (employee.id() > 0) {
            jdbc.update("""
                    update employee set employee_no = ?, name = ?, mobile = ?, avatar_url = ?,
                        department_id = ?, position_id = ?, employment_type = ?, status = ?,
                        source = ?, profile_complete = ?, updated_at = now(3)
                    where id = ?
                    """,
                    employee.employeeNo(), employee.name(), employee.mobile(), employee.avatarUrl(),
                    employee.departmentId(), employee.positionId(), db(employee.employmentType()),
                    db(employee.status()), employee.source(), employee.profileComplete(), employee.id()
            );
            return employee;
        }
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into employee
                        (employee_no, name, mobile, avatar_url, department_id, position_id,
                         employment_type, status, source, profile_complete, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(3), now(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, employee.employeeNo());
            statement.setString(2, employee.name());
            statement.setString(3, employee.mobile());
            statement.setString(4, employee.avatarUrl());
            if (employee.departmentId() == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, employee.departmentId());
            if (employee.positionId() == null) statement.setNull(6, java.sql.Types.BIGINT);
            else statement.setLong(6, employee.positionId());
            statement.setString(7, db(employee.employmentType()));
            statement.setString(8, db(employee.status()));
            statement.setString(9, employee.source());
            statement.setBoolean(10, employee.profileComplete());
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        return new Employee(
                id, employee.employeeNo(), employee.name(), employee.mobile(), employee.avatarUrl(),
                employee.departmentId(), employee.positionId(), employee.employmentType(), employee.status(),
                employee.source(), employee.profileComplete()
        );
    }

    private Employee map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Employee(
                rs.getLong("id"),
                rs.getString("employee_no"),
                rs.getString("name"),
                rs.getString("mobile"),
                rs.getString("avatar_url"),
                nullableLong(rs, "department_id"),
                nullableLong(rs, "position_id"),
                EmploymentType.valueOf(rs.getString("employment_type").toUpperCase(Locale.ROOT)),
                EmployeeStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                rs.getString("source"),
                rs.getBoolean("profile_complete")
        );
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String db(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
