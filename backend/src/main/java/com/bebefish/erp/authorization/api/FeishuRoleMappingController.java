package com.bebefish.erp.authorization.api;

import com.bebefish.erp.authorization.application.FeishuRoleMappingService;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.feishu.FeishuBusinessRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
public class FeishuRoleMappingController {
    private final FeishuRoleMappingService service;

    public FeishuRoleMappingController(FeishuRoleMappingService service) {
        this.service = service;
    }

    @GetMapping("/feishu-roles")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<List<FeishuBusinessRole>> feishuRoles() {
        return ApiResponse.success(service.listFeishuRoles());
    }

    @GetMapping("/feishu-role-mappings")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<List<PermissionDtos.FeishuRoleMappingResponse>> mappings() {
        return ApiResponse.success(service.listMappings());
    }

    @GetMapping("/feishu-role-mappings/candidate-roles")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<List<PermissionDtos.MappingCandidateRole>> candidates() {
        return ApiResponse.success(service.candidateRoles());
    }

    @PutMapping("/feishu-role-mappings/{feishuRoleId}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<PermissionDtos.FeishuRoleMappingResponse> save(
            @PathVariable String feishuRoleId,
            @Valid @RequestBody PermissionDtos.SaveFeishuRoleMappingRequest request
    ) {
        return ApiResponse.success(service.save(feishuRoleId, request));
    }

    @DeleteMapping("/feishu-role-mappings/{feishuRoleId}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<Void> delete(@PathVariable String feishuRoleId) {
        service.delete(feishuRoleId);
        return ApiResponse.success(null);
    }

    @PostMapping("/feishu-role-mappings/sync")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<List<PermissionDtos.FeishuRoleMappingResponse>> sync() {
        return ApiResponse.success(service.sync());
    }
}
