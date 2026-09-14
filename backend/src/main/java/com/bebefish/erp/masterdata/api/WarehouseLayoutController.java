package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.masterdata.application.WarehouseLayoutService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouses/{id}/layout")
public class WarehouseLayoutController {
    private final WarehouseLayoutService service;
    public WarehouseLayoutController(WarehouseLayoutService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<WarehouseLayoutService.Layout> load(@PathVariable long id) {
        return ApiResponse.success(service.load(id));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('warehouse:edit')")
    public ApiResponse<WarehouseLayoutService.Layout> save(@PathVariable long id, @RequestBody WarehouseLayoutService.Layout layout) {
        return ApiResponse.success(service.save(id, layout));
    }
}
