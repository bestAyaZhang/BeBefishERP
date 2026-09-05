package com.bebefish.erp.organization.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.organization.api.OrganizationDtos.*;
import com.bebefish.erp.organization.domain.OrganizationRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;

@Service
@Transactional(readOnly = true)
public class OrganizationService {
    private final OrganizationRepository repository;
    private final PasswordEncoder passwords;

    public OrganizationService(OrganizationRepository repository, PasswordEncoder passwords) {
        this.repository = repository;
        this.passwords = passwords;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }

    private static void status(String status, boolean employee) {
        if (status == null
                || !(employee
                                ? Set.of("active", "disabled", "resigned")
                                : Set.of("enabled", "disabled"))
                        .contains(status)) throw invalid("状态无效");
    }

    private static void validateQuery(Query query, boolean employee) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 500) throw invalid("分页范围无效");
        if (query.status() != null && !query.status().isBlank()) status(query.status(), employee);
        if (query.employmentType() != null
                && !query.employmentType().isBlank()
                && !Set.of("formal", "temporary").contains(query.employmentType()))
            throw invalid("员工类型无效");
    }

    private static <T> T found(T value) {
        if (value == null)
            throw new BusinessException("ORGANIZATION_NOT_FOUND", HttpStatus.NOT_FOUND, "组织记录不存在");
        return value;
    }

    private static <T> T unique(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("ORGANIZATION_DUPLICATE", HttpStatus.CONFLICT, "编码或手机号已存在");
        }
    }

    private static void occupied(boolean value) {
        if (value)
            throw new BusinessException(
                    "ORGANIZATION_IN_USE", HttpStatus.CONFLICT, "存在在职员工或启用中的子部门，不能停用");
    }

    public Page<Department> listDepartments(Query query) {
        validateQuery(query, false);
        return repository.departments(query);
    }

    public Page<Position> listPositions(Query query) {
        validateQuery(query, false);
        return repository.positions(query);
    }

    public Page<Employee> listEmployees(Query query) {
        validateQuery(query, true);
        return repository.employees(query);
    }

    public List<Department> allDepartments() {
        return repository.allDepartments();
    }

    public List<Position> allPositions() {
        return repository.allPositions();
    }

    public List<Employee> allEmployees() {
        return repository.allEmployees();
    }

    public Employee employee(long id) {
        return found(repository.employee(id));
    }

    public Summary summary() {
        return repository.summary();
    }

    public Map<Long, Long> employeeCounts() {
        return repository.employeeCounts();
    }

    private void enabledDepartment(Long id) {
        if (id != null && !"enabled".equals(found(repository.department(id)).status()))
            throw invalid("部门已停用");
    }

    private void enabledPosition(Long id, Long departmentId) {
        if (id == null) return;
        var request = found(repository.position(id));
        if (!"enabled".equals(request.status())) throw invalid("岗位已停用");
        if (request.departmentId() != null && !Objects.equals(request.departmentId(), departmentId))
            throw invalid("岗位不属于所选部门");
    }

    @Transactional
    public Department saveDepartment(Long id, SaveDepartment request) {
        status(request.status(), false);
        if (request.sortOrder() < 0) throw invalid("排序号不能小于 0");
        if (id != null) {
            var existing = found(repository.department(id));
            if ("disabled".equals(request.status())) occupied(existing.statusActionDisabled());
        }
        enabledDepartment(request.parentId());
        if (id != null
                && request.parentId() != null
                && repository.descendant(id, request.parentId())) throw invalid("不能循环引用部门");
        if (request.managerEmployeeId() != null
                && !"active".equals(employee(request.managerEmployeeId()).status()))
            throw invalid("负责人必须是在职员工");
        return unique(() -> repository.department(repository.saveDepartment(id, request)));
    }

    @Transactional
    public Position savePosition(Long id, SavePosition request) {
        status(request.status(), false);
        enabledDepartment(request.departmentId());
        if (id != null) {
            var existing = found(repository.position(id));
            if ("disabled".equals(request.status())) occupied(repository.positionOccupied(id));
        }
        return unique(() -> repository.position(repository.savePosition(id, request)));
    }

    @Transactional
    public Employee saveEmployee(Long id, SaveEmployee request) {
        status(request.status(), true);
        if (!Set.of("formal", "temporary").contains(request.employmentType()))
            throw invalid("员工类型无效");
        if (id != null) employee(id);
        enabledDepartment(request.departmentId());
        enabledPosition(request.positionId(), request.departmentId());
        if (id == null && request.passwordLoginEnabled() && request.password() == null)
            throw invalid("启用密码登录需要设置密码");
        return unique(
                () ->
                        repository.employee(
                                repository.saveEmployee(
                                        id,
                                        request,
                                        request.password() == null
                                                ? null
                                                : passwords.encode(request.password()))));
    }

    @Transactional
    public Department departmentStatus(long id, String status) {
        status(status, false);
        var department = found(repository.department(id));
        if ("disabled".equals(status)) occupied(department.statusActionDisabled());
        if ("enabled".equals(status)) enabledDepartment(department.parentId());
        repository.departmentStatus(id, status);
        return repository.department(id);
    }

    @Transactional
    public Position positionStatus(long id, String status) {
        status(status, false);
        var request = found(repository.position(id));
        if ("disabled".equals(status)) occupied(repository.positionOccupied(id));
        if ("enabled".equals(status)) enabledDepartment(request.departmentId());
        repository.positionStatus(id, status);
        return repository.position(id);
    }

    @Transactional
    public Employee employeeStatus(long id, String status) {
        status(status, true);
        employee(id);
        repository.employeeStatus(id, status);
        return repository.employee(id);
    }
}
