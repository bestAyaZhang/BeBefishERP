package com.bebefish.erp.shipping.api;

import com.bebefish.erp.common.api.*;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.application.ShippingFormOptionsService;
import com.bebefish.erp.shipping.domain.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {
    private final ShipmentService service;
    private final ShippingFormOptionsService optionsService;
    public ShipmentController(ShipmentService service, ShippingFormOptionsService optionsService) {
        this.service = service;
        this.optionsService = optionsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<PageResponse<Shipment>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String platform, @RequestParam(defaultValue = "false") boolean incompleteOnly,
            @RequestParam(required=false) Long platformId, @RequestParam(required=false) Long shopId,
            @RequestParam(defaultValue="false") boolean unlinkedOnly) {
        return ApiResponse.success(PageResponse.from(service.list(new ShipmentQuery(keyword, status, dateFrom, dateTo, platform, incompleteOnly, platformId, shopId, unlinkedOnly), page, size)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<ShipmentSummary> summary(@RequestParam(required = false) LocalDate date) {
        return ApiResponse.success(service.summary(date));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<ShippingFormOptions> formOptions() {
        return ApiResponse.success(optionsService.options());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<Shipment> get(@PathVariable long id) { return ApiResponse.success(service.get(id)); }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<ShippingFormOptionsService.FilterOptions> filterOptions() { return ApiResponse.success(optionsService.filterOptions()); }

    @PostMapping
    @PreAuthorize("hasAuthority('shipping:create')")
    public ApiResponse<Shipment> create(@Valid @RequestBody SaveShipmentRequest request, @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.create(request.form(), request.preparerEmployeeIds(), principal.operatorIdentifier(), operatorDisplayName(principal)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('shipping:edit')")
    public ApiResponse<Shipment> update(@PathVariable long id, @Valid @RequestBody SaveShipmentRequest request,
                                         @AuthenticationPrincipal ErpPrincipal principal) {
        if (request.version() == null) throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "缺少发货单版本，请重新打开后再保存");
        if (request.status() == null || request.status().isBlank())
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "请选择备货状态");
        return ApiResponse.success(service.update(id, request.form(), request.status(), request.version(), principal.operatorIdentifier(),
                request.preparerEmployeeIds(), principal.roles().contains("SUPER_ADMIN")));
    }

    @PatchMapping("/{id}/preparation")
    @PreAuthorize("hasAuthority('shipping:view') and hasAuthority('shipping:prepare')")
    public ApiResponse<Shipment> updatePreparation(@PathVariable long id, @Valid @RequestBody UpdatePreparationRequest request,
                                                    @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.updatePreparation(id, request.status(), request.actualWeight(), request.version(), principal));
    }

    private static String operatorDisplayName(ErpPrincipal principal) {
        if (principal.displayName() != null && !principal.displayName().isBlank()) return principal.displayName().strip();
        if (principal.mobile() != null && !principal.mobile().isBlank()) return principal.mobile().strip();
        return "员工#" + principal.employeeId();
    }
}
