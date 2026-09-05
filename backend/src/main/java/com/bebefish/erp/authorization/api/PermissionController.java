package com.bebefish.erp.authorization.api;

import com.bebefish.erp.authorization.application.PermissionManagementService;
import com.bebefish.erp.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    private final PermissionManagementService service;

    public PermissionController(PermissionManagementService service) {
        this.service = service;
    }

    @GetMapping("/context")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<PermissionDtos.PermissionPageContext> context() {
        return ApiResponse.success(service.context());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<List<PermissionDtos.RoleSummary>> listRoles(
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(service.listRoles(keyword));
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<PermissionDtos.RoleResponse> getRole(@PathVariable long id) {
        return ApiResponse.success(service.getRole(id));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.RoleResponse> createRole(
            @Valid @RequestBody PermissionDtos.CreateRoleRequest request
    ) {
        return ApiResponse.success(service.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.RoleResponse> updateRole(
            @PathVariable long id,
            @Valid @RequestBody PermissionDtos.UpdateRoleRequest request
    ) {
        return ApiResponse.success(service.updateRole(id, request));
    }

    @PutMapping("/roles/{id}/configuration")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.RoleResponse> saveConfiguration(
            @PathVariable long id,
            @Valid @RequestBody PermissionDtos.SaveRoleConfigurationRequest request
    ) {
        return ApiResponse.success(service.saveConfiguration(id, request));
    }

    @PatchMapping("/roles/{id}/status")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.RoleResponse> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody PermissionDtos.ChangeRoleStatusRequest request
    ) {
        return ApiResponse.success(service.changeStatus(id, request.status()));
    }

    @GetMapping("/roles/{id}/members")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<PermissionDtos.RoleMemberPage> listMembers(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId
    ) {
        return ApiResponse.success(service.listMembers(id, page, size, keyword, departmentId, false));
    }

    @GetMapping("/roles/{id}/candidates")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<PermissionDtos.RoleMemberPage> listCandidates(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId
    ) {
        return ApiResponse.success(service.listMembers(id, page, size, keyword, departmentId, true));
    }

    @PostMapping("/roles/{id}/members")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.MemberMutationResult> addMembers(
            @PathVariable long id,
            @Valid @RequestBody PermissionDtos.MemberIdsRequest request
    ) {
        return ApiResponse.success(service.addMembers(id, request.employeeIds()));
    }

    @DeleteMapping("/roles/{id}/members")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.MemberRemovalResult> removeMembers(
            @PathVariable long id,
            @Valid @RequestBody PermissionDtos.MemberIdsRequest request
    ) {
        return ApiResponse.success(service.removeMembers(id, request.employeeIds()));
    }
}
