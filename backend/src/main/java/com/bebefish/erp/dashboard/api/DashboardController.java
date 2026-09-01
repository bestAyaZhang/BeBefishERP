package com.bebefish.erp.dashboard.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.dashboard.application.DashboardQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardQueryService service;

    public DashboardController(DashboardQueryService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<DashboardOverviewResponse> overview(
            @RequestParam(required = false) String period
    ) {
        return ApiResponse.success(DashboardOverviewResponse.from(service.overview(period)));
    }
}
