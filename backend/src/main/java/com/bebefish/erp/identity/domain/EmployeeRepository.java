package com.bebefish.erp.identity.domain;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository {
    Optional<Employee> findById(long id);

    Optional<Employee> findByIdForUpdate(long id);

    List<Employee> findByMobile(String mobile);

    Optional<Long> findDepartmentIdByFeishuId(String feishuDepartmentId);

    Optional<Employee> findByEmployeeNo(String employeeNo);

    Employee save(Employee employee);
}
