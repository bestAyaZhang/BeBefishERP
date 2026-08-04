package com.bebefish.erp.inventory.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.inventory.application.StockAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/adjustments")
public class StockAdjustmentController {
    private final StockAdjustmentService service;

    public StockAdjustmentController(StockAdjustmentService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<StockAdjustmentResponse> get(@PathVariable long id) {
        return ApiResponse.success(StockAdjustmentResponse.from(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<StockAdjustmentResponse> create(@Valid @RequestBody SaveStockAdjustmentRequest request) {
        return ApiResponse.success(StockAdjustmentResponse.from(service.create(request.toCommand())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<StockAdjustmentResponse> update(
            @PathVariable long id,
            @Valid @RequestBody SaveStockAdjustmentRequest request
    ) {
        return ApiResponse.success(StockAdjustmentResponse.from(service.update(id, request.toCommand())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<StockAdjustmentResponse> confirm(
            @PathVariable long id,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(StockAdjustmentResponse.from(service.confirm(id, principal.mobile())));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<StockAdjustmentResponse> voidAdjustment(
            @PathVariable long id,
            @Valid @RequestBody(required = false) VoidStockAdjustmentRequest request,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        var reason = request == null ? null : request.reason();
        return ApiResponse.success(StockAdjustmentResponse.from(
                service.voidAdjustment(id, reason, principal.mobile())
        ));
    }
}
