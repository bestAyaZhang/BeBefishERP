package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.masterdata.application.CustomerAccountService;
import com.bebefish.erp.masterdata.application.CustomerAccountViews.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerAccountController {
    private final CustomerAccountService service;
    public CustomerAccountController(CustomerAccountService service) { this.service = service; }

    @GetMapping("/platform-options")
    @PreAuthorize("hasAuthority('customer:view')")
    public ApiResponse<List<PlatformOption>> platforms() { return ApiResponse.success(service.platforms()); }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAnyAuthority('customer:view','sales:create')")
    public ApiResponse<List<Price>> prices(@PathVariable long id) { return ApiResponse.success(service.prices(id)); }

    public record SavePrice(@NotNull @DecimalMin("0") @Digits(integer=16, fraction=2) BigDecimal unitPrice) {}

    @PutMapping("/{id}/prices/{skuId}")
    @PreAuthorize("hasAuthority('customer:edit')")
    public ApiResponse<List<Price>> savePrice(@PathVariable long id, @PathVariable long skuId,
            @Valid @RequestBody SavePrice request, @AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.savePrice(id, skuId, request.unitPrice(), principal.operatorIdentifier()));
    }

    @DeleteMapping("/{id}/prices/{skuId}")
    @PreAuthorize("hasAuthority('customer:edit')")
    public ApiResponse<Void> deletePrice(@PathVariable long id, @PathVariable long skuId) {
        service.deletePrice(id, skuId); return ApiResponse.success(null);
    }

    @GetMapping("/{id}/bills")
    @PreAuthorize("hasAuthority('customer:view') and hasAuthority('sales:view')")
    public ApiResponse<Bills> bills(@PathVariable long id, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) LocalDate dateFrom,
            @RequestParam(required=false) LocalDate dateTo) {
        return ApiResponse.success(service.bills(id, dateFrom, dateTo, page, size));
    }
}
