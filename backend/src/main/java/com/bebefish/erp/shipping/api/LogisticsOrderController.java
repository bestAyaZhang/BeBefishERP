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
    public ApiResponse<AneOrderService.Availability> availability(@RequestParam(required=false) Long companyId) { return ApiResponse.success(companyId==null?service.availability():service.availability(companyId)); }
    @GetMapping("/{id}/logistics-order")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<LogisticsOrder> get(@PathVariable long id) { shipments.get(id); return ApiResponse.success(service.get(id)); }
    @GetMapping("/{id}/logistics-trace")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<com.bebefish.erp.shipping.logistics.yimi.YimiTraceResult> trace(@PathVariable long id) {
        shipments.get(id);return ApiResponse.success(service.trace(id));
    }
    @PostMapping("/{id}/logistics-order")
    @PreAuthorize("hasAuthority('shipping:order') and hasAuthority('shipping:view')")
    public ApiResponse<LogisticsOrder> place(@PathVariable long id, @Valid @RequestBody PlaceAneOrderRequest request,
                                            @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.place(id, request, principal.operatorIdentifier()));
    }
    @PostMapping("/{id}/logistics-order/cancel")
    @PreAuthorize("hasAuthority('shipping:cancel') and hasAuthority('shipping:view')")
    public ApiResponse<LogisticsOrder> cancel(@PathVariable long id, @Valid @RequestBody CancelAneOrderRequest request,
                                             @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.cancel(id, request, principal.operatorIdentifier()));
    }
}
