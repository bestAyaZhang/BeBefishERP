package com.bebefish.erp.authorization.application;

import com.bebefish.erp.authorization.api.PermissionDtos;
import com.bebefish.erp.common.api.BusinessException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionManagementService {
    private static final Pattern ROLE_CODE = Pattern.compile("[A-Z0-9_]+");
    private static final Set<String> SCOPES = Set.of(
            "company", "department-and-descendants", "department", "self"
    );

    private final JdbcTemplate jdbc;

    public PermissionManagementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PermissionDtos.PermissionPageContext context() {
        var departments = jdbc.query("""
                select id, department_name from department where status = 'enabled' order by sort_order, id
                """, (rs, rowNum) -> new PermissionDtos.DepartmentOption(
                rs.getLong("id"), rs.getString("department_name")
        ));
        var counts = jdbc.queryForMap("""
                select count(*) total,
                       sum(case when employment_type = 'formal' then 1 else 0 end) formal_count
                from employee where status = 'active'
                """);
        long total = ((Number) counts.get("total")).longValue();
        long formal = counts.get("formal_count") == null ? 0 : ((Number) counts.get("formal_count")).longValue();
        return new PermissionDtos.PermissionPageContext(
                departments,
                "当前组织有 " + total + " 名在职员工，其中正式员工 " + formal + " 名。"
        );
    }

    public List<PermissionDtos.RoleSummary> listRoles(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        return jdbc.query("""
                select r.id, r.code, r.name, r.kind, r.immutable, r.status, r.updated_by, r.updated_at,
                       count(distinct ur.user_id) member_count
                from sys_role r
                left join sys_user_role ur on ur.role_id = r.id
                where (? = '' or lower(r.code) like lower(?) or lower(r.name) like lower(?))
                group by r.id, r.code, r.name, r.kind, r.immutable, r.status, r.updated_by, r.updated_at
                order by r.id
                """, (rs, rowNum) -> new PermissionDtos.RoleSummary(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("kind"), rs.getBoolean("immutable"), rs.getString("status"),
                rs.getString("updated_by"), iso(rs.getTimestamp("updated_at")), rs.getInt("member_count")
        ), normalized, "%" + normalized + "%", "%" + normalized + "%");
    }

    public PermissionDtos.RoleResponse getRole(long id) {
        var rows = jdbc.query("select * from sys_role where id = ?", (rs, rowNum) -> new RoleRow(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"),
                rs.getString("kind"), rs.getBoolean("immutable"), rs.getBoolean("is_sensitive"),
                rs.getString("status"), rs.getString("data_scope"), rs.getString("updated_by"),
                rs.getTimestamp("updated_at")
        ), id);
        if (rows.isEmpty()) {
            throw error("ROLE_NOT_FOUND", HttpStatus.NOT_FOUND, "角色不存在");
        }
        RoleRow role = rows.get(0);
        var permissions = jdbc.queryForList("""
                select p.code from sys_role_permission rp
                join sys_permission p on p.id = rp.permission_id
                where rp.role_id = ? order by p.code
                """, String.class, id);
        var members = jdbc.queryForList("""
                select distinct u.employee_id from sys_user_role ur
                join sys_user u on u.id = ur.user_id
                where ur.role_id = ? order by u.employee_id
                """, Long.class, id);
        return toResponse(role, permissions, members);
    }

    @Transactional
    public PermissionDtos.RoleResponse createRole(PermissionDtos.CreateRoleRequest request) {
        String name = request.name().trim();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CODE.matcher(code).matches()) {
            throw error("ROLE_CODE_INVALID", HttpStatus.BAD_REQUEST, "角色编码只能包含大写字母、数字和下划线");
        }
        if (jdbc.queryForObject("select count(*) from sys_role where code = ?", Integer.class, code) > 0) {
            throw error("ROLE_CODE_DUPLICATE", HttpStatus.BAD_REQUEST, "角色编码已存在");
        }
        String scope = "SELF";
        List<String> permissions = List.of();
        if (request.copyFromRoleId() != null) {
            var source = getRole(request.copyFromRoleId());
            scope = dbScope(source.dataScope());
            permissions = source.permissionCodes();
        }
        var keys = new GeneratedKeyHolder();
        String finalScope = scope;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into sys_role
                        (code, name, description, kind, immutable, is_sensitive, status,
                         data_scope, updated_by, created_at, updated_at)
                    values (?, ?, ?, 'custom', false, false, 'enabled', ?, '当前用户', now(3), now(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, request.description() == null ? "" : request.description().trim());
            statement.setString(4, finalScope);
            return statement;
        }, keys);
        long roleId = keys.getKey().longValue();
        insertPermissions(roleId, permissions);
        return getRole(roleId);
    }

    @Transactional
    public PermissionDtos.RoleResponse updateRole(long id, PermissionDtos.UpdateRoleRequest request) {
        requireMutable(id);
        jdbc.update("""
                update sys_role set name = ?, description = ?, updated_by = '当前用户', updated_at = now(3)
                where id = ?
                """, request.name().trim(), request.description() == null ? "" : request.description().trim(), id);
        return getRole(id);
    }

    @Transactional
    public PermissionDtos.RoleResponse saveConfiguration(
            long id,
            PermissionDtos.SaveRoleConfigurationRequest request
    ) {
        requireMutable(id);
        if (!SCOPES.contains(request.dataScope())) {
            throw error("DATA_SCOPE_INVALID", HttpStatus.BAD_REQUEST, "数据范围无效");
        }
        var permissions = request.permissionCodes().stream().distinct().sorted().toList();
        validatePermissionDependencies(permissions);
        int known = permissions.isEmpty() ? 0 : countKnownPermissions(permissions);
        if (known != permissions.size()) {
            throw error("PERMISSION_NOT_FOUND", HttpStatus.BAD_REQUEST, "包含未知权限");
        }
        jdbc.update("delete from sys_role_permission where role_id = ?", id);
        insertPermissions(id, permissions);
        jdbc.update("""
                update sys_role set data_scope = ?, updated_by = '当前用户', updated_at = now(3) where id = ?
                """, dbScope(request.dataScope()), id);
        return getRole(id);
    }

    @Transactional
    public PermissionDtos.RoleResponse changeStatus(long id, String status) {
        requireMutable(id);
        if (!Set.of("enabled", "disabled").contains(status)) {
            throw error("ROLE_STATUS_INVALID", HttpStatus.BAD_REQUEST, "角色状态无效");
        }
        jdbc.update("""
                update sys_role set status = ?, updated_by = '当前用户', updated_at = now(3) where id = ?
                """, status, id);
        return getRole(id);
    }

    public PermissionDtos.RoleMemberPage listMembers(
            long roleId,
            int page,
            int size,
            String keyword,
            Long departmentId,
            boolean candidates
    ) {
        getRole(roleId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalized = keyword == null ? "" : keyword.trim();
        var params = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" where e.status = 'active' and ");
        where.append(candidates ? "not exists" : "exists").append(" (select 1 from sys_user_role target where target.user_id = u.id and target.role_id = ?)");
        params.add(roleId);
        if (!normalized.isBlank()) {
            where.append(" and (e.employee_no like ? or e.name like ? or coalesce(e.mobile, '') like ?)");
            params.add("%" + normalized + "%");
            params.add("%" + normalized + "%");
            params.add("%" + normalized + "%");
        }
        if (departmentId != null) {
            where.append(" and e.department_id = ?");
            params.add(departmentId);
        }
        String from = " from employee e join sys_user u on u.employee_id = e.id "
                + "left join department d on d.id = e.department_id "
                + "left join position p on p.id = e.position_id ";
        long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, params.toArray());
        var pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safePage - 1) * safeSize);
        var records = jdbc.query("""
                select e.id, e.employee_no, e.name, e.mobile, e.department_id,
                       d.department_name, p.position_name, e.employment_type, u.id user_id
                """ + from + where + " order by e.id limit ? offset ?", (rs, rowNum) -> member(
                roleId,
                rs.getLong("user_id"),
                rs.getLong("id"),
                rs.getString("employee_no"),
                rs.getString("name"),
                rs.getString("mobile"),
                rs.getLong("department_id"),
                rs.getString("department_name"),
                rs.getString("position_name"),
                rs.getString("employment_type")
        ), pageParams.toArray());
        return new PermissionDtos.RoleMemberPage(records, safePage, safeSize, total);
    }

    @Transactional
    public PermissionDtos.MemberMutationResult addMembers(long roleId, List<Long> employeeIds) {
        requireMutable(roleId);
        int added = 0;
        int skipped = 0;
        for (Long employeeId : new HashSet<>(employeeIds)) {
            var userIds = jdbc.queryForList("select id from sys_user where employee_id = ?", Long.class, employeeId);
            if (userIds.size() != 1 || hasSuperAdministrator(userIds.get(0))) {
                skipped++;
                continue;
            }
            int exists = jdbc.queryForObject("""
                    select count(*) from sys_user_role
                    where user_id = ? and role_id = ? and assignment_source = 'LOCAL'
                    """, Integer.class, userIds.get(0), roleId);
            if (exists > 0) {
                skipped++;
            } else {
                jdbc.update("""
                        insert into sys_user_role (user_id, role_id, assignment_source, assigned_at)
                        values (?, ?, 'LOCAL', now(3))
                        """, userIds.get(0), roleId);
                added++;
            }
        }
        touch(roleId);
        return new PermissionDtos.MemberMutationResult(added, skipped, getRole(roleId));
    }

    @Transactional
    public PermissionDtos.MemberRemovalResult removeMembers(long roleId, List<Long> employeeIds) {
        requireMutable(roleId);
        int removed = 0;
        int skipped = 0;
        for (Long employeeId : new HashSet<>(employeeIds)) {
            var userIds = jdbc.queryForList("select id from sys_user where employee_id = ?", Long.class, employeeId);
            if (userIds.size() != 1 || hasSuperAdministrator(userIds.get(0))) {
                skipped++;
                continue;
            }
            removed += jdbc.update("""
                    delete from sys_user_role
                    where user_id = ? and role_id = ? and assignment_source = 'LOCAL'
                    """, userIds.get(0), roleId);
        }
        touch(roleId);
        return new PermissionDtos.MemberRemovalResult(removed, skipped, getRole(roleId));
    }

    private PermissionDtos.RoleMember member(
            long currentRoleId,
            long userId,
            long employeeId,
            String employeeNo,
            String name,
            String mobile,
            long departmentId,
            String department,
            String position,
            String employmentType
    ) {
        var otherRoles = jdbc.queryForList("""
                select distinct r.name from sys_user_role ur join sys_role r on r.id = ur.role_id
                where ur.user_id = ? and r.id <> ? and r.status = 'enabled' order by r.name
                """, String.class, userId, currentRoleId);
        var scopes = jdbc.queryForList("""
                select distinct r.data_scope from sys_user_role ur join sys_role r on r.id = ur.role_id
                where ur.user_id = ? and r.status = 'enabled'
                """, String.class, userId);
        return new PermissionDtos.RoleMember(
                employeeId, employeeNo, name, mobile == null ? "" : mobile,
                departmentId, department == null ? "未分配" : department,
                position == null ? "未分配" : position, employmentType, otherRoles,
                widestScope(scopes), hasSuperAdministrator(userId) ? "超级管理员不可移除" : null
        );
    }

    private String widestScope(List<String> scopes) {
        return scopes.stream().max(java.util.Comparator.comparingInt(this::scopeRank))
                .map(this::apiScope).orElse(null);
    }

    private int scopeRank(String scope) {
        return switch (scope) {
            case "COMPANY" -> 3;
            case "DEPARTMENT_AND_DESCENDANTS" -> 2;
            case "DEPARTMENT" -> 1;
            default -> 0;
        };
    }

    private boolean hasSuperAdministrator(long userId) {
        return jdbc.queryForObject("""
                select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id
                where ur.user_id = ? and r.code = 'SUPER_ADMIN'
                """, Integer.class, userId) > 0;
    }

    private void validatePermissionDependencies(List<String> codes) {
        Set<String> selected = Set.copyOf(codes);
        for (String code : codes) {
            int separator = code.lastIndexOf(':');
            if (separator > 0 && !code.endsWith(":view")
                    && !selected.contains(code.substring(0, separator) + ":view")) {
                throw error(
                        "PERMISSION_DEPENDENCY_VIOLATION",
                        HttpStatus.BAD_REQUEST,
                        "选择操作权限时必须同时选择查看权限"
                );
            }
        }
    }

    private int countKnownPermissions(List<String> permissions) {
        String placeholders = String.join(",", java.util.Collections.nCopies(permissions.size(), "?"));
        return jdbc.queryForObject(
                "select count(*) from sys_permission where code in (" + placeholders + ")",
                Integer.class,
                permissions.toArray()
        );
    }

    private void insertPermissions(long roleId, List<String> permissions) {
        for (String permission : permissions) {
            jdbc.update("""
                    insert into sys_role_permission (role_id, permission_id, assigned_at)
                    select ?, id, now(3) from sys_permission where code = ?
                    """, roleId, permission);
        }
    }

    private void requireMutable(long id) {
        var role = getRole(id);
        if (role.immutable()) {
            throw error("SYSTEM_ROLE_IMMUTABLE", HttpStatus.BAD_REQUEST, "系统角色不可修改");
        }
    }

    private void touch(long roleId) {
        jdbc.update("update sys_role set updated_by = '当前用户', updated_at = now(3) where id = ?", roleId);
    }

    private PermissionDtos.RoleResponse toResponse(RoleRow role, List<String> permissions, List<Long> members) {
        return new PermissionDtos.RoleResponse(
                role.id(), role.code(), role.name(), role.description(), role.kind(), role.immutable(),
                role.sensitive(), role.status(), apiScope(role.dataScope()), permissions, members,
                role.updatedBy(), iso(role.updatedAt())
        );
    }

    private String apiScope(String dbScope) {
        return dbScope.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String dbScope(String apiScope) {
        return apiScope.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String iso(Timestamp timestamp) {
        return timestamp.toInstant().toString();
    }

    private BusinessException error(String code, HttpStatus status, String message) {
        return new BusinessException(code, status, message);
    }

    private record RoleRow(
            long id,
            String code,
            String name,
            String description,
            String kind,
            boolean immutable,
            boolean sensitive,
            String status,
            String dataScope,
            String updatedBy,
            Timestamp updatedAt
    ) {
    }
}
