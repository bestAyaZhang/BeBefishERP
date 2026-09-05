package com.bebefish.erp.organization.infrastructure;

import com.bebefish.erp.organization.api.OrganizationDtos.*;
import com.bebefish.erp.organization.domain.OrganizationRepository;

import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String DEPARTMENT_SUBTREE =
            "with recursive subtree as (select id from department where id=? union all select d.id"
                    + " from department d join subtree s on d.parent_id=s.id) ";
    private static final String DEPARTMENT_SELECT =
            """
select d.*, coalesce(m.name,'') manager_name,
    (select count(*) from employee e where e.department_id=d.id) employee_count,
    (select count(*) from department c where c.parent_id=d.id) child_count,
    (exists(select 1 from employee e where e.department_id=d.id and e.status='active')
     or exists(select 1 from department c where c.parent_id=d.id and c.status='enabled')
     or exists(select 1 from position p where p.department_id=d.id and p.status='enabled')) occupied
from department d left join employee m on m.id=d.manager_employee_id
""";
    private static final String POSITION_SELECT =
            """
            select p.*, (select count(*) from employee e where e.position_id=p.id) employee_count
            from position p
            """;
    private static final String EMPLOYEE_SELECT =
            "select e.*,u.password_hash,f.id identity_id,coalesce(f.display_name,'')"
                + " feishu_display_name from employee e left join sys_user u on u.employee_id=e.id"
                + " left join sys_feishu_identity f on f.user_id=u.id";
    private final RowMapper<Department> departmentMapper =
            (row, rowNumber) ->
                    new Department(
                            row.getLong("id"),
                            row.getString("department_code"),
                            row.getString("department_name"),
                            nullable(row, "parent_id"),
                            nullable(row, "manager_employee_id"),
                            row.getString("manager_name"),
                            row.getInt("sort_order"),
                            row.getString("status"),
                            row.getLong("employee_count"),
                            row.getLong("child_count"),
                            "enabled".equals(row.getString("status"))
                                    && row.getBoolean("occupied"));
    private final RowMapper<Position> positionMapper =
            (row, rowNumber) ->
                    new Position(
                            row.getLong("id"),
                            row.getString("position_code"),
                            row.getString("position_name"),
                            nullable(row, "department_id"),
                            row.getString("responsibilities"),
                            row.getString("status"),
                            row.getLong("employee_count"));
    private final RowMapper<Employee> employeeMapper =
            (row, rowNumber) ->
                    new Employee(
                            row.getLong("id"),
                            row.getString("employee_no"),
                            row.getString("name"),
                            Objects.toString(row.getString("mobile"), ""),
                            nullable(row, "department_id"),
                            nullable(row, "position_id"),
                            row.getString("employment_type"),
                            row.getString("status"),
                            row.getDate("hire_date") == null
                                    ? null
                                    : row.getDate("hire_date").toLocalDate(),
                            nullable(row, "identity_id") != null
                                    ? "bound"
                                    : ("formal".equals(row.getString("employment_type"))
                                            ? "pending"
                                            : "unbound"),
                            row.getString("feishu_display_name"),
                            row.getString("password_hash") != null,
                            row.getString("feishu_job_title"));

    private static Long nullable(ResultSet row, String key) throws SQLException {
        return row.getObject(key) == null ? null : row.getLong(key);
    }

    private <T> Page<T> page(
            Query query, String select, String alias, String names, RowMapper<T> mapper) {
        List<Object> parameters = new ArrayList<>();
        String prefix = "";
        StringBuilder where = new StringBuilder(" where 1=1");
        if (query.departmentId() != null) {
            prefix = DEPARTMENT_SUBTREE;
            parameters.add(query.departmentId());
            where.append(" and ")
                    .append(alias)
                    .append(".department_id in (select id from subtree)");
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" and (");
            String[] fields = names.split(",");
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) where.append(" or ");
                where.append(alias).append('.').append(fields[i]).append(" like ?");
                parameters.add("%" + query.keyword().trim() + "%");
            }
            where.append(')');
        }
        if (query.status() != null && !query.status().isBlank()) {
            where.append(" and ").append(alias).append(".status=?");
            parameters.add(query.status());
        }
        if (query.employmentType() != null && !query.employmentType().isBlank()) {
            where.append(" and e.employment_type=?");
            parameters.add(query.employmentType());
        }
        long count =
                jdbc.queryForObject(
                        prefix + "select count(*) from (" + select + where + ") counted",
                        Long.class,
                        parameters.toArray());
        parameters.add(query.size());
        parameters.add((long) (query.page() - 1) * query.size());
        String order = alias.equals("d") ? "d.sort_order,d.id" : alias + ".id";
        return new Page<>(
                jdbc.query(
                        prefix + select + where + " order by " + order + " limit ? offset ?",
                        mapper,
                        parameters.toArray()),
                query.page(),
                query.size(),
                count);
    }

    public Page<Department> departments(Query query) {
        return page(
                query, DEPARTMENT_SELECT, "d", "department_code,department_name", departmentMapper);
    }

    public Page<Position> positions(Query query) {
        return page(query, POSITION_SELECT, "p", "position_code,position_name", positionMapper);
    }

    public Page<Employee> employees(Query query) {
        return page(query, EMPLOYEE_SELECT, "e", "employee_no,name,mobile", employeeMapper);
    }

    public List<Department> allDepartments() {
        return jdbc.query(DEPARTMENT_SELECT + " order by d.sort_order,d.id", departmentMapper);
    }

    public List<Position> allPositions() {
        return jdbc.query(POSITION_SELECT + " order by p.id", positionMapper);
    }

    public List<Employee> allEmployees() {
        return jdbc.query(EMPLOYEE_SELECT + " order by e.id", employeeMapper);
    }

    public Department department(long id) {
        return jdbc.query(DEPARTMENT_SELECT + " where d.id=?", departmentMapper, id).stream()
                .findFirst()
                .orElse(null);
    }

    public Position position(long id) {
        return jdbc.query(POSITION_SELECT + " where p.id=?", positionMapper, id).stream()
                .findFirst()
                .orElse(null);
    }

    public Employee employee(long id) {
        return jdbc.query(EMPLOYEE_SELECT + " where e.id=?", employeeMapper, id).stream()
                .findFirst()
                .orElse(null);
    }

    public Summary summary() {
        return jdbc.queryForObject(
                "select sum(e.employment_type='formal')"
                    + " formal_count,sum(e.employment_type='temporary')"
                    + " temporary_count,sum(e.employment_type='formal' and f.id is null)"
                    + " pending_count,sum(e.status='disabled') disabled_count from employee e left"
                    + " join sys_user u on u.employee_id=e.id left join sys_feishu_identity f on"
                    + " f.user_id=u.id",
                (row, rowNumber) ->
                        new Summary(
                                row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)));
    }

    public Map<Long, Long> employeeCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        jdbc.query(
                "select department_id,count(*) from employee where department_id is not null and"
                        + " status='active' group by department_id",
                (RowCallbackHandler) row -> counts.put(row.getLong(1), row.getLong(2)));
        return counts;
    }

    private long insert(String sql, Object... parameters) {
        var key = new GeneratedKeyHolder();
        jdbc.update(
                c -> {
                    var request = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < parameters.length; i++)
                        request.setObject(i + 1, parameters[i]);
                    return request;
                },
                key);
        return key.getKey().longValue();
    }

    public long saveDepartment(Long id, SaveDepartment request) {
        if (id == null)
            return insert(
                    "insert into"
                        + " department(department_code,department_name,parent_id,manager_employee_id,sort_order,status,created_at,updated_at)"
                        + " values(?,?,?,?,?,?,now(3),now(3))",
                    request.departmentCode(),
                    request.departmentName(),
                    request.parentId(),
                    request.managerEmployeeId(),
                    request.sortOrder(),
                    request.status());
        jdbc.update(
                "update department set"
                    + " department_code=?,department_name=?,parent_id=?,manager_employee_id=?,sort_order=?,status=?,updated_at=now(3)"
                    + " where id=?",
                request.departmentCode(),
                request.departmentName(),
                request.parentId(),
                request.managerEmployeeId(),
                request.sortOrder(),
                request.status(),
                id);
        return id;
    }

    public long savePosition(Long id, SavePosition request) {
        if (id == null)
            return insert(
                    "insert into position"
                        + " (position_code,position_name,department_id,responsibilities,status,created_at,updated_at)"
                        + " values(?,?,?,?,?,now(3),now(3))",
                    request.positionCode(),
                    request.positionName(),
                    request.departmentId(),
                    request.responsibilities() == null ? "" : request.responsibilities(),
                    request.status());
        jdbc.update(
                "update position set"
                    + " position_code=?,position_name=?,department_id=?,responsibilities=?,status=?,updated_at=now(3)"
                    + " where id=?",
                request.positionCode(),
                request.positionName(),
                request.departmentId(),
                request.responsibilities() == null ? "" : request.responsibilities(),
                request.status(),
                id);
        return id;
    }

    public long saveEmployee(Long id, SaveEmployee request, String hash) {
        String mobile =
                request.mobile() == null || request.mobile().isBlank()
                        ? null
                        : request.mobile().trim();
        if (id == null) {
            id =
                    insert(
                            "insert into"
                                + " employee(employee_no,name,mobile,department_id,position_id,employment_type,status,source,hire_date,status_source,created_at,updated_at)"
                                + " values(?,?,?,?,?,?,?,'manual',?,'manual',now(3),now(3))",
                            request.employeeNo(),
                            request.employeeName(),
                            mobile,
                            request.departmentId(),
                            request.positionId(),
                            request.employmentType(),
                            request.status(),
                            request.hireDate());
            insert(
                    "insert into"
                        + " sys_user(employee_id,mobile,password_hash,status,created_at,updated_at)"
                        + " values(?,?,?,?,now(3),now(3))",
                    id,
                    mobile,
                    hash,
                    "active".equals(request.status()) ? "enabled" : "disabled");
        } else {
            jdbc.update(
                    "update employee set"
                        + " employee_no=?,name=?,mobile=?,department_id=?,position_id=?,employment_type=?,status_source=if(status<>?,'manual',status_source),status=?,hire_date=?,updated_at=now(3)"
                        + " where id=?",
                    request.employeeNo(),
                    request.employeeName(),
                    mobile,
                    request.departmentId(),
                    request.positionId(),
                    request.employmentType(),
                    request.status(),
                    request.status(),
                    request.hireDate(),
                    id);
            jdbc.update(
                    "update sys_user set mobile=?,status=?,updated_at=now(3) where employee_id=?",
                    mobile,
                    "active".equals(request.status()) ? "enabled" : "disabled",
                    id);
            if (hash != null)
                jdbc.update("update sys_user set password_hash=? where employee_id=?", hash, id);
        }
        return id;
    }

    public void employeeStatus(long id, String status) {
        jdbc.update(
                "update employee set status=?,status_source='manual',updated_at=now(3) where id=?",
                status,
                id);
        jdbc.update(
                "update sys_user set status=?,updated_at=now(3) where employee_id=?",
                "active".equals(status) ? "enabled" : "disabled",
                id);
    }

    public void departmentStatus(long id, String status) {
        jdbc.update("update department set status=?,updated_at=now(3) where id=?", status, id);
    }

    public void positionStatus(long id, String status) {
        jdbc.update("update position set status=?,updated_at=now(3) where id=?", status, id);
    }

    public boolean positionOccupied(long id) {
        return jdbc.queryForObject(
                        "select count(*) from employee where position_id=? and status='active'",
                        Long.class,
                        id)
                > 0;
    }

    public boolean descendant(long root, long candidate) {
        return jdbc.queryForObject(
                        DEPARTMENT_SUBTREE + "select count(*) from subtree where id=?",
                        Long.class,
                        root,
                        candidate)
                > 0;
    }
}
