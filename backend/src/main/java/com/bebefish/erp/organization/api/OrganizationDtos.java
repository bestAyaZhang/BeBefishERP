package com.bebefish.erp.organization.api;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class OrganizationDtos {
    private OrganizationDtos() {}

    public record Page<T>(List<T> records, int page, int pageSize, long total) {}

    public record Query(
            int page,
            int size,
            String keyword,
            Long departmentId,
            String employmentType,
            String status) {}

    public record Department(
            long id,
            String departmentCode,
            String departmentName,
            Long parentId,
            Long managerEmployeeId,
            String managerName,
            int sortOrder,
            String status,
            long employeeCount,
            long childCount,
            boolean statusActionDisabled) {}

    public record Position(
            long id,
            String positionCode,
            String positionName,
            Long departmentId,
            String responsibilities,
            String status,
            long employeeCount) {}

    public record Employee(
            long id,
            String employeeNo,
            String employeeName,
            String mobile,
            Long departmentId,
            Long positionId,
            String employmentType,
            String status,
            LocalDate hireDate,
            String feishuBindingStatus,
            String feishuDisplayName,
            boolean passwordLoginEnabled,
            String feishuJobTitle) {}

    public record Summary(
            long formalEmployees,
            long temporaryEmployees,
            long pendingFeishuBindings,
            long disabledAccounts) {}

    public record SaveDepartment(
            @NotBlank @Size(max = 50) String departmentCode,
            @NotBlank @Size(max = 100) String departmentName,
            Long parentId,
            Long managerEmployeeId,
            @Min(0) int sortOrder,
            @NotBlank String status) {}

    public record SavePosition(
            @NotBlank @Size(max = 50) String positionCode,
            @NotBlank @Size(max = 100) String positionName,
            Long departmentId,
            @Size(max = 1000) String responsibilities,
            @NotBlank String status) {}

    public record SaveEmployee(
            @NotBlank @Size(max = 50) String employeeNo,
            @NotBlank @Size(max = 100) String employeeName,
            @Size(max = 30) String mobile,
            Long departmentId,
            Long positionId,
            @NotBlank String employmentType,
            @NotBlank String status,
            LocalDate hireDate,
            boolean passwordLoginEnabled,
            @Size(min = 8, max = 72) String password) {}

    public record Status(@NotBlank String status) {}
}
