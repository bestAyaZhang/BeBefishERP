package com.bebefish.erp.organization.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.organization.api.OrganizationDtos.*;
import com.bebefish.erp.organization.application.OrganizationService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/organization")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Page<Department>> departments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                service.listDepartments(new Query(page, size, keyword, null, null, status)));
    }

    @GetMapping("/departments/all")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<List<Department>> allDepartments() {
        return ApiResponse.success(service.allDepartments());
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('organization:create')")
    public ApiResponse<Department> createDepartment(@Valid @RequestBody SaveDepartment request) {
        return ApiResponse.success(service.saveDepartment(null, request));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Department> updateDepartment(
            @PathVariable long id, @Valid @RequestBody SaveDepartment request) {
        return ApiResponse.success(service.saveDepartment(id, request));
    }

    @PatchMapping("/departments/{id}/status")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Department> statusDepartment(
            @PathVariable long id, @Valid @RequestBody Status request) {
        return ApiResponse.success(service.departmentStatus(id, request.status()));
    }

    @GetMapping("/positions")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Page<Position>> positions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                service.listPositions(new Query(page, size, keyword, departmentId, null, status)));
    }

    @GetMapping("/positions/all")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<List<Position>> allPositions() {
        return ApiResponse.success(service.allPositions());
    }

    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('organization:create')")
    public ApiResponse<Position> createPosition(@Valid @RequestBody SavePosition request) {
        return ApiResponse.success(service.savePosition(null, request));
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Position> updatePosition(
            @PathVariable long id, @Valid @RequestBody SavePosition request) {
        return ApiResponse.success(service.savePosition(id, request));
    }

    @PatchMapping("/positions/{id}/status")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Position> statusPosition(
            @PathVariable long id, @Valid @RequestBody Status request) {
        return ApiResponse.success(service.positionStatus(id, request.status()));
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Page<Employee>> employees(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                service.listEmployees(
                        new Query(page, size, keyword, departmentId, employmentType, status)));
    }

    @GetMapping("/employees/all")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<List<Employee>> allEmployees() {
        return ApiResponse.success(service.allEmployees());
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('organization:create')")
    public ApiResponse<Employee> createEmployee(@Valid @RequestBody SaveEmployee request) {
        return ApiResponse.success(service.saveEmployee(null, request));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Employee> updateEmployee(
            @PathVariable long id, @Valid @RequestBody SaveEmployee request) {
        return ApiResponse.success(service.saveEmployee(id, request));
    }

    @PatchMapping("/employees/{id}/status")
    @PreAuthorize("hasAuthority('organization:edit')")
    public ApiResponse<Employee> statusEmployee(
            @PathVariable long id, @Valid @RequestBody Status request) {
        return ApiResponse.success(service.employeeStatus(id, request.status()));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Employee> employee(@PathVariable long id) {
        return ApiResponse.success(service.employee(id));
    }

    @GetMapping("/employees/summary")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Summary> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/departments/employee-counts")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<Map<Long, Long>> counts() {
        return ApiResponse.success(service.employeeCounts());
    }
}
