package com.bebefish.erp.organization.domain;

import com.bebefish.erp.organization.api.OrganizationDtos.*;

import java.util.*;

public interface OrganizationRepository {
    Page<Department> departments(Query query);

    List<Department> allDepartments();

    Page<Position> positions(Query query);

    List<Position> allPositions();

    Page<Employee> employees(Query query);

    List<Employee> allEmployees();

    Department department(long id);

    Position position(long id);

    Employee employee(long id);

    Summary summary();

    Map<Long, Long> employeeCounts();

    long saveDepartment(Long id, SaveDepartment request);

    long savePosition(Long id, SavePosition request);

    long saveEmployee(Long id, SaveEmployee request, String passwordHash);

    void employeeStatus(long id, String status);

    void departmentStatus(long id, String status);

    void positionStatus(long id, String status);

    boolean positionOccupied(long id);

    boolean descendant(long root, long candidate);
}
