package com.bebefish.erp.organization.api;

import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.organization.application.FeishuDirectorySyncTaskService;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization/feishu-syncs")
public class FeishuSyncController {
    private final FeishuDirectorySyncTaskService tasks;
    private final UserAccountRepository users;

    public FeishuSyncController(FeishuDirectorySyncTaskService tasks, UserAccountRepository users) {
        this.tasks = tasks;
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('organization:sync')")
    public ApiResponse<FeishuDirectorySyncTaskService.Task> start(
            @AuthenticationPrincipal ErpPrincipal principal) {
        var user =
                users.findByEmployeeId(principal.employeeId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "用户不存在"));
        return ApiResponse.success(tasks.startManual(user.id()));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<FeishuDirectorySyncTaskService.Task> latest() {
        return ApiResponse.success(tasks.latest());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:view')")
    public ApiResponse<FeishuDirectorySyncTaskService.Task> get(@PathVariable long id) {
        return ApiResponse.success(tasks.get(id));
    }
}
