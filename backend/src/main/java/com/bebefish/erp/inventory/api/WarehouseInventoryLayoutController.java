package com.bebefish.erp.inventory.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.inventory.application.WarehouseInventoryLayoutQueryService;
import com.bebefish.erp.inventory.application.WarehouseInventoryLayoutView;
import com.bebefish.erp.inventory.application.WarehousePileAllocationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouses/{warehouseId}/inventory-layout")
public class WarehouseInventoryLayoutController {
    private final WarehouseInventoryLayoutQueryService service;
    private final WarehousePileAllocationService allocationService;

    public WarehouseInventoryLayoutController(
            WarehouseInventoryLayoutQueryService service,
            WarehousePileAllocationService allocationService
    ) {
        this.service = service;
        this.allocationService = allocationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<WarehouseInventoryLayoutView> get(@PathVariable long warehouseId) {
        return ApiResponse.success(service.get(warehouseId));
    }

    @PostMapping("/pile-allocations")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<WarehouseInventoryLayoutView> allocate(
            @PathVariable long warehouseId,
            @Valid @RequestBody AllocatePileInventoryRequest request
    ) {
        return ApiResponse.success(allocationService.allocate(warehouseId, request.toCommand()));
    }
}
