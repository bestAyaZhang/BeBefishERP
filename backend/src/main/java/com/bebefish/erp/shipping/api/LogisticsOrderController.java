package com.bebefish.erp.shipping.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.logistics.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipments")
public class LogisticsOrderController {
    private final AneOrderService service;
    private final ShipmentService shipments;
    public LogisticsOrderController(AneOrderService service, ShipmentService shipments) { this.service = service; this.shipments = shipments; }
    @GetMapping("/logistics/availability")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<AneOrderService.Availability> availability() { return ApiResponse.success(service.availability()); }
    @GetMapping("/{id}/logistics-order")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<LogisticsOrder> get(@PathVariable long id) { shipments.get(id); return ApiResponse.success(service.get(id)); }
    @PostMapping("/{id}/logistics-order")
    @PreAuthorize("hasAuthority('shipping:order') and hasAuthority('shipping:view')")
    public ApiResponse<LogisticsOrder> place(@PathVariable long id, @Valid @RequestBody PlaceAneOrderRequest request,
                                            @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.place(id, request, principal.operatorIdentifier()));
    }
}
