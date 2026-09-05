package com.bebefish.erp.authorization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class PermissionDtos {
    private PermissionDtos() {
    }

    public record PermissionPageContext(List<DepartmentOption> departments, String organizationSummary) {
    }

    public record DepartmentOption(long id, String name) {
    }

    public record RoleSummary(
            long id,
            String code,
            String name,
            String kind,
            boolean immutable,
            String status,
            String updatedBy,
            String updatedAt,
            int memberCount
    ) {
    }

    public record RoleResponse(
            long id,
            String code,
            String name,
            String description,
            String kind,
            boolean immutable,
            boolean sensitive,
            String status,
            String dataScope,
            List<String> permissionCodes,
            List<Long> memberIds,
            String updatedBy,
            String updatedAt
    ) {
    }

    public record CreateRoleRequest(
            @NotBlank String name,
            @NotBlank String code,
            String description,
            Long copyFromRoleId
    ) {
    }

    public record UpdateRoleRequest(@NotBlank String name, String description) {
    }

    public record SaveRoleConfigurationRequest(
            @NotNull List<String> permissionCodes,
            @NotBlank String dataScope
    ) {
    }

    public record ChangeRoleStatusRequest(@NotBlank String status) {
    }

    public record MemberIdsRequest(@NotEmpty List<Long> employeeIds) {
    }

    public record RoleMember(
            long employeeId,
            String employeeNo,
            String employeeName,
            String mobile,
            long departmentId,
            String departmentName,
            String positionName,
            String employmentType,
            List<String> otherRoleNames,
            String finalDataScope,
            String lockedReason
    ) {
    }

    public record RoleMemberPage(List<RoleMember> records, int page, int pageSize, long total) {
    }

    public record MemberMutationResult(int added, int skipped, RoleResponse role) {
    }

    public record MemberRemovalResult(int removed, int skippedLocked, RoleResponse role) {
    }

    public record SaveFeishuRoleMappingRequest(
            @NotBlank String feishuRoleName,
            long erpRoleId,
            boolean enabled
    ) {
    }

    public record FeishuRoleMappingResponse(
            String feishuRoleId,
            String feishuRoleName,
            long erpRoleId,
            String erpRoleName,
            boolean enabled,
            int memberCount,
            String lastSyncedAt,
            String lastError
    ) {
    }

    public record MappingCandidateRole(long id, String code, String name) {
    }
}
